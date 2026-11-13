package fr.lecabellec.eniworkshop.diagram;

import fr.lecabellec.eniworkshop.EniWorkshopException;
import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.model.EniSlave;
import fr.lecabellec.eniworkshop.eni.model.PreviousPort;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Generates PlantUML source diagrams for EtherCAT topologies and ESM sequences and renders SVG
 * offline using the Apache-2.0 {@code plantuml-asl} engine.
 *
 * <p>Requirement: REQ-00007, Feature: FR-00007, Constraint: CS-0010.3, CS-0030.9.
 */
@Service
public final class PlantUmlService {

    /** Default maximum slaves rendered individually in a topology diagram before summarizing. */
    private static final int MAX_RENDERED_SLAVES = 60;

    /**
     * Generates a PlantUML component/topology diagram from an {@link EniDocument}.
     *
     * @param document non-null ENI document
     * @return PlantUML source text
     */
    public String generateTopologyPuml(final EniDocument document) {
        Objects.requireNonNull(document, "EniDocument must not be null");
        final StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("left to right direction\n");
        sb.append("skinparam componentStyle rectangle\n");
        sb.append("node \"").append(sanitize(document.master().name())).append("\" as master\n");

        final int limit = Math.min(document.slaves().size(), MAX_RENDERED_SLAVES);
        final Map<Integer, String> aliasByPhys = new HashMap<>();

        for (int i = 0; i < limit; i++) {
            final EniSlave s = document.slaves().get(i);
            final String alias = "s_" + i;
            aliasByPhys.put(s.identity().physAddr(), alias);
            final String hcTag = s.hotConnect().map(hc -> " <<HC:" + sanitize(hc.groupName()) + ">>").orElse("");
            sb.append("rectangle \"")
                    .append(sanitize(s.identity().name()))
                    .append("\\nPhys=").append(s.identity().physAddr())
                    .append(" [").append(sanitize(s.identity().physics())).append("]")
                    .append("\"").append(hcTag).append(" as ").append(alias).append('\n');
        }

        for (int i = 0; i < limit; i++) {
            final EniSlave s = document.slaves().get(i);
            final String target = "s_" + i;
            if (s.previousPort().isPresent()) {
                final PreviousPort pp = s.previousPort().get();
                final String src = aliasByPhys.getOrDefault(pp.physAddr(), "master");
                sb.append(src).append(" --> ").append(target)
                        .append(" : Port ").append(pp.port()).append('\n');
            } else {
                final String src = i == 0 ? "master" : "s_" + (i - 1);
                sb.append(src).append(" --> ").append(target).append('\n');
            }
        }

        if (document.slaves().size() > MAX_RENDERED_SLAVES) {
            sb.append("note as N1\n  Showing ").append(limit).append(" of ")
                    .append(document.slaves().size()).append(" slaves\nend note\n");
        }
        if (document.master().redundancy().enabled() && limit > 0) {
            sb.append("s_").append(limit - 1).append(" ..> master : Redundant Loop Return\n");
        }
        sb.append("@enduml\n");
        return sb.toString();
    }

    /**
     * Generates a PlantUML EtherCAT State Machine (ESM) initialization sequence diagram.
     *
     * @param document non-null ENI document
     * @return PlantUML sequence diagram source
     */
    public String generateEsmSequencePuml(final EniDocument document) {
        Objects.requireNonNull(document, "EniDocument must not be null");
        final StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("participant \"").append(sanitize(document.master().name())).append("\" as M\n");
        sb.append("participant \"Bus Slaves (").append(document.slaves().size()).append(")\" as S\n");
        sb.append("M -> S : IP (Init -> PreOp): ")
                .append(document.master().initCmds().size()).append(" Master InitCmds + Mailbox setup\n");
        sb.append("M -> S : PS (PreOp -> SafeOp): SyncManagers & FMMU mapping\n");
        sb.append("M -> S : SO (SafeOp -> Op): Cyclic frames (")
                .append(document.cyclic().frames().size()).append(" frames @ ")
                .append(document.cyclic().cycleTimeUs()).append(" us)\n");
        sb.append("@enduml\n");
        return sb.toString();
    }

    /**
     * Renders PlantUML source text to an SVG string using {@code plantuml-asl}.
     *
     * @param plantUmlSource non-null PlantUML source
     * @return rendered SVG markup
     */
    public String renderSvg(final String plantUmlSource) {
        Objects.requireNonNull(plantUmlSource, "plantUmlSource must not be null");
        final SourceStringReader reader = new SourceStringReader(plantUmlSource);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            reader.outputImage(os, new FileFormatOption(FileFormat.SVG));
            return os.toString(StandardCharsets.UTF_8);
        } catch (final IOException ex) {
            throw new EniWorkshopException("Failed to render PlantUML SVG", ex);
        }
    }

    /**
     * Sanitizes quotes and newlines in PlantUML labels.
     *
     * @param input raw label
     * @return sanitized ASCII-safe label
     */
    private String sanitize(final String input) {
        return input.replace("\"", "'").replace("\n", " ").replace("\r", "");
    }
}
