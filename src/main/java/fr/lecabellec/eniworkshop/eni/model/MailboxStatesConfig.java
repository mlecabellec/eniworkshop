package fr.lecabellec.eniworkshop.eni.model;

/**
 * Master MailboxStates logical polling window (ETG.2100 Master/MailboxStates).
 *
 * @param startAddr logical start address for mailbox state bits
 * @param count     number of monitored slave mailbox state bits
 *
 * <p>Requirement: REQ-00002, Constraint: CS-0030.1.
 */
public record MailboxStatesConfig(
        long startAddr,
        int count) {

    /**
     * Compact constructor validating non-negative address and count.
     */
    public MailboxStatesConfig {
        if (startAddr < 0L || count < 0) {
            throw new IllegalArgumentException("MailboxStates startAddr and count must be non-negative");
        }
    }
}
