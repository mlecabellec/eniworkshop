package fr.lecabellec.eniworkshop.eni.model;

import java.util.Objects;

/**
 * Single variable mapped in the master ProcessImage Inputs or Outputs (ETG.2100).
 *
 * @param name     hierarchical variable name (e.g., "Slave_5001 [EL3204].RTD Inputs Channel 1.Value")
 * @param comment  optional variable comment
 * @param dataType EtherCAT data type string (e.g., "BOOL", "INT", "BIT2")
 * @param bitSize  variable width in bits
 * @param bitOffs  bit offset inside ProcessImage Inputs or Outputs
 * @param isInput  true for ProcessImage/Inputs, false for ProcessImage/Outputs
 *
 * <p>Requirement: REQ-00002, REQ-00003, Constraint: CS-0030.1.
 */
public record ProcessImageVariable(
        String name,
        String comment,
        String dataType,
        int bitSize,
        int bitOffs,
        boolean isInput) {

    /**
     * Compact constructor enforcing non-null strings and non-negative bit metrics.
     */
    public ProcessImageVariable {
        Objects.requireNonNull(name, "ProcessImageVariable name must not be null");
        Objects.requireNonNull(comment, "ProcessImageVariable comment must not be null");
        Objects.requireNonNull(dataType, "ProcessImageVariable dataType must not be null");
        if (bitSize < 0 || bitOffs < 0) {
            throw new IllegalArgumentException("ProcessImageVariable bitSize and bitOffs must be non-negative");
        }
    }
}
