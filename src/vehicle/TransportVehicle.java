package src.vehicle;

import src.model.Edge;
import src.model.Node;

/**
 * รถขนส่งทั่วไป
 *
 * กฎ:
 * - เคารพทิศทาง One-way
 * - ผ่านถนนปกติและน้ำตื้น
 * - ไม่ผ่านน้ำปานกลาง/น้ำลึก
 */
public class TransportVehicle extends Vehicle {

    private static final double MAX_RISK_EXCLUSIVE = 0.45;

    public TransportVehicle(
            String id,
            double capacity,
            Node startNode
    ) {
        super(
                id,
                capacity,
                startNode,
                VehicleType.TRANSPORT
        );
    }

    @Override
    public boolean canPass(Edge edge) {
        if (edge == null) {
            return false;
        }

        if (edge.isReverseEdge()) {
            return false;
        }

        return edge.getRiskLevel() < MAX_RISK_EXCLUSIVE;
    }

    @Override
    public boolean allowsReverseEdge() {
        return false;
    }
}
