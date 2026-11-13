package fr.lecabellec.eniworkshop.topology;

import java.util.Objects;

/**
 * Parameters controlling sub-topology aggregation onto a base EtherCAT bus.
 *
 * @param attachToPhysAddr       upstream base slave PhysAddr (0 to append after last slave)
 * @param attachPort             upstream ESC port ('A'..'D')
 * @param enableCableRedundancy  true to enable master cable redundancy ring closure
 * @param secondarySourceMac     secondary NIC MAC when redundancy enabled
 * @param recalculateCyclic      true to rebuild LRW/LRD/LWR cyclic frames from merged ProcessImage
 *
 * <p>Requirement: REQ-00005, Feature: FR-00005, Constraint: CS-0030.1.
 */
public record TopologyAggregationRequest(
        int attachToPhysAddr,
        char attachPort,
        boolean enableCableRedundancy,
        String secondarySourceMac,
        boolean recalculateCyclic) {

    /**
     * Compact constructor validating port letter and non-null MAC string.
     */
    public TopologyAggregationRequest {
        Objects.requireNonNull(secondarySourceMac, "secondarySourceMac must not be null");
        final char upper = Character.toUpperCase(attachPort);
        if (upper != 'A' && upper != 'B' && upper != 'C' && upper != 'D') {
            throw new IllegalArgumentException("attachPort must be A, B, C, or D: " + attachPort);
        }
        attachPort = upper;
    }

    /**
     * Default aggregation request appending at tail of base bus and rebuilding cyclic frames.
     *
     * @return default request
     */
    public static TopologyAggregationRequest appendAtTail() {
        return new TopologyAggregationRequest(0, 'B', false, "", true);
    }
}
