package fr.lecabellec.eniworkshop.project;

import fr.lecabellec.eniworkshop.EniWorkshopException;
import fr.lecabellec.eniworkshop.messaging.WorkshopEventPublisher;
import fr.lecabellec.eniworkshop.project.ProjectModels.ActivityRecord;
import fr.lecabellec.eniworkshop.project.ProjectModels.ActivitySource;
import fr.lecabellec.eniworkshop.project.ProjectModels.CreateFeatureRequest;
import fr.lecabellec.eniworkshop.project.ProjectModels.CreateTaskRequest;
import fr.lecabellec.eniworkshop.project.ProjectModels.FeatureItem;
import fr.lecabellec.eniworkshop.project.ProjectModels.ProjectOverview;
import fr.lecabellec.eniworkshop.project.ProjectModels.TaskItem;
import fr.lecabellec.eniworkshop.project.ProjectModels.TraceabilityAudit;
import fr.lecabellec.eniworkshop.project.ProjectModels.WorkItemStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Coordinates Nunki- and Quasar-inspired project governance, Markdown dossier synchronization,
 * runtime activity attribution by Task ({@code TSK-*}) or Feature ({@code FR-*}), and
 * {@code CS-0010} codebase traceability audits.
 *
 * <p>Requirement: REQ-00014, Feature: FR-00014, Task: TSK-00014, Constraint: CS-0010, CS-0030.2.
 */
@Service
public final class ProjectManagementService {

    /** Default active work item tracked when no explicit work reference is supplied. */
    public static final String DEFAULT_WORK_REF = "TSK-00014";

    /** Feature ID for project & task management workbench. */
    private static final String FEATURE_PROJECT_WORKBENCH = "FR-00014";

    /** Requirement ID for project & task management workbench. */
    private static final String REQ_PROJECT_WORKBENCH = "REQ-00014";

    /** SLF4J logger. */
    private static final Logger LOG = LoggerFactory.getLogger(ProjectManagementService.class);

    /** Feature ID regex pattern. */
    private static final Pattern FEATURE_ID_PATTERN = Pattern.compile("^FR-\\d{5}$");

    /** Task ID regex pattern. */
    private static final Pattern TASK_ID_PATTERN = Pattern.compile("^TSK-\\d{5}$");

    /** General work reference pattern accepting FR, TSK, REQ, BUG, FIX IDs. */
    private static final Pattern WORK_REF_PATTERN = Pattern.compile("\\b(REQ|FR|TSK|BUG|FIX)-\\d{5}\\b");

    /** Coding standard reference pattern. */
    private static final Pattern CS_REF_PATTERN = Pattern.compile("\\bCS-\\d{4}\\b");

    /** Table row extractor for features README. */
    private static final Pattern FEATURE_ROW_PATTERN = Pattern.compile("^\\|\\s*`(FR-\\d{5})`\\s*\\|.*");

    /** Task header extractor in TSK-*.md dossiers. */
    private static final Pattern TASK_HEADER_PATTERN = Pattern.compile("^#\\s*\\[(TSK-\\d{5})]\\s*(.+)$");

    /** Dossier metadata field patterns. */
    private static final Pattern FIELD_FEATURE_PATTERN = Pattern.compile("^-\\s*\\*\\*Feature\\*\\*:\\s*`([^`]+)`");

    /** Dossier requirement field pattern. */
    private static final Pattern FIELD_REQ_PATTERN = Pattern.compile("^-\\s*\\*\\*Requirement\\*\\*:\\s*`([^`]+)`");

    /** Dossier status field pattern. */
    private static final Pattern FIELD_STATUS_PATTERN = Pattern.compile("^-\\s*\\*\\*Status\\*\\*:\\s*`([^`]+)`");

    /** Minimum columns required in a feature Markdown table row. */
    private static final int FEATURE_TABLE_MIN_COLS = 7;

    /** Column indexes for feature table parsing. */
    private static final int COL_ID = 1;

    /** Column index for feature title. */
    private static final int COL_TITLE = 2;

    /** Column index for requirement ID. */
    private static final int COL_REQ = 3;

    /** Column index for primary task list. */
    private static final int COL_TASKS = 4;

    /** Column index for feature status. */
    private static final int COL_STATUS = 5;

    /** Column index for package / UI scope. */
    private static final int COL_SCOPE = 6;

    /** Maximum activities retained in the in-memory ring buffer. */
    private static final int MAX_MEMORY_ACTIVITIES = 500;

    /** Maximum activities returned in overview queries. */
    private static final int DEFAULT_ACTIVITY_LIMIT = 100;

    /** Percentage scaling factor (100.0). */
    private static final double PERCENT_SCALE = 100.0;

    /** Relative doc directory path segments. */
    private static final String DOC_DIR = "doc";

    /** Relative docs subdirectory name. */
    private static final String DOCS_DIR = "docs";

    /** Relative project docs subdirectory name. */
    private static final String PROJECT_DIR = "project";

    /** Features subdirectory name. */
    private static final String FEATURES_DIR = "features";

    /** Tasks subdirectory name. */
    private static final String TASKS_DIR = "tasks";

    /** Markdown README file name. */
    private static final String README_MD = "README.md";

    /** Markdown TODO board file name. */
    private static final String TODO_MD = "TODO.md";

