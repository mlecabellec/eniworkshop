package fr.lecabellec.eniworkshop.eni.model;

import java.util.List;
import java.util.Objects;

/**
 * EtherCAT Master (MainDevice) configuration block in an ENI document (ETG.2100).
 *
 * @param name           master name (e.g., "Class-B Master")
 * @param destinationMac Ethernet destination MAC (typically "ffffffffffff")
 * @param sourceMac      Ethernet source MAC
 * @param etherType      EtherCAT frame EtherType (typically 0x88A4)
 * @param mailboxStates  logical mailbox polling region
 * @param eoe            Ethernet over EtherCAT resource limits
 * @param redundancy     cable redundancy ring configuration
 * @param initCmds       master-level initialization commands
 *
 * <p>Requirement: REQ-00002, Constraint: CS-0030.2.
 */
public record EniMaster(
        String name,
        String destinationMac,
        String sourceMac,
        int etherType,
        MailboxStatesConfig mailboxStates,
        MasterEoeConfig eoe,
        MasterRedundancyConfig redundancy,
        List<InitCmd> initCmds) {

    /**
     * Compact constructor enforcing non-null invariants and defensive list copy.
     */
    public EniMaster {
        Objects.requireNonNull(name, "Master name must not be null");
        Objects.requireNonNull(destinationMac, "Master destinationMac must not be null");
        Objects.requireNonNull(sourceMac, "Master sourceMac must not be null");
        Objects.requireNonNull(mailboxStates, "Master mailboxStates must not be null");
        Objects.requireNonNull(eoe, "Master eoe must not be null");
        Objects.requireNonNull(redundancy, "Master redundancy must not be null");
        initCmds = List.copyOf(Objects.requireNonNull(initCmds, "Master initCmds must not be null"));
        if (etherType < 0 || etherType > EthercatConstants.MAX_UINT16) {
            throw new IllegalArgumentException("Master etherType out of 16-bit range: " + etherType);
        }
    }
}
