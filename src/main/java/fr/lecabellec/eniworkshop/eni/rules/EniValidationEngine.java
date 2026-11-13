package fr.lecabellec.eniworkshop.eni.rules;

import fr.lecabellec.eniworkshop.eni.model.CyclicCommand;
import fr.lecabellec.eniworkshop.eni.model.CyclicFrame;
import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.model.EniSlave;
import fr.lecabellec.eniworkshop.eni.model.EthercatConstants;
import fr.lecabellec.eniworkshop.eni.model.HotConnectGroup;
import fr.lecabellec.eniworkshop.eni.model.MailboxProtocol;
import fr.lecabellec.eniworkshop.eni.model.PreviousPort;
import fr.lecabellec.eniworkshop.eni.model.ProcessImageVariable;
import fr.lecabellec.eniworkshop.eni.model.SyncManagerConfig;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Executes ETG.1000 and ETG.2100 EtherCAT validation rules across an {@link EniDocument}.
 *
 * <p>Requirement: REQ-00003, Feature: FR-00003, Constraint: CS-0030.6.2, CS-0060.1.
 */
@Service
public final class EniValidationEngine {

    /** Minimum SyncManager overlap comparison count. */
    private static final int PORT_INDEX_A = 0;

    /** Port B index inside Physics string. */
    private static final int PORT_INDEX_B = 1;

    /** Port C index inside Physics string. */
    private static final int PORT_INDEX_C = 2;

    /** Port D index inside Physics string. */
    private static final int PORT_INDEX_D = 3;

    /** Expected 12-hex-digit MAC address length. */
    private static final int MAC_HEX_LENGTH = 12;

    /** Ordered list of registered Strategy rules. */
    private final List<EniValidationRule> rules;

    /**
     * Constructs an {@link EniValidationEngine} with all standard ETG rules registered.
     */
    public EniValidationEngine() {
        this.rules = List.of(
                new AddressingRule(),
                new TopologyPhysicsRule(),
                new SyncManagerAndMailboxRule(),
                new CyclicAndProcessImageRule(),
                new HotConnectAndRedundancyRule(),
                new DistributedClocksRule());
    }

    /**
     * Validates an {@link EniDocument} against all EtherCAT management rules.
     *
     * @param document non-null ENI document
     * @return structured {@link ValidationReport}
     */
    public ValidationReport validate(final EniDocument document) {
        Objects.requireNonNull(document, "EniDocument must not be null");
        final List<ValidationIssue> allIssues = new ArrayList<>();
        for (final EniValidationRule rule : rules) {
            allIssues.addAll(rule.validate(document));
        }
        int errors = 0;
        int warnings = 0;
        for (final ValidationIssue issue : allIssues) {
            switch (issue.severity()) {
                case ERROR -> errors++;
                case WARNING -> warnings++;
                case INFO -> {
                    // Informational diagnostics do not increment error/warning counters.
                }
                default -> throw new IllegalStateException("Unexpected severity: " + issue.severity());
            }
        }
        final boolean valid = errors == 0;
        final String summary = String.format(
                "Validated %d slaves (%d HC groups): %d errors, %d warnings, %d total findings",
                document.slaves().size(),
                document.hotConnectGroupCount(),
                errors,
                warnings,
                allIssues.size());
        return new ValidationReport(valid, errors, warnings, allIssues, summary);
    }

    /**
     * Maps port letter 'A'..'D' to 0-based index in ESC Physics string.
     *
     * @param port port character
     * @return index 0..3 or -1
     */
    private static int portToIndex(final char port) {
        return switch (Character.toUpperCase(port)) {
            case 'A' -> PORT_INDEX_A;
            case 'B' -> PORT_INDEX_B;
            case 'C' -> PORT_INDEX_C;
            case 'D' -> PORT_INDEX_D;
            default -> -1;
        };
    }

    /**
     * Validates station addressing uniqueness and non-zero assignment.
     */
    private static final class AddressingRule implements EniValidationRule {

        @Override
        public String ruleId() {
            return "ECAT-ADDR-001";
        }

        @Override
        public List<ValidationIssue> validate(final EniDocument document) {
            final List<ValidationIssue> issues = new ArrayList<>();
            final Set<Integer> seenPhys = new HashSet<>();
            for (int i = 0; i < document.slaves().size(); i++) {
                final EniSlave slave = document.slaves().get(i);
                final int phys = slave.identity().physAddr();
                if (phys <= 0) {
                    issues.add(new ValidationIssue(
                            ruleId(),
                            ValidationSeverity.ERROR,
                            slave.identity().name(),
                            "Configured station address (PhysAddr) must be > 0",
                            "ETG.1000.4 Sec 4.3"));
                } else if (!seenPhys.add(phys)) {
                    issues.add(new ValidationIssue(
                            ruleId(),
                            ValidationSeverity.ERROR,
                            slave.identity().name(),
                            "Duplicate PhysAddr detected: " + phys,
                            "ETG.1000.4 Sec 4.3"));
                }
            }
            return List.copyOf(issues);
        }
    }

