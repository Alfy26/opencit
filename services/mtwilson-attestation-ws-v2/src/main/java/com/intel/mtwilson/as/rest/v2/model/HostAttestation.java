/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intel.mtwilson.as.rest.v2.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.intel.mtwilson.datatypes.HostTrustResponse;
import com.intel.mtwilson.jersey.Document;
import com.intel.mtwilson.policy.HostReport;
import com.intel.mtwilson.policy.TrustReport;

/**
 *
 * @author ssbangal
 */
@JacksonXmlRootElement(localName="host_attestation")
public class HostAttestation extends Document {
    
    private String hostUuid;
    private String hostName;
    private String aikSha1;
    private String challenge;
    private TrustReport trustReport;
    private HostTrustResponse hostTrustResponse;
    // TODO: Add the capability to send back the complete TrustReport even in the case of GET operation (for the 2.x release)

    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getAikSha1() {
        return aikSha1;
    }

    public void setAikSha1(String aikSha1) {
        this.aikSha1 = aikSha1;
    }

    public String getChallenge() {
        return challenge;
    }

    public void setChallenge(String challenge) {
        this.challenge = challenge;
    }

    public TrustReport getTrustReport() {
        return trustReport;
    }

    public void setTrustReport(TrustReport trustReport) {
        this.trustReport = trustReport;
    }

    public HostTrustResponse getHostTrustResponse() {
        return hostTrustResponse;
    }

    public void setHostTrustResponse(HostTrustResponse hostTrustResponse) {
        this.hostTrustResponse = hostTrustResponse;
    }
    
    
}
