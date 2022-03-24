/*
 * JPedal Microservice Example
 *
 * Project Info: https://github.com/idrsolutions/jpedal-microservice-example
 *
 * Copyright 2022 IDRsolutions
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

import javax.servlet.ServletContextEvent;
import javax.servlet.annotation.WebListener;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebListener
public class JPedalServletContextListener extends BaseServletContextListener {

    private static final Logger LOG = Logger.getLogger(JPedalServletContextListener.class.getName());

    @Override
    public String getConfigPath() {
        String userDir = System.getProperty("user.home");
        if (!userDir.endsWith("/") && !userDir.endsWith("\\")) {
            userDir += System.getProperty("file.separator");
        }
        return userDir + "/.idr/jpedal-microservice/";
    }

    @Override
    public String getConfigName(){
        return "jpedal-microservice.properties";
    }

    @Override
    public void contextInitialized(final ServletContextEvent servletContextEvent) {
        super.contextInitialized(servletContextEvent);
        final Properties propertiesFile = (Properties) servletContextEvent.getServletContext().getAttribute(KEY_PROPERTIES);
        OutputFileServlet.setBasePath(propertiesFile.getProperty(KEY_PROPERTY_OUTPUT_PATH));
    }

    @Override
    protected void validateConfigFileValues(final Properties propertiesFile) {
        super.validateConfigFileValues(propertiesFile);

        validateLibreOfficePath(propertiesFile);
    }

    private static void validateLibreOfficePath(final Properties properties) {
        final String libreOfficePath = properties.getProperty(KEY_PROPERTY_LIBRE_OFFICE);
        if (libreOfficePath == null || libreOfficePath.isEmpty()) {
            properties.setProperty(KEY_PROPERTY_LIBRE_OFFICE, "soffice");
            LOG.log(Level.WARNING, "Properties value for \"libreOfficePath\" was not set. Using a value of \"soffice\"");
        }

    }
}
