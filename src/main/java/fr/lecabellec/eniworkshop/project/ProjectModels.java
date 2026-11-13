package fr.lecabellec.eniworkshop.project;

import java.util.List;
import java.util.Objects;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Immutable domain models for Features ({@code FR-XXXXX}), Tasks ({@code TSK-XXXXX}),
 * tracked {@link ActivityRecord} entries, and {@link TraceabilityAudit} reports.
 *
 * <p>Requirement: REQ-00014, Feature: FR-00014, Task: TSK-00014, Constraint: CS-0010, CS-0030.2.
 */
public final class ProjectModels {

    /** Private constructor preventing instantiation. */
    private ProjectModels() {
        // Intentionally empty.
    }

    /**
     * Lifecycle status for project features and engineering tasks.
     */
    public enum WorkItemStatus {
        /** Planned item awaiting execution. */
        BACKLOG,
        /** Actively worked item. */
        IN_PROGRESS,
        /** Implementation complete and under peer/CI review. */
        IN_REVIEW,
        /** Blocked pending external dependency or bench hardware. */
        BLOCKED,
        /** Verified and closed. */
        DONE
    }

    /**
     * Execution channel originating a tracked activity.
     */
    public enum ActivitySource {
        /** Web workbench HTTP/REST request. */
        WEB,
        /** Headless Picocli CLI subcommand. */
        CLI,
        /** Internal system or startup audit event. */
        SYSTEM
    }

    /**
     * Structured Feature record loaded from and synchronized with {@code doc/docs/project/features/README.md}.
     *
     * @param id            feature identifier (e.g., {@code FR-00014})
     * @param title         concise feature title
     * @param requirementId linked requirement identifier (e.g., {@code REQ-00014})
     * @param taskIds       owning task identifiers
     * @param status        current lifecycle state
     * @param scope         code packages or UI views implementing the feature
     */
    public record FeatureItem(
        String id,
        String title,
        String requirementId,
        List<String> taskIds,
        WorkItemStatus status,
        String scope
    ) {

        /** Compact constructor enforcing non-null fields and immutable list copy. */
        public FeatureItem {
            Objects.requireNonNull(id, "feature id must not be null");
            Objects.requireNonNull(title, "feature title must not be null");
            Objects.requireNonNull(requirementId, "requirementId must not be null");
            taskIds = List.copyOf(Objects.requireNonNull(taskIds, "taskIds must not be null"));
            Objects.requireNonNull(status, "feature status must not be null");
            Objects.requireNonNull(scope, "feature scope must not be null");
        }
    }

    /**
     * Structured Task dossier modeled on Quasar {@code TSK-*.md} and Nunki {@code TODO.md}.
     *
     * @param id            task identifier (e.g., {@code TSK-00014})
     * @param title         task title
     * @param featureId     linked feature identifier (e.g., {@code FR-00014})
     * @param requirementId linked requirement identifier (e.g., {@code REQ-00014})
     * @param status        current task lifecycle status
     * @param goal          concise goal description
     */
    public record TaskItem(
        String id,
        String title,
        String featureId,
        String requirementId,
        WorkItemStatus status,
        String goal
    ) {

        /** Compact constructor enforcing non-null fields. */
        public TaskItem {
            Objects.requireNonNull(id, "task id must not be null");
            Objects.requireNonNull(title, "task title must not be null");
            Objects.requireNonNull(featureId, "featureId must not be null");
            Objects.requireNonNull(requirementId, "requirementId must not be null");
            Objects.requireNonNull(status, "task status must not be null");
            Objects.requireNonNull(goal, "task goal must not be null");
        }
    }

