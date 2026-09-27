package src.rescue;

import src.model.CrisisGraph;
import src.model.Edge;
import src.model.Node;
import src.routing.RiskAwareRouter;
import src.routing.RoutePreference;
import src.vehicle.VehicleProfile;

import java.util.*;

/**
 * Multi-stop / Multi-trip Rescue Planner
 *
 * หลักการ:
 * 1) รถทุกคันเริ่มที่ Depot พร้อม capacity เต็ม
 * 2) ถ้ายังมี slot -> เลือกผู้ประสบภัยจุดถัดไปจาก "ตำแหน่งปัจจุบัน" ของรถ
 * 3) รับคนได้เท่าที่ slot เหลือ
 * 4) ถ้ารถเต็มและยังมีคนค้าง -> กลับ Depot, reset capacity, เริ่มรอบใหม่
 * 5) ทำซ้ำจนช่วยครบ หรือไม่มีรถคันใดเข้าถึงจุดที่เหลือได้
 *
 * จุดสำคัญคือรถ 1 คันไม่ได้ถูกจำกัดให้รับได้เพียง 1 Rescue Request อีกต่อไป
 */
public class MultiVehicleRescuePlanner {
    private final CrisisGraph graph;

    public MultiVehicleRescuePlanner(CrisisGraph graph) {
        this.graph = graph;
    }

    public PlanResult plan(
            Node depot,
            List<RescueRequest> requests,
            int fleetSize,
            int vehicleCapacity,
            RoutePreference preference,
            VehicleProfile profile
    ) {
        int safeFleetSize = Math.max(1, fleetSize);
        int safeCapacity = Math.max(1, vehicleCapacity);

        Map<String, Integer> remaining = new LinkedHashMap<>();
        for (RescueRequest request : requests) {
            remaining.put(request.getId(), Math.max(0, request.getPeople()));
        }

        RiskAwareRouter router = new RiskAwareRouter(graph);
        List<VehicleState> vehicles = new ArrayList<>();
        for (int i = 1; i <= safeFleetSize; i++) {
            RescueAssignment mission = new RescueAssignment(
                    String.format("R-%02d", i),
                    profile
            );
            vehicles.add(new VehicleState(mission, depot, safeCapacity, 1));
        }

        // ใช้เพื่อให้ช่วงแรกพยายามครอบคลุมหลายจุด ก่อนวนไปรับซ้ำจุดเดิม
        Set<String> servedAtLeastOnce = new HashSet<>();

        int safetyGuard = Math.max(100, requests.size() * safeFleetSize * 20);
        int iterations = 0;

        while (hasRemaining(remaining) && iterations++ < safetyGuard) {
            boolean progressThisCycle = false;

            for (VehicleState vehicle : vehicles) {
                if (!hasRemaining(remaining)) break;

                // เต็มจาก stop ก่อนหน้า -> กลับ Start ก่อนออกเที่ยวใหม่
                if (vehicle.capacityLeft <= 0) {
                    if (!returnToDepot(vehicle, depot, router, preference, profile)) {
                        vehicle.blocked = true;
                        continue;
                    }
                    vehicle.capacityLeft = safeCapacity;
                    vehicle.tripNumber++;
                }

                Candidate best = chooseBestCandidate(
                        vehicle.currentNode,
                        requests,
                        remaining,
                        servedAtLeastOnce,
                        vehicle.capacityLeft,
                        router,
                        preference,
                        profile
                );

                // จากตำแหน่งปัจจุบันไปจุดไหนไม่ได้ ลองกลับ Start แล้วคำนวณใหม่ใน cycle ถัดไป
                if (best == null) {
                    if (!sameNode(vehicle.currentNode, depot)) {
                        if (returnToDepot(vehicle, depot, router, preference, profile)) {
                            vehicle.capacityLeft = safeCapacity;
                            vehicle.tripNumber++;
                            vehicle.blocked = false;
                            progressThisCycle = true;
                        } else {
                            vehicle.blocked = true;
                        }
                    } else {
                        vehicle.blocked = true;
                    }
                    continue;
                }

                int peopleLeft = remaining.getOrDefault(best.request.getId(), 0);
                int pickedUp = Math.min(vehicle.capacityLeft, peopleLeft);
                if (pickedUp <= 0) continue;

                vehicle.capacityLeft -= pickedUp;
                remaining.put(best.request.getId(), peopleLeft - pickedUp);

                vehicle.mission.addPickupLeg(
                        vehicle.currentNode,
                        best.request,
                        pickedUp,
                        vehicle.tripNumber,
                        vehicle.capacityLeft,
                        best.route
                );

                vehicle.currentNode = best.request.getNode();
                vehicle.blocked = false;
                servedAtLeastOnce.add(best.request.getId());
                progressThisCycle = true;

                // ถ้าเต็มทันทีและยังมีคนรอ ให้กลับ Depot ตอนนี้เลย
                // เพื่อให้ state ชัดเจนว่าเที่ยวนี้จบ แล้วเที่ยวต่อไปเริ่มจาก Start
                if (vehicle.capacityLeft == 0 && hasRemaining(remaining)) {
                    if (returnToDepot(vehicle, depot, router, preference, profile)) {
                        vehicle.capacityLeft = safeCapacity;
                        vehicle.tripNumber++;
                    } else {
                        vehicle.blocked = true;
                    }
                }
            }

            if (!progressThisCycle) break;
        }

        // ภารกิจกู้ภัยที่รับคนแล้วควรกลับ Depot เสมอ
        for (VehicleState vehicle : vehicles) {
            if (vehicle.mission.getAssignedPeople() > 0 && !sameNode(vehicle.currentNode, depot)) {
                returnToDepot(vehicle, depot, router, preference, profile);
            }
        }

        List<RescueAssignment> assignments = vehicles.stream()
                .map(v -> v.mission)
                .filter(m -> m.getAssignedPeople() > 0)
                .toList();

        int totalPeople = requests.stream().mapToInt(RescueRequest::getPeople).sum();
        int assignedPeople = assignments.stream().mapToInt(RescueAssignment::getAssignedPeople).sum();
        int unservedPeople = remaining.values().stream().mapToInt(Integer::intValue).sum();

        return new PlanResult(assignments, remaining, totalPeople, assignedPeople, unservedPeople);
    }

