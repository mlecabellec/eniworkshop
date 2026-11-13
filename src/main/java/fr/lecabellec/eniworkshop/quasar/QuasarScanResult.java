package fr.lecabellec.eniworkshop.quasar;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of scanning a live or virtual Quasar EtherCAT bus and constructing an ENI document.
 *
 * @param success       true if Quasar scan succeeded
 * @param message       diagnostic or status message
 * @param eniCatalogId  optional persisted ENI catalog ID when stored
 * @param generatedXml  optional raw ETG.2100 ENI XML produced by Quasar EniGenerator
 *
 * <p>Requirement: REQ-00008, Constraint: CS-0030.2.
 */
public record QuasarScanResult(
        boolean success,
        String message,
        Optional<String> eniCatalogId,
        Optional<String> generatedXml) {

    /**
     * Compact constructor enforcing non-null fields.
     */
    public QuasarScanResult {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(eniCatalogId, "eniCatalogId Optional must not be null");
        Objects.requireNonNull(generatedXml, "generatedXml Optional must not be null");
    }
}
