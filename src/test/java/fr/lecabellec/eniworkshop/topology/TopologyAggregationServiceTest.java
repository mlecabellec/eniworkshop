package fr.lecabellec.eniworkshop.topology;

import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.model.EniSlave;
import fr.lecabellec.eniworkshop.eni.rules.EniValidationEngine;
import fr.lecabellec.eniworkshop.eni.rules.ValidationReport;
import fr.lecabellec.eniworkshop.eni.xml.EniXmlParser;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link TopologyAggregationService} for graph generation, address shifting,
 * ProcessImage offset rebasing, cyclic frame rebuilding, and cable redundancy closure.
 * Traces to REQ-00005, CS-0090.
 */
final class TopologyAggregationServiceTest {

    /** Base DOR fixture. */
    private static final Path DOR_FIXTURE = Path.of("src/test/resources/eni/DOR.xml.gz");

    /** Expected DOR slave count. */
    private static final int DOR_SLAVE_COUNT = 15;

    /** Parser under test helper. */
    private final EniXmlParser parser = new EniXmlParser();

    /** Aggregation service under test. */
    private final TopologyAggregationService service = new TopologyAggregationService();

    /** Validation engine for composed topology sanity. */
    private final EniValidationEngine validationEngine = new EniValidationEngine();

    @Test
    void buildsTopologyGraphFromDor() {
        final EniDocument dor = parser.parse(DOR_FIXTURE);
        final TopologyGraph graph = service.buildGraph(dor);
        assertThat(graph.nodes()).hasSize(DOR_SLAVE_COUNT + 1);
        assertThat(graph.edges()).isNotEmpty();
        assertThat(graph.hotConnectGroups()).hasSize(2);
    }

    @Test
    void aggregatesBaseAndBranchTopologiesWithUniqueAddressesAndRedundancy() {
        final EniDocument base = parser.parse(DOR_FIXTURE);
        final EniDocument branch = parser.parse(DOR_FIXTURE);
        final TopologyAggregationRequest req = new TopologyAggregationRequest(
            0,
            'B',
            true,
            "001122334466",
            true
        );

        final EniDocument composed = service.aggregate(base, List.of(branch), req);
        assertThat(composed.slaves()).hasSize(DOR_SLAVE_COUNT * 2);
        assertThat(composed.master().redundancy().enabled()).isTrue();
        assertThat(composed.master().redundancy().secondarySourceMac()).isEqualTo("001122334466");
        assertThat(composed.cyclic().frames()).isNotEmpty();

        final Set<Integer> uniquePhys = new HashSet<>();
        for (final EniSlave slave : composed.slaves()) {
            assertThat(uniquePhys.add(slave.identity().physAddr())).isTrue();
        }
        final ValidationReport report = validationEngine.validate(composed);
        assertThat(report.valid()).isTrue();

        final TopologyGraph graph = service.buildGraph(composed);
        assertThat(graph.redundancyEnabled()).isTrue();
        assertThat(graph.edges()).anyMatch(edge -> "REDUNDANCY_RETURN".equals(edge.linkType()));
    }

    @Test
    void rejectsInvalidPortLetterInRequest() {
        assertThatThrownBy(() -> new TopologyAggregationRequest(0, 'Z', false, "", true))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
