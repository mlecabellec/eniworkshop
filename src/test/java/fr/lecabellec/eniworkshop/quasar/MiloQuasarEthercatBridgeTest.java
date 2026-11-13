package fr.lecabellec.eniworkshop.quasar;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.lecabellec.eniworkshop.EniWorkshopException;
import fr.lecabellec.eniworkshop.catalog.SlaveCatalogService;
import fr.lecabellec.eniworkshop.eni.xml.EniXmlParser;
import fr.lecabellec.eniworkshop.eni.xml.EniXmlWriter;
import fr.lecabellec.eniworkshop.messaging.WorkshopEventPublisher;
import fr.lecabellec.eniworkshop.persistence.EniRepositoryService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link MiloQuasarEthercatBridge} disconnected safeguards and ZeroMQ/OPC-UA error handling.
 * Traces to REQ-00008, CS-0090.
 */
final class MiloQuasarEthercatBridgeTest {

    @Test
    void handlesDisconnectedStateSafelyForStatusScanTestAndCommands() {
        final QuasarProperties props = new QuasarProperties(
            "opc.tcp://127.0.0.1:48499",
            1,
            "EthercatMasterService",
            "tcp://127.0.0.1:55599",
            false
        );
        final EniRepositoryService repo = new EniRepositoryService(
            new EniXmlParser(),
            new EniXmlWriter(),
            new SlaveCatalogService(),
            null,
            null,
            null
        );
        final WorkshopEventPublisher publisher = new WorkshopEventPublisher(null, null, false, "test-events");
        final MiloQuasarEthercatBridge bridge = new MiloQuasarEthercatBridge(
            props,
            repo,
            publisher,
            new ObjectMapper()
        );

        final QuasarBusStatus initial = bridge.refreshStatus();
        assertThat(initial.connected()).isFalse();
        assertThat(initial.redundancyMode()).isEqualTo("DISCONNECTED");

        final QuasarScanResult scan = bridge.scanAndBuildEni("Bus1", "REF1");
        assertThat(scan.success()).isFalse();

        final QuasarCompareResult cmp = bridge.pushAndTestEni("missing-id");
        assertThat(cmp.matched()).isFalse();

        assertThatThrownBy(bridge::forceInit).isInstanceOf(EniWorkshopException.class);
        assertThatThrownBy(() -> bridge.reconfigureSlave(0)).isInstanceOf(EniWorkshopException.class);
        assertThatThrownBy(() -> bridge.reconfigureSlave(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(bridge.pollZeroMqTelemetrySample()).isEmpty();
    }
}
