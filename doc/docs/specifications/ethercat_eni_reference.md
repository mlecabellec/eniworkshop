# EtherCAT & ETG.2100 ENI Reference Specification

This document summarizes the EtherCAT and ENI management rules implemented in `EniValidationEngine` and `TopologyAggregationService`, aligned with **ETG.1000**, **ETG.2000 (ESI)**, **ETG.2100 (ENI)**, and **ETG.5001 (MDP)**.

---

## 1. EtherCAT Frame & Datagram Structure (ETG.1000.4)

- **Ethernet Frame**: Destination MAC (`FFFFFFFFFFFF` broadcast or master-configured), Source MAC, EtherType `0x88A4` (optionally VLAN `0x8100`), EtherCAT Header (2 bytes: `Length` 11-bit, `Res` 1-bit, `Type=1` 4-bit), 1..N Datagrams, Padding (minimum 60B frame before FCS), 4-byte FCS.
- **Datagram Header (10 bytes)**:
  - `Cmd` (1B): `0=NOP`, `1=APRD`, `2=APWR`, `3=APRW`, `4=FPRD`, `5=FPWR`, `6=FPRW`, `7=BRD`, `8=BWR`, `9=BRW`, `10=LRD`, `11=LWR`, `12=LRW`, `13=ARMW`, `14=FRMW`.
  - `Idx` (1B): Master transaction index.
  - `Addr` (4B): Either `(Adp 16b, Ado 16b)` for positional/configured addressing or `LogicalAddr` (32b) for `LRD`/`LWR`/`LRW`.
  - `Len/R/C/M` (2B): 11-bit data length (max 1486 bytes per single datagram in standard Ethernet MTU), `C` (circulating), `M` (more datagrams follow).
  - `IRQ` (2B): EtherCAT interrupt mask.
- **Datagram Trailer**: `Data` (`Len` bytes) + `WKC` (Working Counter, 2B, incremented by each addressed ESC).

---

## 2. Port Physics & Topology Wiring (`Physics`, `PreviousPort`)

Each ESC supports up to 4 ports (`A`, `B`, `C`, `D` — processing order `A -> D -> B -> C`):
- `Y`: MII / 100BASE-TX (Ethernet copper cable)
- `K`: EBUS (LVDS backplane, e.g. Beckhoff E-Bus terminals `EK1100` -> `ELxxxx`)
- `F`: 100BASE-FX (Fiber)
- `H`: Hot Connect / specialized PHY
- ` ` (space): Port not present.

**Management Rules**:
1. Adjacent connected ports (`Slave[i].PreviousPort.Port` -> `Slave[i] Port A`) must have compatible physics (`K<->K`, `Y<->Y`, `F<->F`) unless bridged internally by a coupler (`YKY` / `YYK`).
2. `AutoIncAddr` follows daisy-chain discovery order (`0`, `-1`, `-2`, ... i.e. `0x0000`, `0xFFFF`, `0xFFFE`).
3. `PhysAddr` (Configured Station Address, ESC register `0x0010`) must be non-zero and unique across all slaves on the bus.

---

## 3. EtherCAT State Machine (ESM) & `InitCmds`

States: `INIT (1)`, `PREOP (2)`, `BOOT (3)`, `SAFEOP (4)`, `OP (8)`.
Valid ENI transitions: `IP`, `PS`, `PI`, `SP`, `SO`, `SI`, `OS`, `OP`, `OI`, `IB`, `BI`, `II`, `PP`, `SS`.
- Register `0x0120` (AL Control) sets target state; `0x0130` (AL Status) confirms state; `0x0134` (AL Status Code) reports error codes.
- `InitCmd` validation clauses (`<Validate><Data>`, `<DataMask>`, `<Timeout>`) verify ESC register state transitions deterministically.

---

## 4. SyncManagers, FMMU, ProcessImage & Mailboxes

- **SyncManagers (SM0..SM3+)**:
  - `SM0`: `MbxOut` (Master -> Slave mailbox, default 3-buffer/handshake).
  - `SM1`: `MbxIn` (Slave -> Master mailbox).
  - `SM2`: `Outputs` (`RxPdo`, Master -> Slave process data).
  - `SM3`: `Inputs` (`TxPdo`, Slave -> Master process data).
  - Rules: SM start addresses & lengths must not overlap in ESC DPRAM (typically `0x1000`–`0x8FFF`), mailbox `Send`/`Recv` regions must match `SM0`/`SM1`.
- **Mailbox Protocols**: `CoE` (CANopen over EtherCAT, SDO `0x1C12`/`0x1C13`/`0x1600`/`0x1A00`), `AoE` (ADS over EtherCAT with `NetId`), `EoE` (Ethernet over EtherCAT), `FoE` (File over EtherCAT / Bootstrap), `SoE` (Servo over EtherCAT), `VoE` (Vendor specific).
- **ProcessImage & Cyclic Frames**:
  - `RxPdo` variables map into `ProcessImage/Outputs` (`BitOffs`, `BitSize`) and are transmitted via `LWR`/`LRW` cyclic datagrams (`OutputOffs`).
  - `TxPdo` variables map into `ProcessImage/Inputs` (`BitOffs`, `BitSize`) and are read via `LRD`/`LRW` cyclic datagrams (`InputOffs`).
  - Rules: No overlapping bit ranges inside `Inputs` or `Outputs` (unless intentional multi-mapping / copy info), and cyclic datagram sizes must fit within Ethernet MTU (`<= 1486` bytes per datagram, `<= 1498` bytes total EtherCAT payload).

---

## 5. Distributed Clocks (DC), HotConnect & Cable Redundancy

- **DC**: Elects a `PotentialReferenceClock` slave (first DC-capable slave in topology), aligns system time (`0x0910`) via `ARMW`/`FRMW` propagation delay compensation, and configures `CycleTime0`/`CycleTime1`/`ShiftTime`.
- **HotConnect**: Groups optional/detachable slave sub-trees with `GroupName`, `GroupMemberCnt`, and `IdentifyCmd` (typically reading station alias register `0x0012` via `FPRD` or `APRD` with `<Validate>`). Rules verify `GroupMemberCnt >= 1`, unique station alias / identification, and separate cyclic/process boundaries so detaching a HotConnect group does not corrupt static slaves' WKC.
- **Cable Redundancy (Ring Topology)**: Master operates primary + secondary Ethernet ports; the last port of the branch loops back to Master Secondary RX. In ENI, redundancy requires frame circulation awareness, non-breaking HotConnect boundaries, and master secondary MAC/port wiring verification.
