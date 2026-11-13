package fr.lecabellec.eniworkshop.web;

import fr.lecabellec.eniworkshop.quasar.QuasarBusStatus;
import fr.lecabellec.eniworkshop.quasar.QuasarCompareResult;
import fr.lecabellec.eniworkshop.quasar.QuasarEthercatBridge;
import fr.lecabellec.eniworkshop.quasar.QuasarProperties;
import fr.lecabellec.eniworkshop.quasar.QuasarScanResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * REST controller exposing live Quasar ({@code resoem}/{@code resoes}) bus operations.
 *
 * <p>Requirement: REQ-00008, REQ-00009, Feature: FR-00008.
 */
@RestController
@RequestMapping("/api/quasar")
@Tag(name = "Quasar Integration", description = "Connect to Quasar OPC-UA server, scan bus to ENI, and test ENI live")
public final class QuasarController {

    /** Quasar OPC-UA/ZeroMQ bridge. */
    private final QuasarEthercatBridge quasarBridge;

    /** Default endpoint configuration. */
    private final QuasarProperties properties;

    /**
     * Constructs a {@link QuasarController}.
     *
     * @param quasarBridge Quasar bridge service
     * @param properties   Quasar configuration properties
     */
    @Autowired
    public QuasarController(final QuasarEthercatBridge quasarBridge, final QuasarProperties properties) {
        this.quasarBridge = Objects.requireNonNull(quasarBridge, "quasarBridge must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * Connects to the Quasar OPC-UA server.
     *
     * @param endpoint optional endpoint URL override
     * @return live bus status
     */
    @PostMapping("/connect")
    @Operation(summary = "Connect to Quasar OpcUaServerService")
    public QuasarBusStatus connect(@RequestParam(value = "endpoint", defaultValue = "") final String endpoint) {
        final String target = endpoint.isBlank() ? properties.opcuaUrl() : endpoint;
        return quasarBridge.connect(target);
    }

    /**
     * Disconnects from Quasar.
     */
    @PostMapping("/disconnect")
    @Operation(summary = "Disconnect from Quasar OpcUaServerService")
    public void disconnect() {
        quasarBridge.disconnect();
    }

    /**
     * Refreshes and returns current Quasar EtherCAT bus status.
     *
     * @return bus status
     */
    @GetMapping("/status")
    @Operation(summary = "Refresh live EtherCAT slave status via Quasar")
    public QuasarBusStatus status() {
        return quasarBridge.refreshStatus();
    }

    /**
     * Commands all slaves to INIT via Quasar {@code forceInit}.
     *
     * @return command result
     */
    @PostMapping("/force-init")
    @Operation(summary = "Force all live EtherCAT slaves to INIT state")
    public CommandResponse forceInit() {
        return new CommandResponse(quasarBridge.forceInit());
    }

    /**
     * Reconfigures a specific slave index via Quasar {@code reconfigureSlave}.
     *
     * @param slaveIndex 0-based slave index
     * @return command result
     */
    @PostMapping("/slaves/{slaveIndex}/reconfigure")
    @Operation(summary = "Reconfigure a specific slave via Quasar")
    public CommandResponse reconfigureSlave(@PathVariable("slaveIndex") final int slaveIndex) {
        return new CommandResponse(quasarBridge.reconfigureSlave(slaveIndex));
    }

    /**
     * Scans live/virtual bus on Quasar and constructs an ENI document.
     *
     * @param masterName master name
     * @param reference  catalog reference
     * @return scan outcome
     */
    @PostMapping("/scan")
    @Operation(summary = "Scan EtherCAT bus via Quasar and build ENI")
    public QuasarScanResult scan(
            @RequestParam(value = "masterName", defaultValue = "Quasar Master") final String masterName,
            @RequestParam(value = "reference", defaultValue = "REF-SCAN") final String reference) {
        return quasarBridge.scanAndBuildEni(masterName, reference);
    }

    /**
     * Pushes a catalog ENI file to Quasar and runs {@code compareEni}.
     *
     * @param eniId ENI catalog ID
     * @return comparison result
     */
    @PostMapping("/test/{eniId}")
    @Operation(summary = "Load and compare ENI file against live Quasar bus")
    public QuasarCompareResult testEni(@PathVariable("eniId") final String eniId) {
        return quasarBridge.pushAndTestEni(eniId);
    }

    /**
     * Simple command execution wrapper.
     *
     * @param result raw command response string
     */
    public record CommandResponse(String result) {
    }
}
