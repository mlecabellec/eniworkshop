package fr.lecabellec.eniworkshop.messaging;

import java.time.Instant;
import java.util.Objects;

/**
 * Structured event broadcast over STOMP and optional Kafka topics.
 *
 * @param eventType event kind (e.g., "ENI_IMPORTED", "VALIDATION_COMPLETED", "QUASAR_SCAN")
 * @param targetId  associated ENI/catalog/bus identifier
 * @param summary   human-readable summary
 * @param timestamp UTC event timestamp
 *
 * <p>Requirement: REQ-00009, Constraint: CS-0030.2.
 */
public record WorkshopEvent(
        String eventType,
        String targetId,
        String summary,
        Instant timestamp) {

    /**
     * Compact constructor enforcing non-null fields.
     */
    public WorkshopEvent {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
