package fr.lecabellec.eniworkshop.eni.model;

/**
 * Master Ethernet over EtherCAT (EoE) limits (ETG.2100 Master/EoE).
 *
 * @param maxPorts  maximum virtual EoE switch ports
 * @param maxFrames maximum queued EoE frames
 * @param maxMacs   maximum learned MAC entries
 *
 * <p>Requirement: REQ-00002, Constraint: CS-0030.1.
 */
public record MasterEoeConfig(
        int maxPorts,
        int maxFrames,
        int maxMacs) {

    /**
     * Compact constructor validating non-negative EoE bounds.
     */
    public MasterEoeConfig {
        if (maxPorts < 0 || maxFrames < 0 || maxMacs < 0) {
            throw new IllegalArgumentException("MasterEoeConfig limits must be non-negative");
        }
    }
}
