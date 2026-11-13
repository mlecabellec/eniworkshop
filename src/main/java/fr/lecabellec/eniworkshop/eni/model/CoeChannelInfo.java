package fr.lecabellec.eniworkshop.eni.model;

/**
 * CoE ChannelInfo entry from Slave/Mailbox/CoE/Profile/ChannelInfo (ETG.2100).
 *
 * @param profileNo device profile number (e.g., 402 for CiA 402 drive, 5001 for MDP)
 * @param addInfo   additional profile info code
 *
 * <p>Requirement: REQ-00002, Constraint: CS-0030.1.
 */
public record CoeChannelInfo(
        int profileNo,
        int addInfo) {
}
