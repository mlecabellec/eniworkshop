package fr.lecabellec.eniworkshop.web;

import fr.lecabellec.eniworkshop.EniWorkshopException;
import fr.lecabellec.eniworkshop.compare.ComparisonModels.ComparisonResult;
import fr.lecabellec.eniworkshop.compare.EniComparisonService;
import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.model.EniSlave;
import fr.lecabellec.eniworkshop.persistence.EniCatalogMetadata;
import fr.lecabellec.eniworkshop.persistence.EniRepositoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * REST controller exposing structural comparison between two catalog ENI documents,
 * two bus topologies, two slaves, or two Hot Connect groups.
 *
 * <p>Requirement: REQ-00011, Feature: FR-00011.
 */
@RestController
@RequestMapping("/api/compare")
@Tag(name = "ENI Comparison", description = "Compare two ENIs, topologies, slaves, or Hot Connect groups")
public final class CompareController {

    /** ENI persistence repository. */
    private final EniRepositoryService repositoryService;

    /** Comparison engine. */
    private final EniComparisonService comparisonService;

    /**
     * Constructs a {@link CompareController}.
     *
     * @param repositoryService ENI repository service
     * @param comparisonService structural comparison service
     */
    @Autowired
    public CompareController(
            final EniRepositoryService repositoryService,
            final EniComparisonService comparisonService) {
        this.repositoryService = Objects.requireNonNull(repositoryService, "repositoryService must not be null");
        this.comparisonService = Objects.requireNonNull(comparisonService, "comparisonService must not be null");
    }

    /**
     * Compares two full ENI files in the catalog.
     *
     * @param leftId  left catalog ID
     * @param rightId right catalog ID
     * @return unified {@link ComparisonResult}
     */
    @GetMapping("/eni")
    @Operation(summary = "Compare two full ENI documents in the catalog")
    public ComparisonResult compareEni(
            @RequestParam("leftId") final String leftId,
            @RequestParam("rightId") final String rightId) {
        final EniDocument leftDoc = repositoryService.loadDocument(leftId);
        final EniDocument rightDoc = repositoryService.loadDocument(rightId);
        return comparisonService.compareEnis(
                formatTitle(leftId),
                leftDoc,
                formatTitle(rightId),
                rightDoc,
                false);
    }

    /**
     * Compares two EtherCAT bus topologies in the catalog.
     *
     * @param leftId  left catalog ID
     * @param rightId right catalog ID
     * @return unified {@link ComparisonResult}
     */
    @GetMapping("/topology")
    @Operation(summary = "Compare two bus topologies in the catalog")
    public ComparisonResult compareTopology(
            @RequestParam("leftId") final String leftId,
            @RequestParam("rightId") final String rightId) {
        final EniDocument leftDoc = repositoryService.loadDocument(leftId);
        final EniDocument rightDoc = repositoryService.loadDocument(rightId);
        return comparisonService.compareEnis(
                formatTitle(leftId),
                leftDoc,
                formatTitle(rightId),
                rightDoc,
                true);
    }

    /**
     * Compares two individual EtherCAT slaves selected by ENI ID + physical station address.
     *
     * @param leftId    left ENI catalog ID
     * @param leftPhys  left slave PhysAddr
     * @param rightId   right ENI catalog ID
     * @param rightPhys right slave PhysAddr
     * @return unified {@link ComparisonResult}
     */
    @GetMapping("/slave")
    @Operation(summary = "Compare two slaves across or within catalog ENI documents")
    public ComparisonResult compareSlave(
            @RequestParam("leftId") final String leftId,
            @RequestParam("leftPhys") final int leftPhys,
            @RequestParam("rightId") final String rightId,
            @RequestParam("rightPhys") final int rightPhys) {
        final EniDocument leftDoc = repositoryService.loadDocument(leftId);
        final EniDocument rightDoc = repositoryService.loadDocument(rightId);
        final EniSlave leftSlave = leftDoc.findSlaveByPhysAddr(leftPhys)
                .orElseThrow(() -> new EniWorkshopException("Left slave PhysAddr not found: " + leftPhys));
        final EniSlave rightSlave = rightDoc.findSlaveByPhysAddr(rightPhys)
                .orElseThrow(() -> new EniWorkshopException("Right slave PhysAddr not found: " + rightPhys));
        return comparisonService.compareSlaves(
                leftSlave.identity().name() + " (#" + leftPhys + ")",
                leftSlave,
                rightSlave.identity().name() + " (#" + rightPhys + ")",
                rightSlave);
    }

    /**
     * Compares two Hot Connect groups across or within catalog ENI documents.
     *
     * @param leftId     left ENI catalog ID
     * @param leftGroup  left Hot Connect group name
     * @param rightId    right ENI catalog ID
     * @param rightGroup right Hot Connect group name
     * @return unified {@link ComparisonResult}
     */
    @GetMapping("/hot-connect")
    @Operation(summary = "Compare two Hot Connect groups across or within catalog ENIs")
    public ComparisonResult compareHotConnect(
            @RequestParam("leftId") final String leftId,
            @RequestParam("leftGroup") final String leftGroup,
            @RequestParam("rightId") final String rightId,
            @RequestParam("rightGroup") final String rightGroup) {
        final EniDocument leftDoc = repositoryService.loadDocument(leftId);
        final EniDocument rightDoc = repositoryService.loadDocument(rightId);
        return comparisonService.compareHotConnectGroups(
                formatTitle(leftId),
                leftDoc,
                leftGroup,
                formatTitle(rightId),
                rightDoc,
                rightGroup);
    }

    private String formatTitle(final String catalogId) {
        return repositoryService.findMetadata(catalogId)
                .map(EniCatalogMetadata::name)
                .orElse(catalogId);
    }
}
