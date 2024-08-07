/*
 * JPedal Microservice Example
 *
 * Project Info: https://github.com/idrsolutions/jpedal-microservice-example
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.idrsolutions.microservice;

import com.idrsolutions.image.utility.SupportedFormats;
import com.idrsolutions.microservice.db.DBHandler;
import com.idrsolutions.microservice.storage.Storage;
import com.idrsolutions.microservice.utils.LibreOfficeHelper;
import com.idrsolutions.microservice.utils.ProcessUtils;
import com.idrsolutions.microservice.utils.SettingsValidator;
import com.idrsolutions.microservice.utils.ZipHelper;
import org.jpedal.PdfDecoderServer;
import org.jpedal.exception.PdfException;

import javax.json.stream.JsonParsingException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides an API to use JPedal on its own dedicated app server. See the API
 * documentation for more information on how to interact with this servlet.
 * 
 * @see BaseServlet
 */
@WebServlet(name = "jpedal", urlPatterns = "/jpedal")
@MultipartConfig
public class JPedalServlet extends BaseServlet {

    private enum Mode {
        convertToImages, extractImages, extractText
    }

    private static final String[] validModes = Arrays.stream(Mode.values()).map(Enum::name).toArray(String[]::new);
    private static final String[] validEncoderFormats;
    static {
        final String[][] encoderFormats = SupportedFormats.getSupportedImageEncoders();
        validEncoderFormats = Arrays.stream(encoderFormats).flatMap(Arrays::stream).toArray(String[]::new);
    }

    private static final Logger LOG = Logger.getLogger(JPedalServlet.class.getName());
    private static final String fileSeparator = System.getProperty("file.separator");

