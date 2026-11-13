package fr.lecabellec.eniworkshop.eni.model;

import java.util.Objects;

/**
 * Timing, retry, and cycle scheduling metadata for an EtherCAT InitCmd.
 *
 * @param requires  execution requirement ("cycle", "frame", or empty)
 * @param retries   number of retries on failure
 * @param timeoutMs command timeout in milliseconds
 *
 * <p>Requirement: REQ-00002, Constraint: CS-0030.1.
 */
public record InitCmdTiming(
        String requires,
        int retries,
        int timeoutMs) {

    /**
     * Compact constructor validating non-null requires and non-negative retries/timeout.
     */
    public InitCmdTiming {
        Objects.requireNonNull(requires, "InitCmd requires must not be null");
        if (retries < 0 || timeoutMs < 0) {
            throw new IllegalArgumentException("InitCmdTiming retries and timeoutMs must be non-negative");
        }
    }
}
