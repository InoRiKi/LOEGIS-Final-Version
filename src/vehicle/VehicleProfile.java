package src.vehicle;

import src.model.Edge;

/**
 * ความสามารถของยานพาหนะที่ใช้โดยระบบหาเส้นทางแบบ Risk-Aware
 *
 * สำคัญ: riskLevel จาก AI คือ flood probability/susceptibility ไม่ใช่ระดับน้ำจริง
 * จึงไม่ใช้ค่าความเสี่ยง AI เพื่อตัดสินว่า "รถผ่านไม่ได้" โดยตรง
 *
 * การปิดถนนจริงใช้ passable flag ซึ่งมาจาก Manual Flood / field report
 * ส่วน AI risk ใช้เป็น cost ให้ RiskAwareRouter หลีกเลี่ยงพื้นที่เสี่ยงเมื่อมีทางเลือก
 */
public enum VehicleProfile {
    DELIVERY_TRUCK("รถขนส่ง", false),
    RESCUE_TRUCK("รถกู้ภัย", true),
    HIGH_WATER_RESCUE("รถกู้ภัยยกสูง", true);

    private final String displayName;
    private final boolean reverseAllowed;

    VehicleProfile(String displayName, boolean reverseAllowed) {
        this.displayName = displayName;
        this.reverseAllowed = reverseAllowed;
    }

    public String getDisplayName() { return displayName; }
    public boolean isReverseAllowed() { return reverseAllowed; }

    public boolean canPass(Edge edge) {
        if (edge == null) return false;

        // รถขนส่งปกติยังต้องเคารพกฎ one-way
        if (!reverseAllowed && edge.isReverseEdge()) return false;
        if (!reverseAllowed && !edge.isTrafficAllowed()) return false;

        // AI risk ไม่ได้ปิดถนนตรงนี้
        // ถ้าถนนถูกยืนยันว่าผ่านไม่ได้ Manual Flood จะ set passable=false เอง
        return reverseAllowed
                ? edge.isRescuePassable()
                : edge.isRoadPassable();
    }

    public static VehicleProfile from(String value, boolean rescueMode) {
        if (value != null) {
            try {
                return VehicleProfile.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        return rescueMode ? RESCUE_TRUCK : DELIVERY_TRUCK;
    }
}