    /** Date formatter for task activity log entries. */
    private static final DateTimeFormatter ISO_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    /** STOMP event topic identifier for activity updates. */
    private static final String EVENT_PROJECT_ACTIVITY = "PROJECT_ACTIVITY";

    /** Markdown backtick token. */
    private static final String BACKTICK = "`";

    /** Empty string constant. */
    private static final String EMPTY = "";

    /** Optional MongoDB repository for persistent activity records. */
    private final Optional<ProjectActivityRepository> activityRepository;

    /** Optional STOMP/Kafka publisher for live UI updates. */
    private final Optional<WorkshopEventPublisher> eventPublisher;

    /** Root workspace path containing {@code doc/docs/project} and {@code src/}. */
    private final Path workspaceRoot;

    /** Synchronization monitor guarding registries and Markdown writes. */
    private final Object lock = new Object();

    /** Ordered registry of Features keyed by ID. */
    private final Map<String, FeatureItem> features = new LinkedHashMap<>();

    /** Ordered registry of Tasks keyed by ID. */
    private final Map<String, TaskItem> tasks = new LinkedHashMap<>();

    /** Recent activity records held in memory for offline CLI/test visibility. */
    private final Deque<ActivityRecord> recentActivities = new ArrayDeque<>();

    /** Currently active workbench task or feature identifier. */
    private volatile String activeWorkRef = DEFAULT_WORK_REF;

    /**
     * Constructs a {@link ProjectManagementService} with optional persistence and event publishing.
     *
     * @param activityRepository optional MongoDB activity repository (absent in offline CLI mode)
     * @param eventPublisher     optional STOMP/Kafka event publisher
     * @param workspaceRootPath  workspace root directory path
     */
    @Autowired
    public ProjectManagementService(
            @Autowired(required = false) final ProjectActivityRepository activityRepository,
            @Autowired(required = false) final WorkshopEventPublisher eventPublisher,
            @Value("${eniworkshop.project.root:.}") final String workspaceRootPath) {
        this.activityRepository = Optional.ofNullable(activityRepository);
        this.eventPublisher = Optional.ofNullable(eventPublisher);
        this.workspaceRoot = Path.of(
                Objects.requireNonNull(workspaceRootPath, "workspaceRootPath must not be null")).toAbsolutePath();
        reloadFromWorkspace();
    }

    /**
     * Convenience constructor for offline CLI invocations and unit tests.
     *
     * @param activityRepository optional MongoDB activity repository
     */
    public ProjectManagementService(final ProjectActivityRepository activityRepository) {
        this(activityRepository, null, ".");
    }

