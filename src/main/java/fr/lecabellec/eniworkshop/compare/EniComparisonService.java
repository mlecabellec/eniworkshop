package fr.lecabellec.eniworkshop.compare;

import fr.lecabellec.eniworkshop.EniWorkshopException;
import fr.lecabellec.eniworkshop.catalog.SlaveCatalogService;
import fr.lecabellec.eniworkshop.compare.ComparisonModels.CompareScope;
import fr.lecabellec.eniworkshop.compare.ComparisonModels.ComparisonResult;
import fr.lecabellec.eniworkshop.compare.ComparisonModels.ComparisonSummary;
import fr.lecabellec.eniworkshop.compare.ComparisonModels.ComparisonTreeNode;
import fr.lecabellec.eniworkshop.compare.ComparisonModels.DiffStatus;
import fr.lecabellec.eniworkshop.compare.ComparisonModels.FieldDelta;
import fr.lecabellec.eniworkshop.compare.ComparisonModels.LinearBusItem;
import fr.lecabellec.eniworkshop.compare.ComparisonModels.OuterJoinRow;
import fr.lecabellec.eniworkshop.eni.model.CoeInitCmd;
import fr.lecabellec.eniworkshop.eni.model.CyclicFrame;
import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.model.EniMaster;
import fr.lecabellec.eniworkshop.eni.model.EniSlave;
import fr.lecabellec.eniworkshop.eni.model.HotConnectGroup;
import fr.lecabellec.eniworkshop.eni.model.InitCmd;
import fr.lecabellec.eniworkshop.eni.model.PdoEntry;
import fr.lecabellec.eniworkshop.eni.model.PdoMapping;
import fr.lecabellec.eniworkshop.eni.model.ProcessImageVariable;
import fr.lecabellec.eniworkshop.eni.model.SyncManagerConfig;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Computes structural "outer join" diffs, synchronized trees, and linear EtherCAT bus
 * strip alignments between two ENI documents, topologies, slaves, or Hot Connect groups.
 *
 * <p>Requirement: REQ-00011, Feature: FR-00011, Constraint: CS-0030.2.
 */
@Service
public final class EniComparisonService {

    /** Hex formatting width for 16-bit EtherCAT indices. */
    private static final String HEX4_FORMAT = "0x%04X";

    /** Section constant for master settings. */
    private static final String SEC_MASTER = "MASTER";

    /** Section constant for slave items. */
    private static final String SEC_SLAVE = "SLAVE";

    /** Section constant for Hot Connect groups. */
    private static final String SEC_HOT_CONNECT = "HOT_CONNECT";

    /** Section constant for cyclic frames. */
    private static final String SEC_CYCLIC = "CYCLIC_FRAME";

    /** Section constant for process variables. */
    private static final String SEC_PROCESS_VAR = "PROCESS_VAR";

    /** Root label placeholder when item missing on one side. */
    private static final String ABSENT_LABEL = "<absent>";

    /**
     * Compares two full ENI documents or two bus topologies extracted from ENI documents.
     *
     * @param leftTitle       left display title
     * @param left            left ENI document
     * @param rightTitle      right display title
     * @param right           right ENI document
     * @param topologyFocused when true focuses trees/outer-join on topology (master, slaves, links, HC)
     * @return unified {@link ComparisonResult}
     */
    public ComparisonResult compareEnis(
            final String leftTitle,
            final EniDocument left,
            final String rightTitle,
            final EniDocument right,
            final boolean topologyFocused) {
        Objects.requireNonNull(leftTitle, "leftTitle must not be null");
        Objects.requireNonNull(left, "left EniDocument must not be null");
        Objects.requireNonNull(rightTitle, "rightTitle must not be null");
        Objects.requireNonNull(right, "right EniDocument must not be null");

        final CompareScope scope = topologyFocused ? CompareScope.TOPOLOGY : CompareScope.ENI;
        final List<OuterJoinRow> rows = new ArrayList<>();

        compareMaster(left.master(), right.master(), left.cyclic().cycleTimeUs(), right.cyclic().cycleTimeUs(), rows);
        final Map<String, DiffStatus> slaveStatusByKey = compareSlaveLists(left.slaves(), right.slaves(), rows);
        compareHotConnectHeaders(left.slaves(), right.slaves(), rows);

        if (!topologyFocused) {
            compareCyclicFrames(left.cyclic().frames(), right.cyclic().frames(), rows);
            compareProcessVariables(left.processImage().inputs(), right.processImage().inputs(), "IN", rows);
            compareProcessVariables(left.processImage().outputs(), right.processImage().outputs(), "OUT", rows);
        }

        final List<LinearBusItem> leftBus = buildLinearBus(left.slaves(), slaveStatusByKey);
        final List<LinearBusItem> rightBus = buildLinearBus(right.slaves(), slaveStatusByKey);
        final ComparisonTreeNode leftTree = buildEniTree("left", leftTitle, left, slaveStatusByKey, topologyFocused);
        final ComparisonTreeNode rightTree = buildEniTree("right", rightTitle, right, slaveStatusByKey, topologyFocused);

        return new ComparisonResult(
                scope,
                leftTitle,
                rightTitle,
                summarize(rows),
                leftTree,
                rightTree,
                leftBus,
                rightBus,
                rows);
    }