    private Candidate chooseBestCandidate(
            Node current,
            List<RescueRequest> requests,
            Map<String, Integer> remaining,
            Set<String> servedAtLeastOnce,
            int capacityLeft,
            RiskAwareRouter router,
            RoutePreference preference,
            VehicleProfile profile
    ) {
        boolean hasUnvisitedRemaining = requests.stream().anyMatch(r ->
                remaining.getOrDefault(r.getId(), 0) > 0 && !servedAtLeastOnce.contains(r.getId()));

        Candidate best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (RescueRequest request : requests) {
            int peopleLeft = remaining.getOrDefault(request.getId(), 0);
            if (peopleLeft <= 0 || sameNode(current, request.getNode())) continue;

            List<Edge> route = router.findRoute(current, request.getNode(), preference, profile);
            if (route == null || route.isEmpty()) continue;

            double routeCost = route.stream()
                    .mapToDouble(edge -> router.edgeCost(edge, preference))
                    .sum();

            double score = score(request, routeCost, peopleLeft, capacityLeft);

            // ถ้ายังมีจุดที่ไม่เคยได้รถเลย ให้จุดที่ถูกช่วยแล้วมี penalty สูง
            // เพื่อรักษา behavior แบบ coverage-first แต่ยังอนุญาต multi-stop
            if (hasUnvisitedRemaining && servedAtLeastOnce.contains(request.getId())) {
                score *= 4.0;
            }

            if (score < bestScore) {
                bestScore = score;
                best = new Candidate(request, route, routeCost, score);
            }
        }

        return best;
    }

    private double score(
            RescueRequest request,
            double routeCost,
            int peopleLeft,
            int capacityLeft
    ) {
        double fillPotential = Math.min(1.0, peopleLeft / (double) Math.max(1, capacityLeft));
        double loadFactor = 1.0 / (1.0 + 0.20 * fillPotential);
        return routeCost * request.priorityFactor() * loadFactor;
    }

    private boolean returnToDepot(
            VehicleState vehicle,
            Node depot,
            RiskAwareRouter router,
            RoutePreference preference,
            VehicleProfile profile
    ) {
        if (sameNode(vehicle.currentNode, depot)) {
            vehicle.currentNode = depot;
            return true;
        }

        List<Edge> route = router.findRoute(vehicle.currentNode, depot, preference, profile);
        if (route == null || route.isEmpty()) return false;

        vehicle.mission.addReturnLeg(
                vehicle.currentNode,
                depot,
                vehicle.tripNumber,
                route
        );
        vehicle.currentNode = depot;
        return true;
    }

    private boolean hasRemaining(Map<String, Integer> remaining) {
        return remaining.values().stream().anyMatch(value -> value != null && value > 0);
    }

    private boolean sameNode(Node a, Node b) {
        return a != null && b != null && Objects.equals(a.getId(), b.getId());
    }

    private static class VehicleState {
        private final RescueAssignment mission;
        private Node currentNode;
        private int capacityLeft;
        private int tripNumber;
        private boolean blocked;

        private VehicleState(
                RescueAssignment mission,
                Node currentNode,
                int capacityLeft,
                int tripNumber
        ) {
            this.mission = mission;
            this.currentNode = currentNode;
            this.capacityLeft = capacityLeft;
            this.tripNumber = tripNumber;
        }
    }

    private record Candidate(
            RescueRequest request,
            List<Edge> route,
            double routeCost,
            double score
    ) {}

    public record PlanResult(
            List<RescueAssignment> assignments,
            Map<String, Integer> remainingPeopleByRequest,
            int totalPeople,
            int assignedPeople,
            int unservedPeople
    ) {}
}
