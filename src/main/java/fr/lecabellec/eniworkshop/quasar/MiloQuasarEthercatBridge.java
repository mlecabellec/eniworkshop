package fr.lecabellec.eniworkshop.quasar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.lecabellec.eniworkshop.EniWorkshopException;
import fr.lecabellec.eniworkshop.messaging.WorkshopEventPublisher;
import fr.lecabellec.eniworkshop.persistence.EniRepositoryService;
import fr.lecabellec.eniworkshop.persistence.EniRepositoryService.ImportResult;
import jakarta.annotation.PreDestroy;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Eclipse Milo OPC-UA + JeroMQ ZeroMQ implementation of {@link QuasarEthercatBridge}
 * targeting Quasar's {@code OpcUaServerService} and {@code resoem::EthercatMasterService}.
 *
 * <p>Requirement: REQ-00008, Feature: FR-00008, Task: TSK-00008, Constraint: CS-0030.8, CS-0030.9.
 */
@Service
public final class MiloQuasarEthercatBridge implements QuasarEthercatBridge {

    /** SLF4J logger. */
    private static final Logger LOG = LoggerFactory.getLogger(MiloQuasarEthercatBridge.class);

    /** OPC-UA call timeout in seconds. */
    private static final long OPCUA_TIMEOUT_SECONDS = 10L;

    /** ZeroMQ poll receive timeout in milliseconds. */
    private static final int ZMQ_POLL_TIMEOUT_MS = 250;

    /** Quasar configuration properties. */
    private final QuasarProperties properties;

    /** ENI persistence repository service. */
    private final EniRepositoryService repositoryService;

    /** STOMP/Kafka event publisher. */
    private final WorkshopEventPublisher eventPublisher;

    /** JSON mapper for Quasar NamedMethod JSON-in/JSON-out payloads. */
    private final ObjectMapper objectMapper;

    /** Active Eclipse Milo client (guarded by synchronized methods). */
    private OpcUaClient client;

    /** Currently connected endpoint URL. */
    private String activeEndpoint;

