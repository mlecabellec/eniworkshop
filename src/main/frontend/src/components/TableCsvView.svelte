<script lang="ts">
  import { onMount } from 'svelte';
  import { TabulatorFull as Tabulator } from 'tabulator-tables';
  import Papa from 'papaparse';
  import { Download, Upload, Table2 } from 'lucide-svelte';
  import { eniApi } from '../lib/api';
  import { pushLocalEvent, workshopState } from '../lib/store.svelte';

  let tableKind = $state<'slaves' | 'variables'>('slaves');
  let tableHost: HTMLDivElement | undefined = $state();
  let tabulator: Tabulator | null = null;
  let csvFileInput: HTMLInputElement | undefined = $state();

  function buildRows(): Record<string, unknown>[] {
    const doc = workshopState.activeDoc;
    if (!doc) return [];
    if (tableKind === 'slaves') {
      return doc.slaves.map((s, idx) => ({
        index: idx,
        name: s.identity.name,
        physAddr: s.identity.physAddr,
        autoIncAddr: s.identity.autoIncAddr,
        physics: s.identity.physics,
        vendorId: `0x${s.identity.vendorId.toString(16)}`,
        productCode: `0x${s.identity.productCode.toString(16)}`,
        prevPort: s.previousPort ? `${s.previousPort.physAddr}:${s.previousPort.port}` : 'Master',
        hotConnect: s.hotConnect?.groupName ?? '',
        mailbox: s.mailbox.protocols.join('/'),
        dcSync0Ns: s.dc.enabled ? s.dc.cycleTime0 : 0
      }));
    }
    return [
      ...doc.processImage.inputs.map((v) => ({ direction: 'IN', ...v })),
      ...doc.processImage.outputs.map((v) => ({ direction: 'OUT', ...v }))
    ];
  }

  function renderTable(): void {
    if (!tableHost) return;
    if (tabulator) {
      tabulator.destroy();
      tabulator = null;
    }

    const columns =
      tableKind === 'slaves'
        ? [
            { title: '#', field: 'index', width: 52 },
            { title: 'Name', field: 'name', editor: 'input' as const, headerFilter: 'input' as const },
            { title: 'PhysAddr', field: 'physAddr', width: 95 },
            { title: 'AutoInc', field: 'autoIncAddr', width: 85 },
            { title: 'Physics', field: 'physics', width: 80, editor: 'input' as const },
            { title: 'Vendor', field: 'vendorId', width: 95 },
            { title: 'Product', field: 'productCode', width: 105 },
            { title: 'PrevPort', field: 'prevPort', width: 105 },
            { title: 'HotConnect', field: 'hotConnect', width: 120 },
            { title: 'Mailbox', field: 'mailbox', width: 110 },
            { title: 'DC Sync0 (ns)', field: 'dcSync0Ns', width: 110 }
          ]
        : [
            { title: 'Dir', field: 'direction', width: 65, headerFilter: 'input' as const },
            { title: 'Variable Name', field: 'name', headerFilter: 'input' as const },
            { title: 'DataType', field: 'dataType', width: 110, headerFilter: 'input' as const },
            { title: 'BitOffs', field: 'bitOffs', width: 95 },
            { title: 'BitSize', field: 'bitSize', width: 90 },
            { title: 'Comment', field: 'comment' }
          ];

    tabulator = new Tabulator(tableHost, {
      data: buildRows(),
      layout: 'fitColumns',
      height: '100%',
      pagination: true,
      paginationSize: 25,
      columns
    });

    if (tableKind === 'slaves') {
      tabulator.on('rowClick', (_e, row) => {
        const phys = row.getData()['physAddr'] as number | undefined;
        if (typeof phys === 'number') {
          workshopState.selectedPhysAddr = phys;
        }
      });
      tabulator.on('cellEdited', (cell) => {
        const rowData = cell.getRow().getData();
        const phys = rowData['physAddr'] as number;
        if (!workshopState.activeDoc) return;
        workshopState.activeDoc = {
          ...workshopState.activeDoc,
          slaves: workshopState.activeDoc.slaves.map((s) =>
            s.identity.physAddr === phys
              ? {
                  ...s,
                  identity: {
                    ...s.identity,
                    name: String(rowData['name'] ?? s.identity.name),
                    physics: String(rowData['physics'] ?? s.identity.physics)
                  }
                }
              : s
          )
        };
      });
    }
  }

  async function downloadCsv(): Promise<void> {
    if (!workshopState.activeId) {
      const csv = Papa.unparse(buildRows());
      triggerDownload(`eni-${tableKind}.csv`, csv);
      return;
    }
    const csv = await eniApi.exportCsv(workshopState.activeId, tableKind);
    triggerDownload(`eni-${workshopState.activeId}-${tableKind}.csv`, csv);
  }

  function triggerDownload(filename: string, content: string): void {
    const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  async function handleCsvUpload(e: Event): Promise<void> {
    const input = e.currentTarget as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || !workshopState.activeDoc) return;
    const text = await file.text();
    const parsedSlaves = await eniApi.parseCsvSlaves(text);
    workshopState.activeDoc = {
      ...workshopState.activeDoc,
      slaves: parsedSlaves
    };
    pushLocalEvent('CSV_IMPORTED', file.name, `Loaded ${parsedSlaves.length} slaves from CSV`);
    input.value = '';
  }

  $effect(() => {
    void tableKind;
    void workshopState.activeDoc;
    renderTable();
  });

  onMount(() => {
    renderTable();
    return () => {
      if (tabulator) tabulator.destroy();
    };
  });
</script>

<div class="table-root">
  <div class="table-bar">
    <div class="bar-group">
      <Table2 size={14} />
      <strong>Dynamic ENI Table &amp; CSV</strong>
      <button
        class="btn btn-sm"
        class:btn-primary={tableKind === 'slaves'}
        onclick={() => (tableKind = 'slaves')}>
        Slaves
      </button>
      <button
        class="btn btn-sm"
        class:btn-primary={tableKind === 'variables'}
        onclick={() => (tableKind = 'variables')}>
        ProcessImage Vars
      </button>
    </div>
    <div class="bar-group">
      <input
        bind:this={csvFileInput}
        type="file"
        accept=".csv,text/csv"
        hidden
        onchange={handleCsvUpload} />
      <button class="btn btn-sm" onclick={() => csvFileInput?.click()}>
        <Upload size={12} /> Import Slaves CSV
      </button>
      <button class="btn btn-sm" onclick={downloadCsv}>
        <Download size={12} /> Export CSV
      </button>
    </div>
  </div>
  <div class="table-host" bind:this={tableHost}></div>
</div>

<style>
  .table-root {
    display: flex;
    flex-direction: column;
    inline-size: 100%;
    block-size: 100%;
    min-inline-size: 0;
  }

  .table-bar {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    background: #11161d;
    border-bottom: 1px solid var(--border-subtle);
  }

  .bar-group {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
  }

  .table-host {
    flex: 1 1 auto;
    min-block-size: 180px;
  }
</style>
