package fr.lecabellec.eniworkshop.eni.model;

import java.util.List;
import java.util.Objects;

/**
 * Hot Connect group definition on a head slave (ETG.2100 HotConnect).
 *
 * @param groupName        logical Hot Connect group name
 * @param groupMemberCount total number of slaves belonging to this dynamic group
 * @param identifyCmds     identification commands (e.g., reading station alias 0x0012)
 *
 * <p>Requirement: REQ-00002, REQ-00003, Constraint: CS-0030.1.
 */
public record HotConnectGroup(
        String groupName,
        int groupMemberCount,
        List<InitCmd> identifyCmds) {

    /**
     * Compact constructor validating non-empty group count and defensive list copy.
     */
    public HotConnectGroup {
        Objects.requireNonNull(groupName, "HotConnect groupName must not be null");
        identifyCmds = List.copyOf(Objects.requireNonNull(identifyCmds, "HotConnect identifyCmds must not be null"));
        if (groupMemberCount < 1) {
            throw new IllegalArgumentException("HotConnect groupMemberCount must be >= 1: " + groupMemberCount);
        }
    }
}
