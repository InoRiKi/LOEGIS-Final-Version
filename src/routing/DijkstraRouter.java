package src.routing;

import src.model.CrisisGraph;
import src.model.Edge;
import src.model.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * LOEGIS Dijkstra Router - IGNORE ALL ROAD/FLOOD CONDITIONS
 *
 * TRANSPORT:
 * - NORMAL / SHALLOW ผ่านได้
 * - MEDIUM / DEEP ผ่านไม่ได้
 * - ไม่สน trafficAllowed / one-way
 * - weight = edge.getDistance() เท่านั้น
 *
 * RESCUE:
 * - ใช้ทุก Edge ใน Graph
 * - ไม่สนน้ำทุกระดับ
 * - ไม่สน trafficAllowed / one-way
 * - weight = edge.getDistance() เท่านั้น
 */
public class DijkstraRouter {

    private final CrisisGraph graph;
    private final Map<String, List<Edge>> adjacency;

    public DijkstraRouter(CrisisGraph graph) {
        this.graph = graph;
        this.adjacency = new HashMap<>();
        buildAdjacencyList();
    }

    private void buildAdjacencyList() {
        for (Edge edge : graph.getAllEdges().values()) {
            String sourceId = edge.getSource().getId();
            adjacency.computeIfAbsent(sourceId, k -> new ArrayList<>()).add(edge);
        }
    }

    public List<Edge> findShortestRoute(
            Node start,
            Node destination,
            double alpha,
            double beta
    ) {
        return findShortestRoute(start, destination, alpha, beta, false);
    }

    public List<Edge> findShortestRoute(
            Node start,
            Node destination,
            double alpha,
            double beta,
            boolean rescueMode
    ) {
        System.out.println(
                rescueMode
                        ? "[RESCUE] หาเส้นทางแบบไม่สนเงื่อนไขถนน/น้ำ..."
                        : "[TRANSPORT] หาเส้นทางแบบไม่สนเงื่อนไขถนน/น้ำ..."
        );

        if (start == null || destination == null) {
            System.out.println("[Dijkstra] start หรือ destination เป็น null");
            return new ArrayList<>();
        }

        if (start.getId().equals(destination.getId())) {
            return new ArrayList<>();
        }

        Map<String, Double> distance = new HashMap<>();
        Map<String, Edge> previousEdge = new HashMap<>();

        for (String nodeId : graph.getAllNodes().keySet()) {
            distance.put(nodeId, Double.POSITIVE_INFINITY);
        }

        distance.put(start.getId(), 0.0);

        PriorityQueue<NodeCost> pq =
                new PriorityQueue<>(Comparator.comparingDouble(n -> n.cost));

        pq.add(new NodeCost(start.getId(), 0.0));

        while (!pq.isEmpty()) {
            NodeCost current = pq.poll();

            if (current.cost > distance.getOrDefault(
                    current.nodeId,
                    Double.POSITIVE_INFINITY
            )) {
                continue;
            }

            if (current.nodeId.equals(destination.getId())) {
                break;
            }

            List<Edge> outgoing = adjacency.getOrDefault(
                    current.nodeId,
                    Collections.emptyList()
            );

            for (Edge edge : outgoing) {

                /*
                 * เงื่อนไขน้ำใช้เฉพาะ TRANSPORT เท่านั้น
                 *
                 * NORMAL  = risk 0.00  -> ผ่าน
                 * SHALLOW = risk 0.30  -> ผ่าน
                 * MEDIUM  = risk 0.65  -> ไม่ผ่าน
                 * DEEP    = risk 0.95  -> ไม่ผ่าน
                 *
                 * RESCUE ไม่เช็ก riskLevel เลย จึงผ่านน้ำทุกระดับเหมือนเดิม
                 * และทั้งสองโหมดยังไม่สน trafficAllowed / one-way
                 */
                if (!rescueMode && edge.getRiskLevel() >= 0.45) {
                    continue;
                }

                double weight = edge.getDistance();

                if (Double.isInfinite(weight)
                        || Double.isNaN(weight)
                        || weight < 0.0) {
                    continue;
                }

                String nextId = edge.getTarget().getId();
                double newCost = current.cost + weight;

                if (newCost < distance.getOrDefault(
                        nextId,
                        Double.POSITIVE_INFINITY
                )) {
                    distance.put(nextId, newCost);
                    previousEdge.put(nextId, edge);
                    pq.add(new NodeCost(nextId, newCost));
                }
            }
        }

        if (!previousEdge.containsKey(destination.getId())) {
            System.out.println(
                    rescueMode
                            ? "[RESCUE] ไม่พบเส้นทางใน Graph"
                            : "[TRANSPORT] ไม่พบเส้นทางใน Graph"
            );
            return new ArrayList<>();
        }

        List<Edge> route = reconstructRoute(
                start,
                destination,
                previousEdge
        );

        System.out.println(
                (rescueMode ? "[RESCUE]" : "[TRANSPORT]")
                        + " พบเส้นทาง "
                        + route.size()
                        + " edges"
        );

        return route;
    }

    private List<Edge> reconstructRoute(
            Node start,
            Node destination,
            Map<String, Edge> previousEdge
    ) {
        List<Edge> route = new ArrayList<>();
        String currentId = destination.getId();

        while (!currentId.equals(start.getId())) {
            Edge edge = previousEdge.get(currentId);

            if (edge == null) {
                return new ArrayList<>();
            }

            route.add(edge);
            currentId = edge.getSource().getId();
        }

        Collections.reverse(route);
        return route;
    }

    private static class NodeCost {
        private final String nodeId;
        private final double cost;

        private NodeCost(String nodeId, double cost) {
            this.nodeId = nodeId;
            this.cost = cost;
        }
    }
}
