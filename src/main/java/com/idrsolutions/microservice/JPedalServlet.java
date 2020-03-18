/*
 * JPedal Microservice Example
 *
 * Project Info: https://github.com/idrsolutions/jpedal-microservice-example
 *
 * Copyright 2020 IDRsolutions
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
import com.idrsolutions.microservice.utils.ZipHelper;
import org.jpedal.examples.images.ConvertPagesToImages;
import org.jpedal.examples.text.ExtractStructuredText;
import org.jpedal.examples.text.ExtractTextAsWordlist;
import org.jpedal.examples.text.ExtractTextInRectangle;
import org.jpedal.utils.StringUtils;
import org.w3c.dom.Document;

import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Provides an API to use JPedal on its own dedicated app server. See the API
 * documentation for more information on how to interact with this servlet.
 * 
 * @see BaseServlet
 */
@WebServlet(name = "jpedal", urlPatterns = {"/jpedal"})
@MultipartConfig
public class JPedalServlet extends BaseServlet {

    private enum MODES {
        convertToImages, extractText, extractWordlist
    }
    private static final Logger LOG = Logger.getLogger(JPedalServlet.class.getName());
    private static final String fileSeparator = System.getProperty("file.separator");
    private static final char htmlPathSeparator = '/';
    /**
     * Converts given pdf file or office document to images using JPedal.
     * <p>
     * LibreOffice is used to preconvert office documents to PDF for JPedal to
     * process.
     * <p>
     * See API docs for information on how this method communicates via the
     * individual object to the client.
     * 
     * @param individual The individual object associated with this conversion
     * @param params The map of parameters that came with the request
     * @param inputFile The input file
     * @param outputDir The output directory of the converted file
     * @param contextUrl The context that this servlet is running in
     */
    @Override
    protected void convert(Individual individual, Map<String, String[]> params,
                           File inputFile, File outputDir, String contextUrl) {

        final String[] settings = params.get("settings");
        final String[] conversionParams = settings != null ? getConversionParams(settings[0]) : null;

        final String fileName = inputFile.getName();
        final String ext = fileName.substring(fileName.lastIndexOf(".") + 1);
        final String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
        // To avoid repeated calls to getParent() and getAbsolutePath()
        final String inputDir = inputFile.getParent();
        final String outputDirStr = outputDir.getAbsolutePath();
        
        final String userPdfFilePath;

        final boolean isPDF = ext.toLowerCase().endsWith("pdf");
        if (!isPDF) {
            final int result = convertToPDF(inputFile);
            if (result != 0) {
                individual.setState("error");
                setErrorCode(individual, result);
                return;
            }
            userPdfFilePath = inputDir + fileSeparator + fileNameWithoutExt + ".pdf";
        } else {
            userPdfFilePath = inputDir + fileSeparator + fileName;
        }

        try {
            validateSettings(conversionParams);
        } catch(IllegalArgumentException ex) {
            LOG.log(Level.SEVERE, "Provided settings are not correct", ex);
            individual.setState("error");
            return;
        }

        //Makes the directory for the output file
        new File(outputDirStr + fileSeparator + fileNameWithoutExt).mkdirs();

        individual.setState("processing");

        try {

            final HashMap<String, String> paramMap = new HashMap<>();
            if (conversionParams != null) { //handle string based parameters
                if (conversionParams.length % 2 == 0) {
                    for (int z = 0; z < conversionParams.length; z = z + 2) {
                        paramMap.put(conversionParams[z], conversionParams[z + 1]);
                    }
                } else {
                    throw new Exception("Invalid length of String arguments");
                }
            }

            MODES mode = MODES.valueOf(paramMap.get("mode"));
            switch(mode) {
                case convertToImages:
                    ConvertPagesToImages.writeAllPagesAsImagesToDir(
                            userPdfFilePath,
                            outputDirStr + fileSeparator + fileNameWithoutExt + fileSeparator,
                            paramMap.get("format"),
                            Float.parseFloat(paramMap.getOrDefault("scaling", "1.0")));
                    break;
                case extractText:
                    ExtractStructuredText checkForStructure = new ExtractStructuredText(userPdfFilePath);
                    if (checkForStructure.openPDFFile()) {
                        Document content = checkForStructure.getStructuredTextContent();
                        if (content != null && content.hasChildNodes() && content.getDocumentElement().hasChildNodes()) {
                            ExtractStructuredText.writeAllStructuredTextOutlinesToDir(
                                    userPdfFilePath,
                                    outputDirStr + fileSeparator + fileNameWithoutExt + fileSeparator);
                        } else {
                            ExtractTextInRectangle.writeAllTextToDir(
                                    userPdfFilePath,
                                    outputDirStr + fileSeparator,
                                    -1);
                        }
                    }
                    break;
                case extractWordlist:
                    ExtractTextAsWordlist.writeAllWordlistsToDir(
                            userPdfFilePath,
                            outputDirStr + fileSeparator,
                            -1);
                    break;
                default:
                    throw new Exception("Invalid extraction type specified");
            }

            ZipHelper.zipFolder(outputDirStr + fileSeparator + fileNameWithoutExt,
                    outputDirStr + fileSeparator + fileNameWithoutExt + ".zip");

            final String outputPathInDocroot = individual.getUuid() + htmlPathSeparator + fileNameWithoutExt;
            individual.setValue("downloadUrl", contextUrl + htmlPathSeparator +"output" + htmlPathSeparator + outputPathInDocroot + ".zip");

            individual.setState("processed");

        } catch (final Exception ex) {
            LOG.log(Level.SEVERE, "Exception thrown when trying to convert file", ex);
            individual.setState("error");
        }
    }

