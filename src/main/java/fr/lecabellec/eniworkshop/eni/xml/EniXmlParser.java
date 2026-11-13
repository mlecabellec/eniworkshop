package fr.lecabellec.eniworkshop.eni.xml;

import fr.lecabellec.eniworkshop.EniWorkshopException;
import fr.lecabellec.eniworkshop.eni.model.CoeChannelInfo;
import fr.lecabellec.eniworkshop.eni.model.CoeInitCmd;
import fr.lecabellec.eniworkshop.eni.model.CyclicCommand;
import fr.lecabellec.eniworkshop.eni.model.CyclicConfig;
import fr.lecabellec.eniworkshop.eni.model.CyclicFrame;
import fr.lecabellec.eniworkshop.eni.model.DatagramAddress;
import fr.lecabellec.eniworkshop.eni.model.DatagramCmd;
import fr.lecabellec.eniworkshop.eni.model.DistributedClocksConfig;
import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.model.EniMaster;
import fr.lecabellec.eniworkshop.eni.model.EniSlave;
import fr.lecabellec.eniworkshop.eni.model.EsmTransition;
import fr.lecabellec.eniworkshop.eni.model.EthercatConstants;
import fr.lecabellec.eniworkshop.eni.model.HotConnectGroup;
import fr.lecabellec.eniworkshop.eni.model.InitCmd;
import fr.lecabellec.eniworkshop.eni.model.InitCmdPayload;
import fr.lecabellec.eniworkshop.eni.model.InitCmdTiming;
import fr.lecabellec.eniworkshop.eni.model.MailboxConfig;
import fr.lecabellec.eniworkshop.eni.model.MailboxProtocol;
import fr.lecabellec.eniworkshop.eni.model.MailboxRegion;
import fr.lecabellec.eniworkshop.eni.model.MailboxStatesConfig;
import fr.lecabellec.eniworkshop.eni.model.MasterEoeConfig;
import fr.lecabellec.eniworkshop.eni.model.MasterRedundancyConfig;
import fr.lecabellec.eniworkshop.eni.model.PdoEntry;
import fr.lecabellec.eniworkshop.eni.model.PdoFlags;
import fr.lecabellec.eniworkshop.eni.model.PdoMapping;
import fr.lecabellec.eniworkshop.eni.model.PreviousPort;
import fr.lecabellec.eniworkshop.eni.model.ProcessDataLayout;
import fr.lecabellec.eniworkshop.eni.model.ProcessDataRegion;
import fr.lecabellec.eniworkshop.eni.model.ProcessImage;
import fr.lecabellec.eniworkshop.eni.model.ProcessImageVariable;
import fr.lecabellec.eniworkshop.eni.model.SlaveIdentity;
import fr.lecabellec.eniworkshop.eni.model.SyncManagerConfig;
import fr.lecabellec.eniworkshop.eni.model.ValidateClause;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import org.springframework.stereotype.Service;

/**
 * Streaming XXE-hardened StAX parser for ETG.2100 ENI XML and gzipped ENI files.
 *
 * <p>Handles large production configurations (&gt;50 MB, 350+ slaves) cleanly with bounded
 * StAX event streaming and strict null/range validation.
 *
 * <p>Requirement: REQ-00002, Feature: FR-00002, Constraint: CS-0030.4, CS-0030.9.
 */
@Service
public final class EniXmlParser {

    /** Hexadecimal radix constant. */
    private static final int HEX_RADIX = 16;

    /** GZIP magic header byte 0. */
    private static final int GZIP_MAGIC_0 = 0x1F;

    /** GZIP magic header byte 1. */
    private static final int GZIP_MAGIC_1 = 0x8B;

    /** Byte bitmask for unsigned conversion. */
    private static final int BYTE_MASK = 0xFF;

    /** Lookahead buffer size for GZIP magic detection. */
    private static final int MAGIC_PEEK_BYTES = 4;

    /** Configured XXE-hardened StAX input factory. */
    private final XMLInputFactory xmlInputFactory;

    /**
     * Constructs a new {@link EniXmlParser} with XXE protections enabled.
     */
    public EniXmlParser() {
        this.xmlInputFactory = XMLInputFactory.newInstance();
        this.xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        this.xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        this.xmlInputFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        this.xmlInputFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    }

    /**
     * Parses an ENI file (raw {@code .xml} or gzipped {@code .xml.gz}) from disk.
     *
     * @param filePath path to the ENI file
     * @return parsed {@link EniDocument}
     * @throws EniWorkshopException if reading or XML parsing fails
     */
    public EniDocument parse(final Path filePath) {
        Objects.requireNonNull(filePath, "ENI filePath must not be null");
        try (InputStream rawStream = Files.newInputStream(filePath)) {
            return parse(rawStream);
        } catch (final IOException ex) {
            throw new EniWorkshopException("Failed to open ENI file: " + filePath, ex);
        }
    }

    /**
     * Parses an ENI document from raw UTF-8/ISO-8859-1 XML bytes.
     *
     * @param xmlBytes raw or gzipped XML bytes
     * @return parsed {@link EniDocument}
     * @throws EniWorkshopException if bytes exceed limits or XML is invalid
     */
    public EniDocument parse(final byte[] xmlBytes) {
        Objects.requireNonNull(xmlBytes, "ENI xmlBytes must not be null");
        if (xmlBytes.length > EthercatConstants.MAX_XML_BYTES) {
            throw new EniWorkshopException("ENI payload exceeds MAX_XML_BYTES: " + xmlBytes.length);
        }
        try (InputStream stream = new ByteArrayInputStream(xmlBytes)) {
            return parse(stream);
        } catch (final IOException ex) {
            throw new EniWorkshopException("Failed to read ENI byte array", ex);
        }
    }

    /**
     * Parses an ENI document from an input stream, transparently decompressing GZIP streams.
     *
     * @param inputStream non-null input stream
     * @return parsed {@link EniDocument}
     * @throws EniWorkshopException if XML structure is invalid or not an EtherCATConfig document
     */
    public EniDocument parse(final InputStream inputStream) {
        Objects.requireNonNull(inputStream, "ENI inputStream must not be null");
        try (BufferedInputStream buffered = new BufferedInputStream(inputStream);
                InputStream effectiveStream = wrapIfGzip(buffered)) {
            final XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(effectiveStream);
            try {
                return readRootDocument(reader);
            } finally {
                reader.close();
            }
        } catch (final XMLStreamException | IOException ex) {
            throw new EniWorkshopException("Failed to parse ENI XML stream: " + ex.getMessage(), ex);
        }
    }

