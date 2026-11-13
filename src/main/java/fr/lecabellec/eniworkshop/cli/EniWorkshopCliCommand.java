package fr.lecabellec.eniworkshop.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import fr.lecabellec.eniworkshop.catalog.CatalogDiscoveryReport;
import fr.lecabellec.eniworkshop.catalog.SlaveCatalogEntry;
import fr.lecabellec.eniworkshop.catalog.SlaveCatalogService;
import fr.lecabellec.eniworkshop.compare.ComparisonModels.ComparisonResult;
import fr.lecabellec.eniworkshop.compare.ComparisonModels.DiffStatus;
import fr.lecabellec.eniworkshop.compare.ComparisonModels.OuterJoinRow;
import fr.lecabellec.eniworkshop.compare.EniComparisonService;
import fr.lecabellec.eniworkshop.diagram.PlantUmlService;
import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.model.EniSlave;
import fr.lecabellec.eniworkshop.eni.rules.EniValidationEngine;
import fr.lecabellec.eniworkshop.eni.rules.ValidationIssue;
import fr.lecabellec.eniworkshop.eni.rules.ValidationReport;
import fr.lecabellec.eniworkshop.eni.xml.EniCsvService;
import fr.lecabellec.eniworkshop.eni.xml.EniXmlParser;
import fr.lecabellec.eniworkshop.eni.xml.EniXmlWriter;
import fr.lecabellec.eniworkshop.project.ProjectManagementService;
import fr.lecabellec.eniworkshop.project.ProjectModels.ActivityRecord;
import fr.lecabellec.eniworkshop.project.ProjectModels.ActivitySource;
import fr.lecabellec.eniworkshop.project.ProjectModels.CreateFeatureRequest;
import fr.lecabellec.eniworkshop.project.ProjectModels.CreateTaskRequest;
import fr.lecabellec.eniworkshop.project.ProjectModels.FeatureItem;
import fr.lecabellec.eniworkshop.project.ProjectModels.ProjectOverview;
import fr.lecabellec.eniworkshop.project.ProjectModels.TaskItem;
import fr.lecabellec.eniworkshop.project.ProjectModels.TraceabilityAudit;
import fr.lecabellec.eniworkshop.project.ProjectModels.WorkItemStatus;
import fr.lecabellec.eniworkshop.quasar.QuasarEthercatBridge;
import fr.lecabellec.eniworkshop.quasar.QuasarScanResult;
import fr.lecabellec.eniworkshop.topology.TopologyAggregationRequest;
import fr.lecabellec.eniworkshop.topology.TopologyAggregationService;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Root Picocli command providing headless CLI operations for ENI files, comparison, project tracking,
 * and Quasar OPC-UA servers.
 *
 * <p>Requirement: REQ-00009, REQ-00014, Feature: FR-00009, FR-00014, Task: TSK-00009, TSK-00014.
 */
@Component
@Command(
    name = "eniworkshop",
    mixinStandardHelpOptions = true,
    version = "0.1.0",
    description = "EtherCAT Network Information (ENI) workshop CLI and web platform.",
    subcommands = {
        EniWorkshopCliCommand.InspectCommand.class,
        EniWorkshopCliCommand.ValidateCommand.class,
        EniWorkshopCliCommand.CatalogCommand.class,
        EniWorkshopCliCommand.AggregateCommand.class,
        EniWorkshopCliCommand.ExportCsvCommand.class,
        EniWorkshopCliCommand.PlantUmlCommand.class,
        EniWorkshopCliCommand.QuasarScanCommand.class,
        EniWorkshopCliCommand.CompareCommand.class,
        EniWorkshopCliCommand.ProjectCommand.class
    }
)
public final class EniWorkshopCliCommand implements Callable<Integer> {

    /** Shared Jackson mapper formatted for human-readable CLI JSON output. */
    private static final ObjectMapper PRETTY_MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    /** ENI XML parser service. */
    private final EniXmlParser parser;
    /** ENI XML writer service. */
    private final EniXmlWriter writer;
    /** ENI CSV exporter/importer service. */
    private final EniCsvService csvService;
    /** ETG.2100/ETG.1000 validation engine. */
    private final EniValidationEngine validationEngine;
    /** Slave and extension module catalog service. */
    private final SlaveCatalogService catalogService;
    /** Topology composition and redundancy service. */
    private final TopologyAggregationService aggregationService;
    /** PlantUML generator and SVG rendering service. */
    private final PlantUmlService plantUmlService;
    /** Quasar EtherCAT OPC-UA bridge. */
    private final QuasarEthercatBridge quasarBridge;
    /** Multi-view EtherCAT comparison service. */
    private final EniComparisonService comparisonService;
    /** Project task, feature, and activity tracking service (injected via Spring setter or default offline fallback). */
    private ProjectManagementService projectService = new ProjectManagementService(null);

