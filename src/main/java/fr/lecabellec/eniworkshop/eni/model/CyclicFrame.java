package fr.lecabellec.eniworkshop.eni.model;

import java.util.List;
import java.util.Objects;

/**
 * Cyclic Ethernet frame containing one or more EtherCAT datagrams (ETG.2100 Cyclic/Frame).
 *
 * @param commands ordered datagrams packed inside this Ethernet frame
 *
 * <p>Requirement: REQ-00002, REQ-00003, Constraint: CS-0030.2.
 */
public record CyclicFrame(List<CyclicCommand> commands) {

    /**
     * Compact constructor enforcing defensive list copy.
     */
    public CyclicFrame {
        commands = List.copyOf(Objects.requireNonNull(commands, "CyclicFrame commands must not be null"));
    }

    /**
     * Computes total EtherCAT payload bytes (header + datagram headers/payloads/WKC) excluding Ethernet header.
     *
     * @return EtherCAT frame payload length in bytes
     */
    public int totalEthercatPayloadBytes() {
        int total = EthercatConstants.ETHERCAT_HEADER_BYTES;
        for (final CyclicCommand cmd : commands) {
            total += EthercatConstants.DATAGRAM_HEADER_BYTES
                    + cmd.dataLength()
                    + EthercatConstants.DATAGRAM_WKC_BYTES;
        }
        return total;
    }
}
