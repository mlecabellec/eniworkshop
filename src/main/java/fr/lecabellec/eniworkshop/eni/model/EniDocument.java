package fr.lecabellec.eniworkshop.eni.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Root ETG.2100 EtherCAT Network Information (ENI) AST document.
 *
 * @param version      ENI schema version attribute (e.g., "1.3")
 * @param master       master configuration
 * @param slaves       ordered slave configurations
 * @param cyclic       cyclic frame schedule
 * @param processImage master process image variable layout
 *
 * <p>Requirement: REQ-00002, Feature: FR-00002, Constraint: CS-0030.2.
 */
public record EniDocument(
        String version,
        EniMaster master,
        List<EniSlave> slaves,
        CyclicConfig cyclic,
        ProcessImage processImage) {

    /**
     * Compact constructor enforcing non-null fields, defensive slave list copy, and max slave bound.
     */
    public EniDocument {
        Objects.requireNonNull(version, "EniDocument version must not be null");
        Objects.requireNonNull(master, "EniDocument master must not be null");
        slaves = List.copyOf(Objects.requireNonNull(slaves, "EniDocument slaves must not be null"));
        Objects.requireNonNull(cyclic, "EniDocument cyclic must not be null");
        Objects.requireNonNull(processImage, "EniDocument processImage must not be null");
        if (slaves.size() > EthercatConstants.MAX_ENI_SLAVES) {
            throw new IllegalArgumentException(
                    "Slave count exceeds MAX_ENI_SLAVES (" + EthercatConstants.MAX_ENI_SLAVES + "): " + slaves.size());
        }
    }

    /**
     * Finds a slave by its configured physical station address.
     *
     * @param physAddr 16-bit configured station address
     * @return matching slave if present
     */
    public Optional<EniSlave> findSlaveByPhysAddr(final int physAddr) {
        for (final EniSlave slave : slaves) {
            if (slave.identity().physAddr() == physAddr) {
                return Optional.of(slave);
            }
        }
        return Optional.empty();
    }

    /**
     * Counts the number of Hot Connect group heads in this document.
     *
     * @return count of slaves declaring a HotConnect block
     */
    public int hotConnectGroupCount() {
        int count = 0;
        for (final EniSlave slave : slaves) {
            if (slave.hotConnect().isPresent()) {
                count++;
            }
        }
        return count;
    }
}
