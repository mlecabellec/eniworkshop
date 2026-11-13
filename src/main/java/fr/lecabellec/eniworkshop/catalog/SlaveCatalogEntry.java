package fr.lecabellec.eniworkshop.catalog;

import fr.lecabellec.eniworkshop.eni.model.CoeInitCmd;
import fr.lecabellec.eniworkshop.eni.model.MailboxProtocol;
import fr.lecabellec.eniworkshop.eni.model.PdoMapping;
import fr.lecabellec.eniworkshop.eni.model.SyncManagerConfig;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Reusable Slave or Extension Card/Module profile stored in MongoDB {@code slave_catalog}.
 *
 * @param id                  canonical key {@code vendorId:productCode:revisionNo}
 * @param vendorId            32-bit ETG vendor ID
 * @param productCode         32-bit product code
 * @param revisionNo          32-bit revision number
 * @param displayName         human-friendly device or module name (e.g., "EK1100", "EL3204")
 * @param physics             port physics string (e.g., "YKY", "KK")
 * @param category            classified device role ("COUPLER", "EXTENSION_CARD", "JUNCTION", "SLAVE")
 * @param protocols           supported mailbox protocols
 * @param defaultSyncManagers template SyncManagers
 * @param defaultRxPdos       template RxPDOs
 * @param defaultTxPdos       template TxPDOs
 * @param defaultCoeInitCmds  template CoE initialization commands
 * @param esiEnriched         true if enriched from an ETG.2000 ESI file
 * @param sourceEnis          source ENI/ESI identifiers where this device was observed
 * @param updatedAt           last update timestamp
 *
 * <p>Requirement: REQ-00004, Feature: FR-00004, Constraint: CS-0030.2.
 */
@Document("slave_catalog")
public record SlaveCatalogEntry(
        @Id String id,
        long vendorId,
        long productCode,
        long revisionNo,
        String displayName,
        String physics,
        String category,
        List<MailboxProtocol> protocols,
        List<SyncManagerConfig> defaultSyncManagers,
        List<PdoMapping> defaultRxPdos,
        List<PdoMapping> defaultTxPdos,
        List<CoeInitCmd> defaultCoeInitCmds,
        boolean esiEnriched,
        List<String> sourceEnis,
        Instant updatedAt) {

    /**
     * Compact constructor enforcing non-null fields and defensive collection copies.
     */
    public SlaveCatalogEntry {
        Objects.requireNonNull(id, "Catalog entry id must not be null");
        Objects.requireNonNull(displayName, "Catalog entry displayName must not be null");
        Objects.requireNonNull(physics, "Catalog entry physics must not be null");
        Objects.requireNonNull(category, "Catalog entry category must not be null");
        protocols = List.copyOf(Objects.requireNonNull(protocols, "protocols must not be null"));
        defaultSyncManagers = List.copyOf(
                Objects.requireNonNull(defaultSyncManagers, "defaultSyncManagers must not be null"));
        defaultRxPdos = List.copyOf(Objects.requireNonNull(defaultRxPdos, "defaultRxPdos must not be null"));
        defaultTxPdos = List.copyOf(Objects.requireNonNull(defaultTxPdos, "defaultTxPdos must not be null"));
        defaultCoeInitCmds = List.copyOf(
                Objects.requireNonNull(defaultCoeInitCmds, "defaultCoeInitCmds must not be null"));
        sourceEnis = List.copyOf(Objects.requireNonNull(sourceEnis, "sourceEnis must not be null"));
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