    /**
     * Constructs a {@link MiloQuasarEthercatBridge}.
     *
     * @param properties        Quasar endpoint configuration
     * @param repositoryService ENI repository service
     * @param eventPublisher    event publisher
     * @param objectMapper      Jackson object mapper
     */
    @Autowired
    public MiloQuasarEthercatBridge(
            final QuasarProperties properties,
            final EniRepositoryService repositoryService,
            final WorkshopEventPublisher eventPublisher,
            final ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.repositoryService = Objects.requireNonNull(repositoryService, "repositoryService must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.activeEndpoint = properties.opcuaUrl();
    }

    @Override
    public synchronized QuasarBusStatus connect(final String endpointUrl) {
        Objects.requireNonNull(endpointUrl, "endpointUrl must not be null");
        disconnect();
        this.activeEndpoint = endpointUrl;
        try {
            final OpcUaClient created = OpcUaClient.create(endpointUrl);
            created.connect().get(OPCUA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            this.client = created;
            eventPublisher.publish("QUASAR_CONNECTED", endpointUrl, "Connected to Quasar OPC-UA server");
            return refreshStatus();
        } catch (final Exception ex) {
            this.client = null;
            if (LOG.isWarnEnabled()) {
                LOG.warn("Unable to connect to Quasar OPC-UA at {}: {}", endpointUrl, ex.getMessage());
            }
            return new QuasarBusStatus(false, endpointUrl, 0, "OFFLINE", List.of(ex.getMessage()), Instant.now());
        }
    }

    @Override
    @PreDestroy
    public synchronized void disconnect() {
        if (this.client != null) {
            try {
                this.client.disconnect().get(OPCUA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (final Exception ex) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Error disconnecting Milo OPC-UA client: {}", ex.getMessage());
                }
            } finally {
                this.client = null;
            }
        }
    }

    @Override
    public synchronized QuasarBusStatus refreshStatus() {
        if (this.client == null) {
            return new QuasarBusStatus(
                    false,
                    activeEndpoint,
                    0,
                    "DISCONNECTED",
                    List.of("Not connected to Quasar OpcUaServerService"),
                    Instant.now());
        }
        try {
            final String responseJson = invokeMasterMethod("refreshStatus", "{}");
            final JsonNode root = objectMapper.readTree(responseJson);
            final int slaveCount = root.path("slaveCount").asInt(0);
            final String redundancy = root.path("redundancyState").asText("NONE");
            final List<String> summaries = new ArrayList<>();
            if (root.has("slaves") && root.get("slaves").isArray()) {
                for (final JsonNode s : root.get("slaves")) {
                    summaries.add(s.toString());
                }
            }
            return new QuasarBusStatus(true, activeEndpoint, slaveCount, redundancy, summaries, Instant.now());
        } catch (final Exception ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Quasar refreshStatus failed: {}", ex.getMessage());
            }
            return new QuasarBusStatus(false, activeEndpoint, 0, "ERROR", List.of(ex.getMessage()), Instant.now());
        }
    }

    @Override
    public synchronized String forceInit() {
        ensureConnected();
        final String res = invokeMasterMethod("forceInit", "{}");
        eventPublisher.publish("QUASAR_FORCE_INIT", activeEndpoint, res);
        return res;
    }

    @Override
    public synchronized String reconfigureSlave(final int slaveIndex) {
        if (slaveIndex < 0) {
            throw new IllegalArgumentException("slaveIndex must be non-negative: " + slaveIndex);
        }
        ensureConnected();
        final String res = invokeMasterMethod("reconfigureSlave", "{\"slaveIndex\":" + slaveIndex + "}");
        eventPublisher.publish("QUASAR_RECONFIGURE_SLAVE", Integer.toString(slaveIndex), res);
        return res;
    }

    @Override
    public synchronized QuasarScanResult scanAndBuildEni(final String masterName, final String reference) {
        Objects.requireNonNull(masterName, "masterName must not be null");
        Objects.requireNonNull(reference, "reference must not be null");
        if (this.client == null) {
            return new QuasarScanResult(
                    false,
                    "Quasar server is not connected at " + activeEndpoint,
                    Optional.empty(),
                    Optional.empty());
        }
        try {
            final Path tempEni = Files.createTempFile("quasar-scan-", ".xml");
            try {
                final String args = objectMapper.writeValueAsString(
                        java.util.Map.of("masterName", masterName, "filepath", tempEni.toString()));
                final String res = invokeMasterMethod("exportEni", args);
                final JsonNode node = objectMapper.readTree(res);
                final String xmlContent;
                if (node.has("xml") && !node.get("xml").asText().isBlank()) {
                    xmlContent = node.get("xml").asText();
                } else if (Files.exists(tempEni) && Files.size(tempEni) > 0L) {
                    xmlContent = Files.readString(tempEni, StandardCharsets.UTF_8);
                } else {
                    return new QuasarScanResult(false, "Quasar exportEni returned empty XML: " + res,
                            Optional.empty(), Optional.empty());
                }
                final ImportResult imported = repositoryService.importEni(
                        reference,
                        masterName + " (Quasar Scan)",
                        "1.0.0-scan",
                        xmlContent.getBytes(StandardCharsets.UTF_8),
                        List.of("quasar-scan"));
                eventPublisher.publish("QUASAR_SCAN", imported.metadata().id(),
                        "Scanned " + imported.document().slaves().size() + " slaves from Quasar");
                return new QuasarScanResult(
                        true,
                        "Scanned and imported ENI with " + imported.document().slaves().size() + " slaves",
                        Optional.of(imported.metadata().id()),
                        Optional.of(xmlContent));
            } finally {
                Files.deleteIfExists(tempEni);
            }
        } catch (final Exception ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Quasar scanAndBuildEni failed: {}", ex.getMessage());
            }
            return new QuasarScanResult(false, "Scan failed: " + ex.getMessage(),
                    Optional.empty(), Optional.empty());
        }
    }

