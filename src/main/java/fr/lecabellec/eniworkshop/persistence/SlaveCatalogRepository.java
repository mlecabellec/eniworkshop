package fr.lecabellec.eniworkshop.persistence;

import fr.lecabellec.eniworkshop.catalog.SlaveCatalogEntry;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for {@link SlaveCatalogEntry}.
 *
 * <p>Requirement: REQ-00004, REQ-00006, Feature: FR-00004.
 */
public interface SlaveCatalogRepository extends MongoRepository<SlaveCatalogEntry, String> {
}
