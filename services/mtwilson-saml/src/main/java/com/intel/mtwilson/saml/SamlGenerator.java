/*
 * Copyright (C) 2011-2012 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson.saml;

import com.intel.dcsg.cpg.configuration.CommonsConfigurationAdapter;
import com.intel.mtwilson.atag.model.AttributeOidAndValue;
import com.intel.mtwilson.datatypes.TxtHost;
import com.intel.dcsg.cpg.io.Resource;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.*;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.XMLSignatureException;
import org.joda.time.DateTime;
import org.opensaml.Configuration;
import org.opensaml.DefaultBootstrap;
import org.opensaml.common.SAMLObjectBuilder;
import org.opensaml.common.SAMLVersion;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.AttributeValue;
import org.opensaml.saml2.core.Issuer;
import org.opensaml.saml2.core.NameID;
import org.opensaml.saml2.core.Subject;
import org.opensaml.saml2.core.SubjectConfirmation;
import org.opensaml.saml2.core.SubjectConfirmationData;
import org.opensaml.saml2.core.impl.AssertionMarshaller;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.XMLObjectBuilder;
import org.opensaml.xml.XMLObjectBuilderFactory;
import org.opensaml.xml.io.MarshallingException;
import org.opensaml.xml.schema.XSString;
import org.opensaml.xml.schema.XSAny;
import org.opensaml.xml.util.XMLHelper;
import org.w3c.dom.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * When we respond with an assertion, if we want to prevent caching we should include these headers:
 * Cache-Control: no-cache, no-store, must-revalidate, private
 * Pragma: no-cache
 * But there is no harm in the client caching the attestation results for as long as THEY feel comfortable with it.
 * 
 * @author jbuhacoff
 */
public class SamlGenerator {
    private Logger log = LoggerFactory.getLogger(getClass());
    private static XMLObjectBuilderFactory builderFactory;
    private String issuerName; // for example, http://1.2.3.4/AttestationService
    private String issuerServiceName; // for example "AttestationService"
    private Integer validitySeconds; // for example 3600 for one hour
    private SAMLSignature signatureGenerator = null;
    private SamlAssertion samlAssertion = null;
//    private Resource keystoreResource = null;
    
    /**
     * Compatibility constructor 
     * @param keystoreResource  ignored
     * @param configuration  commons-configuration object 
     * @throws ConfigurationException 
     */
    public SamlGenerator(Resource keystoreResource, org.apache.commons.configuration.Configuration configuration) throws ConfigurationException {
        this(new CommonsConfigurationAdapter(configuration));
    }
    
    /**
     * Configuration keys:
     * saml.issuer=http://1.2.3.4/AttestationService          # used in SAML
     * saml.validity.seconds=3600 # 3600 seconds = 1 hour
     * saml.keystore.file=C:/Intel/CloudSecurity/SAML.jks         # path for keystore file (absolute or relative to the intel/cloudsecurity folder)
     * saml.keystore.password=password         # password for keystore file
     * saml.key.alias=forSigning           # alias of the signing key in the keystore file
     * saml.key.password=password           # password of the signing key
     * jsr105Provider=org.jcp.xml.dsig.internal.dom.XMLDSigRI # SAML XML signature provider
     * keystore.path=.            # disk path to SAML keystore (currently ignored)
     * 
     * 
     * @param configuration with the keys described
     * @throws ConfigurationException 
     */
    public SamlGenerator(com.intel.dcsg.cpg.configuration.Configuration configuration /*Resource keystoreResource, org.apache.commons.configuration.Configuration configuration*/) throws ConfigurationException {
        builderFactory = getSAMLBuilder();
        try {
            signatureGenerator = new SAMLSignature(/*keystoreResource,*/ configuration);
        } catch (ClassNotFoundException | KeyStoreException | NoSuchAlgorithmException | UnrecoverableEntryException | IllegalAccessException | InstantiationException | IOException | CertificateException ex) {
            log.error("Cannot load SAML signature generator: "+ex.getMessage(), ex);
        }
        setIssuer(configuration.getString("saml.issuer", "AttestationService"));
        setValiditySeconds(configuration.getInteger("saml.validity.seconds", 3600));
    }
    
    
    public final void setIssuer(String issuer) {
        this.issuerName = issuer;
        this.issuerServiceName = "AttestationService-0.5.4"; // TODO make this configurable ??? 
    }
    
