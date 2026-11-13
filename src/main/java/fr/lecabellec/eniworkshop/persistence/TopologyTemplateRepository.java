package fr.lecabellec.eniworkshop.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for {@link TopologyTemplateDocument}.
 *
 * <p>Requirement: REQ-00005, REQ-00006.
 */
public interface TopologyTemplateRepository extends MongoRepository<TopologyTemplateDocument, String> {
}
