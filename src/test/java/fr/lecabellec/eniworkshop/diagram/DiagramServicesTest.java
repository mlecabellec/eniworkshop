package fr.lecabellec.eniworkshop.diagram;

import fr.lecabellec.eniworkshop.diagram.FrameByteMapDto.MappedFrame;
import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.xml.EniXmlParser;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link PlantUmlService} and {@link EthercatFrameMapService}. Traces to REQ-00007, CS-0090.
 */
final class DiagramServicesTest {

    /** DOR test fixture path. */
    private static final Path DOR_FIXTURE = Path.of("src/test/resources/eni/DOR.xml.gz");

    /** Minimum Ethernet header byte count before FCS/padding. */
    private static final int MIN_MAPPED_HEADER_BYTES = 16;

    /** Parser helper. */
    private final EniXmlParser parser = new EniXmlParser();

    /** PlantUML service under test. */
    private final PlantUmlService plantUmlService = new PlantUmlService();

    /** Frame map service under test. */
    private final EthercatFrameMapService frameMapService = new EthercatFrameMapService();

    @Test
    void generatesTopologyAndEsmPlantUmlAndRendersSvgOffline() {
        final EniDocument dor = parser.parse(DOR_FIXTURE);

        final String topoPuml = plantUmlService.generateTopologyPuml(dor);
        assertThat(topoPuml).contains("@startuml").contains("@enduml").contains("Phys=");

        final String esmPuml = plantUmlService.generateEsmSequencePuml(dor);
        assertThat(esmPuml).contains("@startuml").contains("@enduml").contains("Init -> PreOp");

        final String svg = plantUmlService.renderSvg(esmPuml);
        assertThat(svg).contains("<svg").contains("</svg>");
    }

    @Test
    void buildsByteAccurateCyclicFrameMap() {
        final EniDocument dor = parser.parse(DOR_FIXTURE);
        final List<MappedFrame> frames = frameMapService.mapCyclicFrames(dor);
        assertThat(frames).isNotEmpty();
        final MappedFrame first = frames.get(0);
        assertThat(first.totalWireBytes()).isGreaterThanOrEqualTo(MIN_MAPPED_HEADER_BYTES);
        assertThat(first.fields()).extracting(FrameByteMapDto.FrameField::category)
            .contains("ETHERNET", "ECAT_HEADER", "DATAGRAM_HEADER", "DATAGRAM_DATA");
    }
}
