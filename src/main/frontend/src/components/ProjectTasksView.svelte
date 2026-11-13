<script lang="ts">
  import { onMount } from 'svelte';
  import { CheckSquare, Layers, Activity, ShieldCheck, RefreshCw, Plus } from 'lucide-svelte';
  import { eniApi } from '../lib/api';
  import {
    changeActiveWorkRef,
    pushLocalEvent,
    refreshProjectOverview,
    workshopState
  } from '../lib/store.svelte';
  import type { FeatureItem, TaskItem, TraceabilityAudit, WorkItemStatus } from '../lib/types';

  type SubTab = 'tasks' | 'features' | 'activities' | 'audit';

  const STATUSES: WorkItemStatus[] = ['BACKLOG', 'IN_PROGRESS', 'IN_REVIEW', 'BLOCKED', 'DONE'];

  let activeTab: SubTab = $state('tasks');
  let busy = $state(false);
  let statusMessage = $state('');
  let filterWorkRef = $state('');

  // New Task form
  let showNewTask = $state(false);
  let newTaskTitle = $state('');
  let newTaskFeature = $state('FR-00014');
  let newTaskGoal = $state('');

  // New Feature form
  let showNewFeature = $state(false);
  let newFeatTitle = $state('');
  let newFeatReq = $state('REQ-00014');
  let newFeatScope = $state('fr.lecabellec.eniworkshop');

  // Manual Activity Log form
  let actRef = $state('TSK-00014');
  let actAction = $state('DESIGN_NOTE');
  let actTarget = $state('Workbench');
  let actSummary = $state('');

  const overview = $derived(workshopState.projectOverview);
  const features = $derived<FeatureItem[]>(overview?.features ?? []);
  const tasks = $derived<TaskItem[]>(overview?.tasks ?? []);
  const activities = $derived(
    (overview?.activities ?? []).filter((a) => !filterWorkRef || a.workRefId === filterWorkRef)
  );
  const audit = $derived<TraceabilityAudit | null>(overview?.traceability ?? null);

  function statusBadgeClass(status: WorkItemStatus): string {
    switch (status) {
      case 'DONE':
        return 'badge badge-ok';
      case 'IN_PROGRESS':
      case 'IN_REVIEW':
        return 'badge badge-info';
      case 'BLOCKED':
        return 'badge badge-err';
      default:
        return 'badge badge-warn';
    }
  }

  onMount(() => {
    actRef = workshopState.activeWorkRef;
    void refreshProjectOverview();
  });

  async function reload(): Promise<void> {
    busy = true;
    try {
      await refreshProjectOverview();
      statusMessage = 'Refreshed project board.';
    } finally {
      busy = false;
    }
  }

  async function handleTaskStatusChange(taskId: string, nextStatus: WorkItemStatus): Promise<void> {
    busy = true;
    try {
      await eniApi.projectUpdateTaskStatus(taskId, nextStatus);
      await refreshProjectOverview();
      statusMessage = `Updated ${taskId} -> ${nextStatus}`;
      pushLocalEvent('TASK_STATUS', taskId, statusMessage);
    } catch (err) {
      statusMessage = err instanceof Error ? err.message : String(err);
    } finally {
      busy = false;
    }
  }

  async function handleFeatureStatusChange(featureId: string, nextStatus: WorkItemStatus): Promise<void> {
    busy = true;
    try {
      await eniApi.projectUpdateFeatureStatus(featureId, nextStatus);
      await refreshProjectOverview();
      statusMessage = `Updated ${featureId} -> ${nextStatus}`;
      pushLocalEvent('FEATURE_STATUS', featureId, statusMessage);
    } catch (err) {
      statusMessage = err instanceof Error ? err.message : String(err);
    } finally {
      busy = false;
    }
  }

  async function submitNewTask(e: SubmitEvent): Promise<void> {
    e.preventDefault();
    if (!newTaskTitle.trim()) return;
    busy = true;
    try {
      const created = await eniApi.projectCreateTask(
        newTaskTitle.trim(),
        newTaskFeature.trim() || 'FR-00014',
        '',
        newTaskGoal.trim()
      );
      newTaskTitle = '';
      newTaskGoal = '';
      showNewTask = false;
      await changeActiveWorkRef(created.id);
      actRef = created.id;
      statusMessage = `Created ${created.id} and activated work ref.`;
    } catch (err) {
      statusMessage = err instanceof Error ? err.message : String(err);
    } finally {
      busy = false;
    }
  }

  async function submitNewFeature(e: SubmitEvent): Promise<void> {
    e.preventDefault();
    if (!newFeatTitle.trim()) return;
    busy = true;
    try {
      const created = await eniApi.projectCreateFeature(
        newFeatTitle.trim(),
        newFeatReq.trim() || 'REQ-00014',
        newFeatScope.trim() || 'fr.lecabellec.eniworkshop'
      );
      newFeatTitle = '';
      showNewFeature = false;
      await refreshProjectOverview();
      statusMessage = `Created feature ${created.id}`;
    } catch (err) {
      statusMessage = err instanceof Error ? err.message : String(err);
    } finally {
      busy = false;
    }
  }

  async function submitActivity(e: SubmitEvent): Promise<void> {
    e.preventDefault();
    if (!actSummary.trim()) return;
    busy = true;
    try {
      const refToUse = actRef.trim() || workshopState.activeWorkRef;
      await eniApi.projectLogActivity(refToUse, actAction.trim() || 'NOTE', actTarget.trim() || 'UI', actSummary.trim());
      actSummary = '';
      await refreshProjectOverview();
      statusMessage = `Logged activity under ${refToUse}.`;
    } catch (err) {
      statusMessage = err instanceof Error ? err.message : String(err);
    } finally {
      busy = false;
    }
  }
