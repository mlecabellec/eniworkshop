package fr.lecabellec.eniworkshop.project;

import fr.lecabellec.eniworkshop.project.ProjectModels.ActivityRecord;
import fr.lecabellec.eniworkshop.project.ProjectModels.ActivitySource;
import fr.lecabellec.eniworkshop.project.ProjectModels.CreateFeatureRequest;
import fr.lecabellec.eniworkshop.project.ProjectModels.CreateTaskRequest;
import fr.lecabellec.eniworkshop.project.ProjectModels.FeatureItem;
import fr.lecabellec.eniworkshop.project.ProjectModels.ProjectOverview;
import fr.lecabellec.eniworkshop.project.ProjectModels.TaskItem;
import fr.lecabellec.eniworkshop.project.ProjectModels.TraceabilityAudit;
import fr.lecabellec.eniworkshop.project.ProjectModels.WorkItemStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProjectManagementService} covering Markdown governance sync,
 * status transitions, activity tracking, and {@code CS-0010} codebase audits.
 *
 * <p>Requirement: REQ-00014, Feature: FR-00014, Task: TSK-00014, Constraint: CS-0010.
 */
final class ProjectManagementServiceTest {

    @Test
    void loadsCreatesTransitionsAndAuditsInsideIsolatedWorkspace(@TempDir final Path tempRoot) throws Exception {
        final Path docsDir = tempRoot.resolve("doc").resolve("docs");
        final Path projectDir = docsDir.resolve("project");
        final Path featuresDir = projectDir.resolve("features");
        final Path tasksDir = projectDir.resolve("tasks");
        final Path srcMain = tempRoot.resolve("src").resolve("main").resolve("java").resolve("demo");
        Files.createDirectories(featuresDir);
        Files.createDirectories(tasksDir);
        Files.createDirectories(srcMain);

        Files.writeString(
                featuresDir.resolve("README.md"),
                "# Feature Registry\n\n"
                        + "| Feature ID | Title | Requirement | Primary Tasks | Status | Package / UI Scope |\n"
                        + "| :--- | :--- | :--- | :--- | :--- | :--- |\n"
                        + "| `FR-00013` | Corpus Alignment | `REQ-00013` | `TSK-00013` | `DONE` | `pom` |\n",
                StandardCharsets.UTF_8);

        Files.writeString(
                tasksDir.resolve("TSK-00013.md"),
                "# [TSK-00013] Corpus Alignment\n\n"
                        + "- **Feature**: `FR-00013`\n"
                        + "- **Requirement**: `REQ-00013`\n"
                        + "- **Status**: `DONE`\n\n"
                        + "## Goal\n"
                        + "Align Maven plugins and extended corpus.\n",
                StandardCharsets.UTF_8);

        Files.writeString(
                tasksDir.resolve("README.md"),
                "# Project Tasks\n\n## Active & Planned Tasks\n"
                        + "- [TSK-00013](TSK-00013.md) (`FR-00013` | `DONE`): Corpus Alignment\n",
                StandardCharsets.UTF_8);

        Files.writeString(
                docsDir.resolve("TODO.md"),
                "# TODO\n\n## Current Focus\n"
                        + "- [x] **[TSK-00013](project/tasks/TSK-00013.md)** Corpus Alignment (`FR-00013`)\n",
                StandardCharsets.UTF_8);

        Files.writeString(
                srcMain.resolve("TaggedService.java"),
                "/** Requirement: REQ-00014, Feature: FR-00014, Task: TSK-00014. */\nclass TaggedService {}\n",
                StandardCharsets.UTF_8);
        Files.writeString(
                srcMain.resolve("MissingTagService.java"),
                "/** Missing traceability tags. */\nclass MissingTagService {}\n",
                StandardCharsets.UTF_8);

        final ProjectManagementService service = new ProjectManagementService(null, tempRoot);

        final ProjectOverview initial = service.getOverview();
        assertThat(initial.features()).hasSize(1);
        assertThat(initial.tasks()).hasSize(1);

        final FeatureItem createdFeature = service.createFeature(
                new CreateFeatureRequest(null, "Governance Workbench", "REQ-00014", "project"),
                ActivitySource.CLI);
        assertThat(createdFeature.id()).isEqualTo("FR-00014");
        assertThat(Files.readString(featuresDir.resolve("README.md"))).contains("FR-00014");

        final TaskItem createdTask = service.createTask(
                new CreateTaskRequest(null, "Track Activity", "FR-00014", null, "Track all actions."),
                ActivitySource.WEB);
        assertThat(createdTask.id()).isEqualTo("TSK-00014");
        assertThat(Files.isRegularFile(tasksDir.resolve("TSK-00014.md"))).isTrue();
        assertThat(Files.readString(docsDir.resolve("TODO.md"))).contains("[TSK-00014]");

        final TaskItem doneTask = service.updateTaskStatus("TSK-00014", WorkItemStatus.DONE, ActivitySource.WEB);
        assertThat(doneTask.status()).isEqualTo(WorkItemStatus.DONE);
        assertThat(Files.readString(tasksDir.resolve("TSK-00014.md"))).contains("`DONE`");
        assertThat(Files.readString(docsDir.resolve("TODO.md"))).contains("- [x] **[TSK-00014]");

        final List<ActivityRecord> filtered = service.listActivities("TSK-00014", 20);
        assertThat(filtered).hasSize(2);

        final TraceabilityAudit audit = service.auditCodebaseTraceability();
        assertThat(audit.totalJavaFiles()).isEqualTo(2);
        assertThat(audit.tracedJavaFiles()).isEqualTo(1);
        assertThat(audit.untaggedFiles()).containsExactly("src/main/java/demo/MissingTagService.java");
        assertThat(audit.unknownWorkRefs()).isEmpty();
    }
}
