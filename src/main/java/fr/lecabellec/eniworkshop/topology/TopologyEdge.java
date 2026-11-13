package fr.lecabellec.eniworkshop.topology;

import java.util.Objects;

/**
 * Directed physical EtherCAT link connecting an upstream port to a downstream slave.
 *
 * @param id         unique edge identifier
 * @param sourceId   upstream node ID
 * @param targetId   downstream node ID
 * @param sourcePort upstream port ('A'..'D' or 'M' for Master)
 * @param targetPort downstream port ('A' or 'R' for Redundant return)
 * @param linkType   "ETHERNET", "EBUS", "FIBER", or "REDUNDANCY_RETURN"
 *
 * <p>Requirement: REQ-00005, REQ-00010, Constraint: CS-0030.1.
 */
public record TopologyEdge(
        String id,
        String sourceId,
        String targetId,
        String sourcePort,
        String targetPort,
        String linkType) {

    /**
     * Compact constructor enforcing non-null edge fields.
     */
    public TopologyEdge {
        Objects.requireNonNull(id, "TopologyEdge id must not be null");
        Objects.requireNonNull(sourceId, "TopologyEdge sourceId must not be null");
        Objects.requireNonNull(targetId, "TopologyEdge targetId must not be null");
        Objects.requireNonNull(sourcePort, "TopologyEdge sourcePort must not be null");
        Objects.requireNonNull(targetPort, "TopologyEdge targetPort must not be null");
        Objects.requireNonNull(linkType, "TopologyEdge linkType must not be null");
    }
}