    /**
     * Performs a deep structural outer-join comparison between two individual slaves.
     *
     * @param leftLabel  left title
     * @param left       left slave
     * @param rightLabel right title
     * @param right      right slave
     * @return unified {@link ComparisonResult}
     */
    public ComparisonResult compareSlaves(
            final String leftLabel,
            final EniSlave left,
            final String rightLabel,
            final EniSlave right) {
        Objects.requireNonNull(leftLabel, "leftLabel must not be null");
        Objects.requireNonNull(left, "left EniSlave must not be null");
        Objects.requireNonNull(rightLabel, "rightLabel must not be null");
        Objects.requireNonNull(right, "right EniSlave must not be null");

        final List<OuterJoinRow> rows = new ArrayList<>();
        compareSingleSlaveIdentity(left, right, rows);
        compareSyncManagers(left.processData().syncManagers(), right.processData().syncManagers(), rows);
        comparePdoLists("RX_PDO", left.processData().rxPdos(), right.processData().rxPdos(), rows);
        comparePdoLists("TX_PDO", left.processData().txPdos(), right.processData().txPdos(), rows);
        compareCoeInitCmds(left.mailbox().coeInitCmds(), right.mailbox().coeInitCmds(), rows);
        compareRegisterInitCmds("SLAVE_INIT_CMD", left.initCmds(), right.initCmds(), rows);

        final DiffStatus overall = rows.stream().allMatch(r -> r.status() == DiffStatus.MATCH)
                ? DiffStatus.MATCH
                : DiffStatus.CHANGED;
        final List<LinearBusItem> leftStrip = List.of(toLinearBusItem(0, left, overall, right.identity().name()));
        final List<LinearBusItem> rightStrip = List.of(toLinearBusItem(0, right, overall, left.identity().name()));

        final ComparisonTreeNode leftTree = buildSlaveDetailTree("left-slave", leftLabel, left, rows);
        final ComparisonTreeNode rightTree = buildSlaveDetailTree("right-slave", rightLabel, right, rows);

        return new ComparisonResult(
                CompareScope.SLAVE,
                leftLabel,
                rightLabel,
                summarize(rows),
                leftTree,
                rightTree,
                leftStrip,
                rightStrip,
                rows);
    }

    /**
     * Compares two Hot Connect groups including head identification commands and group member slaves.
     *
     * @param leftTitle      left ENI title
     * @param leftDoc        left ENI document
     * @param leftGroupName  Hot Connect group name in left document
     * @param rightTitle     right ENI title
     * @param rightDoc       right ENI document
     * @param rightGroupName Hot Connect group name in right document
     * @return unified {@link ComparisonResult}
     */
    public ComparisonResult compareHotConnectGroups(
            final String leftTitle,
            final EniDocument leftDoc,
            final String leftGroupName,
            final String rightTitle,
            final EniDocument rightDoc,
            final String rightGroupName) {
        Objects.requireNonNull(leftDoc, "leftDoc must not be null");
        Objects.requireNonNull(rightDoc, "rightDoc must not be null");
        Objects.requireNonNull(leftGroupName, "leftGroupName must not be null");
        Objects.requireNonNull(rightGroupName, "rightGroupName must not be null");

        final List<EniSlave> leftMembers = extractHotConnectMembers(leftDoc, leftGroupName);
        final List<EniSlave> rightMembers = extractHotConnectMembers(rightDoc, rightGroupName);
        if (leftMembers.isEmpty()) {
            throw new EniWorkshopException("HotConnect group not found in left ENI: " + leftGroupName);
        }
        if (rightMembers.isEmpty()) {
            throw new EniWorkshopException("HotConnect group not found in right ENI: " + rightGroupName);
        }

        final List<OuterJoinRow> rows = new ArrayList<>();
        final HotConnectGroup leftHead = leftMembers.get(0).hotConnect().orElseThrow();
        final HotConnectGroup rightHead = rightMembers.get(0).hotConnect().orElseThrow();
        final List<FieldDelta> hcDeltas = new ArrayList<>();
        compareField("groupName", leftHead.groupName(), rightHead.groupName(), hcDeltas);
        compareField("groupMemberCount",
                String.valueOf(leftHead.groupMemberCount()),
                String.valueOf(rightHead.groupMemberCount()),
                hcDeltas);
        compareField("identifyCmdCount",
                String.valueOf(leftHead.identifyCmds().size()),
                String.valueOf(rightHead.identifyCmds().size()),
                hcDeltas);
        rows.add(new OuterJoinRow(
                "HC:GROUP",
                SEC_HOT_CONNECT,
                leftHead.groupName() + " vs " + rightHead.groupName(),
                hcDeltas.isEmpty() ? DiffStatus.MATCH : DiffStatus.CHANGED,
                "members=" + leftMembers.size() + " identify=" + leftHead.identifyCmds().size(),
                "members=" + rightMembers.size() + " identify=" + rightHead.identifyCmds().size(),
                hcDeltas));

        compareRegisterInitCmds("HC_IDENTIFY", leftHead.identifyCmds(), rightHead.identifyCmds(), rows);
        final Map<String, DiffStatus> memberStatus = compareSlaveListsByRelativeIndex(leftMembers, rightMembers, rows);

        final List<LinearBusItem> leftStrip = buildLinearBusByIndex(leftMembers, memberStatus);
        final List<LinearBusItem> rightStrip = buildLinearBusByIndex(rightMembers, memberStatus);
        final String fullLeftTitle = leftTitle + " :: HC[" + leftGroupName + "]";
        final String fullRightTitle = rightTitle + " :: HC[" + rightGroupName + "]";

        return new ComparisonResult(
                CompareScope.HOT_CONNECT,
                fullLeftTitle,
                fullRightTitle,
                summarize(rows),
                buildHotConnectTree("left-hc", fullLeftTitle, leftHead, leftMembers, memberStatus),
                buildHotConnectTree("right-hc", fullRightTitle, rightHead, rightMembers, memberStatus),
                leftStrip,
                rightStrip,
                rows);
    }

