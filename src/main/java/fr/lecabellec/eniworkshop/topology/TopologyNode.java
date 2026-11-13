package fr.lecabellec.eniworkshop.topology;

import java.util.Objects;

/**
 * Graph node representing the EtherCAT Master or a Slave/Module in topology diagrams.
 *
 * @param id              unique node ID (e.g., "master" or "slave-5000")
 * @param label           display label
 * @param nodeType        "MASTER", "COUPLER", "EXTENSION_CARD", "JUNCTION", or "SLAVE"
 * @param physAddr        configured station address (0 for master)
 * @param autoIncAddr     auto-increment address
 * @param physics         port physics string
 * @param hotConnectGroup HotConnect group name (empty if static)
 * @param dcEnabled       true if Distributed Clocks active
 * @param rxPdoBits       total RxPDO bits
 * @param txPdoBits       total TxPDO bits
 *
 * <p>Requirement: REQ-00005, REQ-00010, Constraint: CS-0030.1.
 */
public record TopologyNode(
        String id,
        String label,
        String nodeType,
        int physAddr,
        int autoIncAddr,
        String physics,
        String hotConnectGroup,
        boolean dcEnabled,
        int rxPdoBits,
        int txPdoBits) {

    /**
     * Compact constructor enforcing non-null node fields.
     */
    public TopologyNode {
        Objects.requireNonNull(id, "TopologyNode id must not be null");
        Objects.requireNonNull(label, "TopologyNode label must not be null");
        Objects.requireNonNull(nodeType, "TopologyNode nodeType must not be null");
        Objects.requireNonNull(physics, "TopologyNode physics must not be null");
        Objects.requireNonNull(hotConnectGroup, "TopologyNode hotConnectGroup must not be null");
    }
}
