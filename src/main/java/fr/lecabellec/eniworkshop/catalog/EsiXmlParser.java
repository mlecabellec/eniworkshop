package fr.lecabellec.eniworkshop.catalog;

import fr.lecabellec.eniworkshop.EniWorkshopException;
import fr.lecabellec.eniworkshop.eni.model.MailboxProtocol;
import org.springframework.stereotype.Service;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Streaming ETG.2000 EtherCAT Slave Information (ESI) XML parser for catalog enrichment.
 *
 * <p>Requirement: REQ-00004, Feature: FR-00004, Constraint: CS-0030.9.
 */
@Service
public final class EsiXmlParser {

    /** Hexadecimal radix. */
    private static final int HEX_RADIX = 16;

    /** XXE-hardened StAX factory. */
    private final XMLInputFactory xmlInputFactory;

    /**
     * Constructs an {@link EsiXmlParser} with XXE protections enabled.
     */
    public EsiXmlParser() {
        this.xmlInputFactory = XMLInputFactory.newInstance();
        this.xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        this.xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        this.xmlInputFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        this.xmlInputFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    }

    /**
     * Parses an ETG.2000 ESI XML payload into {@link SlaveCatalogEntry} definitions.
     *
     * @param xmlBytes   raw ESI XML bytes
     * @param sourceName logical filename or reference
     * @return immutable list of extracted catalog entries
     */
    public List<SlaveCatalogEntry> parse(final byte[] xmlBytes, final String sourceName) {
        Objects.requireNonNull(xmlBytes, "ESI xmlBytes must not be null");
        Objects.requireNonNull(sourceName, "ESI sourceName must not be null");
        try (InputStream is = new ByteArrayInputStream(xmlBytes)) {
            final XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(is);
            try {
                return readEtherCatInfo(reader, sourceName);
            } finally {
                reader.close();
            }
        } catch (final XMLStreamException | IOException ex) {
            throw new EniWorkshopException("Failed to parse ESI XML: " + ex.getMessage(), ex);
        }
    }

    /**
     * Reads {@code <EtherCATInfo>} Vendor and Device definitions.
     *
     * @param reader     StAX reader
     * @param sourceName source filename
     * @return parsed entries
     * @throws XMLStreamException on XML error
     */
    private List<SlaveCatalogEntry> readEtherCatInfo(final XMLStreamReader reader, final String sourceName)
            throws XMLStreamException {
        boolean foundRoot = false;
        long vendorId = 0L;
        final List<SlaveCatalogEntry> entries = new ArrayList<>();

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                if ("EtherCATInfo".equals(tag)) {
                    foundRoot = true;
                } else if ("Vendor".equals(tag)) {
                    vendorId = parseVendorId(reader);
                } else if ("Device".equals(tag)) {
                    entries.add(parseDevice(reader, vendorId, sourceName));
                }
            }
        }
        if (!foundRoot) {
            throw new EniWorkshopException("Invalid ESI XML: missing <EtherCATInfo> root element");
        }
        return List.copyOf(entries);
    }

    /**
     * Parses Vendor/Id inside ESI.
     *
     * @param reader StAX reader positioned at Vendor
     * @return vendor ID
     * @throws XMLStreamException on XML error
     */
    private long parseVendorId(final XMLStreamReader reader) throws XMLStreamException {
        long id = 0L;
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "Vendor".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT && "Id".equals(reader.getLocalName())) {
                id = parseLongFlexible(readLeafText(reader));
            }
        }
        return id;
    }

    /**
     * Parses a single {@code <Device>} element from ESI XML.
     *
     * @param reader     StAX reader
     * @param vendorId   enclosing vendor ID
     * @param sourceName source label
     * @return parsed {@link SlaveCatalogEntry}
     * @throws XMLStreamException on XML error
     */
    private SlaveCatalogEntry parseDevice(
            final XMLStreamReader reader,
            final long vendorId,
            final String sourceName) throws XMLStreamException {
        final String physicsAttr = reader.getAttributeValue(null, "Physics");
        final String physics = physicsAttr != null ? physicsAttr.trim() : "";
        String typeName = "Unknown Device";
        long productCode = 0L;
        long revisionNo = 0L;
        final List<MailboxProtocol> protocols = new ArrayList<>();

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "Device".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                if ("Type".equals(tag)) {
                    final String pcAttr = reader.getAttributeValue(null, "ProductCode");
                    final String revAttr = reader.getAttributeValue(null, "RevisionNo");
                    if (pcAttr != null && !pcAttr.isBlank()) {
                        productCode = parseLongFlexible(pcAttr);
                    }
                    if (revAttr != null && !revAttr.isBlank()) {
                        revisionNo = parseLongFlexible(revAttr);
                    }
                    final String text = readLeafText(reader).trim();
                    if (!text.isEmpty()) {
                        typeName = text;
                    }
                } else if ("CoE".equals(tag)) {
                    protocols.add(MailboxProtocol.COE);
                } else if ("FoE".equals(tag)) {
                    protocols.add(MailboxProtocol.FOE);
                } else if ("EoE".equals(tag)) {
                    protocols.add(MailboxProtocol.EOE);
                } else if ("SoE".equals(tag)) {
                    protocols.add(MailboxProtocol.SOE);
                } else if ("AoE".equals(tag)) {
                    protocols.add(MailboxProtocol.AOE);
                } else if ("VoE".equals(tag)) {
                    protocols.add(MailboxProtocol.VOE);
                }
            }
        }
        final String key = vendorId + ":" + productCode + ":" + revisionNo;
        final String category = SlaveCatalogService.classifyCategory(physics, typeName);
        return new SlaveCatalogEntry(
                key,
                vendorId,
                productCode,
                revisionNo,
                typeName,
                physics,
                category,
                protocols,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true,
                List.of(sourceName),
                Instant.now());
    }

    /**
     * Reads leaf element text cleanly.
     *
     * @param reader StAX reader
     * @return collected text
     * @throws XMLStreamException on XML error
     */
    private String readLeafText(final XMLStreamReader reader) throws XMLStreamException {
        final StringBuilder sb = new StringBuilder();
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                sb.append(reader.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Parses decimal or {@code #x...} / {@code 0x...} hexadecimal long values.
     *
     * @param raw raw attribute or element text
     * @return parsed long (0 if blank)
     */
    private long parseLongFlexible(final String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        final String s = raw.trim();
        if (s.startsWith("#x") || s.startsWith("#X") || s.startsWith("0x") || s.startsWith("0X")) {
            return Long.parseUnsignedLong(s.substring(2), HEX_RADIX);
        }
        return Long.parseLong(s);
    }
}
