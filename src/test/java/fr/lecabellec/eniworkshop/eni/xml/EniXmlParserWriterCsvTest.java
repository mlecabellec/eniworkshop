package fr.lecabellec.eniworkshop.eni.xml;

import fr.lecabellec.eniworkshop.EniWorkshopException;
import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.model.EniSlave;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link EniXmlParser}, {@link EniXmlWriter}, and {@link EniCsvService} against
 * real dimensioning ENIs (DOR, IPSO1, IPSE1) and the imported test ENI corpus.
 * Traces to REQ-00002, REQ-00010, CS-0090.
 */
final class EniXmlParserWriterCsvTest {

    /** Directory containing compressed ENI test fixtures. */
    private static final Path ENI_FIXTURES_DIR = Path.of("src/test/resources/eni");

    /** Negative test fixture containing a truncated ENI XML stream. */
    private static final String NEGATIVE_TRUNCATED_FIXTURE =
        "09bf5138c58bbc40d10bc343b61dd55c1362db400c28dc5ac95ae060337ff44a.xml.gz";

    /** Expected DOR slave count. */
    private static final int DOR_SLAVE_COUNT = 15;

    /** Expected DOR HotConnect group count. */
    private static final int DOR_HC_COUNT = 2;

    /** Expected IPSO1 slave count. */
    private static final int IPSO1_SLAVE_COUNT = 216;

    /** Expected IPSE1 slave count. */
    private static final int IPSE1_SLAVE_COUNT = 353;

    /** Expected updated IPSE1_v2 sample slave count. */
    private static final int IPSE1_V2_SLAVE_COUNT = 334;

    /** Parser under test. */
    private final EniXmlParser parser = new EniXmlParser();

    /** Writer under test. */
    private final EniXmlWriter writer = new EniXmlWriter();

    /** CSV service under test. */
    private final EniCsvService csvService = new EniCsvService();

    static Stream<Path> validEniFixtures() throws IOException {
        try (Stream<Path> walk = Files.list(ENI_FIXTURES_DIR)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".xml.gz"))
                .filter(p -> !p.getFileName().toString().equals(NEGATIVE_TRUNCATED_FIXTURE))
                .sorted()
                .toList()
                .stream();
        }
    }

    @Test
    void parsesDimensioningEniDorRoundTripAndCsv() {
        final EniDocument dor = parser.parse(ENI_FIXTURES_DIR.resolve("DOR.xml.gz"));
        assertThat(dor.version()).isEqualTo("1.3");
        assertThat(dor.slaves()).hasSize(DOR_SLAVE_COUNT);
        assertThat(dor.hotConnectGroupCount()).isEqualTo(DOR_HC_COUNT);
        assertThat(dor.cyclic().frames()).isNotEmpty();

        final String serialized = writer.writeToString(dor);
        final EniDocument reparsed = parser.parse(serialized.getBytes(StandardCharsets.UTF_8));
        assertThat(reparsed.slaves()).hasSameSizeAs(dor.slaves());
        assertThat(reparsed.cyclic().frames()).hasSameSizeAs(dor.cyclic().frames());
        assertThat(reparsed.master().name()).isEqualTo(dor.master().name());

        final String slaveCsv = csvService.exportSlavesToCsv(dor);
        final List<EniSlave> importedSlaves = csvService.importSlavesFromCsv(slaveCsv);
        assertThat(importedSlaves).hasSize(DOR_SLAVE_COUNT);

        final String varsCsv = csvService.exportProcessImageToCsv(dor);
        assertThat(varsCsv).contains("Name,");
    }

    @Test
    void parsesLargeDimensioningEnisIpso1AndIpse1() {
        final EniDocument ipso1 = parser.parse(ENI_FIXTURES_DIR.resolve("IPSO1.xml.gz"));
        assertThat(ipso1.slaves()).hasSize(IPSO1_SLAVE_COUNT);
        assertThat(ipso1.hotConnectGroupCount()).isGreaterThan(0);

        final EniDocument ipse1 = parser.parse(ENI_FIXTURES_DIR.resolve("IPSE1.xml.gz"));
        assertThat(ipse1.slaves()).hasSize(IPSE1_SLAVE_COUNT);
        assertThat(ipse1.hotConnectGroupCount()).isGreaterThan(0);

        final EniDocument ipse1V2 = parser.parse(ENI_FIXTURES_DIR.resolve("IPSE1_v2.xml.gz"));
        assertThat(ipse1V2.slaves()).hasSize(IPSE1_V2_SLAVE_COUNT);

        final EniDocument tte = parser.parse(ENI_FIXTURES_DIR.resolve("TTE.xml.gz"));
        assertThat(tte.slaves()).hasSize(1);
        assertThat(tte.slaves().get(0).identity().name()).contains("ECS-PCIe/FPGA/GTD256");
    }

    @ParameterizedTest(name = "Parse & round-trip {0}")
    @MethodSource("validEniFixtures")
    void parsesAllValidCompressedCorpusFixtures(final Path fixturePath) throws IOException {
        final EniDocument doc;
        try (InputStream is = Files.newInputStream(fixturePath)) {
            doc = parser.parse(is);
        }
        assertThat(doc.master()).isNotNull();
        assertThat(doc.slaves()).isNotEmpty();

        final String xml = writer.writeToString(doc);
        final EniDocument reparsed = parser.parse(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(reparsed.slaves()).hasSameSizeAs(doc.slaves());
    }

    @Test
    void rejectsTruncatedNegativeFixtureCleanly() {
        final Path broken = ENI_FIXTURES_DIR.resolve(NEGATIVE_TRUNCATED_FIXTURE);
        assertThatThrownBy(() -> parser.parse(broken))
            .isInstanceOf(EniWorkshopException.class);
    }
}
