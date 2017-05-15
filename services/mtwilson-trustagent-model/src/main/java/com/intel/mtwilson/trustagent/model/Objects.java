package com.intel.mtwilson.trustagent.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;


@JacksonXmlRootElement(localName="Objects")
public class Objects {
	private String Hash;
	private String pcrindex;
	private String eventtype;
	private String eventdata;
	private String eventdigest;

	public String getHash() {
		return Hash;
	}
	public String getPcrindex() {
		return pcrindex;
	}
	public String getEventtype() {
		return eventtype;
	}
	public String getEventdata() {
		return eventdata;
	}
	public String getEventdigest() {
		return eventdigest;
	}
	public void setHash(String Hash) {
		this.Hash=Hash;
	}
	public void setPcrindex(String pcrindex) {
		this.pcrindex=pcrindex;
	}
	public void setEventtype(String eventtype) {
		this.eventtype=eventtype;
	}
	public void setEventdata (String eventdata) {
		this.eventdata=eventdata;
	}
	public void setEventdigest (String eventdigest) {
		this.eventdigest=eventdigest;
	}
}
