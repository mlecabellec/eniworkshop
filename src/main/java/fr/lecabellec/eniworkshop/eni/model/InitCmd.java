package fr.lecabellec.eniworkshop.eni.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Register-level EtherCAT InitCmd executed during master or slave ESM transitions.
 *
 * @param transitions list of ESM transitions triggering this command
 * @param beforeSlave true when executed before slave-specific commands
 * @param comment     human-readable description
 * @param cmd         EtherCAT datagram command code
 * @param address     datagram target address
 * @param payload     datagram payload, length, and expected working counter
 * @param timing      scheduling requirement, retry count, and timeout
 * @param validate    optional validation clause
 *
 * <p>Requirement: REQ-00002, REQ-00003, Constraint: CS-0030.2.
 */
public record InitCmd(
        List<EsmTransition> transitions,
        boolean beforeSlave,
        String comment,
        DatagramCmd cmd,
        DatagramAddress address,
        InitCmdPayload payload,
        InitCmdTiming timing,
        Optional<ValidateClause> validate) {

    /**
     * Compact constructor enforcing non-null invariants and defensive list copying.
     */
    public InitCmd {
        transitions = List.copyOf(Objects.requireNonNull(transitions, "InitCmd transitions must not be null"));
        Objects.requireNonNull(comment, "InitCmd comment must not be null");
        Objects.requireNonNull(cmd, "InitCmd cmd must not be null");
        Objects.requireNonNull(address, "InitCmd address must not be null");
        Objects.requireNonNull(payload, "InitCmd payload must not be null");
        Objects.requireNonNull(timing, "InitCmd timing must not be null");
        Objects.requireNonNull(validate, "InitCmd validate Optional must not be null");
    }
}