    private void compareMaster(
            final EniMaster left,
            final EniMaster right,
            final int leftCycleUs,
            final int rightCycleUs,
            final List<OuterJoinRow> rows) {
        final List<FieldDelta> deltas = new ArrayList<>();
        compareField("name", left.name(), right.name(), deltas);
        compareField("destinationMac", left.destinationMac(), right.destinationMac(), deltas);
        compareField("sourceMac", left.sourceMac(), right.sourceMac(), deltas);
        compareField("etherType", Integer.toHexString(left.etherType()), Integer.toHexString(right.etherType()), deltas);
        compareField("cycleTimeUs", String.valueOf(leftCycleUs), String.valueOf(rightCycleUs), deltas);
        compareField("redundancyEnabled",
                String.valueOf(left.redundancy().enabled()),
                String.valueOf(right.redundancy().enabled()),
                deltas);
        compareField("redundancyMac",
                left.redundancy().secondarySourceMac(),
                right.redundancy().secondarySourceMac(),
                deltas);
        rows.add(new OuterJoinRow(
                "MASTER:CONFIG",
                SEC_MASTER,
                "EtherCAT Master",
                deltas.isEmpty() ? DiffStatus.MATCH : DiffStatus.CHANGED,
                left.name() + " @" + leftCycleUs + "us red=" + left.redundancy().enabled(),
                right.name() + " @" + rightCycleUs + "us red=" + right.redundancy().enabled(),
                deltas));
    }

    private Map<String, DiffStatus> compareSlaveLists(
            final List<EniSlave> leftSlaves,
            final List<EniSlave> rightSlaves,
            final List<OuterJoinRow> rows) {
        final Map<String, EniSlave> leftByKey = new LinkedHashMap<>();
        final Map<String, EniSlave> rightByKey = new LinkedHashMap<>();
        for (final EniSlave s : leftSlaves) {
            leftByKey.put(slaveKey(s), s);
        }
        for (final EniSlave s : rightSlaves) {
            rightByKey.put(slaveKey(s), s);
        }

        final Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(leftByKey.keySet());
        allKeys.addAll(rightByKey.keySet());

        final Map<String, DiffStatus> statusByKey = new LinkedHashMap<>();
        for (final String key : allKeys) {
            final EniSlave l = leftByKey.get(key);
            final EniSlave r = rightByKey.get(key);
            if (l != null && r == null) {
                statusByKey.put(key, DiffStatus.LEFT_ONLY);
                rows.add(new OuterJoinRow(key, SEC_SLAVE, l.identity().name(), DiffStatus.LEFT_ONLY,
                        summarizeSlave(l), ABSENT_LABEL, List.of()));
            } else if (l == null && r != null) {
                statusByKey.put(key, DiffStatus.RIGHT_ONLY);
                rows.add(new OuterJoinRow(key, SEC_SLAVE, r.identity().name(), DiffStatus.RIGHT_ONLY,
                        ABSENT_LABEL, summarizeSlave(r), List.of()));
            } else if (l != null) {
                final List<FieldDelta> deltas = diffSlaves(l, r);
                final DiffStatus st = deltas.isEmpty() ? DiffStatus.MATCH : DiffStatus.CHANGED;
                statusByKey.put(key, st);
                rows.add(new OuterJoinRow(key, SEC_SLAVE, l.identity().name(), st,
                        summarizeSlave(l), summarizeSlave(r), deltas));
            }
        }
        return statusByKey;
    }

    private Map<String, DiffStatus> compareSlaveListsByRelativeIndex(
            final List<EniSlave> leftMembers,
            final List<EniSlave> rightMembers,
            final List<OuterJoinRow> rows) {
        final int max = Math.max(leftMembers.size(), rightMembers.size());
        final Map<String, DiffStatus> statusByIdx = new LinkedHashMap<>();
        for (int i = 0; i < max; i++) {
            final String key = "HC_MEMBER#" + i;
            final EniSlave l = i < leftMembers.size() ? leftMembers.get(i) : null;
            final EniSlave r = i < rightMembers.size() ? rightMembers.get(i) : null;
            if (l != null && r == null) {
                statusByIdx.put(key, DiffStatus.LEFT_ONLY);
                rows.add(new OuterJoinRow(key, SEC_SLAVE, l.identity().name(), DiffStatus.LEFT_ONLY,
                        summarizeSlave(l), ABSENT_LABEL, List.of()));
            } else if (l == null && r != null) {
                statusByIdx.put(key, DiffStatus.RIGHT_ONLY);
                rows.add(new OuterJoinRow(key, SEC_SLAVE, r.identity().name(), DiffStatus.RIGHT_ONLY,
                        ABSENT_LABEL, summarizeSlave(r), List.of()));
            } else if (l != null) {
                final List<FieldDelta> deltas = diffSlaves(l, r);
                final DiffStatus st = deltas.isEmpty() ? DiffStatus.MATCH : DiffStatus.CHANGED;
                statusByIdx.put(key, st);
                rows.add(new OuterJoinRow(key, SEC_SLAVE, l.identity().name() + " / " + r.identity().name(),
                        st, summarizeSlave(l), summarizeSlave(r), deltas));
            }
        }
        return statusByIdx;
    }

