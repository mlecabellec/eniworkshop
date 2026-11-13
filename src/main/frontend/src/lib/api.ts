import { Client } from '@stomp/stompjs';
import type {
  ActivityRecord,
  ComparisonResult,
  EniCatalogMetadata,
  EniDocument,
  EniSlave,
  FeatureItem,
  ImportResult,
  MappedFrame,
  ProjectOverview,
  QuasarBusStatus,
  QuasarCompareResult,
  QuasarScanResult,
  SlaveCatalogEntry,
  TaskItem,
  TopologyGraph,
  TraceabilityAudit,
  ValidationReport,
  WorkItemStatus,
  WorkshopEvent
} from './types';

let activeWorkRefHeader = 'TSK-00014';

export function setActiveWorkRefHeader(workRefId: string): void {
  if (workRefId && workRefId.trim()) {
    activeWorkRefHeader = workRefId.trim();
  }
}

export function getActiveWorkRefHeader(): string {
  return activeWorkRefHeader;
}

function withWorkRefHeaders(init?: RequestInit): RequestInit {
  const headers = new Headers(init?.headers ?? undefined);
  if (!headers.has('X-Eni-Work-Ref')) {
    headers.set('X-Eni-Work-Ref', activeWorkRefHeader);
  }
  return { ...init, headers };
}

async function jsonFetch<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, withWorkRefHeaders(init));
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`${res.status} ${res.statusText}: ${body}`);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

async function textFetch(url: string, init?: RequestInit): Promise<string> {
  const res = await fetch(url, init);
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`${res.status} ${res.statusText}: ${body}`);
  }
  return res.text();
}