    /**
     * Detects GZIP magic bytes and wraps the stream when compressed.
     *
     * @param buffered buffered input stream supporting mark/reset
     * @return uncompressed or GZIP-wrapped stream
     * @throws IOException on stream read error
     */
    private InputStream wrapIfGzip(final BufferedInputStream buffered) throws IOException {
        buffered.mark(MAGIC_PEEK_BYTES);
        final int b0 = buffered.read();
        final int b1 = buffered.read();
        buffered.reset();
        if ((b0 & BYTE_MASK) == GZIP_MAGIC_0 && (b1 & BYTE_MASK) == GZIP_MAGIC_1) {
            return new GZIPInputStream(buffered);
        }
        return buffered;
    }

    /**
     * Reads the top-level {@code <EtherCATConfig>} element and its {@code <Config>} children.
     *
     * @param reader active StAX reader
     * @return parsed {@link EniDocument}
     * @throws XMLStreamException on XML syntax or unexpected EOF errors
     */
    private EniDocument readRootDocument(final XMLStreamReader reader) throws XMLStreamException {
        String version = "1.3";
        boolean foundRoot = false;
        EniMaster master = defaultMaster();
        final List<EniSlave> slaves = new ArrayList<>();
        CyclicConfig cyclic = CyclicConfig.empty();
        ProcessImage processImage = ProcessImage.empty();

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                if ("EtherCATConfig".equals(tag)) {
                    foundRoot = true;
                    final String attrVersion = reader.getAttributeValue(null, "Version");
                    if (attrVersion != null && !attrVersion.isBlank()) {
                        version = attrVersion.trim();
                    }
                } else if ("Master".equals(tag)) {
                    master = parseMaster(reader);
                } else if ("Slave".equals(tag)) {
                    if (slaves.size() >= EthercatConstants.MAX_ENI_SLAVES) {
                        throw new EniWorkshopException("ENI exceeds MAX_ENI_SLAVES: " + EthercatConstants.MAX_ENI_SLAVES);
                    }
                    slaves.add(parseSlave(reader));
                } else if ("Cyclic".equals(tag)) {
                    cyclic = parseCyclic(reader);
                } else if ("ProcessImage".equals(tag)) {
                    processImage = parseProcessImage(reader);
                }
            }
        }
        if (!foundRoot) {
            throw new EniWorkshopException("Invalid ENI XML: missing <EtherCATConfig> root element");
        }
        return new EniDocument(version, master, slaves, cyclic, processImage);
    }

    /**
     * Parses {@code <Master>} block.
     *
     * @param reader StAX reader positioned at Master start element
     * @return parsed {@link EniMaster}
     * @throws XMLStreamException on malformed XML
     */
    private EniMaster parseMaster(final XMLStreamReader reader) throws XMLStreamException {
        String name = "EtherCAT Master";
        String dstMac = "ffffffffffff";
        String srcMac = "000000000000";
        int etherType = EthercatConstants.ETHERCAT_ETHERTYPE;
        long mbxStart = 0L;
        int mbxCount = 0;
        int eoePorts = 0;
        int eoeFrames = 0;
        int eoeMacs = 0;
        boolean redEnabled = false;
        String redMac = "";
        final List<InitCmd> initCmds = new ArrayList<>();

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "Master".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                switch (tag) {
                    case "Info" -> {
                        final String[] infoValues = parseMasterInfo(reader);
                        final int ethertypeIndex = 3;
                        name = infoValues[0];
                        dstMac = infoValues[1];
                        srcMac = infoValues[2];
                        etherType = parseHexOrDecimalInt(
                            infoValues[ethertypeIndex], EthercatConstants.ETHERCAT_ETHERTYPE, true);
                    }
                    case "MailboxStates" -> {
                        final long[] mbx = parseMailboxStates(reader);
                        mbxStart = mbx[0];
                        mbxCount = (int) mbx[1];
                    }
                    case "EoE" -> {
                        final int[] eoe = parseMasterEoe(reader);
                        eoePorts = eoe[0];
                        eoeFrames = eoe[1];
                        eoeMacs = eoe[2];
                    }
                    case "Redundancy" -> {
                        final String[] red = parseMasterRedundancy(reader);
                        redEnabled = parseBooleanFlag(red[0]);
                        redMac = red[1];
                    }
                    case "InitCmds" -> initCmds.addAll(parseInitCmdList(reader, "InitCmds"));
                    default -> skipElement(reader, tag);
                }
            }
        }
        return new EniMaster(
                name,
                dstMac,
                srcMac,
                etherType,
                new MailboxStatesConfig(mbxStart, mbxCount),
                new MasterEoeConfig(eoePorts, eoeFrames, eoeMacs),
                new MasterRedundancyConfig(redEnabled, redMac),
                initCmds);
    }

    /**
     * Parses Master/Info element into a 4-element array [name, dst, src, etherType].
     *
     * @param reader StAX reader positioned at Info
     * @return 4-element string array
     * @throws XMLStreamException on XML error
     */
    private String[] parseMasterInfo(final XMLStreamReader reader) throws XMLStreamException {
        final String[] result = new String[] {"EtherCAT Master", "ffffffffffff", "000000000000", "a488"};
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "Info".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                final int ethertypeIndex = 3;
                switch (tag) {
                    case "Name" -> result[0] = readElementText(reader);
                    case "Destination" -> result[1] = readElementText(reader);
                    case "Source" -> result[2] = readElementText(reader);
                    case "EtherType" -> result[ethertypeIndex] = readElementText(reader);
                    default -> skipElement(reader, tag);
                }
            }
        }
        return result;
    }

    /**
     * Parses Master/MailboxStates into [startAddr, count].
     *
     * @param reader StAX reader positioned at MailboxStates
     * @return 2-element long array
     * @throws XMLStreamException on XML error
     */
    private long[] parseMailboxStates(final XMLStreamReader reader) throws XMLStreamException {
        final long[] result = new long[] {0L, 0L};
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "MailboxStates".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                if ("StartAddr".equals(tag)) {
                    result[0] = parseLongFlexible(readElementText(reader), 0L);
                } else if ("Count".equals(tag)) {
                    result[1] = parseLongFlexible(readElementText(reader), 0L);
                } else {
                    skipElement(reader, tag);
                }
            }
        }
        return result;
    }

    /**
     * Parses Master/EoE into [maxPorts, maxFrames, maxMacs].
     *
     * @param reader StAX reader positioned at EoE
     * @return 3-element integer array
     * @throws XMLStreamException on XML error
     */
    private int[] parseMasterEoe(final XMLStreamReader reader) throws XMLStreamException {
        final int[] result = new int[] {0, 0, 0};
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "EoE".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                switch (tag) {
                    case "MaxPorts" -> result[0] = parseIntFlexible(readElementText(reader), 0);
                    case "MaxFrames" -> result[1] = parseIntFlexible(readElementText(reader), 0);
                    case "MaxMACs" -> result[2] = parseIntFlexible(readElementText(reader), 0);
                    default -> skipElement(reader, tag);
                }
            }
        }
        return result;
    }

    /**
     * Parses optional Master/Redundancy block into [enabled, secondarySourceMac].
     *
     * @param reader StAX reader positioned at Redundancy
     * @return 2-element string array
     * @throws XMLStreamException on XML error
     */
    private String[] parseMasterRedundancy(final XMLStreamReader reader) throws XMLStreamException {
        final String[] result = new String[] {"1", ""};
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "Redundancy".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                if ("Enable".equals(tag)) {
                    result[0] = readElementText(reader);
                } else if ("Source".equals(tag) || "SecondarySource".equals(tag)) {
                    result[1] = readElementText(reader);
                } else {
                    skipElement(reader, tag);
                }
            }
        }
        return result;
    }

    /**
     * Parses a {@code <Slave>} element and all nested sub-structures.
     *
     * @param reader StAX reader positioned at Slave
     * @return parsed {@link EniSlave}
     * @throws XMLStreamException on XML error
     */
    private EniSlave parseSlave(final XMLStreamReader reader) throws XMLStreamException {
        SlaveIdentity identity = new SlaveIdentity("Slave", 0, 0, "", 0L, 0L, 0L, 0L);
        Optional<PreviousPort> previousPort = Optional.empty();
        Optional<HotConnectGroup> hotConnect = Optional.empty();
        ProcessDataLayout processData = ProcessDataLayout.empty();
        final List<InitCmd> initCmds = new ArrayList<>();
        MailboxConfig mailbox = MailboxConfig.empty();
        DistributedClocksConfig dc = DistributedClocksConfig.disabled();

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "Slave".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                switch (tag) {
                    case "Info" -> identity = parseSlaveInfo(reader);
                    case "PreviousPort" -> previousPort = Optional.of(parsePreviousPort(reader));
                    case "HotConnect" -> hotConnect = Optional.of(parseHotConnect(reader));
                    case "ProcessData" -> processData = parseProcessData(reader);
                    case "InitCmds" -> initCmds.addAll(parseInitCmdList(reader, "InitCmds"));
                    case "Mailbox" -> mailbox = parseMailbox(reader);
                    case "DC" -> dc = parseDc(reader);
                    default -> skipElement(reader, tag);
                }
            }
        }
        return new EniSlave(identity, previousPort, hotConnect, processData, initCmds, mailbox, dc);
    }

    /**
     * Parses {@code Slave/Info} element.
     *
     * @param reader StAX reader positioned at Info
     * @return parsed {@link SlaveIdentity}
     * @throws XMLStreamException on XML error
     */
    private SlaveIdentity parseSlaveInfo(final XMLStreamReader reader) throws XMLStreamException {
        String name = "Slave";
        int physAddr = 0;
        int autoIncAddr = 0;
        String physics = "";
        long vendorId = 0L;
        long productCode = 0L;
        long revisionNo = 0L;
        long serialNo = 0L;

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "Info".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                switch (tag) {
                    case "Name" -> name = readElementText(reader);
                    case "PhysAddr" -> physAddr = parseIntFlexible(readElementText(reader), 0);
                    case "AutoIncAddr" -> autoIncAddr = parseIntFlexible(readElementText(reader), 0);
                    case "Physics" -> physics = readElementText(reader);
                    case "VendorId" -> vendorId = parseLongFlexible(readElementText(reader), 0L);
                    case "ProductCode" -> productCode = parseLongFlexible(readElementText(reader), 0L);
                    case "RevisionNo" -> revisionNo = parseLongFlexible(readElementText(reader), 0L);
                    case "SerialNo" -> serialNo = parseLongFlexible(readElementText(reader), 0L);
                    default -> skipElement(reader, tag);
                }
            }
        }
        return new SlaveIdentity(name, physAddr, autoIncAddr, physics, vendorId, productCode, revisionNo, serialNo);
    }

    /**
     * Parses {@code Slave/PreviousPort} element.
     *
     * @param reader StAX reader positioned at PreviousPort
     * @return parsed {@link PreviousPort}
     * @throws XMLStreamException on XML error
     */
    private PreviousPort parsePreviousPort(final XMLStreamReader reader) throws XMLStreamException {
        final String selAttr = reader.getAttributeValue(null, "Selected");
        final boolean selected = selAttr == null || parseBooleanFlag(selAttr);
        int physAddr = 0;
        char port = 'A';

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "PreviousPort".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                if ("PhysAddr".equals(tag)) {
                    physAddr = parseIntFlexible(readElementText(reader), 0);
                } else if ("Port".equals(tag)) {
                    final String text = readElementText(reader).trim();
                    if (!text.isEmpty()) {
                        port = text.charAt(0);
                    }
                } else {
                    skipElement(reader, tag);
                }
            }
        }
        return new PreviousPort(physAddr, port, selected);
    }

    /**
     * Parses {@code Slave/HotConnect} element.
     *
     * @param reader StAX reader positioned at HotConnect
     * @return parsed {@link HotConnectGroup}
     * @throws XMLStreamException on XML error
     */
    private HotConnectGroup parseHotConnect(final XMLStreamReader reader) throws XMLStreamException {
        String groupName = "Group";
        int memberCnt = 1;
        final List<InitCmd> identifyCmds = new ArrayList<>();

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "HotConnect".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                switch (tag) {
                    case "GroupName" -> groupName = readElementText(reader);
                    case "GroupMemberCnt" -> memberCnt = Math.max(1, parseIntFlexible(readElementText(reader), 1));
                    case "IdentifyCmd" -> identifyCmds.add(parseInitCmd(reader, "IdentifyCmd"));
                    default -> skipElement(reader, tag);
                }
            }
        }
        return new HotConnectGroup(groupName, memberCnt, identifyCmds);
    }

    /**
     * Parses {@code Slave/ProcessData} block (Send, Recv, Sm0..Sm15, RxPdo, TxPdo).
     *
     * @param reader StAX reader positioned at ProcessData
     * @return parsed {@link ProcessDataLayout}
     * @throws XMLStreamException on XML error
     */
    private ProcessDataLayout parseProcessData(final XMLStreamReader reader) throws XMLStreamException {
        Optional<ProcessDataRegion> sendRegion = Optional.empty();
        Optional<ProcessDataRegion> recvRegion = Optional.empty();
        final List<SyncManagerConfig> syncManagers = new ArrayList<>();
        final List<PdoMapping> rxPdos = new ArrayList<>();
        final List<PdoMapping> txPdos = new ArrayList<>();

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "ProcessData".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                if ("Send".equals(tag)) {
                    sendRegion = Optional.of(parseProcessDataRegion(reader, "Send"));
                } else if ("Recv".equals(tag)) {
                    recvRegion = Optional.of(parseProcessDataRegion(reader, "Recv"));
                } else if (tag.startsWith("Sm") && tag.length() > 2 && Character.isDigit(tag.charAt(2))) {
                    final int smIndex = Integer.parseInt(tag.substring(2));
                    syncManagers.add(parseSyncManager(reader, tag, smIndex));
                } else if ("RxPdo".equals(tag)) {
                    rxPdos.add(parsePdoMapping(reader, "RxPdo", false));
                } else if ("TxPdo".equals(tag)) {
                    txPdos.add(parsePdoMapping(reader, "TxPdo", true));
                } else {
                    skipElement(reader, tag);
                }
            }
        }
        return new ProcessDataLayout(sendRegion, recvRegion, syncManagers, rxPdos, txPdos);
    }

    /**
     * Parses Send or Recv bit region.
     *
     * @param reader  StAX reader
     * @param endTag  closing element tag ("Send" or "Recv")
     * @return parsed {@link ProcessDataRegion}
     * @throws XMLStreamException on XML error
     */
    private ProcessDataRegion parseProcessDataRegion(final XMLStreamReader reader, final String endTag)
            throws XMLStreamException {
        int bitStart = 0;
        int bitLength = 0;
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && endTag.equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                if ("BitStart".equals(tag)) {
                    bitStart = parseIntFlexible(readElementText(reader), 0);
                } else if ("BitLength".equals(tag)) {
                    bitLength = parseIntFlexible(readElementText(reader), 0);
                } else {
                    skipElement(reader, tag);
                }
            }
        }
        return new ProcessDataRegion(bitStart, bitLength);
    }

    /**
     * Parses a SyncManager element (e.g., Sm0..Sm3).
     *
     * @param reader  StAX reader
     * @param endTag  element tag name
     * @param smIndex numeric SyncManager index
     * @return parsed {@link SyncManagerConfig}
     * @throws XMLStreamException on XML error
     */
    private SyncManagerConfig parseSyncManager(
            final XMLStreamReader reader,
            final String endTag,
            final int smIndex) throws XMLStreamException {
        String type = "";
        int startAddress = 0;
        int defaultSize = 0;
        int controlByte = 0;
        boolean enable = false;
        final List<Integer> pdoIndices = new ArrayList<>();

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && endTag.equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                switch (tag) {
                    case "Type" -> type = readElementText(reader);
                    case "StartAddress" -> startAddress = parseIntFlexible(readElementText(reader), 0);
                    case "DefaultSize" -> defaultSize = parseIntFlexible(readElementText(reader), 0);
                    case "ControlByte" -> controlByte = parseIntFlexible(readElementText(reader), 0);
                    case "Enable" -> enable = parseBooleanFlag(readElementText(reader));
                    case "Pdo" -> pdoIndices.add(parseIntFlexible(readElementText(reader), 0));
                    default -> skipElement(reader, tag);
                }
            }
        }
        return new SyncManagerConfig(smIndex, type, startAddress, defaultSize, controlByte, enable, pdoIndices);
    }

    /**
     * Parses an RxPdo or TxPdo mapping element.
     *
     * @param reader StAX reader
     * @param endTag closing tag ("RxPdo" or "TxPdo")
     * @param isTx   true for TxPdo, false for RxPdo
     * @return parsed {@link PdoMapping}
     * @throws XMLStreamException on XML error
     */
    private PdoMapping parsePdoMapping(
            final XMLStreamReader reader,
            final String endTag,
            final boolean isTx) throws XMLStreamException {
        final int sm = parseIntFlexible(reader.getAttributeValue(null, "Sm"), -1);
        final int su = parseIntFlexible(reader.getAttributeValue(null, "Su"), -1);
        final boolean fixed = parseBooleanFlag(reader.getAttributeValue(null, "Fixed"));
        final boolean mandatory = parseBooleanFlag(reader.getAttributeValue(null, "Mandatory"));
        final boolean virtual = parseBooleanFlag(reader.getAttributeValue(null, "Virtual"));
        final PdoFlags flags = new PdoFlags(sm, su, fixed, mandatory, virtual, isTx);

        int index = 0;
        String name = "";
        final List<Integer> excludes = new ArrayList<>();
        final List<PdoEntry> entries = new ArrayList<>();

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && endTag.equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                switch (tag) {
                    case "Index" -> index = parseIntFlexible(readElementText(reader), 0);
                    case "Name" -> name = readElementText(reader);
                    case "Exclude" -> excludes.add(parseIntFlexible(readElementText(reader), 0));
                    case "Entry" -> entries.add(parsePdoEntry(reader));
                    default -> skipElement(reader, tag);
                }
            }
        }
        return new PdoMapping(index, name, flags, excludes, entries);
    }

    /**
     * Parses a single {@code <Entry>} inside a PDO.
     *
     * @param reader StAX reader positioned at Entry
     * @return parsed {@link PdoEntry}
     * @throws XMLStreamException on XML error
     */
    private PdoEntry parsePdoEntry(final XMLStreamReader reader) throws XMLStreamException {
        int index = 0;
        int subIndex = 0;
        int bitLen = 0;
        String name = "";
        String comment = "";
        String dataType = "";
        String dscale = "";

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "Entry".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                switch (tag) {
                    case "Index" -> index = parseIntFlexible(readElementText(reader), 0);
                    case "SubIndex" -> subIndex = parseIntFlexible(readElementText(reader), 0);
                    case "BitLen" -> bitLen = parseIntFlexible(readElementText(reader), 0);
                    case "Name" -> name = readElementText(reader);
                    case "Comment" -> comment = readElementText(reader);
                    case "DataType" -> {
                        final String ds = reader.getAttributeValue(null, "DScale");
                        if (ds != null) {
                            dscale = ds;
                        }
                        dataType = readElementText(reader);
                    }
                    default -> skipElement(reader, tag);
                }
            }
        }
        return new PdoEntry(index, subIndex, bitLen, name, comment, dataType, dscale);
    }

    /**
     * Parses {@code Slave/Mailbox} block.
     *
     * @param reader StAX reader positioned at Mailbox
     * @return parsed {@link MailboxConfig}
     * @throws XMLStreamException on XML error
     */
    private MailboxConfig parseMailbox(final XMLStreamReader reader) throws XMLStreamException {
        final boolean dataLinkLayer = parseBooleanFlag(reader.getAttributeValue(null, "DataLinkLayer"));
        int sendStart = 0;
        int sendLen = 0;
        int recvStart = 0;
        int recvLen = 0;
        int statusBitAddr = 0;
        Optional<MailboxRegion> bootstrap = Optional.empty();
        String aoeNetId = "";
        final List<MailboxProtocol> protocols = new ArrayList<>();
        final List<CoeChannelInfo> coeChannels = new ArrayList<>();
        final List<CoeInitCmd> coeInitCmds = new ArrayList<>();

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "Mailbox".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                switch (tag) {
                    case "Send" -> {
                        final int[] send = parseMailboxStartLen(reader, "Send");
                        sendStart = send[0];
                        sendLen = send[1];
                    }
                    case "Recv" -> {
                        final int[] recv = parseMailboxStartLen(reader, "Recv");
                        recvStart = recv[0];
                        recvLen = recv[1];
                        statusBitAddr = recv[2];
                    }
                    case "BootStrap" -> bootstrap = Optional.of(parseBootstrapMailbox(reader));
                    case "Protocol" -> {
                        final String p = readElementText(reader).trim();
                        if (!p.isEmpty()) {
                            try {
                                protocols.add(MailboxProtocol.parse(p));
                            } catch (final IllegalArgumentException ignored) {
                                // Ignore unrecognized vendor protocol strings gracefully.
                            }
                        }
                    }
                    case "AoE" -> aoeNetId = parseAoeNetId(reader);
                    case "CoE" -> parseCoeBlock(reader, coeChannels, coeInitCmds);
                    default -> skipElement(reader, tag);
                }
            }
        }
        final MailboxRegion standard = new MailboxRegion(sendStart, sendLen, recvStart, recvLen, statusBitAddr);
        return new MailboxConfig(dataLinkLayer, standard, bootstrap, aoeNetId, protocols, coeChannels, coeInitCmds);
    }

    /**
     * Parses Mailbox Send/Recv Start, Length, and StatusBitAddr.
     *
     * @param reader StAX reader
     * @param endTag closing element tag
     * @return [start, length, statusBitAddr]
     * @throws XMLStreamException on XML error
     */
    private int[] parseMailboxStartLen(final XMLStreamReader reader, final String endTag) throws XMLStreamException {
        final int[] result = new int[] {0, 0, 0};
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && endTag.equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                switch (tag) {
                    case "Start" -> result[0] = parseIntFlexible(readElementText(reader), 0);
                    case "Length" -> result[1] = parseIntFlexible(readElementText(reader), 0);
                    case "StatusBitAddr" -> result[2] = parseIntFlexible(readElementText(reader), 0);
                    default -> skipElement(reader, tag);
                }
            }
        }
        return result;
    }

    /**
     * Parses {@code Mailbox/BootStrap} block.
     *
     * @param reader StAX reader positioned at BootStrap
     * @return parsed bootstrap {@link MailboxRegion}
     * @throws XMLStreamException on XML error
     */
    private MailboxRegion parseBootstrapMailbox(final XMLStreamReader reader) throws XMLStreamException {
        int sendStart = 0;
        int sendLen = 0;
        int recvStart = 0;
        int recvLen = 0;
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "BootStrap".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                if ("Send".equals(tag)) {
                    final int[] s = parseMailboxStartLen(reader, "Send");
                    sendStart = s[0];
                    sendLen = s[1];
                } else if ("Recv".equals(tag)) {
                    final int[] r = parseMailboxStartLen(reader, "Recv");
                    recvStart = r[0];
                    recvLen = r[1];
                } else {
                    skipElement(reader, tag);
                }
            }
        }
        return new MailboxRegion(sendStart, sendLen, recvStart, recvLen, 0);
    }

    /**
     * Parses {@code Mailbox/AoE/NetId}.
     *
     * @param reader StAX reader positioned at AoE
     * @return AoE NetId string
     * @throws XMLStreamException on XML error
     */
    private String parseAoeNetId(final XMLStreamReader reader) throws XMLStreamException {
        String netId = "";
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "AoE".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                if ("NetId".equals(tag)) {
                    netId = readElementText(reader);
                } else {
                    skipElement(reader, tag);
                }
            }
        }
        return netId;
    }

    /**
     * Parses {@code Mailbox/CoE} profile channels and SDO InitCmds.
     *
     * @param reader      StAX reader positioned at CoE
     * @param coeChannels output list of channel profiles
     * @param coeInitCmds output list of CoE InitCmds
     * @throws XMLStreamException on XML error
     */
    private void parseCoeBlock(
            final XMLStreamReader reader,
            final List<CoeChannelInfo> coeChannels,
            final List<CoeInitCmd> coeInitCmds) throws XMLStreamException {
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "CoE".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                if ("ChannelInfo".equals(tag)) {
                    coeChannels.add(parseCoeChannelInfo(reader));
                } else if ("InitCmd".equals(tag)) {
                    coeInitCmds.add(parseCoeInitCmd(reader));
                } else if (!"Profile".equals(tag) && !"InitCmds".equals(tag)) {
                    skipElement(reader, tag);
                }
            }
        }
    }

    /**
     * Parses a single {@code ChannelInfo} entry.
     *
     * @param reader StAX reader positioned at ChannelInfo
     * @return parsed {@link CoeChannelInfo}
     * @throws XMLStreamException on XML error
     */
    private CoeChannelInfo parseCoeChannelInfo(final XMLStreamReader reader) throws XMLStreamException {
        int profileNo = 0;
        int addInfo = 0;
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "ChannelInfo".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                if ("ProfileNo".equals(tag)) {
                    profileNo = parseIntFlexible(readElementText(reader), 0);
                } else if ("AddInfo".equals(tag)) {
                    addInfo = parseIntFlexible(readElementText(reader), 0);
                } else {
                    skipElement(reader, tag);
                }
            }
        }
        return new CoeChannelInfo(profileNo, addInfo);
    }

    /**
     * Parses a CoE SDO {@code InitCmd}.
     *
     * @param reader StAX reader positioned at InitCmd
     * @return parsed {@link CoeInitCmd}
     * @throws XMLStreamException on XML error
     */
    private CoeInitCmd parseCoeInitCmd(final XMLStreamReader reader) throws XMLStreamException {
        final boolean completeAccess = parseBooleanFlag(reader.getAttributeValue(null, "CompleteAccess"));
        final boolean fixed = parseBooleanFlag(reader.getAttributeValue(null, "Fixed"));
        final List<EsmTransition> transitions = new ArrayList<>();
        String comment = "";
        int index = 0;
        int subIndex = 0;
        int ccs = 1;
        int timeoutMs = 0;
        String dataHex = "";

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "InitCmd".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                switch (tag) {
                    case "Transition" -> addTransitionSafe(transitions, readElementText(reader));
                    case "Comment" -> comment = readElementText(reader);
                    case "Index" -> index = parseIntFlexible(readElementText(reader), 0);
                    case "SubIndex" -> subIndex = parseIntFlexible(readElementText(reader), 0);
                    case "Ccs" -> ccs = parseIntFlexible(readElementText(reader), 1);
                    case "Timeout" -> timeoutMs = Math.max(0, parseIntFlexible(readElementText(reader), 0));
                    case "Data" -> dataHex = readElementText(reader).trim();
                    default -> skipElement(reader, tag);
                }
            }
        }
        return new CoeInitCmd(transitions, comment, index, subIndex, ccs, completeAccess, fixed, timeoutMs, dataHex);
    }

    /**
     * Parses {@code Slave/DC} configuration.
     *
     * @param reader StAX reader positioned at DC
     * @return parsed {@link DistributedClocksConfig}
     * @throws XMLStreamException on XML error
     */
    private DistributedClocksConfig parseDc(final XMLStreamReader reader) throws XMLStreamException {
        boolean potentialRef = false;
        boolean refClock = false;
        long cycle0 = 0L;
        long cycle1 = 0L;
        long shift0 = 0L;
        long shift1 = 0L;
        boolean enabled = false;

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "DC".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                switch (tag) {
                    case "PotentialReferenceClock" -> potentialRef = parseBooleanFlag(readElementText(reader));
                    case "ReferenceClock" -> {
                        refClock = parseBooleanFlag(readElementText(reader));
                        enabled = enabled || refClock;
                    }
                    case "CycleTime0" -> {
                        cycle0 = parseLongFlexible(readElementText(reader), 0L);
                        enabled = enabled || cycle0 > 0L;
                    }
                    case "CycleTime1" -> cycle1 = parseLongFlexible(readElementText(reader), 0L);
                    case "ShiftTime0" -> shift0 = parseLongFlexible(readElementText(reader), 0L);
                    case "ShiftTime1" -> shift1 = parseLongFlexible(readElementText(reader), 0L);
                    default -> skipElement(reader, tag);
                }
            }
        }
        return new DistributedClocksConfig(enabled, potentialRef, refClock, cycle0, cycle1, shift0, shift1);
    }

    /**
     * Parses a container of {@code <InitCmd>} elements.
     *
     * @param reader StAX reader
     * @param endTag closing container tag
     * @return list of parsed {@link InitCmd}
     * @throws XMLStreamException on XML error
     */
    private List<InitCmd> parseInitCmdList(final XMLStreamReader reader, final String endTag)
            throws XMLStreamException {
        final List<InitCmd> list = new ArrayList<>();
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && endTag.equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                if ("InitCmd".equals(tag)) {
                    list.add(parseInitCmd(reader, "InitCmd"));
                } else {
                    skipElement(reader, tag);
                }
            }
        }
        return list;
    }

    /**
     * Parses a single {@code <InitCmd>} or {@code <IdentifyCmd>}.
     *
     * @param reader StAX reader
     * @param endTag closing tag name
     * @return parsed {@link InitCmd}
     * @throws XMLStreamException on XML error
     */
    private InitCmd parseInitCmd(final XMLStreamReader reader, final String endTag) throws XMLStreamException {
        final List<EsmTransition> transitions = new ArrayList<>();
        boolean beforeSlave = false;
        String comment = "";
        String requires = "";
        DatagramCmd cmd = DatagramCmd.NOP;
        int adp = 0;
        int ado = 0;
        long addr = 0L;
        String dataHex = "";
        int dataLength = 0;
        int cnt = 0;
        int retries = 0;
        int timeoutMs = 0;
        Optional<ValidateClause> validate = Optional.empty();

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && endTag.equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                switch (tag) {
                    case "Transition" -> addTransitionSafe(transitions, readElementText(reader));
                    case "BeforeSlave" -> beforeSlave = parseBooleanFlag(readElementText(reader));
                    case "Comment" -> comment = readElementText(reader);
                    case "Requires" -> requires = readElementText(reader);
                    case "Cmd" -> cmd = DatagramCmd.fromCode(parseIntFlexible(readElementText(reader), 0));
                    case "Adp" -> adp = parseIntFlexible(readElementText(reader), 0) & EthercatConstants.MAX_UINT16;
                    case "Ado" -> ado = parseIntFlexible(readElementText(reader), 0) & EthercatConstants.MAX_UINT16;
                    case "Addr" -> addr = Math.max(0L, parseLongFlexible(readElementText(reader), 0L));
                    case "Data" -> dataHex = readElementText(reader).trim();
                    case "DataLength" -> dataLength = Math.max(0, parseIntFlexible(readElementText(reader), 0));
                    case "Cnt" -> cnt = Math.max(0, parseIntFlexible(readElementText(reader), 0));
                    case "Retries" -> retries = Math.max(0, parseIntFlexible(readElementText(reader), 0));
                    case "Timeout" -> timeoutMs = Math.max(0, parseIntFlexible(readElementText(reader), 0));
                    case "Validate" -> validate = Optional.of(parseValidateClause(reader));
                    default -> skipElement(reader, tag);
                }
            }
        }
        return new InitCmd(
                transitions,
                beforeSlave,
                comment,
                cmd,
                new DatagramAddress(addr, adp, ado),
                new InitCmdPayload(dataHex, dataLength, cnt),
                new InitCmdTiming(requires, retries, timeoutMs),
                validate);
    }

    /**
     * Parses {@code Validate} block inside InitCmd or IdentifyCmd.
     *
     * @param reader StAX reader positioned at Validate
     * @return parsed {@link ValidateClause}
     * @throws XMLStreamException on XML error
     */
    private ValidateClause parseValidateClause(final XMLStreamReader reader) throws XMLStreamException {
        final boolean signed = parseBooleanFlag(reader.getAttributeValue(null, "Signed"));
        final String attrType = reader.getAttributeValue(null, "Type");
        final String type = attrType != null && !attrType.isBlank() ? attrType : "EQ";
        String dataHex = "";
        String dataMaskHex = "";
        int timeoutMs = 0;

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "Validate".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                switch (tag) {
                    case "Data" -> dataHex = readElementText(reader).trim();
                    case "DataMask" -> dataMaskHex = readElementText(reader).trim();
                    case "Timeout" -> timeoutMs = Math.max(0, parseIntFlexible(readElementText(reader), 0));
                    default -> skipElement(reader, tag);
                }
            }
        }
        return new ValidateClause(signed, type, dataHex, dataMaskHex, timeoutMs);
    }

    /**
     * Parses {@code Config/Cyclic} schedule and frames.
     *
     * @param reader StAX reader positioned at Cyclic
     * @return parsed {@link CyclicConfig}
     * @throws XMLStreamException on XML error
     */
    private CyclicConfig parseCyclic(final XMLStreamReader reader) throws XMLStreamException {
        int cycleTimeUs = EthercatConstants.DEFAULT_CYCLE_TIME_US;
        final List<CyclicFrame> frames = new ArrayList<>();

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "Cyclic".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                if ("CycleTime".equals(tag)) {
                    cycleTimeUs = Math.max(0, parseIntFlexible(readElementText(reader), EthercatConstants.DEFAULT_CYCLE_TIME_US));
                } else if ("Frame".equals(tag)) {
                    frames.add(parseCyclicFrame(reader));
                } else {
                    skipElement(reader, tag);
                }
            }
        }
        return new CyclicConfig(cycleTimeUs, frames);
    }

    /**
     * Parses a {@code Cyclic/Frame} element.
     *
     * @param reader StAX reader positioned at Frame
     * @return parsed {@link CyclicFrame}
     * @throws XMLStreamException on XML error
     */
    private CyclicFrame parseCyclicFrame(final XMLStreamReader reader) throws XMLStreamException {
        final List<CyclicCommand> commands = new ArrayList<>();
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "Frame".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                if ("Cmd".equals(tag)) {
                    commands.add(parseCyclicCommand(reader));
                } else {
                    skipElement(reader, tag);
                }
            }
        }
        return new CyclicFrame(commands);
    }

    /**
     * Parses a {@code Cyclic/Frame/Cmd} datagram definition.
     *
     * @param reader StAX reader positioned at Cmd
     * @return parsed {@link CyclicCommand}
     * @throws XMLStreamException on XML error
     */
    private CyclicCommand parseCyclicCommand(final XMLStreamReader reader) throws XMLStreamException {
        final List<String> states = new ArrayList<>();
        String comment = "";
        DatagramCmd cmd = DatagramCmd.NOP;
        long addr = 0L;
        int adp = 0;
        int ado = 0;
        int dataLength = 0;
        int cnt = 0;
        int inputOffs = 0;
        int outputOffs = 0;

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "Cmd".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                switch (tag) {
                    case "State" -> states.add(readElementText(reader).trim());
                    case "Comment" -> comment = readElementText(reader);
                    case "Cmd" -> cmd = DatagramCmd.fromCode(parseIntFlexible(readElementText(reader), 0));
                    case "Addr" -> addr = Math.max(0L, parseLongFlexible(readElementText(reader), 0L));
                    case "Adp" -> adp = parseIntFlexible(readElementText(reader), 0) & EthercatConstants.MAX_UINT16;
                    case "Ado" -> ado = parseIntFlexible(readElementText(reader), 0) & EthercatConstants.MAX_UINT16;
                    case "DataLength" -> dataLength = Math.max(0, parseIntFlexible(readElementText(reader), 0));
                    case "Cnt" -> cnt = Math.max(0, parseIntFlexible(readElementText(reader), 0));
                    case "InputOffs" -> inputOffs = Math.max(0, parseIntFlexible(readElementText(reader), 0));
                    case "OutputOffs" -> outputOffs = Math.max(0, parseIntFlexible(readElementText(reader), 0));
                    default -> skipElement(reader, tag);
                }
            }
        }
        return new CyclicCommand(
                states,
                comment,
                cmd,
                new DatagramAddress(addr, adp, ado),
                dataLength,
                cnt,
                inputOffs,
                outputOffs);
    }

    /**
     * Parses {@code Config/ProcessImage} Inputs and Outputs.
     *
     * @param reader StAX reader positioned at ProcessImage
     * @return parsed {@link ProcessImage}
     * @throws XMLStreamException on XML error
     */
    private ProcessImage parseProcessImage(final XMLStreamReader reader) throws XMLStreamException {
        int inputsByteSize = 0;
        int outputsByteSize = 0;
        final List<ProcessImageVariable> inputs = new ArrayList<>();
        final List<ProcessImageVariable> outputs = new ArrayList<>();

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "ProcessImage".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                if ("Inputs".equals(tag)) {
                    inputsByteSize = parseProcessImageSection(reader, "Inputs", true, inputs);
                } else if ("Outputs".equals(tag)) {
                    outputsByteSize = parseProcessImageSection(reader, "Outputs", false, outputs);
                } else {
                    skipElement(reader, tag);
                }
            }
        }
        return new ProcessImage(inputsByteSize, outputsByteSize, inputs, outputs);
    }

    /**
     * Parses either {@code ProcessImage/Inputs} or {@code ProcessImage/Outputs}.
     *
     * @param reader  StAX reader
     * @param endTag  closing section tag
     * @param isInput true for Inputs, false for Outputs
     * @param outVars target variable list
     * @return declared ByteSize
     * @throws XMLStreamException on XML error
     */
    private int parseProcessImageSection(
            final XMLStreamReader reader,
            final String endTag,
            final boolean isInput,
            final List<ProcessImageVariable> outVars) throws XMLStreamException {
        int byteSize = 0;
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && endTag.equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                if ("ByteSize".equals(tag)) {
                    byteSize = Math.max(0, parseIntFlexible(readElementText(reader), 0));
                } else if ("Variable".equals(tag)) {
                    outVars.add(parseProcessImageVariable(reader, isInput));
                } else {
                    skipElement(reader, tag);
                }
            }
        }
        return byteSize;
    }

    /**
     * Parses a single {@code <Variable>} inside ProcessImage.
     *
     * @param reader  StAX reader positioned at Variable
     * @param isInput true for input variable, false for output variable
     * @return parsed {@link ProcessImageVariable}
     * @throws XMLStreamException on XML error
     */
    private ProcessImageVariable parseProcessImageVariable(final XMLStreamReader reader, final boolean isInput)
            throws XMLStreamException {
        String name = "";
        String comment = "";
        String dataType = "";
        int bitSize = 0;
        int bitOffs = 0;

        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "Variable".equals(reader.getLocalName())) {
                break;
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                final String tag = reader.getLocalName();
                switch (tag) {
                    case "Name" -> name = readElementText(reader);
                    case "Comment" -> comment = readElementText(reader);
                    case "DataType" -> dataType = readElementText(reader);
                    case "BitSize" -> bitSize = Math.max(0, parseIntFlexible(readElementText(reader), 0));
                    case "BitOffs" -> bitOffs = Math.max(0, parseIntFlexible(readElementText(reader), 0));
                    default -> skipElement(reader, tag);
                }
            }
        }
        return new ProcessImageVariable(name, comment, dataType, bitSize, bitOffs, isInput);
    }

    /**
     * Reads text/CDATA inside a leaf element up to its matching END_ELEMENT.
     *
     * @param reader StAX reader positioned at leaf start element
     * @return collected text (never null)
     * @throws XMLStreamException on XML error
     */
    private String readElementText(final XMLStreamReader reader) throws XMLStreamException {
        final StringBuilder sb = new StringBuilder();
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                sb.append(reader.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            } else if (event == XMLStreamConstants.START_ELEMENT) {
                skipElement(reader, reader.getLocalName());
            }
        }
        return sb.toString();
    }

    /**
     * Skips an unknown element subtree safely.
     *
     * @param reader  StAX reader positioned at start element
     * @param tagName tag name to skip until matching END_ELEMENT
     * @throws XMLStreamException on XML error
     */
    private void skipElement(final XMLStreamReader reader, final String tagName) throws XMLStreamException {
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            final int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        if (depth != 0) {
            throw new EniWorkshopException("Unexpected end of XML while skipping element: " + tagName);
        }
    }

    /**
     * Adds an {@link EsmTransition} if recognized, ignoring empty values.
     *
     * @param target list to append to
     * @param raw    raw transition mnemonic
     */
    private void addTransitionSafe(final List<EsmTransition> target, final String raw) {
        final String trimmed = raw.trim();
        if (!trimmed.isEmpty()) {
            target.add(EsmTransition.parse(trimmed));
        }
    }

    /**
     * Parses a flexible integer supporting decimal, {@code #x...}, and {@code 0x...} hex.
     *
     * @param raw          raw string (nullable)
     * @param defaultValue fallback when null or blank
     * @return parsed integer
     */
    private int parseIntFlexible(final String raw, final int defaultValue) {
        return parseHexOrDecimalInt(raw, defaultValue, false);
    }

    /**
     * Parses an integer with optional default-hex interpretation (used for EtherType).
     *
     * @param raw          raw string (nullable)
     * @param defaultValue fallback when null or blank
     * @param preferHex    true when unprefixed hex strings like "a488" should parse as hex
     * @return parsed integer
     */
    private int parseHexOrDecimalInt(final String raw, final int defaultValue, final boolean preferHex) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        final String s = raw.trim();
        if (s.startsWith("#x") || s.startsWith("#X")) {
            return Integer.parseUnsignedInt(s.substring(2), HEX_RADIX);
        }
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return Integer.parseUnsignedInt(s.substring(2), HEX_RADIX);
        }
        if (preferHex) {
            return Integer.parseUnsignedInt(s, HEX_RADIX);
        }
        return Integer.parseInt(s);
    }

    /**
     * Parses a flexible long supporting decimal, {@code #x...}, and {@code 0x...} hex.
     *
     * @param raw          raw string (nullable)
     * @param defaultValue fallback when null or blank
     * @return parsed long
     */
    private long parseLongFlexible(final String raw, final long defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        final String s = raw.trim();
        if (s.startsWith("#x") || s.startsWith("#X")) {
            return Long.parseUnsignedLong(s.substring(2), HEX_RADIX);
        }
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return Long.parseUnsignedLong(s.substring(2), HEX_RADIX);
        }
        return Long.parseLong(s);
    }

    /**
     * Parses boolean attribute or element values ("1", "true", "yes").
     *
     * @param raw raw string (nullable)
     * @return true if truthy
     */
    private boolean parseBooleanFlag(final String raw) {
        if (raw == null) {
            return false;
        }
        final String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized);
    }

    /**
     * Builds a minimal default master when XML omits Master details.
     *
     * @return default {@link EniMaster}
     */
    private EniMaster defaultMaster() {
        return new EniMaster(
                "EtherCAT Master",
                "ffffffffffff",
                "000000000000",
                EthercatConstants.ETHERCAT_ETHERTYPE,
                new MailboxStatesConfig(0L, 0),
                new MasterEoeConfig(0, 0, 0),
                MasterRedundancyConfig.disabled(),
                List.of());
    }
}