    /**
     * Convenience constructor targeting an explicit workspace root directory (useful in tests).
     *
     * @param activityRepository optional MongoDB activity repository
     * @param workspaceRoot      root directory containing {@code doc/docs/project} and {@code src/}
     */
    public ProjectManagementService(
            final ProjectActivityRepository activityRepository,
            final Path workspaceRoot) {
        this(activityRepository, null,
                Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null").toString());
    }

    /**
     * Reloads Feature and Task registries from the workspace Markdown tree, falling back to
     * built-in seed entries when running outside the repository root.
     */
    public void reloadFromWorkspace() {
        synchronized (lock) {
            features.clear();
            tasks.clear();
            loadFeaturesLocked();
            loadTasksLocked();
            if (features.isEmpty() && tasks.isEmpty()) {
                seedDefaultsLocked();
            }
            if (!tasks.containsKey(activeWorkRef) && !features.containsKey(activeWorkRef)) {
                activeWorkRef = tasks.isEmpty()
                        ? features.keySet().iterator().next()
                        : DEFAULT_WORK_REF;
            }
        }
    }

    /**
     * Returns an immutable snapshot of active work reference, features, tasks, activities,
     * and live {@code CS-0010} codebase traceability metrics.
     *
     * @return complete {@link ProjectOverview}
     */
    public ProjectOverview getOverview() {
        final List<FeatureItem> featureList = listFeatures();
        final List<TaskItem> taskList = listTasks();
        final List<ActivityRecord> activities = listActivities(null, DEFAULT_ACTIVITY_LIMIT);
        final TraceabilityAudit audit = auditCodebaseTraceability();
        return new ProjectOverview(activeWorkRef, featureList, taskList, activities, audit);
    }

    /**
     * Returns all tracked Features sorted by identifier.
     *
     * @return immutable list of {@link FeatureItem}
     */
    public List<FeatureItem> listFeatures() {
        synchronized (lock) {
            return features.values().stream()
                    .sorted(Comparator.comparing(FeatureItem::id))
                    .toList();
        }
    }

    /**
     * Returns all tracked Tasks sorted by identifier descending (newest first).
     *
     * @return immutable list of {@link TaskItem}
     */
    public List<TaskItem> listTasks() {
        synchronized (lock) {
            return tasks.values().stream()
                    .sorted(Comparator.comparing(TaskItem::id).reversed())
                    .toList();
        }
    }

    /**
     * Returns the currently selected active Task or Feature identifier.
     *
     * @return active work reference (e.g., {@code TSK-00014})
     */
    public String getActiveWorkRef() {
        return activeWorkRef;
    }

    /**
     * Sets the active Task or Feature identifier used to attribute subsequent activities.
     *
     * @param workRefId candidate work item identifier
     * @param source    originating channel
     * @return normalized active identifier
     */
    public String setActiveWorkRef(final String workRefId, final ActivitySource source) {
        final String normalized = normalizeWorkRef(workRefId);
        this.activeWorkRef = normalized;
        recordActivity(normalized, "ACTIVE_REF_SET", normalized,
                "Switched active work item to " + normalized,
                source == null ? ActivitySource.SYSTEM : source);
        return normalized;
    }

    /**
     * Creates a new Feature entry, appends it to {@code doc/docs/project/features/README.md} when
     * writable, and logs a tracked activity.
     *
     * @param request creation payload
     * @param source  originating execution channel
     * @return created {@link FeatureItem}
     */
    public FeatureItem createFeature(final CreateFeatureRequest request, final ActivitySource source) {
        Objects.requireNonNull(request, "request must not be null");
        final String title = requireNonBlank(request.title(), "feature title must not be blank");
        final FeatureItem created;
        synchronized (lock) {
            final String id = isBlank(request.id())
                    ? allocateNextIdLocked(features.keySet(), "FR-")
                    : validatePattern(request.id().trim().toUpperCase(Locale.ROOT), FEATURE_ID_PATTERN, "FR-XXXXX");
            if (features.containsKey(id)) {
                throw new EniWorkshopException("Feature already exists: " + id);
            }
            final String reqId = isBlank(request.requirementId())
                    ? REQ_PROJECT_WORKBENCH
                    : request.requirementId().trim().toUpperCase(Locale.ROOT);
            final String scope = isBlank(request.scope()) ? "fr.lecabellec.eniworkshop" : request.scope().trim();
            created = new FeatureItem(id, title, reqId, List.of(), WorkItemStatus.BACKLOG, scope);
            features.put(id, created);
            appendFeatureToReadmeLocked(created);
        }
        recordActivity(created.id(), "FEATURE_CREATE", created.id(),
                "Created feature " + created.id() + ": " + created.title(),
                source == null ? ActivitySource.SYSTEM : source);
        return created;
    }

    /**
     * Creates a new Task dossier under {@code doc/docs/project/tasks/}, links it to {@code TODO.md},
     * and logs a tracked activity.
     *
     * @param request task creation payload
     * @param source  originating execution channel
     * @return created {@link TaskItem}
     */
    public TaskItem createTask(final CreateTaskRequest request, final ActivitySource source) {
        Objects.requireNonNull(request, "request must not be null");
        final String title = requireNonBlank(request.title(), "task title must not be blank");
        final TaskItem created;
        synchronized (lock) {
            final String id = isBlank(request.id())
                    ? allocateNextIdLocked(tasks.keySet(), "TSK-")
                    : validatePattern(request.id().trim().toUpperCase(Locale.ROOT), TASK_ID_PATTERN, "TSK-XXXXX");
            if (tasks.containsKey(id)) {
                throw new EniWorkshopException("Task already exists: " + id);
            }
            final String featureId = isBlank(request.featureId())
                    ? FEATURE_PROJECT_WORKBENCH
                    : request.featureId().trim().toUpperCase(Locale.ROOT);
            final String reqId = isBlank(request.requirementId())
                    ? resolveFeatureRequirementLocked(featureId)
                    : request.requirementId().trim().toUpperCase(Locale.ROOT);
            final String goal = isBlank(request.goal()) ? title : request.goal().trim();
            created = new TaskItem(id, title, featureId, reqId, WorkItemStatus.BACKLOG, goal);
            tasks.put(id, created);
            writeTaskDossierLocked(created);
            upsertTaskIndexAndTodoLocked(created);
        }
        recordActivity(created.id(), "TASK_CREATE", created.id(),
                "Created task " + created.id() + " (" + created.featureId() + "): " + created.title(),
                source == null ? ActivitySource.SYSTEM : source);
        return created;
    }

    /**
     * Transitions an existing Task to a new lifecycle {@link WorkItemStatus}, updates its Markdown
     * dossier and {@code TODO.md} completion marker, and logs an activity record.
     *
     * @param taskId task identifier
     * @param status target lifecycle state
     * @param source originating channel
     * @return updated {@link TaskItem}
     */
    public TaskItem updateTaskStatus(
            final String taskId,
            final WorkItemStatus status,
            final ActivitySource source) {
        final String normalizedId = validatePattern(
                requireNonBlank(taskId, "taskId must not be blank").toUpperCase(Locale.ROOT),
                TASK_ID_PATTERN, "TSK-XXXXX");
        Objects.requireNonNull(status, "status must not be null");
        final TaskItem updated;
        synchronized (lock) {
            final TaskItem existing = tasks.get(normalizedId);
            if (existing == null) {
                throw new EniWorkshopException("Unknown task ID: " + normalizedId);
            }
            updated = new TaskItem(
                    existing.id(),
                    existing.title(),
                    existing.featureId(),
                    existing.requirementId(),
                    status,
                    existing.goal());
            tasks.put(normalizedId, updated);
            updateTaskDossierStatusLocked(updated);
            upsertTaskIndexAndTodoLocked(updated);
        }
        recordActivity(updated.id(), "TASK_STATUS", updated.id(),
                "Updated " + updated.id() + " status to " + status.name(),
                source == null ? ActivitySource.SYSTEM : source);
        return updated;
    }

    /**
     * Transitions an existing Feature to a new lifecycle {@link WorkItemStatus} and logs an activity.
     *
     * @param featureId feature identifier
     * @param status    target status
     * @param source    originating channel
     * @return updated {@link FeatureItem}
     */
    public FeatureItem updateFeatureStatus(
            final String featureId,
            final WorkItemStatus status,
            final ActivitySource source) {
        final String normalizedId = validatePattern(
                requireNonBlank(featureId, "featureId must not be blank").toUpperCase(Locale.ROOT),
                FEATURE_ID_PATTERN, "FR-XXXXX");
        Objects.requireNonNull(status, "status must not be null");
        final FeatureItem updated;
        synchronized (lock) {
            final FeatureItem existing = features.get(normalizedId);
            if (existing == null) {
                throw new EniWorkshopException("Unknown feature ID: " + normalizedId);
            }
            updated = new FeatureItem(
                    existing.id(),
                    existing.title(),
                    existing.requirementId(),
                    existing.taskIds(),
                    status,
                    existing.scope());
            features.put(normalizedId, updated);
            updateFeatureStatusInReadmeLocked(updated);
        }
        recordActivity(updated.id(), "FEATURE_STATUS", updated.id(),
                "Updated feature " + updated.id() + " status to " + status.name(),
                source == null ? ActivitySource.SYSTEM : source);
        return updated;
    }

    /**
     * Records a workbench, CLI, or system activity attributed to a Task ({@code TSK-*}) or
     * Feature ({@code FR-*}) and persists it to MongoDB when available.
     *
     * @param workRefId task, feature, requirement, bug, or fix identifier (nullable -> active ref)
     * @param action    short uppercase action verb (e.g., {@code ENI_IMPORT})
     * @param target    affected entity or endpoint path
     * @param summary   human-readable explanation
     * @param source    execution channel
     * @return persisted {@link ActivityRecord}
     */
    public ActivityRecord recordActivity(
            final String workRefId,
            final String action,
            final String target,
            final String summary,
            final ActivitySource source) {
        final String resolvedRef = isBlank(workRefId) ? activeWorkRef : normalizeWorkRef(workRefId);
        final String safeAction = isBlank(action) ? "WORKBENCH_ACTION" : action.trim().toUpperCase(Locale.ROOT);
        final String safeTarget = isBlank(target) ? resolvedRef : target.trim();
        final String safeSummary = isBlank(summary) ? safeAction + " on " + safeTarget : summary.trim();
        final ActivitySource safeSource = source == null ? ActivitySource.SYSTEM : source;
        final ActivityRecord record = new ActivityRecord(
                UUID.randomUUID().toString(),
                resolvedRef,
                safeAction,
                safeTarget,
                safeSummary,
                safeSource,
                Instant.now().toString());
        synchronized (lock) {
            recentActivities.addFirst(record);
            while (recentActivities.size() > MAX_MEMORY_ACTIVITIES) {
                recentActivities.removeLast();
            }
        }
        persistActivityBestEffort(record);
        eventPublisher.ifPresent(pub -> pub.publish(EVENT_PROJECT_ACTIVITY, resolvedRef,
                "[" + safeSource + "] " + safeAction + ": " + safeSummary));
        return record;
    }

    /**
     * Lists recent activity records, optionally filtered by {@code workRefId}.
     *
     * @param workRefFilter optional task/feature filter
     * @param limit         maximum number of entries to return
     * @return descending chronological list of {@link ActivityRecord}
     */
    public List<ActivityRecord> listActivities(final String workRefFilter, final int limit) {
        final int safeLimit = limit <= 0 ? DEFAULT_ACTIVITY_LIMIT : limit;
        final String normalizedFilter = isBlank(workRefFilter) ? null : workRefFilter.trim().toUpperCase(Locale.ROOT);

        if (activityRepository.isPresent()) {
            try {
                if (normalizedFilter != null) {
                    return activityRepository.get().findByWorkRefIdOrderByTimestampDesc(normalizedFilter)
                            .stream()
                            .limit(safeLimit)
                            .toList();
                }
                return activityRepository.get().findAll(Sort.by(Sort.Direction.DESC, "timestamp"))
                        .stream()
                        .limit(safeLimit)
                        .toList();
            } catch (final RuntimeException ex) {
                logWarning("Falling back to in-memory {} ring buffer: {}", "activity", ex);
            }
        }

        synchronized (lock) {
            return recentActivities.stream()
                    .filter(item -> normalizedFilter == null || normalizedFilter.equals(item.workRefId()))
                    .limit(safeLimit)
                    .toList();
        }
    }

    /**
     * Resolves the effective Task/Feature identifier for an HTTP request given an explicit
     * header/parameter value or endpoint fallback mapping.
     *
     * @param explicitRef optional {@code X-Eni-Work-Ref} header or query parameter
     * @param requestUri  URI path being accessed
     * @return valid work reference ID
     */
    public String resolveWorkRefForRequest(final String explicitRef, final String requestUri) {
        if (!isBlank(explicitRef)) {
            final Matcher matcher = WORK_REF_PATTERN.matcher(explicitRef.trim().toUpperCase(Locale.ROOT));
            if (matcher.find()) {
                return matcher.group(0);
            }
        }
        final String uri = requestUri == null ? EMPTY : requestUri;
        if (uri.startsWith("/api/compare")) {
            return "FR-00011";
        }
        if (uri.startsWith("/api/quasar")) {
            return "FR-00008";
        }
        if (uri.startsWith("/api/catalog")) {
            return "FR-00004";
        }
        if (uri.startsWith("/api/topology")) {
            return "FR-00005";
        }
        if (uri.startsWith("/api/diagrams")) {
            return "FR-00007";
        }
        if (uri.startsWith("/api/eni")) {
            return "FR-00006";
        }
        return activeWorkRef;
    }

    /**
     * Audits Java source and test files in the workspace for {@code CS-0010} requirement, feature,
     * task, or coding-standard annotations and verifies referenced {@code FR-*}/{@code TSK-*} IDs.
     *
     * @return {@link TraceabilityAudit} metrics
     */
    public TraceabilityAudit auditCodebaseTraceability() {
        final List<Path> scanRoots = List.of(
                workspaceRoot.resolve("src").resolve("main").resolve("java"),
                workspaceRoot.resolve("src").resolve("test").resolve("java"));
        final Set<String> knownFeatureIds;
        final Set<String> knownTaskIds;
        synchronized (lock) {
            knownFeatureIds = new HashSet<>(features.keySet());
            knownTaskIds = new HashSet<>(tasks.keySet());
        }

        int totalFiles = 0;
        int tracedFiles = 0;
        final List<String> untagged = new ArrayList<>();
        final List<String> unknownRefs = new ArrayList<>();

        for (final Path root : scanRoots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                final List<Path> javaFiles = walk
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .filter(path -> !"package-info.java".equals(path.getFileName().toString()))
                        .sorted()
                        .toList();
                for (final Path javaFile : javaFiles) {
                    totalFiles++;
                    final String relPath = workspaceRoot.relativize(javaFile).toString().replace('\\', '/');
                    final String content = Files.readString(javaFile, StandardCharsets.UTF_8);
                    final Set<String> refs = extractMatches(content);
                    final boolean hasCsTag = CS_REF_PATTERN.matcher(content).find();
                    if (refs.isEmpty() && !hasCsTag) {
                        untagged.add(relPath);
                    } else {
                        tracedFiles++;
                        collectUnknownRefs(refs, knownFeatureIds, knownTaskIds, relPath, unknownRefs);
                    }
                }
            } catch (final IOException ex) {
                logWarning("Unable to walk Java tree {} for traceability audit: {}", root, ex);
            }
        }

        final double coverage = totalFiles == 0
                ? PERCENT_SCALE
                : Math.round((double) tracedFiles * PERCENT_SCALE / totalFiles * PERCENT_SCALE) / PERCENT_SCALE;
        return new TraceabilityAudit(totalFiles, tracedFiles, coverage, untagged, unknownRefs);
    }

