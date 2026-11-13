package fr.lecabellec.eniworkshop.quasar;

import java.util.List;
import java.util.Objects;

/**
 * Result of testing/comparing an ENI file against a live Quasar EtherCAT bus.
 *
 * @param matched     true if live bus topology matches the reference ENI
 * @param message     summary message
 * @param differences list of mismatch details from Quasar EniComparator
 *
 * <p>Requirement: REQ-00008, Constraint: CS-0030.2.
 */
public record QuasarCompareResult(
        boolean matched,
        String message,
        List<String> differences) {

    /**
     * Compact constructor enforcing non-null message and defensive list copy.
     */
    public QuasarCompareResult {
        Objects.requireNonNull(message, "message must not be null");
        differences = List.copyOf(Objects.requireNonNull(differences, "differences must not be null"));
    }
}
