package fr.lecabellec.eniworkshop.topology;

import java.util.List;
import java.util.Objects;

/**
 * Complete directed topology graph extracted from an {@link fr.lecabellec.eniworkshop.eni.model.EniDocument}.
 *
 * @param nodes             graph nodes (master + slaves)
 * @param edges             physical port-to-port and redundancy return links
 * @param redundancyEnabled true when master redundancy ring closure is active
 * @param hotConnectGroups  distinct HotConnect group names
 *
 * <p>Requirement: REQ-00005, REQ-00010, Constraint: CS-0030.2.
 */
public record TopologyGraph(
        List<TopologyNode> nodes,
        List<TopologyEdge> edges,
        boolean redundancyEnabled,
        List<String> hotConnectGroups) {

    /**
     * Compact constructor enforcing defensive list copying.
     */
    public TopologyGraph {
        nodes = List.copyOf(Objects.requireNonNull(nodes, "TopologyGraph nodes must not be null"));
        edges = List.copyOf(Objects.requireNonNull(edges, "TopologyGraph edges must not be null"));
        hotConnectGroups = List.copyOf(
                Objects.requireNonNull(hotConnectGroups, "TopologyGraph hotConnectGroups must not be null"));
    }
}