</script>

<div class="panel-scroll proj-wrap">
  <div class="proj-header">
    <div class="proj-title-row">
      <strong>Task, Feature &amp; Activity Governance</strong>
      <span class="badge badge-info" title="Active Task/Feature attached to workbench HTTP requests">
        Active Work Ref: {workshopState.activeWorkRef}
      </span>
      {#if audit}
        <span class={audit.untaggedFiles.length === 0 && audit.unknownWorkRefs.length === 0 ? 'badge badge-ok' : 'badge badge-warn'}>
          CS-0010: {audit.tracedJavaFiles}/{audit.totalJavaFiles} ({audit.coveragePercent}%)
        </span>
      {/if}
    </div>

    <div class="proj-actions">
      <select
        class="select"
        aria-label="Set Active Work Reference"
        value={workshopState.activeWorkRef}
        onchange={(e) => void changeActiveWorkRef(e.currentTarget.value)}>
        {#each tasks as task}
          <option value={task.id}>{task.id} - {task.title.slice(0, 44)}</option>
        {/each}
        {#each features as feat}
          <option value={feat.id}>{feat.id} - {feat.title.slice(0, 44)}</option>
        {/each}
      </select>
      <button class="btn btn-sm" disabled={busy} onclick={reload}>
        <RefreshCw size={12} /> Sync
      </button>
    </div>
  </div>

  {#if statusMessage}
    <div class="status-banner">{statusMessage}</div>
  {/if}

  <div class="tab-bar" role="tablist" aria-label="Project management sections">
    <button
      role="tab"
      aria-selected={activeTab === 'tasks'}
      class={`btn btn-sm ${activeTab === 'tasks' ? 'btn-primary' : ''}`}
      onclick={() => (activeTab = 'tasks')}>
      <CheckSquare size={12} /> Tasks ({tasks.length})
    </button>
    <button
      role="tab"
      aria-selected={activeTab === 'features'}
      class={`btn btn-sm ${activeTab === 'features' ? 'btn-primary' : ''}`}
      onclick={() => (activeTab = 'features')}>
      <Layers size={12} /> Features ({features.length})
    </button>
    <button
      role="tab"
      aria-selected={activeTab === 'activities'}
      class={`btn btn-sm ${activeTab === 'activities' ? 'btn-primary' : ''}`}
      onclick={() => (activeTab = 'activities')}>
      <Activity size={12} /> Activity Feed ({activities.length})
    </button>
    <button
      role="tab"
      aria-selected={activeTab === 'audit'}
      class={`btn btn-sm ${activeTab === 'audit' ? 'btn-primary' : ''}`}
      onclick={() => (activeTab = 'audit')}>
      <ShieldCheck size={12} /> Traceability Audit
    </button>
  </div>

  {#if activeTab === 'tasks'}
    <div class="section-toolbar">
      <span class="field-label">Markdown synced dossiers (`doc/docs/project/tasks/` &amp; `TODO.md`)</span>
      <button class="btn btn-sm btn-primary" onclick={() => (showNewTask = !showNewTask)}>
        <Plus size={12} /> New Task
      </button>
    </div>

    {#if showNewTask}
      <form class="card create-form" onsubmit={submitNewTask}>
        <div class="grid-2">
          <label class="field-row">
            <span class="field-label">Task Title</span>
            <input class="input" bind:value={newTaskTitle} placeholder="e.g. Validate DC sync regression" required />
          </label>
          <label class="field-row">
            <span class="field-label">Feature ID</span>
            <select class="select" bind:value={newTaskFeature}>
              {#each features as feat}
                <option value={feat.id}>{feat.id} - {feat.title}</option>
              {/each}
            </select>
          </label>
        </div>
        <label class="field-row">
          <span class="field-label">Engineering Goal</span>
          <input class="input" bind:value={newTaskGoal} placeholder="Acceptance criteria and scope" />
        </label>
        <div class="form-actions">
          <button class="btn btn-sm btn-primary" type="submit" disabled={busy}>Create Task</button>
          <button class="btn btn-sm" type="button" onclick={() => (showNewTask = false)}>Cancel</button>
        </div>
      </form>
    {/if}

    <div class="item-list">
      {#each tasks as task}
        <div class={`card item-card ${workshopState.activeWorkRef === task.id ? 'active-card' : ''}`}>
          <div class="item-top">
            <div class="item-id-group">
              <strong class="mono">{task.id}</strong>
              <span class="badge badge-info">{task.featureId}</span>
              <span class={statusBadgeClass(task.status)}>{task.status}</span>
            </div>
            <div class="item-controls">
              <button
                class="btn btn-sm"
                onclick={() => {
                  actRef = task.id;
                  void changeActiveWorkRef(task.id);
                }}>
                {workshopState.activeWorkRef === task.id ? 'Active' : 'Set Active'}
              </button>
              <select
                class="select"
                aria-label={`Status for ${task.id}`}
                value={task.status}
                onchange={(e) => void handleTaskStatusChange(task.id, e.currentTarget.value as WorkItemStatus)}>
                {#each STATUSES as st}
                  <option value={st}>{st}</option>
                {/each}
              </select>
            </div>
          </div>
          <div class="item-title">{task.title}</div>
          <div class="item-sub">{task.goal}</div>
        </div>
      {/each}
    </div>
  {:else if activeTab === 'features'}
    <div class="section-toolbar">
      <span class="field-label">Feature Registry (`doc/docs/project/features/README.md`)</span>
      <button class="btn btn-sm btn-primary" onclick={() => (showNewFeature = !showNewFeature)}>
        <Plus size={12} /> New Feature
      </button>
    </div>

    {#if showNewFeature}
      <form class="card create-form" onsubmit={submitNewFeature}>
        <div class="grid-2">
          <label class="field-row">
            <span class="field-label">Feature Title</span>
            <input class="input" bind:value={newFeatTitle} placeholder="e.g. Live EtherCAT Scope Trigger" required />
          </label>
          <label class="field-row">
            <span class="field-label">Requirement ID</span>
            <input class="input" bind:value={newFeatReq} placeholder="REQ-00014" />
          </label>
        </div>
        <label class="field-row">
          <span class="field-label">Package / UI Scope</span>
          <input class="input" bind:value={newFeatScope} />
        </label>
        <div class="form-actions">
          <button class="btn btn-sm btn-primary" type="submit" disabled={busy}>Create Feature</button>
          <button class="btn btn-sm" type="button" onclick={() => (showNewFeature = false)}>Cancel</button>
        </div>
      </form>
    {/if}

    <div class="item-list">
      {#each features as feat}
        <div class={`card item-card ${workshopState.activeWorkRef === feat.id ? 'active-card' : ''}`}>
          <div class="item-top">
            <div class="item-id-group">
              <strong class="mono">{feat.id}</strong>
              <span class="badge">{feat.requirementId}</span>
              <span class={statusBadgeClass(feat.status)}>{feat.status}</span>
            </div>
            <div class="item-controls">
              <button
                class="btn btn-sm"
                onclick={() => {
                  actRef = feat.id;
                  void changeActiveWorkRef(feat.id);
                }}>
                {workshopState.activeWorkRef === feat.id ? 'Active' : 'Set Active'}
              </button>
              <select
                class="select"
                aria-label={`Status for ${feat.id}`}
                value={feat.status}
                onchange={(e) => void handleFeatureStatusChange(feat.id, e.currentTarget.value as WorkItemStatus)}>
                {#each STATUSES as st}
                  <option value={st}>{st}</option>
                {/each}
              </select>
            </div>
          </div>
          <div class="item-title">{feat.title}</div>
          <div class="item-sub mono">Scope: {feat.scope} | Tasks: {feat.taskIds.join(', ') || '-'}</div>
        </div>
      {/each}
    </div>
  {:else if activeTab === 'activities'}
    <form class="card create-form" onsubmit={submitActivity}>
      <div class="grid-2">
        <label class="field-row">
          <span class="field-label">Work Ref (TSK-* / FR-*)</span>
          <select class="select" bind:value={actRef}>
            {#each tasks as t}
              <option value={t.id}>{t.id} ({t.featureId})</option>
            {/each}
            {#each features as f}
              <option value={f.id}>{f.id}</option>
            {/each}
          </select>
        </label>
        <label class="field-row">
          <span class="field-label">Action Tag</span>
          <input class="input" bind:value={actAction} placeholder="REVIEW / BENCH / NOTE" />
        </label>
      </div>
      <div class="grid-2">
        <label class="field-row">
          <span class="field-label">Target</span>
          <input class="input" bind:value={actTarget} placeholder="ENI / Topology / Component" />
        </label>
        <label class="field-row">
          <span class="field-label">Activity Summary</span>
          <input class="input" bind:value={actSummary} placeholder="What was verified or modified..." required />
        </label>
      </div>
      <div class="form-actions">
        <button class="btn btn-sm btn-primary" type="submit" disabled={busy}>Log Tracked Activity</button>
        <select class="select" aria-label="Filter activities by work ref" bind:value={filterWorkRef}>
          <option value="">All Work Refs</option>
          {#each tasks as t}
            <option value={t.id}>{t.id}</option>
          {/each}
          {#each features as f}
            <option value={f.id}>{f.id}</option>
          {/each}
        </select>
      </div>
    </form>

    <div class="item-list">
      {#if activities.length === 0}
        <div class="card item-sub">No recorded activities matching filter. Mutating API actions &amp; CLI runs appear here automatically.</div>
      {:else}
        {#each activities as act}
          <div class="card activity-row">
            <span class="badge badge-info mono">{act.workRefId}</span>
            <span class="badge">{act.source}</span>
            <strong class="mono">{act.action}</strong>
            <span class="activity-summary">{act.summary}</span>
            <span class="activity-time mono">{act.timestamp.replace('T', ' ').slice(0, 19)}Z</span>
          </div>
        {/each}
      {/if}
    </div>
  {:else if activeTab === 'audit'}
    {#if audit}
      <div class="card create-form">
        <div class="item-top">
          <strong>Codebase Traceability Coverage (`CS-0010`)</strong>
          <span class={audit.untaggedFiles.length === 0 ? 'badge badge-ok' : 'badge badge-warn'}>
            {audit.tracedJavaFiles} / {audit.totalJavaFiles} Java classes ({audit.coveragePercent}%)
          </span>
        </div>
        <div class="item-sub">
          Every Java source and test file is scanned for structured `REQ-XXXXX`, `FR-XXXXX`, `TSK-XXXXX`, `BUG-XXXXX`, `FIX-XXXXX`, or `CS-XXXX` markers and validated against `doc/docs/project/`.
        </div>
        {#if audit.untaggedFiles.length > 0}
          <div class="audit-warn">
            <strong>Untagged Java Files ({audit.untaggedFiles.length}):</strong>
            {#each audit.untaggedFiles as f}
              <div class="mono">{f}</div>
            {/each}
          </div>
        {/if}
        {#if audit.unknownWorkRefs.length > 0}
          <div class="audit-warn">
            <strong>Unregistered References ({audit.unknownWorkRefs.length}):</strong>
            {#each audit.unknownWorkRefs as r}
              <div class="mono">{r}</div>
            {/each}
          </div>
        {/if}
        {#if audit.untaggedFiles.length === 0 && audit.unknownWorkRefs.length === 0}
          <div class="badge badge-ok">100% traceability verified with zero unknown work references.</div>
        {/if}
      </div>
    {/if}
  {/if}
</div>

<style>
  .proj-wrap {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .proj-header,
  .section-toolbar,
  .item-top,
  .proj-title-row,
  .proj-actions,
  .item-id-group,
  .item-controls,
  .tab-bar,
  .form-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .proj-header,
  .section-toolbar,
  .item-top {
    justify-content: space-between;
  }

  .status-banner {
    padding: 6px 10px;
    border-radius: var(--radius-sm);
    background: rgba(56, 139, 253, 0.12);
    border: 1px solid rgba(56, 139, 253, 0.35);
    color: #9ecbff;
    font-size: 12px;
  }

  .create-form {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .item-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .item-card {
    display: flex;
    flex-direction: column;
    gap: 5px;
  }

  .active-card {
    border-color: var(--accent-blue);
    box-shadow: 0 0 0 1px rgba(56, 139, 253, 0.25);
  }

  .item-title {
    font-weight: 600;
    font-size: 13px;
  }

  .item-sub {
    font-size: 11px;
    color: var(--text-muted);
  }

  .activity-row {
    display: grid;
    grid-template-columns: auto auto auto 1fr auto;
    align-items: center;
    gap: 8px;
  }

  .activity-summary {
    min-inline-size: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .activity-time {
    font-size: 11px;
    color: var(--text-muted);
  }

  .audit-warn {
    padding: 8px;
    border-radius: var(--radius-sm);
    background: rgba(248, 81, 73, 0.12);
    border: 1px solid rgba(248, 81, 73, 0.4);
    font-size: 11px;
  }

  .mono {
    font-family: var(--font-mono);
  }
</style>
