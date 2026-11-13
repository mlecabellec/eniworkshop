# Requirements, Features & Tasks Registry

Traceability registry compliant with **CS-0010** and **CS-0020**.

## Requirements (`REQ-XXXXX`)

| ID | Title | Description |
| :--- | :--- | :--- |
| `REQ-00001` | Dual-Mode CLI & Web App | Single Maven Spring Boot + Picocli executable JAR supporting CLI and Web UI modes. |
| `REQ-00002` | Complete ENI XML Engine | Parse, analyze, and export ETG.2100 ENI XML files including >50 MB configurations (`DOR.xml`, `IPSE1.xml`, `IPSO1.xml`). |
| `REQ-00003` | EtherCAT Rules Engine | Validate topology physics, addresses, SM/FMMU, PDOs, cyclic frames, mailboxes, DC, HotConnect, and redundant loops. |
| `REQ-00004` | Slave & Extension Card Catalog | Maintain reusable slave/module catalog; auto-discover unknown devices on ENI import; support ESI XML import. |
| `REQ-00005` | Bus Designer & Topology Aggregation | Compose custom buses and aggregate sub-topologies with automatic address/PDO recalculation and ring redundancy. |
| `REQ-00006` | Hybrid MongoDB Persistence & Versioning | Store ENI catalog metadata, versions (duplicate/rename/modify), topologies, and GridFS XML blobs (>16 MB safe). |
| `REQ-00007` | PlantUML & Diagram Generation | Backend Apache-2.0 PlantUML ASL + frontend PlantUML JS, network topology, and EtherCAT frame byte-map diagrams. |
| `REQ-00008` | Quasar (`resoem`/`resoes`) Integration | Eclipse Milo OPC-UA + ZeroMQ integration + `quasar_server/` C++ helper for live scan & ENI testing. |
| `REQ-00009` | REST, STOMP, OpenAPI & Messaging | Full OpenAPI/Swagger REST API, STOMP WebSocket real-time events, JeroMQ, and optional Kafka messaging. |
| `REQ-00010` | Dynamic Svelte 5 Workbench | Dockable multi-panel Svelte 5 UI combining Topology, Tree, Forms, Dynamic Tables (CSV), Frame Map, and PlantUML. |
| `REQ-00011` | Multi-Scope Comparison & Governance | Compare 2 ENIs, topologies, slaves, or HotConnect groups via trees, linear buses, and outer-join diff tables. |
| `REQ-00012` | Podman Containers & CI Automation | Multi-stage OCI Containerfile, Compose stack, Testcontainers persistence ITs, and GitHub CI/Docs/Package workflows. |
| `REQ-00013` | Nunki Alignment & Extended ENI Corpus | Align Maven capabilities (`actuator`, `luaj-jse`, `jaxb`, `shade`) and integrate `IPSE1_v2` & `TTE` ENI samples. |
| `REQ-00014` | Task, Feature & Activity Tracking | Track features, tasks, milestones, reviews, and tie all engineering/CLI/Web activities to `TSK-*` or `FR-*` identifiers. |

## Features (`FR-XXXXX`) & Tasks (`TSK-XXXXX`)

| Feature | Task | Scope |
| :--- | :--- | :--- |
| `FR-00001` | `TSK-00001` | Project initialization, Apache 2.0 `LICENSE`, `pom.xml`, `checkstyle.xml`, `pmd-ruleset.xml`, `mkdocs-kit` docs |
| `FR-00002` | `TSK-00002` | ETG.2100 ENI AST, streaming StAX `EniXmlParser` & `EniXmlWriter`, `DOR`/`IPSE1`/`IPSO1` + corpus verification |
| `FR-00003` | `TSK-00003` | ETG.1000/ETG.2100 `EniValidationEngine` (Addressing, Physics, SM, PDO, Cyclic MTU, DC, HotConnect, Redundancy) |
| `FR-00004` | `TSK-00004` | `SlaveCatalogService` (auto-discovery from ENIs + ETG.2000 ESI XML parser) |
| `FR-00005` | `TSK-00005` | `TopologyAggregationService` (compose/aggregate sub-topologies, re-index, recalculate cyclic frames & ring loops) |
| `FR-00006` | `TSK-00006` | MongoDB & GridFS persistence + versioning (duplicate, rename, branch/bump version) |
| `FR-00007` | `TSK-00007` | `PlantUmlService` (`plantuml-asl`), CSV importer/exporter, and EtherCAT frame byte-map analyzer |
| `FR-00008` | `TSK-00008` | Quasar OPC-UA + ZeroMQ bridge (`QuasarEthercatBridge`) & `quasar_server/` C++ helper |
| `FR-00009` | `TSK-00009` | REST OpenAPI controllers, STOMP WebSocket broker, Security profiles, Picocli CLI commands |
| `FR-00010` | `TSK-00010` | Svelte 5 + Vite + Dockview + XYFlow + Cytoscape + Tabulator/PapaParse + Frame Byte-Map + PlantUML UI |
| `FR-00011` | `TSK-00011` | `EniComparisonService`, `CompareController`, `--cli compare`, and `CompareView.svelte` |
| `FR-00012` | `TSK-00012` | `Containerfile`, `helpers/podman-compose.yml`, `ContainerPersistenceIT`, and `.github/workflows/` |
| `FR-00013` | `TSK-00013` | `nunki/pom.xml` alignment (`actuator`, `luaj-jse`, `jaxb`, `shade`), `IPSE1_v2.xml.gz`, `TTE.xml.gz` |
| `FR-00014` | `TSK-00014` | `doc/docs/project/`, `ProjectManagementService`, `--cli project`, `ProjectTasksView.svelte`, `check-traceability.py` |
