package fr.lecabellec.eniworkshop.persistence;

import fr.lecabellec.eniworkshop.catalog.SlaveCatalogService;
import fr.lecabellec.eniworkshop.persistence.EniRepositoryService.ImportResult;
import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.xml.EniXmlParser;
import fr.lecabellec.eniworkshop.eni.xml.EniXmlWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests {@link EniRepositoryService} for import, duplication, renaming, versioning, deletion,
 * and GridFS persistence behavior. Traces to REQ-00001, REQ-00006, CS-0090.
 */
final class EniRepositoryServiceTest {

    /** Compressed DOR test fixture path. */
    private static final Path DOR_FIXTURE = Path.of("src/test/resources/eni/DOR.xml.gz");

    /** Simulated large XML payload threshold (>16 MB BSON limit). */
    private static final int LARGE_BLOB_PAD_BYTES = 17_000_000;

    @Test
    void supportsFullCatalogLifecycleWithLocalFallback() throws IOException {
        final SlaveCatalogService catalog = new SlaveCatalogService();
        final EniRepositoryService service = new EniRepositoryService(
            new EniXmlParser(),
            new EniXmlWriter(),
            catalog,
            null,
            null,
            null
        );

        final byte[] xmlBytes = readGunzipped(DOR_FIXTURE);
        final ImportResult imported = service.importEni("REF-DOR", "DOR Line", "1.0.0", xmlBytes, List.of("prod"));
        final String id = imported.metadata().id();
        assertThat(imported.discovery().newlyAddedCount()).isGreaterThan(0);
        assertThat(service.listAll()).hasSize(1);
        assertThat(service.loadDocument(id).slaves()).hasSameSizeAs(imported.document().slaves());

        final EniCatalogMetadata duplicated = service.duplicate(id, "REF-DOR-COPY", "DOR Clone", "1.0.1");
        assertThat(duplicated.parentId()).isEqualTo(id);
        assertThat(service.listAll()).hasSize(2);

        final EniCatalogMetadata renamed = service.rename(duplicated.id(), "DOR Renamed", "REF-DOR-NEW");
        assertThat(renamed.name()).isEqualTo("DOR Renamed");
        assertThat(renamed.reference()).isEqualTo("REF-DOR-NEW");

        final EniDocument loaded = service.loadDocument(renamed.id());
        final EniCatalogMetadata bumped = service.commitNewVersion(renamed.id(), "2.0.0", loaded);
        assertThat(bumped.version()).isEqualTo("2.0.0");
        assertThat(bumped.parentId()).isEqualTo(renamed.id());
        assertThat(service.exportXmlBytes(bumped.id())).isNotEmpty();

        service.delete(id);
        assertThat(service.findMetadata(id)).isEmpty();
    }

    @Test
    void storesOversizedXmlPayloadInGridFsWithoutBsonOverflow() {
        final GridFsTemplate gridFs = mock(GridFsTemplate.class);
        final EniMetadataRepository metaRepo = mock(EniMetadataRepository.class);
        final SlaveCatalogRepository slaveRepo = mock(SlaveCatalogRepository.class);
        final ObjectId expectedOid = new ObjectId();
        when(gridFs.store(any(InputStream.class), any(String.class), eq("application/xml")))
            .thenReturn(expectedOid);

        final EniRepositoryService service = new EniRepositoryService(
            new EniXmlParser(),
            new EniXmlWriter(),
            new SlaveCatalogService(),
            metaRepo,
            slaveRepo,
            gridFs
        );
        final byte[] oversizedBytes = new byte[LARGE_BLOB_PAD_BYTES];
        final EniDocument minimalDoc = new EniXmlParser().parse(DOR_FIXTURE);
        final EniCatalogMetadata saved = service.saveNewVersion(
            "BIG-01",
            "Large ENI",
            "1.0.0",
            "",
            minimalDoc,
            oversizedBytes,
            List.of("large")
        );

        assertThat(saved.gridFsFileId()).isEqualTo(expectedOid.toHexString());
        verify(gridFs).store(any(InputStream.class), eq(saved.id() + ".xml"), eq("application/xml"));
        verify(metaRepo).save(any(EniCatalogMetadata.class));
    }

    private byte[] readGunzipped(final Path path) throws IOException {
        final byte[] gz = Files.readAllBytes(path);
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            return gis.readAllBytes();
        }
    }
}
