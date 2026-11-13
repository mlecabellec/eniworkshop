export interface EniSummaryStats {
  slaveCount: number;
  hotConnectGroupCount: number;
  cyclicCommandCount: number;
  inputsByteSize: number;
  outputsByteSize: number;
}

export interface EniCatalogMetadata {
  id: string;
  reference: string;
  name: string;
  version: string;
  parentId: string;
  sha256: string;
  stats: EniSummaryStats;
  gridFsFileId: string;
  tags: string[];
  createdAt: string;
  updatedAt: string;
}

export interface SlaveIdentity {
  name: string;
  physAddr: number;
  autoIncAddr: number;
  physics: string;
  vendorId: number;
  productCode: number;
  revisionNo: number;
  serialNo: number;
}

export interface PreviousPort {
  physAddr: number;
  port: string;
  selected: boolean;
}

export interface HotConnectGroup {
  groupName: string;
  groupMemberCount: number;
  identifyCmds: InitCmd[];
}

export interface InitCmd {
  transitions: string[];
  beforeSlave: boolean;
  comment: string;
  cmd: string;
  address: { adp: number; ado: number; addr: number };
  payload: { dataLength: number; dataHex: string; cnt: number };
  timing: { requirement: string; retries: number; timeoutMs: number };
  validate: unknown | null;
}

export interface CoeInitCmd {
  fixed: boolean;
  completeAccess: boolean;
  ccs: number;
  index: number;
  subIndex: number;
  transitions: string[];
  dataHex: string;
  timeoutMs: number;
  comment: string;
}

export interface PdoEntry {
  index: number;
  subIndex: number;
  bitLen: number;
  name: string;
  dataType: string;
  comment: string;
}

export interface PdoMapping {
  index: number;
  name: string;
  smIndex: number;
  fixed: boolean;
  mandatory: boolean;
  virtual: boolean;
  excludeIndices: number[];
  entries: PdoEntry[];
}

export interface SyncManagerConfig {
  index: number;
  type: string;
  minSize: number;
  maxSize: number;
  defaultSize: number;
  startAddress: number;
  controlByte: number;
  enable: boolean;
  virtual: boolean;
  pdoIndices: number[];
}

export interface ProcessDataRegion {
  bitStart: number;
  bitLength: number;
}

export interface ProcessDataLayout {
  sendRegions: ProcessDataRegion[];
  recvRegions: ProcessDataRegion[];
  syncManagers: SyncManagerConfig[];
  rxPdos: PdoMapping[];
  txPdos: PdoMapping[];
}

export interface MailboxRegion {
  sendStart: number;
  sendLength: number;
  recvStart: number;
  recvLength: number;
  statusBitAddr: number;
}

export interface MailboxConfig {
  dataLinkLayer: boolean;
  standard: MailboxRegion;
  bootstrap: MailboxRegion | null;
  aoeNetId: string;
  protocols: string[];
  coeProfileChannels: unknown[];
  coeInitCmds: CoeInitCmd[];
}

export interface DistributedClocksConfig {
  enabled: boolean;
  potentialReferenceClock: boolean;
  referenceClock: boolean;
  cycleTime0: number;
  cycleTime1: number;
  shiftTime0: number;
  shiftTime1: number;
}

export interface EniSlave {
  identity: SlaveIdentity;
  previousPort: PreviousPort | null;
  hotConnect: HotConnectGroup | null;
  processData: ProcessDataLayout;
  initCmds: InitCmd[];
  mailbox: MailboxConfig;
  dc: DistributedClocksConfig;
}

export interface MasterRedundancyConfig {
  enabled: boolean;
  secondarySourceMac: string;
}

export interface EniMaster {
  name: string;
  destinationMac: string;
  sourceMac: string;
  etherType: number;
  mailboxStates: { startAddr: number; count: number };
  eoe: { maxPorts: number; maxFrames: number; maxMacs: number };
  redundancy: MasterRedundancyConfig;
  initCmds: InitCmd[];
}

export interface CyclicCommand {
  states: string[];
  comment: string;
  cmd: string;
  address: { kind: string; adp: number; ado: number; addr: number };
  dataLength: number;
  cnt: number;
  inputOffs: number;
  outputOffs: number;
}

export interface CyclicFrame {
  commands: CyclicCommand[];
}

export interface CyclicConfig {
  cycleTimeUs: number;
  frames: CyclicFrame[];
}

export interface ProcessImageVariable {
  name: string;
  dataType: string;
  bitSize: number;
  bitOffs: number;
  comment: string;
}

export interface ProcessImage {
  inputsByteSize: number;
  outputsByteSize: number;
  inputs: ProcessImageVariable[];
  outputs: ProcessImageVariable[];
}

export interface EniDocument {
  version: string;
  master: EniMaster;
  slaves: EniSlave[];
  cyclic: CyclicConfig;
  processImage: ProcessImage;
}

export interface ValidationIssue {
  ruleId: string;
  severity: 'ERROR' | 'WARNING' | 'INFO';
  target: string;
  message: string;
  specificationRef: string;
}

export interface ValidationReport {
  valid: boolean;
  errorCount: number;
  warningCount: number;
  issues: ValidationIssue[];
  summary: string;
}