    /** Inherited work-item attribution flag for all CLI subcommands. */
    @Option(
        names = "--work-ref",
        scope = CommandLine.ScopeType.INHERIT,
        description = "Optional Task (TSK-*) or Feature (FR-*) attributing CLI execution."
    )
    private String workRef;

    /** Picocli command specification injected at execution time. */
    @Spec
    private CommandSpec spec;

    /**
     * Constructs the root CLI command with required domain services.
     *
     * @param parser             ENI XML parser
     * @param writer             ENI XML writer
     * @param csvService         CSV exporter/importer
     * @param validationEngine   rule validation engine
     * @param catalogService     slave/module catalog service
     * @param aggregationService topology aggregation service
     * @param plantUmlService    PlantUML generator and renderer
     * @param quasarBridge       Quasar OPC-UA bridge
     * @param comparisonService  multi-view ENI/topology/slave/hot-connect comparison service
     */
    @Autowired
    public EniWorkshopCliCommand(
        final EniXmlParser parser,
        final EniXmlWriter writer,
        final EniCsvService csvService,
        final EniValidationEngine validationEngine,
        final SlaveCatalogService catalogService,
        final TopologyAggregationService aggregationService,
        final PlantUmlService plantUmlService,
        final QuasarEthercatBridge quasarBridge,
        final EniComparisonService comparisonService
    ) {
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.writer = Objects.requireNonNull(writer, "writer must not be null");
        this.csvService = Objects.requireNonNull(csvService, "csvService must not be null");
        this.validationEngine = Objects.requireNonNull(validationEngine, "validationEngine must not be null");
        this.catalogService = Objects.requireNonNull(catalogService, "catalogService must not be null");
        this.aggregationService = Objects.requireNonNull(aggregationService, "aggregationService must not be null");
        this.plantUmlService = Objects.requireNonNull(plantUmlService, "plantUmlService must not be null");
        this.quasarBridge = Objects.requireNonNull(quasarBridge, "quasarBridge must not be null");
        this.comparisonService = Objects.requireNonNull(comparisonService, "comparisonService must not be null");
    }

    /**
     * Injects the {@link ProjectManagementService} bean when running under Spring Boot.
     *
     * @param projectService project governance service
     */
    @Autowired(required = false)
    public void setProjectService(final ProjectManagementService projectService) {
        if (projectService != null) {
            this.projectService = projectService;
        }
    }

    /**
     * Prints usage information when invoked without a subcommand.
     *
     * @return exit code 0
     */
    @Override
    public Integer call() {
        if (this.spec != null) {
            this.spec.commandLine().usage(this.spec.commandLine().getOut());
        }
        return 0;
    }

    private PrintWriter out() {
        return this.spec.commandLine().getOut();
    }

    private PrintWriter err() {
        return this.spec.commandLine().getErr();
    }

    private EniXmlParser getParser() {
        return this.parser;
    }

    private EniXmlWriter getWriter() {
        return this.writer;
    }

    private EniCsvService getCsvService() {
        return this.csvService;
    }

    private EniValidationEngine getValidationEngine() {
        return this.validationEngine;
    }

    private SlaveCatalogService getCatalogService() {
        return this.catalogService;
    }

    private TopologyAggregationService getAggregationService() {
        return this.aggregationService;
    }

    private PlantUmlService getPlantUmlService() {
        return this.plantUmlService;
    }

    private QuasarEthercatBridge getQuasarBridge() {
        return this.quasarBridge;
    }

    private EniComparisonService getComparisonService() {
        return this.comparisonService;
    }

    private ProjectManagementService getProjectService() {
        return this.projectService;
    }

    private void recordCliActivity(final String defaultRef, final String action, final String target, final String summary) {
        final String resolvedRef = (this.workRef == null || this.workRef.isBlank()) ? defaultRef : this.workRef;
        this.projectService.recordActivity(resolvedRef, action, target, summary, ActivitySource.CLI);
    }

    /**
     * Inspects an ENI XML file and prints summary metrics (master, slaves, hot-connect, cyclic frames).
     */
    @Command(name = "inspect", description = "Inspect summary metadata of an ENI XML file.")
    public static final class InspectCommand implements Callable<Integer> {

        /** Enclosing root CLI command. */
        @CommandLine.ParentCommand
        private EniWorkshopCliCommand parent;

        /** Input ENI XML file path. */
        @Parameters(index = "0", description = "Path to the ENI XML file.")
        private Path eniFile;

