package fr.lecabellec.eniworkshop.eni.model;

import java.util.List;
import java.util.Objects;

/**
 * Master ProcessImage layout containing Inputs and Outputs byte sizes and variable maps (ETG.2100).
 *
 * @param inputsByteSize  declared Inputs ByteSize
 * @param outputsByteSize declared Outputs ByteSize
 * @param inputs          input variable definitions
 * @param outputs         output variable definitions
 *
 * <p>Requirement: REQ-00002, REQ-00003, Constraint: CS-0030.2.
 */
public record ProcessImage(
        int inputsByteSize,
        int outputsByteSize,
        List<ProcessImageVariable> inputs,
        List<ProcessImageVariable> outputs) {

    /**
     * Compact constructor validating byte sizes and defensive copying.
     */
    public ProcessImage {
        inputs = List.copyOf(Objects.requireNonNull(inputs, "ProcessImage inputs must not be null"));
        outputs = List.copyOf(Objects.requireNonNull(outputs, "ProcessImage outputs must not be null"));
        if (inputsByteSize < 0 || outputsByteSize < 0) {
            throw new IllegalArgumentException("ProcessImage byte sizes must be non-negative");
        }
    }

    /**
     * Returns an empty ProcessImage.
     *
     * @return empty {@link ProcessImage}
     */
    public static ProcessImage empty() {
        return new ProcessImage(0, 0, List.of(), List.of());
    }
}
