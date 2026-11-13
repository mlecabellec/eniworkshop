package fr.lecabellec.eniworkshop.eni.rules;

import fr.lecabellec.eniworkshop.eni.model.CyclicConfig;
import fr.lecabellec.eniworkshop.eni.model.DistributedClocksConfig;
import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.model.EniMaster;
import fr.lecabellec.eniworkshop.eni.model.EniSlave;
import fr.lecabellec.eniworkshop.eni.model.MailboxConfig;
import fr.lecabellec.eniworkshop.eni.model.MailboxStatesConfig;
import fr.lecabellec.eniworkshop.eni.model.MasterEoeConfig;
import fr.lecabellec.eniworkshop.eni.model.MasterRedundancyConfig;
import fr.lecabellec.eniworkshop.eni.model.PreviousPort;
import fr.lecabellec.eniworkshop.eni.model.ProcessDataLayout;
import fr.lecabellec.eniworkshop.eni.model.ProcessImage;
import fr.lecabellec.eniworkshop.eni.model.SlaveIdentity;
import fr.lecabellec.eniworkshop.eni.xml.EniXmlParser;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link EniValidationEngine} across valid ENI files and synthetic EtherCAT rule violations.
 * Traces to REQ-00003, CS-0090.
 */
final class EniValidationEngineTest {

    /** DOR fixture path. */
    private static final Path DOR_FIXTURE = Path.of("src/test/resources/eni/DOR.xml.gz");

    /** Station address used for duplicate PhysAddr test. */
    private static final int DUP_PHYS_ADDR = 1001;

    /** Default Ethernet frame EtherType. */
    private static final int ECAT_ETHERTYPE = 0x88A4;

    /** Synthetic Beckhoff vendor ID. */
    private static final long BECKHOFF_VENDOR_ID = 2L;

    /** Synthetic EK1100 product code. */
    private static final long EK1100_PRODUCT = 72100946L;

    /** Parser under test. */
    private final EniXmlParser parser = new EniXmlParser();

    /** Validation engine under test. */
    private final EniValidationEngine engine = new EniValidationEngine();

    @Test
    void validatesDorEniWithoutErrors() {
        final EniDocument dor = parser.parse(DOR_FIXTURE);
        final ValidationReport report = engine.validate(dor);
        assertThat(report.valid()).isTrue();
        assertThat(report.errorCount()).isZero();
    }

    @Test
    void detectsDuplicatePhysAddrAndMissingPreviousPort() {
        final EniMaster master = new EniMaster(
            "TestMaster",
            "ffffffffffff",
            "001122334455",
            ECAT_ETHERTYPE,
            new MailboxStatesConfig(0L, 0),
            new MasterEoeConfig(0, 0, 0),
            new MasterRedundancyConfig(false, ""),
            List.of()
        );
        final EniSlave first = new EniSlave(
            new SlaveIdentity("Slave1", DUP_PHYS_ADDR, 0, "YY", BECKHOFF_VENDOR_ID, EK1100_PRODUCT, 1L, 0L),
            Optional.empty(),
            Optional.empty(),
            ProcessDataLayout.empty(),
            List.of(),
            MailboxConfig.empty(),
            DistributedClocksConfig.disabled()
        );
        final EniSlave secondDuplicate = new EniSlave(
            new SlaveIdentity("Slave2", DUP_PHYS_ADDR, -1, "YY", BECKHOFF_VENDOR_ID, EK1100_PRODUCT, 1L, 0L),
            Optional.of(new PreviousPort(9999, 'B', true)),
            Optional.empty(),
            ProcessDataLayout.empty(),
            List.of(),
            MailboxConfig.empty(),
            DistributedClocksConfig.disabled()
        );
        final EniDocument broken = new EniDocument(
            "1.3",
            master,
            List.of(first, secondDuplicate),
            new CyclicConfig(1000, List.of()),
            ProcessImage.empty()
        );

        final ValidationReport report = engine.validate(broken);
        assertThat(report.valid()).isFalse();
        assertThat(report.errorCount()).isGreaterThanOrEqualTo(2);
        assertThat(report.issues()).extracting(ValidationIssue::ruleId)
            .contains("ECAT-ADDR-001", "ECAT-TOPO-001");
    }
}