    /**
     * 
     * @param seconds number of seconds or null to not set any expiration
     */
    public final void setValiditySeconds(Integer seconds) {
        this.validitySeconds = seconds;
    }
    
    /*
    public void setKeystoreResource(Resource keystoreResource) {
        this.keystoreResource = keystoreResource;
    }*/
    
    /**
     * Input is a Host record with all the attributes to assert
     * Output is XML containing the SAML assertions
     * 
     * From /hosts/trust we get BIOS:1,VMM:1
     * From /hosts/location we get location
     * From /pollhosts we get trust level "unknown/untrusted/trusted" and timestamp
     * From /hosts/reports/trust we get host name, mle info string, created on, overall trust status, and verified on
     * From /hosts/reports/manifest we get PCR values, trust status, and verified on for each PCR
     * 
     * @return @SamlAssertion
     * @throws MarshallingException 
     */
    public SamlAssertion generateHostAssertion(TxtHost host, ArrayList<AttributeOidAndValue> atags) throws MarshallingException, ConfigurationException, UnknownHostException, GeneralSecurityException, XMLSignatureException, MarshalException {
        samlAssertion = new SamlAssertion();
        Assertion assertion = createAssertion(host, atags);

        AssertionMarshaller marshaller = new AssertionMarshaller();
        Element plaintextElement = marshaller.marshall(assertion);
        
        String originalAssertionString = XMLHelper.nodeToString(plaintextElement);
        System.out.println("Assertion String: " + originalAssertionString);

        // add signatures and/or encryption
        signAssertion(plaintextElement);
        
        samlAssertion.assertion =  XMLHelper.nodeToString(plaintextElement);
        System.out.println("Signed Assertion String: " + samlAssertion.assertion );
        // TODO: now you can also add encryption....
        
        
        return samlAssertion;
    }
    
    /**
     * Generates a multi-host SAML assertion which contains an AttributeStatement
     * for each host containing a Host_Address attribute with the host IP address
     * or hostname and the trust attributes as for a single-host assertion.
     * The Subject of the multi-host SAML assertion should not be used because
     * it is simply the collection hosts in the assertion and no statements
     * are made about the collection as a whole.
     * 
     * @param hosts
     * @return
     * @throws SamlException 
     */
    public SamlAssertion generateHostAssertions(Collection<TxtHostWithAssetTag> hosts) throws SamlException {
        try {
            samlAssertion = new SamlAssertion();
            Assertion assertion = createAssertion(hosts);

            AssertionMarshaller marshaller = new AssertionMarshaller();
            Element plaintextElement = marshaller.marshall(assertion);

            String originalAssertionString = XMLHelper.nodeToString(plaintextElement);
            System.out.println("Assertion String: " + originalAssertionString);

            // add signatures and/or encryption
            signAssertion(plaintextElement);

            samlAssertion.assertion =  XMLHelper.nodeToString(plaintextElement);
            System.out.println("Signed Assertion String: " + samlAssertion.assertion );
            // TODO: now you can also add encryption....


            return samlAssertion;
        }
        catch(Exception e) {
            throw new SamlException(e);
        }
    }
 
	public static XMLObjectBuilderFactory getSAMLBuilder() throws ConfigurationException{
 
		if(builderFactory == null){
			// OpenSAML 2.3
			 DefaultBootstrap.bootstrap();
	         builderFactory = Configuration.getBuilderFactory();
		}
 
		return builderFactory;
	}
 
 
        // create the issuer
        
        private Issuer createIssuer() {
            // Create Issuer
            SAMLObjectBuilder issuerBuilder = (SAMLObjectBuilder)  builderFactory.getBuilder(Issuer.DEFAULT_ELEMENT_NAME);
            Issuer issuer = (Issuer) issuerBuilder.buildObject();
            issuer.setValue(this.issuerName);
            return issuer;
        }
        // create the Subject Name
        