        /** Emit JSON instead of plain-text summary when true. */
        @Option(names = {"--json"}, description = "Print full parsed summary as JSON.")
        private boolean json;

        /**
         * Creates an InspectCommand instance for Picocli.
         */
        public InspectCommand() {
            // Default constructor for Picocli
        }

        @Override
        public Integer call() throws IOException {
            EniDocument doc = this.parent.getParser().parse(this.eniFile);
            if (this.json) {
                this.parent.out().println(PRETTY_MAPPER.writeValueAsString(doc));
            } else {
                int varCount = doc.processImage().inputs().size() + doc.processImage().outputs().size();
                this.parent.out().printf(
                    "ENI v%s | Master='%s' | Slaves=%d | HotConnect=%d | CyclicFrames=%d | Variables=%d%n",
                    doc.version(),
                    doc.master().name(),
                    doc.slaves().size(),
                    doc.hotConnectGroupCount(),
                    doc.cyclic().frames().size(),
                    varCount
                );
            }
            return 0;
        }
    }

    /**
     * Validates an ENI XML file against EtherCAT ETG.2100/ETG.1000 consistency rules.
     */
    @Command(name = "validate", description = "Validate an ENI XML file against EtherCAT rules.")
    public static final class ValidateCommand implements Callable<Integer> {

        /** Enclosing root CLI command. */
        @CommandLine.ParentCommand
        private EniWorkshopCliCommand parent;

        /** Input ENI XML file path. */
        @Parameters(index = "0", description = "Path to the ENI XML file.")
        private Path eniFile;

        /** Return exit code 2 when validation reports errors. */
        @Option(names = {"--strict"}, description = "Return non-zero exit code on validation errors.")
        private boolean strict;

        /**
         * Creates a ValidateCommand instance for Picocli.
         */
        public ValidateCommand() {
            // Default constructor for Picocli
        }

        @Override
        public Integer call() {
            EniDocument doc = this.parent.getParser().parse(this.eniFile);
            ValidationReport report = this.parent.getValidationEngine().validate(doc);
            this.parent.out().printf(
                "Validation: valid=%s | errors=%d | warnings=%d | total=%d%n",
                report.valid(),
                report.errorCount(),
                report.warningCount(),
                report.issues().size()
            );
            for (ValidationIssue issue : report.issues()) {
                this.parent.out().printf(
                    "  [%s] %s (%s): %s%n",
                    issue.severity(),
                    issue.ruleId(),
                    issue.target(),
                    issue.message()
                );
            }
            if (this.strict && !report.valid()) {
                return 2;
            }
            return 0;
        }
    }

    /**
     * Discovers unknown slaves/modules from an ENI file into the in-session catalog.
     */
    @Command(name = "catalog", description = "Discover and list slave/module profiles from an ENI XML file.")
    public static final class CatalogCommand implements Callable<Integer> {

        /** Enclosing root CLI command. */
        @CommandLine.ParentCommand
        private EniWorkshopCliCommand parent;

        /** Input ENI XML file path. */
        @Parameters(index = "0", description = "Path to the ENI XML file.")
        private Path eniFile;

        /**
         * Creates a CatalogCommand instance for Picocli.
         */
        public CatalogCommand() {
            // Default constructor for Picocli
        }

        @Override
        public Integer call() {
            EniDocument doc = this.parent.getParser().parse(this.eniFile);
            CatalogDiscoveryReport report = this.parent.getCatalogService()
                .discoverAndRegisterFromEni(doc, this.eniFile.getFileName().toString());
            this.parent.out().printf(
                "Catalog discovery: unique=%d | added=%d | known=%d%n",
                report.uniqueProfilesFound(),
                report.newlyAddedCount(),
                report.alreadyKnownKeys().size()
            );
            for (SlaveCatalogEntry entry : this.parent.getCatalogService().listAll()) {
                this.parent.out().printf(
                    "  - %s | %s (%s) [protocols=%s, physics=%s]%n",
                    entry.id(),
                    entry.displayName(),
                    entry.category(),
                    entry.protocols(),
                    entry.physics()
                );
            }
            return 0;
        }
    }

    /**
     * Aggregates a base ENI topology with one or more branch ENI topologies into a single ENI XML file.
     */
    @Command(name = "aggregate", description = "Aggregate multiple ENI files into a composed topology.")
    public static final class AggregateCommand implements Callable<Integer> {

        /** Enclosing root CLI command. */
        @CommandLine.ParentCommand
        private EniWorkshopCliCommand parent;

        /** Base ENI XML file. */
        @Parameters(index = "0", description = "Base ENI XML file.")
        private Path baseEni;

