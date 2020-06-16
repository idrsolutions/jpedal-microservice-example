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
import com.idrsolutions.microservice.utils.SettingsValidator;
import com.idrsolutions.microservice.utils.ZipHelper;
import org.jpedal.examples.images.ConvertPagesToImages;
import org.jpedal.examples.text.ExtractStructuredText;
import org.jpedal.examples.text.ExtractTextAsWordlist;
import org.jpedal.examples.text.ExtractTextInRectangle;
import org.w3c.dom.Document;

import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides an API to use JPedal on its own dedicated app server. See the API
 * documentation for more information on how to interact with this servlet.
 * 
 * @see BaseServlet
 */
@WebServlet(name = "jpedal", urlPatterns = {"/jpedal"})
@MultipartConfig
public class JPedalServlet extends BaseServlet {

    private enum Mode {
        convertToImages, extractText, extractWordlist
    }

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

        //Makes the directory for the output file
        new File(outputDirStr + fileSeparator + fileNameWithoutExt).mkdirs();

        individual.setState("processing");

        try {

            final HashMap<String, String> paramMap = new HashMap<>();
            if (conversionParams != null) { //handle string based parameters
                if (conversionParams.length % 2 == 0) {
                    for (int z = 0; z < conversionParams.length; z += 2) {
                        paramMap.put(conversionParams[z], conversionParams[z + 1]);
                    }
                } else {
                    throw new Exception("Invalid length of String arguments");
                }
            }

            final Mode mode;
            try {
                mode = Mode.valueOf(paramMap.remove("mode"));
            } catch (final IllegalArgumentException | NullPointerException e) {
                throw new Exception("Required setting \"mode\" is missing or has incorrect value. Valid values are " + Arrays.toString(Mode.values()) + '.');
            }

            validateSettings(paramMap, mode);

            convertPDF(mode, userPdfFilePath, outputDirStr, fileNameWithoutExt, paramMap);

            ZipHelper.zipFolder(outputDirStr + fileSeparator + fileNameWithoutExt,
                    outputDirStr + fileSeparator + fileNameWithoutExt + ".zip");

            final String outputPathInDocroot = individual.getUuid() + '/' + fileNameWithoutExt;
            individual.setValue("downloadUrl", contextUrl + "/output/" + outputPathInDocroot + ".zip");

            individual.setState("processed");

        } catch (final Exception ex) {
            LOG.log(Level.SEVERE, "Exception thrown when trying to convert file", ex);
            individual.setState("error");
        }
    }

    private static void validateSettings(final Map<String, String> paramMap, final Mode mode) throws Exception {
        final SettingsValidator settingsValidator = new SettingsValidator(paramMap);

        if (mode == Mode.convertToImages) {
            settingsValidator.requireString("format", validEncoderFormats);
            settingsValidator.optionalFloat("scaling", new float[]{0.1f, 10});
        }

        if (!settingsValidator.validates()) {
            throw new Exception(settingsValidator.getMessage());
        }
    }

    private static void convertPDF(final Mode mode, final String userPdfFilePath, final String outputDirStr,
                                   final String fileNameWithoutExt, final Map<String, String> paramMap) throws Exception {
        switch (mode) {
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
                throw new Exception("Unrecognised mode specified: " + mode.name());
        }
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
                individual.doError(1050, "Libreoffice timed out after 1 minute"); // Libreoffice killed after 1 minute
                break;
            case 2:
                individual.doError(1070, "Internal error processing file"); // Internal error
                break;
            default:
                individual.doError(1100, "An internal error has occured"); // Internal error
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