        private NameID createNameID(String hostName) {
            // Create the NameIdentifier
            SAMLObjectBuilder nameIdBuilder = (SAMLObjectBuilder) builderFactory.getBuilder(NameID.DEFAULT_ELEMENT_NAME);
            NameID nameId = (NameID) nameIdBuilder.buildObject();
            nameId.setValue(hostName);
//            nameId.setNameQualifier(input.getStrNameQualifier()); optional:  TODO should be linked to the security domain
            nameId.setFormat(NameID.UNSPECIFIED); // !!! CAN ALSO USE X509 SUBJECT FROM HOST CERTIFICATE instead of host name in database   
            return nameId;
        }
        private NameID createNameID(TxtHost host) {
            return createNameID(host.getHostName().toString());
        }

        
        // create the Subject and Subject Confirmation
        
        private SubjectConfirmation createSubjectConfirmation(TxtHost host) throws ConfigurationException, UnknownHostException {
            SAMLObjectBuilder subjectConfirmationBuilder = (SAMLObjectBuilder)  builderFactory.getBuilder(SubjectConfirmation.DEFAULT_ELEMENT_NAME);
            SubjectConfirmation subjectConfirmation = (SubjectConfirmation) subjectConfirmationBuilder.buildObject();
            subjectConfirmation.setMethod(SubjectConfirmation.METHOD_SENDER_VOUCHES); // XXX TODO !!! consider using the host's AIK Certificate name with "holder of key" method
            subjectConfirmation.setSubjectConfirmationData(createSubjectConfirmationData(host));
            // Create the NameIdentifier
            SAMLObjectBuilder nameIdBuilder = (SAMLObjectBuilder) builderFactory.getBuilder(NameID.DEFAULT_ELEMENT_NAME);
            NameID nameId = (NameID) nameIdBuilder.buildObject();
            nameId.setValue(issuerServiceName);
//            nameId.setNameQualifier(input.getStrNameQualifier()); optional:  TODO should be linked to the security domain
            nameId.setFormat(NameID.UNSPECIFIED); // !!! CAN ALSO USE X509 SUBJECT FROM HOST CERTIFICATE instead of host name in database   
            subjectConfirmation.setNameID(nameId);
            return subjectConfirmation;
        }
        
        /**
         * 
         * The SubjectConfirmationData element may be extended with custom information that we want to include, both as attributes or as child elements.
         * 
         * See also section 2.4.1.2 Element <SubjectConfirmationData> of http://docs.oasis-open.org/security/saml/v2.0/saml-core-2.0-os.pdf
         * 
         * @param host
         * @return
         * @throws ConfigurationException
         * @throws UnknownHostException 
         */
        private SubjectConfirmationData createSubjectConfirmationData(TxtHost host) throws ConfigurationException, UnknownHostException {
            SAMLObjectBuilder confirmationMethodBuilder = (SAMLObjectBuilder)  builderFactory.getBuilder(SubjectConfirmationData.DEFAULT_ELEMENT_NAME);
            SubjectConfirmationData confirmationMethod = (SubjectConfirmationData) confirmationMethodBuilder.buildObject();
            DateTime now = new DateTime();
            // Required to add to cache
            samlAssertion.created_ts = now.toDate();
            
            confirmationMethod.setNotBefore(now); // XXX TODO: should put the date that we actually got the record into our database here; 
            if( validitySeconds != null ) {
                confirmationMethod.setNotOnOrAfter(now.plusSeconds(validitySeconds));
                // Required to add to cache
                samlAssertion.expiry_ts = confirmationMethod.getNotOnOrAfter().toDate();
            }
            InetAddress localhost = InetAddress.getLocalHost();
            confirmationMethod.setAddress(localhost.getHostAddress()); // NOTE: This is the ATTESTATION SERVICE IP ADDRESS,  **NOT** THE HOST ADDRESS
            return confirmationMethod;
        }
        
        private Subject createSubject(TxtHost host) throws ConfigurationException, UnknownHostException {
            // Create the Subject
            SAMLObjectBuilder subjectBuilder = (SAMLObjectBuilder)  builderFactory.getBuilder(Subject.DEFAULT_ELEMENT_NAME);
            Subject subject = (Subject) subjectBuilder.buildObject();
            subject.setNameID(createNameID(host));
            subject.getSubjectConfirmations().add(createSubjectConfirmation(host));
            return subject;
        }
        
        // create the host attributes 
        