    /** Loads feature definitions from {@code doc/docs/project/features/README.md}. */
    private void loadFeaturesLocked() {
        final Path featuresReadme = projectDir().resolve(FEATURES_DIR).resolve(README_MD);
        if (!Files.isRegularFile(featuresReadme)) {
            return;
        }
        try {
            final List<String> lines = Files.readAllLines(featuresReadme, StandardCharsets.UTF_8);
            for (final String line : lines) {
                if (!FEATURE_ROW_PATTERN.matcher(line).matches()) {
                    continue;
                }
                final String[] cols = line.split("\\|");
                if (cols.length < FEATURE_TABLE_MIN_COLS) {
                    continue;
                }
                final String id = stripTicks(cols[COL_ID]);
                final String title = cols[COL_TITLE].trim();
                final String reqId = stripTicks(cols[COL_REQ]);
                final List<String> taskIds = parseTaskIdList(cols[COL_TASKS]);
                final WorkItemStatus status = parseStatus(stripTicks(cols[COL_STATUS]));
                final String scope = stripTicks(cols[COL_SCOPE]);
                features.put(id, new FeatureItem(id, title, reqId, taskIds, status, scope));
            }
        } catch (final IOException ex) {
            logWarning("Failed reading features registry {}: {}", featuresReadme, ex);
        }
    }