        /** Additional branch ENI XML files to append. */
        @Parameters(index = "1..*", arity = "1..*", description = "Branch ENI XML files to append.")
        private List<Path> branchEnis = new ArrayList<>();

        /** Destination file path for the composed ENI XML. */
        @Option(names = {"-o", "--output"}, required = true, description = "Output aggregated ENI XML path.")
        private Path outputPath;

        /** Enable cable redundancy ring closure on the aggregated topology. */
        @Option(names = {"--redundant"}, description = "Enable cable redundancy closure.")
        private boolean redundant;

        /** Upstream physical station address to attach to (0 appends at tail). */
        @Option(names = {"--attach-addr"}, defaultValue = "0", description = "Attach PhysAddr (0 for tail).")
        private int attachAddr;

        /**
         * Creates an AggregateCommand instance for Picocli.
         */
        public AggregateCommand() {
            // Default constructor for Picocli
        }

        @Override
        public Integer call() {
            EniDocument base = this.parent.getParser().parse(this.baseEni);
            List<EniDocument> branches = new ArrayList<>();
            for (Path branchPath : this.branchEnis) {
                branches.add(this.parent.getParser().parse(branchPath));
            }
            TopologyAggregationRequest request = new TopologyAggregationRequest(
                this.attachAddr,
                'B',
                this.redundant,
                "",
                true
            );
            EniDocument merged = this.parent.getAggregationService().aggregate(base, branches, request);
            this.parent.getWriter().writeToFile(merged, this.outputPath);
            this.parent.out().printf(
                "Aggregated %d branches into %s (slaves=%d, frames=%d)%n",
                branches.size(),
                this.outputPath,
                merged.slaves().size(),
                merged.cyclic().frames().size()
            );
            return 0;
        }
    }

    /**
     * Exports slaves or process-image variables of an ENI XML file to CSV.
     */
    @Command(name = "export-csv", description = "Export ENI slaves or process variables to CSV.")
    public static final class ExportCsvCommand implements Callable<Integer> {

        /** Enclosing root CLI command. */
        @CommandLine.ParentCommand
        private EniWorkshopCliCommand parent;

        /** Input ENI XML file path. */
        @Parameters(index = "0", description = "Path to the ENI XML file.")
        private Path eniFile;

        /** CSV table kind: slaves or variables. */
        @Option(names = {"--kind"}, defaultValue = "slaves", description = "Export kind: slaves or variables.")
        private String kind;

        /** Optional output CSV path (stdout when null). */
        @Option(names = {"-o", "--output"}, description = "Optional output CSV file (stdout if omitted).")
        private Path outputPath;

        /**
         * Creates an ExportCsvCommand instance for Picocli.
         */
        public ExportCsvCommand() {
            // Default constructor for Picocli
        }

        @Override
        public Integer call() throws IOException {
            EniDocument doc = this.parent.getParser().parse(this.eniFile);
            String csv = "variables".equalsIgnoreCase(this.kind)
                ? this.parent.getCsvService().exportProcessImageToCsv(doc)
                : this.parent.getCsvService().exportSlavesToCsv(doc);
            if (this.outputPath != null) {
                Files.writeString(this.outputPath, csv, StandardCharsets.UTF_8);
                this.parent.out().printf("CSV exported to %s%n", this.outputPath);
            } else {
                this.parent.out().print(csv);
            }
            return 0;
        }
    }

    /**
     * Generates PlantUML (.puml) or rendered SVG from an ENI file.
     */
    @Command(name = "plantuml", description = "Generate PlantUML topology or ESM diagram from an ENI file.")
    public static final class PlantUmlCommand implements Callable<Integer> {

        /** Enclosing root CLI command. */
        @CommandLine.ParentCommand
        private EniWorkshopCliCommand parent;

        /** Input ENI XML file path. */
        @Parameters(index = "0", description = "Path to the ENI XML file.")
        private Path eniFile;

        /** Diagram kind: topology or esm. */
        @Option(names = {"--kind"}, defaultValue = "topology", description = "Diagram kind: topology or esm.")
        private String kind;

        /** Render SVG via PlantUML instead of emitting .puml source. */
        @Option(names = {"--svg"}, description = "Render output directly as SVG instead of raw .puml source.")
        private boolean svg;

        /** Optional output file path (stdout when null). */
        @Option(names = {"-o", "--output"}, description = "Optional output file path.")
        private Path outputPath;

        /**
         * Creates a PlantUmlCommand instance for Picocli.
         */
        public PlantUmlCommand() {
            // Default constructor for Picocli
        }

