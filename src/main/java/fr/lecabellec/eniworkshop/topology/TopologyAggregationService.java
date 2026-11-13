package fr.lecabellec.eniworkshop.topology;

import fr.lecabellec.eniworkshop.catalog.SlaveCatalogService;
import fr.lecabellec.eniworkshop.eni.model.CyclicCommand;
import fr.lecabellec.eniworkshop.eni.model.CyclicConfig;
import fr.lecabellec.eniworkshop.eni.model.CyclicFrame;
import fr.lecabellec.eniworkshop.eni.model.DatagramAddress;
import fr.lecabellec.eniworkshop.eni.model.DatagramCmd;
import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.eni.model.EniMaster;
import fr.lecabellec.eniworkshop.eni.model.EniSlave;
import fr.lecabellec.eniworkshop.eni.model.EthercatConstants;
import fr.lecabellec.eniworkshop.eni.model.MasterRedundancyConfig;
import fr.lecabellec.eniworkshop.eni.model.PdoMapping;
import fr.lecabellec.eniworkshop.eni.model.PreviousPort;
import fr.lecabellec.eniworkshop.eni.model.ProcessDataLayout;
import fr.lecabellec.eniworkshop.eni.model.ProcessDataRegion;
import fr.lecabellec.eniworkshop.eni.model.ProcessImage;
import fr.lecabellec.eniworkshop.eni.model.ProcessImageVariable;
import fr.lecabellec.eniworkshop.eni.model.SlaveIdentity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Builds graphical topology representations and aggregates multiple EtherCAT topologies
 * with automatic station addressing, port rewiring, ProcessImage shifting, cyclic frame
 * packing, and cable redundancy loop modeling.
 *
 * <p>Requirement: REQ-00005, Feature: FR-00005, Task: TSK-00005, Constraint: CS-0030.2.
 */
@Service
public final class TopologyAggregationService {

    /** Master graph node identifier. */
    private static final String MASTER_NODE_ID = "master";

    /** Base 32-bit logical memory start address for rebuilt cyclic commands. */
    private static final long LOGICAL_BASE_ADDR = 0x10000000L;

    /**
     * Converts an {@link EniDocument} into a {@link TopologyGraph} suitable for Cytoscape,
     * XYFlow, and PlantUML visualization.
     *
     * @param document non-null ENI document
     * @return structured {@link TopologyGraph}
     */
    public TopologyGraph buildGraph(final EniDocument document) {
        Objects.requireNonNull(document, "EniDocument must not be null");
        final List<TopologyNode> nodes = new ArrayList<>();
        final List<TopologyEdge> edges = new ArrayList<>();
        final Set<String> hcGroups = new LinkedHashSet<>();

        nodes.add(new TopologyNode(
                MASTER_NODE_ID,
                document.master().name(),
                "MASTER",
                0,
                0,
                "Y",
                "",
                false,
                0,
                0));

        final Map<Integer, String> nodeIdByPhys = new HashMap<>();
        for (int i = 0; i < document.slaves().size(); i++) {
            final EniSlave slave = document.slaves().get(i);
            final SlaveIdentity id = slave.identity();
            final String nodeId = "slave-" + id.physAddr() + "-" + i;
            nodeIdByPhys.put(id.physAddr(), nodeId);

            final String hcGroup = slave.hotConnect().map(hc -> hc.groupName()).orElse("");
            if (!hcGroup.isEmpty()) {
                hcGroups.add(hcGroup);
            }
            final String category = SlaveCatalogService.classifyCategory(id.physics(), id.name());
            final int rxBits = sumPdoBits(slave.processData().rxPdos());
            final int txBits = sumPdoBits(slave.processData().txPdos());
            nodes.add(new TopologyNode(
                    nodeId,
                    id.name(),
                    category,
                    id.physAddr(),
                    id.autoIncAddr(),
                    id.physics(),
                    hcGroup,
                    slave.dc().enabled() || slave.dc().potentialReferenceClock(),
                    rxBits,
                    txBits));
        }

        for (int i = 0; i < document.slaves().size(); i++) {
            final EniSlave slave = document.slaves().get(i);
            final String targetId = "slave-" + slave.identity().physAddr() + "-" + i;
            if (slave.previousPort().isPresent()) {
                final PreviousPort pp = slave.previousPort().get();
                final String sourceId = nodeIdByPhys.getOrDefault(pp.physAddr(), MASTER_NODE_ID);
                final String linkType = inferLinkType(slave.identity().physics());
                edges.add(new TopologyEdge(
                        "edge-" + i,
                        sourceId,
                        targetId,
                        Character.toString(pp.port()),
                        "A",
                        linkType));
            } else {
                final String sourceId = i == 0
                        ? MASTER_NODE_ID
                        : "slave-" + document.slaves().get(i - 1).identity().physAddr() + "-" + (i - 1);
                edges.add(new TopologyEdge(
                        "edge-" + i,
                        sourceId,
                        targetId,
                        i == 0 ? "M" : "B",
                        "A",
                        inferLinkType(slave.identity().physics())));
            }
        }

        if (document.master().redundancy().enabled() && !document.slaves().isEmpty()) {
            final int lastIdx = document.slaves().size() - 1;
            final EniSlave lastSlave = document.slaves().get(lastIdx);
            final String lastId = "slave-" + lastSlave.identity().physAddr() + "-" + lastIdx;
            edges.add(new TopologyEdge(
                    "edge-redundancy-return",
                    lastId,
                    MASTER_NODE_ID,
                    "B",
                    "R",
                    "REDUNDANCY_RETURN"));
        }

        return new TopologyGraph(
                nodes,
                edges,
                document.master().redundancy().enabled(),
                List.copyOf(hcGroups));
    }