    private void validateSettings(String[] settings) throws IllegalArgumentException {

        final StringBuilder errorMessage = new StringBuilder();

        if (settings != null) {
            final HashMap<String, String> paramMap = new HashMap<>();
            for (int z = 0; z < settings.length; z = z + 2) {
                paramMap.put(settings[z], settings[z + 1]);
            }

            final String modeMessage = checkStringSetting(paramMap, "mode", Stream.of(MODES.values()).map(MODES::name).toArray(String[]::new), true);
            if (modeMessage.length() == 0) {
                MODES mode = MODES.valueOf(paramMap.get("mode"));
                paramMap.remove("mode");
                switch (mode) {
                    case convertToImages: {
                            final String[][] encoderFormats = SupportedFormats.getSupportedImageEncoders();
                            ArrayList<String> fullList = new ArrayList<>();
                            for (String[] exts : encoderFormats) {
                                fullList.addAll(Arrays.asList(exts));
                            }
                            errorMessage.append(checkStringSetting(paramMap, "format", fullList.toArray(new String[0]), true));
                            paramMap.remove("format");
                            errorMessage.append(checkFloatSetting(paramMap, "scaling", new float[]{0.1f, 10}, false));
                            paramMap.remove("scaling");
                        }
                        break;
                    case extractText:
                    case extractWordlist:
                        break;
                }

                if (paramMap.size() > 0) {
                    errorMessage.append("The following settings were not recognised.\n");
                    final Set<String> keys = paramMap.keySet();
                    for (String key : keys) {
                        errorMessage.append("    ").append(key).append('\n');
                    }
                }

            } else {
                errorMessage.append(modeMessage);
            }
        } else {
            errorMessage.append("Settings value required to specify how the file will be processed.\n");
        }

        if (errorMessage.length() > 0) {
            throw new IllegalArgumentException(errorMessage.toString());
        }
    }

    private static String checkStringSetting(final HashMap<String, String> paramMap, final String setting, final String[] values, final boolean required) {
        final StringBuilder errorMessage = new StringBuilder();

        if (paramMap.containsKey(setting)) {
            //check valid
            final String value = paramMap.get(setting);
            if (!Arrays.asList(values).contains(value)) {
                if (required) {
                    errorMessage.append("Required ");
                } else {
                    errorMessage.append("Optional ");
                }
                errorMessage.append("setting \"").append(setting).append("\" has incorrect value. Valid options are ").append(Arrays.toString(values)).append('\n');
            }
        } else {
            if (required) {
                errorMessage.append("Required setting \"").append(setting).append("\" missing.\n");
            }
        }
        return errorMessage.toString();
    }

    private static String checkFloatSetting(final HashMap<String, String> paramMap, final String setting, final float[] range, final boolean required) {
        final StringBuilder errorMessage = new StringBuilder();

        if (paramMap.containsKey(setting)) {
            //check valid
            final String value = paramMap.get(setting);
            if (StringUtils.isNumber(value)) {
                final float fValue = Float.parseFloat(value);
                if (fValue < range[0] && range[1] < fValue) {
                    if (required) {
                        errorMessage.append("Required ");
                    } else {
                        errorMessage.append("Optional ");
                    }
                    errorMessage.append("setting \"").append(setting).append("\" has incorrect value. Valid values are between ").append(range[0]).append(" and ").append(range[1]).append(".\n");
                }
            }
        } else {
            if (required) {
                errorMessage.append("Required setting \"").append(setting).append("\" missing.\n");
            }
        }
        return errorMessage.toString();
    }

    /**
     * Set the error code in the given individual object. Error codes are based
     * on the return values of 
     * {@link JPedalServlet#convertToPDF(File)}
     *
     * @param individual the individual object associated with this conversion
     * @param errorCode The return code to be parsed to an error code
     */
    private void setErrorCode(final Individual individual, final int errorCode) {
        switch (errorCode) {
            case 1:
                individual.doError(1050); // Libreoffice killed after 1 minute
                break;
            case 2:
                individual.doError(1070); // Internal error
                break;
            default:
                individual.doError(1100); // Internal error
                break;
        }
    }

    /**
     * Converts an office file to PDF using LibreOffice.
     *
     * @param file The office file to convert to PDF
     * @return 0 if success, 1 if libreoffice timed out, 2 if process error
     * occurs
     */
    private static int convertToPDF(final File file) {
        final ProcessBuilder pb = new ProcessBuilder("soffice", "--headless", "--convert-to", "pdf", file.getName());
        pb.directory(new File(file.getParent()));
        final Process process;

        try {
            process = pb.start();
            if (!process.waitFor(1, TimeUnit.MINUTES)) {
                process.destroy();
                return 1;
            }
        } catch (final IOException | InterruptedException e) {
            e.printStackTrace(); // soffice location may need to be added to the path
            LOG.severe(e.getMessage());
            return 2;
        }
        return 0;
    }
}
