# Components Index

| Component | Package / Path | Responsibility | Traceability |
| :--- | :--- | :--- | :--- |
| `EniWorkshopApplication` | `fr.lecabellec.eniworkshop` | Dual-mode Web + Picocli CLI bootstrap | `REQ-00001`, `FR-00001` |
| `EniDocument` & AST | `fr.lecabellec.eniworkshop.eni.model` | ETG.2100 ENI model (Master, Slave, Cyclic, ProcessImage, DC, HotConnect, Redundancy) | `REQ-00002`, `FR-00002` |
| `EniXmlParser` / `EniXmlWriter` | `fr.lecabellec.eniworkshop.eni.xml` | Streaming StAX ENI import & export (>50 MB safe) | `REQ-00002`, `FR-00002` |
| `EniValidationEngine` | `fr.lecabellec.eniworkshop.eni.rules` | ETG.1000 / ETG.2100 management & consistency rules | `REQ-00003`, `FR-00003` |
| `SlaveCatalogService` | `fr.lecabellec.eniworkshop.catalog` | Slave & extension card catalog + unknown device discovery + ESI import | `REQ-00004`, `FR-00004` |
| `TopologyAggregationService` | `fr.lecabellec.eniworkshop.topology` | Composable bus design, address/offset recalculation, redundant rings | `REQ-00005`, `FR-00005` |
| `EniRepositoryService` | `fr.lecabellec.eniworkshop.persistence` | Hybrid MongoDB metadata + GridFS XML storage & versioning | `REQ-00006`, `FR-00006` |
| `PlantUmlService` | `fr.lecabellec.eniworkshop.diagram` | Apache-2.0 PlantUML ASL SVG generation for topologies & sequences | `REQ-00007`, `FR-00007` |
| `QuasarEthercatBridge` | `fr.lecabellec.eniworkshop.quasar` | Eclipse Milo OPC-UA & ZeroMQ bridge to Quasar (`resoem`/`resoes`) | `REQ-00008`, `FR-00008` |
| `EniWorkbenchController` & STOMP | `fr.lecabellec.eniworkshop.web` | REST OpenAPI endpoints + STOMP live updates | `REQ-00009`, `FR-00009` |
| `EniComparisonService` & `CompareController` | `fr.lecabellec.eniworkshop.compare`, `web` | Outer-join, side-by-side tree, and linear bus diff across ENIs, topologies, slaves, and HotConnect groups | `REQ-00011`, `FR-00011` |
| `ProjectManagementService` & `ProjectController` | `fr.lecabellec.eniworkshop.project`, `web` | Nunki/Quasar task & feature registries, Markdown/Mongo activity tracking (`X-Eni-Work-Ref`, CLI), and `CS-0010` traceability audit | `REQ-00014`, `FR-00014`, `TSK-00014` |
| `Svelte 5 Workbench` | `src/main/frontend` | Dockview multi-panel UI (Topology, Tree, Form, Table/CSV, Frame Map, PlantUML, Compare, Tasks & Features) | `REQ-00010`, `FR-00010`, `FR-00011`, `FR-00014` |
| `Containerfile` & `helpers/` | `Containerfile`, `helpers/` | Rootless Podman/Docker OCI build, compose stack (`mongodb` + `eniworkshop` + `quasar`), Testcontainers runner | `CS-0090` |
| `quasar_server` | `quasar_server/` | C++ helper bridging Quasar `OpcUaServerService` + `resoem`/`resoes` | `REQ-00008`, `FR-00008` |
