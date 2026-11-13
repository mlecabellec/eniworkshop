package fr.lecabellec.eniworkshop.persistence;

import com.mongodb.client.gridfs.model.GridFSFile;
import fr.lecabellec.eniworkshop.EniWorkshopException;
import fr.lecabellec.eniworkshop.catalog.CatalogDiscoveryReport;
import fr.lecabellec.eniworkshop.catalog.SlaveCatalogEntry;
import fr.lecabellec.eniworkshop.catalog.SlaveCatalogService;
import fr.lecabellec.eniworkshop.eni.model.CyclicFrame;
import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.xml.EniXmlParser;
import fr.lecabellec.eniworkshop.eni.xml.EniXmlWriter;
import fr.lecabellec.eniworkshop.persistence.EniCatalogMetadata.EniSummaryStats;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hybrid ENI catalog persistence service storing metadata in MongoDB and raw XML payloads
 * (&gt;16 MB supported) in GridFS, with resilient in-memory fallback when MongoDB is absent.
 *
 * <p>Requirement: REQ-00006, Feature: FR-00006, Task: TSK-00006, Constraint: CS-0030.8, CS-0030.9.
 */
@Service
public final class EniRepositoryService {

    /** SLF4J logger. */
    private static final Logger LOG = LoggerFactory.getLogger(EniRepositoryService.class);

    /** ENI XML parser. */
    private final EniXmlParser xmlParser;

    /** ENI XML writer. */
    private final EniXmlWriter xmlWriter;

    /** Slave catalog discovery service. */
    private final SlaveCatalogService slaveCatalogService;

    /** Optional Mongo metadata repository. */
    private final Optional<EniMetadataRepository> metadataRepository;

    /** Optional Mongo slave catalog repository. */
    private final Optional<SlaveCatalogRepository> slaveCatalogRepository;

    /** Optional Mongo GridFS template. */
    private final Optional<GridFsTemplate> gridFsTemplate;

    /** Local thread-safe metadata store used in CLI/offline mode or cache. */
    private final Map<String, EniCatalogMetadata> localMetadata = new ConcurrentHashMap<>();

    /** Local thread-safe raw XML store keyed by gridFsFileId. */
    private final Map<String, byte[]> localBlobs = new ConcurrentHashMap<>();

    /**
     * Constructs an {@link EniRepositoryService} with optional Spring Data MongoDB beans.
     *
     * @param xmlParser              ENI XML parser
     * @param xmlWriter              ENI XML writer
     * @param slaveCatalogService    slave catalog service
     * @param metadataRepository     optional Mongo metadata repository
     * @param slaveCatalogRepository optional Mongo slave catalog repository
     * @param gridFsTemplate         optional Mongo GridFS template
     */
    @Autowired
    public EniRepositoryService(
            final EniXmlParser xmlParser,
            final EniXmlWriter xmlWriter,
            final SlaveCatalogService slaveCatalogService,
            @Autowired(required = false) final EniMetadataRepository metadataRepository,
            @Autowired(required = false) final SlaveCatalogRepository slaveCatalogRepository,
            @Autowired(required = false) final GridFsTemplate gridFsTemplate) {
        this.xmlParser = Objects.requireNonNull(xmlParser, "xmlParser must not be null");
        this.xmlWriter = Objects.requireNonNull(xmlWriter, "xmlWriter must not be null");
        this.slaveCatalogService = Objects.requireNonNull(slaveCatalogService, "slaveCatalogService must not be null");
        this.metadataRepository = Optional.ofNullable(metadataRepository);
        this.slaveCatalogRepository = Optional.ofNullable(slaveCatalogRepository);
        this.gridFsTemplate = Optional.ofNullable(gridFsTemplate);
    }

