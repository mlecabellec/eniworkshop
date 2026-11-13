<script lang="ts">
  import { onMount, mount, unmount, type Component } from 'svelte';
  import { createDockview, type DockviewApi, type IContentRenderer } from 'dockview-core';
  import {
    Upload,
    Copy,
    Download,
    Pencil,
    PlusSquare,
    BookOpen,
    FileCode2,
    Radio
  } from 'lucide-svelte';
  import { connectWorkshopStomp, eniApi } from './lib/api';
  import {
    changeActiveWorkRef,
    pushLocalEvent,
    refreshAllCatalogs,
    refreshProjectOverview,
    selectEni,
    workshopState
  } from './lib/store.svelte';
  import TopologyView from './components/TopologyView.svelte';
  import TreeView from './components/TreeView.svelte';
  import FormView from './components/FormView.svelte';
  import TableCsvView from './components/TableCsvView.svelte';
  import FrameMapView from './components/FrameMapView.svelte';
  import PlantUmlView from './components/PlantUmlView.svelte';
  import CatalogQuasarView from './components/CatalogQuasarView.svelte';
  import CompareView from './components/CompareView.svelte';
  import ProjectTasksView from './components/ProjectTasksView.svelte';

  const VIEW_COMPONENTS: Record<string, { title: string; component: Component }> = {
    topology: { title: 'Bus Topology (Graph/Flow)', component: TopologyView },
    tree: { title: 'Bus Tree', component: TreeView },
    form: { title: 'Forms & Validation', component: FormView },
    table: { title: 'Table & CSV', component: TableCsvView },
    frames: { title: 'Frame Byte-Map', component: FrameMapView },
    plantuml: { title: 'PlantUML Diagrams', component: PlantUmlView },
    catalogQuasar: { title: 'Catalog / Composer / Quasar', component: CatalogQuasarView },
    compare: { title: 'Compare & Diff', component: CompareView },
    project: { title: 'Tasks & Features', component: ProjectTasksView }
  };

  let dockHost: HTMLElement | undefined = $state();
  let dockApi: DockviewApi | null = null;
  let eniFileInput: HTMLInputElement | undefined = $state();
  let panelSeq = 0;

  function createSvelteRenderer(comp: Component): IContentRenderer {
    const element = document.createElement('div');
    element.style.width = '100%';
    element.style.height = '100%';
    let mountedInstance: Record<string, unknown> | null = null;
    return {
      element,
      init() {
        mountedInstance = mount(comp, { target: element });
      },
      dispose() {
        if (mountedInstance) {
          void unmount(mountedInstance);
          mountedInstance = null;
        }
      }
    };
  }

  function openViewPanel(viewKey: string): void {
    if (!dockApi) return;
    const def = VIEW_COMPONENTS[viewKey];
    if (!def) return;
    panelSeq += 1;
    dockApi.addPanel({
      id: `${viewKey}-${panelSeq}`,
      component: viewKey,
      title: def.title
    });
  }

  async function handleEniUpload(e: Event): Promise<void> {
    const input = e.currentTarget as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    const ref = `ENI-${new Date().toISOString().slice(11, 19).replaceAll(':', '')}`;
    const res = await eniApi.importFile(file, ref, file.name, '1.0.0', 'workshop');
    await refreshAllCatalogs();
    await selectEni(res.metadata.id);
    pushLocalEvent(
      'IMPORT',
      res.metadata.id,
      `Imported ${file.name} (${res.discovery.newlyAddedCount} new slave profiles)`
    );
    input.value = '';
  }

  async function duplicateCurrent(): Promise<void> {
    if (!workshopState.activeId) return;
    const cur = workshopState.catalog.find((c) => c.id === workshopState.activeId);
    const dup = await eniApi.duplicate(
      workshopState.activeId,
      `${cur?.reference ?? 'ENI'}-COPY`,
      `${cur?.name ?? 'ENI'} Copy`,
      '1.0.0'
    );
    await refreshAllCatalogs();
    await selectEni(dup.id);
  }

  async function renameCurrent(): Promise<void> {
    if (!workshopState.activeId) return;
    const cur = workshopState.catalog.find((c) => c.id === workshopState.activeId);
    const nextName = window.prompt('New ENI display name:', cur?.name ?? '');
    if (!nextName) return;
    await eniApi.rename(workshopState.activeId, nextName, cur?.reference ?? 'REF');
    await refreshAllCatalogs();
  }

  onMount(() => {
    if (dockHost) {
      dockApi = createDockview(dockHost, {
        className: 'dockview-theme-eni',
        createComponent(options) {
          const def = VIEW_COMPONENTS[options.name] ?? VIEW_COMPONENTS.topology;
          return createSvelteRenderer(def.component);
        }
      });

      const treePanel = dockApi.addPanel({
        id: 'tree-main',
        component: 'tree',
        title: VIEW_COMPONENTS.tree.title
      });
      const topoPanel = dockApi.addPanel({
        id: 'topology-main',
        component: 'topology',
        title: VIEW_COMPONENTS.topology.title,
        position: { referencePanel: treePanel, direction: 'right' }
      });
      dockApi.addPanel({
        id: 'table-main',
        component: 'table',
        title: VIEW_COMPONENTS.table.title,
        position: { referencePanel: topoPanel, direction: 'within' }
      });
      dockApi.addPanel({
        id: 'frames-main',
        component: 'frames',
        title: VIEW_COMPONENTS.frames.title,
        position: { referencePanel: topoPanel, direction: 'within' }
      });
      dockApi.addPanel({
        id: 'plantuml-main',
        component: 'plantuml',
        title: VIEW_COMPONENTS.plantuml.title,
        position: { referencePanel: topoPanel, direction: 'within' }
      });
      const formPanel = dockApi.addPanel({
        id: 'form-main',
        component: 'form',
        title: VIEW_COMPONENTS.form.title,
        position: { referencePanel: topoPanel, direction: 'right' }
      });
      dockApi.addPanel({
        id: 'catalog-main',
        component: 'catalogQuasar',
        title: VIEW_COMPONENTS.catalogQuasar.title,
        position: { referencePanel: formPanel, direction: 'within' }
      });
      dockApi.addPanel({
        id: 'compare-main',
        component: 'compare',
        title: VIEW_COMPONENTS.compare.title,
        position: { referencePanel: formPanel, direction: 'within' }
      });
      dockApi.addPanel({
        id: 'project-main',
        component: 'project',
        title: VIEW_COMPONENTS.project.title,
        position: { referencePanel: formPanel, direction: 'within' }
      });
      topoPanel.api.setActive();
      formPanel.api.setActive();
    }

    void refreshProjectOverview();
    void refreshAllCatalogs().then(() => {
      if (workshopState.catalog.length > 0 && !workshopState.activeId) {
        void selectEni(workshopState.catalog[0].id);
      }
    });

    const stopStomp = connectWorkshopStomp((evt) => {
      workshopState.events = [evt, ...workshopState.events.slice(0, 29)];
      if (evt.eventType === 'PROJECT_ACTIVITY') {
        void refreshProjectOverview();
      }
    });

    return () => {
      stopStomp();
      dockApi?.dispose();
    };
  });
