package fr.lecabellec.eniworkshop.eni.xml;

import fr.lecabellec.eniworkshop.eni.model.DistributedClocksConfig;
import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.model.EniSlave;
import fr.lecabellec.eniworkshop.eni.model.HotConnectGroup;
import fr.lecabellec.eniworkshop.eni.model.MailboxConfig;
import fr.lecabellec.eniworkshop.eni.model.MailboxProtocol;
import fr.lecabellec.eniworkshop.eni.model.PreviousPort;
import fr.lecabellec.eniworkshop.eni.model.ProcessDataLayout;
import fr.lecabellec.eniworkshop.eni.model.ProcessImageVariable;
import fr.lecabellec.eniworkshop.eni.model.SlaveIdentity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * CSV import and export service for EtherCAT slave topology tables and process image variables.
 *
 * <p>Requirement: REQ-00002, REQ-00010, Feature: FR-00007, Constraint: CS-0030.1.
 */
@Service
public final class EniCsvService {

    /** Column index for slave Physics string. */
    private static final int COL_PHYSICS = 3;

    /** Column index for slave VendorId. */
    private static final int COL_VENDOR_ID = 4;

    /** Column index for slave ProductCode. */
    private static final int COL_PRODUCT_CODE = 5;

    /** Column index for slave RevisionNo. */
    private static final int COL_REVISION_NO = 6;

    /** Column index for slave SerialNo. */
    private static final int COL_SERIAL_NO = 7;

    /** Minimum expected CSV columns for a slave row. */
    private static final int MIN_SLAVE_CSV_COLUMNS = 8;

    /** Index of previous port column in slave CSV. */
    private static final int COL_PREV_PHYS = 8;

    /** Index of previous port letter column in slave CSV. */
    private static final int COL_PREV_PORT = 9;

    /** Index of HotConnect group column in slave CSV. */
    private static final int COL_HOTCONNECT = 10;

    /**
     * Exports all slaves of an {@link EniDocument} to RFC-4180 CSV text.
     *
     * @param document non-null ENI document
     * @return CSV table string with header row
     */
    public String exportSlavesToCsv(final EniDocument document) {
        Objects.requireNonNull(document, "EniDocument must not be null");
        final StringBuilder sb = new StringBuilder();
        sb.append("Name,PhysAddr,AutoIncAddr,Physics,VendorId,ProductCode,RevisionNo,SerialNo,")
                .append("PrevPhysAddr,PrevPort,HotConnectGroup,Protocols,RxPdoCount,TxPdoCount\n");
        for (final EniSlave slave : document.slaves()) {
            final SlaveIdentity id = slave.identity();
            final String prevPhys = slave.previousPort().map(p -> Integer.toString(p.physAddr())).orElse("");
            final String prevPort = slave.previousPort().map(p -> Character.toString(p.port())).orElse("");
            final String hcName = slave.hotConnect().map(HotConnectGroup::groupName).orElse("");
            final String protocols = slave.mailbox().protocols().stream()
                    .map(MailboxProtocol::getXmlName)
                    .collect(Collectors.joining("|"));
            sb.append(escapeCsv(id.name())).append(',')
                    .append(id.physAddr()).append(',')
                    .append(id.autoIncAddr()).append(',')
                    .append(escapeCsv(id.physics())).append(',')
                    .append(id.vendorId()).append(',')
                    .append(id.productCode()).append(',')
                    .append(id.revisionNo()).append(',')
                    .append(id.serialNo()).append(',')
                    .append(prevPhys).append(',')
                    .append(prevPort).append(',')
                    .append(escapeCsv(hcName)).append(',')
                    .append(escapeCsv(protocols)).append(',')
                    .append(slave.processData().rxPdos().size()).append(',')
                    .append(slave.processData().txPdos().size())
                    .append('\n');
        }
        return sb.toString();
    }

