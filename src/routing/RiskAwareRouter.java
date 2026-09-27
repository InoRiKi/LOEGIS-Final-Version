package src.routing;

import src.model.CrisisGraph;
import src.model.Edge;
import src.model.Node;
import src.vehicle.VehicleProfile;

import java.util.*;

/**
 * Dynamic Risk-Aware Routing
 * ต้นทุนของถนน = ระยะทางที่ปรับด้วยเวลา + penalty จากความเสี่ยง
 * และตัดถนนที่เกินขีดความสามารถของยานพาหนะออกก่อนคำนวณ
 */
public class RiskAwareRouter {
    private final CrisisGraph graph;
    private final Map<String, List<Edge>> adjacency = new HashMap<>();

    public RiskAwareRouter(CrisisGraph graph) {
        this.graph = graph;
        for (Edge edge : graph.getAllEdges().values()) {
            adjacency.computeIfAbsent(edge.getSource().getId(), k -> new ArrayList<>()).add(edge);
        }
    }

    public List<Edge> findRoute(Node start, Node target, RoutePreference preference, VehicleProfile vehicle) {
        if (start == null || target == null || start.getId().equals(target.getId())) return new ArrayList<>();

        Map<String, Double> best = new HashMap<>();
        Map<String, Edge> previous = new HashMap<>();
        for (String id : graph.getAllNodes().keySet()) best.put(id, Double.POSITIVE_INFINITY);
        best.put(start.getId(), 0.0);

        PriorityQueue<NodeCost> pq = new PriorityQueue<>(Comparator.comparingDouble(x -> x.cost));
        pq.add(new NodeCost(start.getId(), 0.0));

        while (!pq.isEmpty()) {
            NodeCost current = pq.poll();
            if (current.cost > best.getOrDefault(current.nodeId, Double.POSITIVE_INFINITY)) continue;
            if (current.nodeId.equals(target.getId())) break;

            for (Edge edge : adjacency.getOrDefault(current.nodeId, Collections.emptyList())) {
                if (!vehicle.canPass(edge)) continue;
                double edgeCost = edgeCost(edge, preference);
                if (!Double.isFinite(edgeCost)) continue;

                String next = edge.getTarget().getId();
                double newCost = current.cost + edgeCost;
                if (newCost < best.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    best.put(next, newCost);
                    previous.put(next, edge);
                    pq.add(new NodeCost(next, newCost));
                }
            }
        }

        if (!previous.containsKey(target.getId())) return new ArrayList<>();
        List<Edge> route = new ArrayList<>();
        String id = target.getId();
        while (!id.equals(start.getId())) {
            Edge edge = previous.get(id);
            if (edge == null) return new ArrayList<>();
            route.add(edge);
            id = edge.getSource().getId();
        }
        Collections.reverse(route);
        return route;
    }

    public double edgeCost(Edge edge, RoutePreference preference) {
        double distanceTimeCost = edge.getDistance() * Math.max(1.0, edge.getTravelTimeFactor());
        // risk penalty scales with distance, so the score remains meaningful on different road lengths.
        double riskPenalty = edge.getDistance() * edge.getRiskLevel() * 4.0;
        return preference.distanceWeight() * distanceTimeCost
                + preference.riskWeight() * riskPenalty;
    }

    private record NodeCost(String nodeId, double cost) {}
}
