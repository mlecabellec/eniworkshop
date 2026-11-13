package fr.lecabellec.eniworkshop.eni.model;

import java.util.Objects;

/**
 * Single variable/entry mapped inside an EtherCAT RxPDO or TxPDO (ETG.2100).
 *
 * @param index    CANopen object dictionary index (0x0000..0xFFFF)
 * @param subIndex object subindex (0..255)
 * @param bitLen   entry size in bits
 * @param name     entry symbolic name
 * @param comment  optional entry comment
 * @param dataType EtherCAT/IEC61131 data type name (e.g., "BOOL", "INT", "UINT")
 * @param dscale   optional DataType DScale attribute
 *
 * <p>Requirement: REQ-00002, Constraint: CS-0030.1.
 */
public record PdoEntry(
        int index,
        int subIndex,
        int bitLen,
        String name,
        String comment,
        String dataType,
        String dscale) {

    /** Maximum 8-bit subindex value. */
    private static final int MAX_SUBINDEX = 0xFF;

    /**
     * Compact constructor validating index, subindex, bit length, and non-null strings.
     */
    public PdoEntry {
        Objects.requireNonNull(name, "PdoEntry name must not be null");
        Objects.requireNonNull(comment, "PdoEntry comment must not be null");
        Objects.requireNonNull(dataType, "PdoEntry dataType must not be null");
        Objects.requireNonNull(dscale, "PdoEntry dscale must not be null");
        if (index < 0 || index > EthercatConstants.MAX_UINT16) {
            throw new IllegalArgumentException("PdoEntry index out of range: " + index);
        }
        if (subIndex < 0 || subIndex > MAX_SUBINDEX) {
            throw new IllegalArgumentException("PdoEntry subIndex out of range: " + subIndex);
        }
        if (bitLen < 0) {
            throw new IllegalArgumentException("PdoEntry bitLen must be non-negative: " + bitLen);
        }
    }
}