    /**
     * Aggregates one or more add-on topologies onto a base {@link EniDocument}, re-allocating
     * unique physical addresses, sequential auto-increment addresses, port wiring, and ProcessImage
     * bit offsets.
     *
     * @param base    base ENI document
     * @param addons  ordered list of sub-topology ENI documents to aggregate
     * @param request aggregation options
     * @return merged and recalculated {@link EniDocument}
     */
    public EniDocument aggregate(
            final EniDocument base,
            final List<EniDocument> addons,
            final TopologyAggregationRequest request) {
        Objects.requireNonNull(base, "base EniDocument must not be null");
        Objects.requireNonNull(addons, "addons list must not be null");
        Objects.requireNonNull(request, "TopologyAggregationRequest must not be null");

        final List<EniSlave> mergedSlaves = new ArrayList<>(base.slaves());
        final List<ProcessImageVariable> mergedInputs = new ArrayList<>(base.processImage().inputs());
        final List<ProcessImageVariable> mergedOutputs = new ArrayList<>(base.processImage().outputs());

        int currentInputBytes = base.processImage().inputsByteSize();
        int currentOutputBytes = base.processImage().outputsByteSize();
        int nextPhysAddr = computeNextPhysAddr(base.slaves());

        for (final EniDocument addon : addons) {
            if (addon.slaves().isEmpty()) {
                continue;
            }
            final int anchorPhys = resolveAnchorPhysAddr(mergedSlaves, request.attachToPhysAddr());
            final Map<Integer, Integer> physRemap = new HashMap<>();
            for (final EniSlave addonSlave : addon.slaves()) {
                physRemap.put(addonSlave.identity().physAddr(), nextPhysAddr);
                nextPhysAddr++;
            }

            final int inputBitShift = currentInputBytes * EthercatConstants.BITS_PER_BYTE;
            final int outputBitShift = currentOutputBytes * EthercatConstants.BITS_PER_BYTE;

            for (int i = 0; i < addon.slaves().size(); i++) {
                final EniSlave s = addon.slaves().get(i);
                final int newPhys = physRemap.get(s.identity().physAddr());
                final int newAutoInc = -mergedSlaves.size();

                final Optional<PreviousPort> newPrevPort;
                if (i == 0 && anchorPhys > 0) {
                    newPrevPort = Optional.of(new PreviousPort(anchorPhys, request.attachPort(), true));
                } else if (s.previousPort().isPresent()) {
                    final PreviousPort origPrev = s.previousPort().get();
                    final int mappedPrev = physRemap.getOrDefault(origPrev.physAddr(), origPrev.physAddr());
                    newPrevPort = Optional.of(new PreviousPort(mappedPrev, origPrev.port(), origPrev.selected()));
                } else {
                    newPrevPort = Optional.empty();
                }

                final ProcessDataLayout shiftedPd = shiftProcessData(s.processData(), outputBitShift, inputBitShift);
                final SlaveIdentity newId = new SlaveIdentity(
                        s.identity().name(),
                        newPhys,
                        newAutoInc,
                        s.identity().physics(),
                        s.identity().vendorId(),
                        s.identity().productCode(),
                        s.identity().revisionNo(),
                        s.identity().serialNo());
                mergedSlaves.add(new EniSlave(
                        newId,
                        newPrevPort,
                        s.hotConnect(),
                        shiftedPd,
                        s.initCmds(),
                        s.mailbox(),
                        s.dc()));
            }

            for (final ProcessImageVariable v : addon.processImage().inputs()) {
                mergedInputs.add(new ProcessImageVariable(
                        v.name(),
                        v.comment(),
                        v.dataType(),
                        v.bitSize(),
                        v.bitOffs() + inputBitShift,
                        true));
            }
            for (final ProcessImageVariable v : addon.processImage().outputs()) {
                mergedOutputs.add(new ProcessImageVariable(
                        v.name(),
                        v.comment(),
                        v.dataType(),
                        v.bitSize(),
                        v.bitOffs() + outputBitShift,
                        false));
            }
            currentInputBytes += addon.processImage().inputsByteSize();
            currentOutputBytes += addon.processImage().outputsByteSize();
        }

        final ProcessImage mergedPi = new ProcessImage(currentInputBytes, currentOutputBytes, mergedInputs, mergedOutputs);
        final EniMaster updatedMaster = request.enableCableRedundancy()
                ? new EniMaster(
                        base.master().name(),
                        base.master().destinationMac(),
                        base.master().sourceMac(),
                        base.master().etherType(),
                        base.master().mailboxStates(),
                        base.master().eoe(),
                        new MasterRedundancyConfig(true, request.secondarySourceMac()),
                        base.master().initCmds())
                : base.master();

        final CyclicConfig updatedCyclic = request.recalculateCyclic()
                ? rebuildCyclicConfig(base.cyclic().cycleTimeUs(), mergedSlaves.size(), mergedPi)
                : base.cyclic();

        return new EniDocument(base.version(), updatedMaster, mergedSlaves, updatedCyclic, mergedPi);
    }

