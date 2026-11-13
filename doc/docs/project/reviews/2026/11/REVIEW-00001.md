# REVIEW-00001 — ENI Workshop Baseline, Comparison, Containers & Project Governance Audit

- **[REVIEW-001.1]** Reference: `REVIEW-00001` (`2026-11-13`)
- **[REVIEW-001.2]** Title & Purpose: Comprehensive architecture, corpus parsing, Podman container testing, and governance audit across `eniworkshop`.
- **[REVIEW-001.3]** Reviewed Code & Deliverables:
  - `src/main/java/fr/lecabellec/eniworkshop/{eni,catalog,topology,persistence,diagram,compare,quasar,web,cli,project}/`
  - `src/main/frontend/src/components/` (`TopologyView`, `TreeView`, `FormView`, `TableCsvView`, `FrameMapView`, `PlantUmlView`, `CatalogQuasarView`, `CompareView`, `ProjectTasksView`)
  - `Containerfile`, `helpers/podman-compose.yml`, `helpers/*.sh`, `.github/workflows/*.yml`, `quasar_server/`
- **[REVIEW-001.4]** Reviewed Features & Constraints:
  - Features: `FR-00001` through `FR-00014`
  - Constraints: `CS-0010`..`CS-0100`, Apache-2.0 license compliance across 135 npm packages and Maven dependencies.
- **[REVIEW-001.5]** Issues Found During Review:
  1. `EniWorkshopApplication.runCli` initially triggered MongoDB auto-index creation against `localhost:27017` when running packaged JAR `--cli` offline.
  2. `helpers/run-container-tests.sh` needed pre-existing `DOCKER_HOST` unix socket liveness probing under rootless Podman.
  3. `EniWorkshopCliCommand` Javadoc cited legacy string `REQ-CLI-001` instead of numeric `CS-0010.2` reference.
- **[REVIEW-001.6]** Issues Resolved During Review:
  1. Excluded Mongo/Kafka auto-configurations in `runCli` (`CLI_EXCLUDED_AUTOCONFIG`) and made `auto-index-creation` configurable in `application.yml`.
  2. Updated `run-container-tests.sh` with `wait_for_socket` and `systemctl --user start podman.socket` / `podman system service` fallback; verified `ContainerPersistenceIT` against `mongo:7.0.14`.
  3. Added `CS-0010` / `FR-00009` / `TSK-00009` citations and `helpers/check-traceability.py`.
- **[REVIEW-001.7]** Open Issues Not Resolved:
  - `TSK-00015` (physical live EtherCAT hardware timing & WKC measurement suite with Quasar Master) awaits hardware bench scheduling (`BACKLOG`).
- **[REVIEW-001.8]** Future Review Items:
  - Verify `package.yml` multi-OS CI jobs and hardware Quasar round-trip benchmarks once bench hardware is attached.
