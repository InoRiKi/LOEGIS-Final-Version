package src.rescue;

import src.model.Node;

/**
 * จุดขอความช่วยเหลือหนึ่งจุดสำหรับ Multi-Vehicle Rescue Planner
 */
public class RescueRequest {
    private final String id;
    private final Node node;
    private final int people;
    private final String priority;
    private final String note;
    private final long createdAtEpochMs;

    public RescueRequest(String id, Node node, int people, String priority, String note) {
        this.id = id;
        this.node = node;
        this.people = Math.max(1, people);
        this.priority = normalizePriority(priority);
        this.note = note == null ? "" : note.trim();
        this.createdAtEpochMs = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public Node getNode() { return node; }
    public int getPeople() { return people; }
    public String getPriority() { return priority; }
    public String getNote() { return note; }
    public long getCreatedAtEpochMs() { return createdAtEpochMs; }

    /** ค่ายิ่งต่ำยิ่งเร่งด่วนในการคำนวณ assignment score */
    public double priorityFactor() {
        return switch (priority) {
            case "CRITICAL" -> 0.40;
            case "HIGH" -> 0.62;
            case "LOW" -> 1.20;
            default -> 1.00;
        };
    }

    public static String normalizePriority(String value) {
        if (value == null) return "NORMAL";
        String v = value.trim().toUpperCase();
        return switch (v) {
            case "CRITICAL", "HIGH", "NORMAL", "LOW" -> v;
            default -> "NORMAL";
        };
    }
}
