package fr.lecabellec.eniworkshop.compare;

import java.util.List;
import java.util.Objects;

/**
 * Immutable DTOs representing multi-view EtherCAT comparison results across two ENI documents,
 * two bus topologies, two individual slaves, or two Hot Connect groups.
 *
 * <p>Requirement: REQ-00011, Feature: FR-00011, Constraint: CS-0030.2.
 */
public final class ComparisonModels {

    /** Private constructor preventing instantiation of holder class. */
    private ComparisonModels() {
        // Intentionally empty.
    }

    /**
     * Supported comparison granularities.
     */
    public enum CompareScope {
        /** Compare two full ETG.2100 ENI documents in the catalog. */
        ENI,
        /** Compare two EtherCAT bus topologies (slaves, ports, Hot Connect, redundancy). */
        TOPOLOGY,
        /** Compare two individual EtherCAT slaves (identity, PDOs, SMs, mailbox, DC, InitCmds). */
        SLAVE,
        /** Compare two Hot Connect groups including identification commands and member slices. */
        HOT_CONNECT
    }

    /**
     * Full outer-join comparison status between Left and Right items.
     */
    public enum DiffStatus {
        /** Key present on both sides with identical effective attributes. */
        MATCH,
        /** Key present on both sides with one or more field differences. */
        CHANGED,
        /** Key present only on the Left side. */
        LEFT_ONLY,
        /** Key present only on the Right side. */
        RIGHT_ONLY
    }

    /**
     * Single field-level difference inside a changed outer-join row.
     *
     * @param field      property or attribute name
     * @param leftValue  value on the left side (or empty string if absent)
     * @param rightValue value on the right side (or empty string if absent)
     */
    public record FieldDelta(String field, String leftValue, String rightValue) {

        /**
         * Compact constructor enforcing non-null strings.
         */
        public FieldDelta {
            Objects.requireNonNull(field, "field must not be null");
            Objects.requireNonNull(leftValue, "leftValue must not be null");
            Objects.requireNonNull(rightValue, "rightValue must not be null");
        }
    }

    /**
     * Single row in the dynamic "Outer Join" comparison table aligning Left and Right keys.
     *
     * @param rowKey       canonical outer-join alignment key
     * @param section      logical category (MASTER, SLAVE, PORT_LINK, PDO, SYNC_MANAGER, DC, etc.)
     * @param name         human-readable label
     * @param status       outer-join match status
     * @param leftSummary  concise left summary
     * @param rightSummary concise right summary
     * @param deltas       granular field deltas when status is {@link DiffStatus#CHANGED}
     */
    public record OuterJoinRow(
            String rowKey,
            String section,
            String name,
            DiffStatus status,
            String leftSummary,
            String rightSummary,
            List<FieldDelta> deltas) {

        /**
         * Compact constructor enforcing non-null values and defensive list copying.
         */
        public OuterJoinRow {
            Objects.requireNonNull(rowKey, "rowKey must not be null");
            Objects.requireNonNull(section, "section must not be null");
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(leftSummary, "leftSummary must not be null");
            Objects.requireNonNull(rightSummary, "rightSummary must not be null");
            deltas = List.copyOf(Objects.requireNonNull(deltas, "deltas must not be null"));
        }
    }