        @Override
        public Integer call() throws IOException {
            EniDocument doc = this.parent.getParser().parse(this.eniFile);
            String source = "esm".equalsIgnoreCase(this.kind)
                ? this.parent.getPlantUmlService().generateEsmSequencePuml(doc)
                : this.parent.getPlantUmlService().generateTopologyPuml(doc);
            String rendered = this.svg ? this.parent.getPlantUmlService().renderSvg(source) : source;
            if (this.outputPath != null) {
                Files.writeString(this.outputPath, rendered, StandardCharsets.UTF_8);
                this.parent.out().printf("Diagram written to %s%n", this.outputPath);
            } else {
                this.parent.out().print(rendered);
            }
            return 0;
        }
    }

    /**
     * Connects to a Quasar EtherCAT OPC-UA server, scans the live bus, and writes a generated ENI XML.
     */
    @Command(name = "quasar-scan", description = "Scan a live EtherCAT bus via Quasar OPC-UA and export ENI.")
    public static final class QuasarScanCommand implements Callable<Integer> {

        /** Enclosing root CLI command. */
        @CommandLine.ParentCommand
        private EniWorkshopCliCommand parent;

        /** Optional Quasar OPC-UA endpoint override. */
        @Option(names = {"--endpoint"}, description = "Quasar OPC-UA endpoint URL.")
        private String endpointUrl;

        /** Catalog name for the scanned ENI document. */
        @Option(names = {"--name"}, defaultValue = "ScannedBus", description = "Catalog name for scanned ENI.")
        private String name;

        /** Optional destination path for scanned ENI XML. */
        @Option(names = {"-o", "--output"}, description = "Optional output path for scanned ENI XML.")
        private Path outputPath;

        /**
         * Creates a QuasarScanCommand instance for Picocli.
         */
        public QuasarScanCommand() {
            // Default constructor for Picocli
        }

        @Override
        public Integer call() throws IOException {
            if (this.endpointUrl != null && !this.endpointUrl.isBlank()) {
                this.parent.getQuasarBridge().connect(this.endpointUrl);
            }
            QuasarScanResult scanResult = this.parent.getQuasarBridge().scanAndBuildEni(
                this.name,
                "CLI-SCAN"
            );
            this.parent.out().printf(
                "Quasar scan completed: success=%s | catalogId=%s | msg=%s%n",
                scanResult.success(),
                scanResult.eniCatalogId().orElse("none"),
                scanResult.message()
            );
            if (this.outputPath != null && scanResult.generatedXml().isPresent()) {
                Files.writeString(this.outputPath, scanResult.generatedXml().get(), StandardCharsets.UTF_8);
                this.parent.out().printf("Scanned ENI written to %s%n", this.outputPath);
            }
            if (!scanResult.success()) {
                this.parent.err().printf("Quasar scan did not succeed: %s%n", scanResult.message());
                return 1;
            }
            return 0;
        }
    }

    /**
     * Compares two ENI files, topologies, slaves, or Hot Connect groups and emits an outer-join diff.
     */
    @Command(name = "compare", description = "Compare two ENIs, topologies, slaves, or Hot Connect groups.")
    public static final class CompareCommand implements Callable<Integer> {

        /** Enclosing root CLI command. */
        @CommandLine.ParentCommand
        private EniWorkshopCliCommand parent;

        /** Left ENI XML or gzipped XML file. */
        @Parameters(index = "0", description = "Left ENI file path.")
        private Path leftFile;

        /** Right ENI XML or gzipped XML file. */
        @Parameters(index = "1", description = "Right ENI file path.")
        private Path rightFile;

        /** Comparison granularity scope. */
        @Option(
            names = {"--scope"},
            defaultValue = "eni",
            description = "Scope: eni, topology, slave, or hot-connect (default: eni)."
        )
        private String scope;

        /** Optional selector key on the left file (slave physAddr or Hot Connect group name). */
        @Option(names = {"--left-key"}, description = "Left slave physAddr or Hot Connect group name.")
        private String leftKey;

        /** Optional selector key on the right file (slave physAddr or Hot Connect group name). */
        @Option(names = {"--right-key"}, description = "Right slave physAddr or Hot Connect group name.")
        private String rightKey;

        /** Emit full structured ComparisonResult JSON instead of text summary. */
        @Option(names = {"--json"}, description = "Emit full ComparisonResult JSON.")
        private boolean json;

        /**
         * Creates a CompareCommand instance for Picocli.
         */
        public CompareCommand() {
            // Default constructor for Picocli
        }

