<script lang="ts">
  import { PackagePlus, PlugZap, Combine, Upload } from 'lucide-svelte';
  import { eniApi } from '../lib/api';
  import {
    appendSlaveFromCatalog,
    pushLocalEvent,
    refreshAllCatalogs,
    selectEni,
    workshopState
  } from '../lib/store.svelte';

  let slaveFilter = $state('');
  let esiInput: HTMLInputElement | undefined = $state();

  // Topology Aggregation Form State
  let selectedAddonIds = $state<string[]>([]);
  let aggName = $state('ComposedBus');
  let aggRef = $state('COMP-001');
  let aggPort = $state('B');
  let aggPhys = $state(0);
  let aggRedundant = $state(false);
  let aggMac = $state('001122334466');

  // Quasar Live State
  let opcuaEndpoint = $state('opc.tcp://localhost:4840');
  let quasarOutput = $state('');

  const filteredCatalog = $derived.by(() => {
    const q = slaveFilter.trim().toLowerCase();
    if (!q) return workshopState.slaveCatalog;
    return workshopState.slaveCatalog.filter(
      (e) =>
        e.displayName.toLowerCase().includes(q) ||
        e.category.toLowerCase().includes(q) ||
        e.id.toLowerCase().includes(q)
    );
  });

  async function handleEsiUpload(e: Event): Promise<void> {
    const input = e.currentTarget as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    const res = await eniApi.importEsi(file);
    await refreshAllCatalogs();
    pushLocalEvent('ESI_IMPORTED', res.source, `Enriched ${res.importedCount} devices from ESI`);
    input.value = '';
  }

  function toggleAddon(id: string): void {
    if (selectedAddonIds.includes(id)) {
      selectedAddonIds = selectedAddonIds.filter((x) => x !== id);
    } else {
      selectedAddonIds = [...selectedAddonIds, id];
    }
  }

  async function runAggregation(): Promise<void> {
    if (!workshopState.activeId || selectedAddonIds.length === 0) return;
    const saved = await eniApi.aggregate({
      baseId: workshopState.activeId,
      addonIds: selectedAddonIds,
      reference: aggRef,
      name: aggName,
      version: '1.0.0',
      attachToPhysAddr: aggPhys,
      attachPort: aggPort,
      enableCableRedundancy: aggRedundant,
      secondarySourceMac: aggMac
    });
    await refreshAllCatalogs();
    await selectEni(saved.id);
    pushLocalEvent('TOPOLOGY_AGGREGATED', saved.id, `Composed ${saved.name}`);
  }

  async function connectQuasar(): Promise<void> {
    workshopState.quasarStatus = await eniApi.quasarConnect(opcuaEndpoint);
    quasarOutput = JSON.stringify(workshopState.quasarStatus, null, 2);
  }

  async function scanQuasar(): Promise<void> {
    const res = await eniApi.quasarScan('QuasarMaster', 'QUASAR-SCAN');
    quasarOutput = res.message;
    if (res.eniCatalogId) {
      await refreshAllCatalogs();
      await selectEni(res.eniCatalogId);
    }
  }

  async function testOnQuasar(): Promise<void> {
    if (!workshopState.activeId) return;
    const cmp = await eniApi.quasarTestEni(workshopState.activeId);
    quasarOutput = `Matched=${cmp.matched}: ${cmp.summary}\n${cmp.differences.join('\n')}`;
  }
</script>

