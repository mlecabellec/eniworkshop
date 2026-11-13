package fr.lecabellec.eniworkshop.diagram;

import fr.lecabellec.eniworkshop.diagram.FrameByteMapDto.FrameField;
import fr.lecabellec.eniworkshop.diagram.FrameByteMapDto.MappedFrame;
import fr.lecabellec.eniworkshop.diagram.FrameByteMapDto.MappedVariableSlice;
import fr.lecabellec.eniworkshop.eni.model.CyclicCommand;
import fr.lecabellec.eniworkshop.eni.model.CyclicFrame;
import fr.lecabellec.eniworkshop.eni.model.DatagramCmd;
import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.model.EthercatConstants;
import fr.lecabellec.eniworkshop.eni.model.ProcessImageVariable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds byte-accurate Ethernet / EtherCAT frame field maps linked to ProcessImage variables.
 *
 * <p>Requirement: REQ-00007, REQ-00010, Feature: FR-00007, Constraint: CS-0030.2.
 */
@Service
public final class EthercatFrameMapService {

    /** Maximum variable slices attached per datagram field to keep UI payloads bounded. */
    private static final int MAX_MAPPED_VARS_PER_DATAGRAM = 256;

    /**
     * Maps every cyclic frame in an {@link EniDocument} into a {@link MappedFrame} byte layout.
     *
     * @param document non-null ENI document
     * @return immutable list of mapped frames
     */
    public List<MappedFrame> mapCyclicFrames(final EniDocument document) {
        Objects.requireNonNull(document, "EniDocument must not be null");
        final List<CyclicFrame> frames = document.cyclic().frames();
        final List<MappedFrame> mappedFrames = new ArrayList<>(frames.size());

        for (int fIdx = 0; fIdx < frames.size(); fIdx++) {
            final CyclicFrame frame = frames.get(fIdx);
            final List<FrameField> fields = new ArrayList<>();
            int cursor = 0;

            fields.add(new FrameField(
                    "Ethernet Header",
                    "ETHERNET",
                    cursor,
                    EthercatConstants.ETHERNET_HEADER_BYTES,
                    String.format(
                            Locale.ROOT,
                            "Dst=%s Src=%s Type=0x%04x",
                            document.master().destinationMac(),
                            document.master().sourceMac(),
                            document.master().etherType()),
                    List.of()));
            cursor += EthercatConstants.ETHERNET_HEADER_BYTES;

            final int ecatPayload = frame.totalEthercatPayloadBytes();
            fields.add(new FrameField(
                    "EtherCAT Header",
                    "ECAT_HEADER",
                    cursor,
                    EthercatConstants.ETHERCAT_HEADER_BYTES,
                    "Length=" + (ecatPayload - EthercatConstants.ETHERCAT_HEADER_BYTES) + "B Type=1",
                    List.of()));
            cursor += EthercatConstants.ETHERCAT_HEADER_BYTES;

            final List<CyclicCommand> commands = frame.commands();
            for (int cIdx = 0; cIdx < commands.size(); cIdx++) {
                final CyclicCommand cmd = commands.get(cIdx);
                fields.add(new FrameField(
                        "Dgram#" + cIdx + " Hdr (" + cmd.cmd().name() + ")",
                        "DATAGRAM_HEADER",
                        cursor,
                        EthercatConstants.DATAGRAM_HEADER_BYTES,
                        formatAddressDetail(cmd),
                        List.of()));
                cursor += EthercatConstants.DATAGRAM_HEADER_BYTES;

                final List<MappedVariableSlice> slices = collectVariableSlices(document, cmd);
                fields.add(new FrameField(
                        "Dgram#" + cIdx + " Data (" + cmd.dataLength() + "B)",
                        "DATAGRAM_DATA",
                        cursor,
                        cmd.dataLength(),
                        "InOffs=" + cmd.inputOffs() + " OutOffs=" + cmd.outputOffs()
                                + " " + cmd.comment(),
                        slices));
                cursor += cmd.dataLength();

                fields.add(new FrameField(
                        "Dgram#" + cIdx + " WKC",
                        "WKC",
                        cursor,
                        EthercatConstants.DATAGRAM_WKC_BYTES,
                        "Expected WKC=" + cmd.cnt(),
                        List.of()));
                cursor += EthercatConstants.DATAGRAM_WKC_BYTES;
            }

            if (cursor < EthercatConstants.MIN_ETHERNET_FRAME_BYTES) {
                final int pad = EthercatConstants.MIN_ETHERNET_FRAME_BYTES - cursor;
                fields.add(new FrameField(
                        "Ethernet Padding",
                        "PADDING",
                        cursor,
                        pad,
                        "Zero pad to 60B minimum frame",
                        List.of()));
                cursor += pad;
            }

            fields.add(new FrameField(
                    "Ethernet FCS",
                    "FCS",
                    cursor,
                    EthercatConstants.ETHERNET_FCS_BYTES,
                    "CRC-32 trailer",
                    List.of()));
            cursor += EthercatConstants.ETHERNET_FCS_BYTES;

            mappedFrames.add(new MappedFrame(
                    fIdx,
                    document.cyclic().cycleTimeUs(),
                    cursor,
                    ecatPayload,
                    fields));
        }
        return List.copyOf(mappedFrames);
    }

