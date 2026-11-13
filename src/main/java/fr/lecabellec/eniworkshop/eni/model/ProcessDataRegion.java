package fr.lecabellec.eniworkshop.eni.model;

/**
 * Bit-level process data region (Send or Recv) declared for a slave (ETG.2100).
 *
 * @param bitStart  starting bit offset in the master process image
 * @param bitLength total bit length in the process image
 *
 * <p>Requirement: REQ-00002, Constraint: CS-0030.1.
 */
public record ProcessDataRegion(
        int bitStart,
        int bitLength) {

    /**
     * Compact constructor enforcing non-negative bit offsets and lengths.
     */
    public ProcessDataRegion {
        if (bitStart < 0 || bitLength < 0) {
            throw new IllegalArgumentException("ProcessDataRegion bitStart/bitLength must be non-negative");
        }
    }
}
