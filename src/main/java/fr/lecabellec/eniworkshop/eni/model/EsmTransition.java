package fr.lecabellec.eniworkshop.eni.model;

import java.util.Objects;

/**
 * EtherCAT State Machine (ESM) transitions permitted in ENI InitCmds (ETG.2100).
 *
 * <p>Requirement: REQ-00002, REQ-00003.
 */
public enum EsmTransition {
    /** Init to Pre-Operational. */
    IP,
    /** Pre-Operational to Safe-Operational. */
    PS,
    /** Pre-Operational to Init. */
    PI,
    /** Safe-Operational to Pre-Operational. */
    SP,
    /** Safe-Operational to Operational. */
    SO,
    /** Safe-Operational to Init. */
    SI,
    /** Operational to Safe-Operational. */
    OS,
    /** Operational to Pre-Operational. */
    OP,
    /** Operational to Init. */
    OI,
    /** Init to Bootstrap. */
    IB,
    /** Bootstrap to Init. */
    BI,
    /** Init to Init. */
    II,
    /** Pre-Operational to Pre-Operational. */
    PP,
    /** Safe-Operational to Safe-Operational. */
    SS;

    /**
     * Parses an ENI transition string (case-insensitive, trimmed).
     *
     * @param raw transition mnemonic such as "IP" or "PS"
     * @return parsed {@link EsmTransition}
     * @throws IllegalArgumentException if mnemonic is unknown
     */
    public static EsmTransition parse(final String raw) {
        Objects.requireNonNull(raw, "Transition string must not be null");
        final String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT);
        return EsmTransition.valueOf(normalized);
    }
}