    /**
     * Converts given pdf file or office document to images using JPedal.
     * <p>
     * LibreOffice is used to preconvert office documents to PDF for JPedal to
     * process.
     * <p>
     * See API docs for information on how this method communicates via the
     * individual object to the client.
     * 
     * @param uuid The uuid of the conversion
     * @param inputFile The input file
     * @param contextUrl The context that this servlet is running in
     */
    @Override
    protected void convert(final String uuid, final File inputFile, final String contextUrl) {

        final Map<String, String> conversionParams;
        try {
            final Map<String, String> settings = DBHandler.getInstance().getSettings(uuid);
            conversionParams = settings != null ? settings : new HashMap<>();
        } catch (final SQLException e) {
            DBHandler.getInstance().setError(uuid, 500, "Database failure");
            return;
        }

        final String fileName = inputFile.getName();
        final String ext = fileName.substring(fileName.lastIndexOf(".") + 1);

        final Properties properties = (Properties) getServletContext().getAttribute(BaseServletContextListener.KEY_PROPERTIES);

        final File inputPdf;
        final File outputDir = new File(getOutputPath(), uuid);

        final boolean isPDF = ext.toLowerCase().endsWith("pdf");
        if (!isPDF) {
            final String libreOfficePath = properties.getProperty(BaseServletContextListener.KEY_PROPERTY_LIBRE_OFFICE);
            final long libreOfficeTimeout = Long.parseLong(properties.getProperty(BaseServletContextListener.KEY_PROPERTY_LIBRE_OFFICE_TIMEOUT));
            final ProcessUtils.Result libreOfficeConversionResult = LibreOfficeHelper.convertDocToPDF(libreOfficePath, inputFile, uuid, libreOfficeTimeout);
            switch (libreOfficeConversionResult) {
                case TIMEOUT:
                    DBHandler.getInstance().setError(uuid, libreOfficeConversionResult.getCode(), "Maximum conversion duration exceeded.");
                    return;
                case ERROR:
                    DBHandler.getInstance().setError(uuid, libreOfficeConversionResult.getCode(), "Internal error processing file");
                    return;
                case SUCCESS:
                    inputPdf = new File(inputFile.getParentFile(), uuid + ".pdf");
                    if (!inputPdf.exists()) {
                        LOG.log(Level.SEVERE, "LibreOffice error found while converting to PDF: " + inputPdf.getAbsolutePath());
                        DBHandler.getInstance().setError(uuid, 1080, "Error processing PDF");
                        return;
                    }
                    break;
                default:
                    LOG.log(Level.SEVERE, "Unexpected error has occurred converting office document: " + libreOfficeConversionResult.getCode() + " using LibreOffice");
                    DBHandler.getInstance().setError(uuid, libreOfficeConversionResult.getCode(), "Failed to convert office document to PDF");
                    return;
            }
        } else {
            inputPdf = inputFile;
        }

        //Makes the directory for the output file
        if (!outputDir.mkdirs()) {
            LOG.log(Level.SEVERE, "Failed to create output directory: " + outputDir.getAbsolutePath());
            DBHandler.getInstance().setError(uuid, 500, "File system failure");
            return;
        }

        final int pageCount;
        try {
            final PdfDecoderServer decoder = new PdfDecoderServer(false);
            decoder.openPdfFile(inputPdf.getAbsolutePath());

            decoder.setEncryptionPassword(conversionParams.getOrDefault("org.jpedal.pdf2html.password", ""));

            if (decoder.isEncrypted() && !decoder.isPasswordSupplied()) {
                LOG.log(Level.SEVERE, "Invalid Password");
                DBHandler.getInstance().setError(uuid, 1070, "Invalid password supplied.");
                return;
            }

            pageCount = decoder.getPageCount();
            DBHandler.getInstance().setCustomValue(uuid, "pageCount", String.valueOf(pageCount));
            DBHandler.getInstance().setCustomValue(uuid, "pagesConverted", "0");
            decoder.closePdfFile();
            decoder.dispose();
        } catch (final PdfException e) {
            LOG.log(Level.SEVERE, "Invalid PDF", e);
            DBHandler.getInstance().setError(uuid, 1060, "Invalid PDF");
            return;
        }
        DBHandler.getInstance().setState(uuid, "processing");

        try {
            final String servletDirectory = getServletContext().getRealPath("");
            final String webappDirectory;
            if (servletDirectory != null) {
                webappDirectory = servletDirectory + File.separator + "WEB-INF/lib/jpedal.jar";
            } else {
                webappDirectory = "WEB-INF/lib/jpedal.jar";
            }

            final long maxDuration = Long.parseLong(properties.getProperty(BaseServletContextListener.KEY_PROPERTY_MAX_CONVERSION_DURATION));

            final ProcessUtils.Result result = convertFile(conversionParams, uuid, webappDirectory, inputPdf, outputDir, maxDuration);

            if ("1230".equals(DBHandler.getInstance().getStatus(uuid).get("errorCode"))) {
                final String message = String.format("Conversion %s exceeded max duration of %dms", uuid, maxDuration);
                LOG.log(Level.INFO, message);
                return;
            }

            switch (result) {
                case SUCCESS:
                    final File outputZip = new File(outputDir.getParentFile(), uuid + ".zip");
                    ZipHelper.zipFolder(outputDir, outputZip, false);

                    DBHandler.getInstance().setCustomValue(uuid, "downloadUrl", contextUrl + "/output/" + uuid + ".zip");

                    final Storage storage = (Storage) getServletContext().getAttribute("storage");

                    if (storage != null) {
                    final String remoteUrl = storage.put(outputZip, uuid + ".zip", uuid);
                        DBHandler.getInstance().setCustomValue(uuid, "remoteUrl", remoteUrl);
                    }

                    DBHandler.getInstance().setState(uuid, "processed");

                    break;
                case TIMEOUT:
                    final String message = String.format("Conversion %s exceeded max duration of %dms", uuid, maxDuration);
                    LOG.log(Level.INFO, message);
                    DBHandler.getInstance().setError(uuid, 1230, "Conversion exceeded max duration of " + maxDuration + "ms");
                    break;
                case ERROR:
                    LOG.log(Level.SEVERE, "An error occurred during the conversion");
                    DBHandler.getInstance().setError(uuid, 1220, "An error occurred during the conversion");
                    break;

                }
            } catch (final Throwable ex) {
                LOG.log(Level.SEVERE, "Exception thrown when converting input", ex);
                DBHandler.getInstance().setError(uuid, 1220, "Exception thrown when converting input: " + ex.getMessage());
            }
    }

    /**
     * Validates the settings parameter passed to the request. It will parse the conversionParams,
     * validate them, and then set the params in the Individual object.
     *
     * If settings are not parsed or validated, doError will be called.
     *
     * @param request the request for this conversion
     * @param response the response object for the request
     * @param uuid the uuid of this conversion
     * @return true if the settings are parsed and validated successfully, false if not
     */
    @Override
    protected boolean validateRequest(final HttpServletRequest request, final HttpServletResponse response,
                                      final String uuid) {

        final Map<String, String> settings;
        try {
            settings = parseSettings(request.getParameter("settings"));
        } catch (JsonParsingException exception) {
            doError(request, response, "Error encountered when parsing settings JSON <" + exception.getMessage() + '>', 400);
            return false;
        }

        final SettingsValidator settingsValidator = new SettingsValidator(settings);

        final String mode = settingsValidator.validateString("mode", validModes, true);
        if (mode != null && Arrays.stream(validModes).anyMatch(s -> s.equals(mode))) {
            switch (Mode.valueOf(mode)) {
                case convertToImages:
                    settingsValidator.validateString("format", validEncoderFormats, true);
                    settingsValidator.validateFloat("scaling", new float[]{0.1f, 10}, false);
                    settingsValidator.validateString("password", ".*", false);
                    break;
                case extractImages:
                    settingsValidator.validateString("type",
                            new String[]{"rawImages", "clippedImages"}, true);
                    settingsValidator.validateString("format", validEncoderFormats, true);
                    settingsValidator.validateString("password", ".*", false);
                    break;
                case extractText:
                    settingsValidator.validateString("type",
                            new String[]{"plainText", "wordlist", "structuredText"}, true);
                    settingsValidator.validateString("password", ".*", false);
                    break;
            }
        }

        if (!settingsValidator.isValid()) {
            doError(request, response, "Invalid settings detected.\n" + settingsValidator.getMessage(), 400);
            return false;
        }

        request.setAttribute("com.idrsolutions.microservice.settings", settings);

        return true;
    }

