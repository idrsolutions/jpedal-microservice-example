package com.idrsolutions.microservice;

import javax.servlet.annotation.WebListener;

@WebListener
public class JPedalServletContextListener extends BaseServletContextListener {

    public String getConfigPath() {
        String userDir = System.getProperty("user.home");
        if (!userDir.endsWith("/") && !userDir.endsWith("\\")) {
            userDir += System.getProperty("file.separator");
        }
        return userDir + "/.idr/jpedal-microservice/";
    }

    public String getConfigName(){
        return "jpedal-microservice.properties";
    }
}
