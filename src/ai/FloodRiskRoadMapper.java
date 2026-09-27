package src.ai;

import src.model.CrisisGraph;
import src.model.Edge;
import src.model.Node;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * แปลง Flood Risk Grid (GeoJSON) ให้เป็นความเสี่ยงของถนนใน CrisisGraph
 *
 * แนวคิด:
 * 1) อ่านเฉพาะ grid ที่ risk_percent >= threshold
 * 2) สร้าง spatial hash จาก bounding box ของแต่ละ grid
 * 3) sample จุดบนแต่ละ Edge แล้วหา grid ที่ครอบจุดนั้น
 * 4) ใช้ค่าความเสี่ยงสูงสุดที่พบเป็น AI risk ของถนนเส้นนั้น
 *
 * จุดสำคัญ: คลาสนี้ "ไม่" ตีความ probability เป็นระดับน้ำจริง
 * มันเพียง map ความน่าจะเป็น/ความเสี่ยงจากโมเดลลงบนโครงข่ายถนน
 */
public final class FloodRiskRoadMapper {

    private static final Pattern RISK_PERCENT = Pattern.compile(
            "\\\"risk_percent\\\"\\s*:\\s*([-+0-9.eE]+)"
    );

    private static final Pattern COORDINATE_PAIR = Pattern.compile(
            "\\[\\s*([-+0-9.eE]+)\\s*,\\s*([-+0-9.eE]+)\\s*\\]"
    );

    // ~220 เมตรในแนว latitude แถวหาดใหญ่; grid จริงประมาณ 100 เมตร
    private static final double BUCKET_SIZE_DEG = 0.002;

    // sample 5 ตำแหน่งต่อถนน ป้องกัน edge ยาวพาดผ่านหลาย grid
    private static final double[] EDGE_SAMPLES = {0.0, 0.25, 0.50, 0.75, 1.0};

    public Result mapRoadRisk(
            CrisisGraph graph,
            Path geoJsonPath,
            double thresholdPercent
    ) throws IOException {

        if (graph == null) {
            throw new IllegalArgumentException("graph must not be null");
        }

        double threshold = clamp(thresholdPercent, 0.0, 100.0);
        SpatialIndex index = loadRiskCells(geoJsonPath, threshold);

        Map<String, Double> riskByEdgeId = new LinkedHashMap<>();
        double maxRoadRisk = 0.0;

        for (Edge edge : graph.getAllEdges().values()) {
            double risk = maxRiskAlongEdge(index, edge);

            if (risk >= threshold / 100.0 && risk > 0.0) {
                riskByEdgeId.put(edge.getId(), risk);
                maxRoadRisk = Math.max(maxRoadRisk, risk);
            }
        }

        return new Result(
                riskByEdgeId,
                index.cellCount,
                threshold,
                maxRoadRisk
        );
    }

