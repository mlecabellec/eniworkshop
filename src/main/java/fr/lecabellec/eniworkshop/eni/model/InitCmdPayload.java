package fr.lecabellec.eniworkshop.eni.model;

import java.util.Objects;

/**
 * Payload bytes, explicit length, and expected working counter for an EtherCAT InitCmd.
 *
 * @param dataHex    hexadecimal byte payload
 * @param dataLength explicit byte length (used when payload data is omitted)
 * @param cnt        expected working counter (0 if unspecified)
 *
 * <p>Requirement: REQ-00002, Constraint: CS-0030.1.
 */
public record InitCmdPayload(
        String dataHex,
        int dataLength,
        int cnt) {

    /**
     * Compact constructor validating non-null dataHex and non-negative length/cnt.
     */
    public InitCmdPayload {
        Objects.requireNonNull(dataHex, "InitCmd dataHex must not be null");
        if (dataLength < 0 || cnt < 0) {
            throw new IllegalArgumentException("InitCmdPayload dataLength and cnt must be non-negative");
        }
    }
}
