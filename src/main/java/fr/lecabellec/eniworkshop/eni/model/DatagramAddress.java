package fr.lecabellec.eniworkshop.eni.model;

import java.util.Objects;

/**
 * Composite datagram address holding raw 32-bit logical Addr or 16-bit Adp/Ado pair.
 *
 * @param addr 32-bit logical address (for LRD/LWR/LRW) or 0
 * @param adp  16-bit auto-increment or configured physical station address
 * @param ado  16-bit ESC physical memory/register offset
 *
 * <p>Requirement: REQ-00002, Constraint: CS-0030.1.
 */
public record DatagramAddress(
        long addr,
        int adp,
        int ado) {

    /**
     * Compact constructor enforcing unsigned 16-bit bounds on adp and ado.
     */
    public DatagramAddress {
        if (adp < 0 || adp > EthercatConstants.MAX_UINT16) {
            throw new IllegalArgumentException("Datagram adp out of 16-bit range: " + adp);
        }
        if (ado < 0 || ado > EthercatConstants.MAX_UINT16) {
            throw new IllegalArgumentException("Datagram ado out of 16-bit range: " + ado);
        }
        if (addr < 0L) {
            throw new IllegalArgumentException("Datagram logical addr must be non-negative: " + addr);
        }
    }

    /**
     * Creates a physical-register address pair (adp, ado).
     *
     * @param adp 16-bit station/auto-inc address
     * @param ado 16-bit ESC offset
     * @return validated {@link DatagramAddress}
     */
    public static DatagramAddress physical(final int adp, final int ado) {
        return new DatagramAddress(0L, Objects.requireNonNull(Integer.valueOf(adp)), ado);
    }
}
