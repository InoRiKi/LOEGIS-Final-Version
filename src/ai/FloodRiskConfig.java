package src.ai;

import java.nio.file.Path;

/**
 * จุดรวม configuration ของระบบ AI Flood Prediction
 *
 * ลำดับการเลือก path:
 * 1) -Dloegis.ai.floodGeoJson=...
 * 2) Environment variable LOEGIS_FLOOD_GEOJSON
 * 3) ค่า default เดิมของโปรเจกต์
 */
public final class FloodRiskConfig {

    private static final String DEFAULT_GEOJSON_PATH =
            "AI_Model/flood_risk_grid.geojson";

    private FloodRiskConfig() {
    }

    public static Path getFloodRiskGeoJsonPath() {
        String systemProperty =
                System.getProperty("loegis.ai.floodGeoJson");

        if (systemProperty != null && !systemProperty.isBlank()) {
            return Path.of(systemProperty.trim());
        }

        String environment =
                System.getenv("LOEGIS_FLOOD_GEOJSON");

        if (environment != null && !environment.isBlank()) {
            return Path.of(environment.trim());
        }

        return Path.of(DEFAULT_GEOJSON_PATH);
    }
}
