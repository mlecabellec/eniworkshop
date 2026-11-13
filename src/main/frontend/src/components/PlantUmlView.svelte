<script lang="ts">
  import plantumlEncoder from 'plantuml-encoder';
  import { Workflow, RefreshCw, Download } from 'lucide-svelte';
  import { eniApi } from '../lib/api';
  import { workshopState } from '../lib/store.svelte';

  let kind = $state<'topology' | 'esm'>('topology');
  let pumlSource = $state('@startuml\nMaster -> Slave : Select an ENI file\n@enduml\n');
  let svgMarkup = $state('');
  let encodedToken = $derived(plantumlEncoder.encode(pumlSource));
  let renderError = $state<string | null>(null);

  async function loadAndRender(): Promise<void> {
    if (!workshopState.activeId) return;
    renderError = null;
    try {
      pumlSource = await eniApi.getPlantUml(workshopState.activeId, kind);
      svgMarkup = await eniApi.renderPlantUmlSvg(pumlSource);
    } catch (err) {
      renderError = err instanceof Error ? err.message : String(err);
    }
  }

  async function renderCustomPuml(): Promise<void> {
    renderError = null;
    try {
      svgMarkup = await eniApi.renderPlantUmlSvg(pumlSource);
    } catch (err) {
      renderError = err instanceof Error ? err.message : String(err);
    }
  }

  function downloadSvg(): void {
    if (!svgMarkup) return;
    const blob = new Blob([svgMarkup], { type: 'image/svg+xml;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `eni-${kind}.svg`;
    a.click();
    URL.revokeObjectURL(url);
  }

  $effect(() => {
    void workshopState.activeId;
    void kind;
    if (workshopState.activeId) {
      void loadAndRender();
    }
  });
</script>

<div class="puml-root">
  <div class="puml-bar">
    <div class="bar-grp">
      <Workflow size={14} />
      <strong>PlantUML Studio</strong>
      <button
        class="btn btn-sm"
        class:btn-primary={kind === 'topology'}
        onclick={() => (kind = 'topology')}>
        Topology
      </button>
      <button
        class="btn btn-sm"
        class:btn-primary={kind === 'esm'}
        onclick={() => (kind = 'esm')}>
        ESM Sequence
      </button>
    </div>
    <div class="bar-grp">
      <button class="btn btn-sm btn-primary" onclick={renderCustomPuml}>
        <RefreshCw size={12} /> Render SVG
      </button>
      <button class="btn btn-sm" onclick={downloadSvg} disabled={!svgMarkup}>
        <Download size={12} /> SVG
      </button>
    </div>
  </div>

  <div class="puml-split">
    <div class="editor-col">
      <div class="enc-token">
        JS plantuml-encoder ID: <code>{encodedToken.slice(0, 40)}...</code>
      </div>
      <textarea class="textarea puml-editor" bind:value={pumlSource}></textarea>
    </div>
    <div class="svg-col">
      {#if renderError}
        <div class="badge badge-err">{renderError}</div>
      {/if}
      {#if svgMarkup}
        <div class="svg-canvas">{@html svgMarkup}</div>
      {:else}
        <p class="muted">Click Render SVG or load an ENI document.</p>
      {/if}
    </div>
  </div>
</div>

<style>
  .puml-root {
    display: flex;
    flex-direction: column;
    inline-size: 100%;
    block-size: 100%;
    min-inline-size: 0;
  }

  .puml-bar {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    background: #11161d;
    border-bottom: 1px solid var(--border-subtle);
  }

  .bar-grp {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .puml-split {
    flex: 1 1 auto;
    display: grid;
    grid-template-columns: minmax(220px, 1fr) minmax(260px, 1.4fr);
    min-block-size: 0;
  }

  .editor-col {
    display: flex;
    flex-direction: column;
    border-right: 1px solid var(--border-subtle);
    min-block-size: 0;
  }

  .enc-token {
    padding: 4px 8px;
    font-size: 11px;
    color: var(--text-muted);
    background: #0d1117;
    border-bottom: 1px solid var(--border-subtle);
  }

  .puml-editor {
    flex: 1 1 auto;
    border: none;
    border-radius: 0;
    resize: none;
    font-family: var(--font-mono);
    font-size: 11px;
    padding: 8px;
  }

  .svg-col {
    padding: 12px;
    overflow: auto;
    background: #ffffff;
    color: #111111;
  }

  .svg-canvas :global(svg) {
    max-inline-size: 100%;
    block-size: auto;
  }

  .muted {
    color: #555;
  }
</style>
