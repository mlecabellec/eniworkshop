# Project, Task & Activity Governance

This directory implements the **ENI Workshop** project management workflow synthesized from **Nunki** (`TODO.md`, requirements registry) and **Quasar** (`doc/docs/project/{tasks,milestones,reviews}`).

## Core Principle: Universal Task & Feature Attribution

> **All engineering activities—code changes, CLI runs, ENI imports/modifications, comparisons, reviews, and Quasar hardware sessions—shall be tracked by an explicit Task (`TSK-XXXXX`) or Feature (`FR-XXXXX`).**

## Artifact Hierarchy

1. **Requirements (`REQ-XXXXX`)** — Defined in [Requirements & Tasks Registry](../misc/requirements.md).
2. **Features (`FR-XXXXX`)** — Managed in [Features Registry](features/README.md) with business scope, linked requirements, owning tasks, and verification status.
3. **Tasks (`TSK-XXXXX`)** — Tracked as individual Markdown dossiers under [tasks/](tasks/README.md) using Quasar's structured sections: `Goal`, `Linked Feature`, `Implementation Plan`, `Test and Validation`, `Status`, and `Activity Log`.
4. **Milestones** — Tracked in [milestones/README.md](milestones/README.md).
5. **Architecture & Code Reviews (`REVIEW-XXXXX`)** — Archived by `YYYY/MM` under [reviews/](reviews/README.md).

## Workflow States

```text
BACKLOG  --->  IN_PROGRESS  --->  IN_REVIEW  --->  DONE
                   |
                   +---> BLOCKED ---> IN_PROGRESS
```

## Runtime & CI Enforcement

- **HTTP / Web Workbench**: `X-Eni-Work-Ref` header (or active work-item selector in UI) associates REST operations (`IMPORT`, `DUPLICATE`, `COMPARE`, `QUASAR_SCAN`, etc.) with a `TSK-*` or `FR-*` entry.
- **Picocli CLI**: `--cli project [list|create-task|create-feature|set-status|log|audit]` manages items and records CLI activities.
- **Static Traceability Check**: `helpers/check-traceability.py` verifies source Javadoc traceability tags against [CS-0010](../architecture/CS-0010.md).
