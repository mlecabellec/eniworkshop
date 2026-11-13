package fr.lecabellec.eniworkshop.web;

import fr.lecabellec.eniworkshop.catalog.EsiXmlParser;
import fr.lecabellec.eniworkshop.catalog.SlaveCatalogEntry;
import fr.lecabellec.eniworkshop.catalog.SlaveCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * REST controller for the Slave and Extension Card/Module Catalog.
 *
 * <p>Requirement: REQ-00004, REQ-00009, Feature: FR-00004.
 */
@RestController
@RequestMapping("/api/catalog")
@Tag(name = "Slave & Module Catalog", description = "Browse discovered EtherCAT slaves/extension cards and import ETG.2000 ESI XML")
public final class CatalogController {

    /** Slave catalog service. */
    private final SlaveCatalogService catalogService;

    /** ETG.2000 ESI XML parser. */
    private final EsiXmlParser esiParser;

    /**
     * Constructs a {@link CatalogController}.
     *
     * @param catalogService slave catalog service
     * @param esiParser      ESI XML parser
     */
    @Autowired
    public CatalogController(final SlaveCatalogService catalogService, final EsiXmlParser esiParser) {
        this.catalogService = Objects.requireNonNull(catalogService, "catalogService must not be null");
        this.esiParser = Objects.requireNonNull(esiParser, "esiParser must not be null");
    }

    /**
     * Lists all discovered slave and extension module catalog entries.
     *
     * @return list of catalog entries
     */
    @GetMapping("/slaves")
    @Operation(summary = "List all catalogued slaves and extension cards")
    public List<SlaveCatalogEntry> listSlaves() {
        return catalogService.listAll();
    }

    /**
     * Retrieves a single catalog entry by canonical key.
     *
     * @param key {@code vendorId:productCode:revisionNo}
     * @return catalog entry or 404
     */
    @GetMapping("/slaves/{key}")
    @Operation(summary = "Lookup slave or module profile by canonical key")
    public ResponseEntity<SlaveCatalogEntry> getSlave(@PathVariable("key") final String key) {
        return catalogService.findByKey(key)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Imports an ETG.2000 ESI XML file to enrich the slave/module catalog.
     *
     * @param file uploaded ESI XML file
     * @return count of imported device profiles
     * @throws IOException on upload stream failure
     */
    @PostMapping(value = "/import-esi", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import ETG.2000 ESI XML device descriptions")
    public EsiImportResponse importEsi(@RequestParam("file") final MultipartFile file) throws IOException {
        final String source = Objects.requireNonNullElse(file.getOriginalFilename(), "uploaded-esi.xml");
        final List<SlaveCatalogEntry> entries = esiParser.parse(file.getBytes(), source);
        final int count = catalogService.importEsiEntries(entries);
        return new EsiImportResponse(count, source);
    }

    /**
     * Response payload for ESI import.
     *
     * @param importedCount number of devices imported
     * @param sourceFile    source filename
     */
    public record EsiImportResponse(int importedCount, String sourceFile) {
    }
}
