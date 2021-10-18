/*
 * JPedal Microservice Example
 *
 * Project Info: https://github.com/idrsolutions/jpedal-microservice-example
 *
 * Copyright 2021 IDRsolutions
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
import org.jpedal.exception.PdfException;
import org.w3c.dom.Document;

import javax.json.stream.JsonParsingException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jpedal.examples.images.ExtractClippedImages;
import org.jpedal.examples.images.ExtractImages;

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
     * @param individual The individual object associated with this conversion
     * @param params The map of parameters that came with the request
     * @param inputFile The input file
     * @param outputDir The output directory of the converted file
     * @param contextUrl The context that this servlet is running in
     */
    @Override
    protected void convert(Individual individual, Map<String, String[]> params,
                           File inputFile, File outputDir, String contextUrl) {

        final Map<String, String> conversionParams = individual.getSettings() != null
                ? individual.getSettings() : new HashMap<>();

        final String fileName = inputFile.getName();
        final String ext = fileName.substring(fileName.lastIndexOf(".") + 1);
        final String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
        // To avoid repeated calls to getParent() and getAbsolutePath()
        final String inputDir = inputFile.getParent();
        final String outputDirStr = outputDir.getAbsolutePath();
        
        final String userPdfFilePath;

        final boolean isPDF = ext.toLowerCase().endsWith("pdf");
        if (!isPDF) {
            if (!convertToPDF(inputFile, individual)) {
                return;
            }
            userPdfFilePath = inputDir + fileSeparator + fileNameWithoutExt + ".pdf";
            final File userPdfFile = new File(userPdfFilePath);
            if (!userPdfFile.exists()) {
                LOG.log(Level.SEVERE, "LibreOffice error found while converting to PDF: " + userPdfFile.getAbsolutePath());
                individual.doError(1080, "Error processing file");
                return;
            }
        } else {
            userPdfFilePath = inputDir + fileSeparator + fileName;
        }

        //Makes the directory for the output file
        new File(outputDirStr + fileSeparator + fileNameWithoutExt).mkdirs();

        individual.setState("processing");

        try {

            final Mode mode;
            try {
                mode = Mode.valueOf(conversionParams.remove("mode"));
            } catch (final IllegalArgumentException | NullPointerException e) {
                throw new JPedalServletException("Required setting \"mode\" has incorrect value. Valid values are "
                        + Arrays.toString(Mode.values()) + '.');
            }

            convertPDF(mode, userPdfFilePath, outputDirStr, fileNameWithoutExt, conversionParams);

            ZipHelper.zipFolder(outputDirStr + fileSeparator + fileNameWithoutExt,
                    outputDirStr + fileSeparator + fileNameWithoutExt + ".zip");

            final String outputPathInDocroot = individual.getUuid() + '/' + fileNameWithoutExt;
            individual.setValue("downloadUrl", contextUrl + "/output/" + outputPathInDocroot + ".zip");

            individual.setState("processed");

        } catch (final Throwable ex) {
            LOG.log(Level.SEVERE, "Exception thrown when converting input", ex);
            individual.doError(1220, "Exception thrown when converting input" + ex.getMessage());
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
     * @param individual the individual belonging to this conversion
     * @return true if the settings are parsed and validated successfully, false if not
     */
    @Override
    protected boolean validateRequest(final HttpServletRequest request, final HttpServletResponse response,
                                      final Individual individual) {

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

        individual.setSettings(settings);

        return true;
    }

    private static void convertPDF(final Mode mode, final String userPdfFilePath, final String outputDirStr,
                                   final String fileNameWithoutExt, final Map<String, String> paramMap) throws Exception {
        switch (mode) {
            case convertToImages:
                ConvertPagesToImages.writeAllPagesAsImagesToDir(
                        userPdfFilePath,
                        outputDirStr + fileSeparator + fileNameWithoutExt + fileSeparator,
                        paramMap.get("format"),
                        Float.parseFloat(paramMap.getOrDefault("scaling", "1.0")),
                        paramMap.get("password"));
                break;
                case extractImages:{
                final String type = paramMap.get("type");
                switch (type) {
                    case "rawImages" :
                        ExtractImages.writeAllImagesToDir(
                                userPdfFilePath,
                                paramMap.get("password"),
                                outputDirStr + fileSeparator + fileNameWithoutExt + fileSeparator ,
                                paramMap.get("format"), true, true);
                        break;
                    case "clippedImages" :
                        ExtractClippedImages.writeAllClippedImagesToDirs(userPdfFilePath,
                                paramMap.get("password"),
                                outputDirStr + fileSeparator,
                                paramMap.get("format"),new String[]{"0",fileNameWithoutExt});
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
                                -1);
                        break;
                    case "wordlist" :
                        ExtractTextAsWordlist.writeAllWordlistsToDir(
                                userPdfFilePath,
                                paramMap.get("password"),
                                outputDirStr + fileSeparator,
                                -1);
                        break;
                    case "structuredText" :
                        ExtractStructuredText checkForStructure = new ExtractStructuredText(userPdfFilePath);
                        if (checkForStructure.openPDFFile()) {
                            Document content = checkForStructure.getStructuredTextContent();
                            if (content != null && content.hasChildNodes() && content.getDocumentElement().hasChildNodes()) {
                                ExtractStructuredText.writeAllStructuredTextOutlinesToDir(
                                        userPdfFilePath,
                                        paramMap.get("password"),
                                        outputDirStr + fileSeparator + fileNameWithoutExt + fileSeparator);
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

    /**
     * Converts an office file to PDF using LibreOffice.
     *
     * @param file The office file to convert to PDF
     * @param individual The Individual on which to set the error if one occurs
     * @return true on success, false on failure
     */
    private static boolean convertToPDF(final File file, final Individual individual) {
        final String uuid = individual.getUuid();
        final String uniqueLOProfile = TEMP_DIR.replace('\\', '/') + "LO-" + uuid;

        final ProcessBuilder pb = new ProcessBuilder("soffice",
                "-env:UserInstallation=file:///" + uniqueLOProfile + "/",
                "--headless", "--convert-to", "pdf", file.getName());

        pb.directory(new File(file.getParent()));

        try {
            final Process process = pb.start();
            if (!process.waitFor(1, TimeUnit.MINUTES)) {
                process.destroy();
                individual.doError(1050, "Libreoffice timed out after 1 minute");
                return false;
            }
        } catch (final IOException | InterruptedException e) {
            e.printStackTrace(); // soffice location may need to be added to the path
            LOG.severe(e.getMessage());
            individual.doError(1070, "Internal error processing file");
            return false;
        } finally {
            deleteFolder(new File(uniqueLOProfile));
        }
        return true;
    }

    static class JPedalServletException extends Exception {
        JPedalServletException(final String msg) {
            super(msg);
        }
    }
}
