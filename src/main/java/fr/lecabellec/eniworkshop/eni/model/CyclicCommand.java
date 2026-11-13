package fr.lecabellec.eniworkshop.eni.model;

import java.util.List;
import java.util.Objects;

/**
 * Single cyclic datagram command inside an EtherCAT cyclic Ethernet frame (ETG.2100).
 *
 * @param states     active bus states (e.g., "OP", "SAFEOP")
 * @param comment    human-readable comment
 * @param cmd        EtherCAT datagram command code
 * @param address    logical or physical datagram address
 * @param dataLength payload length in bytes
 * @param cnt        expected working counter
 * @param inputOffs  byte offset in ProcessImage Inputs
 * @param outputOffs byte offset in ProcessImage Outputs
 *
 * <p>Requirement: REQ-00002, REQ-00003, Constraint: CS-0030.2.
 */
public record CyclicCommand(
        List<String> states,
        String comment,
        DatagramCmd cmd,
        DatagramAddress address,
        int dataLength,
        int cnt,
        int inputOffs,
        int outputOffs) {

    /**
     * Compact constructor enforcing non-null invariants and non-negative offsets.
     */
    public CyclicCommand {
        states = List.copyOf(Objects.requireNonNull(states, "CyclicCommand states must not be null"));
        Objects.requireNonNull(comment, "CyclicCommand comment must not be null");
        Objects.requireNonNull(cmd, "CyclicCommand cmd must not be null");
        Objects.requireNonNull(address, "CyclicCommand address must not be null");
        if (dataLength < 0 || cnt < 0 || inputOffs < 0 || outputOffs < 0) {
            throw new IllegalArgumentException("CyclicCommand lengths, cnt, and offsets must be non-negative");
        }
    }
}
