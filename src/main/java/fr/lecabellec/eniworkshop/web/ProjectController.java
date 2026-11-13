package fr.lecabellec.eniworkshop.web;

import fr.lecabellec.eniworkshop.project.ProjectManagementService;
import fr.lecabellec.eniworkshop.project.ProjectModels.ActivityRecord;
import fr.lecabellec.eniworkshop.project.ProjectModels.ActivitySource;
import fr.lecabellec.eniworkshop.project.ProjectModels.CreateFeatureRequest;
import fr.lecabellec.eniworkshop.project.ProjectModels.CreateTaskRequest;
import fr.lecabellec.eniworkshop.project.ProjectModels.FeatureItem;
import fr.lecabellec.eniworkshop.project.ProjectModels.LogActivityRequest;
import fr.lecabellec.eniworkshop.project.ProjectModels.ProjectOverview;
import fr.lecabellec.eniworkshop.project.ProjectModels.TaskItem;
import fr.lecabellec.eniworkshop.project.ProjectModels.TraceabilityAudit;
import fr.lecabellec.eniworkshop.project.ProjectModels.WorkItemStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST API controller exposing Features ({@code FR-*}), Tasks ({@code TSK-*}),
 * activity attribution, active work-reference selection, and {@code CS-0010}
 * codebase traceability auditing.
 *
 * <p>Requirement: REQ-00014, Feature: FR-00014, Task: TSK-00014, Constraint: CS-0010.
 */
@RestController
@RequestMapping("/api/project")
@Tag(name = "Project & Task Management", description = "Features, Tasks, Activities, and Traceability Audit")
public final class ProjectController {

    /** Default maximum activities returned by GET queries. */
    private static final int DEFAULT_LIMIT = 100;

    /** Project governance & activity service. */
    private final ProjectManagementService projectService;

    /**
     * Constructs a {@link ProjectController}.
     *
     * @param projectService project management service
     */
    @Autowired
    public ProjectController(final ProjectManagementService projectService) {
        this.projectService = Objects.requireNonNull(projectService, "projectService must not be null");
    }

    /**
     * Returns full project overview combining features, tasks, activities, active work ref, and audit.
     *
     * @return immutable {@link ProjectOverview}
     */
    @GetMapping("/overview")
    @Operation(summary = "Get complete project overview, features, tasks, activity feed, and CS-0010 audit")
    public ProjectOverview getOverview() {
        return projectService.getOverview();
    }

    /**
     * Lists all tracked Features.
     *
     * @return immutable list of {@link FeatureItem}
     */
    @GetMapping("/features")
    @Operation(summary = "List tracked Features (FR-XXXXX)")
    public List<FeatureItem> listFeatures() {
        return projectService.listFeatures();
    }

    /**
     * Creates a new Feature item.
     *
     * @param request feature creation payload
     * @return created {@link FeatureItem}
     */
    @PostMapping("/features")
    @Operation(summary = "Create a new Feature (FR-XXXXX)")
    public FeatureItem createFeature(@RequestBody final CreateFeatureRequest request) {
        return projectService.createFeature(request, ActivitySource.WEB);
    }

    /**
     * Updates a Feature lifecycle status.
     *
     * @param featureId target feature ID
     * @param body      map containing {@code status}
     * @return updated {@link FeatureItem}
     */
    @PatchMapping("/features/{featureId}/status")
    @Operation(summary = "Update Feature lifecycle status")
    public FeatureItem updateFeatureStatus(
            @PathVariable("featureId") final String featureId,
            @RequestBody final Map<String, String> body) {
        final WorkItemStatus status = WorkItemStatus.valueOf(
                Objects.requireNonNull(body.get("status"), "status field is required"));
        return projectService.updateFeatureStatus(featureId, status, ActivitySource.WEB);
    }

    /**
     * Lists all tracked Tasks.
     *
     * @return immutable list of {@link TaskItem}
     */
    @GetMapping("/tasks")
    @Operation(summary = "List tracked Tasks (TSK-XXXXX)")
    public List<TaskItem> listTasks() {
        return projectService.listTasks();
    }

    /**
     * Creates a new Task dossier and registers it in {@code TODO.md}.
     *
     * @param request task creation payload
     * @return created {@link TaskItem}
     */
    @PostMapping("/tasks")
    @Operation(summary = "Create a new Task dossier (TSK-XXXXX)")
    public TaskItem createTask(@RequestBody final CreateTaskRequest request) {
        return projectService.createTask(request, ActivitySource.WEB);
    }

    /**
     * Updates a Task lifecycle status and syncs Markdown dossier / TODO board.
     *
     * @param taskId target task ID
     * @param body   map containing {@code status}
     * @return updated {@link TaskItem}
     */
    @PatchMapping("/tasks/{taskId}/status")
    @Operation(summary = "Update Task lifecycle status")
    public TaskItem updateTaskStatus(
            @PathVariable("taskId") final String taskId,
            @RequestBody final Map<String, String> body) {
        final WorkItemStatus status = WorkItemStatus.valueOf(
                Objects.requireNonNull(body.get("status"), "status field is required"));
        return projectService.updateTaskStatus(taskId, status, ActivitySource.WEB);
    }

    /**
     * Updates the currently active Task or Feature identifier for workbench attribution.
     *
     * @param body map containing {@code workRefId}
     * @return normalized active reference payload
     */
    @PutMapping("/active-ref")
    @Operation(summary = "Set the active Task/Feature ID for automatic workbench tracking")
    public Map<String, String> setActiveWorkRef(@RequestBody final Map<String, String> body) {
        final String updated = projectService.setActiveWorkRef(body.get("workRefId"), ActivitySource.WEB);
        return Map.of("activeWorkRef", updated);
    }

    /**
     * Lists recent activity records optionally filtered by {@code workRef}.
     *
     * @param workRef optional Task/Feature filter
     * @param limit   max entries
     * @return chronological list of {@link ActivityRecord}
     */
    @GetMapping("/activities")
    @Operation(summary = "List tracked workbench and CLI activities")
    public List<ActivityRecord> listActivities(
            @RequestParam(value = "workRef", required = false) final String workRef,
            @RequestParam(value = "limit", required = false) final Integer limit) {
        return projectService.listActivities(workRef, limit == null ? DEFAULT_LIMIT : limit);
    }

    /**
     * Manually logs a tracked engineering activity under a Task or Feature ID.
     *
     * @param request activity log payload
     * @return recorded {@link ActivityRecord}
     */
    @PostMapping("/activities")
    @Operation(summary = "Log an activity attributed to a Task or Feature")
    public ActivityRecord logActivity(@RequestBody final LogActivityRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return projectService.recordActivity(
                request.workRefId(),
                request.action(),
                request.target(),
                request.summary(),
                ActivitySource.WEB);
    }

    /**
     * Runs an on-demand {@code CS-0010} Java traceability audit against the workspace.
     *
     * @return {@link TraceabilityAudit} report
     */
    @GetMapping("/traceability")
    @Operation(summary = "Run CS-0010 Java codebase traceability audit")
    public TraceabilityAudit auditTraceability() {
        return projectService.auditCodebaseTraceability();
    }
}