    /**
     * Imports a new ENI XML payload, extracts unknown slaves into the catalog, and persists metadata + XML blob.
     *
     * @param reference business reference code
     * @param name      display name
     * @param version   initial version label
     * @param xmlBytes  raw or gzipped XML bytes
     * @param tags      classification tags
     * @return import outcome containing metadata, parsed document, and catalog discovery report
     */
    public ImportResult importEni(
            final String reference,
            final String name,
            final String version,
            final byte[] xmlBytes,
            final List<String> tags) {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(xmlBytes, "xmlBytes must not be null");
        Objects.requireNonNull(tags, "tags must not be null");

        final EniDocument document = xmlParser.parse(xmlBytes);
        final byte[] canonicalXml = xmlWriter.writeToString(document).getBytes(StandardCharsets.UTF_8);
        final CatalogDiscoveryReport discovery = slaveCatalogService.discoverAndRegisterFromEni(document, reference);
        syncSlaveCatalogToMongo();

        final EniCatalogMetadata saved = saveNewVersion(
                reference,
                name,
                version,
                "",
                document,
                canonicalXml,
                tags);
        return new ImportResult(saved, document, discovery);
    }

    /**
     * Saves a newly created or modified {@link EniDocument} as a new catalog version.
     *
     * @param reference business reference
     * @param name      display name
     * @param version   version string
     * @param parentId  parent version ID (empty if root)
     * @param document  ENI document
     * @param xmlBytes  serialized XML bytes
     * @param tags      tags list
     * @return persisted {@link EniCatalogMetadata}
     */
    public EniCatalogMetadata saveNewVersion(
            final String reference,
            final String name,
            final String version,
            final String parentId,
            final EniDocument document,
            final byte[] xmlBytes,
            final List<String> tags) {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(xmlBytes, "xmlBytes must not be null");

        final String id = UUID.randomUUID().toString();
        final String sha256 = sha256Hex(xmlBytes);
        final String blobId = storeBlob(id + ".xml", xmlBytes);
        final Instant now = Instant.now();
        final EniSummaryStats stats = computeStats(document);

        final EniCatalogMetadata meta = new EniCatalogMetadata(
                id,
                reference,
                name,
                version,
                parentId,
                sha256,
                stats,
                blobId,
                tags,
                now,
                now);
        saveMetadata(meta);
        return meta;
    }

    /**
     * Duplicates an existing ENI catalog entry with a new reference, name, and version.
     *
     * @param sourceId     existing ENI catalog ID
     * @param newReference new reference string
     * @param newName      new name
     * @param newVersion   new version label
     * @return duplicated {@link EniCatalogMetadata}
     */
    public EniCatalogMetadata duplicate(
            final String sourceId,
            final String newReference,
            final String newName,
            final String newVersion) {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        final EniCatalogMetadata source = findMetadata(sourceId)
                .orElseThrow(() -> new EniWorkshopException("ENI catalog entry not found: " + sourceId));
        final byte[] xmlBytes = loadRawXml(source.gridFsFileId());
        final EniDocument doc = xmlParser.parse(xmlBytes);
        return saveNewVersion(newReference, newName, newVersion, source.id(), doc, xmlBytes, source.tags());
    }

    /**
     * Renames or updates reference metadata on an existing ENI catalog entry.
     *
     * @param id           existing ENI ID
     * @param newName      new display name
     * @param newReference new business reference
     * @return updated {@link EniCatalogMetadata}
     */
    public EniCatalogMetadata rename(final String id, final String newName, final String newReference) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(newName, "newName must not be null");
        Objects.requireNonNull(newReference, "newReference must not be null");