    /**
     * Formats logical or physical datagram address details.
     *
     * @param cmd cyclic command
     * @return formatted address summary
     */
    private String formatAddressDetail(final CyclicCommand cmd) {
        if (cmd.cmd().isLogicalAddressing()) {
            return String.format(Locale.ROOT, "LogAddr=0x%08x Len=%d", cmd.address().addr(), cmd.dataLength());
        }
        return String.format(
                Locale.ROOT,
                "Adp=0x%04x Ado=0x%04x Len=%d",
                cmd.address().adp(),
                cmd.address().ado(),
                cmd.dataLength());
    }

    /**
     * Finds ProcessImage Input/Output variables falling inside this datagram's byte window.
     *
     * @param document ENI document
     * @param cmd      cyclic command
     * @return bounded list of mapped variable slices
     */
    private List<MappedVariableSlice> collectVariableSlices(final EniDocument document, final CyclicCommand cmd) {
        final List<MappedVariableSlice> slices = new ArrayList<>();
        final boolean readsInputs = cmd.cmd() == DatagramCmd.LRD || cmd.cmd() == DatagramCmd.LRW
                || cmd.cmd() == DatagramCmd.BRD || cmd.cmd() == DatagramCmd.APRD || cmd.cmd() == DatagramCmd.FPRD;
        final boolean writesOutputs = cmd.cmd() == DatagramCmd.LWR || cmd.cmd() == DatagramCmd.LRW
                || cmd.cmd() == DatagramCmd.BWR || cmd.cmd() == DatagramCmd.APWR || cmd.cmd() == DatagramCmd.FPWR;

        if (readsInputs) {
            appendSlices(
                    document.processImage().inputs(),
                    "INPUT",
                    cmd.inputOffs(),
                    cmd.dataLength(),
                    slices);
        }
        if (writesOutputs) {
            appendSlices(
                    document.processImage().outputs(),
                    "OUTPUT",
                    cmd.outputOffs(),
                    cmd.dataLength(),
                    slices);
        }
        return List.copyOf(slices);
    }

    /**
     * Matches variables against a byte window [baseOffs, baseOffs + len).
     *
     * @param vars      ProcessImage variable list
     * @param direction "INPUT" or "OUTPUT"
     * @param baseOffs  byte offset in ProcessImage
     * @param len       datagram byte length
     * @param outSlices target slice list
     */
    private void appendSlices(
            final List<ProcessImageVariable> vars,
            final String direction,
            final int baseOffs,
            final int len,
            final List<MappedVariableSlice> outSlices) {
        final int startBit = baseOffs * EthercatConstants.BITS_PER_BYTE;
        final int endBit = (baseOffs + len) * EthercatConstants.BITS_PER_BYTE;
        for (final ProcessImageVariable v : vars) {
            if (outSlices.size() >= MAX_MAPPED_VARS_PER_DATAGRAM) {
                break;
            }
            if (v.bitOffs() >= startBit && v.bitOffs() < endBit) {
                outSlices.add(new MappedVariableSlice(
                        v.name(),
                        direction,
                        v.dataType(),
                        v.bitOffs() - startBit,
                        v.bitSize()));
            }
        }
    }
}