        @Override
        public Integer call() throws IOException {
            final EniDocument leftDoc = this.parent.getParser().parse(this.leftFile);
            final EniDocument rightDoc = this.parent.getParser().parse(this.rightFile);
            final String normalizedScope = this.scope.toLowerCase(Locale.ROOT).trim();
            final ComparisonResult result = switch (normalizedScope) {
                case "topology" -> this.parent.getComparisonService().compareEnis(
                    this.leftFile.getFileName().toString(),
                    leftDoc,
                    this.rightFile.getFileName().toString(),
                    rightDoc,
                    true
                );
                case "slave" -> compareSelectedSlaves(leftDoc, rightDoc);
                case "hot-connect", "hot_connect" -> compareSelectedHotConnect(leftDoc, rightDoc);
                case "eni" -> this.parent.getComparisonService().compareEnis(
                    this.leftFile.getFileName().toString(),
                    leftDoc,
                    this.rightFile.getFileName().toString(),
                    rightDoc,
                    false
                );
                default -> throw new IllegalArgumentException(
                    "Unsupported --scope: " + this.scope + " (expected eni, topology, slave, hot-connect)"
                );
            };
            if (this.json) {
                this.parent.out().println(PRETTY_MAPPER.writeValueAsString(result));
            } else {
                this.parent.out().printf(
                    Locale.ROOT,
                    "Compare scope=%s | total=%d matched=%d changed=%d leftOnly=%d rightOnly=%d%n",
                    result.scope(),
                    result.summary().totalRows(),
                    result.summary().matchedCount(),
                    result.summary().changedCount(),
                    result.summary().leftOnlyCount(),
                    result.summary().rightOnlyCount()
                );
                for (final OuterJoinRow row : result.outerJoinRows()) {
                    if (row.status() == DiffStatus.MATCH) {
                        continue;
                    }
                    this.parent.out().printf(
                        Locale.ROOT,
                        "  [%s] %s (%s) | L=%s | R=%s%n",
                        row.status(),
                        row.rowKey(),
                        row.name(),
                        row.leftSummary(),
                        row.rightSummary()
                    );
                }
            }
            return 0;
        }

        private ComparisonResult compareSelectedSlaves(final EniDocument leftDoc, final EniDocument rightDoc) {
            final EniSlave leftSlave = findSlave(leftDoc, this.leftKey, this.leftFile);
            final EniSlave rightSlave = findSlave(rightDoc, this.rightKey, this.rightFile);
            return this.parent.getComparisonService().compareSlaves(
                this.leftFile.getFileName() + "#" + leftSlave.identity().physAddr(),
                leftSlave,
                this.rightFile.getFileName() + "#" + rightSlave.identity().physAddr(),
                rightSlave
            );
        }

        private ComparisonResult compareSelectedHotConnect(final EniDocument leftDoc, final EniDocument rightDoc) {
            final String leftGroup = resolveGroupName(leftDoc, this.leftKey, this.leftFile);
            final String rightGroup = resolveGroupName(rightDoc, this.rightKey, this.rightFile);
            return this.parent.getComparisonService().compareHotConnectGroups(
                this.leftFile.getFileName().toString(),
                leftDoc,
                leftGroup,
                this.rightFile.getFileName().toString(),
                rightDoc,
                rightGroup
            );
        }

        private static EniSlave findSlave(final EniDocument doc, final String key, final Path file) {
            if (doc.slaves().isEmpty()) {
                throw new IllegalArgumentException("No slaves present in " + file);
            }
            if (key == null || key.isBlank()) {
                return doc.slaves().get(0);
            }
            final int phys = Integer.parseInt(key.trim());
            for (final EniSlave s : doc.slaves()) {
                if (s.identity().physAddr() == phys) {
                    return s;
                }
            }
            throw new IllegalArgumentException("Slave physAddr=" + phys + " not found in " + file);
        }

        private static String resolveGroupName(final EniDocument doc, final String key, final Path file) {
            if (key != null && !key.isBlank()) {
                return key.trim();
            }
            for (final EniSlave slave : doc.slaves()) {
                if (slave.hotConnect().isPresent()) {
                    return slave.hotConnect().get().groupName();
                }
            }
            throw new IllegalArgumentException("No Hot Connect groups present in " + file);
        }
    }

    /**
     * Manages Features ({@code FR-*}), Tasks ({@code TSK-*}), activity logs, and {@code CS-0010}
     * traceability auditing from the headless CLI.
     */
    @Command(
        name = "project",
        description = "Manage tasks, features, activity logs, and CS-0010 codebase traceability.",
        subcommands = {
            ProjectCommand.ListCommand.class,
            ProjectCommand.CreateFeatureCommand.class,
            ProjectCommand.CreateTaskCommand.class,
            ProjectCommand.SetStatusCommand.class,
            ProjectCommand.LogCommand.class,
            ProjectCommand.AuditCommand.class
        }
    )
    public static final class ProjectCommand implements Callable<Integer> {