    private SpatialIndex loadRiskCells(
            Path geoJsonPath,
            double thresholdPercent
    ) throws IOException {

        if (geoJsonPath == null || !Files.isRegularFile(geoJsonPath)) {
            throw new IOException("ไม่พบไฟล์ Flood Risk GeoJSON: " + geoJsonPath);
        }

        SpatialIndex index = new SpatialIndex(BUCKET_SIZE_DEG);

        try (BufferedReader reader = Files.newBufferedReader(
                geoJsonPath,
                StandardCharsets.UTF_8
        )) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (!line.contains("\"type\": \"Feature\"")) {
                    continue;
                }

                Matcher riskMatcher = RISK_PERCENT.matcher(line);
                if (!riskMatcher.find()) {
                    continue;
                }

                double riskPercent;
                try {
                    riskPercent = Double.parseDouble(riskMatcher.group(1));
                } catch (NumberFormatException ex) {
                    continue;
                }

                if (riskPercent < thresholdPercent) {
                    continue;
                }

                int coordinatesPos = line.indexOf("\"coordinates\"");
                if (coordinatesPos < 0) {
                    continue;
                }

                String geometryPart = line.substring(coordinatesPos);
                Matcher coordinateMatcher = COORDINATE_PAIR.matcher(geometryPart);

                double minLon = Double.POSITIVE_INFINITY;
                double minLat = Double.POSITIVE_INFINITY;
                double maxLon = Double.NEGATIVE_INFINITY;
                double maxLat = Double.NEGATIVE_INFINITY;
                int coordinateCount = 0;

                while (coordinateMatcher.find()) {
                    double lon = Double.parseDouble(coordinateMatcher.group(1));
                    double lat = Double.parseDouble(coordinateMatcher.group(2));

                    minLon = Math.min(minLon, lon);
                    minLat = Math.min(minLat, lat);
                    maxLon = Math.max(maxLon, lon);
                    maxLat = Math.max(maxLat, lat);
                    coordinateCount++;
                }

                if (coordinateCount < 3) {
                    continue;
                }

                RiskCell cell = new RiskCell(
                        minLon,
                        minLat,
                        maxLon,
                        maxLat,
                        clamp(riskPercent / 100.0, 0.0, 1.0)
                );

                index.add(cell);
            }
        }

        return index;
    }

    private double maxRiskAlongEdge(SpatialIndex index, Edge edge) {
        Node a = edge.getSource();
        Node b = edge.getTarget();
        double maxRisk = 0.0;

        for (double t : EDGE_SAMPLES) {
            double lat = a.getLatitude() + (b.getLatitude() - a.getLatitude()) * t;
            double lon = a.getLongitude() + (b.getLongitude() - a.getLongitude()) * t;
            maxRisk = Math.max(maxRisk, index.riskAt(lat, lon));

            if (maxRisk >= 1.0) {
                break;
            }
        }

        return maxRisk;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record RiskCell(
            double minLon,
            double minLat,
            double maxLon,
            double maxLat,
            double risk
    ) {
        boolean contains(double lat, double lon) {
            return lon >= minLon && lon <= maxLon
                    && lat >= minLat && lat <= maxLat;
        }
    }

    private static final class SpatialIndex {
        private final double bucketSize;
        private final Map<Long, List<RiskCell>> buckets = new HashMap<>();
        private int cellCount = 0;

        private SpatialIndex(double bucketSize) {
            this.bucketSize = bucketSize;
        }

        private void add(RiskCell cell) {
            int minX = bucketX(cell.minLon());
            int maxX = bucketX(cell.maxLon());
            int minY = bucketY(cell.minLat());
            int maxY = bucketY(cell.maxLat());

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    buckets.computeIfAbsent(key(x, y), k -> new ArrayList<>())
                            .add(cell);
                }
            }

            cellCount++;
        }

        private double riskAt(double lat, double lon) {
            List<RiskCell> candidates = buckets.get(
                    key(bucketX(lon), bucketY(lat))
            );

            if (candidates == null || candidates.isEmpty()) {
                return 0.0;
            }

            double risk = 0.0;
            for (RiskCell cell : candidates) {
                if (cell.contains(lat, lon)) {
                    risk = Math.max(risk, cell.risk());
                }
            }
            return risk;
        }

        private int bucketX(double lon) {
            return (int) Math.floor(lon / bucketSize);
        }

        private int bucketY(double lat) {
            return (int) Math.floor(lat / bucketSize);
        }

        private long key(int x, int y) {
            return (((long) x) << 32) ^ (y & 0xffffffffL);
        }
    }

    public static final class Result {
        private final Map<String, Double> riskByEdgeId;
        private final int riskCellCount;
        private final double thresholdPercent;
        private final double maxRoadRisk;

        private Result(
                Map<String, Double> riskByEdgeId,
                int riskCellCount,
                double thresholdPercent,
                double maxRoadRisk
        ) {
            this.riskByEdgeId = riskByEdgeId;
            this.riskCellCount = riskCellCount;
            this.thresholdPercent = thresholdPercent;
            this.maxRoadRisk = maxRoadRisk;
        }

        public Map<String, Double> getRiskByEdgeId() {
            return riskByEdgeId;
        }

        public int getAffectedEdgeCount() {
            return riskByEdgeId.size();
        }

        public int getRiskCellCount() {
            return riskCellCount;
        }

        public double getThresholdPercent() {
            return thresholdPercent;
        }

        public double getMaxRoadRisk() {
            return maxRoadRisk;
        }
    }
}
