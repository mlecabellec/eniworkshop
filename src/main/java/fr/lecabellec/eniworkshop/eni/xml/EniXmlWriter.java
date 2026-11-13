package fr.lecabellec.eniworkshop.eni.xml;

import fr.lecabellec.eniworkshop.EniWorkshopException;
import fr.lecabellec.eniworkshop.eni.model.CoeChannelInfo;
import fr.lecabellec.eniworkshop.eni.model.CoeInitCmd;
import fr.lecabellec.eniworkshop.eni.model.CyclicCommand;
import fr.lecabellec.eniworkshop.eni.model.CyclicFrame;
import fr.lecabellec.eniworkshop.eni.model.DistributedClocksConfig;
import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.model.EniMaster;
import fr.lecabellec.eniworkshop.eni.model.EniSlave;
import fr.lecabellec.eniworkshop.eni.model.EsmTransition;
import fr.lecabellec.eniworkshop.eni.model.HotConnectGroup;
import fr.lecabellec.eniworkshop.eni.model.InitCmd;
import fr.lecabellec.eniworkshop.eni.model.MailboxConfig;
import fr.lecabellec.eniworkshop.eni.model.MailboxProtocol;
import fr.lecabellec.eniworkshop.eni.model.MailboxRegion;
import fr.lecabellec.eniworkshop.eni.model.PdoEntry;
import fr.lecabellec.eniworkshop.eni.model.PdoMapping;
import fr.lecabellec.eniworkshop.eni.model.PreviousPort;
import fr.lecabellec.eniworkshop.eni.model.ProcessDataLayout;
import fr.lecabellec.eniworkshop.eni.model.ProcessDataRegion;
import fr.lecabellec.eniworkshop.eni.model.ProcessImage;
import fr.lecabellec.eniworkshop.eni.model.ProcessImageVariable;
import fr.lecabellec.eniworkshop.eni.model.SlaveIdentity;
import fr.lecabellec.eniworkshop.eni.model.SyncManagerConfig;
import fr.lecabellec.eniworkshop.eni.model.ValidateClause;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Streaming StAX XML serializer for ETG.2100 {@link EniDocument} configurations.
 *
 * <p>Requirement: REQ-00002, Feature: FR-00002, Constraint: CS-0030.9.
 */
@Service
public final class EniXmlWriter {

    /** XML schema instance namespace URI. */
    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    /** StAX output factory. */
    private final XMLOutputFactory outputFactory;

    /**
     * Constructs a new {@link EniXmlWriter}.
     */
    public EniXmlWriter() {
        this.outputFactory = XMLOutputFactory.newInstance();
    }