    /**
     * Linear bus strip node representing one positioned EtherCAT device in a side-by-side bus diagram.
     *
     * @param index                  0-based order along the bus
     * @param physAddr               configured physical address
     * @param name                   slave display name
     * @param category               MASTER, COUPLER, EXTENSION_CARD, JUNCTION, or SLAVE
     * @param physics                ESC port physics string
     * @param prevPort               upstream port description
     * @param hotConnectGroup        Hot Connect group name (empty if fixed)
     * @param dcEnabled              true if Distributed Clocks enabled
     * @param rxBits                 total RxPDO bits
     * @param txBits                 total TxPDO bits
     * @param status                 diff status relative to counterpart bus
     * @param matchedCounterpartKey  corresponding key on the opposite bus (empty if unmatched)
     */
    public record LinearBusItem(
            int index,
            int physAddr,
            String name,
            String category,
            String physics,
            String prevPort,
            String hotConnectGroup,
            boolean dcEnabled,
            int rxBits,
            int txBits,
            DiffStatus status,
            String matchedCounterpartKey) {

        /**
         * Compact constructor enforcing non-null strings.
         */
        public LinearBusItem {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(category, "category must not be null");
            Objects.requireNonNull(physics, "physics must not be null");
            Objects.requireNonNull(prevPort, "prevPort must not be null");
            Objects.requireNonNull(hotConnectGroup, "hotConnectGroup must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(matchedCounterpartKey, "matchedCounterpartKey must not be null");
        }
    }

    /**
     * Hierarchical node used to render synchronized side-by-side comparison trees.
     *
     * @param nodeId   stable tree node identifier
     * @param label    display text
     * @param section  logical category
     * @param status   diff status relative to opposite tree
     * @param detail   secondary detail string
     * @param children immutable child nodes
     */
    public record ComparisonTreeNode(
            String nodeId,
            String label,
            String section,
            DiffStatus status,
            String detail,
            List<ComparisonTreeNode> children) {

        /**
         * Compact constructor enforcing non-null fields and immutable children.
         */
        public ComparisonTreeNode {
            Objects.requireNonNull(nodeId, "nodeId must not be null");
            Objects.requireNonNull(label, "label must not be null");
            Objects.requireNonNull(section, "section must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(detail, "detail must not be null");
            children = List.copyOf(Objects.requireNonNull(children, "children must not be null"));
        }
    }

    /**
     * Aggregate outer-join statistics for a comparison run.
     *
     * @param totalRows      total aligned outer-join rows
     * @param matchedCount   rows with {@link DiffStatus#MATCH}
     * @param changedCount   rows with {@link DiffStatus#CHANGED}
     * @param leftOnlyCount  rows with {@link DiffStatus#LEFT_ONLY}
     * @param rightOnlyCount rows with {@link DiffStatus#RIGHT_ONLY}
     */
    public record ComparisonSummary(
            int totalRows,
            int matchedCount,
            int changedCount,
            int leftOnlyCount,
            int rightOnlyCount) {
    }

    /**
     * Complete multi-representation comparison payload for trees, linear bus strips, and outer-join tables.
     *
     * @param scope          comparison granularity
     * @param leftTitle      left label
     * @param rightTitle     right label
     * @param summary        aggregated diff counters
     * @param leftTree       left hierarchical tree
     * @param rightTree      right hierarchical tree
     * @param leftLinearBus  ordered linear items on left side
     * @param rightLinearBus ordered linear items on right side
     * @param outerJoinRows  sorted full outer-join diff table rows
     */
    public record ComparisonResult(
            CompareScope scope,
            String leftTitle,
            String rightTitle,
            ComparisonSummary summary,
            ComparisonTreeNode leftTree,
            ComparisonTreeNode rightTree,
            List<LinearBusItem> leftLinearBus,
            List<LinearBusItem> rightLinearBus,
            List<OuterJoinRow> outerJoinRows) {

        /**
         * Compact constructor enforcing non-null invariants and defensive copies.
         */
        public ComparisonResult {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(leftTitle, "leftTitle must not be null");
            Objects.requireNonNull(rightTitle, "rightTitle must not be null");
            Objects.requireNonNull(summary, "summary must not be null");
            Objects.requireNonNull(leftTree, "leftTree must not be null");
            Objects.requireNonNull(rightTree, "rightTree must not be null");
            leftLinearBus = List.copyOf(Objects.requireNonNull(leftLinearBus, "leftLinearBus must not be null"));
            rightLinearBus = List.copyOf(Objects.requireNonNull(rightLinearBus, "rightLinearBus must not be null"));
            outerJoinRows = List.copyOf(Objects.requireNonNull(outerJoinRows, "outerJoinRows must not be null"));
        }
    }
}
