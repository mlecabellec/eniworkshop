package fr.lecabellec.eniworkshop.eni.model;

import java.util.Locale;
import java.util.Objects;

/**
 * EtherCAT Mailbox application protocols defined in ETG.1000.6 and ETG.2100.
 *
 * <p>Requirement: REQ-00002, REQ-00003.
 */
public enum MailboxProtocol {
    /** CANopen over EtherCAT. */
    COE("CoE"),
    /** ADS over EtherCAT. */
    AOE("AoE"),
    /** Ethernet over EtherCAT. */
    EOE("EoE"),
    /** File Access over EtherCAT. */
    FOE("FoE"),
    /** Servo Drive Profile over EtherCAT (SERCOS). */
    SOE("SoE"),
    /** Vendor-Specific Protocol over EtherCAT. */
    VOE("VoE");

    /** Canonical ETG.2100 XML protocol label. */
    private final String xmlName;

    /**
     * Constructs a MailboxProtocol constant.
     *
     * @param xmlName canonical XML tag/text name
     */
    MailboxProtocol(final String xmlName) {
        this.xmlName = xmlName;
    }

    /**
     * Returns the canonical ENI XML name.
     *
     * @return canonical name (e.g., "CoE")
     */
    public String getXmlName() {
        return xmlName;
    }

    /**
     * Parses a mailbox protocol string case-insensitively.
     *
     * @param raw protocol name from ENI XML
     * @return matching {@link MailboxProtocol}
     */
    public static MailboxProtocol parse(final String raw) {
        Objects.requireNonNull(raw, "Mailbox protocol string must not be null");
        final String upper = raw.trim().toUpperCase(Locale.ROOT);
        return MailboxProtocol.valueOf(upper);
    }
}
