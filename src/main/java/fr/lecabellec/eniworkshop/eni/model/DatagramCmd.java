package fr.lecabellec.eniworkshop.eni.model;

/**
 * EtherCAT datagram command codes defined by ETG.1000.4 and ETG.2100.
 *
 * <p>Requirement: REQ-00002, REQ-00003, Constraint: CS-0060.3.
 */
public enum DatagramCmd {
    /** No operation (0). */
    NOP(0, false),
    /** Auto-increment physical read (1). */
    APRD(1, false),
    /** Auto-increment physical write (2). */
    APWR(2, false),
    /** Auto-increment physical read-write (3). */
    APRW(3, false),
    /** Configured-address physical read (4). */
    FPRD(4, false),
    /** Configured-address physical write (5). */
    FPWR(5, false),
    /** Configured-address physical read-write (6). */
    FPRW(6, false),
    /** Broadcast read (7). */
    BRD(7, false),
    /** Broadcast write (8). */
    BWR(8, false),
    /** Broadcast read-write (9). */
    BRW(9, false),
    /** Logical memory read (10). */
    LRD(10, true),
    /** Logical memory write (11). */
    LWR(11, true),
    /** Logical memory read-write (12). */
    LRW(12, true),
    /** Auto-increment physical read multiple write (13). */
    ARMW(13, false),
    /** Configured-address physical read multiple write (14). */
    FRMW(14, false);

    /** Numeric EtherCAT command code. */
    private final int code;

    /** True if the command addresses the 32-bit logical process memory space. */
    private final boolean logicalAddressing;

    /**
     * Constructs a DatagramCmd enum constant.
     *
     * @param code              numeric ETG.1000 datagram command code
     * @param logicalAddressing true if logical 32-bit addressing is used
     */
    DatagramCmd(final int code, final boolean logicalAddressing) {
        this.code = code;
        this.logicalAddressing = logicalAddressing;
    }

    /**
     * Returns the numeric command code.
     *
     * @return command code (0..14)
     */
    public int getCode() {
        return code;
    }

    /**
     * Indicates whether this command uses 32-bit logical addressing (LRD/LWR/LRW).
     *
     * @return true if logical addressing
     */
    public boolean isLogicalAddressing() {
        return logicalAddressing;
    }

    /**
     * Resolves a numeric command code into its {@link DatagramCmd}.
     *
     * @param code integer command code from ENI XML
     * @return matching {@link DatagramCmd}
     * @throws IllegalArgumentException if code is outside 0..14
     */
    public static DatagramCmd fromCode(final int code) {
        for (final DatagramCmd value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unsupported EtherCAT datagram command code: " + code);
    }
}
