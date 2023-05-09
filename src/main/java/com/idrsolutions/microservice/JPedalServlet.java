/*
 * JPedal Microservice Example
 *
 * Project Info: https://github.com/idrsolutions/jpedal-microservice-example
 *
 * Copyright 2023 IDRsolutions
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
import com.idrsolutions.microservice.utils.ConversionTracker;
import com.idrsolutions.microservice.utils.DefaultFileServlet;
import com.idrsolutions.microservice.utils.LibreOfficeHelper;
import com.idrsolutions.microservice.utils.SettingsValidator;
import com.idrsolutions.microservice.utils.ZipHelper;
import org.jpedal.PdfDecoderServer;
import org.jpedal.examples.images.ConvertPagesToImages;
import org.jpedal.examples.images.ExtractClippedImages;
import org.jpedal.examples.images.ExtractImages;
import org.jpedal.examples.text.ExtractStructuredText;
import org.jpedal.examples.text.ExtractTextAsWordlist;
import org.jpedal.examples.text.ExtractTextInRectangle;
import org.jpedal.exception.PdfException;
import org.jpedal.external.ErrorTracker;
import org.w3c.dom.Document;

import javax.json.stream.JsonParsingException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
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
     * @param outputDir The output directory of the converted file
     * @param contextUrl The context that this servlet is running in
     */
    @Override
    protected void convert(String uuid,
                           File inputFile, File outputDir, String contextUrl) {

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
        final String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
        // To avoid repeated calls to getParent() and getAbsolutePath()
        final String inputDir = inputFile.getParent();
        final String outputDirStr = outputDir.getAbsolutePath();
        final Properties properties = (Properties) getServletContext().getAttribute(BaseServletContextListener.KEY_PROPERTIES);
        
        final String userPdfFilePath;

        final boolean isPDF = ext.toLowerCase().endsWith("pdf");
        if (!isPDF) {
            final String libreOfficePath = properties.getProperty(BaseServletContextListener.KEY_PROPERTY_LIBRE_OFFICE);
            final long libreOfficeTimeout = Long.parseLong(properties.getProperty(BaseServletContextListener.KEY_PROPERTY_LIBRE_OFFICE_TIMEOUT));
            LibreOfficeHelper.Result libreOfficeConversionResult = LibreOfficeHelper.convertDocToPDF(libreOfficePath, inputFile, uuid, libreOfficeTimeout);
            switch (libreOfficeConversionResult) {
                case TIMEOUT:
                    DBHandler.getInstance().setError(uuid, libreOfficeConversionResult.getCode(), "Maximum conversion duration exceeded.");
                    return;
                case ERROR:
                    DBHandler.getInstance().setError(uuid, libreOfficeConversionResult.getCode(), "Internal error processing file");
                    return;
                case SUCCESS:
                    userPdfFilePath = inputDir + fileSeparator + fileNameWithoutExt + ".pdf";
                    final File inputPdf = new File(userPdfFilePath);
                    if (!inputPdf.exists()) {
                        LOG.log(Level.SEVERE, "LibreOffice error found while converting to PDF: " + inputPdf.getAbsolutePath());
                        DBHandler.getInstance().setError(uuid, 1080, "Error processing PDF");
                        return;
                    }
                default:
                    LOG.log(Level.SEVERE, "Unexpected error has occurred converting office document: " + libreOfficeConversionResult.getCode() + " using LibreOffice");
                    DBHandler.getInstance().setError(uuid, libreOfficeConversionResult.getCode(), "Failed to convert office document to PDF");
                    return;
            }
        } else {
            userPdfFilePath = inputDir + fileSeparator + fileName;
        }

        //Makes the directory for the output file
        new File(outputDirStr + fileSeparator + fileNameWithoutExt).mkdirs();

        final int pageCount;
        try {
            final PdfDecoderServer decoder = new PdfDecoderServer(false);
            decoder.openPdfFile(inputFile.getAbsolutePath());

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
        DBHandler.getInstance().setState(uuid,"processing");

        try {

            final Mode mode;
            try {
                mode = Mode.valueOf(conversionParams.remove("mode"));
            } catch (final IllegalArgumentException | NullPointerException e) {
                throw new JPedalServletException("Required setting \"mode\" has incorrect value. Valid values are "
                        + Arrays.toString(Mode.values()) + '.');
            }

            final long maxDuration = Long.parseLong(properties.getProperty(BaseServletContextListener.KEY_PROPERTY_MAX_CONVERSION_DURATION));

            convertPDF(mode, userPdfFilePath, outputDirStr, fileNameWithoutExt, conversionParams, new ConversionTracker(uuid, maxDuration));

            if ("1230".equals(DBHandler.getInstance().getStatus(uuid).get("errorCode"))) {
                final String message = String.format("Conversion %s exceeded max duration of %dms", uuid, maxDuration);
                LOG.log(Level.INFO, message);
                return;
            }

            ZipHelper.zipFolder(outputDirStr + fileSeparator + fileNameWithoutExt,
                    outputDirStr + fileSeparator + fileNameWithoutExt + ".zip");

            final String outputPathInDocroot = uuid + '/' + DefaultFileServlet.encodeURI(fileNameWithoutExt);
            DBHandler.getInstance().setCustomValue(uuid, "downloadUrl", contextUrl + "/output/" + outputPathInDocroot + ".zip");

            final Storage storage = (Storage) getServletContext().getAttribute("storage");

            if (storage != null) {
                final String remoteUrl = storage.put(new File(outputDirStr + "/" + fileNameWithoutExt + ".zip"), fileNameWithoutExt + ".zip", uuid);
                DBHandler.getInstance().setCustomValue(uuid, "remoteUrl", remoteUrl);
            }

            DBHandler.getInstance().setState(uuid, "processed");

        } catch (final Throwable ex) {
            LOG.log(Level.SEVERE, "Exception thrown when converting input", ex);
            DBHandler.getInstance().setError(uuid, 1220, "Exception thrown when converting input" + ex.getMessage());
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

    private static void convertPDF(final Mode mode, final String userPdfFilePath, final String outputDirStr,
                                   final String fileNameWithoutExt, final Map<String, String> paramMap, final ErrorTracker durationTracker) throws Exception {

        switch (mode) {
            case convertToImages:
                ConvertPagesToImages.writeAllPagesAsImagesToDir(
                        userPdfFilePath,
                        outputDirStr + fileSeparator + fileNameWithoutExt + fileSeparator,
                        paramMap.get("format"),
                        Float.parseFloat(paramMap.getOrDefault("scaling", "1.0")),
                        paramMap.get("password"),
                        durationTracker);
                break;
                case extractImages:{
                final String type = paramMap.get("type");
                switch (type) {
                    case "rawImages" :
                        ExtractImages.writeAllImagesToDir(
                                userPdfFilePath,
                                paramMap.get("password"),
                                outputDirStr + fileSeparator + fileNameWithoutExt + fileSeparator ,
                                paramMap.get("format"), true, true,
                                durationTracker);
                        break;
                    case "clippedImages" :
                        ExtractClippedImages.writeAllClippedImagesToDirs(userPdfFilePath,
                                paramMap.get("password"),
                                outputDirStr + fileSeparator,
                                paramMap.get("format"),new String[]{"0",fileNameWithoutExt},
                                durationTracker);
                        break;
                }
                } break;
            case extractText:
                final String type = paramMap.get("type");
                switch (type) {
                    case "plainText" :
                        ExtractTextInRectangle.writeAllTextToDir(
                                userPdfFilePath,
                                paramMap.get("password"),
                                outputDirStr + fileSeparator,
                                -1,
                                ExtractTextInRectangle.OUTPUT_FORMAT.TXT,
                                false,
                                durationTracker);
                        break;
                    case "wordlist" :
                        ExtractTextAsWordlist.writeAllWordlistsToDir(
                                userPdfFilePath,
                                paramMap.get("password"),
                                outputDirStr + fileSeparator,
                                -1,
                                durationTracker);
                        break;
                    case "structuredText" :
                        ExtractStructuredText checkForStructure = new ExtractStructuredText(userPdfFilePath);
                        if (checkForStructure.openPDFFile()) {
                            Document content = checkForStructure.getStructuredTextContent();
                            if (content != null && content.hasChildNodes() && content.getDocumentElement().hasChildNodes()) {
                                ExtractStructuredText.writeAllStructuredTextOutlinesToDir(
                                        userPdfFilePath,
                                        paramMap.get("password"),
                                        outputDirStr + fileSeparator + fileNameWithoutExt + fileSeparator,
                                        durationTracker);
                            } else {
                                throw new JPedalServletException("File contains no structured content to extract.");
                            }
                        } else {
                            throw new JPedalServletException("Unable to open specified file");
                        }
                        break;
                }
                break;
            default:
                throw new JPedalServletException("Unrecognised mode specified: " + mode.name());
        }
    }

    static class JPedalServletException extends Exception {
        JPedalServletException(final String msg) {
            super(msg);
        }
    }
}
