package fr.lecabellec.eniworkshop.eni.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Complete ETG.2100 configuration for a single EtherCAT slave (SubDevice).
 *
 * @param identity     identity, addressing, and port physics
 * @param previousPort optional upstream port wiring
 * @param hotConnect   optional Hot Connect group head descriptor
 * @param processData  SyncManagers, Send/Recv regions, RxPDOs, and TxPDOs
 * @param initCmds     register-level initialization commands
 * @param mailbox      mailbox protocols, DPRAM regions, and CoE InitCmds
 * @param dc           Distributed Clocks settings
 *
 * <p>Requirement: REQ-00002, REQ-00003, Constraint: CS-0030.2.
 */
public record EniSlave(
        SlaveIdentity identity,
        Optional<PreviousPort> previousPort,
        Optional<HotConnectGroup> hotConnect,
        ProcessDataLayout processData,
        List<InitCmd> initCmds,
        MailboxConfig mailbox,
        DistributedClocksConfig dc) {

    /**
     * Compact constructor enforcing non-null invariants and defensive copying.
     */
    public EniSlave {
        Objects.requireNonNull(identity, "Slave identity must not be null");
        Objects.requireNonNull(previousPort, "Slave previousPort Optional must not be null");
        Objects.requireNonNull(hotConnect, "Slave hotConnect Optional must not be null");
        Objects.requireNonNull(processData, "Slave processData must not be null");
        initCmds = List.copyOf(Objects.requireNonNull(initCmds, "Slave initCmds must not be null"));
        Objects.requireNonNull(mailbox, "Slave mailbox must not be null");
        Objects.requireNonNull(dc, "Slave dc must not be null");
    }
}
