package fr.lecabellec.eniworkshop.catalog;

import java.util.List;
import java.util.Objects;

/**
 * Summary report of unknown slaves and extension cards discovered during ENI import.
 *
 * @param totalSlavesInspected  number of slave instances inspected in the ENI
 * @param uniqueProfilesFound   unique {@code vendorId:productCode:revisionNo} profiles in the ENI
 * @param newlyAddedCount       count of previously unknown profiles added to the catalog
 * @param newlyAddedKeys        canonical keys newly added
 * @param alreadyKnownKeys      canonical keys already present in the catalog
 *
 * <p>Requirement: REQ-00004, Constraint: CS-0030.2.
 */
public record CatalogDiscoveryReport(
        int totalSlavesInspected,
        int uniqueProfilesFound,
        int newlyAddedCount,
        List<String> newlyAddedKeys,
        List<String> alreadyKnownKeys) {

    /**
     * Compact constructor enforcing defensive list copies.
     */
    public CatalogDiscoveryReport {
        newlyAddedKeys = List.copyOf(Objects.requireNonNull(newlyAddedKeys, "newlyAddedKeys must not be null"));
        alreadyKnownKeys = List.copyOf(Objects.requireNonNull(alreadyKnownKeys, "alreadyKnownKeys must not be null"));
    }
}