    /** Loads task dossiers from {@code doc/docs/project/tasks/TSK-*.md}. */
    private void loadTasksLocked() {
        final Path tasksDir = projectDir().resolve(TASKS_DIR);
        if (!Files.isDirectory(tasksDir)) {
            return;
        }
        try (Stream<Path> files = Files.list(tasksDir)) {
            final List<Path> taskFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("TSK-")
                            && path.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
            for (final Path taskFile : taskFiles) {
                parseTaskFile(taskFile).ifPresent(item -> tasks.put(item.id(), item));
            }
        } catch (final IOException ex) {
            logWarning("Failed scanning task dossiers in {}: {}", tasksDir, ex);
        }
    }

    /**
     * Parses an individual Markdown task dossier into a {@link TaskItem}.
     *
     * @param taskFile path to the task Markdown dossier
     * @return parsed task item or empty when malformed
     */
    private Optional<TaskItem> parseTaskFile(final Path taskFile) {
        try {
            final List<String> lines = Files.readAllLines(taskFile, StandardCharsets.UTF_8);
            String id = null;
            String title = null;
            String featureId = FEATURE_PROJECT_WORKBENCH;
            String reqId = REQ_PROJECT_WORKBENCH;
            WorkItemStatus status = WorkItemStatus.BACKLOG;
            final StringBuilder goalBuilder = new StringBuilder();
            boolean inGoal = false;

            for (final String line : lines) {
                final Matcher headerMatcher = TASK_HEADER_PATTERN.matcher(line.trim());
                if (headerMatcher.matches() && id == null) {
                    id = headerMatcher.group(1);
                    title = headerMatcher.group(COL_TITLE).trim();
                    continue;
                }
                final Matcher featMatcher = FIELD_FEATURE_PATTERN.matcher(line.trim());
                if (featMatcher.find()) {
                    featureId = featMatcher.group(1).trim();
                    continue;
                }
                final Matcher reqMatcher = FIELD_REQ_PATTERN.matcher(line.trim());
                if (reqMatcher.find()) {
                    reqId = reqMatcher.group(1).trim();
                    continue;
                }
                final Matcher statusMatcher = FIELD_STATUS_PATTERN.matcher(line.trim());
                if (statusMatcher.find()) {
                    status = parseStatus(statusMatcher.group(1).trim());
                    continue;
                }
                if ("## Goal".equals(line.trim())) {
                    inGoal = true;
                    continue;
                }
                if (inGoal) {
                    if (line.startsWith("## ")) {
                        inGoal = false;
                    } else if (!line.isBlank()) {
                        if (!goalBuilder.isEmpty()) {
                            goalBuilder.append(' ');
                        }
                        goalBuilder.append(line.trim());
                    }
                }
            }
            if (id == null || title == null) {
                return Optional.empty();
            }
            final String goal = goalBuilder.isEmpty() ? title : goalBuilder.toString();
            return Optional.of(new TaskItem(id, title, featureId, reqId, status, goal));
        } catch (final IOException ex) {
            logWarning("Unable to read task dossier {}: {}", taskFile, ex);
            return Optional.empty();
        }
    }

