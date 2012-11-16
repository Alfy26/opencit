/*
 * Copyright (C) 2012 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson.client.cmd;

import com.intel.mtwilson.ApiClient;
import com.intel.mtwilson.client.AbstractCommand;
import com.intel.mtwilson.datatypes.HostTrustResponse;
import com.intel.mtwilson.datatypes.Hostname;
import com.intel.mtwilson.io.Filename;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 *     HostTrustResponse getHostTrust(Hostname hostname) throws IOException, ApiException, SignatureException;
 * 
 * @author jbuhacoff
 */
public class DecodeUsername extends AbstractCommand {

    @Override
    public void execute(String[] args) throws Exception {
        if( args.length < 2 ) {
            throw new IllegalArgumentException("Usage: DecodeUsername alias | RsaCommand DecodeUsername -  (and supply username on stdin)");
        }
        if( args[1].equals("-") ) {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            System.out.println(Filename.decode(in.readLine()));
            in.close();
        }
        else {
            System.out.println(Filename.decode(args[1])); // XXX doesn't work.  "hello world" becomes "hello" instead of "hello%xxworld"
        }
    }

}