export const eniApi = {
  listCatalog: () => jsonFetch<EniCatalogMetadata[]>('/api/eni'),

  importFile: (file: File, reference: string, name: string, version: string, tags = '') => {
    const form = new FormData();
    form.append('file', file);
    form.append('reference', reference);
    form.append('name', name);
    form.append('version', version);
    form.append('tags', tags);
    return jsonFetch<ImportResult>('/api/eni/import', { method: 'POST', body: form });
  },

  getDocument: (id: string) => jsonFetch<EniDocument>(`/api/eni/${id}/document`),

  saveVersion: (id: string, version: string, doc: EniDocument) =>
    jsonFetch<EniCatalogMetadata>(`/api/eni/${id}/document?version=${encodeURIComponent(version)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(doc)
    }),

  duplicate: (id: string, reference: string, name: string, version: string) =>
    jsonFetch<EniCatalogMetadata>(`/api/eni/${id}/duplicate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reference, name, version })
    }),

  rename: (id: string, name: string, reference: string) =>
    jsonFetch<EniCatalogMetadata>(`/api/eni/${id}/rename`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, reference })
    }),

  deleteEni: (id: string) =>
    fetch(`/api/eni/${id}`, withWorkRefHeaders({ method: 'DELETE' })).then((r) => {
      if (!r.ok) throw new Error(`Delete failed: ${r.status}`);
    }),

  validate: (id: string) => jsonFetch<ValidationReport>(`/api/eni/${id}/validate`),

  getTopology: (id: string) => jsonFetch<TopologyGraph>(`/api/eni/${id}/topology`),

  aggregate: (payload: {
    baseId: string;
    addonIds: string[];
    reference: string;
    name: string;
    version: string;
    attachToPhysAddr: number;
    attachPort: string;
    enableCableRedundancy: boolean;
    secondarySourceMac: string;
  }) =>
    jsonFetch<EniCatalogMetadata>('/api/eni/aggregate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    }),

  getFrames: (id: string) => jsonFetch<MappedFrame[]>(`/api/eni/${id}/frames`),

  exportCsv: (id: string, kind: 'slaves' | 'variables') =>
    textFetch(`/api/eni/${id}/csv?kind=${kind}`),

  parseCsvSlaves: (csvText: string) =>
    jsonFetch<EniSlave[]>('/api/eni/parse-csv', {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain' },
      body: csvText
    }),

  getPlantUml: (id: string, kind: 'topology' | 'esm') =>
    textFetch(`/api/eni/${id}/plantuml?kind=${kind}`),

  renderPlantUmlSvg: (source: string) =>
    textFetch('/api/diagrams/plantuml', {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain' },
      body: source
    }),

  listSlaveCatalog: () => jsonFetch<SlaveCatalogEntry[]>('/api/catalog/slaves'),

  importEsi: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return jsonFetch<{ importedCount: number; source: string }>('/api/catalog/import-esi', {
      method: 'POST',
      body: form
    });
  },

  quasarConnect: (endpoint = '') =>
    jsonFetch<QuasarBusStatus>(`/api/quasar/connect?endpoint=${encodeURIComponent(endpoint)}`, {
      method: 'POST'
    }),

  quasarDisconnect: () => fetch('/api/quasar/disconnect', { method: 'POST' }),

  quasarStatus: () => jsonFetch<QuasarBusStatus>('/api/quasar/status'),

  quasarForceInit: () => jsonFetch<{ result: string }>('/api/quasar/force-init', { method: 'POST' }),

  quasarReconfigureSlave: (slaveIndex: number) =>
    jsonFetch<{ result: string }>(`/api/quasar/slaves/${slaveIndex}/reconfigure`, { method: 'POST' }),

  quasarScan: (masterName: string, reference: string) =>
    jsonFetch<QuasarScanResult>(
      `/api/quasar/scan?masterName=${encodeURIComponent(masterName)}&reference=${encodeURIComponent(reference)}`,
      { method: 'POST' }
    ),

  quasarTestEni: (eniId: string) =>
    jsonFetch<QuasarCompareResult>(`/api/quasar/test/${eniId}`, { method: 'POST' }),

  compareEnis: (leftId: string, rightId: string) =>
    jsonFetch<ComparisonResult>(
      `/api/compare/eni?leftId=${encodeURIComponent(leftId)}&rightId=${encodeURIComponent(rightId)}`
    ),

  compareTopologies: (leftId: string, rightId: string) =>
    jsonFetch<ComparisonResult>(
      `/api/compare/topology?leftId=${encodeURIComponent(leftId)}&rightId=${encodeURIComponent(rightId)}`
    ),

  compareSlaves: (leftId: string, leftPhys: number, rightId: string, rightPhys: number) =>
    jsonFetch<ComparisonResult>(
      `/api/compare/slave?leftId=${encodeURIComponent(leftId)}&leftPhys=${leftPhys}&rightId=${encodeURIComponent(rightId)}&rightPhys=${rightPhys}`
    ),

  compareHotConnect: (leftId: string, leftGroup: string, rightId: string, rightGroup: string) =>
    jsonFetch<ComparisonResult>(
      `/api/compare/hot-connect?leftId=${encodeURIComponent(leftId)}&leftGroup=${encodeURIComponent(leftGroup)}&rightId=${encodeURIComponent(rightId)}&rightGroup=${encodeURIComponent(rightGroup)}`
    ),

  projectOverview: () => jsonFetch<ProjectOverview>('/api/project/overview'),

  projectCreateFeature: (title: string, requirementId: string, scope: string) =>
    jsonFetch<FeatureItem>('/api/project/features', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id: null, title, requirementId, scope })
    }),

  projectUpdateFeatureStatus: (featureId: string, status: WorkItemStatus) =>
    jsonFetch<FeatureItem>(`/api/project/features/${encodeURIComponent(featureId)}/status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status })
    }),

  projectCreateTask: (title: string, featureId: string, requirementId: string, goal: string) =>
    jsonFetch<TaskItem>('/api/project/tasks', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id: null, title, featureId, requirementId, goal })
    }),

  projectUpdateTaskStatus: (taskId: string, status: WorkItemStatus) =>
    jsonFetch<TaskItem>(`/api/project/tasks/${encodeURIComponent(taskId)}/status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status })
    }),

  projectSetActiveRef: (workRefId: string) =>
    jsonFetch<{ activeWorkRef: string }>('/api/project/active-ref', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ workRefId })
    }),

  projectListActivities: (workRef?: string, limit = 100) => {
    const query = workRef
      ? `?workRef=${encodeURIComponent(workRef)}&limit=${limit}`
      : `?limit=${limit}`;
    return jsonFetch<ActivityRecord[]>(`/api/project/activities${query}`);
  },

  projectLogActivity: (workRefId: string, action: string, target: string, summary: string) =>
    jsonFetch<ActivityRecord>('/api/project/activities', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ workRefId, action, target, summary })
    }),

  projectAudit: () => jsonFetch<TraceabilityAudit>('/api/project/traceability')
};

export function connectWorkshopStomp(onEvent: (evt: WorkshopEvent) => void): () => void {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const brokerURL = `${proto}//${window.location.host}/ws-stomp`;
  const client = new Client({
    brokerURL,
    reconnectDelay: 5000,
    onConnect: () => {
      client.subscribe('/topic/eni-events', (msg) => {
        try {
          onEvent(JSON.parse(msg.body) as WorkshopEvent);
        } catch {
          // ignore malformed messages
        }
      });
    },
    onStompError: () => {
      // silent backoff when offline
    }
  });
  client.activate();
  return () => {
    void client.deactivate();
  };
}