    private void compareHotConnectHeaders(
            final List<EniSlave> leftSlaves,
            final List<EniSlave> rightSlaves,
            final List<OuterJoinRow> rows) {
        final Map<String, HotConnectGroup> leftHc = new LinkedHashMap<>();
        final Map<String, HotConnectGroup> rightHc = new LinkedHashMap<>();
        for (final EniSlave s : leftSlaves) {
            s.hotConnect().ifPresent(hc -> leftHc.put(hc.groupName(), hc));
        }
        for (final EniSlave s : rightSlaves) {
            s.hotConnect().ifPresent(hc -> rightHc.put(hc.groupName(), hc));
        }
        final Set<String> allGroups = new LinkedHashSet<>();
        allGroups.addAll(leftHc.keySet());
        allGroups.addAll(rightHc.keySet());
        for (final String group : allGroups) {
            final HotConnectGroup l = leftHc.get(group);
            final HotConnectGroup r = rightHc.get(group);
            final String key = "HC:" + group;
            if (l != null && r == null) {
                rows.add(new OuterJoinRow(key, SEC_HOT_CONNECT, group, DiffStatus.LEFT_ONLY,
                        "members=" + l.groupMemberCount(), ABSENT_LABEL, List.of()));
            } else if (l == null && r != null) {
                rows.add(new OuterJoinRow(key, SEC_HOT_CONNECT, group, DiffStatus.RIGHT_ONLY,
                        ABSENT_LABEL, "members=" + r.groupMemberCount(), List.of()));
            } else if (l != null) {
                final List<FieldDelta> deltas = new ArrayList<>();
                compareField("groupMemberCount",
                        String.valueOf(l.groupMemberCount()),
                        String.valueOf(r.groupMemberCount()),
                        deltas);
                compareField("identifyCmds",
                        String.valueOf(l.identifyCmds().size()),
                        String.valueOf(r.identifyCmds().size()),
                        deltas);
                rows.add(new OuterJoinRow(key, SEC_HOT_CONNECT, group,
                        deltas.isEmpty() ? DiffStatus.MATCH : DiffStatus.CHANGED,
                        "members=" + l.groupMemberCount(), "members=" + r.groupMemberCount(), deltas));
            }
        }
    }

    private void compareCyclicFrames(
            final List<CyclicFrame> leftFrames,
            final List<CyclicFrame> rightFrames,
            final List<OuterJoinRow> rows) {
        final int max = Math.max(leftFrames.size(), rightFrames.size());
        for (int i = 0; i < max; i++) {
            final String key = "FRAME:" + i;
            final CyclicFrame l = i < leftFrames.size() ? leftFrames.get(i) : null;
            final CyclicFrame r = i < rightFrames.size() ? rightFrames.get(i) : null;
            if (l != null && r == null) {
                rows.add(new OuterJoinRow(key, SEC_CYCLIC, "Cyclic Frame #" + i, DiffStatus.LEFT_ONLY,
                        summarizeFrame(l), ABSENT_LABEL, List.of()));
            } else if (l == null && r != null) {
                rows.add(new OuterJoinRow(key, SEC_CYCLIC, "Cyclic Frame #" + i, DiffStatus.RIGHT_ONLY,
                        ABSENT_LABEL, summarizeFrame(r), List.of()));
            } else if (l != null) {
                final List<FieldDelta> deltas = new ArrayList<>();
                final String lSum = summarizeFrame(l);
                final String rSum = summarizeFrame(r);
                compareField("frameCommands", lSum, rSum, deltas);
                rows.add(new OuterJoinRow(key, SEC_CYCLIC, "Cyclic Frame #" + i,
                        deltas.isEmpty() ? DiffStatus.MATCH : DiffStatus.CHANGED, lSum, rSum, deltas));
            }
        }
    }

    private void compareProcessVariables(
            final List<ProcessImageVariable> leftVars,
            final List<ProcessImageVariable> rightVars,
            final String dir,
            final List<OuterJoinRow> rows) {
        final Map<String, ProcessImageVariable> lMap = new LinkedHashMap<>();
        final Map<String, ProcessImageVariable> rMap = new LinkedHashMap<>();
        for (final ProcessImageVariable v : leftVars) {
            lMap.put(dir + ":" + v.name(), v);
        }
        for (final ProcessImageVariable v : rightVars) {
            rMap.put(dir + ":" + v.name(), v);
        }
        final Set<String> keys = new LinkedHashSet<>();
        keys.addAll(lMap.keySet());
        keys.addAll(rMap.keySet());
        for (final String key : keys) {
            final ProcessImageVariable l = lMap.get(key);
            final ProcessImageVariable r = rMap.get(key);
            if (l != null && r == null) {
                rows.add(new OuterJoinRow(key, SEC_PROCESS_VAR, l.name(), DiffStatus.LEFT_ONLY,
                        formatVar(l), ABSENT_LABEL, List.of()));
            } else if (l == null && r != null) {
                rows.add(new OuterJoinRow(key, SEC_PROCESS_VAR, r.name(), DiffStatus.RIGHT_ONLY,
                        ABSENT_LABEL, formatVar(r), List.of()));
            } else if (l != null) {
                final List<FieldDelta> deltas = new ArrayList<>();
                compareField("dataType", l.dataType(), r.dataType(), deltas);
                compareField("bitSize", String.valueOf(l.bitSize()), String.valueOf(r.bitSize()), deltas);
                compareField("bitOffs", String.valueOf(l.bitOffs()), String.valueOf(r.bitOffs()), deltas);
                rows.add(new OuterJoinRow(key, SEC_PROCESS_VAR, l.name(),
                        deltas.isEmpty() ? DiffStatus.MATCH : DiffStatus.CHANGED,
                        formatVar(l), formatVar(r), deltas));
            }
        }
    }

