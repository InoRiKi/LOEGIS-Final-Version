package src.vehicle;

import src.model.Edge;
import src.model.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ศูนย์กลางสำหรับจัดการรถและกฎการผ่านถนน
 *
 * SimulationState ไม่ต้องรู้รายละเอียดว่า
 * รถแต่ละชนิดผ่านน้ำระดับไหนหรือใช้ reverse edge ได้หรือไม่
 */
public class VehicleManager {

    private final List<Vehicle> vehicles = new ArrayList<>();
    private VehicleType activeType = VehicleType.TRANSPORT;

    public void clear() {
        vehicles.clear();
        activeType = VehicleType.TRANSPORT;
    }

    public void setupFleet(
            int fleetSize,
            double capacity,
            Node startNode
    ) {
        vehicles.clear();

        int safeSize = Math.max(0, fleetSize);
        double safeCapacity = Math.max(0.0, capacity);

        for (int i = 1; i <= safeSize; i++) {
            vehicles.add(
                    new TransportVehicle(
                            String.format("TRANSPORT_%02d", i),
                            safeCapacity,
                            startNode
                    )
            );

            vehicles.add(
                    new RescueVehicle(
                            String.format("RESCUE_%02d", i),
                            safeCapacity,
                            startNode
                    )
            );
        }
    }

    public void updateStartNode(Node startNode) {
        for (Vehicle vehicle : vehicles) {
            vehicle.setStartNode(startNode);
        }
    }

    public void setActiveType(VehicleType activeType) {
        this.activeType =
                activeType == null
                        ? VehicleType.TRANSPORT
                        : activeType;
    }

    public VehicleType getActiveType() {
        return activeType;
    }

    public List<Vehicle> getVehicles() {
        return Collections.unmodifiableList(vehicles);
    }

    public List<Vehicle> getVehicles(VehicleType type) {
        List<Vehicle> result = new ArrayList<>();

        for (Vehicle vehicle : vehicles) {
            if (vehicle.getType() == type) {
                result.add(vehicle);
            }
        }

        return result;
    }

    /**
     * ใช้กฎของรถกับ Edge ก่อนส่งเข้า DijkstraRouter
     *
     * riskLevel ของ Loegis ปัจจุบัน:
     * 0.00          = ปกติ
     * ประมาณ 0.30   = น้ำตื้น
     * ประมาณ 0.65   = น้ำปานกลาง
     * ประมาณ 0.95   = น้ำลึก
     */
    public void applyRoadRules(
            Iterable<Edge> edges,
            VehicleType type
    ) {
        setActiveType(type);

        boolean rescue =
                activeType == VehicleType.RESCUE;

        for (Edge edge : edges) {
            if (edge == null) {
                continue;
            }

            // กฎทิศทางจราจร
            edge.setTrafficAllowed(
                    !edge.isReverseEdge() || rescue
            );

            double risk = edge.getRiskLevel();

            // กฎระดับน้ำตามชนิดรถ
            // RESCUE: ผ่านน้ำได้ทุกระดับ
            // TRANSPORT: ผ่านได้เฉพาะถนนปกติ/น้ำตื้น (risk < 0.45)
            if (rescue) {
                // RESCUE ใช้ passable ของตัวเองเท่านั้น
                edge.setRescuePassable(true);
            } else {
                // TRANSPORT ใช้ passable ของตัวเองเท่านั้น
                edge.setTransportPassable(risk < 0.45);
            }
        }
    }

    public boolean canActiveVehiclePass(Edge edge) {
        if (activeType == VehicleType.RESCUE) {
            return new RescueVehicle(
                    "RULE_CHECK",
                    0.0,
                    null
            ).canPass(edge);
        }

        return new TransportVehicle(
                "RULE_CHECK",
                0.0,
                null
        ).canPass(edge);
    }
}
