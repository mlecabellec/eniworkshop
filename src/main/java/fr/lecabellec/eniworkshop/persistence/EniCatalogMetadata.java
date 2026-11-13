package fr.lecabellec.eniworkshop.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * MongoDB catalog metadata document for a stored ENI version (raw XML stored in GridFS).
 *
 * @param id              unique MongoDB identifier
 * @param reference       user/business ENI reference code (e.g., "REF-IPSE1")
 * @param name            human-readable ENI configuration name
 * @param version         semantic or revision version string (e.g., "1.0.0")
 * @param parentId        optional parent version ID when duplicated/modified
 * @param sha256          SHA-256 hex digest of XML payload
 * @param stats           summary counts (slaves, hotConnect groups, input/output bytes)
 * @param gridFsFileId    GridFS ObjectId hex string holding raw ENI XML
 * @param tags            searchable classification tags
 * @param createdAt       creation timestamp
 * @param updatedAt       last modification timestamp
 *
 * <p>Requirement: REQ-00006, Feature: FR-00006, Constraint: CS-0030.2.
 */
@Document("eni_catalog")
public record EniCatalogMetadata(
        @Id String id,
        @Indexed String reference,
        String name,
        String version,
        String parentId,
        String sha256,
        EniSummaryStats stats,
        String gridFsFileId,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Compact constructor enforcing non-null fields and defensive tag copying.
     */
    public EniCatalogMetadata {
        Objects.requireNonNull(id, "EniCatalogMetadata id must not be null");
        Objects.requireNonNull(reference, "EniCatalogMetadata reference must not be null");
        Objects.requireNonNull(name, "EniCatalogMetadata name must not be null");
        Objects.requireNonNull(version, "EniCatalogMetadata version must not be null");
        Objects.requireNonNull(parentId, "EniCatalogMetadata parentId must not be null");
        Objects.requireNonNull(sha256, "EniCatalogMetadata sha256 must not be null");
        Objects.requireNonNull(stats, "EniCatalogMetadata stats must not be null");
        Objects.requireNonNull(gridFsFileId, "EniCatalogMetadata gridFsFileId must not be null");
        tags = List.copyOf(Objects.requireNonNull(tags, "EniCatalogMetadata tags must not be null"));
        Objects.requireNonNull(createdAt, "EniCatalogMetadata createdAt must not be null");
        Objects.requireNonNull(updatedAt, "EniCatalogMetadata updatedAt must not be null");
    }

    /**
     * Compact summary statistics extracted from an ENI configuration.
     *
     * @param slaveCount      number of slaves
     * @param hotConnectCount number of HotConnect groups
     * @param cyclicCmdCount  number of cyclic datagrams
     * @param inputsByteSize  ProcessImage Inputs byte size
     * @param outputsByteSize ProcessImage Outputs byte size
     */
    public record EniSummaryStats(
            int slaveCount,
            int hotConnectCount,
            int cyclicCmdCount,
            int inputsByteSize,
            int outputsByteSize) {
    }
}