    private void compareSingleSlaveIdentity(final EniSlave left, final EniSlave right, final List<OuterJoinRow> rows) {
        final List<FieldDelta> idDeltas = diffSlaves(left, right);
        rows.add(new OuterJoinRow(
                "SLAVE:IDENTITY",
                SEC_SLAVE,
                "Identity, Addressing & DC",
                idDeltas.isEmpty() ? DiffStatus.MATCH : DiffStatus.CHANGED,
                summarizeSlave(left),
                summarizeSlave(right),
                idDeltas));
    }

    private void compareSyncManagers(
            final List<SyncManagerConfig> leftSms,
            final List<SyncManagerConfig> rightSms,
            final List<OuterJoinRow> rows) {
        final Map<Integer, SyncManagerConfig> lMap = new LinkedHashMap<>();
        final Map<Integer, SyncManagerConfig> rMap = new LinkedHashMap<>();
        for (final SyncManagerConfig sm : leftSms) {
            lMap.put(sm.index(), sm);
        }
        for (final SyncManagerConfig sm : rightSms) {
            rMap.put(sm.index(), sm);
        }
        final Set<Integer> all = new LinkedHashSet<>();
        all.addAll(lMap.keySet());
        all.addAll(rMap.keySet());
        for (final Integer idx : all) {
            final SyncManagerConfig l = lMap.get(idx);
            final SyncManagerConfig r = rMap.get(idx);
            final String key = "SM:" + idx;
            if (l != null && r == null) {
                rows.add(new OuterJoinRow(key, "SYNC_MANAGER", "SM" + idx, DiffStatus.LEFT_ONLY,
                        formatSm(l), ABSENT_LABEL, List.of()));
            } else if (l == null && r != null) {
                rows.add(new OuterJoinRow(key, "SYNC_MANAGER", "SM" + idx, DiffStatus.RIGHT_ONLY,
                        ABSENT_LABEL, formatSm(r), List.of()));
            } else if (l != null) {
                final List<FieldDelta> deltas = new ArrayList<>();
                compareField("type", l.type(), r.type(), deltas);
                compareField("startAddress", String.format(Locale.ROOT, HEX4_FORMAT, l.startAddress()),
                        String.format(Locale.ROOT, HEX4_FORMAT, r.startAddress()), deltas);
                compareField("defaultSize", String.valueOf(l.defaultSize()), String.valueOf(r.defaultSize()), deltas);
                compareField("controlByte", String.valueOf(l.controlByte()), String.valueOf(r.controlByte()), deltas);
                compareField("pdoIndices", l.pdoIndices().toString(), r.pdoIndices().toString(), deltas);
                rows.add(new OuterJoinRow(key, "SYNC_MANAGER", "SM" + idx,
                        deltas.isEmpty() ? DiffStatus.MATCH : DiffStatus.CHANGED,
                        formatSm(l), formatSm(r), deltas));
            }
        }
    }

    private void comparePdoLists(
            final String section,
            final List<PdoMapping> leftPdos,
            final List<PdoMapping> rightPdos,
            final List<OuterJoinRow> rows) {
        final Map<Integer, PdoMapping> lMap = new LinkedHashMap<>();
        final Map<Integer, PdoMapping> rMap = new LinkedHashMap<>();
        for (final PdoMapping pdo : leftPdos) {
            lMap.put(pdo.index(), pdo);
        }
        for (final PdoMapping pdo : rightPdos) {
            rMap.put(pdo.index(), pdo);
        }
        final Set<Integer> all = new LinkedHashSet<>();
        all.addAll(lMap.keySet());
        all.addAll(rMap.keySet());
        for (final Integer idx : all) {
            final PdoMapping l = lMap.get(idx);
            final PdoMapping r = rMap.get(idx);
            final String hex = String.format(Locale.ROOT, HEX4_FORMAT, idx);
            final String key = section + ":" + hex;
            if (l != null && r == null) {
                rows.add(new OuterJoinRow(key, section, l.name() + " (" + hex + ")", DiffStatus.LEFT_ONLY,
                        formatPdo(l), ABSENT_LABEL, List.of()));
            } else if (l == null && r != null) {
                rows.add(new OuterJoinRow(key, section, r.name() + " (" + hex + ")", DiffStatus.RIGHT_ONLY,
                        ABSENT_LABEL, formatPdo(r), List.of()));
            } else if (l != null) {
                final List<FieldDelta> deltas = new ArrayList<>();
                compareField("name", l.name(), r.name(), deltas);
                compareField("smIndex", String.valueOf(l.flags().syncManager()),
                        String.valueOf(r.flags().syncManager()), deltas);
                compareField("entries", formatPdoEntries(l.entries()), formatPdoEntries(r.entries()), deltas);
                rows.add(new OuterJoinRow(key, section, l.name() + " (" + hex + ")",
                        deltas.isEmpty() ? DiffStatus.MATCH : DiffStatus.CHANGED,
                        formatPdo(l), formatPdo(r), deltas));
            }
        }
    }