        /** Enclosing root CLI command. */
        @CommandLine.ParentCommand
        private EniWorkshopCliCommand parent;

        /** Command spec for printing subcommand usage. */
        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            if (this.spec != null) {
                this.spec.commandLine().usage(this.parent.out());
            }
            return 0;
        }

        /** Lists tracked Features, Tasks, active work reference, and recent activities. */
        @Command(name = "list", description = "List Features, Tasks, and recent tracked activities.")
        public static final class ListCommand implements Callable<Integer> {

            /** Enclosing project command. */
            @CommandLine.ParentCommand
            private ProjectCommand project;

            /** Emit structured JSON instead of formatted text. */
            @Option(names = "--json", description = "Print project overview as JSON.")
            private boolean json;

            @Override
            public Integer call() throws IOException {
                final ProjectOverview overview = this.project.parent.getProjectService().getOverview();
                if (this.json) {
                    this.project.parent.out().println(PRETTY_MAPPER.writeValueAsString(overview));
                } else {
                    this.project.parent.out().printf(
                        Locale.ROOT,
                        "Active=%s | Features=%d | Tasks=%d | Activities=%d | Traceability=%.1f%%%n",
                        overview.activeWorkRef(),
                        overview.features().size(),
                        overview.tasks().size(),
                        overview.activities().size(),
                        overview.traceability().coveragePercent()
                    );
                    for (final FeatureItem feature : overview.features()) {
                        this.project.parent.out().printf(
                            Locale.ROOT,
                            "  [%s] %-11s %s (%s)%n",
                            feature.id(),
                            feature.status(),
                            feature.title(),
                            feature.requirementId()
                        );
                    }
                    for (final TaskItem task : overview.tasks()) {
                        this.project.parent.out().printf(
                            Locale.ROOT,
                            "  [%s] %-11s (%s) %s%n",
                            task.id(),
                            task.status(),
                            task.featureId(),
                            task.title()
                        );
                    }
                }
                return 0;
            }
        }

        /** Creates a new Feature entry in the project registry. */
        @Command(name = "create-feature", description = "Create a new Feature (FR-XXXXX).")
        public static final class CreateFeatureCommand implements Callable<Integer> {

            /** Enclosing project command. */
            @CommandLine.ParentCommand
            private ProjectCommand project;

            /** Feature title. */
            @Parameters(index = "0", description = "Short Feature title.")
            private String title;

            /** Optional explicit requirement ID. */
            @Option(names = "--req", defaultValue = "REQ-00014", description = "Linked Requirement ID.")
            private String requirementId;

            /** Optional package/UI scope. */
            @Option(names = "--scope", defaultValue = "fr.lecabellec.eniworkshop", description = "Code or UI scope.")
            private String scope;

            @Override
            public Integer call() {
                final FeatureItem created = this.project.parent.getProjectService().createFeature(
                    new CreateFeatureRequest(null, this.title, this.requirementId, this.scope),
                    ActivitySource.CLI
                );
                this.project.parent.out().printf(
                    Locale.ROOT,
                    "Created feature %s (%s): %s%n",
                    created.id(),
                    created.requirementId(),
                    created.title()
                );
                return 0;
            }
        }

        /** Creates a new Task dossier and updates TODO.md. */
        @Command(name = "create-task", description = "Create a new Task dossier (TSK-XXXXX).")
        public static final class CreateTaskCommand implements Callable<Integer> {

            /** Enclosing project command. */
            @CommandLine.ParentCommand
            private ProjectCommand project;

            /** Task title. */
            @Parameters(index = "0", description = "Short Task title.")
            private String title;

            /** Linked feature identifier. */
            @Option(names = "--feature", defaultValue = "FR-00014", description = "Linked Feature ID.")
            private String featureId;

            /** Optional linked requirement identifier. */
            @Option(names = "--req", defaultValue = "", description = "Optional Requirement ID.")
            private String requirementId;

            /** Optional goal paragraph. */
            @Option(names = "--goal", defaultValue = "", description = "Task engineering goal.")
            private String goal;

            @Override
            public Integer call() {
                final TaskItem created = this.project.parent.getProjectService().createTask(
                    new CreateTaskRequest(null, this.title, this.featureId, this.requirementId, this.goal),
                    ActivitySource.CLI
                );
                this.project.parent.out().printf(
                    Locale.ROOT,
                    "Created task %s (%s | %s): %s%n",
                    created.id(),
                    created.featureId(),
                    created.requirementId(),
                    created.title()
                );
                return 0;
            }
        }