</script>

<div class="app-shell">
  <header class="topbar">
    <div class="brand">
      <span class="logo-dot"></span>
      <strong>ENI Workshop</strong>
      <span class="badge badge-info">ETG.2100 + Quasar</span>
      <select
        class="select"
        aria-label="Active Task or Feature"
        title="Active Task or Feature attached to REST requests"
        value={workshopState.activeWorkRef}
        onchange={(e) => void changeActiveWorkRef(e.currentTarget.value)}>
        <option value={workshopState.activeWorkRef}>{workshopState.activeWorkRef}</option>
        {#each workshopState.projectOverview?.tasks ?? [] as t}
          {#if t.id !== workshopState.activeWorkRef}
            <option value={t.id}>{t.id}</option>
          {/if}
        {/each}
        {#each workshopState.projectOverview?.features ?? [] as f}
          {#if f.id !== workshopState.activeWorkRef}
            <option value={f.id}>{f.id}</option>
          {/if}
        {/each}
      </select>
    </div>

    <div class="controls">
      <select
        class="select"
        aria-label="Active ENI Catalog Entry"
        value={workshopState.activeId ?? ''}
        onchange={(e) => {
          const val = e.currentTarget.value;
          if (val) void selectEni(val);
        }}>
        <option value="">-- Select ENI Catalog ({workshopState.catalog.length}) --</option>
        {#each workshopState.catalog as item}
          <option value={item.id}>
            {item.reference} | {item.name} v{item.version} ({item.stats.slaveCount} slaves)
          </option>
        {/each}
      </select>

      <input
        bind:this={eniFileInput}
        type="file"
        accept=".xml,.gz"
        hidden
        onchange={handleEniUpload} />
      <button class="btn btn-primary" onclick={() => eniFileInput?.click()}>
        <Upload size={13} /> Import ENI
      </button>
      <button class="btn" disabled={!workshopState.activeId} onclick={duplicateCurrent}>
        <Copy size={13} /> Duplicate
      </button>
      <button class="btn" disabled={!workshopState.activeId} onclick={renameCurrent}>
        <Pencil size={13} /> Rename
      </button>
      {#if workshopState.activeId}
        <a class="btn" href={`/api/eni/${workshopState.activeId}/export`} download>
          <Download size={13} /> Export XML
        </a>
      {/if}
    </div>

    <div class="views-bar">
      <button class="btn btn-sm" onclick={() => openViewPanel('topology')}>
        <PlusSquare size={12} /> Topology
      </button>
      <button class="btn btn-sm" onclick={() => openViewPanel('table')}>
        <PlusSquare size={12} /> Table/CSV
      </button>
      <button class="btn btn-sm" onclick={() => openViewPanel('frames')}>
        <PlusSquare size={12} /> FrameMap
      </button>
      <button class="btn btn-sm" onclick={() => openViewPanel('plantuml')}>
        <PlusSquare size={12} /> PlantUML
      </button>
      <button class="btn btn-sm" onclick={() => openViewPanel('catalogQuasar')}>
        <PlusSquare size={12} /> Catalog/Quasar
      </button>
      <button class="btn btn-sm" onclick={() => openViewPanel('compare')}>
        <PlusSquare size={12} /> Compare
      </button>
      <button class="btn btn-sm" onclick={() => openViewPanel('project')}>
        <PlusSquare size={12} /> Tasks/Features
      </button>
      <a class="btn btn-sm" href="/swagger-ui.html" target="_blank" rel="noreferrer">
        <FileCode2 size={12} /> OpenAPI
      </a>
      <a class="btn btn-sm" href="/v3/api-docs" target="_blank" rel="noreferrer">
        <BookOpen size={12} /> Spec
      </a>
    </div>
  </header>

  {#if workshopState.errorMessage}
    <div class="error-banner">{workshopState.errorMessage}</div>
  {/if}

  <main class="dock-main" bind:this={dockHost}></main>

  <footer class="statusbar">
    <div class="status-left">
      <Radio size={12} />
      <span>
        {workshopState.events.length > 0
          ? `[${workshopState.events[0].eventType}] ${workshopState.events[0].summary}`
          : 'Ready — Drag tabs to split or mix any Topology, Tree, Form, Table/CSV, FrameMap, PlantUML, or Quasar panels'}
      </span>
    </div>
    <div class="status-right">
      <span>Catalog: {workshopState.slaveCatalog.length} devices</span>
      <span>|</span>
      <span>Apache-2.0</span>
    </div>
  </footer>
</div>

<style>
  .app-shell {
    display: flex;
    flex-direction: column;
    inline-size: 100%;
    block-size: 100dvb;
  }

  .topbar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding: 8px 12px;
    background: #11161d;
    border-bottom: 1px solid var(--border-subtle);
  }

  .brand,
  .controls,
  .views-bar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
  }

  .logo-dot {
    inline-size: 10px;
    block-size: 10px;
    border-radius: 50%;
    background: var(--accent-cyan);
    box-shadow: 0 0 8px var(--accent-cyan);
  }

  .error-banner {
    padding: 6px 12px;
    background: rgba(248, 81, 73, 0.2);
    border-bottom: 1px solid var(--accent-red);
    color: #ff7b72;
    font-size: 12px;
  }

  .dock-main {
    flex: 1 1 auto;
    min-block-size: 0;
    inline-size: 100%;
  }

  .statusbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 4px 12px;
    font-size: 11px;
    color: var(--text-muted);
    background: #11161d;
    border-top: 1px solid var(--border-subtle);
  }

  .status-left,
  .status-right {
    display: flex;
    align-items: center;
    gap: 6px;
  }
</style>