    /**
     * Rebuilds MTU-compliant cyclic LRW/LRD/LWR frames covering the merged ProcessImage.
     *
     * @param cycleTimeUs  cyclic task interval in microseconds
     * @param slaveCount   total slave count for WKC calculation
     * @param processImage merged process image
     * @return rebuilt {@link CyclicConfig}
     */
    private CyclicConfig rebuildCyclicConfig(
            final int cycleTimeUs,
            final int slaveCount,
            final ProcessImage processImage) {
        final int totalBytes = Math.max(processImage.inputsByteSize(), processImage.outputsByteSize());
        if (totalBytes <= 0) {
            return new CyclicConfig(cycleTimeUs, List.of());
        }
        final List<CyclicFrame> frames = new ArrayList<>();
        int offset = 0;
        while (offset < totalBytes) {
            final int chunk = Math.min(EthercatConstants.MAX_SINGLE_DATAGRAM_DATA_BYTES, totalBytes - offset);
            final CyclicCommand cmd = new CyclicCommand(
                    List.of("OP", "SAFEOP"),
                    "Aggregated cyclic LRW chunk at offset " + offset,
                    DatagramCmd.LRW,
                    new DatagramAddress(LOGICAL_BASE_ADDR + offset, 0, 0),
                    chunk,
                    Math.max(1, slaveCount),
                    offset,
                    offset);
            frames.add(new CyclicFrame(List.of(cmd)));
            offset += chunk;
        }
        return new CyclicConfig(cycleTimeUs, frames);
    }

    /**
     * Shifts Send/Recv bit regions on a slave during topology composition.
     *
     * @param pd             original layout
     * @param outputBitShift shift applied to Send (output) bits
     * @param inputBitShift  shift applied to Recv (input) bits
     * @return shifted {@link ProcessDataLayout}
     */
    private ProcessDataLayout shiftProcessData(
            final ProcessDataLayout pd,
            final int outputBitShift,
            final int inputBitShift) {
        final Optional<ProcessDataRegion> send = pd.sendRegion()
                .map(r -> new ProcessDataRegion(r.bitStart() + outputBitShift, r.bitLength()));
        final Optional<ProcessDataRegion> recv = pd.recvRegion()
                .map(r -> new ProcessDataRegion(r.bitStart() + inputBitShift, r.bitLength()));
        return new ProcessDataLayout(send, recv, pd.syncManagers(), pd.rxPdos(), pd.txPdos());
    }

    /**
     * Computes next available unique PhysAddr above existing slaves.
     *
     * @param slaves current slave list
     * @return next unused physical station address
     */
    private int computeNextPhysAddr(final List<EniSlave> slaves) {
        int maxPhys = EthercatConstants.DEFAULT_START_PHYS_ADDR - 1;
        for (final EniSlave s : slaves) {
            if (s.identity().physAddr() > maxPhys) {
                maxPhys = s.identity().physAddr();
            }
        }
        return maxPhys + 1;
    }

    /**
     * Resolves the upstream anchor PhysAddr for an aggregated sub-topology.
     *
     * @param currentSlaves  existing merged slaves
     * @param requestedPhys  explicit anchor PhysAddr or 0 for tail slave
     * @return resolved PhysAddr or 0 if empty
     */
    private int resolveAnchorPhysAddr(final List<EniSlave> currentSlaves, final int requestedPhys) {
        if (requestedPhys > 0) {
            return requestedPhys;
        }
        if (currentSlaves.isEmpty()) {
            return 0;
        }
        return currentSlaves.get(currentSlaves.size() - 1).identity().physAddr();
    }

    /**
     * Infers physical link medium from slave port physics string.
     *
     * @param physics slave physics string
     * @return "EBUS", "FIBER", or "ETHERNET"
     */
    private String inferLinkType(final String physics) {
        if (physics.startsWith("K")) {
            return "EBUS";
        }
        if (physics.startsWith("F")) {
            return "FIBER";
        }
        return "ETHERNET";
    }

    /**
     * Sums total mapped bit length across a list of PDOs.
     *
     * @param pdos list of PDO mappings
     * @return total bit count
     */
    private int sumPdoBits(final List<PdoMapping> pdos) {
        int sum = 0;
        for (final PdoMapping pdo : pdos) {
            sum += pdo.totalBitLength();
        }
        return sum;
    }
}
