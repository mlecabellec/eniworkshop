package fr.lecabellec.eniworkshop.quasar;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

/**
 * Externalized configuration properties for Quasar OPC-UA and ZeroMQ integration.
 *
 * @param opcuaUrl      Quasar OPC-UA endpoint URL (default opc.tcp://localhost:4840)
 * @param namespaceIdx  Quasar OPC-UA namespace index (default 1)
 * @param masterPath    OPC-UA browse path prefix to resoem::EthercatMasterService
 * @param zmqEndpoint   ZeroMQ PUB/SUB endpoint for cyclic bus measurements
 * @param autoConnect   whether to attempt OPC-UA connection at web startup
 *
 * <p>Requirement: REQ-00008, Constraint: CS-0030.1.
 */
@ConfigurationProperties(prefix = "eniworkshop.quasar")
public record QuasarProperties(
        String opcuaUrl,
        int namespaceIdx,
        String masterPath,
        String zmqEndpoint,
        boolean autoConnect) {

    /** Default Quasar OPC-UA URL. */
    public static final String DEFAULT_OPCUA_URL = "opc.tcp://localhost:4840";

    /** Default ZeroMQ endpoint. */
    public static final String DEFAULT_ZMQ_ENDPOINT = "tcp://localhost:5556";

    /**
     * Compact constructor applying defaults when fields are null.
     */
    public QuasarProperties {
        opcuaUrl = opcuaUrl != null && !opcuaUrl.isBlank() ? opcuaUrl : DEFAULT_OPCUA_URL;
        namespaceIdx = namespaceIdx > 0 ? namespaceIdx : 1;
        masterPath = Objects.requireNonNullElse(masterPath, "EthercatMasterService");
        zmqEndpoint = zmqEndpoint != null && !zmqEndpoint.isBlank() ? zmqEndpoint : DEFAULT_ZMQ_ENDPOINT;
    }
}
