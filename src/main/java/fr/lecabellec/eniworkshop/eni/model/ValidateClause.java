package fr.lecabellec.eniworkshop.eni.model;

import java.util.Objects;

/**
 * Validation clause attached to an EtherCAT InitCmd or HotConnect IdentifyCmd (ETG.2100).
 *
 * @param signed    whether signed comparison applies
 * @param type      comparison operator (e.g., "EQ")
 * @param dataHex   expected hexadecimal byte string
 * @param dataMaskHex optional hexadecimal mask byte string
 * @param timeoutMs validation timeout in milliseconds
 *
 * <p>Requirement: REQ-00002, Constraint: CS-0030.1, CS-0030.11.
 */
public record ValidateClause(
        boolean signed,
        String type,
        String dataHex,
        String dataMaskHex,
        int timeoutMs) {

    /**
     * Compact constructor enforcing null safety and non-negative timeout.
     */
    public ValidateClause {
        Objects.requireNonNull(type, "Validate type must not be null");
        Objects.requireNonNull(dataHex, "Validate dataHex must not be null");
        Objects.requireNonNull(dataMaskHex, "Validate dataMaskHex must not be null");
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Validate timeoutMs must be non-negative: " + timeoutMs);
        }
    }
}
