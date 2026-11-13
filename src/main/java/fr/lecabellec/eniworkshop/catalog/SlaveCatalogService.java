package fr.lecabellec.eniworkshop.catalog;

import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.model.EniSlave;
import fr.lecabellec.eniworkshop.eni.model.SlaveIdentity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages the catalog of EtherCAT slaves and extension cards/modules, automatically discovering
 * unknown devices whenever an ENI file is imported.
 *
 * <p>Requirement: REQ-00004, Feature: FR-00004, Constraint: CS-0030.2, CS-0030.5.
 */
@Service
public final class SlaveCatalogService {

    /** Minimum port count for multi-port EtherCAT junction blocks. */
    private static final int MIN_JUNCTION_PORTS = 3;

    /** Pattern extracting bracketed device model such as [EK1100] or [EL3204]. */
    private static final Pattern BRACKET_MODEL_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");

    /** Thread-safe in-memory catalog cache synchronized with persistence when available. */
    private final Map<String, SlaveCatalogEntry> entriesByKey = new ConcurrentHashMap<>();

    /**
     * Classifies an EtherCAT device into COUPLER, EXTENSION_CARD, JUNCTION, or SLAVE from its physics and model name.
     *
     * @param physics   ESC port physics string (e.g., "YKY", "KK", "YYYY")
     * @param modelName device type/name
     * @return normalized device category
     */
    public static String classifyCategory(final String physics, final String modelName) {
        Objects.requireNonNull(physics, "physics must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
        final String p = physics.trim().toUpperCase(Locale.ROOT);
        final String m = modelName.trim().toUpperCase(Locale.ROOT);
        if (p.contains("Y") && p.contains("K")) {
            return "COUPLER";
        }
        if (p.equals("KK") || p.equals("K") || m.startsWith("EL") || m.startsWith("EP")) {
            return "EXTENSION_CARD";
        }
        if (p.length() >= MIN_JUNCTION_PORTS && p.chars().allMatch(c -> c == 'Y' || c == 'F')) {
            return "JUNCTION";
        }
        return "SLAVE";
    }

    /**
     * Extracts readable model name (e.g., "EL3204" from "Slave_5001 [EL3204]").
     *
     * @param rawName raw slave name
     * @return extracted model display name
     */
    public static String extractDisplayName(final String rawName) {
        Objects.requireNonNull(rawName, "rawName must not be null");
        final Matcher matcher = BRACKET_MODEL_PATTERN.matcher(rawName);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return rawName.trim();
    }

    /**
     * Inspects all slaves in an imported {@link EniDocument}, adds unknown slaves/modules to the catalog,
     * and returns a structured discovery report.
     *
     * @param document   non-null ENI document
     * @param sourceRef  source ENI reference name
     * @return {@link CatalogDiscoveryReport} detailing newly discovered vs known profiles
     */
    public CatalogDiscoveryReport discoverAndRegisterFromEni(final EniDocument document, final String sourceRef) {
        Objects.requireNonNull(document, "EniDocument must not be null");
        Objects.requireNonNull(sourceRef, "sourceRef must not be null");

        final Set<String> seenInDoc = new LinkedHashSet<>();
        final List<String> newlyAdded = new ArrayList<>();
        final List<String> alreadyKnown = new ArrayList<>();

        for (final EniSlave slave : document.slaves()) {
            final SlaveIdentity id = slave.identity();
            final String key = id.catalogKey();
            if (!seenInDoc.add(key)) {
                continue;
            }
            final SlaveCatalogEntry existing = entriesByKey.get(key);
            if (existing == null) {
                final String displayName = extractDisplayName(id.name());
                final String category = classifyCategory(id.physics(), displayName);
                final SlaveCatalogEntry created = new SlaveCatalogEntry(
                        key,
                        id.vendorId(),
                        id.productCode(),
                        id.revisionNo(),
                        displayName,
                        id.physics(),
                        category,
                        slave.mailbox().protocols(),
                        slave.processData().syncManagers(),
                        slave.processData().rxPdos(),
                        slave.processData().txPdos(),
                        slave.mailbox().coeInitCmds(),
                        false,
                        List.of(sourceRef),
                        Instant.now());
                entriesByKey.put(key, created);
                newlyAdded.add(key);
            } else {
                alreadyKnown.add(key);
                if (!existing.sourceEnis().contains(sourceRef)) {
                    final List<String> updatedSources = new ArrayList<>(existing.sourceEnis());
                    updatedSources.add(sourceRef);
                    entriesByKey.put(key, new SlaveCatalogEntry(
                            existing.id(),
                            existing.vendorId(),
                            existing.productCode(),
                            existing.revisionNo(),
                            existing.displayName(),
                            existing.physics(),
                            existing.category(),
                            existing.protocols(),
                            existing.defaultSyncManagers(),
                            existing.defaultRxPdos(),
                            existing.defaultTxPdos(),
                            existing.defaultCoeInitCmds(),
                            existing.esiEnriched(),
                            updatedSources,
                            Instant.now()));
                }
            }
        }
        return new CatalogDiscoveryReport(
                document.slaves().size(),
                seenInDoc.size(),
                newlyAdded.size(),
                newlyAdded,
                alreadyKnown);
    }

    /**
     * Upserts ESI-derived entries into the catalog, preserving existing source ENI lists.
     *
     * @param esiEntries list of parsed ESI catalog entries
     * @return count of entries upserted
     */
    public int importEsiEntries(final List<SlaveCatalogEntry> esiEntries) {
        Objects.requireNonNull(esiEntries, "esiEntries must not be null");
        for (final SlaveCatalogEntry entry : esiEntries) {
            final SlaveCatalogEntry existing = entriesByKey.get(entry.id());
            if (existing == null) {
                entriesByKey.put(entry.id(), entry);
            } else {
                final Set<String> mergedSources = new LinkedHashSet<>(existing.sourceEnis());
                mergedSources.addAll(entry.sourceEnis());
                entriesByKey.put(entry.id(), new SlaveCatalogEntry(
                        entry.id(),
                        entry.vendorId(),
                        entry.productCode(),
                        entry.revisionNo(),
                        entry.displayName(),
                        entry.physics().isEmpty() ? existing.physics() : entry.physics(),
                        entry.category(),
                        entry.protocols().isEmpty() ? existing.protocols() : entry.protocols(),
                        existing.defaultSyncManagers(),
                        existing.defaultRxPdos(),
                        existing.defaultTxPdos(),
                        existing.defaultCoeInitCmds(),
                        true,
                        List.copyOf(mergedSources),
                        Instant.now()));
            }
        }
        return esiEntries.size();
    }

    /**
     * Returns all catalog entries sorted by displayName and id.
     *
     * @return immutable list of catalog entries
     */
    public List<SlaveCatalogEntry> listAll() {
        return entriesByKey.values().stream()
                .sorted((a, b) -> {
                    final int cmp = a.displayName().compareToIgnoreCase(b.displayName());
                    return cmp != 0 ? cmp : a.id().compareTo(b.id());
                })
                .toList();
    }

    /**
     * Finds a catalog entry by canonical key.
     *
     * @param key {@code vendorId:productCode:revisionNo}
     * @return optional entry
     */
    public Optional<SlaveCatalogEntry> findByKey(final String key) {
        Objects.requireNonNull(key, "Catalog key must not be null");
        return Optional.ofNullable(entriesByKey.get(key));
    }

    /**
     * Seeds entries loaded from MongoDB at startup or test setup.
     *
     * @param entries catalog entries to load into memory
     */
    public void seedEntries(final List<SlaveCatalogEntry> entries) {
        Objects.requireNonNull(entries, "entries must not be null");
        for (final SlaveCatalogEntry entry : entries) {
            entriesByKey.putIfAbsent(entry.id(), entry);
        }
    }

    /**
     * Clears in-memory catalog entries (for isolated test execution).
     */
    public void clear() {
        entriesByKey.clear();
    }
}