        /**
         * An attribute can be multi-valued, but this method builds a single-valued
         * String attribute such as FirstName=John or IPAddress=1.2.3.4
         * @param name
         * @param value
         * @return
         * @throws ConfigurationException 
         */
	private Attribute createStringAttribute(String name, String value) throws ConfigurationException {
            SAMLObjectBuilder attrBuilder = (SAMLObjectBuilder)  builderFactory.getBuilder(Attribute.DEFAULT_ELEMENT_NAME);
            Attribute attr = (Attribute) attrBuilder.buildObject();
            attr.setName(name);

            XMLObjectBuilder xmlBuilder =  builderFactory.getBuilder(XSString.TYPE_NAME);
            XSString attrValue = (XSString) xmlBuilder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME);
            attrValue.setValue(value);

            attr.getAttributeValues().add(attrValue);
            return attr;
	}

        /**
         * This method builds a single-valued boolean attribute such as isTrusted=true
         * @param name
         * @param value
         * @return
         * @throws ConfigurationException 
         */
	private Attribute createBooleanAttribute(String name, boolean value) throws ConfigurationException {
            SAMLObjectBuilder attrBuilder = (SAMLObjectBuilder)  builderFactory.getBuilder(Attribute.DEFAULT_ELEMENT_NAME);
            Attribute attr = (Attribute) attrBuilder.buildObject();
            attr.setName(name);

            XMLObjectBuilder xmlBuilder =  builderFactory.getBuilder(XSAny.TYPE_NAME);
            XSAny attrValue = (XSAny) xmlBuilder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSAny.TYPE_NAME);
            attrValue.setTextContent( value ? "true" : "false" );

            attr.getAttributeValues().add(attrValue);
            return attr;
	}
        
        /*  works but not needed
        private List<Attribute> createStringAttributes(Map<String,String> attributes) throws ConfigurationException {
            ArrayList<Attribute> list = new ArrayList<Attribute>();
            for(Map.Entry<String,String> e : attributes.entrySet()) {
                Attribute attr = createStringAttribute(e.getKey(), e.getValue());
                list.add(attr);
            }
            return list;
        }
        * 
        */

	// currently unused but probably works
	/*
	private Attribute createComplexAttribute(String name, String xmlValue) throws ConfigurationException {
            SAMLObjectBuilder attrBuilder = (SAMLObjectBuilder)  builderFactory.getBuilder(Attribute.DEFAULT_ELEMENT_NAME);
            Attribute attr = (Attribute) attrBuilder.buildObject();
            attr.setName(name);

            XMLObjectBuilder stringBuilder =  builderFactory.getBuilder(XSString.TYPE_NAME);
            XSAny attrValue = (XSAny) stringBuilder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSAny.TYPE_NAME);
            attrValue.setTextContent(xmlValue);

            attr.getAttributeValues().add(attrValue);
            return attr;
	}
	*/
        private final String DEFAULT_OID = "2.5.4.789.1";
        private AttributeStatement createHostAttributes(TxtHost host, ArrayList<AttributeOidAndValue> atags) throws ConfigurationException {
            // Builder Attributes
            SAMLObjectBuilder attrStatementBuilder = (SAMLObjectBuilder)  builderFactory.getBuilder(AttributeStatement.DEFAULT_ELEMENT_NAME);
            AttributeStatement attrStatement = (AttributeStatement) attrStatementBuilder.buildObject();
            // add host attributes (both for single host and multi-host assertions)
            attrStatement.getAttributes().add(createStringAttribute("Host_Name", host.getHostName().toString()));  // TODO:  need a revised object to replace TxtHost which combines hostname/ipaddress into one field,  and also if we are doing an anonymous assertion then this would be an AIK fingerprint and not a hostname at all
            attrStatement.getAttributes().add(createStringAttribute("Host_Address", host.getIPAddress()));  // TODO:  need a revised object to replace TxtHost which combines hostname/ipaddress into one field,  and also if we are doing an anonymous assertion then this would be an AIK fingerprint and not a hostname at all
//            attrStatement.getAttributes().add(createStringAttribute("Host_UUID", host.getUuid()));  // TODO:  need a revised object to replace TxtHost which includes the UUID of the host (arbitrary from our database) and the hardware UUID which we get from the host agent
//            attrStatement.getAttributes().add(createStringAttribute("Host_AIK_SHA1", host.getUuid()));  // TODO:  need a revised object to replace TxtHost which includes the UUID of the host (arbitrary from our database) and the hardware UUID which we get from the host agent
            

            // Create the attribute statements that are trusted
            attrStatement.getAttributes().add(createBooleanAttribute("Trusted", host.isBiosTrusted() && host.isVmmTrusted()));
            attrStatement.getAttributes().add(createBooleanAttribute("Trusted_BIOS", host.isBiosTrusted()));
            if( host.isBiosTrusted() ) {
                attrStatement.getAttributes().add(createStringAttribute("BIOS_Name", host.getBios().getName()));
                attrStatement.getAttributes().add(createStringAttribute("BIOS_Version", host.getBios().getVersion()));
                attrStatement.getAttributes().add(createStringAttribute("BIOS_OEM", host.getBios().getOem()));
            }
            attrStatement.getAttributes().add(createBooleanAttribute("Trusted_VMM", host.isVmmTrusted()));
            if( host.isVmmTrusted() ) {
                attrStatement.getAttributes().add(createStringAttribute("VMM_Name", host.getVmm().getName()));
                attrStatement.getAttributes().add(createStringAttribute("VMM_Version", host.getVmm().getVersion()));
                attrStatement.getAttributes().add(createStringAttribute("VMM_OSName", host.getVmm().getOsName()));
                attrStatement.getAttributes().add(createStringAttribute("VMM_OSVersion", host.getVmm().getOsVersion()));                
            }
            
            //attrStatement.getAttributes().add(createBooleanAttribute("Trusted_Location", host.isLocationTrusted()));
            //if( host.isLocationTrusted() ) {
            //    attrStatement.getAttributes().add(createStringAttribute("Location", host.getLocation()));            
            //}
            
            // add the asset tag attestation status and if the status is trusted, then add all the attributes. In order to uniquely
            // identify all the asset tags on the client side, we will just append the text ATAG for all of them.
            attrStatement.getAttributes().add(createBooleanAttribute("Asset_Tag", host.isAssetTagTrusted()));
            if( host.isAssetTagTrusted() && atags != null && !atags.isEmpty()) {
//                AssetTagCertBO certBO = new AssetTagCertBO();
                for (AttributeOidAndValue atagAttr : atags) {
                    String tagValue = atagAttr.getValue();
                    String tagName = "N/A";
                    log.debug("tag atrr OID = " + atagAttr.getOid() + " default OID = " + DEFAULT_OID);
                    if (! atagAttr.getOid().equalsIgnoreCase(DEFAULT_OID)) { 
                        log.error("Unsupported OID {}", atagAttr.getOid());
                        
                        // XXX  commenting out this code for now because we're going to limit asset tags
                        //      to our DEFAULT_OID defined as key/value pairs until we have better general
                        //      support for X509 OIDs  - and when we do, we won't need to query the 
                        //      asset tag service for anything because support for additional OIDs would
                        //      be in the form of plugins, because we will need code to interpret them
                        //      but all the necessary information will be embedded and we won't need 
                        //      to look anything up.
                        /*
                        // not the default oid that means value == key/value
                        // so we need to query the service and try and get the mapping from there
                        try {
                            TagDataType tag = certBO.getTagInfoByOID(atagAttr.getOid());
                            log.error("createHostAttributes found tag for oid " + atagAttr.getOid());
                            tagName = tag.name;
                        } catch (Exception ex) {
                          log.error("error getting tag attributes: " + ex.getMessage());
                          ex.printStackTrace();
                        }
                        */
                    }
                    // XXX TODO  change String.format("ATAG :"+atagAttr.getOid() + "[" + tagName + "]")  to something more general wherein we can encode the OIDs as is - 
                    // probaly string attribute is not the right thing to do here anymore, and the client will need the OID-parsing code too for anything that
                    // is not a key value pair.   Possibly for our DEFAULT_OID  we can keep the easy string attribute format  like String.format(ATAG_%s, tagName) , tagValue
                    attrStatement.getAttributes().add(createStringAttribute(String.format("ATAG :"+atagAttr.getOid() + "[" + tagName + "]"),tagValue));
                }
            }

            if( host.getAikCertificate() != null ) {
                attrStatement.getAttributes().add(createStringAttribute("AIK_Certificate", host.getAikCertificate()));
                attrStatement.getAttributes().add(createStringAttribute("AIK_SHA1", host.getAikSha1()));
            }
            else if( host.getAikPublicKey() != null ) {
                attrStatement.getAttributes().add(createStringAttribute("AIK_PublicKey", host.getAikPublicKey()));                
                attrStatement.getAttributes().add(createStringAttribute("AIK_SHA1", host.getAikSha1()));
            }
            
            return attrStatement;
            
        }

        /*
        private AttributeStatement createHostAttributes(TxtHost host, ManifestType pcrManifest) throws ConfigurationException {
            AttributeStatement attrStatement = createHostAttributes(host);
            attrStatement.getAttributes().add(createComplexAttribute("Manifest", pcrManifest);

            return attrStatement;
            
        }
        */
        
        /**
         * Creates an assertion with attributes of the host
         * 
         * ID attribute: see section 5.4.2  "References" of http://docs.oasis-open.org/security/saml/v2.0/saml-core-2.0-os.pdf
         * 
         * @param host
         * @return 
         */
        private Assertion createAssertion(TxtHost host, ArrayList<AttributeOidAndValue> atags) throws ConfigurationException, UnknownHostException {
            // Create the assertion
            SAMLObjectBuilder assertionBuilder = (SAMLObjectBuilder)  builderFactory.getBuilder(Assertion.DEFAULT_ELEMENT_NAME);
            Assertion assertion = (Assertion) assertionBuilder.buildObject();
            assertion.setID("HostTrustAssertion"); // ID is arbitrary, only needs to be unique WITHIN THE DOCUMENT, and is required so that the Signature element can refer to it, for example #HostTrustAssertion
            assertion.setIssuer(createIssuer());
            DateTime now = new DateTime();
            assertion.setIssueInstant(now);
            assertion.setVersion(SAMLVersion.VERSION_20);
            assertion.setSubject(createSubject(host));
            assertion.getAttributeStatements().add(createHostAttributes(host, atags));

            return assertion;
        }

        /**
         * Differences from createAssertion:
         * - the assertion ID is "MultipleHostTrustAssertion" instead of "HostTrustAssertion"
         * - there is no overall Subject for the assertion because it's for multiple host
         * - each host is identified with host attributes within its own attribute statement
         * 
         * @param hosts
         * @return
         * @throws ConfigurationException
         * @throws UnknownHostException 
         */
        private Assertion createAssertion(Collection<TxtHostWithAssetTag> hosts) throws ConfigurationException, UnknownHostException {
            // Create the assertion
            SAMLObjectBuilder assertionBuilder = (SAMLObjectBuilder)  builderFactory.getBuilder(Assertion.DEFAULT_ELEMENT_NAME);
            Assertion assertion = (Assertion) assertionBuilder.buildObject();
            assertion.setID("MultipleHostTrustAssertion"); // ID is arbitrary, only needs to be unique WITHIN THE DOCUMENT, and is required so that the Signature element can refer to it, for example #HostTrustAssertion
            assertion.setIssuer(createIssuer());
            DateTime now = new DateTime();
            assertion.setIssueInstant(now);
            assertion.setVersion(SAMLVersion.VERSION_20);
//            assertion.setSubject(createSubject(host));
            for(TxtHostWithAssetTag host : hosts) {
                assertion.getAttributeStatements().add(createHostAttributes(host.getHost(), host.getAtags()));            
            }

            return assertion;
        }
        
 
        private void signAssertion(Element assertion) throws GeneralSecurityException, XMLSignatureException, MarshalException {
            // Signature
            //   SignedInfo
            //     CanonicalizationMethod  Algorithm=http://www.w3.org/2001/10/xml-exc-c14n#
            //     SignatureMethod  Algorithm=http://www.w3.org/2000/09/xmldsig#rsa-sha1
            //     Reference URI="#HostTrustAssertion"
            //       Transforms
            //         Transform Algorithm=http://www.w3.org/2000/09/xmldsig#enveloped-signature
            //         Transform Algorithm=http://www.w3.org/2001/10/xml-exc-c14n#
            //       DigestMethod Algorithm=http://www.w3.org/2000/09/xmldsig#rsa-sha1
            //       DigestValue (the digest value as text content)
            //   SignatureValue (the signature as text content)
            //   KeyInfo
            //     X509Data
            //       X509Certificate (the certificate as text content)
            // KeyInfo: can include Certificate (to make it easy to find in a public keystore or verify its CA)
            if( signatureGenerator != null ) {
                signatureGenerator.signSAMLObject(assertion);                
            }
        }
     
}