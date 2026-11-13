<script lang="ts">
  import { FolderTree, Cpu, Layers, ShieldAlert } from 'lucide-svelte';
  import { workshopState } from '../lib/store.svelte';

  let search = $state('');
  let expandedSlaves = $state<Record<number, boolean>>({});
  let showMaster = $state(true);
  let showCyclic = $state(true);
  let showProcessImage = $state(false);

  const filteredSlaves = $derived.by(() => {
    const doc = workshopState.activeDoc;
    if (!doc) return [];
    const q = search.trim().toLowerCase();
    if (!q) return doc.slaves;
    return doc.slaves.filter(
      (s) =>
        s.identity.name.toLowerCase().includes(q) ||
        String(s.identity.physAddr).includes(q) ||
        (s.hotConnect?.groupName.toLowerCase().includes(q) ?? false)
    );
  });

  function toggleSlave(physAddr: number): void {
    expandedSlaves = {
      ...expandedSlaves,
      [physAddr]: !expandedSlaves[physAddr]
    };
    workshopState.selectedPhysAddr = physAddr;
  }
</script>

<div class="panel-scroll">
  <div class="tree-header">
    <div class="title-row">
      <FolderTree size={14} />
      <strong>ENI Hierarchical Bus Tree</strong>
    </div>
    <input
      class="input"
      type="search"
      placeholder="Filter slave name, PhysAddr, HotConnect..."
      bind:value={search} />
  </div>

  {#if !workshopState.activeDoc}
    <p class="muted">No ENI document loaded.</p>
  {:else}
    {@const doc = workshopState.activeDoc}
    <div class="tree-node">
      <button class="tree-toggle" onclick={() => (showMaster = !showMaster)}>
        {showMaster ? 'v' : '>'} <strong>EtherCATConfig v{doc.version}</strong> :: {doc.master.name}
      </button>
      {#if showMaster}
        <ul class="tree-list">
          <li>
            MAC: <code>{doc.master.sourceMac}</code> -&gt; <code>{doc.master.destinationMac}</code>
            (EtherType <code>0x{doc.master.etherType.toString(16)}</code>)
          </li>
          <li>
            Cable Redundancy:
            <span class="badge" class:badge-ok={doc.master.redundancy.enabled}>
              {doc.master.redundancy.enabled ? 'ENABLED' : 'DISABLED'}
            </span>
          </li>
          <li>Master InitCmds: {doc.master.initCmds.length}</li>
        </ul>
      {/if}
    </div>

    <div class="tree-node">
      <div class="section-hdr">
        <Cpu size={13} /> Slaves ({filteredSlaves.length} / {doc.slaves.length})
      </div>
      {#each filteredSlaves as slave}
        {@const selected = workshopState.selectedPhysAddr === slave.identity.physAddr}
        <div class="slave-branch" class:selected>
          <button class="tree-toggle" onclick={() => toggleSlave(slave.identity.physAddr)}>
            <span>{expandedSlaves[slave.identity.physAddr] ? 'v' : '>'}</span>
            <strong>[{slave.identity.physAddr}]</strong>
            <span>{slave.identity.name}</span>
            <span class="badge badge-info">{slave.identity.physics || '?'}</span>
            {#if slave.hotConnect}
              <span class="badge badge-warn">HC: {slave.hotConnect.groupName}</span>
            {/if}
          </button>

          {#if expandedSlaves[slave.identity.physAddr]}
            <ul class="tree-list">
              <li>
                Vendor=0x{slave.identity.vendorId.toString(16)} Product=0x{slave.identity.productCode.toString(16)} Rev=0x{slave.identity.revisionNo.toString(16)}
              </li>
              <li>
                PrevPort: {slave.previousPort ? `${slave.previousPort.physAddr}:${slave.previousPort.port}` : 'Master Root'}
              </li>
              <li>
                SyncManagers ({slave.processData.syncManagers.length}) | RxPDOs ({slave.processData.rxPdos.length}) | TxPDOs ({slave.processData.txPdos.length})
              </li>
              <li>
                Mailbox: {slave.mailbox.protocols.join(', ') || 'None'} | CoE InitCmds: {slave.mailbox.coeInitCmds.length} | ESC InitCmds: {slave.initCmds.length}
              </li>
              <li>
                DC: {slave.dc.enabled ? `Sync0=${slave.dc.cycleTime0}ns Ref=${slave.dc.referenceClock}` : 'Disabled'}
              </li>
            </ul>
          {/if}
        </div>
      {/each}
    </div>

    <div class="tree-node">
      <button class="tree-toggle" onclick={() => (showCyclic = !showCyclic)}>
        <Layers size={13} />
        {showCyclic ? 'v' : '>'} Cyclic Schedule (@ {doc.cyclic.cycleTimeUs} us, {doc.cyclic.frames.length} frames)
      </button>
      {#if showCyclic}
        <ul class="tree-list">
          {#each doc.cyclic.frames as frame, idx}
            <li>
              Frame #{idx}: {frame.commands.length} datagrams
              ({frame.commands.map((c) => `${c.cmd}[${c.dataLength}B]`).join(', ')})
            </li>
          {/each}
        </ul>
      {/if}
    </div>

    <div class="tree-node">
      <button class="tree-toggle" onclick={() => (showProcessImage = !showProcessImage)}>
        <ShieldAlert size={13} />
        {showProcessImage ? 'v' : '>'} ProcessImage (In={doc.processImage.inputsByteSize}B / {doc.processImage.inputs.length} vars, Out={doc.processImage.outputsByteSize}B / {doc.processImage.outputs.length} vars)
      </button>
      {#if showProcessImage}
        <ul class="tree-list">
          {#each doc.processImage.inputs.slice(0, 80) as v}
            <li>IN @{v.bitOffs}b ({v.bitSize}b {v.dataType}): {v.name}</li>
          {/each}
          {#each doc.processImage.outputs.slice(0, 80) as v}
            <li>OUT @{v.bitOffs}b ({v.bitSize}b {v.dataType}): {v.name}</li>
          {/each}
        </ul>
      {/if}
    </div>
  {/if}
</div>

<style>
  .tree-header {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-bottom: 10px;
  }

  .title-row,
  .section-hdr {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    font-weight: 600;
    margin-bottom: 6px;
  }

  .tree-node {
    margin-bottom: 10px;
    border-left: 2px solid var(--border-subtle);
    padding-left: 8px;
  }

  .slave-branch {
    border-radius: var(--radius-sm);
    margin-bottom: 4px;
  }

  .slave-branch.selected {
    background: rgba(56, 139, 253, 0.14);
    border: 1px solid rgba(56, 139, 253, 0.45);
  }

  .tree-toggle {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
    width: 100%;
    text-align: left;
    background: transparent;
    color: var(--text-primary);
    border: none;
    padding: 4px 6px;
    cursor: pointer;
    font-size: 12px;
  }

  .tree-toggle:hover {
    background: rgba(255, 255, 255, 0.04);
  }

  .tree-list {
    margin: 4px 0 8px 16px;
    padding: 0;
    list-style: none;
    font-family: var(--font-mono);
    font-size: 11px;
    color: var(--text-muted);
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  .muted {
    color: var(--text-muted);
  }
</style>
