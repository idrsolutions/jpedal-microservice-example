package com.idrsolutions.microservice;

import javax.servlet.ServletContextEvent;
import javax.servlet.annotation.WebListener;

@WebListener
public class JPedalServletContextListener extends BaseServletContextListener {

    @Override
    public void contextInitialized(final ServletContextEvent servletContextEvent) {
        String userDir = System.getProperty("user.home");
        if (!userDir.endsWith("/") && !userDir.endsWith("\\")) {
            userDir += System.getProperty("file.separator");
        }
        propertiesFile = userDir + "/.idr/jpedal-microservice/jpedal-microservice.properties";

        super.contextInitialized(servletContextEvent);
    }

}
