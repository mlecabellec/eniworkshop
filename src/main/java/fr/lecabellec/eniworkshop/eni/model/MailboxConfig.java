package fr.lecabellec.eniworkshop.eni.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Complete EtherCAT slave mailbox configuration (standard, bootstrap, AoE, CoE, protocols).
 *
 * @param dataLinkLayer      whether DataLinkLayer="1" attribute is set
 * @param standard           standard PreOp/SafeOp/Op mailbox region
 * @param bootstrap          optional Bootstrap (FoE firmware) mailbox region
 * @param aoeNetId           optional ADS over EtherCAT NetId string
 * @param protocols          supported mailbox application protocols
 * @param coeProfileChannels CoE device profile channel entries
 * @param coeInitCmds        CoE SDO initialization commands
 *
 * <p>Requirement: REQ-00002, REQ-00003, Constraint: CS-0030.2.
 */
public record MailboxConfig(
        boolean dataLinkLayer,
        MailboxRegion standard,
        Optional<MailboxRegion> bootstrap,
        String aoeNetId,
        List<MailboxProtocol> protocols,
        List<CoeChannelInfo> coeProfileChannels,
        List<CoeInitCmd> coeInitCmds) {

    /**
     * Compact constructor enforcing non-null invariants and defensive list copying.
     */
    public MailboxConfig {
        Objects.requireNonNull(standard, "Mailbox standard region must not be null");
        Objects.requireNonNull(bootstrap, "Mailbox bootstrap Optional must not be null");
        Objects.requireNonNull(aoeNetId, "Mailbox aoeNetId must not be null");
        protocols = List.copyOf(Objects.requireNonNull(protocols, "Mailbox protocols must not be null"));
        coeProfileChannels = List.copyOf(
                Objects.requireNonNull(coeProfileChannels, "Mailbox coeProfileChannels must not be null"));
        coeInitCmds = List.copyOf(Objects.requireNonNull(coeInitCmds, "Mailbox coeInitCmds must not be null"));
    }

    /**
     * Returns an empty MailboxConfig for slaves without mailbox support.
     *
     * @return empty {@link MailboxConfig}
     */
    public static MailboxConfig empty() {
        return new MailboxConfig(
                false,
                MailboxRegion.empty(),
                Optional.empty(),
                "",
                List.of(),
                List.of(),
                List.of());
    }
}
