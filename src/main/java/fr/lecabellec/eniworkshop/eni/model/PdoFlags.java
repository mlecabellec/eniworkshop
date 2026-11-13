package fr.lecabellec.eniworkshop.eni.model;

/**
 * Boolean attributes and SyncManager/SyncUnit binding flags on a PDO (ETG.2100).
 *
 * @param syncManager SyncManager index assigned via Sm attribute (-1 if unassigned)
 * @param syncUnit    SyncUnit index assigned via Su attribute (-1 if unassigned)
 * @param fixed       Fixed="1" attribute
 * @param mandatory   Mandatory="1" attribute
 * @param virtual     Virtual="1" attribute
 * @param isTx        true for TxPDO (Inputs), false for RxPDO (Outputs)
 *
 * <p>Requirement: REQ-00002, Constraint: CS-0030.11.
 */
public record PdoFlags(
        int syncManager,
        int syncUnit,
        boolean fixed,
        boolean mandatory,
        boolean virtual,
        boolean isTx) {
}
