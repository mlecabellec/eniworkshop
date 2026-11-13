package fr.lecabellec.eniworkshop.eni.model;

/**
 * Standard ESC DPRAM Send/Recv mailbox memory region (ETG.2100).
 *
 * @param sendStart     master-to-slave mailbox DPRAM start address (SM0)
 * @param sendLength    master-to-slave mailbox byte length
 * @param recvStart     slave-to-master mailbox DPRAM start address (SM1)
 * @param recvLength    slave-to-master mailbox byte length
 * @param statusBitAddr optional status bit address for mailbox polling
 *
 * <p>Requirement: REQ-00002, REQ-00003, Constraint: CS-0030.1.
 */
public record MailboxRegion(
        int sendStart,
        int sendLength,
        int recvStart,
        int recvLength,
        int statusBitAddr) {

    /**
     * Compact constructor validating non-negative ESC addresses and lengths.
     */
    public MailboxRegion {
        if (sendStart < 0 || sendLength < 0 || recvStart < 0 || recvLength < 0 || statusBitAddr < 0) {
            throw new IllegalArgumentException("MailboxRegion addresses and lengths must be non-negative");
        }
    }

    /**
     * Returns an empty mailbox region.
     *
     * @return zeroed {@link MailboxRegion}
     */
    public static MailboxRegion empty() {
        return new MailboxRegion(0, 0, 0, 0, 0);
    }
}
