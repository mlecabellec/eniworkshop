<script lang="ts">
  import { Save, AlertTriangle, CheckCircle2, Trash2 } from 'lucide-svelte';
  import { saveActiveDocument, workshopState } from '../lib/store.svelte';

  let saveVersionTag = $state('1.1.0');

  const selectedSlave = $derived.by(() => {
    const doc = workshopState.activeDoc;
    if (!doc || workshopState.selectedPhysAddr === null) return null;
    return doc.slaves.find((s) => s.identity.physAddr === workshopState.selectedPhysAddr) ?? null;
  });

  function updateMasterField<K extends keyof NonNullable<typeof workshopState.activeDoc>['master']>(
    key: K,
    val: NonNullable<typeof workshopState.activeDoc>['master'][K]
  ): void {
    if (!workshopState.activeDoc) return;
    workshopState.activeDoc = {
      ...workshopState.activeDoc,
      master: {
        ...workshopState.activeDoc.master,
        [key]: val
      }
    };
  }

  function updateSelectedIdentity(field: 'name' | 'physAddr' | 'autoIncAddr' | 'physics', val: string | number): void {
    const doc = workshopState.activeDoc;
    const current = selectedSlave;
    if (!doc || !current) return;

    const updatedSlaves = doc.slaves.map((s) => {
      if (s.identity.physAddr !== current.identity.physAddr) return s;
      return {
        ...s,
        identity: {
          ...s.identity,
          [field]: val
        }
      };
    });
    if (field === 'physAddr' && typeof val === 'number') {
      workshopState.selectedPhysAddr = val;
    }
    workshopState.activeDoc = { ...doc, slaves: updatedSlaves };
  }

  function updateSelectedPrevPort(physAddrStr: string, portChar: string): void {
    const doc = workshopState.activeDoc;
    const current = selectedSlave;
    if (!doc || !current) return;

    const prevPhys = Number.parseInt(physAddrStr, 10);
    const updatedSlaves = doc.slaves.map((s) => {
      if (s.identity.physAddr !== current.identity.physAddr) return s;
      return {
        ...s,
        previousPort: Number.isNaN(prevPhys)
          ? null
          : { physAddr: prevPhys, port: portChar.toUpperCase() || 'B', selected: true }
      };
    });
    workshopState.activeDoc = { ...doc, slaves: updatedSlaves };
  }

  function removeSelectedSlave(): void {
    const doc = workshopState.activeDoc;
    const current = selectedSlave;
    if (!doc || !current) return;
    const remaining = doc.slaves.filter((s) => s.identity.physAddr !== current.identity.physAddr);
    workshopState.activeDoc = { ...doc, slaves: remaining };
    workshopState.selectedPhysAddr = remaining.length > 0 ? remaining[0].identity.physAddr : null;
  }
</script>