    private void compareCoeInitCmds(
            final List<CoeInitCmd> leftCmds,
            final List<CoeInitCmd> rightCmds,
            final List<OuterJoinRow> rows) {
        final Map<String, CoeInitCmd> lMap = new LinkedHashMap<>();
        final Map<String, CoeInitCmd> rMap = new LinkedHashMap<>();
        for (int i = 0; i < leftCmds.size(); i++) {
            final CoeInitCmd c = leftCmds.get(i);
            lMap.put(coeKey(c, i), c);
        }
        for (int i = 0; i < rightCmds.size(); i++) {
            final CoeInitCmd c = rightCmds.get(i);
            rMap.put(coeKey(c, i), c);
        }
        final Set<String> all = new LinkedHashSet<>();
        all.addAll(lMap.keySet());
        all.addAll(rMap.keySet());
        for (final String key : all) {
            final CoeInitCmd l = lMap.get(key);
            final CoeInitCmd r = rMap.get(key);
            if (l != null && r == null) {
                rows.add(new OuterJoinRow("COE:" + key, "COE_INIT_CMD", l.comment(), DiffStatus.LEFT_ONLY,
                        l.dataHex(), ABSENT_LABEL, List.of()));
            } else if (l == null && r != null) {
                rows.add(new OuterJoinRow("COE:" + key, "COE_INIT_CMD", r.comment(), DiffStatus.RIGHT_ONLY,
                        ABSENT_LABEL, r.dataHex(), List.of()));
            } else if (l != null) {
                final List<FieldDelta> deltas = new ArrayList<>();
                compareField("dataHex", l.dataHex(), r.dataHex(), deltas);
                compareField("transitions", l.transitions().toString(), r.transitions().toString(), deltas);
                rows.add(new OuterJoinRow("COE:" + key, "COE_INIT_CMD", l.comment(),
                        deltas.isEmpty() ? DiffStatus.MATCH : DiffStatus.CHANGED,
                        l.dataHex(), r.dataHex(), deltas));
            }
        }
    }

    private void compareRegisterInitCmds(
            final String section,
            final List<InitCmd> leftCmds,
            final List<InitCmd> rightCmds,
            final List<OuterJoinRow> rows) {
        final int max = Math.max(leftCmds.size(), rightCmds.size());
        for (int i = 0; i < max; i++) {
            final String key = section + ":" + i;
            final InitCmd l = i < leftCmds.size() ? leftCmds.get(i) : null;
            final InitCmd r = i < rightCmds.size() ? rightCmds.get(i) : null;
            if (l != null && r == null) {
                rows.add(new OuterJoinRow(key, section, l.comment(), DiffStatus.LEFT_ONLY,
                        formatInitCmd(l), ABSENT_LABEL, List.of()));
            } else if (l == null && r != null) {
                rows.add(new OuterJoinRow(key, section, r.comment(), DiffStatus.RIGHT_ONLY,
                        ABSENT_LABEL, formatInitCmd(r), List.of()));
            } else if (l != null) {
                final List<FieldDelta> deltas = new ArrayList<>();
                final String ls = formatInitCmd(l);
                final String rs = formatInitCmd(r);
                compareField("initCmd", ls, rs, deltas);
                rows.add(new OuterJoinRow(key, section, l.comment(),
                        deltas.isEmpty() ? DiffStatus.MATCH : DiffStatus.CHANGED, ls, rs, deltas));
            }
        }
    }

    private List<FieldDelta> diffSlaves(final EniSlave l, final EniSlave r) {
        final List<FieldDelta> deltas = new ArrayList<>();
        compareField("name", l.identity().name(), r.identity().name(), deltas);
        compareField("physAddr", String.valueOf(l.identity().physAddr()), String.valueOf(r.identity().physAddr()), deltas);
        compareField("physics", l.identity().physics(), r.identity().physics(), deltas);
        compareField("vendorId", Long.toHexString(l.identity().vendorId()), Long.toHexString(r.identity().vendorId()), deltas);
        compareField("productCode", Long.toHexString(l.identity().productCode()),
                Long.toHexString(r.identity().productCode()), deltas);
        compareField("revisionNo", Long.toHexString(l.identity().revisionNo()),
                Long.toHexString(r.identity().revisionNo()), deltas);
        compareField("previousPort", formatPrevPort(l), formatPrevPort(r), deltas);
        compareField("hotConnect",
                l.hotConnect().map(HotConnectGroup::groupName).orElse(""),
                r.hotConnect().map(HotConnectGroup::groupName).orElse(""),
                deltas);
        compareField("mailboxProtocols", l.mailbox().protocols().toString(), r.mailbox().protocols().toString(), deltas);
        compareField("dcEnabled", String.valueOf(l.dc().enabled()), String.valueOf(r.dc().enabled()), deltas);
        compareField("dcCycleTime0", String.valueOf(l.dc().cycleTime0()), String.valueOf(r.dc().cycleTime0()), deltas);
        compareField("rxPdoCount",
                String.valueOf(l.processData().rxPdos().size()),
                String.valueOf(r.processData().rxPdos().size()),
                deltas);
        compareField("txPdoCount",
                String.valueOf(l.processData().txPdos().size()),
                String.valueOf(r.processData().txPdos().size()),
                deltas);
        compareField("coeInitCmdCount",
                String.valueOf(l.mailbox().coeInitCmds().size()),
                String.valueOf(r.mailbox().coeInitCmds().size()),
                deltas);
        return deltas;
    }

    private List<EniSlave> extractHotConnectMembers(final EniDocument doc, final String groupName) {
        final List<EniSlave> slaves = doc.slaves();
        for (int i = 0; i < slaves.size(); i++) {
            final EniSlave head = slaves.get(i);
            if (head.hotConnect().isPresent() && groupName.equals(head.hotConnect().get().groupName())) {
                final int cnt = head.hotConnect().get().groupMemberCount();
                final int end = Math.min(slaves.size(), i + cnt);
                return new ArrayList<>(slaves.subList(i, end));
            }
        }
        return List.of();
    }