        /** Transitions an existing Task or Feature status. */
        @Command(name = "set-status", description = "Update Task or Feature status (BACKLOG/IN_PROGRESS/IN_REVIEW/BLOCKED/DONE).")
        public static final class SetStatusCommand implements Callable<Integer> {

            /** Enclosing project command. */
            @CommandLine.ParentCommand
            private ProjectCommand project;

            /** Target TSK-* or FR-* identifier. */
            @Parameters(index = "0", description = "Task (TSK-XXXXX) or Feature (FR-XXXXX) ID.")
            private String itemId;

            /** Target status. */
            @Parameters(index = "1", description = "Target status: BACKLOG, IN_PROGRESS, IN_REVIEW, BLOCKED, DONE.")
            private WorkItemStatus status;

            @Override
            public Integer call() {
                final String upper = this.itemId.trim().toUpperCase(Locale.ROOT);
                if (upper.startsWith("FR-")) {
                    final FeatureItem updated = this.project.parent.getProjectService()
                        .updateFeatureStatus(upper, this.status, ActivitySource.CLI);
                    this.project.parent.out().printf(
                        Locale.ROOT,
                        "Updated %s -> %s%n",
                        updated.id(),
                        updated.status()
                    );
                } else {
                    final TaskItem updated = this.project.parent.getProjectService()
                        .updateTaskStatus(upper, this.status, ActivitySource.CLI);
                    this.project.parent.out().printf(
                        Locale.ROOT,
                        "Updated %s -> %s%n",
                        updated.id(),
                        updated.status()
                    );
                }
                return 0;
            }
        }

        /** Records an explicit CLI activity entry under a Task or Feature. */
        @Command(name = "log", description = "Record a tracked CLI activity under a Task or Feature ID.")
        public static final class LogCommand implements Callable<Integer> {

            /** Enclosing project command. */
            @CommandLine.ParentCommand
            private ProjectCommand project;

            /** Task or Feature reference. */
            @Parameters(index = "0", description = "Work reference ID (TSK-XXXXX or FR-XXXXX).")
            private String workRefId;

            /** Short action verb. */
            @Parameters(index = "1", description = "Short action name (e.g. CLI_VERIFY).")
            private String action;

            /** Summary text. */
            @Parameters(index = "2", description = "Human-readable activity summary.")
            private String summary;

            /** Optional target path or identifier. */
            @Option(names = "--target", defaultValue = "", description = "Optional target file or entity.")
            private String target;

            @Override
            public Integer call() {
                final ActivityRecord record = this.project.parent.getProjectService().recordActivity(
                    this.workRefId,
                    this.action,
                    this.target,
                    this.summary,
                    ActivitySource.CLI
                );
                this.project.parent.out().printf(
                    Locale.ROOT,
                    "Logged [%s] %s: %s (%s)%n",
                    record.workRefId(),
                    record.action(),
                    record.summary(),
                    record.id()
                );
                return 0;
            }
        }

        /** Audits Java codebase traceability against doc/docs/project/ registries. */
        @Command(name = "audit", description = "Run CS-0010 codebase traceability audit across Java sources and tests.")
        public static final class AuditCommand implements Callable<Integer> {

            /** Enclosing project command. */
            @CommandLine.ParentCommand
            private ProjectCommand project;

            /** Emit structured JSON output. */
            @Option(names = "--json", description = "Print traceability report as JSON.")
            private boolean json;

            @Override
            public Integer call() throws IOException {
                final TraceabilityAudit audit = this.project.parent.getProjectService().auditCodebaseTraceability();
                this.project.parent.recordCliActivity(
                    "TSK-00014",
                    "CLI_AUDIT",
                    "src/main/java",
                    String.format(
                        Locale.ROOT,
                        "Traceability audit: %d/%d Java files (%.2f%%)",
                        audit.tracedJavaFiles(),
                        audit.totalJavaFiles(),
                        audit.coveragePercent()
                    )
                );
                if (this.json) {
                    this.project.parent.out().println(PRETTY_MAPPER.writeValueAsString(audit));
                } else {
                    this.project.parent.out().printf(
                        Locale.ROOT,
                        "Traceability: %d/%d Java files (%.2f%%) | untagged=%d | unknownRefs=%d%n",
                        audit.tracedJavaFiles(),
                        audit.totalJavaFiles(),
                        audit.coveragePercent(),
                        audit.untaggedFiles().size(),
                        audit.unknownWorkRefs().size()
                    );
                }
                return audit.untaggedFiles().isEmpty() && audit.unknownWorkRefs().isEmpty() ? 0 : 1;
            }
        }
    }
}
