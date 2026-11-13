package fr.lecabellec.eniworkshop.eni.model;

/**
 * Distributed Clocks (DC) configuration for an EtherCAT slave (ETG.2100).
 *
 * @param enabled                 whether DC sync is active
 * @param potentialReferenceClock whether this slave can act as the DC reference clock
 * @param referenceClock          whether this slave is elected as the DC reference clock
 * @param cycleTime0              Sync0 cycle time in nanoseconds
 * @param cycleTime1              Sync1 cycle time in nanoseconds
 * @param shiftTime0              Sync0 shift time in nanoseconds
 * @param shiftTime1              Sync1 shift time in nanoseconds
 *
 * <p>Requirement: REQ-00002, REQ-00003, Constraint: CS-0030.11.
 */
public record DistributedClocksConfig(
        boolean enabled,
        boolean potentialReferenceClock,
        boolean referenceClock,
        long cycleTime0,
        long cycleTime1,
        long shiftTime0,
        long shiftTime1) {

    /**
     * Returns a disabled default DC configuration.
     *
     * @return disabled {@link DistributedClocksConfig}
     */
    public static DistributedClocksConfig disabled() {
        return new DistributedClocksConfig(false, false, false, 0L, 0L, 0L, 0L);
    }
}
