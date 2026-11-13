package fr.lecabellec.eniworkshop.diagram;

import java.util.List;
import java.util.Objects;

/**
 * Mapped byte-field and bit-variable layout DTOs for the EtherCAT Frame Byte-Map diagram.
 *
 * <p>Requirement: REQ-00007, REQ-00010, Feature: FR-00007, Constraint: CS-0030.2.
 */
public final class FrameByteMapDto {

    /** Utility holder for nested DTO records. */
    private FrameByteMapDto() {
        // Intentionally empty.
    }

    /**
     * Complete frame byte-map payload for one cyclic Ethernet frame.
     *
     * @param frameIndex           0-based cyclic frame index
     * @param cycleTimeUs          cycle period in microseconds
     * @param totalWireBytes       total frame wire length including Ethernet header, padding, and FCS
     * @param ethercatPayloadBytes EtherCAT payload length excluding Ethernet header and FCS
     * @param fields               ordered byte-range fields (Ethernet header, ECAT header, datagrams, padding, FCS)
     */
    public record MappedFrame(
            int frameIndex,
            int cycleTimeUs,
            int totalWireBytes,
            int ethercatPayloadBytes,
            List<FrameField> fields) {

        /**
         * Compact constructor enforcing defensive list copy.
         */
        public MappedFrame {
            fields = List.copyOf(Objects.requireNonNull(fields, "fields must not be null"));
        }
    }

    /**
     * Single contiguous byte-range field inside an Ethernet/EtherCAT frame.
     *
     * @param name        field label (e.g., "Ethernet Header", "Datagram #0 [LRW]", "FCS")
     * @param category    "ETHERNET", "ECAT_HEADER", "DATAGRAM_HEADER", "DATAGRAM_DATA", "WKC", "PADDING", "FCS"
     * @param byteOffset  starting byte offset in wire frame
     * @param byteLength  byte length of this field
     * @param detail      protocol details (Cmd, Addr, InputOffs, OutputOffs, WKC)
     * @param mappedVars  ProcessImage variables mapped inside this field (non-empty for DATAGRAM_DATA)
     */
    public record FrameField(
            String name,
            String category,
            int byteOffset,
            int byteLength,
            String detail,
            List<MappedVariableSlice> mappedVars) {

        /**
         * Compact constructor enforcing non-null fields and defensive list copy.
         */
        public FrameField {
            Objects.requireNonNull(name, "FrameField name must not be null");
            Objects.requireNonNull(category, "FrameField category must not be null");
            Objects.requireNonNull(detail, "FrameField detail must not be null");
            mappedVars = List.copyOf(Objects.requireNonNull(mappedVars, "mappedVars must not be null"));
        }
    }

    /**
     * ProcessImage variable slice mapped inside a cyclic datagram payload.
     *
     * @param name           variable name
     * @param direction      "INPUT" or "OUTPUT"
     * @param dataType       data type string
     * @param localBitOffset bit offset relative to the start of the datagram Data field
     * @param bitSize        variable bit width
     */
    public record MappedVariableSlice(
            String name,
            String direction,
            String dataType,
            int localBitOffset,
            int bitSize) {
    }
}
