package fr.lecabellec.eniworkshop.eni.model;

import java.util.List;
import java.util.Objects;

/**
 * CANopen over EtherCAT (CoE) SDO initialization command inside Slave Mailbox/CoE/InitCmds.
 *
 * @param transitions    ESM transitions triggering this SDO transfer
 * @param comment        human-readable comment
 * @param index          CANopen object dictionary index (0x0000..0xFFFF)
 * @param subIndex       CANopen subindex (0..255)
 * @param ccs            CoE Client Command Specifier (1=Download, 2=Upload)
 * @param completeAccess whether Complete Access attribute is set
 * @param fixed          whether Fixed attribute is set
 * @param timeoutMs      timeout in milliseconds
 * @param dataHex        hexadecimal payload bytes
 *
 * <p>Requirement: REQ-00002, Constraint: CS-0030.1.
 */
public record CoeInitCmd(
        List<EsmTransition> transitions,
        String comment,
        int index,
        int subIndex,
        int ccs,
        boolean completeAccess,
        boolean fixed,
        int timeoutMs,
        String dataHex) {

    /** Maximum 8-bit subindex value. */
    private static final int MAX_SUBINDEX = 0xFF;

    /**
     * Compact constructor validating CoE index/subindex bounds and null safety.
     */
    public CoeInitCmd {
        transitions = List.copyOf(Objects.requireNonNull(transitions, "CoeInitCmd transitions must not be null"));
        Objects.requireNonNull(comment, "CoeInitCmd comment must not be null");
        Objects.requireNonNull(dataHex, "CoeInitCmd dataHex must not be null");
        if (index < 0 || index > EthercatConstants.MAX_UINT16) {
            throw new IllegalArgumentException("CoE index out of 16-bit range: " + index);
        }
        if (subIndex < 0 || subIndex > MAX_SUBINDEX) {
            throw new IllegalArgumentException("CoE subIndex out of 8-bit range: " + subIndex);
        }
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("CoeInitCmd timeoutMs must be non-negative: " + timeoutMs);
        }
    }
}