    /**
     * Serializes an {@link EniDocument} to a UTF-8 XML string.
     *
     * @param document non-null ENI document
     * @return formatted ETG.2100 XML string
     */
    public String writeToString(final EniDocument document) {
        Objects.requireNonNull(document, "EniDocument must not be null");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            write(document, baos);
            return baos.toString(StandardCharsets.UTF_8);
        } catch (final IOException ex) {
            throw new EniWorkshopException("Failed to serialize ENI document to string", ex);
        }
    }

    /**
     * Writes an {@link EniDocument} to disk.
     *
     * @param document   non-null ENI document
     * @param targetPath destination file path
     */
    public void writeToFile(final EniDocument document, final Path targetPath) {
        Objects.requireNonNull(document, "EniDocument must not be null");
        Objects.requireNonNull(targetPath, "targetPath must not be null");
        try (OutputStream os = Files.newOutputStream(targetPath)) {
            write(document, os);
        } catch (final IOException ex) {
            throw new EniWorkshopException("Failed to write ENI XML to file: " + targetPath, ex);
        }
    }

    /**
     * Streams an {@link EniDocument} into an {@link OutputStream}.
     *
     * @param document     non-null ENI document
     * @param outputStream destination stream
     */
    public void write(final EniDocument document, final OutputStream outputStream) {
        Objects.requireNonNull(document, "EniDocument must not be null");
        Objects.requireNonNull(outputStream, "outputStream must not be null");
        try {
            final XMLStreamWriter writer = outputFactory.createXMLStreamWriter(outputStream, StandardCharsets.UTF_8.name());
            try {
                writeDocument(writer, document);
                writer.flush();
            } finally {
                writer.close();
            }
        } catch (final XMLStreamException ex) {
            throw new EniWorkshopException("Failed to write ENI XML stream", ex);
        }
    }

    /**
     * Writes the root EtherCATConfig and Config structure.
     *
     * @param writer   StAX writer
     * @param document ENI document
     * @throws XMLStreamException on write error
     */
    private void writeDocument(final XMLStreamWriter writer, final EniDocument document) throws XMLStreamException {
        writer.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
        writer.writeStartElement("EtherCATConfig");
        writer.writeAttribute("Version", document.version());
        writer.writeAttribute("xmlns:xsi", XSI_NS);
        writer.writeAttribute("xsi:noNamespaceSchemaLocation", "EtherCATConfig.xsd");
        writer.writeStartElement("Config");

        writeMaster(writer, document.master());
        for (final EniSlave slave : document.slaves()) {
            writeSlave(writer, slave);
        }
        writeCyclic(writer, document);
        writeProcessImage(writer, document.processImage());

        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndDocument();
    }

    /**
     * Writes the Master element.
     *
     * @param writer StAX writer
     * @param master master configuration
     * @throws XMLStreamException on write error
     */
    private void writeMaster(final XMLStreamWriter writer, final EniMaster master) throws XMLStreamException {
        writer.writeStartElement("Master");
        writer.writeStartElement("Info");
        writeTextElement(writer, "Name", master.name());
        writeTextElement(writer, "Destination", master.destinationMac());
        writeTextElement(writer, "Source", master.sourceMac());
        writeTextElement(writer, "EtherType", String.format(Locale.ROOT, "%04x", master.etherType()));
        writer.writeEndElement();

        writer.writeStartElement("MailboxStates");
        writeTextElement(writer, "StartAddr", Long.toString(master.mailboxStates().startAddr()));
        writeTextElement(writer, "Count", Integer.toString(master.mailboxStates().count()));
        writer.writeEndElement();

        writer.writeStartElement("EoE");
        writeTextElement(writer, "MaxPorts", Integer.toString(master.eoe().maxPorts()));
        writeTextElement(writer, "MaxFrames", Integer.toString(master.eoe().maxFrames()));
        writeTextElement(writer, "MaxMACs", Integer.toString(master.eoe().maxMacs()));
        writer.writeEndElement();

        if (master.redundancy().enabled()) {
            writer.writeStartElement("Redundancy");
            writeTextElement(writer, "Enable", "1");
            writeTextElement(writer, "Source", master.redundancy().secondarySourceMac());
            writer.writeEndElement();
        }

        if (!master.initCmds().isEmpty()) {
            writer.writeStartElement("InitCmds");
            for (final InitCmd cmd : master.initCmds()) {
                writeInitCmd(writer, "InitCmd", cmd);
            }
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    /**
     * Writes a single Slave element.
     *
     * @param writer StAX writer
     * @param slave  slave configuration
     * @throws XMLStreamException on write error
     */
    private void writeSlave(final XMLStreamWriter writer, final EniSlave slave) throws XMLStreamException {
        writer.writeStartElement("Slave");
        final SlaveIdentity id = slave.identity();
        writer.writeStartElement("Info");
        writeTextElement(writer, "Name", id.name());
        writeTextElement(writer, "PhysAddr", Integer.toString(id.physAddr()));
        writeTextElement(writer, "AutoIncAddr", Integer.toString(id.autoIncAddr()));
        if (!id.physics().isEmpty()) {
            writeTextElement(writer, "Physics", id.physics());
        }
        writeTextElement(writer, "VendorId", Long.toString(id.vendorId()));
        writeTextElement(writer, "ProductCode", Long.toString(id.productCode()));
        writeTextElement(writer, "RevisionNo", Long.toString(id.revisionNo()));
        writeTextElement(writer, "SerialNo", Long.toString(id.serialNo()));
        writer.writeEndElement();

        if (slave.previousPort().isPresent()) {
            final PreviousPort pp = slave.previousPort().get();
            writer.writeStartElement("PreviousPort");
            writer.writeAttribute("Selected", pp.selected() ? "1" : "0");
            writeTextElement(writer, "PhysAddr", Integer.toString(pp.physAddr()));
            writeTextElement(writer, "Port", Character.toString(pp.port()));
            writer.writeEndElement();
        }

        if (slave.hotConnect().isPresent()) {
            writeHotConnect(writer, slave.hotConnect().get());
        }

        writeProcessData(writer, slave.processData());

        if (!slave.initCmds().isEmpty()) {
            writer.writeStartElement("InitCmds");
            for (final InitCmd cmd : slave.initCmds()) {
                writeInitCmd(writer, "InitCmd", cmd);
            }
            writer.writeEndElement();
        }

        writeMailbox(writer, slave.mailbox());
        writeDc(writer, slave.dc());
        writer.writeEndElement();
    }

    /**
     * Writes HotConnect block.
     *
     * @param writer StAX writer
     * @param hc     HotConnect group
     * @throws XMLStreamException on write error
     */
    private void writeHotConnect(final XMLStreamWriter writer, final HotConnectGroup hc) throws XMLStreamException {
        writer.writeStartElement("HotConnect");
        writeTextElement(writer, "GroupName", hc.groupName());
        writeTextElement(writer, "GroupMemberCnt", Integer.toString(hc.groupMemberCount()));
        for (final InitCmd idCmd : hc.identifyCmds()) {
            writeInitCmd(writer, "IdentifyCmd", idCmd);
        }
        writer.writeEndElement();
    }

    /**
     * Writes Slave ProcessData section.
     *
     * @param writer StAX writer
     * @param pd     process data layout
     * @throws XMLStreamException on write error
     */
    private void writeProcessData(final XMLStreamWriter writer, final ProcessDataLayout pd) throws XMLStreamException {
        writer.writeStartElement("ProcessData");
        if (pd.sendRegion().isPresent()) {
            writeRegion(writer, "Send", pd.sendRegion().get());
        }
        if (pd.recvRegion().isPresent()) {
            writeRegion(writer, "Recv", pd.recvRegion().get());
        }
        for (final SyncManagerConfig sm : pd.syncManagers()) {
            writer.writeStartElement("Sm" + sm.index());
            writeTextElement(writer, "Type", sm.type());
            if (sm.defaultSize() > 0) {
                writeTextElement(writer, "DefaultSize", Integer.toString(sm.defaultSize()));
            }
            writeTextElement(writer, "StartAddress", Integer.toString(sm.startAddress()));
            writeTextElement(writer, "ControlByte", Integer.toString(sm.controlByte()));
            writeTextElement(writer, "Enable", sm.enable() ? "1" : "0");
            for (final Integer pdoIdx : sm.pdoIndices()) {
                writeTextElement(writer, "Pdo", Integer.toString(pdoIdx));
            }
            writer.writeEndElement();
        }
        for (final PdoMapping pdo : pd.rxPdos()) {
            writePdo(writer, "RxPdo", pdo);
        }
        for (final PdoMapping pdo : pd.txPdos()) {
            writePdo(writer, "TxPdo", pdo);
        }
        writer.writeEndElement();
    }

    /**
     * Writes Send or Recv bit region.
     *
     * @param writer StAX writer
     * @param tag    element tag
     * @param region process data region
     * @throws XMLStreamException on write error
     */
    private void writeRegion(final XMLStreamWriter writer, final String tag, final ProcessDataRegion region)
            throws XMLStreamException {
        writer.writeStartElement(tag);
        writeTextElement(writer, "BitStart", Integer.toString(region.bitStart()));
        writeTextElement(writer, "BitLength", Integer.toString(region.bitLength()));
        writer.writeEndElement();
    }

    /**
     * Writes an RxPdo or TxPdo block.
     *
     * @param writer StAX writer
     * @param tag    "RxPdo" or "TxPdo"
     * @param pdo    PDO mapping
     * @throws XMLStreamException on write error
     */
    private void writePdo(final XMLStreamWriter writer, final String tag, final PdoMapping pdo) throws XMLStreamException {
        writer.writeStartElement(tag);
        if (pdo.flags().fixed()) {
            writer.writeAttribute("Fixed", "1");
        }
        if (pdo.flags().mandatory()) {
            writer.writeAttribute("Mandatory", "1");
        }
        if (pdo.flags().virtual()) {
            writer.writeAttribute("Virtual", "1");
        }
        if (pdo.flags().syncManager() >= 0) {
            writer.writeAttribute("Sm", Integer.toString(pdo.flags().syncManager()));
        }
        if (pdo.flags().syncUnit() >= 0) {
            writer.writeAttribute("Su", Integer.toString(pdo.flags().syncUnit()));
        }
        writeTextElement(writer, "Index", String.format(Locale.ROOT, "#x%x", pdo.index()));
        writeTextElement(writer, "Name", pdo.name());
        for (final Integer excl : pdo.excludes()) {
            writeTextElement(writer, "Exclude", String.format(Locale.ROOT, "#x%x", excl));
        }
        for (final PdoEntry entry : pdo.entries()) {
            writer.writeStartElement("Entry");
            writeTextElement(writer, "Index", String.format(Locale.ROOT, "#x%x", entry.index()));
            writeTextElement(writer, "SubIndex", Integer.toString(entry.subIndex()));
            writeTextElement(writer, "BitLen", Integer.toString(entry.bitLen()));
            if (!entry.name().isEmpty()) {
                writeTextElement(writer, "Name", entry.name());
            }
            if (!entry.comment().isEmpty()) {
                writeTextElement(writer, "Comment", entry.comment());
            }
            if (!entry.dataType().isEmpty()) {
                writer.writeStartElement("DataType");
                if (!entry.dscale().isEmpty()) {
                    writer.writeAttribute("DScale", entry.dscale());
                }
                writer.writeCharacters(entry.dataType());
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    /**
     * Writes Slave Mailbox configuration.
     *
     * @param writer StAX writer
     * @param mbx    mailbox config
     * @throws XMLStreamException on write error
     */
    private void writeMailbox(final XMLStreamWriter writer, final MailboxConfig mbx) throws XMLStreamException {
        if (mbx.protocols().isEmpty()
                && mbx.standard().sendLength() == 0
                && mbx.standard().recvLength() == 0
                && !mbx.dataLinkLayer()) {
            return;
        }
        writer.writeStartElement("Mailbox");
        writer.writeAttribute("DataLinkLayer", mbx.dataLinkLayer() ? "1" : "0");
        writeMailboxRegion(writer, "Send", mbx.standard().sendStart(), mbx.standard().sendLength(), 0);
        writeMailboxRegion(
                writer,
                "Recv",
                mbx.standard().recvStart(),
                mbx.standard().recvLength(),
                mbx.standard().statusBitAddr());

        if (mbx.bootstrap().isPresent()) {
            final MailboxRegion bs = mbx.bootstrap().get();
            writer.writeStartElement("BootStrap");
            writeMailboxRegion(writer, "Send", bs.sendStart(), bs.sendLength(), 0);
            writeMailboxRegion(writer, "Recv", bs.recvStart(), bs.recvLength(), 0);
            writer.writeEndElement();
        }
        for (final MailboxProtocol p : mbx.protocols()) {
            writeTextElement(writer, "Protocol", p.getXmlName());
        }
        if (!mbx.aoeNetId().isEmpty()) {
            writer.writeStartElement("AoE");
            writeTextElement(writer, "NetId", mbx.aoeNetId());
            writer.writeEndElement();
        }
        if (!mbx.coeProfileChannels().isEmpty() || !mbx.coeInitCmds().isEmpty()) {
            writeCoe(writer, mbx.coeProfileChannels(), mbx.coeInitCmds());
        }
        writer.writeEndElement();
    }

    /**
     * Writes Mailbox Send or Recv sub-element.
     *
     * @param writer        StAX writer
     * @param tag           "Send" or "Recv"
     * @param start         DPRAM start address
     * @param length        DPRAM byte length
     * @param statusBitAddr status bit address (written if &gt; 0)
     * @throws XMLStreamException on write error
     */
    private void writeMailboxRegion(
            final XMLStreamWriter writer,
            final String tag,
            final int start,
            final int length,
            final int statusBitAddr) throws XMLStreamException {
        writer.writeStartElement(tag);
        writeTextElement(writer, "Start", Integer.toString(start));
        writeTextElement(writer, "Length", Integer.toString(length));
        if (statusBitAddr > 0) {
            writeTextElement(writer, "StatusBitAddr", Integer.toString(statusBitAddr));
        }
        writer.writeEndElement();
    }

    /**
     * Writes CoE Profile and InitCmds block.
     *
     * @param writer   StAX writer
     * @param channels channel profiles
     * @param cmds     CoE InitCmds
     * @throws XMLStreamException on write error
     */
    private void writeCoe(
            final XMLStreamWriter writer,
            final List<CoeChannelInfo> channels,
            final List<CoeInitCmd> cmds) throws XMLStreamException {
        writer.writeStartElement("CoE");
        if (!channels.isEmpty()) {
            writer.writeStartElement("Profile");
            for (final CoeChannelInfo ch : channels) {
                writer.writeStartElement("ChannelInfo");
                writeTextElement(writer, "ProfileNo", Integer.toString(ch.profileNo()));
                writeTextElement(writer, "AddInfo", Integer.toString(ch.addInfo()));
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
        if (!cmds.isEmpty()) {
            writer.writeStartElement("InitCmds");
            for (final CoeInitCmd cmd : cmds) {
                writer.writeStartElement("InitCmd");
                writer.writeAttribute("Fixed", cmd.fixed() ? "1" : "0");
                if (cmd.completeAccess()) {
                    writer.writeAttribute("CompleteAccess", "1");
                }
                for (final EsmTransition tr : cmd.transitions()) {
                    writeTextElement(writer, "Transition", tr.name());
                }
                writeTextElement(writer, "Comment", cmd.comment());
                writeTextElement(writer, "Timeout", Integer.toString(cmd.timeoutMs()));
                writeTextElement(writer, "Ccs", Integer.toString(cmd.ccs()));
                writeTextElement(writer, "Index", Integer.toString(cmd.index()));
                writeTextElement(writer, "SubIndex", Integer.toString(cmd.subIndex()));
                writeTextElement(writer, "Data", cmd.dataHex());
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    /**
     * Writes Distributed Clocks configuration.
     *
     * @param writer StAX writer
     * @param dc     DC configuration
     * @throws XMLStreamException on write error
     */
    private void writeDc(final XMLStreamWriter writer, final DistributedClocksConfig dc) throws XMLStreamException {
        writer.writeStartElement("DC");
        writeTextElement(writer, "PotentialReferenceClock", dc.potentialReferenceClock() ? "1" : "0");
        if (dc.referenceClock()) {
            writeTextElement(writer, "ReferenceClock", "1");
        }
        if (dc.cycleTime0() > 0L) {
            writeTextElement(writer, "CycleTime0", Long.toString(dc.cycleTime0()));
        }
        if (dc.cycleTime1() > 0L) {
            writeTextElement(writer, "CycleTime1", Long.toString(dc.cycleTime1()));
        }
        if (dc.shiftTime0() != 0L) {
            writeTextElement(writer, "ShiftTime0", Long.toString(dc.shiftTime0()));
        }
        if (dc.shiftTime1() != 0L) {
            writeTextElement(writer, "ShiftTime1", Long.toString(dc.shiftTime1()));
        }
        writer.writeEndElement();
    }

    /**
     * Writes an InitCmd or IdentifyCmd element.
     *
     * @param writer StAX writer
     * @param tag    element name
     * @param cmd    InitCmd model
     * @throws XMLStreamException on write error
     */
    private void writeInitCmd(final XMLStreamWriter writer, final String tag, final InitCmd cmd)
            throws XMLStreamException {
        writer.writeStartElement(tag);
        for (final EsmTransition tr : cmd.transitions()) {
            writeTextElement(writer, "Transition", tr.name());
        }
        if (cmd.beforeSlave()) {
            writeTextElement(writer, "BeforeSlave", "1");
        }
        if (!cmd.comment().isEmpty()) {
            writeTextElement(writer, "Comment", cmd.comment());
        }
        if (!cmd.timing().requires().isEmpty()) {
            writeTextElement(writer, "Requires", cmd.timing().requires());
        }
        writeTextElement(writer, "Cmd", Integer.toString(cmd.cmd().getCode()));
        if (cmd.address().addr() > 0L) {
            writeTextElement(writer, "Addr", Long.toString(cmd.address().addr()));
        } else {
            writeTextElement(writer, "Adp", Integer.toString(cmd.address().adp()));
            writeTextElement(writer, "Ado", Integer.toString(cmd.address().ado()));
        }
        if (!cmd.payload().dataHex().isEmpty()) {
            writeTextElement(writer, "Data", cmd.payload().dataHex());
        } else {
            writeTextElement(writer, "DataLength", Integer.toString(cmd.payload().dataLength()));
        }
        if (cmd.payload().cnt() > 0) {
            writeTextElement(writer, "Cnt", Integer.toString(cmd.payload().cnt()));
        }
        writeTextElement(writer, "Retries", Integer.toString(cmd.timing().retries()));
        if (cmd.timing().timeoutMs() > 0) {
            writeTextElement(writer, "Timeout", Integer.toString(cmd.timing().timeoutMs()));
        }
        if (cmd.validate().isPresent()) {
            final ValidateClause val = cmd.validate().get();
            writer.writeStartElement("Validate");
            if (val.signed()) {
                writer.writeAttribute("Signed", "1");
            }
            if (!"EQ".equals(val.type())) {
                writer.writeAttribute("Type", val.type());
            }
            writeTextElement(writer, "Data", val.dataHex());
            if (!val.dataMaskHex().isEmpty()) {
                writeTextElement(writer, "DataMask", val.dataMaskHex());
            }
            writeTextElement(writer, "Timeout", Integer.toString(val.timeoutMs()));
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    /**
     * Writes Config/Cyclic frames and commands.
     *
     * @param writer   StAX writer
     * @param document ENI document
     * @throws XMLStreamException on write error
     */
    private void writeCyclic(final XMLStreamWriter writer, final EniDocument document) throws XMLStreamException {
        writer.writeStartElement("Cyclic");
        writeTextElement(writer, "CycleTime", Integer.toString(document.cyclic().cycleTimeUs()));
        for (final CyclicFrame frame : document.cyclic().frames()) {
            writer.writeStartElement("Frame");
            for (final CyclicCommand cmd : frame.commands()) {
                writer.writeStartElement("Cmd");
                for (final String st : cmd.states()) {
                    writeTextElement(writer, "State", st);
                }
                if (!cmd.comment().isEmpty()) {
                    writeTextElement(writer, "Comment", cmd.comment());
                }
                writeTextElement(writer, "Cmd", Integer.toString(cmd.cmd().getCode()));
                if (cmd.cmd().isLogicalAddressing()) {
                    writeTextElement(writer, "Addr", Long.toString(cmd.address().addr()));
                } else {
                    writeTextElement(writer, "Adp", Integer.toString(cmd.address().adp()));
                    writeTextElement(writer, "Ado", Integer.toString(cmd.address().ado()));
                }
                writeTextElement(writer, "DataLength", Integer.toString(cmd.dataLength()));
                writeTextElement(writer, "Cnt", Integer.toString(cmd.cnt()));
                writeTextElement(writer, "InputOffs", Integer.toString(cmd.inputOffs()));
                writeTextElement(writer, "OutputOffs", Integer.toString(cmd.outputOffs()));
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    /**
     * Writes Config/ProcessImage Inputs and Outputs.
     *
     * @param writer StAX writer
     * @param pi     ProcessImage model
     * @throws XMLStreamException on write error
     */
    private void writeProcessImage(final XMLStreamWriter writer, final ProcessImage pi) throws XMLStreamException {
        writer.writeStartElement("ProcessImage");
        writer.writeStartElement("Inputs");
        writeTextElement(writer, "ByteSize", Integer.toString(pi.inputsByteSize()));
        for (final ProcessImageVariable v : pi.inputs()) {
            writeVariable(writer, v);
        }
        writer.writeEndElement();

        writer.writeStartElement("Outputs");
        writeTextElement(writer, "ByteSize", Integer.toString(pi.outputsByteSize()));
        for (final ProcessImageVariable v : pi.outputs()) {
            writeVariable(writer, v);
        }
        writer.writeEndElement();
        writer.writeEndElement();
    }

    /**
     * Writes a single ProcessImage Variable element.
     *
     * @param writer   StAX writer
     * @param variable variable definition
     * @throws XMLStreamException on write error
     */
    private void writeVariable(final XMLStreamWriter writer, final ProcessImageVariable variable)
            throws XMLStreamException {
        writer.writeStartElement("Variable");
        writeTextElement(writer, "Name", variable.name());
        if (!variable.comment().isEmpty()) {
            writeTextElement(writer, "Comment", variable.comment());
        }
        writeTextElement(writer, "DataType", variable.dataType());
        writeTextElement(writer, "BitSize", Integer.toString(variable.bitSize()));
        writeTextElement(writer, "BitOffs", Integer.toString(variable.bitOffs()));
        writer.writeEndElement();
    }

    /**
     * Helper writing a simple text leaf element.
     *
     * @param writer StAX writer
     * @param tag    element tag name
     * @param value  non-null text content
     * @throws XMLStreamException on write error
     */
    private void writeTextElement(final XMLStreamWriter writer, final String tag, final String value)
            throws XMLStreamException {
        writer.writeStartElement(tag);
        writer.writeCharacters(value);
        writer.writeEndElement();
    }
}
