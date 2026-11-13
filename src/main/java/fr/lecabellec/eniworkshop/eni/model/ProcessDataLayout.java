package fr.lecabellec.eniworkshop.eni.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Complete ProcessData layout for an EtherCAT slave (Send, Recv, SyncManagers, RxPDOs, TxPDOs).
 *
 * @param sendRegion   optional output region (Master -> Slave)
 * @param recvRegion   optional input region (Slave -> Master)
 * @param syncManagers configured SyncManagers (Sm0..Sm3+)
 * @param rxPdos       RxPDO definitions (Outputs)
 * @param txPdos       TxPDO definitions (Inputs)
 *
 * <p>Requirement: REQ-00002, Constraint: CS-0030.2.
 */
public record ProcessDataLayout(
        Optional<ProcessDataRegion> sendRegion,
        Optional<ProcessDataRegion> recvRegion,
        List<SyncManagerConfig> syncManagers,
        List<PdoMapping> rxPdos,
        List<PdoMapping> txPdos) {

    /**
     * Compact constructor enforcing non-null invariants and defensive list copying.
     */
    public ProcessDataLayout {
        Objects.requireNonNull(sendRegion, "sendRegion Optional must not be null");
        Objects.requireNonNull(recvRegion, "recvRegion Optional must not be null");
        syncManagers = List.copyOf(Objects.requireNonNull(syncManagers, "syncManagers must not be null"));
        rxPdos = List.copyOf(Objects.requireNonNull(rxPdos, "rxPdos must not be null"));
        txPdos = List.copyOf(Objects.requireNonNull(txPdos, "txPdos must not be null"));
    }

    /**
     * Returns an empty ProcessDataLayout instance.
     *
     * @return empty layout
     */
    public static ProcessDataLayout empty() {
        return new ProcessDataLayout(Optional.empty(), Optional.empty(), List.of(), List.of(), List.of());
    }
}
