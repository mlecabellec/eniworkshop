package fr.lecabellec.eniworkshop.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/**
 * Reusable sub-topology template persisted in MongoDB {@code topology_templates}.
 *
 * @param id          template identifier
 * @param name        template name
 * @param description description of the sub-topology block
 * @param eniId       referenced ENI catalog document ID
 * @param createdAt   creation timestamp
 *
 * <p>Requirement: REQ-00005, REQ-00006, Constraint: CS-0030.1.
 */
@Document("topology_templates")
public record TopologyTemplateDocument(
        @Id String id,
        String name,
        String description,
        String eniId,
        Instant createdAt) {

    /**
     * Compact constructor enforcing non-null fields.
     */
    public TopologyTemplateDocument {
        Objects.requireNonNull(id, "Template id must not be null");
        Objects.requireNonNull(name, "Template name must not be null");
        Objects.requireNonNull(description, "Template description must not be null");
        Objects.requireNonNull(eniId, "Template eniId must not be null");
        Objects.requireNonNull(createdAt, "Template createdAt must not be null");
    }
}
