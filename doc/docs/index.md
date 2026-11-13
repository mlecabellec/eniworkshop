# ENI Workshop (`eniworkshop`)

**ENI Workshop** is an Apache 2.0 licensed, Java 21 Spring Boot + Picocli (Web & CLI) and Svelte 5 engineering workbench for **EtherCAT Network Information (ETG.2100 ENI)** and **EtherCAT Slave Information (ETG.2000 ESI)** analysis, catalog management, bus topology design, validation, and live testing via **Quasar** (`resoem` / `resoes` / `OpcUaServerService`).

---

## Core Capabilities

- **Dual-Mode Execution**: Single executable Spring Boot JAR supporting Web UI + REST + STOMP + OpenAPI (`/swagger-ui.html`) as well as non-web Picocli CLI subcommands (`--cli`).
- **Complete ETG.2100 ENI Engine**: Streaming StAX parsing and serialization supporting large configurations (>50 MB, 350+ slaves, 46+ HotConnect groups), backed by MongoDB metadata + GridFS blob storage.
- **Slave & Extension Card Catalog**: Automatic extraction of unknown slave and module profiles from imported ENIs plus optional ETG.2000 ESI XML enrichment.
- **EtherCAT Rule Engine**: Validates AutoInc/Physical addressing, `PreviousPort` port physics (`Y`/`K`/`F`/`H`), FMMU/SyncManager consistency, Mailbox protocols (`CoE`, `AoE`, `EoE`, `FoE`, `SoE`, `VoE`), Distributed Clocks, Hot Connect groups, and Cable Redundancy loops.
- **Composable Bus Workshop**: Interactive Svelte 5 dockable multi-panel UI (`dockview-core`) combining Graphical Topology (`@xyflow/svelte` + `cytoscape`), Tree, Forms, Dynamic Tables with CSV import/export (`tabulator-tables` + `papaparse`), EtherCAT Frame Byte-Map, and PlantUML (`plantuml-encoder` + Apache-2.0 `plantuml-asl`).
- **Quasar Integration**: Eclipse Milo OPC-UA and JeroMQ bridge to Quasar's `EthercatMasterService` (`loadEni`, `exportEni`, `compareEni`, `refreshStatus`, `forceInit`, `reconfigureSlave`) and `quasar_server/` helper.

---

## Quick Navigation

- [Topics Index](topics_index.md)
- [Project Rules & Constraints](architecture/rules_index.md)
- [Architecture Index](architecture/architecture_index.md)
- [EtherCAT & ENI Specification Reference](specifications/ethercat_eni_reference.md)
- [Requirements & Traceability Registry](misc/requirements.md)
