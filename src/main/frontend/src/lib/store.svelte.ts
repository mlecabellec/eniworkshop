import { eniApi, setActiveWorkRefHeader } from './api';
import type {
  EniCatalogMetadata,
  EniDocument,
  EniSlave,
  MappedFrame,
  ProjectOverview,
  QuasarBusStatus,
  SlaveCatalogEntry,
  TopologyGraph,
  ValidationReport,
  WorkshopEvent
} from './types';

export const workshopState = $state({
  catalog: [] as EniCatalogMetadata[],
  activeId: null as string | null,
  activeDoc: null as EniDocument | null,
  validation: null as ValidationReport | null,
  topology: null as TopologyGraph | null,
  frames: [] as MappedFrame[],
  slaveCatalog: [] as SlaveCatalogEntry[],
  selectedPhysAddr: null as number | null,
  quasarStatus: null as QuasarBusStatus | null,
  projectOverview: null as ProjectOverview | null,
  activeWorkRef: 'TSK-00014',
  events: [] as WorkshopEvent[],
  busy: false,
  errorMessage: null as string | null
});

export async function refreshProjectOverview(): Promise<void> {
  try {
    const overview = await eniApi.projectOverview();
    workshopState.projectOverview = overview;
    if (overview.activeWorkRef) {
      workshopState.activeWorkRef = overview.activeWorkRef;
      setActiveWorkRefHeader(overview.activeWorkRef);
    }
  } catch (err) {
    workshopState.errorMessage = err instanceof Error ? err.message : String(err);
  }
}

export async function changeActiveWorkRef(workRefId: string): Promise<void> {
  workshopState.activeWorkRef = workRefId;
  setActiveWorkRefHeader(workRefId);
  try {
    await eniApi.projectSetActiveRef(workRefId);
    await refreshProjectOverview();
  } catch (err) {
    workshopState.errorMessage = err instanceof Error ? err.message : String(err);
  }
}

export function pushLocalEvent(eventType: string, targetId: string, summary: string): void {
  workshopState.events = [
    { eventType, targetId, summary, timestamp: new Date().toISOString() },
    ...workshopState.events.slice(0, 29)
  ];
}

export async function refreshAllCatalogs(): Promise<void> {
  const [eniList, slaves] = await Promise.all([
    eniApi.listCatalog(),
    eniApi.listSlaveCatalog()
  ]);
  workshopState.catalog = eniList;
  workshopState.slaveCatalog = slaves;
}

export async function selectEni(id: string): Promise<void> {
  workshopState.busy = true;
  workshopState.errorMessage = null;
  try {
    workshopState.activeId = id;
    const [doc, val, topo, frames] = await Promise.all([
      eniApi.getDocument(id),
      eniApi.validate(id),
      eniApi.getTopology(id),
      eniApi.getFrames(id)
    ]);
    workshopState.activeDoc = doc;
    workshopState.validation = val;
    workshopState.topology = topo;
    workshopState.frames = frames;
    workshopState.selectedPhysAddr =
      doc.slaves.length > 0 ? doc.slaves[0].identity.physAddr : null;
  } catch (err) {
    workshopState.errorMessage = err instanceof Error ? err.message : String(err);
  } finally {
    workshopState.busy = false;
  }
}

export async function saveActiveDocument(newVersion: string): Promise<void> {
  if (!workshopState.activeId || !workshopState.activeDoc) return;
  workshopState.busy = true;
  workshopState.errorMessage = null;
  try {
    const saved = await eniApi.saveVersion(workshopState.activeId, newVersion, workshopState.activeDoc);
    await refreshAllCatalogs();
    await selectEni(saved.id);
    pushLocalEvent('VERSION_SAVED', saved.id, `Saved ${saved.name} v${saved.version}`);
  } catch (err) {
    workshopState.errorMessage = err instanceof Error ? err.message : String(err);
  } finally {
    workshopState.busy = false;
  }
}

export function appendSlaveFromCatalog(entry: SlaveCatalogEntry): void {
  if (!workshopState.activeDoc) return;
  const slaves = workshopState.activeDoc.slaves;
  const maxPhys = slaves.reduce((acc, s) => Math.max(acc, s.identity.physAddr), 1000);
  const nextPhys = maxPhys + 1;
  const nextAutoInc = slaves.length === 0 ? 0 : -slaves.length;
  const prevPhys =
    workshopState.selectedPhysAddr ??
    (slaves.length > 0 ? slaves[slaves.length - 1].identity.physAddr : null);

  const newSlave: EniSlave = {
    identity: {
      name: `Slave_${nextPhys} [${entry.displayName}]`,
      physAddr: nextPhys,
      autoIncAddr: nextAutoInc,
      physics: entry.physics || 'YY',
      vendorId: entry.vendorId,
      productCode: entry.productCode,
      revisionNo: entry.revisionNo,
      serialNo: 0
    },
    previousPort: prevPhys !== null ? { physAddr: prevPhys, port: 'B', selected: true } : null,
    hotConnect: null,
    processData: {
      sendRegions: [],
      recvRegions: [],
      syncManagers: entry.defaultSyncManagers,
      rxPdos: entry.defaultRxPdos,
      txPdos: entry.defaultTxPdos
    },
    initCmds: [],
    mailbox: {
      dataLinkLayer: entry.protocols.length > 0,
      standard: {
        sendStart: 0x1000,
        sendLength: 128,
        recvStart: 0x1080,
        recvLength: 128,
        statusBitAddr: 0
      },
      bootstrap: null,
      aoeNetId: '',
      protocols: entry.protocols,
      coeProfileChannels: [],
      coeInitCmds: entry.defaultCoeInitCmds
    },
    dc: {
      enabled: false,
      potentialReferenceClock: false,
      referenceClock: false,
      cycleTime0: 0,
      cycleTime1: 0,
      shiftTime0: 0,
      shiftTime1: 0
    }
  };

  workshopState.activeDoc = {
    ...workshopState.activeDoc,
    slaves: [...slaves, newSlave]
  };
  workshopState.selectedPhysAddr = nextPhys;
  pushLocalEvent('SLAVE_ADDED', String(nextPhys), `Appended ${newSlave.identity.name}`);
}
