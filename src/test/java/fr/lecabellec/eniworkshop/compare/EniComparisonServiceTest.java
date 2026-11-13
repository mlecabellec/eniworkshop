package fr.lecabellec.eniworkshop.compare;

import fr.lecabellec.eniworkshop.compare.ComparisonModels.CompareScope;
import fr.lecabellec.eniworkshop.compare.ComparisonModels.ComparisonResult;
import fr.lecabellec.eniworkshop.compare.ComparisonModels.DiffStatus;
import fr.lecabellec.eniworkshop.eni.model.CyclicConfig;
import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.model.EniSlave;
import fr.lecabellec.eniworkshop.eni.model.HotConnectGroup;
import fr.lecabellec.eniworkshop.eni.xml.EniXmlParser;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link EniComparisonService} across ENI, topology, slave, and Hot Connect comparison scopes.
 * Traces to REQ-00011, FR-00011.
 */
final class EniComparisonServiceTest {

    /** DOR compressed ENI fixture. */
    private static final Path DOR_FIXTURE = Path.of("src/test/resources/eni/DOR.xml.gz");

    /** IPSO1 compressed ENI fixture. */
    private static final Path IPSO1_FIXTURE = Path.of("src/test/resources/eni/IPSO1.xml.gz");

    /** Identical self-comparison yields zero changed or unmatched rows. */
    @Test
    void selfComparisonProducesAllMatchesAcrossEniAndTopologyScopes() throws Exception {
        final EniXmlParser parser = new EniXmlParser();
        final EniDocument dor = parser.parse(DOR_FIXTURE);
        final EniComparisonService service = new EniComparisonService();

        final ComparisonResult eniDiff = service.compareEnis("DOR-A", dor, "DOR-B", dor, false);
        assertThat(eniDiff.scope()).isEqualTo(CompareScope.ENI);
        assertThat(eniDiff.summary().changedCount()).isZero();
        assertThat(eniDiff.summary().leftOnlyCount()).isZero();
        assertThat(eniDiff.summary().rightOnlyCount()).isZero();
        assertThat(eniDiff.leftLinearBus()).hasSize(dor.slaves().size());
        assertThat(eniDiff.rightLinearBus()).hasSize(dor.slaves().size());

        final ComparisonResult topoDiff = service.compareEnis("DOR-A", dor, "DOR-B", dor, true);
        assertThat(topoDiff.scope()).isEqualTo(CompareScope.TOPOLOGY);
        assertThat(topoDiff.summary().changedCount()).isZero();
        assertThat(topoDiff.leftTree().children()).isNotEmpty();
    }

    /** Cross-comparison between DOR and IPSO1 surfaces changed, left-only, and right-only items. */
    @Test
    void comparesTwoDifferentEnisAndIndividualSlaves() throws Exception {
        final EniXmlParser parser = new EniXmlParser();
        final EniDocument dor = parser.parse(DOR_FIXTURE);
        final EniDocument ipso = parser.parse(IPSO1_FIXTURE);
        final EniComparisonService service = new EniComparisonService();

        final ComparisonResult eniDiff = service.compareEnis("DOR", dor, "IPSO1", ipso, false);
        assertThat(eniDiff.outerJoinRows()).isNotEmpty();
        assertThat(
            eniDiff.summary().changedCount()
                + eniDiff.summary().leftOnlyCount()
                + eniDiff.summary().rightOnlyCount()
        ).isPositive();

        final EniSlave leftSlave = dor.slaves().get(0);
        final EniSlave rightSlave = ipso.slaves().get(ipso.slaves().size() - 1);
        final ComparisonResult slaveDiff = service.compareSlaves("DOR#1", leftSlave, "IPSO1#last", rightSlave);
        assertThat(slaveDiff.scope()).isEqualTo(CompareScope.SLAVE);
        assertThat(slaveDiff.outerJoinRows())
            .anySatisfy(row -> assertThat(row.status()).isEqualTo(DiffStatus.CHANGED));
    }

    /** Compares two Hot Connect groups including identify commands and member differences. */
    @Test
    void comparesHotConnectGroupsWithMemberAndIdentifyCmdDeltas() throws Exception {
        final EniXmlParser parser = new EniXmlParser();
        final EniDocument base = parser.parse(DOR_FIXTURE);
        final EniSlave first = base.slaves().get(0);
        final EniSlave second = base.slaves().get(1);

        final EniSlave headA = new EniSlave(
            first.identity(),
            first.previousPort(),
            Optional.of(new HotConnectGroup("HC-Station-A", 2, List.of())),
            first.processData(),
            first.initCmds(),
            first.mailbox(),
            first.dc()
        );
        final EniSlave headB = new EniSlave(
            second.identity(),
            second.previousPort(),
            Optional.of(new HotConnectGroup("HC-Station-B", 1, List.of())),
            second.processData(),
            second.initCmds(),
            second.mailbox(),
            second.dc()
        );

        final EniDocument docA = new EniDocument(
            base.version(),
            base.master(),
            List.of(headA, second),
            new CyclicConfig(base.cyclic().cycleTimeUs(), List.of()),
            base.processImage()
        );
        final EniDocument docB = new EniDocument(
            base.version(),
            base.master(),
            List.of(headB),
            new CyclicConfig(base.cyclic().cycleTimeUs(), List.of()),
            base.processImage()
        );

        final EniComparisonService service = new EniComparisonService();
        final ComparisonResult hcDiff = service.compareHotConnectGroups(
            "DocA",
            docA,
            "HC-Station-A",
            "DocB",
            docB,
            "HC-Station-B"
        );
        assertThat(hcDiff.scope()).isEqualTo(CompareScope.HOT_CONNECT);
        assertThat(hcDiff.outerJoinRows())
            .anySatisfy(row -> assertThat(row.status()).isIn(DiffStatus.CHANGED, DiffStatus.LEFT_ONLY));
        assertThat(hcDiff.leftTree().label()).contains("HC-Station-A");
        assertThat(hcDiff.rightTree().label()).contains("HC-Station-B");
    }
}
