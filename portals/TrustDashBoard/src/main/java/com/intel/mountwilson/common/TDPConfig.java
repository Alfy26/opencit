/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intel.mountwilson.common;

import com.intel.mtwilson.util.ConfigBase;
import java.util.Properties;
import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Yuvrajx
 */
public class TDPConfig extends ConfigBase {

    private static final Logger log = LoggerFactory.getLogger(TDPConfig.class);
    private static final TDPConfig global = new TDPConfig();
    
    public static Configuration getConfiguration() { return global.getConfigurationInstance(); }
        
    private TDPConfig() {
        super("trust-dashboard.properties", getDefaults());
    }

    private static Properties getDefaults() {
        Properties defaults = new Properties();
        
        // Properties for the API Client
        defaults.setProperty("mtwilson.api.baseurl", "https://127.0.0.1:8181");
        defaults.setProperty("mtwilson.api.ssl.verifyHostname", "true"); // must be secure out of the box.  TODO: installer should generate an ssl cert for glassfish that matches the url that will be used to access it.
        defaults.setProperty("mtwilson.api.ssl.requireTrustedCertificate", "true");  // must be secure out of the box. user registration process should download server ssl certs
        
        
        // Trust Dashboard Portal specific properties 
        defaults.setProperty("mtwilson.tdbp.sessionTimeOut", "1800");
        defaults.setProperty("mtwilson.tdbp.paginationRowCount", "10");
        defaults.setProperty("mtwilson.tdbp.keystore.dir", "/var/opt/intel/trust-dashboard/users"); // XXX TODO make a linux default and windows default, utiilizing some centralized configuration functions suh as getDataDirectory() which would already provide an os-specific directory that has already been created (or with a function to create it)
        
        return defaults;
                
	}

    // for troubleshooting
    @Override
    public void dumpConfiguration(Configuration c, String label) {
        String keys[] = new String[] { "mtwilson.api.baseurl", "mtwilson.tdbp.keystore.dir", "mtwilson.tdbp.sessionTimeOut" };
        for(String key : keys) {
            String value = c.getString(key);
            log.info(String.format("TrustDashBoardConfig [%s]: %s=%s", label, key, value));  
        }
    }
    
}

