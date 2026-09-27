package src.rescue;

import src.model.Edge;
import src.model.Node;
import src.vehicle.VehicleProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * แผนภารกิจของรถกู้ภัย "หนึ่งคัน" ตลอดหลายจุด/หลายรอบ
 *
 * รถสามารถ:
 * - รับผู้ประสบภัยหลายจุดต่อเนื่อง ตราบใดที่ยังมี capacity เหลือ
 * - กลับ Depot เมื่อเต็ม
 * - reset capacity แล้วออกเป็นรอบใหม่
 */
public class RescueAssignment {
    private final String vehicleId;
    private final VehicleProfile vehicleProfile;
    private final List<Leg> legs = new ArrayList<>();

    public RescueAssignment(String vehicleId, VehicleProfile vehicleProfile) {
        this.vehicleId = vehicleId;
        this.vehicleProfile = vehicleProfile;
    }

    public void addPickupLeg(
            Node from,
            RescueRequest request,
            int assignedPeople,
            int tripNumber,
            int slotRemainingAfter,
            List<Edge> route
    ) {
        legs.add(new Leg(
                "PICKUP",
                from,
                request.getNode(),
                request,
                assignedPeople,
                tripNumber,
                slotRemainingAfter,
                route
        ));
    }

    public void addReturnLeg(
            Node from,
            Node depot,
            int tripNumber,
            List<Edge> route
    ) {
        legs.add(new Leg(
                "RETURN",
                from,
                depot,
                null,
                0,
                tripNumber,
                0,
                route
        ));
    }

    public String getVehicleId() { return vehicleId; }
    public VehicleProfile getVehicleProfile() { return vehicleProfile; }
    public List<Leg> getLegs() { return Collections.unmodifiableList(legs); }

    /** route ทั้งหมดของรถคันนี้ รวมขากลับ Depot */
    public List<Edge> getRoute() {
        List<Edge> all = new ArrayList<>();
        for (Leg leg : legs) all.addAll(leg.getRoute());
        return all;
    }

    public int getAssignedPeople() {
        return legs.stream().mapToInt(Leg::getAssignedPeople).sum();
    }

    public int getTripCount() {
        return legs.stream().mapToInt(Leg::getTripNumber).max().orElse(0);
    }

    public int getPickupStopCount() {
        return (int) legs.stream().filter(Leg::isPickup).count();
    }

    public double getDistanceMeters() {
        return legs.stream().mapToDouble(Leg::getDistanceMeters).sum();
    }

    public double getAverageRisk() {
        List<Edge> route = getRoute();
        return route.stream().mapToDouble(Edge::getRiskLevel).average().orElse(0.0);
    }

    public double getMaxRisk() {
        return getRoute().stream().mapToDouble(Edge::getRiskLevel).max().orElse(0.0);
    }

    public static class Leg {
        private final String type;
        private final Node from;
        private final Node to;
        private final RescueRequest request;
        private final int assignedPeople;
        private final int tripNumber;
        private final int slotRemainingAfter;
        private final List<Edge> route;
        private final double distanceMeters;
        private final double averageRisk;
        private final double maxRisk;

        private Leg(
                String type,
                Node from,
                Node to,
                RescueRequest request,
                int assignedPeople,
                int tripNumber,
                int slotRemainingAfter,
                List<Edge> route
        ) {
            this.type = type;
            this.from = from;
            this.to = to;
            this.request = request;
            this.assignedPeople = assignedPeople;
            this.tripNumber = tripNumber;
            this.slotRemainingAfter = slotRemainingAfter;
            this.route = new ArrayList<>(route);
            this.distanceMeters = route.stream().mapToDouble(Edge::getDistance).sum();
            this.averageRisk = route.stream().mapToDouble(Edge::getRiskLevel).average().orElse(0.0);
            this.maxRisk = route.stream().mapToDouble(Edge::getRiskLevel).max().orElse(0.0);
        }

        public String getType() { return type; }
        public Node getFrom() { return from; }
        public Node getTo() { return to; }
        public RescueRequest getRequest() { return request; }
        public int getAssignedPeople() { return assignedPeople; }
        public int getTripNumber() { return tripNumber; }
        public int getSlotRemainingAfter() { return slotRemainingAfter; }
        public List<Edge> getRoute() { return Collections.unmodifiableList(route); }
        public double getDistanceMeters() { return distanceMeters; }
        public double getAverageRisk() { return averageRisk; }
        public double getMaxRisk() { return maxRisk; }
        public boolean isPickup() { return "PICKUP".equals(type); }
        public boolean isReturn() { return "RETURN".equals(type); }
    }
}
