# Svelte 5 Workbench Frontend Architecture

The frontend lives in `src/main/frontend` and is built with **Svelte 5 + TypeScript + Vite**, bundled into `target/classes/static` via `frontend-maven-plugin`.

---

## Dynamic Multi-Panel Workbench (`dockview-core`)

Users can open, split, rearrange, float, and mix multiple synchronized views simultaneously inside a `dockview-core` workspace:

1. **Graphical Topology Editor & Viewer** (`@xyflow/svelte` + `cytoscape`)
   - Interactive node-edge graph of Master, Couplers (`EK1100`), Junctions (`EK1122`), Slaves, HotConnect groups, and Redundant Ring loops.
   - Drag-and-drop insertion from the Slave & Extension Card Catalog and Sub-Topology aggregation blocks.
2. **Bus Tree Explorer**
   - Hierarchical Master -> HotConnect / Bus Segments -> Slaves -> Modules -> SyncManagers -> PDOs -> Variables & InitCmds tree.
3. **Reactive Property Forms**
   - Validated forms for Master MAC/EtherType, Slave Vendor/Product/Revision, Port Physics (`Y`/`K`/`F`/`H`), Mailbox (`CoE`, `AoE`, `EoE`, `FoE`, `SoE`), DC, FMMU/SM, and InitCmds.
4. **Dynamic Table & CSV Workbench** (`tabulator-tables` + `papaparse`)
   - Inline-editable, sortable, filterable tables for Slaves, ProcessImage Variables, PDOs, and InitCmds with CSV import & export.
5. **EtherCAT Frame Byte-Map Diagram** (Interactive SVG + `d3-scale`)
   - Bit/byte-accurate visualization of Ethernet Header (14B) + EtherCAT Header (2B) + Datagrams (`Cmd`, `Idx`, `Adp/Ado`/`LogAddr`, `Len`, `R/C/M`, `IRQ`, Data Payload, `WKC`) + mapped ProcessImage variables.
6. **PlantUML Diagram Viewer & Editor** (`plantuml-encoder` + Apache-2.0 `/api/diagrams/plantuml` SVG endpoint)
   - Live topology, ESM sequence, and HotConnect PlantUML rendering with editable `.puml` source.
7. **Catalog, Versioning & Quasar Live Panel**
   - Browse/search ENI catalog versions, unknown slave/module discoveries, ESI imports, and Quasar live bus scan / ENI test controls.
8. **Multi-Scope Comparison Workbench** (`CompareView.svelte` + `Tabulator`)
   - Compare **2 ENI files**, **2 bus topologies**, **2 individual slaves**, or **2 Hot Connect groups**.
   - Switch between **Outer Join Dynamic Table** (filterable `CHANGED`/`LEFT_ONLY`/`RIGHT_ONLY`/`MATCH` with field-level delta inspector & CSV export), **Side-by-Side Trees**, and **Graphical Linear Bus Strips**.
9. **Task, Feature & Activity Governance Panel** (`ProjectTasksView.svelte` + Topbar Work-Ref Selector)
   - Manage Features (`FR-XXXXX`), Tasks (`TSK-XXXXX`), activity logs attributed via `X-Eni-Work-Ref` headers/CLI/STOMP, and live `CS-0010` traceability coverage (`/api/project/*`).