    /**
     * Validates PreviousPort references and ESC port physics compatibility (Y/K/F/H).
     */
    private static final class TopologyPhysicsRule implements EniValidationRule {

        @Override
        public String ruleId() {
            return "ECAT-TOPO-001";
        }

        @Override
        public List<ValidationIssue> validate(final EniDocument document) {
            final List<ValidationIssue> issues = new ArrayList<>();
            final Map<Integer, EniSlave> byPhys = new HashMap<>();
            for (final EniSlave s : document.slaves()) {
                byPhys.put(s.identity().physAddr(), s);
            }
            for (final EniSlave slave : document.slaves()) {
                if (slave.previousPort().isEmpty()) {
                    continue;
                }
                final PreviousPort prev = slave.previousPort().get();
                final EniSlave upstream = byPhys.get(prev.physAddr());
                if (upstream == null) {
                    issues.add(new ValidationIssue(
                            ruleId(),
                            ValidationSeverity.ERROR,
                            slave.identity().name(),
                            "PreviousPort references unknown upstream PhysAddr: " + prev.physAddr(),
                            "ETG.2100 Sec 5.4"));
                    continue;
                }
                final String upPhysics = upstream.identity().physics();
                final String downPhysics = slave.identity().physics();
                final int upIdx = portToIndex(prev.port());
                if (!upPhysics.isEmpty() && upIdx >= 0 && upIdx < upPhysics.length() && !downPhysics.isEmpty()) {
                    final char upPhy = upPhysics.charAt(upIdx);
                    final char downPhy = downPhysics.charAt(0);
                    if (upPhy != ' ' && downPhy != ' ' && upPhy != downPhy) {
                        issues.add(new ValidationIssue(
                                ruleId(),
                                ValidationSeverity.ERROR,
                                slave.identity().name(),
                                "Port physics mismatch: upstream " + upstream.identity().name()
                                        + " port " + prev.port() + " (" + upPhy + ") vs downstream Port A (" + downPhy + ")",
                                "ETG.1000.2"));
                    }
                }
            }
            return List.copyOf(issues);
        }
    }

    /**
     * Validates SyncManager DPRAM non-overlap and Mailbox protocol declarations.
     */
    private static final class SyncManagerAndMailboxRule implements EniValidationRule {

        @Override
        public String ruleId() {
            return "ECAT-SM-001";
        }

        @Override
        public List<ValidationIssue> validate(final EniDocument document) {
            final List<ValidationIssue> issues = new ArrayList<>();
            for (final EniSlave slave : document.slaves()) {
                final List<SyncManagerConfig> sms = slave.processData().syncManagers();
                for (int i = 0; i < sms.size(); i++) {
                    final SyncManagerConfig a = sms.get(i);
                    if (!a.enable() || a.defaultSize() <= 0) {
                        continue;
                    }
                    for (int j = i + 1; j < sms.size(); j++) {
                        final SyncManagerConfig b = sms.get(j);
                        if (!b.enable() || b.defaultSize() <= 0) {
                            continue;
                        }
                        final int aEnd = a.startAddress() + a.defaultSize();
                        final int bEnd = b.startAddress() + b.defaultSize();
                        if (a.startAddress() < bEnd && b.startAddress() < aEnd) {
                            issues.add(new ValidationIssue(
                                    ruleId(),
                                    ValidationSeverity.ERROR,
                                    slave.identity().name(),
                                    "Overlapping enabled SyncManager DPRAM regions: Sm" + a.index() + " and Sm" + b.index(),
                                    "ETG.1000.4 Sec 6.7"));
                        }
                    }
                }
                if (!slave.mailbox().coeInitCmds().isEmpty()
                        && !slave.mailbox().protocols().contains(MailboxProtocol.COE)) {
                    issues.add(new ValidationIssue(
                            "ECAT-MBX-001",
                            ValidationSeverity.WARNING,
                            slave.identity().name(),
                            "Slave defines CoE InitCmds without declaring CoE in Mailbox/Protocol",
                            "ETG.2100 Sec 5.6"));
                }
            }
            return List.copyOf(issues);
        }
    }

    /**
     * Validates Ethernet MTU bounds for cyclic frames and ProcessImage variable ranges.
     */
    private static final class CyclicAndProcessImageRule implements EniValidationRule {

        @Override
        public String ruleId() {
            return "ECAT-CYC-001";
        }

