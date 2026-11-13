package fr.lecabellec.eniworkshop.eni.model;

import java.util.List;
import java.util.Objects;

/**
 * Cyclic communication schedule block in an ENI configuration (ETG.2100 Config/Cyclic).
 *
 * @param cycleTimeUs cyclic task period in microseconds
 * @param frames      ordered cyclic Ethernet frames
 *
 * <p>Requirement: REQ-00002, REQ-00003, Constraint: CS-0030.1.
 */
public record CyclicConfig(
        int cycleTimeUs,
        List<CyclicFrame> frames) {

    /**
     * Compact constructor validating cycle period and defensive frame list copy.
     */
    public CyclicConfig {
        frames = List.copyOf(Objects.requireNonNull(frames, "CyclicConfig frames must not be null"));
        if (cycleTimeUs < 0) {
            throw new IllegalArgumentException("CyclicConfig cycleTimeUs must be non-negative: " + cycleTimeUs);
        }
    }

    /**
     * Returns an empty cyclic configuration with default 1000 us cycle time.
     *
     * @return empty {@link CyclicConfig}
     */
    public static CyclicConfig empty() {
        return new CyclicConfig(EthercatConstants.DEFAULT_CYCLE_TIME_US, List.of());
    }
}
