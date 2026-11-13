# Feature Registry (`FR-XXXXX`)

All first-party ENI Workshop capabilities are indexed as structured Features linked to Requirements (`REQ-XXXXX`) and Tasks (`TSK-XXXXX`).

| Feature ID | Title | Requirement | Primary Tasks | Status | Package / UI Scope |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `FR-00001` | Dual-Mode Platform, Apache 2.0 & Quality Gates | `REQ-00001` | `TSK-00001` | `DONE` | `fr.lecabellec.eniworkshop`, `checkstyle.xml`, `pmd-ruleset.xml`, `doc/` |
| `FR-00002` | Streaming StAX ETG.2100 ENI Parser & Writer | `REQ-00002` | `TSK-00002` | `DONE` | `fr.lecabellec.eniworkshop.eni.model`, `eni.xml` |
| `FR-00003` | EtherCAT Physics, Address, SM & DC Rules Engine | `REQ-00003` | `TSK-00003` | `DONE` | `fr.lecabellec.eniworkshop.eni.rules` |
| `FR-00004` | Slave/Extension Catalog & ETG.2000 ESI Importer | `REQ-00004` | `TSK-00004` | `DONE` | `fr.lecabellec.eniworkshop.catalog` |
| `FR-00005` | Hierarchical Topology Composer & Cable Redundancy | `REQ-00005` | `TSK-00005` | `DONE` | `fr.lecabellec.eniworkshop.topology` |
| `FR-00006` | Hybrid MongoDB Metadata & GridFS (>16 MB) Versioning | `REQ-00006` | `TSK-00006` | `DONE` | `fr.lecabellec.eniworkshop.persistence` |
| `FR-00007` | PlantUML ASL, EtherCAT Frame Byte-Map & CSV Workbench | `REQ-00007` | `TSK-00007` | `DONE` | `fr.lecabellec.eniworkshop.diagram`, `eni.xml.EniCsvService` |
| `FR-00008` | Quasar (`resoem`/`resoes`) OPC-UA & ZeroMQ Integration | `REQ-00008` | `TSK-00008`, `TSK-00015` | `IN_PROGRESS` | `fr.lecabellec.eniworkshop.quasar`, `quasar_server/` |
| `FR-00009` | OpenAPI REST, STOMP WebSocket, Security & Picocli CLI | `REQ-00009` | `TSK-00009` | `DONE` | `fr.lecabellec.eniworkshop.web`, `cli`, `messaging`, `security` |
| `FR-00010` | Svelte 5 + Dockview Multi-Panel Engineering UI | `REQ-00010` | `TSK-00010` | `DONE` | `src/main/frontend` (`TopologyView`, `TreeView`, `FormView`, `TableCsvView`, `FrameMapView`, `PlantUmlView`, `CatalogQuasarView`) |
| `FR-00011` | Multi-Scope Comparison (ENI, Topology, Slave, HotConnect) | `REQ-00011` | `TSK-00011` | `DONE` | `fr.lecabellec.eniworkshop.compare`, `CompareController`, `CompareView.svelte` |
| `FR-00012` | Podman OCI Stack, Testcontainers Persistence & CI Workflows | `REQ-00012` | `TSK-00012` | `DONE` | `Containerfile`, `helpers/*.sh`, `helpers/podman-compose.yml`, `.github/workflows/`, `ContainerPersistenceIT` |
| `FR-00013` | Nunki Maven Plugin Alignment & Extended Corpus (`IPSE1_v2`, `TTE`) | `REQ-00013` | `TSK-00013` | `DONE` | `pom.xml` (`actuator`, `luaj-jse`, `jaxb`, `shade`), `src/test/resources/eni/` |
| `FR-00014` | Task, Feature & Universal Activity Tracking Workbench | `REQ-00014` | `TSK-00014` | `DONE` | `fr.lecabellec.eniworkshop.project`, `ProjectController`, `ProjectTasksView.svelte`, `doc/docs/project/`, `helpers/check-traceability.py` |