    /** Seeds default governance entries when launched outside the source repository root. */
    private void seedDefaultsLocked() {
        features.put(FEATURE_PROJECT_WORKBENCH, new FeatureItem(
                FEATURE_PROJECT_WORKBENCH,
                "Task, Feature & Universal Activity Tracking Workbench",
                REQ_PROJECT_WORKBENCH,
                List.of(DEFAULT_WORK_REF),
                WorkItemStatus.IN_PROGRESS,
                "fr.lecabellec.eniworkshop.project"));
        tasks.put(DEFAULT_WORK_REF, new TaskItem(
                DEFAULT_WORK_REF,
                "Task, Feature & Universal Activity Tracking Workbench",
                FEATURE_PROJECT_WORKBENCH,
                REQ_PROJECT_WORKBENCH,
                WorkItemStatus.IN_PROGRESS,
                "Track all workbench activities, code modules, CLI runs, and documentation by Task/Feature."));
    }

    /**
     * Appends a created Feature row to {@code doc/docs/project/features/README.md} if present.
     *
     * @param feature created feature item
     */
    private void appendFeatureToReadmeLocked(final FeatureItem feature) {
        final Path featuresReadme = projectDir().resolve(FEATURES_DIR).resolve(README_MD);
        if (!Files.isWritable(featuresReadme)) {
            return;
        }
        try {
            final String existing = Files.readString(featuresReadme, StandardCharsets.UTF_8);
            final String row = String.format(
                    "| `%s` | %s | `%s` | `%s` | `%s` | `%s` |%n",
                    feature.id(),
                    feature.title(),
                    feature.requirementId(),
                    feature.taskIds().isEmpty() ? "-" : String.join(", ", feature.taskIds()),
                    feature.status().name(),
                    feature.scope());
            final String updated = existing.endsWith("\n") ? existing + row : existing + "\n" + row;
            Files.writeString(featuresReadme, updated, StandardCharsets.UTF_8);
        } catch (final IOException ex) {
            logWarning("Failed updating features README {}: {}", featuresReadme, ex);
        }
    }

    /**
     * Updates feature status cell inside {@code doc/docs/project/features/README.md}.
     *
     * @param feature updated feature item
     */
    private void updateFeatureStatusInReadmeLocked(final FeatureItem feature) {
        final Path featuresReadme = projectDir().resolve(FEATURES_DIR).resolve(README_MD);
        if (!Files.isWritable(featuresReadme)) {
            return;
        }
        try {
            final List<String> lines = Files.readAllLines(featuresReadme, StandardCharsets.UTF_8);
            final List<String> rewritten = new ArrayList<>(lines.size());
            final String prefix = "| `" + feature.id() + "` |";
            for (final String line : lines) {
                if (line.startsWith(prefix)) {
                    final String[] cols = line.split("\\|", -1);
                    if (cols.length >= FEATURE_TABLE_MIN_COLS) {
                        cols[COL_STATUS] = " `" + feature.status().name() + "` ";
                        rewritten.add(String.join("|", cols));
                        continue;
                    }
                }
                rewritten.add(line);
            }
            Files.write(featuresReadme, rewritten, StandardCharsets.UTF_8);
        } catch (final IOException ex) {
            logWarning("Failed updating feature status in {}: {}", featuresReadme, ex);
        }
    }

