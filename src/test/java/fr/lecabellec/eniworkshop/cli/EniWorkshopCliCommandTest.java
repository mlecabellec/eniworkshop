package fr.lecabellec.eniworkshop.cli;

import fr.lecabellec.eniworkshop.EniWorkshopApplication;
import fr.lecabellec.eniworkshop.catalog.SlaveCatalogService;
import fr.lecabellec.eniworkshop.compare.EniComparisonService;
import fr.lecabellec.eniworkshop.diagram.PlantUmlService;
import fr.lecabellec.eniworkshop.eni.rules.EniValidationEngine;
import fr.lecabellec.eniworkshop.eni.xml.EniCsvService;
import fr.lecabellec.eniworkshop.eni.xml.EniXmlParser;
import fr.lecabellec.eniworkshop.eni.xml.EniXmlWriter;
import fr.lecabellec.eniworkshop.project.ProjectManagementService;
import fr.lecabellec.eniworkshop.quasar.QuasarBusStatus;
import fr.lecabellec.eniworkshop.quasar.QuasarCompareResult;
import fr.lecabellec.eniworkshop.quasar.QuasarEthercatBridge;
import fr.lecabellec.eniworkshop.quasar.QuasarScanResult;
import fr.lecabellec.eniworkshop.topology.TopologyAggregationService;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link EniWorkshopCliCommand} subcommands and dual-mode CLI argument dispatch.
 * Traces to REQ-CLI-001, CS-0090.
 */
final class EniWorkshopCliCommandTest {

    /** DOR compressed ENI fixture. */
    private static final Path DOR_FIXTURE = Path.of("src/test/resources/eni/DOR.xml.gz");

    /** IPSO1 compressed ENI fixture. */
    private static final Path IPSO1_FIXTURE = Path.of("src/test/resources/eni/IPSO1.xml.gz");

    @Test
    void executesInspectValidateCatalogCsvPlantumlAggregateAndQuasarSubcommands(@TempDir final Path tempDir)
            throws Exception {
        final QuasarEthercatBridge mockBridge = mock(QuasarEthercatBridge.class);
        when(mockBridge.scanAndBuildEni("Bus", "CLI-SCAN")).thenReturn(
            new QuasarScanResult(true, "OK", Optional.of("cat-1"), Optional.of("<EtherCATConfig/>"))
        );
        when(mockBridge.connect("opc.tcp://localhost:4840")).thenReturn(
            new QuasarBusStatus(true, "opc.tcp://localhost:4840", 1, "NONE", List.of(), Instant.now())
        );
        when(mockBridge.pushAndTestEni("cat-1")).thenReturn(
            new QuasarCompareResult(true, "Matched", List.of())
        );

        final EniWorkshopCliCommand root = new EniWorkshopCliCommand(
            new EniXmlParser(),
            new EniXmlWriter(),
            new EniCsvService(),
            new EniValidationEngine(),
            new SlaveCatalogService(),
            new TopologyAggregationService(),
            new PlantUmlService(),
            mockBridge,
            new EniComparisonService()
        );
        root.setProjectService(new ProjectManagementService(null));

        final StringWriter outBuf = new StringWriter();
        final StringWriter errBuf = new StringWriter();
        final CommandLine cmd = new CommandLine(root);
        cmd.setOut(new PrintWriter(outBuf));
        cmd.setErr(new PrintWriter(errBuf));

        assertThat(cmd.execute("inspect", DOR_FIXTURE.toString())).isZero();
        assertThat(outBuf.toString()).contains("Slaves=15");

        outBuf.getBuffer().setLength(0);
        assertThat(cmd.execute("validate", "--strict", DOR_FIXTURE.toString())).isZero();
        assertThat(outBuf.toString()).contains("valid=true");

        outBuf.getBuffer().setLength(0);
        assertThat(cmd.execute("catalog", DOR_FIXTURE.toString())).isZero();
        assertThat(outBuf.toString()).contains("Catalog discovery:");

        final Path csvOut = tempDir.resolve("dor.csv");
        assertThat(cmd.execute("export-csv", DOR_FIXTURE.toString(), "-o", csvOut.toString())).isZero();
        assertThat(Files.readString(csvOut)).contains("Name,");

        final Path svgOut = tempDir.resolve("dor.svg");
        assertThat(cmd.execute("plantuml", DOR_FIXTURE.toString(), "--kind", "esm", "--svg", "-o", svgOut.toString()))
            .isZero();
        assertThat(Files.readString(svgOut)).contains("<svg");

        final Path aggOut = tempDir.resolve("agg.xml");
        assertThat(
            cmd.execute("aggregate", DOR_FIXTURE.toString(), DOR_FIXTURE.toString(), "--redundant", "-o", aggOut.toString())
        ).isZero();
        assertThat(Files.readString(aggOut)).contains("<EtherCATConfig");

        final Path scannedOut = tempDir.resolve("scan.xml");
        assertThat(
            cmd.execute("quasar-scan", "--name", "Bus", "-o", scannedOut.toString())
        ).isZero();
        assertThat(Files.readString(scannedOut)).contains("<EtherCATConfig/>");

        outBuf.getBuffer().setLength(0);
        assertThat(
            cmd.execute("compare", DOR_FIXTURE.toString(), IPSO1_FIXTURE.toString(), "--scope", "topology")
        ).isZero();
        assertThat(outBuf.toString()).contains("Compare scope=TOPOLOGY");

        outBuf.getBuffer().setLength(0);
        assertThat(
            cmd.execute("compare", DOR_FIXTURE.toString(), DOR_FIXTURE.toString(), "--scope", "slave", "--json")
        ).isZero();
        assertThat(outBuf.toString()).contains("\"scope\" : \"SLAVE\"");

        outBuf.getBuffer().setLength(0);
        assertThat(cmd.execute("project", "list")).isZero();
        assertThat(outBuf.toString()).contains("FR-00014");

        outBuf.getBuffer().setLength(0);
        assertThat(
            cmd.execute("project", "log", "TSK-00014", "CLI_SMOKE", "Verified CLI project tracking")
        ).isZero();
        assertThat(outBuf.toString()).contains("[TSK-00014] CLI_SMOKE");

        outBuf.getBuffer().setLength(0);
        assertThat(cmd.execute("project", "audit", "--json")).isZero();
        assertThat(outBuf.toString()).contains("\"coveragePercent\"");
    }

    @Test
    void runsHeadlessSpringBootCliContextAndVersion() {
        final int code = EniWorkshopApplication.runCli(new String[] {"--cli", "--version"});
        assertThat(code).isZero();
    }
}