    private List<LinearBusItem> buildLinearBus(final List<EniSlave> slaves, final Map<String, DiffStatus> statusByKey) {
        final List<LinearBusItem> list = new ArrayList<>(slaves.size());
        for (int i = 0; i < slaves.size(); i++) {
            final EniSlave s = slaves.get(i);
            final String key = slaveKey(s);
            final DiffStatus st = statusByKey.getOrDefault(key, DiffStatus.MATCH);
            final String partner = (st == DiffStatus.MATCH || st == DiffStatus.CHANGED) ? key : "";
            list.add(toLinearBusItem(i, s, st, partner));
        }
        return list;
    }

    private List<LinearBusItem> buildLinearBusByIndex(
            final List<EniSlave> slaves,
            final Map<String, DiffStatus> statusByIdx) {
        final List<LinearBusItem> list = new ArrayList<>(slaves.size());
        for (int i = 0; i < slaves.size(); i++) {
            final EniSlave s = slaves.get(i);
            final String key = "HC_MEMBER#" + i;
            final DiffStatus st = statusByIdx.getOrDefault(key, DiffStatus.MATCH);
            list.add(toLinearBusItem(i, s, st, key));
        }
        return list;
    }

    private LinearBusItem toLinearBusItem(
            final int index,
            final EniSlave slave,
            final DiffStatus status,
            final String counterpartKey) {
        final int rxBits = sumPdoBits(slave.processData().rxPdos());
        final int txBits = sumPdoBits(slave.processData().txPdos());
        final String category = SlaveCatalogService.classifyCategory(
                slave.identity().physics(), slave.identity().name());
        return new LinearBusItem(
                index,
                slave.identity().physAddr(),
                slave.identity().name(),
                category,
                slave.identity().physics(),
                formatPrevPort(slave),
                slave.hotConnect().map(HotConnectGroup::groupName).orElse(""),
                slave.dc().enabled(),
                rxBits,
                txBits,
                status,
                counterpartKey);
    }

    private ComparisonTreeNode buildEniTree(
            final String prefix,
            final String title,
            final EniDocument doc,
            final Map<String, DiffStatus> slaveStatusByKey,
            final boolean topologyFocused) {
        final List<ComparisonTreeNode> slaveChildren = new ArrayList<>();
        for (final EniSlave s : doc.slaves()) {
            final String key = slaveKey(s);
            final DiffStatus st = slaveStatusByKey.getOrDefault(key, DiffStatus.MATCH);
            slaveChildren.add(new ComparisonTreeNode(
                    prefix + "-" + key,
                    "#" + s.identity().physAddr() + " " + s.identity().name(),
                    SEC_SLAVE,
                    st,
                    summarizeSlave(s),
                    List.of()));
        }
        final List<ComparisonTreeNode> rootChildren = new ArrayList<>();
        rootChildren.add(new ComparisonTreeNode(
                prefix + "-master",
                "Master: " + doc.master().name(),
                SEC_MASTER,
                DiffStatus.MATCH,
                "cycle=" + doc.cyclic().cycleTimeUs() + "us red=" + doc.master().redundancy().enabled(),
                List.of()));
        rootChildren.add(new ComparisonTreeNode(
                prefix + "-slaves",
                "Slaves (" + doc.slaves().size() + ")",
                SEC_SLAVE,
                deriveAggregateStatus(slaveChildren),
                "HC=" + doc.hotConnectGroupCount(),
                slaveChildren));
        if (!topologyFocused) {
            rootChildren.add(new ComparisonTreeNode(
                    prefix + "-cyclic",
                    "Cyclic Frames (" + doc.cyclic().frames().size() + ")",
                    SEC_CYCLIC,
                    DiffStatus.MATCH,
                    "cycleTimeUs=" + doc.cyclic().cycleTimeUs(),
                    List.of()));
            rootChildren.add(new ComparisonTreeNode(
                    prefix + "-pi",
                    "ProcessImage",
                    SEC_PROCESS_VAR,
                    DiffStatus.MATCH,
                    "In=" + doc.processImage().inputs().size() + " Out=" + doc.processImage().outputs().size(),
                    List.of()));
        }
        return new ComparisonTreeNode(
                prefix + "-root",
                title,
                "ROOT",
                deriveAggregateStatus(rootChildren),
                "ENI v" + doc.version(),
                rootChildren);
    }

    private ComparisonTreeNode buildSlaveDetailTree(
            final String prefix,
            final String label,
            final EniSlave slave,
            final List<OuterJoinRow> rows) {
        final Map<String, DiffStatus> rowStatusByKey = new LinkedHashMap<>();
        for (final OuterJoinRow r : rows) {
            rowStatusByKey.put(r.rowKey(), r.status());
        }
        final List<ComparisonTreeNode> children = new ArrayList<>();
        for (final SyncManagerConfig sm : slave.processData().syncManagers()) {
            children.add(new ComparisonTreeNode(
                    prefix + "-sm-" + sm.index(),
                    "SM" + sm.index() + " [" + sm.type() + "]",
                    "SYNC_MANAGER",
                    rowStatusByKey.getOrDefault("SM:" + sm.index(), DiffStatus.MATCH),
                    formatSm(sm),
                    List.of()));
        }
        for (final PdoMapping pdo : slave.processData().rxPdos()) {
            final String hex = String.format(Locale.ROOT, HEX4_FORMAT, pdo.index());
            children.add(new ComparisonTreeNode(
                    prefix + "-rx-" + hex,
                    "RxPDO " + hex + " " + pdo.name(),
                    "RX_PDO",
                    rowStatusByKey.getOrDefault("RX_PDO:" + hex, DiffStatus.MATCH),
                    formatPdo(pdo),
                    List.of()));
        }
        for (final PdoMapping pdo : slave.processData().txPdos()) {
            final String hex = String.format(Locale.ROOT, HEX4_FORMAT, pdo.index());
            children.add(new ComparisonTreeNode(
                    prefix + "-tx-" + hex,
                    "TxPDO " + hex + " " + pdo.name(),
                    "TX_PDO",
                    rowStatusByKey.getOrDefault("TX_PDO:" + hex, DiffStatus.MATCH),
                    formatPdo(pdo),
                    List.of()));
        }
        return new ComparisonTreeNode(
                prefix + "-root",
                label,
                SEC_SLAVE,
                deriveAggregateStatus(children),
                summarizeSlave(slave),
                children);
    }

