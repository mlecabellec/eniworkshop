package fr.lecabellec.eniworkshop.eni.model;

import java.util.List;
import java.util.Objects;

/**
 * EtherCAT Slave Controller SyncManager configuration (Sm0..Sm3+ in ETG.2100).
 *
 * @param index        SyncManager index (0..7)
 * @param type         SyncManager type ("MbxOut", "MbxIn", "Outputs", "Inputs")
 * @param startAddress ESC DPRAM start address
 * @param defaultSize  configured default byte size
 * @param controlByte  ESC SyncManager control register byte
 * @param enable       whether this SyncManager is enabled
 * @param pdoIndices   PDO object indices assigned to this SyncManager
 *
 * <p>Requirement: REQ-00002, REQ-00003, Constraint: CS-0030.1.
 */
public record SyncManagerConfig(
        int index,
        String type,
        int startAddress,
        int defaultSize,
        int controlByte,
        boolean enable,
        List<Integer> pdoIndices) {

    /** Maximum ESC SyncManager index (0..15). */
    private static final int MAX_SM_INDEX = 15;

    /** Maximum unsigned 8-bit control byte value. */
    private static final int MAX_UINT8 = 0xFF;

    /**
     * Compact constructor enforcing bounds and defensive copying.
     */
    public SyncManagerConfig {
        Objects.requireNonNull(type, "SyncManager type must not be null");
        pdoIndices = List.copyOf(Objects.requireNonNull(pdoIndices, "SyncManager pdoIndices must not be null"));
        if (index < 0 || index > MAX_SM_INDEX) {
            throw new IllegalArgumentException("SyncManager index out of range: " + index);
        }
        if (startAddress < 0 || startAddress > EthercatConstants.MAX_UINT16) {
            throw new IllegalArgumentException("SyncManager startAddress out of range: " + startAddress);
        }
        if (defaultSize < 0 || defaultSize > EthercatConstants.MAX_UINT16) {
            throw new IllegalArgumentException("SyncManager defaultSize out of range: " + defaultSize);
        }
        if (controlByte < 0 || controlByte > MAX_UINT8) {
            throw new IllegalArgumentException("SyncManager controlByte out of range: " + controlByte);
        }
    }
}