        final EniCatalogMetadata existing = findMetadata(id)
                .orElseThrow(() -> new EniWorkshopException("ENI catalog entry not found: " + id));
        final EniCatalogMetadata updated = new EniCatalogMetadata(
                existing.id(),
                newReference,
                newName,
                existing.version(),
                existing.parentId(),
                existing.sha256(),
                existing.stats(),
                existing.gridFsFileId(),
                existing.tags(),
                existing.createdAt(),
                Instant.now());
        saveMetadata(updated);
        return updated;
    }

    /**
     * Creates a new version from an updated {@link EniDocument} linked to an existing entry.
     *
     * @param parentId   existing ENI ID
     * @param newVersion new version string
     * @param updatedDoc modified {@link EniDocument}
     * @return persisted new version metadata
     */
    public EniCatalogMetadata commitNewVersion(
            final String parentId,
            final String newVersion,
            final EniDocument updatedDoc) {
        Objects.requireNonNull(parentId, "parentId must not be null");
        Objects.requireNonNull(newVersion, "newVersion must not be null");
        Objects.requireNonNull(updatedDoc, "updatedDoc must not be null");

        final EniCatalogMetadata parent = findMetadata(parentId)
                .orElseThrow(() -> new EniWorkshopException("Parent ENI catalog entry not found: " + parentId));
        final byte[] xmlBytes = xmlWriter.writeToString(updatedDoc).getBytes(StandardCharsets.UTF_8);
        return saveNewVersion(
                parent.reference(),
                parent.name(),
                newVersion,
                parent.id(),
                updatedDoc,
                xmlBytes,
                parent.tags());
    }

    /**
     * Loads and parses the {@link EniDocument} for a catalog ID.
     *
     * @param id catalog ID
     * @return parsed {@link EniDocument}
     */
    public EniDocument loadDocument(final String id) {
        final EniCatalogMetadata meta = findMetadata(id)
                .orElseThrow(() -> new EniWorkshopException("ENI catalog entry not found: " + id));
        return xmlParser.parse(loadRawXml(meta.gridFsFileId()));
    }

    /**
     * Loads raw ENI XML bytes for export/download.
     *
     * @param id catalog ID
     * @return raw XML byte array
     */
    public byte[] exportXmlBytes(final String id) {
        final EniCatalogMetadata meta = findMetadata(id)
                .orElseThrow(() -> new EniWorkshopException("ENI catalog entry not found: " + id));
        return loadRawXml(meta.gridFsFileId());
    }

    /**
     * Lists all ENI catalog entries sorted by updatedAt descending.
     *
     * @return list of metadata entries
     */
    public List<EniCatalogMetadata> listAll() {
        if (metadataRepository.isPresent()) {
            try {
                final List<EniCatalogMetadata> fromDb = metadataRepository.get().findAll();
                for (final EniCatalogMetadata m : fromDb) {
                    localMetadata.put(m.id(), m);
                }
            } catch (final RuntimeException ex) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("MongoDB unavailable for listAll; serving local cache: {}", ex.getMessage());
                }
            }
        }
        final List<EniCatalogMetadata> list = new ArrayList<>(localMetadata.values());
        list.sort((a, b) -> b.updatedAt().compareTo(a.updatedAt()));
        return List.copyOf(list);
    }

    /**
     * Finds catalog metadata by ID.
     *
     * @param id catalog ID
     * @return optional metadata
     */
    public Optional<EniCatalogMetadata> findMetadata(final String id) {
        Objects.requireNonNull(id, "id must not be null");
        final EniCatalogMetadata cached = localMetadata.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        if (metadataRepository.isPresent()) {
            try {
                return metadataRepository.get().findById(id);
            } catch (final RuntimeException ex) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("MongoDB lookup failed for id {}: {}", id, ex.getMessage());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Deletes an ENI catalog entry and its stored XML blob.
     *
     * @param id catalog ID
     */
    public void delete(final String id) {
        Objects.requireNonNull(id, "id must not be null");
        final Optional<EniCatalogMetadata> meta = findMetadata(id);
        localMetadata.remove(id);
        meta.ifPresent(m -> localBlobs.remove(m.gridFsFileId()));
        if (metadataRepository.isPresent()) {
            try {
                metadataRepository.get().deleteById(id);
                if (meta.isPresent() && gridFsTemplate.isPresent()) {
                    gridFsTemplate.get().delete(Query.query(Criteria.where("_id").is(meta.get().gridFsFileId())));
                }
            } catch (final RuntimeException ex) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("MongoDB delete failed for id {}: {}", id, ex.getMessage());
                }
            }
        }
    }

    /**
     * Saves metadata to local cache and MongoDB when reachable.
     *
     * @param meta metadata record
     */
    private void saveMetadata(final EniCatalogMetadata meta) {
        localMetadata.put(meta.id(), meta);
        if (metadataRepository.isPresent()) {
            try {
                metadataRepository.get().save(meta);
            } catch (final RuntimeException ex) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("MongoDB saveMetadata unavailable; stored in local cache: {}", ex.getMessage());
                }
            }
        }
    }

    /**
     * Persists raw XML bytes in GridFS (falling back to local memory if MongoDB is offline).
     *
     * @param filename logical blob filename
     * @param xmlBytes raw XML bytes
     * @return GridFS ObjectId or local blob ID
     */
    private String storeBlob(final String filename, final byte[] xmlBytes) {
        if (gridFsTemplate.isPresent()) {
            try (InputStream is = new ByteArrayInputStream(xmlBytes)) {
                final ObjectId oid = gridFsTemplate.get().store(is, filename, "application/xml");
                final String id = oid.toHexString();
                localBlobs.put(id, xmlBytes.clone());
                return id;
            } catch (final RuntimeException | IOException ex) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("GridFS store unavailable; keeping blob in local memory: {}", ex.getMessage());
                }
            }
        }
        final String fallbackId = "local-" + UUID.randomUUID();
        localBlobs.put(fallbackId, xmlBytes.clone());
        return fallbackId;
    }

    /**
     * Loads raw XML bytes from local cache or GridFS.
     *
     * @param blobId GridFS file ID or local blob ID
     * @return raw XML bytes
     */
    private byte[] loadRawXml(final String blobId) {
        final byte[] cached = localBlobs.get(blobId);
        if (cached != null) {
            return cached.clone();
        }
        if (gridFsTemplate.isPresent()) {
            try {
                final GridFSFile file = gridFsTemplate.get().findOne(Query.query(Criteria.where("_id").is(blobId)));
                if (file != null) {
                    final GridFsResource resource = gridFsTemplate.get().getResource(file);
                    try (InputStream is = resource.getInputStream()) {
                        final byte[] loaded = is.readAllBytes();
                        localBlobs.put(blobId, loaded.clone());
                        return loaded;
                    }
                }
            } catch (final RuntimeException | IOException ex) {
                throw new EniWorkshopException("Failed to load ENI blob from GridFS: " + blobId, ex);
            }
        }
        throw new EniWorkshopException("ENI XML blob not found: " + blobId);
    }

    /**
     * Synchronizes discovered slave catalog entries to MongoDB when available.
     */
    private void syncSlaveCatalogToMongo() {
        if (slaveCatalogRepository.isEmpty()) {
            return;
        }
        try {
            final List<SlaveCatalogEntry> entries = slaveCatalogService.listAll();
            slaveCatalogRepository.get().saveAll(entries);
        } catch (final RuntimeException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("MongoDB slave_catalog sync skipped: {}", ex.getMessage());
            }
        }
    }

    /**
     * Computes summary statistics for an {@link EniDocument}.
     *
     * @param doc parsed ENI document
     * @return summary stats
     */
    private EniSummaryStats computeStats(final EniDocument doc) {
        int cyclicCmds = 0;
        for (final CyclicFrame frame : doc.cyclic().frames()) {
            cyclicCmds += frame.commands().size();
        }
        return new EniSummaryStats(
                doc.slaves().size(),
                doc.hotConnectGroupCount(),
                cyclicCmds,
                doc.processImage().inputsByteSize(),
                doc.processImage().outputsByteSize());
    }

    /**
     * Computes SHA-256 hex digest of XML bytes.
     *
     * @param data input bytes
     * @return lowercase hex digest
     */
    private String sha256Hex(final byte[] data) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (final NoSuchAlgorithmException ex) {
            throw new EniWorkshopException("SHA-256 algorithm unavailable", ex);
        }
    }

    /**
     * Composite result returned by {@link #importEni(String, String, String, byte[], List)}.
     *
     * @param metadata  persisted catalog metadata
     * @param document  parsed ENI AST
     * @param discovery unknown slave/module discovery report
     */
    public record ImportResult(
            EniCatalogMetadata metadata,
            EniDocument document,
            CatalogDiscoveryReport discovery) {
    }
}
