<script lang="ts">
  import { onMount } from 'svelte';
  import { TabulatorFull as Tabulator } from 'tabulator-tables';
  import Papa from 'papaparse';
  import {
    GitCompare,
    Download,
    RefreshCw,
    GitFork,
    Layers,
    Table2,
    ArrowRight
  } from 'lucide-svelte';
  import { eniApi } from '../lib/api';
  import { workshopState } from '../lib/store.svelte';
  import type {
    CompareScope,
    ComparisonResult,
    ComparisonTreeNode,
    DiffStatus,
    EniDocument,
    OuterJoinRow
  } from '../lib/types';

  let scope = $state<CompareScope>('ENI');
  let viewMode = $state<'table' | 'trees' | 'bus'>('table');
  let statusFilter = $state<'ALL' | 'DIFFS' | DiffStatus>('ALL');

  let leftId = $state<string>('');
  let rightId = $state<string>('');
  let leftDoc = $state<EniDocument | null>(null);
  let rightDoc = $state<EniDocument | null>(null);
  let leftPhys = $state<number>(0);
  let rightPhys = $state<number>(0);
  let leftGroup = $state<string>('');
  let rightGroup = $state<string>('');

  let loading = $state(false);
  let errorMsg = $state<string | null>(null);
  let result = $state<ComparisonResult | null>(null);
  let selectedRow = $state<OuterJoinRow | null>(null);

  let tableHost: HTMLDivElement | undefined = $state();
  let tabulator: Tabulator | null = null;

  function statusColor(status: DiffStatus): string {
    switch (status) {
      case 'MATCH':
        return '#22c55e';
      case 'CHANGED':
        return '#f59e0b';
      case 'LEFT_ONLY':
        return '#ef4444';
      case 'RIGHT_ONLY':
        return '#38bdf8';
    }
  }

  function statusBadgeLabel(status: DiffStatus): string {
    switch (status) {
      case 'MATCH':
        return 'MATCH';
      case 'CHANGED':
        return 'CHANGED';
      case 'LEFT_ONLY':
        return 'LEFT ONLY';
      case 'RIGHT_ONLY':
        return 'RIGHT ONLY';
    }
  }

  function extractHotConnectGroups(doc: EniDocument | null): string[] {
    if (!doc) return [];
    const seen = new Set<string>();
    for (const s of doc.slaves) {
      if (s.hotConnect?.groupName) {
        seen.add(s.hotConnect.groupName);
      }
    }
    return Array.from(seen);
  }

  async function loadSideDoc(side: 'left' | 'right', id: string): Promise<void> {
    if (!id) return;
    try {
      const doc = await eniApi.getDocument(id);
      if (side === 'left') {
        leftDoc = doc;
        leftPhys = doc.slaves[0]?.identity.physAddr ?? 0;
        leftGroup = extractHotConnectGroups(doc)[0] ?? '';
      } else {
        rightDoc = doc;
        rightPhys = doc.slaves[0]?.identity.physAddr ?? 0;
        rightGroup = extractHotConnectGroups(doc)[0] ?? '';
      }
    } catch (err) {
      errorMsg = err instanceof Error ? err.message : String(err);
    }
  }

  async function runCompare(): Promise<void> {
    if (!leftId || !rightId) return;
    loading = true;
    errorMsg = null;
    selectedRow = null;
    try {
      if (scope === 'ENI') {
        result = await eniApi.compareEnis(leftId, rightId);
      } else if (scope === 'TOPOLOGY') {
        result = await eniApi.compareTopologies(leftId, rightId);
      } else if (scope === 'SLAVE') {
        result = await eniApi.compareSlaves(leftId, leftPhys, rightId, rightPhys);
      } else {
        if (!leftGroup || !rightGroup) {
          throw new Error('Select a Hot Connect group on both sides to compare');
        }
        result = await eniApi.compareHotConnect(leftId, leftGroup, rightId, rightGroup);
      }
      renderOuterJoinTable();
    } catch (err) {
      errorMsg = err instanceof Error ? err.message : String(err);
    } finally {
      loading = false;
    }
  }

  function filteredRows(): OuterJoinRow[] {
    if (!result) return [];
    if (statusFilter === 'ALL') return result.outerJoinRows;
    if (statusFilter === 'DIFFS') return result.outerJoinRows.filter((r) => r.status !== 'MATCH');
    return result.outerJoinRows.filter((r) => r.status === statusFilter);
  }

  function renderOuterJoinTable(): void {
    if (!tableHost || viewMode !== 'table' || !result) return;
    if (tabulator) {
      tabulator.destroy();
      tabulator = null;
    }
    const data = filteredRows().map((row) => ({
      rowKey: row.rowKey,
      section: row.section,
      name: row.name,
      status: row.status,
      leftSummary: row.leftSummary,
      rightSummary: row.rightSummary,
      deltaCount: row.deltas.length,
      deltasText: row.deltas.map((d) => `${d.field}: ${d.leftValue} -> ${d.rightValue}`).join(' | '),
      _raw: row
    }));

    tabulator = new Tabulator(tableHost, {
      data,
      layout: 'fitDataStretch',
      height: '100%',
      columns: [
        { title: 'Section', field: 'section', width: 120, headerFilter: 'input' as const },
        { title: 'Key', field: 'rowKey', width: 165, headerFilter: 'input' as const },
        { title: 'Name', field: 'name', width: 170, headerFilter: 'input' as const },
        {
          title: 'Status',
          field: 'status',
          width: 110,
          formatter: (cell) => {
            const val = String(cell.getValue()) as DiffStatus;
            return `<span style="color:${statusColor(val)};font-weight:700">${val}</span>`;
          }
        },
        { title: `Left (${result.leftTitle})`, field: 'leftSummary', widthGrow: 1 },
        { title: `Right (${result.rightTitle})`, field: 'rightSummary', widthGrow: 1 },
        { title: 'Deltas', field: 'deltasText', widthGrow: 1 }
      ]
    });
    tabulator.on('rowClick', (_e, row) => {
      const rec = row.getData() as { _raw?: OuterJoinRow };
      selectedRow = rec._raw ?? null;
    });
  }

  function exportOuterJoinCsv(): void {
    if (!result) return;
    const rows = filteredRows().map((r) => ({
      Section: r.section,
      Key: r.rowKey,
      Name: r.name,
      Status: r.status,
      Left: r.leftSummary,
      Right: r.rightSummary,
      Deltas: r.deltas.map((d) => `${d.field}: ${d.leftValue} -> ${d.rightValue}`).join('; ')
    }));
    const csv = Papa.unparse(rows);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `eni-compare-${scope.toLowerCase()}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  onMount(() => {
    const items = workshopState.catalog;
    if (items.length > 0) {
      leftId = workshopState.activeId ?? items[0].id;
      rightId = items.length > 1 ? items[1].id : items[0].id;
      void Promise.all([loadSideDoc('left', leftId), loadSideDoc('right', rightId)]).then(() => runCompare());
    }
    return () => {
      tabulator?.destroy();
      tabulator = null;
    };
  });

  $effect(() => {
    // Reactively initialize IDs when catalog loads after panel opens
    const items = workshopState.catalog;
    if (!leftId && items.length > 0) {
      leftId = workshopState.activeId ?? items[0].id;
      rightId = items.length > 1 ? items[1].id : items[0].id;
      void Promise.all([loadSideDoc('left', leftId), loadSideDoc('right', rightId)]).then(() => runCompare());
    }
  });

  $effect(() => {
    void viewMode;
    void statusFilter;
    void result;
    if (viewMode === 'table' && result && tableHost) {
      queueMicrotask(() => renderOuterJoinTable());
    }
  });
</script>

{#snippet treeNodeView(node: ComparisonTreeNode, depth: number)}
  <div class="cmp-tree-node" style={`padding-left: ${depth * 14 + 6}px`}>
    <span class="cmp-pill" style={`border-color:${statusColor(node.status)};color:${statusColor(node.status)}`}>
      {statusBadgeLabel(node.status)}
    </span>
    <span class="cmp-node-sec">[{node.section}]</span>
    <strong class="cmp-node-label">{node.label}</strong>
    {#if node.detail}
      <span class="cmp-node-detail">{node.detail}</span>
    {/if}
  </div>
  {#each node.children as child}
    {@render treeNodeView(child, depth + 1)}
  {/each}
{/snippet}

<div class="compare-shell">
  <div class="toolbar">
    <div class="scope-group">
      <GitCompare size={14} />
      <select class="select" bind:value={scope} onchange={() => runCompare()} aria-label="Comparison Scope">
        <option value="ENI">2 ENI Files</option>
        <option value="TOPOLOGY">2 Bus Topologies</option>
        <option value="SLAVE">2 Slaves</option>
        <option value="HOT_CONNECT">2 Hot Connect Groups</option>
      </select>
    </div>

    <div class="side-picker">
      <span class="side-tag left-tag">L</span>
      <select
        class="select"
        bind:value={leftId}
        onchange={async () => {
          await loadSideDoc('left', leftId);
          await runCompare();
        }}>
        {#each workshopState.catalog as item}
          <option value={item.id}>{item.reference} | {item.name} ({item.stats.slaveCount})</option>
        {/each}
      </select>
      {#if scope === 'SLAVE' && leftDoc}
        <select class="select" bind:value={leftPhys} onchange={() => runCompare()}>
          {#each leftDoc.slaves as s}
            <option value={s.identity.physAddr}>#{s.identity.physAddr} {s.identity.name}</option>
          {/each}
        </select>
      {:else if scope === 'HOT_CONNECT'}
        <select class="select" bind:value={leftGroup} onchange={() => runCompare()}>
          {#each extractHotConnectGroups(leftDoc) as g}
            <option value={g}>{g}</option>
          {/each}
        </select>
      {/if}
    </div>

    <div class="side-picker">
      <span class="side-tag right-tag">R</span>
      <select
        class="select"
        bind:value={rightId}
        onchange={async () => {
          await loadSideDoc('right', rightId);
          await runCompare();
        }}>
        {#each workshopState.catalog as item}
          <option value={item.id}>{item.reference} | {item.name} ({item.stats.slaveCount})</option>
        {/each}
      </select>
      {#if scope === 'SLAVE' && rightDoc}
        <select class="select" bind:value={rightPhys} onchange={() => runCompare()}>
          {#each rightDoc.slaves as s}
            <option value={s.identity.physAddr}>#{s.identity.physAddr} {s.identity.name}</option>
          {/each}
        </select>
      {:else if scope === 'HOT_CONNECT'}
        <select class="select" bind:value={rightGroup} onchange={() => runCompare()}>
          {#each extractHotConnectGroups(rightDoc) as g}
            <option value={g}>{g}</option>
          {/each}
        </select>
      {/if}
    </div>

    <div class="actions">
      <button class="btn btn-primary btn-sm" onclick={() => runCompare()} disabled={loading}>
        <RefreshCw size={12} /> {loading ? 'Comparing...' : 'Compare'}
      </button>
      <button class="btn btn-sm" class:active={viewMode === 'table'} onclick={() => (viewMode = 'table')}>
        <Table2 size={12} /> Outer Join
      </button>
      <button class="btn btn-sm" class:active={viewMode === 'trees'} onclick={() => (viewMode = 'trees')}>
        <GitFork size={12} /> Trees
      </button>
      <button class="btn btn-sm" class:active={viewMode === 'bus'} onclick={() => (viewMode = 'bus')}>
        <Layers size={12} /> Linear Bus
      </button>
      {#if result}
        <button class="btn btn-sm" onclick={exportOuterJoinCsv}>
          <Download size={12} /> CSV
        </button>
      {/if}
    </div>
  </div>

  {#if errorMsg}
    <div class="cmp-error">{errorMsg}</div>
  {/if}

  {#if result}
    <div class="summary-bar">
      <button class="filter-pill" class:selected={statusFilter === 'ALL'} onclick={() => (statusFilter = 'ALL')}>
        All ({result.summary.totalRows})
      </button>
      <button class="filter-pill" class:selected={statusFilter === 'DIFFS'} onclick={() => (statusFilter = 'DIFFS')}>
        Diffs ({result.summary.changedCount + result.summary.leftOnlyCount + result.summary.rightOnlyCount})
      </button>
      <button
        class="filter-pill"
        class:selected={statusFilter === 'CHANGED'}
        style="color:#f59e0b"
        onclick={() => (statusFilter = 'CHANGED')}>
        Changed ({result.summary.changedCount})
      </button>
      <button
        class="filter-pill"
        class:selected={statusFilter === 'LEFT_ONLY'}
        style="color:#ef4444"
        onclick={() => (statusFilter = 'LEFT_ONLY')}>
        Left Only ({result.summary.leftOnlyCount})
      </button>
      <button
        class="filter-pill"
        class:selected={statusFilter === 'RIGHT_ONLY'}
        style="color:#38bdf8"
        onclick={() => (statusFilter = 'RIGHT_ONLY')}>
        Right Only ({result.summary.rightOnlyCount})
      </button>
      <button
        class="filter-pill"
        class:selected={statusFilter === 'MATCH'}
        style="color:#22c55e"
        onclick={() => (statusFilter = 'MATCH')}>
        Match ({result.summary.matchedCount})
      </button>
    </div>

    <div class="workspace">
      {#if viewMode === 'table'}
        <div class="table-split">
          <div class="table-wrap" bind:this={tableHost}></div>
          {#if selectedRow}
            <aside class="delta-drawer">
              <div class="drawer-hdr">
                <strong>{selectedRow.name}</strong>
                <span class="cmp-pill" style={`color:${statusColor(selectedRow.status)}`}>
                  {selectedRow.status}
                </span>
              </div>
              <div class="drawer-meta">{selectedRow.section} | {selectedRow.rowKey}</div>
              <div class="drawer-kv">
                <div><strong>Left:</strong> {selectedRow.leftSummary}</div>
                <div><strong>Right:</strong> {selectedRow.rightSummary}</div>
              </div>
              {#if selectedRow.deltas.length > 0}
                <table class="delta-tbl">
                  <thead>
                    <tr>
                      <th>Field</th>
                      <th>Left</th>
                      <th>Right</th>
                    </tr>
                  </thead>
                  <tbody>
                    {#each selectedRow.deltas as d}
                      <tr>
                        <td>{d.field}</td>
                        <td class="del-l">{d.leftValue}</td>
                        <td class="del-r">{d.rightValue}</td>
                      </tr>
                    {/each}
                  </tbody>
                </table>
              {/if}
            </aside>
          {/if}
        </div>
      {:else if viewMode === 'trees'}
        <div class="trees-grid">
          <section class="tree-pane">
            <header class="pane-title">LEFT: {result.leftTitle}</header>
            <div class="tree-scroll">
              {@render treeNodeView(result.leftTree, 0)}
            </div>
          </section>
          <section class="tree-pane">
            <header class="pane-title">RIGHT: {result.rightTitle}</header>
            <div class="tree-scroll">
              {@render treeNodeView(result.rightTree, 0)}
            </div>
          </section>
        </div>
      {:else}
        <div class="bus-compare-stack">
          <section class="bus-lane">
            <header class="pane-title">LEFT BUS: {result.leftTitle} ({result.leftLinearBus.length} devices)</header>
            <div class="bus-strip">
              <div class="bus-node bus-master">Master</div>
              {#each result.leftLinearBus as item}
                <ArrowRight size={14} class="bus-arrow" />
                <div class="bus-node" style={`border-color:${statusColor(item.status)}`}>
                  <div class="bus-top">
                    <span class="bus-phys">#{item.physAddr}</span>
                    <span class="cmp-pill" style={`color:${statusColor(item.status)}`}>{item.status}</span>
                  </div>
                  <div class="bus-name">{item.name}</div>
                  <div class="bus-meta">{item.category} | {item.physics} | Prev={item.prevPort}</div>
                  <div class="bus-meta">
                    Rx={item.rxBits}b Tx={item.txBits}b {item.dcEnabled ? '| DC' : ''}
                  </div>
                  {#if item.hotConnectGroup}
                    <div class="hc-badge">HC: {item.hotConnectGroup}</div>
                  {/if}
                </div>
              {/each}
            </div>
          </section>

          <section class="bus-lane">
            <header class="pane-title">RIGHT BUS: {result.rightTitle} ({result.rightLinearBus.length} devices)</header>
            <div class="bus-strip">
              <div class="bus-node bus-master">Master</div>
              {#each result.rightLinearBus as item}
                <ArrowRight size={14} class="bus-arrow" />
                <div class="bus-node" style={`border-color:${statusColor(item.status)}`}>
                  <div class="bus-top">
                    <span class="bus-phys">#{item.physAddr}</span>
                    <span class="cmp-pill" style={`color:${statusColor(item.status)}`}>{item.status}</span>
                  </div>
                  <div class="bus-name">{item.name}</div>
                  <div class="bus-meta">{item.category} | {item.physics} | Prev={item.prevPort}</div>
                  <div class="bus-meta">
                    Rx={item.rxBits}b Tx={item.txBits}b {item.dcEnabled ? '| DC' : ''}
                  </div>
                  {#if item.hotConnectGroup}
                    <div class="hc-badge">HC: {item.hotConnectGroup}</div>
                  {/if}
                </div>
              {/each}
            </div>
          </section>
        </div>
      {/if}
    </div>
  {:else}
    <div class="empty-msg">Import at least one ENI file in the catalog to start comparing.</div>
  {/if}
</div>

<style>
  .compare-shell {
    display: flex;
    flex-direction: column;
    height: 100%;
    padding: 8px;
    gap: 8px;
    background: #0f172a;
    color: #e2e8f0;
    font-size: 12px;
  }
  .toolbar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    background: #1e293b;
    padding: 6px 8px;
    border-radius: 6px;
    border: 1px solid #334155;
  }
  .scope-group,
  .side-picker,
  .actions {
    display: flex;
    align-items: center;
    gap: 4px;
  }
  .side-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    border-radius: 4px;
    font-weight: 700;
    font-size: 11px;
  }
  .left-tag {
    background: #7f1d1d;
    color: #fecaca;
  }
  .right-tag {
    background: #0c4a6e;
    color: #bae6fd;
  }
  .summary-bar {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }
  .filter-pill {
    background: #1e293b;
    border: 1px solid #334155;
    color: #cbd5e1;
    border-radius: 12px;
    padding: 3px 10px;
    cursor: pointer;
    font-size: 11px;
  }
  .filter-pill.selected {
    border-color: #60a5fa;
    background: #1d4ed833;
  }
  .btn.active {
    background: #2563eb;
    color: #fff;
    border-color: #3b82f6;
  }
  .workspace {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }
  .table-split {
    flex: 1;
    min-height: 0;
    display: flex;
    gap: 8px;
  }
  .table-wrap {
    flex: 1;
    min-width: 0;
    height: 100%;
    border: 1px solid #334155;
    border-radius: 6px;
    overflow: hidden;
  }
  .delta-drawer {
    width: 340px;
    background: #1e293b;
    border: 1px solid #334155;
    border-radius: 6px;
    padding: 10px;
    overflow: auto;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .drawer-hdr {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .drawer-meta {
    color: #94a3b8;
    font-size: 11px;
  }
  .drawer-kv {
    display: flex;
    flex-direction: column;
    gap: 4px;
    background: #0f172a;
    padding: 6px;
    border-radius: 4px;
  }
  .delta-tbl {
    width: 100%;
    border-collapse: collapse;
    font-size: 11px;
  }
  .delta-tbl th,
  .delta-tbl td {
    border: 1px solid #334155;
    padding: 4px;
    text-align: left;
  }
  .del-l {
    color: #fca5a5;
  }
  .del-r {
    color: #7dd3fc;
  }
  .trees-grid {
    flex: 1;
    min-height: 0;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
  }
  .tree-pane {
    display: flex;
    flex-direction: column;
    min-height: 0;
    border: 1px solid #334155;
    border-radius: 6px;
    background: #1e293b;
  }
  .pane-title {
    padding: 6px 10px;
    font-weight: 700;
    border-bottom: 1px solid #334155;
    background: #0f172a;
    color: #e2e8f0;
  }
  .tree-scroll {
    flex: 1;
    overflow: auto;
    padding: 6px;
  }
  .cmp-tree-node {
    display: flex;
    align-items: center;
    gap: 6px;
    padding-top: 3px;
    padding-bottom: 3px;
    border-bottom: 1px dashed #33415555;
    white-space: nowrap;
  }
  .cmp-pill {
    font-size: 10px;
    border: 1px solid;
    border-radius: 4px;
    padding: 0 4px;
    font-weight: 700;
  }
  .cmp-node-sec {
    color: #94a3b8;
    font-size: 11px;
  }
  .cmp-node-detail {
    color: #94a3b8;
    font-size: 11px;
  }
  .bus-compare-stack {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    gap: 10px;
    overflow: auto;
  }
  .bus-lane {
    border: 1px solid #334155;
    border-radius: 6px;
    background: #1e293b;
  }
  .bus-strip {
    display: flex;
    align-items: center;
    gap: 6px;
    overflow-x: auto;
    padding: 12px 10px;
  }
  .bus-node {
    min-width: 175px;
    background: #0f172a;
    border: 2px solid #475569;
    border-radius: 6px;
    padding: 8px;
    display: flex;
    flex-direction: column;
    gap: 3px;
    flex-shrink: 0;
  }
  .bus-master {
    min-width: 85px;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    border-color: #38bdf8;
    color: #38bdf8;
  }
  .bus-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .bus-phys {
    font-weight: 700;
    color: #93c5fd;
  }
  .bus-name {
    font-weight: 600;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .bus-meta {
    color: #94a3b8;
    font-size: 11px;
  }
  .hc-badge {
    display: inline-block;
    background: #7c2d12;
    color: #fed7aa;
    font-size: 10px;
    padding: 1px 5px;
    border-radius: 4px;
    width: fit-content;
  }
  .cmp-error {
    background: #7f1d1d;
    color: #fecaca;
    padding: 6px 10px;
    border-radius: 4px;
  }
  .empty-msg {
    padding: 20px;
    color: #94a3b8;
    text-align: center;
  }
</style>