    private ComparisonTreeNode buildHotConnectTree(
            final String prefix,
            final String title,
            final HotConnectGroup head,
            final List<EniSlave> members,
            final Map<String, DiffStatus> memberStatus) {
        final List<ComparisonTreeNode> children = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            final EniSlave m = members.get(i);
            children.add(new ComparisonTreeNode(
                    prefix + "-m-" + i,
                    "#" + m.identity().physAddr() + " " + m.identity().name(),
                    SEC_SLAVE,
                    memberStatus.getOrDefault("HC_MEMBER#" + i, DiffStatus.MATCH),
                    summarizeSlave(m),
                    List.of()));
        }
        return new ComparisonTreeNode(
                prefix + "-root",
                title,
                SEC_HOT_CONNECT,
                deriveAggregateStatus(children),
                "members=" + head.groupMemberCount() + " identifyCmds=" + head.identifyCmds().size(),
                children);
    }

    private DiffStatus deriveAggregateStatus(final List<ComparisonTreeNode> nodes) {
        for (final ComparisonTreeNode n : nodes) {
            if (n.status() != DiffStatus.MATCH) {
                return DiffStatus.CHANGED;
            }
        }
        return DiffStatus.MATCH;
    }

    private ComparisonSummary summarize(final List<OuterJoinRow> rows) {
        int matched = 0;
        int changed = 0;
        int leftOnly = 0;
        int rightOnly = 0;
        for (final OuterJoinRow row : rows) {
            switch (row.status()) {
                case MATCH -> matched++;
                case CHANGED -> changed++;
                case LEFT_ONLY -> leftOnly++;
                case RIGHT_ONLY -> rightOnly++;
                default -> { }
            }
        }
        return new ComparisonSummary(rows.size(), matched, changed, leftOnly, rightOnly);
    }

    private void compareField(
            final String field,
            final String leftVal,
            final String rightVal,
            final List<FieldDelta> deltas) {
        if (!Objects.equals(leftVal, rightVal)) {
            deltas.add(new FieldDelta(field, leftVal, rightVal));
        }
    }

    private String slaveKey(final EniSlave slave) {
        return "SLAVE:" + slave.identity().physAddr();
    }

    private String coeKey(final CoeInitCmd cmd, final int ordinal) {
        return String.format(Locale.ROOT, "0x%04X:%d:ccs%d#%d", cmd.index(), cmd.subIndex(), cmd.ccs(), ordinal);
    }

    private String summarizeSlave(final EniSlave s) {
        return String.format(
                Locale.ROOT,
                "Phys=%d [%s] Prev=%s Mbx=%s DC=%s Rx=%db Tx=%db",
                s.identity().physAddr(),
                s.identity().physics(),
                formatPrevPort(s),
                s.mailbox().protocols(),
                s.dc().enabled() ? s.dc().cycleTime0() + "ns" : "off",
                sumPdoBits(s.processData().rxPdos()),
                sumPdoBits(s.processData().txPdos()));
    }

    private String formatPrevPort(final EniSlave s) {
        return s.previousPort()
                .map(pp -> pp.physAddr() + ":" + pp.port())
                .orElse("Master");
    }

    private String summarizeFrame(final CyclicFrame frame) {
        return frame.commands().size() + " cmds [" + frame.totalEthercatPayloadBytes() + "B]";
    }

    private String formatVar(final ProcessImageVariable v) {
        return v.dataType() + " @bit" + v.bitOffs() + " (" + v.bitSize() + "b)";
    }

    private String formatSm(final SyncManagerConfig sm) {
        return String.format(Locale.ROOT, "%s addr=0x%04X size=%d", sm.type(), sm.startAddress(), sm.defaultSize());
    }

    private String formatPdo(final PdoMapping pdo) {
        int bits = 0;
        for (final PdoEntry e : pdo.entries()) {
            bits += e.bitLen();
        }
        return "SM" + pdo.flags().syncManager() + " entries=" + pdo.entries().size() + " bits=" + bits;
    }

    private String formatPdoEntries(final List<PdoEntry> entries) {
        final StringBuilder sb = new StringBuilder();
        for (final PdoEntry e : entries) {
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append(String.format(Locale.ROOT, "0x%04X:%d(%db)", e.index(), e.subIndex(), e.bitLen()));
        }
        return sb.toString();
    }

    private String formatInitCmd(final InitCmd cmd) {
        return cmd.cmd().name() + " addr=" + cmd.address().addr() + " data=" + cmd.payload().dataHex();
    }

    private int sumPdoBits(final List<PdoMapping> pdos) {
        int total = 0;
        for (final PdoMapping pdo : pdos) {
            for (final PdoEntry e : pdo.entries()) {
                total += e.bitLen();
            }
        }
        return total;
    }
}
