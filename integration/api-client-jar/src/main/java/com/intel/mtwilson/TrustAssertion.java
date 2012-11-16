/*
 * Copyright (C) 2011-2012 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson;

import com.intel.mtwilson.crypto.RsaUtil;
import com.intel.mtwilson.crypto.SamlUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.opensaml.DefaultBootstrap;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.Statement;
import org.opensaml.xml.Configuration;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.io.Unmarshaller;
import org.opensaml.xml.io.UnmarshallerFactory;
import org.opensaml.xml.io.UnmarshallingException;
import org.opensaml.xml.schema.XSAny;
import org.opensaml.xml.schema.XSString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.apache.commons.codec.binary.Base64;

/**
 * This class extracts trust information from a SAML assertion.
 * 
 * Before using the assertions contained within a TrustAssertion object, you
 * must call isValid() to find out if the provided assertion is valid. If it
 * is, you can call getSubject(), getIssuer(), getAttributeNames(), and
 * getStringAttribute(), etc. 
 * If isValid() returns false, you can call error() to get the Exception object
 * that describes the validation error.
 * 
 * See also http://ws.apache.org/wss4j/config.html 
 * 
 * @author jbuhacoff
 */
public class TrustAssertion {
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private Assertion assertion;
    private HashMap<String,String> assertionMap;
    private boolean isValid;
    private Exception error;
    
    /**
     * Trusted SAML-signing certificates in the keystore must be marked for
     * this trusted purpose with the tag "(saml)" or "(SAML)" at the end of
     * their alias.
     * 
     * @param trustedSigners keystore with at least one trusted certificate with the "(saml)" tag in its alias
     * @param xml returned from attestation service
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     * @throws UnmarshallingException
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws MarshalException
     * @throws XMLSignatureException
     * @throws KeyStoreException 
     */
    public TrustAssertion(X509Certificate[] trustedSigners, String xml) {
        try {
            // is the xml signed by a trusted signer?
            Element document = readXml(xml);
            SamlUtil verifier = new SamlUtil(); // ClassNotFoundException, InstantiationException, IllegalAccessException
            boolean isVerified = verifier.verifySAMLSignature(document, trustedSigners);
            if( isVerified ) {
                log.debug("Validated signature in xml document");
                // populate assertions map
                DefaultBootstrap.bootstrap(); // required to load default configs that ship with opensaml that specify how to build and parse the xml (if you don't do this you will get a null unmarshaller when you try to parse xml)
                assertion = readAssertion(document); // ParserConfigurationException, SAXException, IOException, UnmarshallingException
                assertionMap = new HashMap<String,String>();        
                populateAssertionMap();
                isValid = true;
                error = null;
            }
            else {
                throw new IllegalArgumentException("Cannot verify XML signature");
            }
        }
        catch(Exception e) {
            isValid = false;
            error = e;
            assertion = null;
            assertionMap = null;
        }
    }
    
    public boolean isValid() {
        return isValid;
    }
    
    /**
     * 
     * @return null if assertion is valid, otherwise an exception object describing the error
     */
    public Exception error() { return error; }
    
    /**
     * 
     * @return the OpenSAML Assertion object, or null if there was an error
     */
    public Assertion getAssertion() {
        return assertion;
    }
    
    /**
     * @return the assertion's issue instant
     * @since 0.5.3
     */
    public Date getDate() {
        return assertion.getIssueInstant().toDate();
    }
    
    /**
     * 
     * @return the assertion subject's AIK public key
     * @throws NullPointerException if isValid() == false
     */
    public String getSubject() {
        return assertion.getSubject().getNameID().getValue();
    }
    
    /**
     * 
     * @return the assertion subject's format
     * @throws NullPointerException if isValid() == false
     */
    public String getSubjectFormat() {
        return assertion.getSubject().getNameID().getFormat();
    }

    /**
     * 
     * @return the assertion issuer
     * @throws NullPointerException if isValid() == false
     */
    public String getIssuer() {
        return assertion.getIssuer().getValue();
    }
    
    /**
     * 
     * @return a set of the available attribute names in the assertion
     * @throws NullPointerException if isValid() == false
     */
    public Set<String> getAttributeNames() {
        HashSet<String> names = new HashSet<String>();
        names.addAll(assertionMap.keySet());
        return names;
    }
    
    /**
     * 
     * @param name 
     * @return the value of the named attribute
     * @throws NullPointerException if isValid() == false
     */
    public String getStringAttribute(String name) {
        return assertionMap.get(name);
    }
    /*
    public Boolean getBooleanAttribute(String name) {
        String value = assertionMap.get(name);
        if( value == null ) { 
            return null;
        }
        return Boolean.valueOf(value);
    }*/
    
    public X509Certificate getAikCertificate() throws CertificateException {
        String pem = assertionMap.get("AIK_Certificate");
        X509Certificate cert = RsaUtil.toX509Certificate(pem);
        return cert;
    }
    
    private Element readXml(String xml) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory =  DocumentBuilderFactory.newInstance ();
        factory.setNamespaceAware (true);
        DocumentBuilder builder = factory.newDocumentBuilder(); // ParserConfigurationException
        ByteArrayInputStream in = new ByteArrayInputStream(xml.getBytes());
        Element document = builder.parse(in).getDocumentElement (); // SAXException, IOException
        in.close(); // IOExeception
        return document;
    }

    private Assertion readAssertion(Element document) throws UnmarshallingException {
        log.debug("Reading assertion from element {}", document.getTagName());
        UnmarshallerFactory factory = Configuration.getUnmarshallerFactory();
        Unmarshaller unmarshaller = factory.getUnmarshaller(document);
        XMLObject xml = unmarshaller.unmarshall(document); // UnmarshallingException
        Assertion samlAssertion = (Assertion) xml;
        return samlAssertion;
    }
    
    /**
     * Sample assertion statements that may appear in the XML:
     * Trusted (boolean)
     * Trusted_BIOS (boolean)
     * Trusted_VMM (boolean)
     * BIOS_Name (string)
     * BIOS_Version (string)
     * BIOS_OEM (string)
     * VMM_Name (string)
     * VMM_Version (string)
     * VMM_OSName (string)
     * VMM_OSVersion (string)
     * The BIOS_* entries will only appear if Trusted_BIOS is true
     * The VMM_* entries will only appear if Trusted_VMM is true
     */
    private void populateAssertionMap() {
        for (Statement statement : assertion.getStatements ()) {
            if (statement instanceof AttributeStatement) {
                for (Attribute attribute : 
                        ((AttributeStatement) statement).getAttributes ())
                {
                    String attributeValue = null;
                    // XXX TODO currently this only grabs the last value if there was more than one value in the attribute... full implementation should handle all possibilities but we do provide a getAssertion() function so the client can navigate the assertion tree directly in case they need something not covered here
                    for (XMLObject value : attribute.getAttributeValues ()) {
                        if (value instanceof XSAny) {
                            attributeValue = (((XSAny) value).getTextContent()); // boolean attributes are the text "true" or "false"
                        }
                        if( value instanceof XSString ) {
                            attributeValue = (((XSString) value).getValue()); 
                        }
                    }
                    assertionMap.put(attribute.getName(), attributeValue);
                }
            }
        }
    }
    
}
