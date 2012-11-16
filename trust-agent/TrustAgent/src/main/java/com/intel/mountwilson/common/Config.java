/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intel.mountwilson.common;

import java.io.File;
import java.io.FileNotFoundException;


import org.apache.commons.configuration.Configuration;
import org.slf4j.LoggerFactory;



/**
 *
 * @author dsmagadX
 */
public class Config {
    
    private static Configuration config = TAConfig.getConfiguration();
    private static Config instance = null;
    //private static String appPath = config.getString("app.path"); // System.getProperty("app.path",".");;
    private static Boolean debug;

    private static String homeFolder = "./config";

    public static String getHomeFolder() {
		return homeFolder;
	}


	public static void setHomeFolder(String homeFolder) {
		Config.homeFolder = homeFolder;
	}



	static{
		File propFile;
		try {
			propFile = TAConfig.getFile("trustagent.properties");
			homeFolder = propFile.getAbsolutePath();
			homeFolder = homeFolder.substring(0,homeFolder.indexOf("trustagent.properties"));
			LoggerFactory.getLogger(Config.class.getName()).warn("Home folder. Using " + homeFolder);
		} catch (FileNotFoundException e) {
			LoggerFactory.getLogger(Config.class.getName()).warn("Could Not find the home folder. Using " + homeFolder);
		}
    }
    
    public static boolean isDebug() {
        if( debug == null ) {
            debug =  config.getString("debug").equalsIgnoreCase("true");
        }
        return debug;
    }
    
    
    private Config() {
    }
    
    public static Config getInstance() {
        if(instance == null){
            instance = new Config();
        }
        
        return instance;
    }
    
    public String getProperty(String property){
        if( config.containsKey(property) ) {
            return config.getString(property);
        }
        else {
            LoggerFactory.getLogger(Config.class.getName()).warn("Property {0} missing in config file.", property);
            return null;
        }
    }
    
    
     
    public static String getAppPath(){
        return config.getString("app.path");
    }
    
    
    
    public static String getBinPath() {
        return getAppPath() + File.separator + "bin";
    }
    
    
}
