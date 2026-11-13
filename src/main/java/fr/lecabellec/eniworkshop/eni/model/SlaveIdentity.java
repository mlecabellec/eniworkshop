package fr.lecabellec.eniworkshop.eni.model;

import java.util.Objects;

/**
 * Identity, addressing, and physical port descriptors of an EtherCAT slave (ETG.2100 Slave/Info).
 *
 * @param name        slave instance name
 * @param physAddr    configured station address (0x0010, e.g., 5000)
 * @param autoIncAddr auto-increment positional address (0, -1, -2, ...)
 * @param physics     port physics string (e.g., "YKY", "KK", "YY")
 * @param vendorId    ETG vendor identifier (32-bit unsigned)
 * @param productCode vendor product code (32-bit unsigned)
 * @param revisionNo  hardware/firmware revision number (32-bit unsigned)
 * @param serialNo    optional serial number (32-bit unsigned)
 *
 * <p>Requirement: REQ-00002, REQ-00004, Constraint: CS-0030.1.
 */
public record SlaveIdentity(
        String name,
        int physAddr,
        int autoIncAddr,
        String physics,
        long vendorId,
        long productCode,
        long revisionNo,
        long serialNo) {

    /**
     * Compact constructor validating non-null strings and unsigned 16-bit station address.
     */
    public SlaveIdentity {
        Objects.requireNonNull(name, "Slave name must not be null");
        Objects.requireNonNull(physics, "Slave physics must not be null");
        if (physAddr < 0 || physAddr > EthercatConstants.MAX_UINT16) {
            throw new IllegalArgumentException("Slave physAddr out of 16-bit range: " + physAddr);
        }
    }

    /**
     * Returns canonical catalog key {@code vendorId:productCode:revisionNo}.
     *
     * @return catalog key string
     */
    public String catalogKey() {
        return vendorId + ":" + productCode + ":" + revisionNo;
    }
}
