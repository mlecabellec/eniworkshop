package fr.lecabellec.eniworkshop.web;

import fr.lecabellec.eniworkshop.diagram.EthercatFrameMapService;
import fr.lecabellec.eniworkshop.diagram.FrameByteMapDto.MappedFrame;
import fr.lecabellec.eniworkshop.diagram.PlantUmlService;
import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.model.EniSlave;
import fr.lecabellec.eniworkshop.eni.rules.EniValidationEngine;
import fr.lecabellec.eniworkshop.eni.rules.ValidationReport;
import fr.lecabellec.eniworkshop.eni.xml.EniCsvService;
import fr.lecabellec.eniworkshop.eni.xml.EniXmlWriter;
import fr.lecabellec.eniworkshop.messaging.WorkshopEventPublisher;
import fr.lecabellec.eniworkshop.persistence.EniCatalogMetadata;
import fr.lecabellec.eniworkshop.persistence.EniRepositoryService;
import fr.lecabellec.eniworkshop.persistence.EniRepositoryService.ImportResult;
import fr.lecabellec.eniworkshop.topology.TopologyAggregationRequest;
import fr.lecabellec.eniworkshop.topology.TopologyAggregationService;
import fr.lecabellec.eniworkshop.topology.TopologyGraph;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * REST controller for ENI catalog management, validation, topology aggregation, CSV, and frame analysis.
 *
 * <p>Requirement: REQ-00002, REQ-00003, REQ-00005, REQ-00006, REQ-00009, Feature: FR-00009.
 */
@RestController
@RequestMapping("/api/eni")
@Tag(name = "ENI Workbench", description = "Import, export, version, validate, and compose EtherCAT ENI files")
public final class EniWorkshopController {

    /** Persistence repository service. */
    private final EniRepositoryService repositoryService;

    /** EtherCAT validation rules engine. */
    private final EniValidationEngine validationEngine;

    /** Topology graph and aggregation service. */
    private final TopologyAggregationService topologyService;

    /** Frame byte-map analyzer. */
    private final EthercatFrameMapService frameMapService;

    /** CSV import/export service. */
    private final EniCsvService csvService;

    /** PlantUML generator and renderer. */
    private final PlantUmlService plantUmlService;

    /** XML writer for direct serialization. */
    private final EniXmlWriter xmlWriter;

    /** STOMP/Kafka event broadcaster. */
    private final WorkshopEventPublisher eventPublisher;

