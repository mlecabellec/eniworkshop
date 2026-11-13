package fr.lecabellec.eniworkshop.eni.model;

import java.util.Objects;

/**
 * Upstream port connection descriptor for an EtherCAT slave (ETG.2100 PreviousPort).
 *
 * @param physAddr configured station address of the upstream slave
 * @param port     upstream ESC port character ('A', 'B', 'C', or 'D')
 * @param selected whether Selected="1" attribute is present
 *
 * <p>Requirement: REQ-00002, REQ-00005, Constraint: CS-0030.1.
 */
public record PreviousPort(
        int physAddr,
        char port,
        boolean selected) {

    /**
     * Compact constructor validating station address and port letter.
     */
    public PreviousPort {
        if (physAddr < 0 || physAddr > EthercatConstants.MAX_UINT16) {
            throw new IllegalArgumentException("PreviousPort physAddr out of range: " + physAddr);
        }
        final char upper = Character.toUpperCase(port);
        if (upper != 'A' && upper != 'B' && upper != 'C' && upper != 'D') {
            throw new IllegalArgumentException("PreviousPort port must be A, B, C, or D: " + port);
        }
        port = upper;
    }

    /**
     * Formats port label cleanly for graph visualization.
     *
     * @return string representation such as "1001:B"
     */
    public String toPortLabel() {
        return Objects.toString(physAddr) + ":" + port;
    }
}