        @Override
        public List<ValidationIssue> validate(final EniDocument document) {
            final List<ValidationIssue> issues = new ArrayList<>();
            final int frameCount = document.cyclic().frames().size();
            for (int f = 0; f < frameCount; f++) {
                final CyclicFrame frame = document.cyclic().frames().get(f);
                final int payloadBytes = frame.totalEthercatPayloadBytes();
                if (payloadBytes > EthercatConstants.MAX_ETHERCAT_PAYLOAD_BYTES) {
                    issues.add(new ValidationIssue(
                            ruleId(),
                            ValidationSeverity.ERROR,
                            "CyclicFrame#" + f,
                            "Cyclic frame payload (" + payloadBytes + " B) exceeds Ethernet MTU limit ("
                                    + EthercatConstants.MAX_ETHERCAT_PAYLOAD_BYTES + " B)",
                            "ETG.1000.4 Sec 5.2"));
                }
                for (final CyclicCommand cmd : frame.commands()) {
                    if (cmd.dataLength() > EthercatConstants.MAX_SINGLE_DATAGRAM_DATA_BYTES) {
                        issues.add(new ValidationIssue(
                                ruleId(),
                                ValidationSeverity.ERROR,
                                "CyclicFrame#" + f,
                                "Cyclic datagram length (" + cmd.dataLength() + " B) exceeds 1486 B",
                                "ETG.1000.4 Sec 5.3"));
                    }
                }
            }
            validateVariableBounds(
                    document.processImage().inputs(),
                    document.processImage().inputsByteSize(),
                    "ProcessImage.Inputs",
                    issues);
            validateVariableBounds(
                    document.processImage().outputs(),
                    document.processImage().outputsByteSize(),
                    "ProcessImage.Outputs",
                    issues);
            return List.copyOf(issues);
        }

        /**
         * Verifies every variable lies within declared ProcessImage ByteSize * 8 bits.
         *
         * @param vars     variable list
         * @param byteSize declared byte size
         * @param target   diagnostic target name
         * @param issues   output diagnostics list
         */
        private void validateVariableBounds(
                final List<ProcessImageVariable> vars,
                final int byteSize,
                final String target,
                final List<ValidationIssue> issues) {
            final long maxBits = (long) byteSize * EthercatConstants.BITS_PER_BYTE;
            for (final ProcessImageVariable v : vars) {
                final long endBit = (long) v.bitOffs() + v.bitSize();
                if (endBit > maxBits) {
                    issues.add(new ValidationIssue(
                            "ECAT-PI-001",
                            ValidationSeverity.ERROR,
                            target,
                            "Variable '" + v.name() + "' ends at bit " + endBit
                                    + " beyond declared ByteSize (" + maxBits + " bits)",
                            "ETG.2100 Sec 5.8"));
                }
            }
        }
    }

    /**
     * Validates HotConnect groups and Master Cable Redundancy ring settings.
     */
    private static final class HotConnectAndRedundancyRule implements EniValidationRule {

        @Override
        public String ruleId() {
            return "ECAT-HC-001";
        }

        @Override
        public List<ValidationIssue> validate(final EniDocument document) {
            final List<ValidationIssue> issues = new ArrayList<>();
            final int totalSlaves = document.slaves().size();
            for (int i = 0; i < totalSlaves; i++) {
                final EniSlave slave = document.slaves().get(i);
                if (slave.hotConnect().isPresent()) {
                    final HotConnectGroup hc = slave.hotConnect().get();
                    if (i + hc.groupMemberCount() > totalSlaves) {
                        issues.add(new ValidationIssue(
                                ruleId(),
                                ValidationSeverity.ERROR,
                                slave.identity().name(),
                                "HotConnect group '" + hc.groupName() + "' claims " + hc.groupMemberCount()
                                        + " members extending past end of slave list",
                                "ETG.2100 Sec 5.5"));
                    }
                    if (hc.identifyCmds().isEmpty()) {
                        issues.add(new ValidationIssue(
                                ruleId(),
                                ValidationSeverity.WARNING,
                                slave.identity().name(),
                                "HotConnect group '" + hc.groupName() + "' has no IdentifyCmd",
                                "ETG.2100 Sec 5.5"));
                    }
                }
            }
            if (document.master().redundancy().enabled()) {
                final String secMac = document.master().redundancy().secondarySourceMac().trim();
                if (secMac.length() != MAC_HEX_LENGTH) {
                    issues.add(new ValidationIssue(
                            "ECAT-RED-001",
                            ValidationSeverity.ERROR,
                            document.master().name(),
                            "Redundancy enabled but secondarySourceMac is not 12 hex digits",
                            "ETG.1000 Cable Redundancy"));
                }
            }
            return List.copyOf(issues);
        }
    }

    /**
     * Validates Distributed Clocks (DC) potential reference clock availability.
     */
    private static final class DistributedClocksRule implements EniValidationRule {

        @Override
        public String ruleId() {
            return "ECAT-DC-001";
        }

        @Override
        public List<ValidationIssue> validate(final EniDocument document) {
            final List<ValidationIssue> issues = new ArrayList<>();
            boolean anyDcEnabled = false;
            boolean anyRefCandidate = false;
            for (final EniSlave slave : document.slaves()) {
                if (slave.dc().enabled()) {
                    anyDcEnabled = true;
                }
                if (slave.dc().potentialReferenceClock() || slave.dc().referenceClock()) {
                    anyRefCandidate = true;
                }
            }
            if (anyDcEnabled && !anyRefCandidate) {
                issues.add(new ValidationIssue(
                        ruleId(),
                        ValidationSeverity.WARNING,
                        document.master().name(),
                        "Distributed Clocks enabled on slaves, but no PotentialReferenceClock is declared",
                        "ETG.1000.6 Sec 6"));
            }
            return List.copyOf(issues);
        }
    }
}
