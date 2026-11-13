package fr.lecabellec.eniworkshop.eni.model;

import java.util.Objects;

/**
 * Master Cable Redundancy configuration for secondary Ethernet ring closure.
 *
 * @param enabled            whether cable redundancy loop is enabled
 * @param secondarySourceMac secondary NIC source MAC address (12 hex digits)
 *
 * <p>Requirement: REQ-00002, REQ-00003, Constraint: CS-0030.1.
 */
public record MasterRedundancyConfig(
        boolean enabled,
        String secondarySourceMac) {

    /**
     * Compact constructor enforcing non-null secondary MAC string.
     */
    public MasterRedundancyConfig {
        Objects.requireNonNull(secondarySourceMac, "secondarySourceMac must not be null");
    }

    /**
     * Returns a disabled redundancy configuration.
     *
     * @return disabled {@link MasterRedundancyConfig}
     */
    public static MasterRedundancyConfig disabled() {
        return new MasterRedundancyConfig(false, "");
    }
}
