package fr.lecabellec.eniworkshop.eni.rules;

/**
 * Severity level for EtherCAT ENI validation diagnostics.
 *
 * <p>Requirement: REQ-00003, Constraint: CS-0030.12.
 */
public enum ValidationSeverity {
    /** Informational observation or best-practice hint. */
    INFO,
    /** Non-fatal warning that may impair determinism or interoperability. */
    WARNING,
    /** Specification violation preventing safe EtherCAT bus operation. */
    ERROR
}
