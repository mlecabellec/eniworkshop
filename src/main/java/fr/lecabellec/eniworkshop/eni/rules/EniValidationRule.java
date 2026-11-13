package fr.lecabellec.eniworkshop.eni.rules;

import fr.lecabellec.eniworkshop.eni.model.EniDocument;

import java.util.List;

/**
 * Strategy interface for individual ETG.1000 / ETG.2100 validation rules.
 *
 * <p>Requirement: REQ-00003, Constraint: CS-0030.6.2.
 */
public interface EniValidationRule {

    /**
     * Returns the unique rule identifier.
     *
     * @return rule ID such as "ECAT-ADDR-001"
     */
    String ruleId();

    /**
     * Evaluates this rule against an {@link EniDocument}.
     *
     * @param document non-null ENI document to inspect
     * @return immutable list of findings (empty if compliant)
     */
    List<ValidationIssue> validate(EniDocument document);
}
