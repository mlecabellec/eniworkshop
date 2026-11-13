package fr.lecabellec.eniworkshop.quasar;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Live EtherCAT bus status retrieved from Quasar's {@code resoem::EthercatMasterService}.
 *
 * @param connected      true when OPC-UA session to Quasar is active
 * @param endpointUrl    connected OPC-UA URL
 * @param slaveCount     discovered live slave count
 * @param redundancyMode active cable redundancy mode
 * @param slaveSummaries per-slave live ESM state and AL status summaries
 * @param timestamp      observation timestamp
 *
 * <p>Requirement: REQ-00008, Constraint: CS-0030.2.
 */
public record QuasarBusStatus(
        boolean connected,
        String endpointUrl,
        int slaveCount,
        String redundancyMode,
        List<String> slaveSummaries,
        Instant timestamp) {

    /**
     * Compact constructor enforcing non-null fields and defensive list copy.
     */
    public QuasarBusStatus {
        Objects.requireNonNull(endpointUrl, "endpointUrl must not be null");
        Objects.requireNonNull(redundancyMode, "redundancyMode must not be null");
        slaveSummaries = List.copyOf(Objects.requireNonNull(slaveSummaries, "slaveSummaries must not be null"));
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
