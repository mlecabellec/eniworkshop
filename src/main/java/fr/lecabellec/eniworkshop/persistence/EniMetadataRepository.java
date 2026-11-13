package fr.lecabellec.eniworkshop.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data MongoDB repository for {@link EniCatalogMetadata}.
 *
 * <p>Requirement: REQ-00006, Feature: FR-00006.
 */
public interface EniMetadataRepository extends MongoRepository<EniCatalogMetadata, String> {

    /**
     * Finds all catalog versions sharing a business reference.
     *
     * @param reference reference string
     * @return matching metadata list
     */
    List<EniCatalogMetadata> findByReference(String reference);
}
