/*
 * Copyright (C) 2011-2012 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson.wlm.rest.data;

import com.intel.mountwilson.as.common.ValidationException;
import java.util.List;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 *
 * @author jbuhacoff
 */
public class MleData {

    public enum MleType {

        BIOS,
        VMM;
    }

    public enum AttestationType {

        PCR,
        MODULE;
    }
    private String name;
    private String version;
    private AttestationType attestationType;
    private MleType mleType;
    private String description;
    private List<ManifestData> mleManifests;
    /* Start attributes added for phase 2*/
    private String osName; // This will be set for MleType.VMM
    private String osVersion; // This will be set for MleType.VMM
    private String oemName; // This will be set for MleType.BIOS
    /* End attributes added for phase 2*/

    public MleData() {
    }

    public MleData(String name, String version, MleType mleType, AttestationType attestationType,
            List<ManifestData> manifestList, String description,
            String osName, String osVersion, String oemName) {
        setName(name);
        setVersion(version);
        setMleType(mleType.toString());
        setAttestationType(attestationType.toString());
        setManifestList(manifestList);
        setDescription(description);
        setOsName(osName);
        setOsVersion(osVersion);
        setOemName(oemName);
    }

    @JsonProperty("OemName")
    public String getOemName() {
        if (getMleType().equals(MleType.BIOS.toString()) && (oemName == null || oemName.isEmpty())) {
            throw new ValidationException("OEM name is missing for BIOS MLE");
        }

        return oemName;
    }

    @JsonProperty("OemName")
    public final void setOemName(String oemName) {
        this.oemName = oemName;
    }

    @JsonProperty("OsName")
    public String getOsName() {
        if (getMleType().equals(MleType.VMM.toString()) && (osName == null || osName.isEmpty())) {
            throw new ValidationException("OS name is missing for VMM MLE");
        }

        return osName;
    }

    @JsonProperty("OsName")
    public final void setOsName(String osName) {
        this.osName = osName;
    }

    @JsonProperty("OsVersion")
    public String getOsVersion() {
        if (getMleType().equals(MleType.VMM.toString()) && (osVersion == null || osVersion.isEmpty())) {
            throw new ValidationException("OS version is missing for VMM MLE");
        }
        return osVersion;
    }

    @JsonProperty("OsVersion")
    public final void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    @JsonProperty("Name")
    public final void setName(String value) {
        name = value;
    }

    @JsonProperty("Name")
    public String getName() {
        if (name == null || name.isEmpty()) {
            throw new ValidationException("MLE Name is missing");
        }
        return name;
    }

    @JsonProperty("Version")
    public final void setVersion(String value) {
        version = value;
    }

    @JsonProperty("Version")
    public String getVersion() {
        if (version == null || version.isEmpty()) {
            throw new ValidationException("MLE Version is missing");
        }
        return version;
    }

    @JsonProperty("Attestation_Type")
    public final void setAttestationType(String value) {
        if (value == null || value.isEmpty()) {
            throw new ValidationException("MLE Attestation Type is missing");
        }
        try {
            attestationType = AttestationType.valueOf(value.toUpperCase()); //Enum.valueOf(AttestationType.class, value);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Attestation Type is invalid");
        }
    }

    @JsonProperty("Attestation_Type")
    public String getAttestationType() {
        if (attestationType == null) {
            throw new ValidationException("MLE Attestation Type is missing");
        }

        return attestationType.toString();
    }

    @JsonProperty("MLE_Type")
    public final void setMleType(String value) {
        if (value == null || value.isEmpty()) {
            throw new ValidationException("MLE Type is missing");
        }
        try {
            mleType = MleType.valueOf(value); //Enum.valueOf(MleType.class, value);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("MLE Type is invalid");
        }
    }

    @JsonProperty("MLE_Type")
    public String getMleType() {
        if (mleType == null) {
            throw new ValidationException("MLE Type is missing");
        }
        return mleType.toString();
    }

    @JsonProperty("Description")
    public final void setDescription(String value) {
        description = value;
    }

    @JsonProperty("Description")
    public String getDescription() {
        return description;
    }

    @JsonProperty("MLE_Manifests")
    public final void setManifestList(List<ManifestData> list) {
        mleManifests = list;
    }

    @JsonProperty("MLE_Manifests")
    public final List<ManifestData> getManifestList() {
        //return Arrays.asList(mleManifests);
        //validate data before returning the list
        if (mleManifests != null) {
            for (ManifestData manifestData : mleManifests) {
                manifestData.getName();
                manifestData.getValue();
            }
        }
        return mleManifests;
    }

    public String toString() {
        return String.format("%s %s (%s %s) - %s", name, version, mleType.toString(), attestationType.toString(), description);
    }
}
