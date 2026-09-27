package src.ai;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service สำหรับ AI Flood Prediction
 *
 * เวอร์ชันปัจจุบันอ่านผล spatial probability จากโมเดล Recurrent Flood XGBoost ชุดใหม่ใน flood_risk_grid.geojson
 * โดยไม่ผูกกับ Web/HttpServer ทำให้ภายหลังสามารถเปลี่ยน implementation
 * เป็นการเรียก Python/XGBoost แบบ real-time ได้ในคลาสนี้โดยตรง
 */
public class FloodPredictionService {

    public FloodPredictionResult loadPrediction() {
        Path file = FloodRiskConfig
                .getFloodRiskGeoJsonPath()
                .toAbsolutePath()
                .normalize();

        System.out.println("[🤖 AI] อ่านไฟล์: " + file);

        if (!Files.exists(file)) {
            return FloodPredictionResult.error(
                    404,
                    "ไม่พบผล Recurrent Flood GeoJSON",
                    file.toString()
            );
        }

        if (!Files.isRegularFile(file)) {
            return FloodPredictionResult.error(
                    404,
                    "Path ไม่ใช่ไฟล์",
                    file.toString()
            );
        }

        try {
            String json = Files.readString(
                    file,
                    StandardCharsets.UTF_8
            );

            if (json.trim().isEmpty()) {
                return FloodPredictionResult.error(
                        500,
                        "ไฟล์ Recurrent Flood GeoJSON ว่าง",
                        file.toString()
                );
            }

            if (!json.trim().startsWith("{")) {
                return FloodPredictionResult.error(
                        500,
                        "Recurrent Flood GeoJSON ไม่ใช่ JSON object",
                        file.toString()
                );
            }

            System.out.println(
                    "[🤖 AI] อ่านสำเร็จ: "
                            + json.getBytes(StandardCharsets.UTF_8).length
                            + " bytes"
            );

            return FloodPredictionResult.success(
                    json,
                    file.toString()
            );

        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || message.isBlank()) {
                message = "Unknown error";
            }

            return FloodPredictionResult.error(
                    500,
                    "อ่าน Recurrent Flood GeoJSON ไม่สำเร็จ: " + message,
                    file.toString()
            );
        }
    }
}
