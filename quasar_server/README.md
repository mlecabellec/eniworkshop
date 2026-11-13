# ENI Workshop Quasar OPC-UA + `resoem` Bridge Helper

This native C++23 companion server exposes Quasar's `resoem::EthercatMasterService` over OPC-UA (`opc.tcp://localhost:4840`) using `quasar::opcua::OpcUaServerService`, matching the pattern established in Nunki.

## Exposed OPC-UA Service & Methods
Mounted under `Root/EthercatMasterService`:
- `refreshStatus({})` — enumerates live slaves, AL states, and redundancy state
- `forceInit({})` — transitions all live EtherCAT slaves back to ESM `INIT`
- `reconfigureSlave({"slaveIndex": N})` — reconfigures a single slave via mailbox/FMMU/SM
- `exportEni({"masterName": "...", "filepath": "..."})` — constructs an ETG.2100 ENI XML from the discovered topology
- `loadEni({"filepath": "..."})` — loads and applies an ENI file onto `resoem`
- `compareEni({"filepath": "..."})` — compares live slaves against the ENI specification

## Build Instructions

```bash
cmake -S quasar_server -B quasar_server/build -DQUASAR_ROOT=/home/vortigern/git/quasar
cmake --build quasar_server/build -j
./quasar_server/build/eniworkshop_quasar_server virtual   # or e.g. eth0 for hardware NIC
```