export interface TopologyNode {
  id: string;
  label: string;
  category: string;
  physAddr: number;
  autoIncAddr: number;
  physics: string;
  hotConnectGroup: string;
  dcEnabled: boolean;
  rxBits: number;
  txBits: number;
}

export interface TopologyEdge {
  id: string;
  sourceId: string;
  targetId: string;
  sourcePort: string;
  targetPort: string;
  linkType: string;
}

export interface TopologyGraph {
  nodes: TopologyNode[];
  edges: TopologyEdge[];
  redundancyEnabled: boolean;
  hotConnectGroups: string[];
}

export interface MappedVariableSlice {
  name: string;
  direction: string;
  dataType: string;
  relativeByteOffset: number;
  bitOffsetInByte: number;
  bitSize: number;
}

export interface FrameField {
  name: string;
  category: string;
  byteOffset: number;
  byteLength: number;
  detail: string;
  mappedVars: MappedVariableSlice[];
}

export interface MappedFrame {
  frameIndex: number;
  cycleTimeUs: number;
  totalWireBytes: number;
  ethercatPayloadBytes: number;
  fields: FrameField[];
}

export interface SlaveCatalogEntry {
  id: string;
  vendorId: number;
  productCode: number;
  revisionNo: number;
  displayName: string;
  physics: string;
  category: string;
  protocols: string[];
  defaultSyncManagers: SyncManagerConfig[];
  defaultRxPdos: PdoMapping[];
  defaultTxPdos: PdoMapping[];
  defaultCoeInitCmds: CoeInitCmd[];
  esiEnriched: boolean;
  sourceEnis: string[];
  updatedAt: string;
}

export interface CatalogDiscoveryReport {
  totalSlavesInspected: number;
  uniqueProfilesFound: number;
  newlyAddedCount: number;
  newlyAddedKeys: string[];
  alreadyKnownKeys: string[];
}

export interface ImportResult {
  metadata: EniCatalogMetadata;
  document: EniDocument;
  discovery: CatalogDiscoveryReport;
}

export interface QuasarBusStatus {
  connected: boolean;
  endpointUrl: string;
  slaveCount: number;
  redundancyMode: string;
  slaveSummaries: string[];
  timestamp: string;
}

export interface QuasarScanResult {
  success: boolean;
  message: string;
  eniCatalogId: string | null;
  generatedXml: string | null;
}

export interface QuasarCompareResult {
  matched: boolean;
  summary: string;
  differences: string[];
}

export interface WorkshopEvent {
  eventType: string;
  targetId: string;
  summary: string;
  timestamp: string;
}

export type CompareScope = 'ENI' | 'TOPOLOGY' | 'SLAVE' | 'HOT_CONNECT';

export type DiffStatus = 'MATCH' | 'CHANGED' | 'LEFT_ONLY' | 'RIGHT_ONLY';

export interface FieldDelta {
  field: string;
  leftValue: string;
  rightValue: string;
}

export interface OuterJoinRow {
  rowKey: string;
  section: string;
  name: string;
  status: DiffStatus;
  leftSummary: string;
  rightSummary: string;
  deltas: FieldDelta[];
}

export interface LinearBusItem {
  index: number;
  physAddr: number;
  name: string;
  category: string;
  physics: string;
  prevPort: string;
  hotConnectGroup: string;
  dcEnabled: boolean;
  rxBits: number;
  txBits: number;
  status: DiffStatus;
  matchedCounterpartKey: string;
}

export interface ComparisonTreeNode {
  nodeId: string;
  label: string;
  section: string;
  status: DiffStatus;
  detail: string;
  children: ComparisonTreeNode[];
}

export interface ComparisonSummary {
  totalRows: number;
  matchedCount: number;
  changedCount: number;
  leftOnlyCount: number;
  rightOnlyCount: number;
}

export interface ComparisonResult {
  scope: CompareScope;
  leftTitle: string;
  rightTitle: string;
  summary: ComparisonSummary;
  leftTree: ComparisonTreeNode;
  rightTree: ComparisonTreeNode;
  leftLinearBus: LinearBusItem[];
  rightLinearBus: LinearBusItem[];
  outerJoinRows: OuterJoinRow[];
}

export type WorkItemStatus = 'BACKLOG' | 'IN_PROGRESS' | 'IN_REVIEW' | 'BLOCKED' | 'DONE';

export type ActivitySource = 'WEB' | 'CLI' | 'SYSTEM';

export interface FeatureItem {
  id: string;
  title: string;
  requirementId: string;
  taskIds: string[];
  status: WorkItemStatus;
  scope: string;
}

export interface TaskItem {
  id: string;
  title: string;
  featureId: string;
  requirementId: string;
  status: WorkItemStatus;
  goal: string;
}

export interface ActivityRecord {
  id: string;
  workRefId: string;
  action: string;
  target: string;
  summary: string;
  source: ActivitySource;
  timestamp: string;
}

export interface TraceabilityAudit {
  totalJavaFiles: number;
  tracedJavaFiles: number;
  coveragePercent: number;
  untaggedFiles: string[];
  unknownWorkRefs: string[];
}

export interface ProjectOverview {
  activeWorkRef: string;
  features: FeatureItem[];
  tasks: TaskItem[];
  activities: ActivityRecord[];
  traceability: TraceabilityAudit;
}
