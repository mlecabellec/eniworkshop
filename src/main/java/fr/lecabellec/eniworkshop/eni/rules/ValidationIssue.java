package fr.lecabellec.eniworkshop.eni.rules;

import java.util.Objects;

/**
 * Single diagnostic produced by an {@link EniValidationRule}.
 *
 * @param ruleId           stable rule identifier (e.g., "ECAT-ADDR-001")
 * @param severity         diagnostic severity
 * @param target           target element or slave name
 * @param message          human-readable explanation
 * @param specificationRef ETG standard section reference
 *
 * <p>Requirement: REQ-00003, Constraint: CS-0030.11.
 */
public record ValidationIssue(
        String ruleId,
        ValidationSeverity severity,
        String target,
        String message,
        String specificationRef) {

    /**
     * Compact constructor enforcing non-null diagnostic fields.
     */
    public ValidationIssue {
        Objects.requireNonNull(ruleId, "ValidationIssue ruleId must not be null");
        Objects.requireNonNull(severity, "ValidationIssue severity must not be null");
        Objects.requireNonNull(target, "ValidationIssue target must not be null");
        Objects.requireNonNull(message, "ValidationIssue message must not be null");
        Objects.requireNonNull(specificationRef, "ValidationIssue specificationRef must not be null");
    }
}
