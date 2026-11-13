package fr.lecabellec.eniworkshop.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Publishes domain and Quasar live telemetry events to STOMP WebSocket subscribers and
 * optionally Apache Kafka when enabled.
 *
 * <p>Requirement: REQ-00009, Feature: FR-00009, Constraint: CS-0030.8, CS-0030.13.
 */
@Service
public final class WorkshopEventPublisher {

    /** STOMP topic destination for ENI workbench events. */
    public static final String STOMP_EVENTS_TOPIC = "/topic/eni-events";

    /** SLF4J logger. */
    private static final Logger LOG = LoggerFactory.getLogger(WorkshopEventPublisher.class);

    /** Optional STOMP messaging template (absent in non-web CLI mode). */
    private final Optional<SimpMessagingTemplate> stompTemplate;

    /** Optional Kafka template. */
    private final Optional<KafkaTemplate<String, String>> kafkaTemplate;

    /** Whether Kafka publishing is enabled via configuration. */
    private final boolean kafkaEnabled;

    /** Kafka topic name. */
    private final String kafkaTopic;

    /**
     * Constructs a {@link WorkshopEventPublisher} via constructor injection.
     *
     * @param stompTemplate optional STOMP template
     * @param kafkaTemplate optional Kafka template
     * @param kafkaEnabled  enable Kafka forwarding flag
     * @param kafkaTopic    target Kafka topic name
     */
    @Autowired
    public WorkshopEventPublisher(
            @Autowired(required = false) final SimpMessagingTemplate stompTemplate,
            @Autowired(required = false) final KafkaTemplate<String, String> kafkaTemplate,
            @Value("${eniworkshop.messaging.kafka.enabled:false}") final boolean kafkaEnabled,
            @Value("${eniworkshop.messaging.kafka.topic:eniworkshop-events}") final String kafkaTopic) {
        this.stompTemplate = Optional.ofNullable(stompTemplate);
        this.kafkaTemplate = Optional.ofNullable(kafkaTemplate);
        this.kafkaEnabled = kafkaEnabled;
        this.kafkaTopic = Objects.requireNonNull(kafkaTopic, "kafkaTopic must not be null");
    }

    /**
     * Broadcasts an event to STOMP subscribers and optional Kafka topic.
     *
     * @param eventType event category
     * @param targetId  entity ID
     * @param summary   summary text
     */
    public void publish(final String eventType, final String targetId, final String summary) {
        final WorkshopEvent event = new WorkshopEvent(eventType, targetId, summary, Instant.now());
        if (stompTemplate.isPresent()) {
            try {
                stompTemplate.get().convertAndSend(STOMP_EVENTS_TOPIC, event);
            } catch (final RuntimeException ex) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Failed to publish STOMP event {}: {}", eventType, ex.getMessage());
                }
            }
        }
        if (kafkaEnabled && kafkaTemplate.isPresent()) {
            try {
                kafkaTemplate.get().send(kafkaTopic, targetId, eventType + ":" + summary);
            } catch (final RuntimeException ex) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Failed to forward event {} to Kafka: {}", eventType, ex.getMessage());
                }
            }
        }
    }
}
