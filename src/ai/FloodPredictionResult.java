package src.ai;

/**
 * ผลลัพธ์จาก FloodPredictionService
 * แยกข้อมูล AI ออกจาก HTTP layer เพื่อให้ภายหลังนำไปใช้กับ Swing
 * หรือ service อื่นได้โดยไม่ต้องพึ่ง ApiServer
 */
public final class FloodPredictionResult {

    private final boolean success;
    private final int statusCode;
    private final String geoJson;
    private final String message;
    private final String path;

    private FloodPredictionResult(
            boolean success,
            int statusCode,
            String geoJson,
            String message,
            String path
    ) {
        this.success = success;
        this.statusCode = statusCode;
        this.geoJson = geoJson;
        this.message = message;
        this.path = path;
    }

    public static FloodPredictionResult success(
            String geoJson,
            String path
    ) {
        return new FloodPredictionResult(
                true, 200, geoJson,
                "โหลดผลการทำนายสำเร็จ", path
        );
    }

    public static FloodPredictionResult error(
            int statusCode,
            String message,
            String path
    ) {
        return new FloodPredictionResult(
                false, statusCode, null, message, path
        );
    }

    public boolean isSuccess() { return success; }
    public int getStatusCode() { return statusCode; }
    public String getGeoJson() { return geoJson; }
    public String getMessage() { return message; }
    public String getPath() { return path; }

    public String toErrorJson() {
        return "{\"ok\":false,\"error\":\""
                + escapeJson(message)
                + "\",\"path\":\""
                + escapeJson(path == null ? "" : path)
                + "\"}";
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