<div class="panel-scroll stack">
  <section class="card">
    <div class="sec-hdr">
      <div class="ttl"><PackagePlus size={14} /> Slave &amp; Extension Card Catalog ({filteredCatalog.length})</div>
      <div>
        <input bind:this={esiInput} type="file" accept=".xml" hidden onchange={handleEsiUpload} />
        <button class="btn btn-sm" onclick={() => esiInput?.click()}>
          <Upload size={12} /> Import ESI XML
        </button>
      </div>
    </div>
    <input class="input" style="width:100%;margin-bottom:8px;" placeholder="Search model, category, ID..." bind:value={slaveFilter} />
    <div class="cat-list">
      {#each filteredCatalog.slice(0, 80) as entry}
        <div class="cat-row">
          <div>
            <strong>{entry.displayName}</strong>
            <span class="badge badge-info">{entry.category}</span>
            <span class="badge">{entry.physics || '?'}</span>
            {#if entry.esiEnriched}
              <span class="badge badge-ok">ESI</span>
            {/if}
            <div class="sub">{entry.id} | {entry.protocols.join('/') || 'No Mbx'}</div>
          </div>
          <button
            class="btn btn-sm btn-primary"
            disabled={!workshopState.activeDoc}
            onclick={() => appendSlaveFromCatalog(entry)}>
            + Append
          </button>
        </div>
      {/each}
    </div>
  </section>

  <section class="card">
    <div class="sec-hdr">
      <div class="ttl"><Combine size={14} /> Topology Composer (Aggregate Sub-Buses)</div>
    </div>
    <div class="grid-2">
      <div class="field-row">
        <label class="field-label" for="agg-name">Composed Bus Name</label>
        <input id="agg-name" class="input" bind:value={aggName} />
      </div>
      <div class="field-row">
        <label class="field-label" for="agg-ref">Reference</label>
        <input id="agg-ref" class="input" bind:value={aggRef} />
      </div>
      <div class="field-row">
        <label class="field-label" for="agg-phys">Attach PhysAddr (0=tail)</label>
        <input id="agg-phys" class="input" type="number" bind:value={aggPhys} />
      </div>
      <div class="field-row">
        <label class="field-label" for="agg-port">Attach Port</label>
        <select id="agg-port" class="select" bind:value={aggPort}>
          <option value="B">Port B</option>
          <option value="C">Port C</option>
          <option value="D">Port D</option>
        </select>
      </div>
    </div>
    <div class="addon-checks">
      {#each workshopState.catalog as item}
        <label class="chk-pill">
          <input
            type="checkbox"
            checked={selectedAddonIds.includes(item.id)}
            onchange={() => toggleAddon(item.id)} />
          {item.name} ({item.stats.slaveCount}s)
        </label>
      {/each}
    </div>
    <div class="sec-hdr" style="margin-top:8px;">
      <label class="chk-pill">
        <input type="checkbox" bind:checked={aggRedundant} /> Enable Cable Redundancy Ring
      </label>
      <button
        class="btn btn-primary btn-sm"
        disabled={!workshopState.activeId || selectedAddonIds.length === 0}
        onclick={runAggregation}>
        Aggregate Selected Onto Active
      </button>
    </div>
  </section>

  <section class="card">
    <div class="sec-hdr">
      <div class="ttl"><PlugZap size={14} /> Quasar OPC-UA &amp; resoem Live Bus</div>
      <span class="badge" class:badge-ok={workshopState.quasarStatus?.connected}>
        {workshopState.quasarStatus?.connected ? 'CONNECTED' : 'OFFLINE'}
      </span>
    </div>
    <div class="quasar-row">
      <input class="input" style="flex:1;" bind:value={opcuaEndpoint} />
      <button class="btn btn-sm btn-primary" onclick={connectQuasar}>Connect</button>
      <button class="btn btn-sm" onclick={scanQuasar}>Scan Bus -&gt; ENI</button>
      <button class="btn btn-sm" disabled={!workshopState.activeId} onclick={testOnQuasar}>Test Active ENI</button>
    </div>
    {#if quasarOutput}
      <pre class="q-out">{quasarOutput}</pre>
    {/if}
  </section>
</div>

<style>
  .stack {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .sec-hdr {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }

  .ttl {
    display: flex;
    align-items: center;
    gap: 6px;
    font-weight: 600;
  }

  .cat-list {
    max-block-size: 220px;
    overflow: auto;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .cat-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
    padding: 6px 8px;
    background: #0d1117;
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
  }

  .sub {
    font-size: 11px;
    color: var(--text-muted);
    font-family: var(--font-mono);
  }

  .addon-checks {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 8px;
  }

  .chk-pill {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 11px;
    padding: 3px 8px;
    background: #0d1117;
    border: 1px solid var(--border-subtle);
    border-radius: 999px;
  }

  .quasar-row {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .q-out {
    margin: 8px 0 0 0;
    padding: 8px;
    background: #0d1117;
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
    font-family: var(--font-mono);
    font-size: 11px;
    white-space: pre-wrap;
  }
</style>
