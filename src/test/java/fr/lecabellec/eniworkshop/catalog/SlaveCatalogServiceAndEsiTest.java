package fr.lecabellec.eniworkshop.catalog;

import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.model.MailboxProtocol;
import fr.lecabellec.eniworkshop.eni.xml.EniXmlParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies automatic discovery of unknown slaves/extension cards from ENI documents and
 * ETG.2000 ESI XML enrichment. Traces to REQ-00004, CS-0090.
 */
final class SlaveCatalogServiceAndEsiTest {

    /** Sample ETG.2000 ESI XML snippet for enrichment tests. */
    private static final String SAMPLE_ESI_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <EtherCATInfo>
          <Vendor>
            <Id>2</Id>
            <Name>Beckhoff Automation</Name>
          </Vendor>
          <Descriptions>
            <Devices>
              <Device Physics="KK">
                <Type ProductCode="#x0c843052" RevisionNo="#x00120000">EL3204</Type>
                <Name>EL3204 4Ch. Ana. Input PT100</Name>
                <Mailbox>
                  <CoE/>
                  <FoE/>
                </Mailbox>
              </Device>
            </Devices>
          </Descriptions>
        </EtherCATInfo>
        """;

    /** ENI parser for fixture loading. */
    private final EniXmlParser eniParser = new EniXmlParser();

    /** ESI parser under test. */
    private final EsiXmlParser esiParser = new EsiXmlParser();

    @Test
    void discoversUnknownDevicesFromDorAndIpse1WithoutDuplicates() {
        final SlaveCatalogService catalogService = new SlaveCatalogService();
        final EniDocument dor = eniParser.parse(Path.of("src/test/resources/eni/DOR.xml.gz"));

        final CatalogDiscoveryReport firstPass = catalogService.discoverAndRegisterFromEni(dor, "DOR.xml");
        assertThat(firstPass.newlyAddedCount()).isGreaterThan(0);
        assertThat(firstPass.alreadyKnownKeys()).isEmpty();
        assertThat(catalogService.listAll()).hasSize(firstPass.uniqueProfilesFound());

        final CatalogDiscoveryReport secondPass = catalogService.discoverAndRegisterFromEni(dor, "DOR-copy.xml");
        assertThat(secondPass.newlyAddedCount()).isZero();
        assertThat(secondPass.alreadyKnownKeys()).hasSameSizeAs(firstPass.newlyAddedKeys());

        final EniDocument ipse1 = eniParser.parse(Path.of("src/test/resources/eni/IPSE1.xml.gz"));
        final CatalogDiscoveryReport thirdPass = catalogService.discoverAndRegisterFromEni(ipse1, "IPSE1.xml");
        assertThat(thirdPass.uniqueProfilesFound()).isGreaterThan(0);
        assertThat(catalogService.listAll()).extracting(SlaveCatalogEntry::category)
            .containsAnyOf("COUPLER", "EXTENSION_CARD", "SLAVE");
    }

    @Test
    void parsesEsiXmlAndImportsEntriesIntoCatalog() {
        final List<SlaveCatalogEntry> parsed = esiParser.parse(
            SAMPLE_ESI_XML.getBytes(StandardCharsets.UTF_8),
            "Beckhoff_EL3xxx.xml"
        );
        assertThat(parsed).hasSize(1);
        final SlaveCatalogEntry entry = parsed.get(0);
        assertThat(entry.displayName()).contains("EL3204");
        assertThat(entry.category()).isEqualTo("EXTENSION_CARD");
        assertThat(entry.protocols()).contains(MailboxProtocol.COE, MailboxProtocol.FOE);
        assertThat(entry.esiEnriched()).isTrue();

        final SlaveCatalogService catalogService = new SlaveCatalogService();
        final int imported = catalogService.importEsiEntries(parsed);
        assertThat(imported).isEqualTo(1);
        assertThat(catalogService.findByKey(entry.id())).isPresent();
    }
}
