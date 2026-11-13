package fr.lecabellec.eniworkshop.eni.rules;

import java.util.List;
import java.util.Objects;

/**
 * Aggregated result of validating an {@link fr.lecabellec.eniworkshop.eni.model.EniDocument}.
 *
 * @param valid        true when errorCount is zero
 * @param errorCount   total ERROR diagnostics
 * @param warningCount total WARNING diagnostics
 * @param issues       immutable list of all diagnostics
 * @param summary      concise human-readable summary
 *
 * <p>Requirement: REQ-00003, Constraint: CS-0030.2.
 */
public record ValidationReport(
        boolean valid,
        int errorCount,
        int warningCount,
        List<ValidationIssue> issues,
        String summary) {

    /**
     * Compact constructor validating defensive list copy and counts.
     */
    public ValidationReport {
        issues = List.copyOf(Objects.requireNonNull(issues, "ValidationReport issues must not be null"));
        Objects.requireNonNull(summary, "ValidationReport summary must not be null");
        if (errorCount < 0 || warningCount < 0) {
            throw new IllegalArgumentException("ValidationReport errorCount and warningCount must be non-negative");
        }
    }
}