<div class="panel-scroll">
  {#if !workshopState.activeDoc}
    <p class="muted">Select or import an ENI file to inspect and edit properties.</p>
  {:else}
    {@const doc = workshopState.activeDoc}

    <div class="form-actions">
      <div class="save-row">
        <label class="field-label" for="save-ver-input">Commit New Version</label>
        <input id="save-ver-input" class="input" style="width:110px" bind:value={saveVersionTag} />
        <button class="btn btn-primary" onclick={() => saveActiveDocument(saveVersionTag)}>
          <Save size={13} /> Save ENI Version
        </button>
      </div>
      {#if workshopState.validation}
        <span class="badge" class:badge-ok={workshopState.validation.valid} class:badge-err={!workshopState.validation.valid}>
          {#if workshopState.validation.valid}
            <CheckCircle2 size={12} /> Valid ({workshopState.validation.warningCount} warn)
          {:else}
            <AlertTriangle size={12} /> {workshopState.validation.errorCount} errors, {workshopState.validation.warningCount} warn
          {/if}
        </span>
      {/if}
    </div>

    <div class="card">
      <h4>Master Configuration (ETG.2100)</h4>
      <div class="grid-2">
        <div class="field-row">
          <label class="field-label" for="m-name">Master Name</label>
          <input
            id="m-name"
            class="input"
            value={doc.master.name}
            onchange={(e) => updateMasterField('name', e.currentTarget.value)} />
        </div>
        <div class="field-row">
          <label class="field-label" for="m-cyc">Cyclic Period (us)</label>
          <input
            id="m-cyc"
            class="input"
            type="number"
            value={doc.cyclic.cycleTimeUs}
            onchange={(e) => {
              const v = Number.parseInt(e.currentTarget.value, 10) || 1000;
              workshopState.activeDoc = { ...doc, cyclic: { ...doc.cyclic, cycleTimeUs: v } };
            }} />
        </div>
        <div class="field-row">
          <label class="field-label" for="m-src">Source MAC</label>
          <input
            id="m-src"
            class="input"
            value={doc.master.sourceMac}
            onchange={(e) => updateMasterField('sourceMac', e.currentTarget.value)} />
        </div>
        <div class="field-row">
          <label class="field-label" for="m-dst">Destination MAC</label>
          <input
            id="m-dst"
            class="input"
            value={doc.master.destinationMac}
            onchange={(e) => updateMasterField('destinationMac', e.currentTarget.value)} />
        </div>
        <div class="field-row">
          <label class="field-label" for="m-red">Cable Redundancy Loop</label>
          <select
            id="m-red"
            class="select"
            value={doc.master.redundancy.enabled ? 'true' : 'false'}
            onchange={(e) =>
              updateMasterField('redundancy', {
                ...doc.master.redundancy,
                enabled: e.currentTarget.value === 'true'
              })}>
            <option value="false">Disabled</option>
            <option value="true">Enabled (Ring Closure)</option>
          </select>
        </div>
        <div class="field-row">
          <label class="field-label" for="m-redmac">Secondary NIC MAC</label>
          <input
            id="m-redmac"
            class="input"
            value={doc.master.redundancy.secondarySourceMac}
            onchange={(e) =>
              updateMasterField('redundancy', {
                ...doc.master.redundancy,
                secondarySourceMac: e.currentTarget.value
              })} />
        </div>
      </div>
    </div>

    {#if selectedSlave}
      <div class="card" style="margin-top:12px;">
        <div class="card-hdr">
          <h4>Selected Slave #{selectedSlave.identity.physAddr}</h4>
          <button class="btn btn-danger btn-sm" onclick={removeSelectedSlave}>
            <Trash2 size={12} /> Remove Slave
          </button>
        </div>
        <div class="grid-2">
          <div class="field-row">
            <label class="field-label" for="s-name">Name</label>
            <input
              id="s-name"
              class="input"
              value={selectedSlave.identity.name}
              onchange={(e) => updateSelectedIdentity('name', e.currentTarget.value)} />
          </div>
          <div class="field-row">
            <label class="field-label" for="s-phys">PhysAddr (decimal)</label>
            <input
              id="s-phys"
              class="input"
              type="number"
              value={selectedSlave.identity.physAddr}
              onchange={(e) =>
                updateSelectedIdentity('physAddr', Number.parseInt(e.currentTarget.value, 10) || 1001)} />
          </div>
          <div class="field-row">
            <label class="field-label" for="s-physics">ESC Port Physics (Y/K/F/H)</label>
            <input
              id="s-physics"
              class="input"
              value={selectedSlave.identity.physics}
              onchange={(e) => updateSelectedIdentity('physics', e.currentTarget.value)} />
          </div>
          <div class="field-row">
            <label class="field-label" for="s-prev-addr">PreviousPort Upstream PhysAddr</label>
            <input
              id="s-prev-addr"
              class="input"
              value={selectedSlave.previousPort ? String(selectedSlave.previousPort.physAddr) : ''}
              onchange={(e) =>
                updateSelectedPrevPort(
                  e.currentTarget.value,
                  selectedSlave.previousPort?.port ?? 'B'
                )} />
          </div>
          <div class="field-row">
            <label class="field-label" for="s-prev-port">PreviousPort Port (A/B/C/D)</label>
            <select
              id="s-prev-port"
              class="select"
              value={selectedSlave.previousPort?.port ?? 'B'}
              onchange={(e) =>
                updateSelectedPrevPort(
                  selectedSlave.previousPort ? String(selectedSlave.previousPort.physAddr) : '',
                  e.currentTarget.value
                )}>
              <option value="A">Port A</option>
              <option value="B">Port B</option>
              <option value="C">Port C</option>
              <option value="D">Port D</option>
            </select>
          </div>
          <div class="field-row">
            <label class="field-label" for="s-mbx">Mailbox &amp; DC Summary</label>
            <input
              id="s-mbx"
              class="input"
              readonly
              value={`${selectedSlave.mailbox.protocols.join('/') || 'No Mbx'} | CoE InitCmds=${selectedSlave.mailbox.coeInitCmds.length} | DC=${selectedSlave.dc.enabled ? selectedSlave.dc.cycleTime0 + 'ns' : 'off'}`} />
          </div>
        </div>
      </div>
    {/if}

    {#if workshopState.validation && workshopState.validation.issues.length > 0}
      <div class="card" style="margin-top:12px;">
        <h4>EtherCAT Validation Diagnostics ({workshopState.validation.issues.length})</h4>
        <div class="issues-list">
          {#each workshopState.validation.issues as issue}
            <div class="issue-row">
              <span
                class="badge"
                class:badge-err={issue.severity === 'ERROR'}
                class:badge-warn={issue.severity === 'WARNING'}
                class:badge-info={issue.severity === 'INFO'}>
                {issue.severity}
              </span>
              <code>{issue.ruleId}</code>
              <strong>{issue.target}</strong>
              <span>{issue.message}</span>
              <span class="muted">[{issue.specificationRef}]</span>
            </div>
          {/each}
        </div>
      </div>
    {/if}
  {/if}
</div>

<style>
  h4 {
    margin: 0 0 10px 0;
    font-size: 12px;
    color: var(--accent-cyan);
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }

  .form-actions,
  .save-row,
  .card-hdr {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 10px;
  }

  .issues-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
    max-block-size: 240px;
    overflow: auto;
  }

  .issue-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    font-size: 11px;
    border-bottom: 1px solid rgba(48, 54, 61, 0.5);
    padding-bottom: 4px;
  }

  .muted {
    color: var(--text-muted);
  }
</style>