    /**
     * Constructs an {@link EniWorkshopController} using explicit constructor injection.
     *
     * @param repositoryService ENI persistence service
     * @param validationEngine  EtherCAT validation engine
     * @param topologyService   topology aggregation service
     * @param frameMapService   frame byte-map service
     * @param csvService        CSV service
     * @param plantUmlService   PlantUML service
     * @param xmlWriter         ENI XML writer
     * @param eventPublisher    event publisher
     */
    @Autowired
    public EniWorkshopController(
            final EniRepositoryService repositoryService,
            final EniValidationEngine validationEngine,
            final TopologyAggregationService topologyService,
            final EthercatFrameMapService frameMapService,
            final EniCsvService csvService,
            final PlantUmlService plantUmlService,
            final EniXmlWriter xmlWriter,
            final WorkshopEventPublisher eventPublisher) {
        this.repositoryService = Objects.requireNonNull(repositoryService, "repositoryService must not be null");
        this.validationEngine = Objects.requireNonNull(validationEngine, "validationEngine must not be null");
        this.topologyService = Objects.requireNonNull(topologyService, "topologyService must not be null");
        this.frameMapService = Objects.requireNonNull(frameMapService, "frameMapService must not be null");
        this.csvService = Objects.requireNonNull(csvService, "csvService must not be null");
        this.plantUmlService = Objects.requireNonNull(plantUmlService, "plantUmlService must not be null");
        this.xmlWriter = Objects.requireNonNull(xmlWriter, "xmlWriter must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    /**
     * Lists all ENI catalog entries.
     *
     * @return list of catalog metadata entries
     */
    @GetMapping
    @Operation(summary = "List ENI catalog entries")
    public List<EniCatalogMetadata> listAll() {
        return repositoryService.listAll();
    }

    /**
     * Imports an ENI XML or GZIP XML file via multipart upload.
     *
     * @param file      uploaded ENI file
     * @param reference business reference
     * @param name      display name
     * @param version   version string
     * @param tags      optional comma-separated tags
     * @return import summary including unknown slave discovery
     * @throws IOException on upload stream failure
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import ENI XML/GZ file and discover unknown slaves")
    public ImportResult importFile(
            @RequestParam("file") final MultipartFile file,
            @RequestParam(value = "reference", defaultValue = "REF-NEW") final String reference,
            @RequestParam(value = "name", defaultValue = "") final String name,
            @RequestParam(value = "version", defaultValue = "1.0.0") final String version,
            @RequestParam(value = "tags", defaultValue = "") final String tags) throws IOException {
        final String effectiveName = name.isBlank()
                ? Objects.requireNonNullElse(file.getOriginalFilename(), "imported-eni.xml")
                : name;
        final List<String> tagList = tags.isBlank()
                ? List.of()
                : List.of(tags.split(","));
        final ImportResult result = repositoryService.importEni(
                reference,
                effectiveName,
                version,
                file.getBytes(),
                tagList);
        eventPublisher.publish(
                "ENI_IMPORTED",
                result.metadata().id(),
                "Imported " + effectiveName + " (" + result.document().slaves().size() + " slaves)");
        return result;
    }

    /**
     * Imports raw ENI XML payload submitted in request body.
     *
     * @param request JSON payload holding reference, name, version, xml, tags
     * @return import summary
     */
    @PostMapping("/import-raw")
    @Operation(summary = "Import raw ENI XML string")
    public ImportResult importRaw(@RequestBody final RawImportRequest request) {
        Objects.requireNonNull(request, "RawImportRequest must not be null");
        final ImportResult result = repositoryService.importEni(
                request.reference(),
                request.name(),
                request.version(),
                request.xml().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                request.tags());
        eventPublisher.publish("ENI_IMPORTED", result.metadata().id(), "Imported " + request.name());
        return result;
    }

    /**
     * Loads full parsed {@link EniDocument} AST for a catalog ID.
     *
     * @param id ENI catalog ID
     * @return parsed ENI document
     */
    @GetMapping("/{id}/document")
    @Operation(summary = "Retrieve full parsed EniDocument AST")
    public EniDocument getDocument(@PathVariable("id") final String id) {
        return repositoryService.loadDocument(id);
    }

    /**
     * Saves modified {@link EniDocument} as a new version.
     *
     * @param id      parent catalog ID
     * @param version new version label
     * @param doc     updated ENI document
     * @return persisted version metadata
     */
    @PutMapping("/{id}/document")
    @Operation(summary = "Save modified EniDocument as a new version")
    public EniCatalogMetadata updateDocument(
            @PathVariable("id") final String id,
            @RequestParam(value = "version", defaultValue = "1.0.1") final String version,
            @RequestBody final EniDocument doc) {
        final EniCatalogMetadata updated = repositoryService.commitNewVersion(id, version, doc);
        eventPublisher.publish("ENI_VERSION_CREATED", updated.id(), "Saved version " + version);
        return updated;
    }

    /**
     * Exports raw ETG.2100 ENI XML as downloadable file.
     *
     * @param id ENI catalog ID
     * @return XML bytes response
     */
    @GetMapping(value = "/{id}/export", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Export ENI XML file")
    public ResponseEntity<byte[]> exportXml(@PathVariable("id") final String id) {
        final byte[] xml = repositoryService.exportXmlBytes(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"eni-" + id + ".xml\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(xml);
    }

    /**
     * Duplicates an existing ENI entry under a new reference, name, and version.
     *
     * @param id      source catalog ID
     * @param request duplicate metadata parameters
     * @return new catalog metadata
     */
    @PostMapping("/{id}/duplicate")
    @Operation(summary = "Duplicate ENI catalog entry with new reference and version")
    public EniCatalogMetadata duplicate(
            @PathVariable("id") final String id,
            @RequestBody final DuplicateRequest request) {
        Objects.requireNonNull(request, "DuplicateRequest must not be null");
        final EniCatalogMetadata dup = repositoryService.duplicate(
                id,
                request.reference(),
                request.name(),
                request.version());
        eventPublisher.publish("ENI_DUPLICATED", dup.id(), "Duplicated from " + id);
        return dup;
    }

    /**
     * Renames or updates reference metadata for an ENI catalog entry.
     *
     * @param id      catalog ID
     * @param request rename parameters
     * @return updated catalog metadata
     */
    @PostMapping("/{id}/rename")
    @Operation(summary = "Rename ENI catalog entry")
    public EniCatalogMetadata rename(
            @PathVariable("id") final String id,
            @RequestBody final RenameRequest request) {
        Objects.requireNonNull(request, "RenameRequest must not be null");
        return repositoryService.rename(id, request.name(), request.reference());
    }

    /**
     * Deletes an ENI catalog entry.
     *
     * @param id catalog ID
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete ENI catalog entry")
    public void delete(@PathVariable("id") final String id) {
        repositoryService.delete(id);
        eventPublisher.publish("ENI_DELETED", id, "Deleted ENI " + id);
    }

    /**
     * Validates an ENI catalog entry against ETG.1000 / ETG.2100 rules.
     *
     * @param id catalog ID
     * @return validation diagnostics report
     */
    @GetMapping("/{id}/validate")
    @Operation(summary = "Validate ENI against ETG.1000/ETG.2100 management rules")
    public ValidationReport validate(@PathVariable("id") final String id) {
        final EniDocument doc = repositoryService.loadDocument(id);
        return validationEngine.validate(doc);
    }

    /**
     * Returns directed topology graph (nodes + port edges + redundancy links) for UI rendering.
     *
     * @param id catalog ID
     * @return {@link TopologyGraph}
     */
    @GetMapping("/{id}/topology")
    @Operation(summary = "Build graphical topology nodes and edges")
    public TopologyGraph getTopology(@PathVariable("id") final String id) {
        return topologyService.buildGraph(repositoryService.loadDocument(id));
    }

    /**
     * Aggregates multiple catalog ENIs into a new composed ENI catalog entry.
     *
     * @param request aggregation parameters
     * @return newly created aggregated ENI metadata
     */
    @PostMapping("/aggregate")
    @Operation(summary = "Aggregate multiple ENI topologies into a single composed ENI")
    public EniCatalogMetadata aggregate(@RequestBody final AggregateRequest request) {
        Objects.requireNonNull(request, "AggregateRequest must not be null");
        final EniDocument base = repositoryService.loadDocument(request.baseId());
        final List<EniDocument> addons = new ArrayList<>();
        for (final String addonId : request.addonIds()) {
            addons.add(repositoryService.loadDocument(addonId));
        }
        final TopologyAggregationRequest aggReq = new TopologyAggregationRequest(
                request.attachToPhysAddr(),
                request.attachPort(),
                request.enableCableRedundancy(),
                request.secondarySourceMac(),
                true);
        final EniDocument merged = topologyService.aggregate(base, addons, aggReq);
        final byte[] xmlBytes = xmlWriter.writeToString(merged).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final EniCatalogMetadata saved = repositoryService.saveNewVersion(
                request.reference(),
                request.name(),
                request.version(),
                request.baseId(),
                merged,
                xmlBytes,
                List.of("aggregated"));
        eventPublisher.publish("ENI_AGGREGATED", saved.id(), "Aggregated " + merged.slaves().size() + " slaves");
        return saved;
    }

    /**
     * Returns Ethernet/EtherCAT Frame Byte-Map layout for cyclic frames.
     *
     * @param id catalog ID
     * @return mapped frames list
     */
    @GetMapping("/{id}/frames")
    @Operation(summary = "Compute EtherCAT Frame Byte-Map for cyclic frames")
    public List<MappedFrame> getFrameMaps(@PathVariable("id") final String id) {
        return frameMapService.mapCyclicFrames(repositoryService.loadDocument(id));
    }

    /**
     * Exports slave topology or ProcessImage variables as CSV.
     *
     * @param id   catalog ID
     * @param kind "slaves" or "variables"
     * @return CSV text
     */
    @GetMapping(value = "/{id}/csv", produces = "text/csv")
    @Operation(summary = "Export slaves or ProcessImage variables to CSV")
    public String exportCsv(
            @PathVariable("id") final String id,
            @RequestParam(value = "kind", defaultValue = "slaves") final String kind) {
        final EniDocument doc = repositoryService.loadDocument(id);
        if ("variables".equalsIgnoreCase(kind)) {
            return csvService.exportProcessImageToCsv(doc);
        }
        return csvService.exportSlavesToCsv(doc);
    }

    /**
     * Parses CSV rows into slave skeleton definitions for interactive table import.
     *
     * @param csvText CSV payload
     * @return parsed slave list
     */
    @PostMapping(value = "/parse-csv", consumes = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Parse CSV slave table into EniSlave definitions")
    public List<EniSlave> parseCsvSlaves(@RequestBody final String csvText) {
        return csvService.importSlavesFromCsv(csvText);
    }

    /**
     * Generates PlantUML source for topology or ESM sequence diagram.
     *
     * @param id   catalog ID
     * @param kind "topology" or "esm"
     * @return PlantUML source string
     */
    @GetMapping(value = "/{id}/plantuml", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Generate PlantUML source for ENI topology or ESM sequence")
    public String getPlantUml(
            @PathVariable("id") final String id,
            @RequestParam(value = "kind", defaultValue = "topology") final String kind) {
        final EniDocument doc = repositoryService.loadDocument(id);
        if ("esm".equalsIgnoreCase(kind)) {
            return plantUmlService.generateEsmSequencePuml(doc);
        }
        return plantUmlService.generateTopologyPuml(doc);
    }

    /**
     * Request payload for raw XML import.
     *
     * @param reference business reference
     * @param name      display name
     * @param version   version string
     * @param xml       raw XML text
     * @param tags      tags list
     */
    public record RawImportRequest(
            String reference,
            String name,
            String version,
            String xml,
            List<String> tags) {
    }

    /**
     * Request payload for duplicating an ENI entry.
     *
     * @param reference new reference
     * @param name      new name
     * @param version   new version
     */
    public record DuplicateRequest(String reference, String name, String version) {
    }

    /**
     * Request payload for renaming an ENI entry.
     *
     * @param reference new reference
     * @param name      new name
     */
    public record RenameRequest(String reference, String name) {
    }

    /**
     * Request payload for aggregating multiple ENI topologies.
     *
     * @param baseId                base ENI catalog ID
     * @param addonIds              ordered add-on ENI catalog IDs
     * @param attachToPhysAddr      target attachment PhysAddr (0 for tail)
     * @param attachPort            target port letter
     * @param enableCableRedundancy enable ring redundancy
     * @param secondarySourceMac    secondary NIC MAC
     * @param reference             output reference
     * @param name                  output name
     * @param version               output version
     */
    public record AggregateRequest(
            String baseId,
            List<String> addonIds,
            int attachToPhysAddr,
            char attachPort,
            boolean enableCableRedundancy,
            String secondarySourceMac,
            String reference,
            String name,
            String version) {
    }
}