    /**
     * Writes a new Markdown task dossier under {@code doc/docs/project/tasks/}.
     *
     * @param task created task item
     */
    private void writeTaskDossierLocked(final TaskItem task) {
        final Path tasksDir = projectDir().resolve(TASKS_DIR);
        if (!Files.isDirectory(tasksDir) || !Files.isWritable(tasksDir)) {
            return;
        }
        final Path file = tasksDir.resolve(task.id() + ".md");
        final String today = ISO_DATE_FMT.format(Instant.now());
        final String content = "# [" + task.id() + "] " + task.title() + "\n\n"
                + "- **Feature**: `" + task.featureId() + "`\n"
                + "- **Requirement**: `" + task.requirementId() + "`\n"
                + "- **Status**: `" + task.status().name() + "`\n\n"
                + "## Goal\n"
                + task.goal() + "\n\n"
                + "## Implementation Plan\n"
                + "- [ ] Execute and verify task deliverables.\n\n"
                + "## Activity Log\n"
                + "- `" + today + "` `[" + task.featureId() + " / " + task.id() + "]` Task initialized.\n";
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (final IOException ex) {
            logWarning("Failed writing task dossier {}: {}", file, ex);
        }
    }

    /**
     * Updates the status line inside an existing task dossier Markdown file.
     *
     * @param task updated task item
     */
    private void updateTaskDossierStatusLocked(final TaskItem task) {
        final Path file = projectDir().resolve(TASKS_DIR).resolve(task.id() + ".md");
        if (!Files.isWritable(file)) {
            return;
        }
        try {
            final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            final List<String> rewritten = new ArrayList<>(lines.size() + 1);
            for (final String line : lines) {
                if (line.trim().startsWith("- **Status**:")) {
                    rewritten.add("- **Status**: `" + task.status().name() + "`");
                } else {
                    rewritten.add(line);
                }
            }
            final String today = ISO_DATE_FMT.format(Instant.now());
            rewritten.add("- `" + today + "` `[" + task.id() + "]` Status updated to `" + task.status().name() + "`.");
            Files.write(file, rewritten, StandardCharsets.UTF_8);
        } catch (final IOException ex) {
            logWarning("Failed updating task dossier {}: {}", file, ex);
        }
    }

    /**
     * Updates or inserts the task summary row in {@code tasks/README.md} and {@code TODO.md}.
     *
     * @param task target task item
     */
    private void upsertTaskIndexAndTodoLocked(final TaskItem task) {
        final Path tasksReadme = projectDir().resolve(TASKS_DIR).resolve(README_MD);
        if (Files.isWritable(tasksReadme)) {
            upsertMarkdownLine(
                    tasksReadme,
                    "[" + task.id() + "]",
                    "## Active & Planned Tasks",
                    "- [" + task.id() + "](" + task.id() + ".md) (`" + task.featureId()
                            + "` | `" + task.status().name() + "`): " + task.title());
        }
        final Path todoFile = workspaceRoot.resolve(DOC_DIR).resolve(DOCS_DIR).resolve(TODO_MD);
        if (Files.isWritable(todoFile)) {
            final String checkbox = task.status() == WorkItemStatus.DONE ? "[x]" : "[ ]";
            upsertMarkdownLine(
                    todoFile,
                    "[" + task.id() + "]",
                    "## Current Focus",
                    "- " + checkbox + " **[" + task.id() + "](project/tasks/" + task.id() + ".md)** "
                            + task.title() + " (`" + task.featureId() + "`)");
        }
    }

    /**
     * Replaces matching entry or inserts directly under {@code anchorHeading}.
     *
     * @param file          Markdown file path
     * @param matchToken    unique token identifying the entry line
     * @param anchorHeading heading under which new entries are inserted
     * @param newLine       formatted Markdown line
     */
    private void upsertMarkdownLine(
            final Path file,
            final String matchToken,
            final String anchorHeading,
            final String newLine) {
        try {
            final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            final List<String> rewritten = new ArrayList<>(lines.size() + 1);
            boolean replaced = false;
            boolean inserted = false;
            for (final String line : lines) {
                if (line.contains(matchToken)) {
                    rewritten.add(newLine);
                    replaced = true;
                } else {
                    rewritten.add(line);
                    if (!replaced && !inserted && line.trim().equals(anchorHeading)) {
                        rewritten.add(newLine);
                        inserted = true;
                    }
                }
            }
            if (!replaced && !inserted) {
                rewritten.add(newLine);
            }
            Files.write(file, rewritten, StandardCharsets.UTF_8);
        } catch (final IOException ex) {
            logWarning("Failed updating Markdown index {}: {}", file, ex);
        }
    }

    /**
     * Saves an activity document to MongoDB when configured without failing CLI/offline callers.
     *
     * @param record activity document to persist
     */
    private void persistActivityBestEffort(final ActivityRecord record) {
        if (activityRepository.isPresent()) {
            try {
                activityRepository.get().save(record);
            } catch (final RuntimeException ex) {
                logWarning("Failed storing activity record {} to MongoDB: {}", record.id(), ex);
            }
        }
    }

    /**
     * Resolves {@code doc/docs/project} directory path.
     *
     * @return resolved project directory path
     */
    private Path projectDir() {
        return workspaceRoot.resolve(DOC_DIR).resolve(DOCS_DIR).resolve(PROJECT_DIR);
    }

    /**
     * Resolves the requirement ID attached to a feature or falls back cleanly.
     *
     * @param featureId feature identifier
     * @return linked requirement ID
     */
    private String resolveFeatureRequirementLocked(final String featureId) {
        final FeatureItem feature = features.get(featureId);
        return feature == null ? REQ_PROJECT_WORKBENCH : feature.requirementId();
    }

