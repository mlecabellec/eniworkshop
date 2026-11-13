package fr.lecabellec.eniworkshop.eni.model;

import java.util.List;
import java.util.Objects;

/**
 * Process Data Object (RxPDO or TxPDO) definition for a slave (ETG.2100).
 *
 * @param index    PDO object index (e.g., 0x1600..0x17FF or 0x1A00..0x1BFF)
 * @param name     PDO name
 * @param flags    PDO SyncManager/SyncUnit and Fixed/Mandatory/Virtual/Direction flags
 * @param excludes mutually exclusive PDO indices listed under Exclude elements
 * @param entries  ordered PDO entry mappings
 *
 * <p>Requirement: REQ-00002, Constraint: CS-0030.2.
 */
public record PdoMapping(
        int index,
        String name,
        PdoFlags flags,
        List<Integer> excludes,
        List<PdoEntry> entries) {

    /**
     * Compact constructor enforcing non-null invariants and defensive list copying.
     */
    public PdoMapping {
        Objects.requireNonNull(name, "PdoMapping name must not be null");
        Objects.requireNonNull(flags, "PdoMapping flags must not be null");
        excludes = List.copyOf(Objects.requireNonNull(excludes, "PdoMapping excludes must not be null"));
        entries = List.copyOf(Objects.requireNonNull(entries, "PdoMapping entries must not be null"));
        if (index < 0 || index > EthercatConstants.MAX_UINT16) {
            throw new IllegalArgumentException("PdoMapping index out of range: " + index);
        }
    }

    /**
     * Computes total bit length across all mapped entries in this PDO.
     *
     * @return sum of entry bit lengths
     */
    public int totalBitLength() {
        int sum = 0;
        for (final PdoEntry entry : entries) {
            sum += entry.bitLen();
        }
        return sum;
    }
}
