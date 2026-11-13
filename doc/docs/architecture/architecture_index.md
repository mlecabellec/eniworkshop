# Architecture Index

ENI Workshop follows a layered, modular Spring Boot + Svelte 5 architecture:

1. **CLI & Web Bootstrap Layer (`fr.lecabellec.eniworkshop`)**
   - `EniWorkshopApplication` switches cleanly between Picocli non-web CLI execution (`--cli`) and Spring Boot Web/STOMP server mode.
2. **EtherCAT & ENI Domain Core (`fr.lecabellec.eniworkshop.eni.*`)**
   - Streaming StAX `EniXmlParser` & `EniXmlWriter` (handles >50 MB ENIs cleanly).
   - Immutable/validated domain model (`EniDocument`, `EniMaster`, `EniSlave`, `EniHotConnect`, `EniMailbox`, `EniSyncManager`, `EniPdo`, `EniProcessImage`, `EniCyclicFrame`, `EniDistributedClocks`, `EniRedundancy`).
   - ETG.1000/ETG.2100 `EniValidationEngine` (Strategy-based rules).
   - `TopologyAggregationService` (hierarchical sub-topology merging, address & PDO offset recalculation, ring redundancy validation).
   - `SlaveCatalogService` (auto-extraction from ENIs + ETG.2000 ESI XML enrichment).
3. **Persistence Layer (`fr.lecabellec.eniworkshop.persistence`)**
   - Hybrid MongoDB storage: structured metadata/version/catalog/topology collections + GridFS raw ENI XML blobs (>16 MB safe).
4. **Messaging & Quasar Bridge (`fr.lecabellec.eniworkshop.quasar`, `messaging`, `websocket`)**
   - REST + OpenAPI (`/swagger-ui.html`), STOMP WebSocket (`/ws-stomp`), JeroMQ, Kafka (optional), and Eclipse Milo OPC-UA integration with Quasar's `EthercatMasterService` (`resoem`/`resoes`).
5. **Visualization & Workbench (`src/main/frontend`, `plantuml`)**
   - Svelte 5 + Vite + `dockview-core` dynamic panels + `@xyflow/svelte` + `cytoscape` + Frame Byte-Map + Tabulator/PapaParse + PlantUML.