    /**
     * Allocates the next sequential 5-digit identifier with the given prefix.
     *
     * @param existingIds set of currently registered identifiers
     * @param prefix      identifier prefix such as {@code FR-} or {@code TSK-}
     * @return next formatted identifier
     */
    private String allocateNextIdLocked(final Set<String> existingIds, final String prefix) {
        int max = 0;
        for (final String existing : existingIds) {
            if (existing.startsWith(prefix)) {
                try {
                    final int numeric = Integer.parseInt(existing.substring(prefix.length()));
                    if (numeric > max) {
                        max = numeric;
                    }
                } catch (final NumberFormatException ignored) {
                    // Ignore non-numeric legacy IDs.
                }
            }
        }
        return String.format("%s%05d", prefix, max + 1);
    }

    /**
     * Normalizes and validates an arbitrary work reference token.
     *
     * @param workRef candidate work reference string
     * @return trimmed uppercase identifier
     */
    private String normalizeWorkRef(final String workRef) {
        final String candidate = requireNonBlank(workRef, "workRef must not be blank").toUpperCase(Locale.ROOT);
        if (!WORK_REF_PATTERN.matcher(candidate).matches()) {
            throw new EniWorkshopException(
                    "Invalid work reference ID '" + candidate + "' (expected REQ/FR/TSK/BUG/FIX-XXXXX)");
        }
        return candidate;
    }

    /**
     * Collects all work reference tokens found in a Java file.
     *
     * @param content Java source text
     * @return unique set of matched work reference identifiers
     */
    private Set<String> extractMatches(final String content) {
        final Set<String> found = new HashSet<>();
        final Matcher matcher = WORK_REF_PATTERN.matcher(content);
        while (matcher.find()) {
            found.add(matcher.group(0));
        }
        return found;
    }

    /**
     * Checks references against known Feature and Task registries.
     *
     * @param refs          extracted work references
     * @param knownFeatures registered feature IDs
     * @param knownTasks    registered task IDs
     * @param relPath       relative Java file path
     * @param unknownRefs   collector list for unrecognized references
     */
    private void collectUnknownRefs(
            final Set<String> refs,
            final Set<String> knownFeatures,
            final Set<String> knownTasks,
            final String relPath,
            final List<String> unknownRefs) {
        for (final String ref : refs) {
            if (ref.startsWith("FR-") && !knownFeatures.isEmpty() && !knownFeatures.contains(ref)) {
                unknownRefs.add(ref + " (" + relPath + ")");
            } else if (ref.startsWith("TSK-") && !knownTasks.isEmpty() && !knownTasks.contains(ref)) {
                unknownRefs.add(ref + " (" + relPath + ")");
            }
        }
    }

    /**
     * Parses comma-separated task IDs from a Markdown table cell.
     *
     * @param rawCell table cell string
     * @return immutable list of parsed task identifiers
     */
    private List<String> parseTaskIdList(final String rawCell) {
        final String cleaned = stripTicks(rawCell);
        if (cleaned.isEmpty() || "-".equals(cleaned)) {
            return List.of();
        }
        final List<String> result = new ArrayList<>();
        for (final String part : cleaned.split(",")) {
            final String token = stripTicks(part);
            if (!token.isEmpty()) {
                result.add(token);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Maps text token to {@link WorkItemStatus} with safe {@code BACKLOG} fallback.
     *
     * @param raw status string
     * @return resolved {@link WorkItemStatus}
     */
    private WorkItemStatus parseStatus(final String raw) {
        final String normalized = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        for (final WorkItemStatus candidate : WorkItemStatus.values()) {
            if (candidate.name().equals(normalized)) {
                return candidate;
            }
        }
        return WorkItemStatus.BACKLOG;
    }

    /**
     * Strips wrapping backticks and whitespace from Markdown cells.
     *
     * @param value raw Markdown cell text
     * @return normalized cell value
     */
    private String stripTicks(final String value) {
        return value.replace(BACKTICK, EMPTY).trim();
    }

    /**
     * Validates a required identifier string against a regex pattern.
     *
     * @param value          candidate identifier
     * @param pattern        compiled validation pattern
     * @param expectedFormat human-readable format hint
     * @return validated identifier
     */
    private String validatePattern(final String value, final Pattern pattern, final String expectedFormat) {
        if (!pattern.matcher(value).matches()) {
            throw new EniWorkshopException("Invalid identifier '" + value + "', expected " + expectedFormat);
        }
        return value;
    }

    /**
     * Ensures string contains non-whitespace characters.
     *
     * @param value   input text
     * @param message exception message when blank
     * @return trimmed string
     */
    private String requireNonBlank(final String value, final String message) {
        if (isBlank(value)) {
            throw new EniWorkshopException(message);
        }
        return value.trim();
    }

    /**
     * Returns true when null or blank.
     *
     * @param value candidate string
     * @return {@code true} if {@code null} or blank
     */
    private boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    /**
     * Emits a guarded SLF4J warning message.
     *
     * @param message log format pattern with two placeholders
     * @param context contextual path or identifier
     * @param error   thrown exception
     */
    private void logWarning(final String message, final Object context, final Throwable error) {
        if (LOG.isWarnEnabled()) {
            LOG.warn(message, context, error.getMessage());
        }
    }
}