    @Override
    public synchronized QuasarCompareResult pushAndTestEni(final String eniCatalogId) {
        Objects.requireNonNull(eniCatalogId, "eniCatalogId must not be null");
        if (this.client == null) {
            return new QuasarCompareResult(false, "Quasar server is not connected at " + activeEndpoint, List.of());
        }
        try {
            final byte[] xmlBytes = repositoryService.exportXmlBytes(eniCatalogId);
            final Path tempEni = Files.createTempFile("quasar-test-", ".xml");
            try {
                Files.write(tempEni, xmlBytes);
                final String args = objectMapper.writeValueAsString(java.util.Map.of("filepath", tempEni.toString()));
                final String loadRes = invokeMasterMethod("loadEni", args);
                final String cmpRes = invokeMasterMethod("compareEni", args);
                final JsonNode cmpNode = objectMapper.readTree(cmpRes);
                final boolean matched = cmpNode.path("matched").asBoolean(false);
                final List<String> diffs = new ArrayList<>();
                if (cmpNode.has("differences") && cmpNode.get("differences").isArray()) {
                    for (final JsonNode d : cmpNode.get("differences")) {
                        diffs.add(d.asText());
                    }
                }
                eventPublisher.publish("QUASAR_TEST_ENI", eniCatalogId, "loadEni=" + loadRes + " matched=" + matched);
                return new QuasarCompareResult(matched, "Tested ENI against live Quasar bus", diffs);
            } finally {
                Files.deleteIfExists(tempEni);
            }
        } catch (final Exception ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Quasar pushAndTestEni failed: {}", ex.getMessage());
            }
            return new QuasarCompareResult(false, "ENI test failed: " + ex.getMessage(), List.of(ex.getMessage()));
        }
    }

    /**
     * Polls one cyclic telemetry snapshot from Quasar ZeroMQ PUB socket with bounded timeout.
     *
     * @return optional telemetry JSON payload
     */
    public Optional<String> pollZeroMqTelemetrySample() {
        try (ZContext ctx = new ZContext(); ZMQ.Socket sub = ctx.createSocket(SocketType.SUB)) {
            sub.setReceiveTimeOut(ZMQ_POLL_TIMEOUT_MS);
            sub.connect(properties.zmqEndpoint());
            sub.subscribe(ZMQ.SUBSCRIPTION_ALL);
            final String msg = sub.recvStr();
            return Optional.ofNullable(msg);
        } catch (final RuntimeException ex) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No ZeroMQ telemetry sample available at {}: {}", properties.zmqEndpoint(), ex.getMessage());
            }
            return Optional.empty();
        }
    }

    /**
     * Invokes a NamedMethod on Quasar's EthercatMasterService over Eclipse Milo.
     *
     * @param methodName method name under {@code masterPath}
     * @param jsonArgs   JSON input string
     * @return JSON output string
     */
    private String invokeMasterMethod(final String methodName, final String jsonArgs) {
        ensureConnected();
        final int ns = properties.namespaceIdx();
        final NodeId objectId = new NodeId(ns, properties.masterPath());
        final NodeId methodId = new NodeId(ns, properties.masterPath() + "/" + methodName);
        final CallMethodRequest req = new CallMethodRequest(
                objectId,
                methodId,
                new Variant[] {new Variant(jsonArgs)});
        try {
            final CompletableFuture<CallMethodResult> fut = client.call(req);
            final CallMethodResult result = fut.get(OPCUA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!result.getStatusCode().isGood()) {
                throw new EniWorkshopException("Quasar OPC-UA method " + methodName
                        + " returned bad status: " + result.getStatusCode());
            }
            final Variant[] outputs = result.getOutputArguments();
            if (outputs != null && outputs.length > 0 && outputs[0].getValue() != null) {
                return outputs[0].getValue().toString();
            }
            return "{}";
        } catch (final EniWorkshopException ex) {
            throw ex;
        } catch (final Exception ex) {
            throw new EniWorkshopException("OPC-UA call failed for " + methodName, ex);
        }
    }

    /**
     * Ensures an active OPC-UA client exists before method invocation.
     */
    private void ensureConnected() {
        if (this.client == null) {
            throw new EniWorkshopException("Not connected to Quasar OPC-UA server at " + activeEndpoint);
        }
    }
}