    /**
     * Imports slave skeletons from a topology CSV payload.
     *
     * @param csvText non-null CSV string matching {@link #exportSlavesToCsv(EniDocument)} columns
     * @return immutable list of parsed {@link EniSlave} entries
     */
    public List<EniSlave> importSlavesFromCsv(final String csvText) {
        Objects.requireNonNull(csvText, "csvText must not be null");
        final List<EniSlave> result = new ArrayList<>();
        final String[] lines = csvText.split("\\r?\\n");
        for (int idx = 0; idx < lines.length; idx++) {
            final String line = lines[idx].trim();
            if (line.isEmpty() || (idx == 0 && line.startsWith("Name,"))) {
                continue;
            }
            final List<String> cols = splitCsvLine(line);
            if (cols.size() < MIN_SLAVE_CSV_COLUMNS) {
                throw new IllegalArgumentException("Invalid CSV slave row at line " + (idx + 1));
            }
            final SlaveIdentity identity = new SlaveIdentity(
                    cols.get(0),
                    Integer.parseInt(cols.get(1).trim()),
                    Integer.parseInt(cols.get(2).trim()),
                    cols.get(COL_PHYSICS).trim(),
                    Long.parseLong(cols.get(COL_VENDOR_ID).trim()),
                    Long.parseLong(cols.get(COL_PRODUCT_CODE).trim()),
                    Long.parseLong(cols.get(COL_REVISION_NO).trim()),
                    Long.parseLong(cols.get(COL_SERIAL_NO).trim()));
            Optional<PreviousPort> prevPort = Optional.empty();
            if (cols.size() > COL_PREV_PORT && !cols.get(COL_PREV_PHYS).isBlank() && !cols.get(COL_PREV_PORT).isBlank()) {
                prevPort = Optional.of(new PreviousPort(
                        Integer.parseInt(cols.get(COL_PREV_PHYS).trim()),
                        cols.get(COL_PREV_PORT).trim().charAt(0),
                        true));
            }
            Optional<HotConnectGroup> hc = Optional.empty();
            if (cols.size() > COL_HOTCONNECT && !cols.get(COL_HOTCONNECT).isBlank()) {
                hc = Optional.of(new HotConnectGroup(cols.get(COL_HOTCONNECT).trim(), 1, List.of()));
            }
            result.add(new EniSlave(
                    identity,
                    prevPort,
                    hc,
                    ProcessDataLayout.empty(),
                    List.of(),
                    MailboxConfig.empty(),
                    DistributedClocksConfig.disabled()));
        }
        return List.copyOf(result);
    }

    /**
     * Exports all ProcessImage variables (Inputs and Outputs) to CSV.
     *
     * @param document non-null ENI document
     * @return CSV table string
     */
    public String exportProcessImageToCsv(final EniDocument document) {
        Objects.requireNonNull(document, "EniDocument must not be null");
        final StringBuilder sb = new StringBuilder();
        sb.append("Direction,Name,DataType,BitSize,BitOffs,Comment\n");
        for (final ProcessImageVariable v : document.processImage().inputs()) {
            appendVariableRow(sb, "Input", v);
        }
        for (final ProcessImageVariable v : document.processImage().outputs()) {
            appendVariableRow(sb, "Output", v);
        }
        return sb.toString();
    }

    /**
     * Appends one variable row to the CSV builder.
     *
     * @param sb        string builder
     * @param direction "Input" or "Output"
     * @param v         process image variable
     */
    private void appendVariableRow(final StringBuilder sb, final String direction, final ProcessImageVariable v) {
        sb.append(direction).append(',')
                .append(escapeCsv(v.name())).append(',')
                .append(escapeCsv(v.dataType())).append(',')
                .append(v.bitSize()).append(',')
                .append(v.bitOffs()).append(',')
                .append(escapeCsv(v.comment())).append('\n');
    }

    /**
     * Escapes a single CSV field according to RFC 4180.
     *
     * @param value raw string
     * @return escaped CSV token
     */
    private String escapeCsv(final String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Splits a single RFC-4180 CSV line respecting double-quoted commas.
     *
     * @param line raw CSV line
     * @return list of unescaped field strings
     */
    private List<String> splitCsvLine(final String line) {
        final List<String> cols = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                cols.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cols.add(current.toString());
        return cols;
    }
}