    private ProcessUtils.Result convertFile(final Map<String, String> conversionParams,
        final String uuid, final String webappDirectory, final File inputPdf,
        final File outputDir, final long maxDuration) {


        final ArrayList<String> commandArgs = new ArrayList<>();
        commandArgs.add("java");

        final Properties properties = (Properties) getServletContext().getAttribute(BaseServletContextListener.KEY_PROPERTIES);
        final int memoryLimit = Integer.parseInt(properties.getProperty(BaseServletContextListener.KEY_PROPERTY_CONVERSION_MEMORY));

        final int remoteTrackerPort = Integer.parseInt((String) properties.get(BaseServletContextListener.KEY_PROPERTY_REMOTE_TRACKING_PORT));

        final float scaling = (conversionParams.containsKey("scaling") ? Float.parseFloat(conversionParams.remove("scaling")) * 1.52f : 1.52f);
        if (memoryLimit > 0) {
            commandArgs.add("-Xmx" + memoryLimit + "M");
        }

        //Set settings
        commandArgs.add("-Dcom.idrsolutions.remoteTracker.port=" + remoteTrackerPort);
        commandArgs.add("-Dcom.idrsolutions.remoteTracker.uuid=" + uuid);

        final Mode mode = Mode.valueOf((conversionParams.remove("mode")));

        if (!conversionParams.isEmpty()) {
            final Set<String> keys = conversionParams.keySet();
            for (final String key : keys) {
                if (!key.equals("type") && !key.equals("format")) {
                    final String value = conversionParams.get(key);
                    commandArgs.add("-D" + key + '=' + value);
                }
            }
        }

        //Add jar and input / output
        commandArgs.add("-cp");
        commandArgs.add(webappDirectory);

        switch (mode) {
            case convertToImages:
                commandArgs.add("org.jpedal.examples.images.ConvertPagesToImages");
                commandArgs.add(inputPdf.getAbsolutePath());
                commandArgs.add(outputDir.getAbsolutePath());
                commandArgs.add(conversionParams.get("format"));
                commandArgs.add(String.valueOf(scaling));
                break;
            case extractImages:
                final String type = conversionParams.get("type");
                switch (type) {
                    case "rawImages" :
                        commandArgs.add("org.jpedal.examples.images.ExtractImages");
                        commandArgs.add(inputPdf.getAbsolutePath());
                        commandArgs.add(outputDir.getAbsolutePath());
                        commandArgs.add(conversionParams.get("format"));

                        break;
                    case "clippedImages" :
                        String name = inputPdf.getName();
                        commandArgs.add("org.jpedal.examples.images.ExtractClippedImages");
                        commandArgs.add(inputPdf.getAbsolutePath());
                        commandArgs.add(outputDir.getAbsolutePath());
                        commandArgs.add(conversionParams.get("format"));
                        commandArgs.add("0");
                        commandArgs.add(name.substring(0, name.lastIndexOf(".")));
                        break;
                }
                break;
            case extractText:
                final String textType = conversionParams.get("type");
                switch (textType) {
                    case "plainText" :
                        commandArgs.add("org.jpedal.examples.text.ExtractTextInRectangle");
                        commandArgs.add(inputPdf.getAbsolutePath());
                        commandArgs.add(outputDir.getAbsolutePath() + fileSeparator);
                        break;
                    case "wordlist" :
                        commandArgs.add("org.jpedal.examples.text.ExtractTextAsWordlist");
                        commandArgs.add(inputPdf.getAbsolutePath());
                        commandArgs.add(outputDir.getAbsolutePath());
                        break;
                    case "structuredText" :
                        commandArgs.add("org.jpedal.examples.text.ExtractStructuredText");
                        commandArgs.add(inputPdf.getAbsolutePath());
                        commandArgs.add(outputDir.getAbsolutePath());
                        break;
                }
                break;
                default:
                    throw new RuntimeException("Unrecognised mode specified: " + mode.name());

                }


                final String[] commands = commandArgs.toArray(new String[0]);

                return ProcessUtils.runProcess(commands, inputPdf.getParentFile(), uuid, "JPedal Conversion", maxDuration);
    }
}