    /**
     * Persistent audit entry attributing every engineering, CLI, or ENI action to a Task or Feature.
     *
     * @param id        unique event identifier
     * @param workRefId task ({@code TSK-XXXXX}) or feature ({@code FR-XXXXX}) identifier
     * @param action    action verb (e.g., {@code ENI_IMPORT}, {@code CLI_COMPARE}, {@code TASK_STATUS})
     * @param target    target catalog ID, file path, or resource key
     * @param summary   human-readable activity summary
     * @param source    originating execution channel
     * @param timestamp ISO-8601 UTC instant string
     */
    @Document("project_activities")
    public record ActivityRecord(
        @Id String id,
        String workRefId,
        String action,
        String target,
        String summary,
        ActivitySource source,
        String timestamp
    ) {

        /** Compact constructor enforcing non-null invariants. */
        public ActivityRecord {
            Objects.requireNonNull(id, "activity id must not be null");
            Objects.requireNonNull(workRefId, "workRefId must not be null");
            Objects.requireNonNull(action, "action must not be null");
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(summary, "summary must not be null");
            Objects.requireNonNull(source, "source must not be null");
            Objects.requireNonNull(timestamp, "timestamp must not be null");
        }
    }

    /**
     * Live Java source traceability summary verifying CS-0010 compliance.
     *
     * @param totalJavaFiles       total scanned non-package-info Java files
     * @param tracedJavaFiles      files citing a REQ/FR/TSK/BUG/FIX/CS identifier
     * @param coveragePercent      percentage of traced Java files
     * @param untaggedFiles        relative paths missing traceability annotations
     * @param unknownWorkRefs      identifiers found in code lacking doc/docs/project registry entries
     */
    public record TraceabilityAudit(
        int totalJavaFiles,
        int tracedJavaFiles,
        double coveragePercent,
        List<String> untaggedFiles,
        List<String> unknownWorkRefs
    ) {

        /** Compact constructor copying mutable lists defensively. */
        public TraceabilityAudit {
            untaggedFiles = List.copyOf(Objects.requireNonNull(untaggedFiles, "untaggedFiles must not be null"));
            unknownWorkRefs = List.copyOf(Objects.requireNonNull(unknownWorkRefs, "unknownWorkRefs must not be null"));
        }
    }

    /**
     * Unified dashboard overview payload served to the Workbench UI and CLI.
     *
     * @param activeWorkRef currently selected default Task or Feature attribution ID
     * @param features      all tracked features
     * @param tasks         all tracked tasks
     * @param activities    most recent activity records (newest first)
     * @param traceability  live CS-0010 codebase traceability report
     */
    public record ProjectOverview(
        String activeWorkRef,
        List<FeatureItem> features,
        List<TaskItem> tasks,
        List<ActivityRecord> activities,
        TraceabilityAudit traceability
    ) {

        /** Compact constructor copying collections defensively. */
        public ProjectOverview {
            Objects.requireNonNull(activeWorkRef, "activeWorkRef must not be null");
            features = List.copyOf(Objects.requireNonNull(features, "features must not be null"));
            tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks must not be null"));
            activities = List.copyOf(Objects.requireNonNull(activities, "activities must not be null"));
            Objects.requireNonNull(traceability, "traceability must not be null");
        }
    }

    /**
     * Payload to register a new Feature ({@code FR-XXXXX}).
     *
     * @param id            optional explicit ID (auto-assigned next {@code FR-XXXXX} if blank)
     * @param title         feature title
     * @param requirementId linked {@code REQ-XXXXX} identifier
     * @param scope         target packages or views
     */
    public record CreateFeatureRequest(
        String id,
        String title,
        String requirementId,
        String scope
    ) {
    }

    /**
     * Payload to create a new Task ({@code TSK-XXXXX}) linked to a Feature.
     *
     * @param id            optional explicit ID (auto-assigned next {@code TSK-XXXXX} if blank)
     * @param title         task title
     * @param featureId     linked {@code FR-XXXXX} identifier
     * @param requirementId linked {@code REQ-XXXXX} identifier
     * @param goal          task objective
     */
    public record CreateTaskRequest(
        String id,
        String title,
        String featureId,
        String requirementId,
        String goal
    ) {
    }

    /**
     * Payload to record an explicit activity linked to a Task or Feature.
     *
     * @param workRefId task or feature ID
     * @param action    action name
     * @param target    target entity or file
     * @param summary   activity summary
     */
    public record LogActivityRequest(
        String workRefId,
        String action,
        String target,
        String summary
    ) {
    }
}
