package fr.lecabellec.eniworkshop.eni.model;

/**
 * Symbolic EtherCAT (ETG.1000) and ENI (ETG.2100) constants and execution bounds.
 *
 * <p>Requirement: REQ-00002, REQ-00003, Constraint: CS-0030.4, CS-0060.7.
 */
public final class EthercatConstants {

    /** EtherCAT Ethernet frame EtherType (0x88A4). */
    public static final int ETHERCAT_ETHERTYPE = 0x88A4;

    /** Standard Ethernet header size in bytes (Dst MAC 6 + Src MAC 6 + EtherType 2). */
    public static final int ETHERNET_HEADER_BYTES = 14;

    /** EtherCAT frame header size in bytes (11-bit length + reserved + 4-bit type). */
    public static final int ETHERCAT_HEADER_BYTES = 2;

    /** EtherCAT datagram header size in bytes (Cmd, Idx, Addr[4], Len/R/C/M[2], IRQ[2]). */
    public static final int DATAGRAM_HEADER_BYTES = 10;

    /** EtherCAT datagram working counter trailer size in bytes. */
    public static final int DATAGRAM_WKC_BYTES = 2;

    /** Maximum EtherCAT payload bytes per standard Ethernet frame (1500 - 2). */
    public static final int MAX_ETHERCAT_PAYLOAD_BYTES = 1498;

    /** Maximum data length for a single EtherCAT datagram inside a standard Ethernet frame. */
    public static final int MAX_SINGLE_DATAGRAM_DATA_BYTES = 1486;

    /** Minimum Ethernet frame size without FCS in bytes. */
    public static final int MIN_ETHERNET_FRAME_BYTES = 60;

    /** Ethernet FCS trailer size in bytes. */
    public static final int ETHERNET_FCS_BYTES = 4;

    /** Maximum number of slaves supported in a single ENI file (bounded per CS-0030.4). */
    public static final int MAX_ENI_SLAVES = 4096;

    /** Maximum 16-bit EtherCAT station/register address value. */
    public static final int MAX_UINT16 = 0xFFFF;

    /** Maximum ESC ports per device (Ports A, B, C, D). */
    public static final int MAX_ESC_PORTS = 4;

    /** ESC Configured Station Address register (0x0010). */
    public static final int REG_CONFIGURED_STATION_ADDRESS = 0x0010;

    /** ESC Configured Station Alias register (0x0012). */
    public static final int REG_CONFIGURED_STATION_ALIAS = 0x0012;

    /** ESC AL Control register (0x0120). */
    public static final int REG_AL_CONTROL = 0x0120;

    /** ESC AL Status register (0x0130). */
    public static final int REG_AL_STATUS = 0x0130;

    /** ESC RX Error Counter base register (0x0300). */
    public static final int REG_RX_ERROR_COUNTER = 0x0300;

    /** ESC FMMU base register (0x0600). */
    public static final int REG_FMMU_BASE = 0x0600;

    /** ESC SyncManager base register (0x0800). */
    public static final int REG_SYNC_MANAGER_BASE = 0x0800;

    /** ESC Distributed Clocks System Time register (0x0910). */
    public static final int REG_DC_SYSTEM_TIME = 0x0910;

    /** Minimum ESC DPRAM address for user/mailbox/process data (0x1000). */
    public static final int MIN_ESC_DPRAM_ADDR = 0x1000;

    /** Default starting physical station address allocated during bus design. */
    public static final int DEFAULT_START_PHYS_ADDR = 1001;

    /** Default cycle time in microseconds (1 ms). */
    public static final int DEFAULT_CYCLE_TIME_US = 1000;

    /** Default InitCmd retry count. */
    public static final int DEFAULT_INIT_CMD_RETRIES = 3;

    /** Default InitCmd timeout in milliseconds. */
    public static final int DEFAULT_INIT_CMD_TIMEOUT_MS = 5000;

    /** Maximum XML input size accepted (100 MB). */
    public static final long MAX_XML_BYTES = 100L * 1024L * 1024L;

    /** Number of bits per byte. */
    public static final int BITS_PER_BYTE = 8;

    /** Default SyncManager count per standard ESC. */
    public static final int STANDARD_SM_COUNT = 4;

    /** Private constructor preventing instantiation of utility constant class. */
    private EthercatConstants() {
        // Intentionally empty utility class constructor.
    }
}
