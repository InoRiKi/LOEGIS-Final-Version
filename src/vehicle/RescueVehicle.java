package src.vehicle;

import src.model.Edge;
import src.model.Node;

/**
 * รถกู้ภัย
 *
 * กฎ:
 * - สามารถใช้เส้นย้อนศรที่ระบบสร้างไว้ได้ในภาวะฉุกเฉิน
 * - ผ่านน้ำได้ทุกระดับ รวมถึงน้ำลึก
 * - ความเสี่ยงจากน้ำไม่ทำให้เส้นทางถูกปิดสำหรับโหมดกู้ภัย
 */
public class RescueVehicle extends Vehicle {

    public RescueVehicle(
            String id,
            double capacity,
            Node startNode
    ) {
        super(
                id,
                capacity,
                startNode,
                VehicleType.RESCUE
        );
    }

    @Override
    public boolean canPass(Edge edge) {
        if (edge == null) {
            return false;
        }

        // โหมดกู้ภัยผ่านน้ำได้ทุกระดับ
        return true;
    }

    @Override
    public boolean allowsReverseEdge() {
        return true;
    }
}
