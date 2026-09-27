package src.server;

import src.ai.FloodRiskConfig;
import src.ai.FloodRiskRoadMapper;
import src.model.CrisisGraph;
import src.model.Edge;
import src.model.Node;
import src.routing.DijkstraRouter;
import src.routing.RiskAwareRouter;
import src.routing.RoutePreference;
import src.vehicle.VehicleProfile;
import src.rescue.MultiVehicleRescuePlanner;
import src.rescue.RescueAssignment;
import src.rescue.RescueRequest;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SimulationState {

    private CrisisGraph graph;

    private Node depot;
    private Node refugeeCamp;

    private final List<Node> rescuePoints = new ArrayList<>();
    private final List<RescueRequest> rescueRequests = new ArrayList<>();
    private final List<RescueAssignment> rescueAssignments = new ArrayList<>();
    private final List<LocalFloodZone> floodZones = new ArrayList<>();

    private int rescueRequestCounter = 1;
    private int fieldReportCounter = 1;
    private int rescuePlanTotalPeople = 0;
    private int rescuePlanAssignedPeople = 0;
    private int rescuePlanUnservedPeople = 0;

    // AI layer: เก็บค่าความเสี่ยงที่ map ลงบนถนนแล้ว (0.0 - 1.0)
    // แยกจาก floodZones เพื่อให้ Manual Flood สามารถ override ทับ AI ได้
    private final Map<String, Double> aiRiskByEdgeId = new HashMap<>();

    private boolean aiRiskActive = false;
    private double aiRiskThresholdPercent = 25.0;
    private int aiRiskCellCount = 0;
    private int aiAffectedEdgeCount = 0;
    private double aiMaxRoadRisk = 0.0;

    private List<Edge> optimalRoute = new ArrayList<>();

    private double mockRouteCost = 0.0;

    private int fleetSize = 0;
    private double vehicleCapacity = 0.0;

    private double demandWater = 500.0;
    private double demandMedical = 50.0;
    private double timeStart = 60.0;
    private double timeEnd = 180.0;

    private String routePreference = "BALANCED";
    private String vehicleProfile = "DELIVERY_TRUCK";
    private double routeDistanceMeters = 0.0;
    private double routeAverageRisk = 0.0;
    private double routeMaxRisk = 0.0;
    private String routeExplanation = "";

    private Consumer<String> logger;

    public SimulationState() {
        this.logger = System.out::println;
    }

    public SimulationState(Consumer<String> logger) {
        this.logger = logger != null
                ? logger
                : System.out::println;
    }

    private void log(String text) {
        if (logger != null) {
            logger.accept(text);
        }
    }


    // =========================================================
    // BASIC
    // =========================================================

    public boolean isGraphLoaded() {
        return graph != null;
    }


    public void reset() {

        graph = null;

        depot = null;
        refugeeCamp = null;

        rescuePoints.clear();
        rescueRequests.clear();
        rescueAssignments.clear();
        resetRescuePlanStats();
        rescueRequestCounter = 1;
        fieldReportCounter = 1;
        floodZones.clear();
        clearAiRiskMetadata();

        optimalRoute.clear();

        mockRouteCost = 0.0;
        clearRouteMetrics();

        log("[🔄] รีเซ็ตระบบเรียบร้อย");
    }


    // =========================================================
    // LOAD MAP
    // =========================================================

    public boolean loadMap(
            String mapFilePath,
            boolean respectOneWay
    ) {

        try {

            CrisisGraph loadedGraph =
                    importGraphByReflection(
                            mapFilePath,
                            respectOneWay
                    );


            if (loadedGraph == null) {

                log(
                        "[❌] โหลดแผนที่ไม่สำเร็จ: " +
                                "ไม่พบ GraphImporter หรือ method import ที่ใช้ได้"
                );

                return false;
            }


            this.graph =
                    loadedGraph;


            depot = null;
            refugeeCamp = null;

            rescuePoints.clear();
            rescueRequests.clear();
            rescueAssignments.clear();
            resetRescuePlanStats();
            rescueRequestCounter = 1;
            fieldReportCounter = 1;
            floodZones.clear();
            clearAiRiskMetadata();

            optimalRoute.clear();

            mockRouteCost =
                    0.0;
            clearRouteMetrics();


            setTrafficMode(
                    respectOneWay
            );


            log(
                    "[✓] โหลดแผนที่สำเร็จ: " +
                            mapFilePath
            );


            return true;

        } catch (Exception e) {

            log(
                    "[❌] โหลดแผนที่ล้มเหลว: " +
                            e.getMessage()
            );

            return false;
        }
    }


    private CrisisGraph importGraphByReflection(
            String mapFilePath,
            boolean respectOneWay
    ) {

        String[] classNames = {

                "src.GraphImporter",

                "src.server.GraphImporter",

                "src.model.GraphImporter",

                "src.importer.GraphImporter",

                "src.io.GraphImporter",

                "src.loader.GraphImporter"
        };


        for (
                String className :
                classNames
        ) {

            try {

                Class<?> cls =
                        Class.forName(
                                className
                        );


                Object instance =
                        null;


                for (
                        Method method :
                        cls.getMethods()
                ) {

                    if (
                            !CrisisGraph.class
                                    .isAssignableFrom(
                                            method.getReturnType()
                                    )
                    ) {
                        continue;
                    }


                    Class<?>[] params =
                            method.getParameterTypes();


                    Object target =
                            null;


                    if (
                            !Modifier.isStatic(
                                    method.getModifiers()
                            )
                    ) {

                        if (instance == null) {

                            instance =
                                    cls.getDeclaredConstructor()
                                            .newInstance();
                        }

                        target =
                                instance;
                    }


                    /*
                     * GraphImporter.importMap(
                     *     String,
                     *     boolean
                     * )
                     */
                    if (
                            params.length == 2
                                    &&
                                    params[0] == String.class
                                    &&
                                    (
                                            params[1] == boolean.class
                                                    ||
                                                    params[1] == Boolean.class
                                    )
                    ) {

                        Object result =
                                method.invoke(
                                        target,
                                        mapFilePath,
                                        respectOneWay
                                );


                        return
                                (CrisisGraph)
                                        result;
                    }


                    /*
                     * GraphImporter.importMap(
                     *     String
                     * )
                     */
                    if (
                            params.length == 1
                                    &&
                                    params[0] == String.class
                    ) {

                        Object result =
                                method.invoke(
                                        target,
                                        mapFilePath
                                );


                        return
                                (CrisisGraph)
                                        result;
                    }

                }

            } catch (Exception ignored) {

                /*
                 * ลอง class ถัดไป
                 */
            }

        }


        return null;
    }


    // =========================================================
    // TRAFFIC / TRAVEL MODE
    // =========================================================

    public boolean setTrafficMode(
            boolean respectOneWay
    ) {

        if (!isGraphLoaded()) {

            log(
                    "[⚠️] กรุณาโหลดแผนที่ก่อน"
            );

            return false;
        }


        try {

            Method method =
                    graph.getClass()
                            .getMethod(
                                    "setRespectOneWay",
                                    boolean.class
                            );


            method.invoke(
                    graph,
                    respectOneWay
            );


            optimalRoute.clear();


            return true;

        } catch (Exception e) {

            /*
             * ถ้ากราฟเก่าไม่มี method
             * ให้ระบบส่วนอื่นทำงานต่อได้
             */
            return true;
        }
    }


    // =========================================================
    // START / TARGET
    // =========================================================

    public boolean setDepotById(
            String nodeId
    ) {

        if (!isGraphLoaded()) {

            log(
                    "[⚠️] กรุณาโหลดแผนที่ก่อน"
            );

            return false;
        }


        Node node =
                findNodeById(
                        nodeId
                );


        if (node == null) {

            log(
                    "[⚠️] ไม่พบโหนดจุดเริ่มต้น: " +
                            nodeId
            );

            return false;
        }


        /*
         * ถ้ามี depot เก่า
         * ปลดสถานะก่อน
         */
        if (depot != null) {

            depot.setDepot(
                    false
            );
        }


        depot =
                node;


        depot.setDepot(
                true
        );


        rescueAssignments.clear();
        resetRescuePlanStats();
        optimalRoute.clear();

        mockRouteCost =
                0.0;
        clearRouteMetrics();


        log(
                "[📍] ตั้งจุดเริ่มต้นที่โหนด: " +
                        node.getId()
        );


        return true;
    }


    public boolean setCampById(
            String nodeId
    ) {

        if (!isGraphLoaded()) {

            log(
                    "[⚠️] กรุณาโหลดแผนที่ก่อน"
            );

            return false;
        }


        Node node =
                findNodeById(
                        nodeId
                );


        if (node == null) {

            log(
                    "[⚠️] ไม่พบโหนดเป้าหมาย: " +
                            nodeId
            );

            return false;
        }


        refugeeCamp =
                node;


        optimalRoute.clear();

        mockRouteCost =
                0.0;
        clearRouteMetrics();


        log(
                "[🎯] ตั้งเป้าหมายการขนส่งที่โหนด: " +
                        node.getId()
        );


        return true;
    }


    /**
     * หน้า UI ใหม่ใช้ผู้ประสบภัยเป็น
     * "เป้าหมายเดียว"
     *
     * ดังนั้นเมื่อเลือกจุดใหม่
     * จะล้างเป้าหมายเดิมออกก่อน
     */
    public boolean addRescuePointById(
            String nodeId
    ) {
        // compatibility: จุดเดี่ยวแบบเดิม = request ปกติ 1 คน
        return addRescueRequestById(nodeId, 1, "NORMAL", "", true);
    }


    /**
     * เพิ่มคำขอกู้ภัยลงคิว
     * replaceExisting=true ใช้กับโหมด route เดี่ยวแบบเดิม
     * replaceExisting=false ใช้กับ Multi-Vehicle Rescue Planner
     */
    public boolean addRescueRequestById(
            String nodeId,
            int people,
            String priority,
            String note,
            boolean replaceExisting
    ) {
        if (!isGraphLoaded()) {
            log("[⚠️] กรุณาโหลดแผนที่ก่อน");
            return false;
        }

        Node node = findNodeById(nodeId);
        if (node == null) {
            log("[⚠️] ไม่พบโหนดผู้ประสบภัย: " + nodeId);
            return false;
        }

        if (replaceExisting) {
            rescuePoints.clear();
            rescueRequests.clear();
        }

        rescuePoints.add(node);

        String requestId = "REQ-" + String.format("%03d", rescueRequestCounter++);
        RescueRequest request = new RescueRequest(
                requestId,
                node,
                Math.max(1, people),
                priority,
                note
        );
        rescueRequests.add(request);

        rescueAssignments.clear();
        resetRescuePlanStats();
        optimalRoute.clear();
        mockRouteCost = 0.0;
        clearRouteMetrics();

        log("[🆘 Field] เพิ่มคำขอกู้ภัย " + requestId
                + " | Node " + node.getId()
                + " | " + request.getPeople() + " คน"
                + " | Priority " + request.getPriority());

        return true;
    }


    public boolean clearRescueRequests() {
        rescuePoints.clear();
        rescueRequests.clear();
        rescueAssignments.clear();
        resetRescuePlanStats();
        optimalRoute.clear();
        clearRouteMetrics();
        log("[🧹 Rescue] ล้างคำขอกู้ภัยทั้งหมด");
        return true;
    }


    public void setDepot(
            Node node
    ) {

        this.depot =
                node;
    }


    public void setCamp(
            Node node
    ) {

        this.refugeeCamp =
                node;
    }


    public boolean addRescuePoint(
            Node node
    ) {
        if (node == null) return false;
        rescuePoints.add(node);
        RescueRequest request = new RescueRequest(
                "REQ-" + String.format("%03d", rescueRequestCounter++),
                node,
                1,
                "NORMAL",
                ""
        );
        rescueRequests.add(request);
        return true;
    }


    // =========================================================
    // LEGACY DEMAND / FLEET
    // =========================================================
    //
    // หน้าเว็บใหม่ไม่ได้แสดงส่วนนี้
    // แต่เก็บ method ไว้เพื่อ compatibility
    // กับ code เดิม
    // =========================================================

    public void setDemandParams(
            double water,
            double medical,
            double startMin,
            double endMin
    ) {

        this.demandWater =
                water;

        this.demandMedical =
                medical;

        this.timeStart =
                startMin;

        this.timeEnd =
                endMin;
    }


    public boolean setupFleet(
            int fleetSize,
            double capacity
    ) {

        this.fleetSize =
                fleetSize;

        this.vehicleCapacity =
                capacity;


        return true;
    }


    // =========================================================
    // FLOOD
    // =========================================================

    public boolean addFloodZone(
            double lat,
            double lon,
            double radiusKm,
            String level
    ) {
        // compatibility กับ API เดิม: ถือว่าเป็น Field Report แบบยืนยันน้ำท่วม
        return addFieldReport(
                lat,
                lon,
                radiusKm,
                level,
                "CONFIRMED_FLOOD",
                "Manual report",
                ""
        );
    }


    /**
     * Phase 2: Field Report / Manual Override
     *
     * overrideType:
     * - CONFIRMED_FLOOD = ใช้ระดับ SHALLOW/MEDIUM/DEEP ทับ baseline AI
     * - ROAD_CLOSED     = ยืนยันว่าถนนปิด ใช้ไม่ได้ทั้งขนส่งและกู้ภัย
     */
    public boolean addFieldReport(
            double lat,
            double lon,
            double radiusKm,
            String level,
            String overrideType,
            String reporter,
            String note
    ) {
        if (!isGraphLoaded()) {
            log("[⚠️] กรุณาโหลดแผนที่ก่อน");
            return false;
        }

        if (radiusKm <= 0 || Double.isNaN(radiusKm) || Double.isInfinite(radiusKm)) {
            return false;
        }

        String reportId = "FR-" + String.format("%03d", fieldReportCounter++);
        LocalFloodZone zone = new LocalFloodZone(
                reportId,
                lat,
                lon,
                radiusKm,
                level,
                overrideType,
                reporter,
                note,
                System.currentTimeMillis()
        );

        floodZones.add(zone);
        rebuildEnvironmentalState();

        rescueAssignments.clear();
        resetRescuePlanStats();
        optimalRoute.clear();
        mockRouteCost = 0.0;
        clearRouteMetrics();

        log("[📡 Field Report] " + reportId
                + " | " + zone.overrideType
                + " | " + zone.level
                + " | ผู้รายงาน: " + zone.reporter);

        return true;
    }


    /**
     * เพิ่ม Flood Zone หลายจุดจาก /api/addFloodZonesBatch
     * เก็บไว้เพื่อ compatibility กับ ApiServer/app.js
     */
    public boolean addFloodZonesBatch(
            List<Map<String, Object>> zones
    ) {
        if (!isGraphLoaded()) {
            log("[⚠️] กรุณาโหลดแผนที่ก่อน");
            return false;
        }

        if (zones == null || zones.isEmpty()) {
            return true;
        }

        boolean addedAny = false;

        for (Map<String, Object> zone : zones) {
            if (zone == null) {
                continue;
            }

            try {
                Object latValue = zone.get("lat");
                Object lonValue = zone.get("lon");
                Object radiusValue = zone.get("radiusKm");

                if (latValue == null || lonValue == null || radiusValue == null) {
                    continue;
                }

                double lat = latValue instanceof Number
                        ? ((Number) latValue).doubleValue()
                        : Double.parseDouble(String.valueOf(latValue));

                double lon = lonValue instanceof Number
                        ? ((Number) lonValue).doubleValue()
                        : Double.parseDouble(String.valueOf(lonValue));

                double radiusKm = radiusValue instanceof Number
                        ? ((Number) radiusValue).doubleValue()
                        : Double.parseDouble(String.valueOf(radiusValue));

                String level = String.valueOf(
                        zone.getOrDefault("level", "MEDIUM")
                );

                if (addFloodZone(lat, lon, radiusKm, level)) {
                    addedAny = true;
                }
            } catch (RuntimeException ignored) {
                // ข้าม zone ที่ข้อมูลไม่ถูกต้อง
            }
        }

        return addedAny;
    }

    public boolean clearFloodZones() {

        if (!isGraphLoaded()) {

            log(
                    "[⚠️] กรุณาโหลดแผนที่ก่อน"
            );

            return false;
        }


        floodZones.clear();

        // ล้างเฉพาะ Manual Flood แต่ยังคง AI risk ถ้าเปิดใช้อยู่
        rebuildEnvironmentalState();

        rescueAssignments.clear();
        resetRescuePlanStats();
        optimalRoute.clear();

        mockRouteCost =
                0.0;
        clearRouteMetrics();


        return true;
    }


    // =========================================================
    // AI FLOOD RISK -> ROAD NETWORK
    // =========================================================

    /**
     * อ่าน flood_risk_grid.geojson แล้ว map risk ลงบน Edge ของถนน
     * โดยไม่ต้องให้ browser ส่ง polygon หลายหมื่นชิ้นกลับมาที่ server
     */
    public boolean applyAiFloodRisk(double thresholdPercent) {

        if (!isGraphLoaded()) {
            log("[⚠️ AI] กรุณาโหลดแผนที่ก่อนใช้ผลทำนายกับเส้นทาง");
            return false;
        }

        try {
            FloodRiskRoadMapper mapper = new FloodRiskRoadMapper();

            FloodRiskRoadMapper.Result result = mapper.mapRoadRisk(
                    graph,
                    FloodRiskConfig.getFloodRiskGeoJsonPath()
                            .toAbsolutePath()
                            .normalize(),
                    thresholdPercent
            );

            aiRiskByEdgeId.clear();
            aiRiskByEdgeId.putAll(result.getRiskByEdgeId());

            aiRiskActive = true;
            aiRiskThresholdPercent = result.getThresholdPercent();
            aiRiskCellCount = result.getRiskCellCount();
            aiAffectedEdgeCount = result.getAffectedEdgeCount();
            aiMaxRoadRisk = result.getMaxRoadRisk();

            rebuildEnvironmentalState();

            rescueAssignments.clear();
            resetRescuePlanStats();
            optimalRoute.clear();
            mockRouteCost = 0.0;
            clearRouteMetrics();

            log(
                    "[🤖🛣️ AI] map Flood Risk ลงถนนแล้ว "
                            + aiAffectedEdgeCount
                            + " เส้น | grid เสี่ยง "
                            + aiRiskCellCount
                            + " ช่อง | threshold ≥ "
                            + String.format("%.0f%%", aiRiskThresholdPercent)
            );

            return true;

        } catch (Exception e) {
            log("[❌ AI] map Flood Risk ลงถนนไม่สำเร็จ: " + e.getMessage());
            return false;
        }
    }


    /**
     * ปิดเฉพาะ AI layer แล้ว rebuild จาก Manual Flood ที่ยังมีอยู่
     */
    public boolean clearAiFloodRisk() {

        if (!isGraphLoaded()) {
            return false;
        }

        clearAiRiskMetadata();
        rebuildEnvironmentalState();

        rescueAssignments.clear();
        resetRescuePlanStats();
        optimalRoute.clear();
        mockRouteCost = 0.0;
        clearRouteMetrics();

        log("[🧹 AI] ปิด AI Flood Risk บนโครงข่ายถนนแล้ว");
        return true;
    }


    private void clearAiRiskMetadata() {
        aiRiskByEdgeId.clear();
        aiRiskActive = false;
        aiRiskThresholdPercent = 25.0;
        aiRiskCellCount = 0;
        aiAffectedEdgeCount = 0;
        aiMaxRoadRisk = 0.0;
    }


    /**
     * สร้าง environmental state ใหม่ทุกครั้งจาก 2 layer:
     * 1) AI prediction = baseline risk
     * 2) Manual Flood = field/simulation override
     *
     * ลำดับนี้สำคัญ เพราะ manual flood ถูกใช้ทีหลังและสามารถเพิ่มความรุนแรง
     * หรือปิดถนนทับค่าที่ AI ทำนายไว้ได้
     */
    private void rebuildEnvironmentalState() {

        /*
         * reset เฉพาะ environmental state
         *
         * ไม่แตะ trafficAllowed
         * เพื่อไม่ทำลายโหมด
         * ขนส่ง / กู้ภัย
         */
        for (
                Edge edge :
                getEdges()
        ) {

            edge.setRiskLevel(
                    0.0
            );

            edge.setTravelTimeFactor(
                    1.0
            );

            edge.setTransportPassable(
                    true
            );
            edge.setRescuePassable(
                    true
            );
        }


        // ---------------------------------------------------------
        // Layer 1: AI Flood Risk
        // ---------------------------------------------------------
        if (aiRiskActive) {
            for (Edge edge : getEdges()) {
                Double aiRisk = aiRiskByEdgeId.get(edge.getId());
                if (aiRisk != null) {
                    applyAiRiskToEdge(edge, aiRisk);
                }
            }
        }


        // ---------------------------------------------------------
        // Layer 2: Manual Flood Zone / field override
        // ---------------------------------------------------------
        for (
                Edge edge :
                getEdges()
        ) {

            Node source =
                    edge.getSource();

            Node target =
                    edge.getTarget();


            double midLat =
                    (
                            source.getLatitude()
                                    +
                                    target.getLatitude()
                    )
                            /
                            2.0;


            double midLon =
                    (
                            source.getLongitude()
                                    +
                                    target.getLongitude()
                    )
                            /
                            2.0;


            for (
                    LocalFloodZone zone :
                    floodZones
            ) {

                double distKm =
                        distanceKm(
                                midLat,
                                midLon,
                                zone.lat,
                                zone.lon
                        );


                if (
                        distKm <=
                                zone.radiusKm
                ) {

                    if ("ROAD_CLOSED".equals(zone.overrideType)) {
                        applyRoadClosedOverride(edge);
                    } else {
                        applyFloodLevelToEdge(
                                edge,
                                zone.level
                        );
                    }
                }

            }

        }

    }


    /**
     * AI ให้ "probability/risk" ไม่ใช่ระดับน้ำจริง
     * จึงไม่สั่งปิดถนนตรงนี้ แต่เพิ่ม risk + travel penalty ให้ router หลีกเลี่ยง
     * ถนนจะถูกปิดจริงจาก Manual Flood/ข้อมูลภาคสนามที่ยืนยันแล้ว
     */
    private void applyAiRiskToEdge(
            Edge edge,
            double risk
    ) {
        double normalizedRisk = Math.max(0.0, Math.min(1.0, risk));

        edge.setRiskLevel(
                Math.max(edge.getRiskLevel(), normalizedRisk)
        );

        double factor;

        if (normalizedRisk >= 0.75) {
            factor = 2.20;
        } else if (normalizedRisk >= 0.50) {
            factor = 1.60;
        } else if (normalizedRisk >= 0.25) {
            factor = 1.20;
        } else {
            factor = 1.00;
        }

        edge.setTravelTimeFactor(
                Math.max(edge.getTravelTimeFactor(), factor)
        );
    }


    private void applyRoadClosedOverride(Edge edge) {
        edge.setRiskLevel(Math.max(edge.getRiskLevel(), 1.0));
        edge.setTravelTimeFactor(Math.max(edge.getTravelTimeFactor(), 10.0));
        edge.setTransportPassable(false);
        edge.setRescuePassable(false);
    }


    private void applyFloodLevelToEdge(
            Edge edge,
            String level
    ) {

        String lv =
                level == null
                        ? "MEDIUM"
                        : level.toUpperCase();

        // กู้ภัยผ่านน้ำได้ทุกระดับ แยกจากขนส่ง
        edge.setRescuePassable(true);


        /*
         * น้ำตื้น
         */
        if (
                lv.equals(
                        "SHALLOW"
                )
        ) {

            edge.setRiskLevel(
                    Math.max(
                            edge.getRiskLevel(),
                            0.30
                    )
            );


            edge.setTravelTimeFactor(
                    Math.max(
                            edge.getTravelTimeFactor(),
                            1.5
                    )
            );


            edge.setTransportPassable(
                    true
            );
            edge.setRescuePassable(
                    true
            );


            return;
        }


        /*
         * น้ำปานกลาง
         */
        if (
                lv.equals(
                        "MEDIUM"
                )
        ) {

            edge.setRiskLevel(
                    Math.max(
                            edge.getRiskLevel(),
                            0.65
                    )
            );


            edge.setTravelTimeFactor(
                    Math.max(
                            edge.getTravelTimeFactor(),
                            3.0
                    )
            );


            edge.setTransportPassable(
                    false
            );


            return;
        }


        /*
         * น้ำลึก
         */
        if (
                lv.equals(
                        "DEEP"
                )
        ) {

            edge.setRiskLevel(
                    Math.max(
                            edge.getRiskLevel(),
                            0.95
                    )
            );


            edge.setTravelTimeFactor(
                    Math.max(
                            edge.getTravelTimeFactor(),
                            8.0
                    )
            );


            edge.setTransportPassable(
                    false
            );


            return;
        }


        /*
         * fallback = MEDIUM
         */
        edge.setRiskLevel(
                Math.max(
                        edge.getRiskLevel(),
                        0.65
                )
        );


        edge.setTravelTimeFactor(
                Math.max(
                        edge.getTravelTimeFactor(),
                        3.0
                )
        );


        edge.setTransportPassable(
                true
        );
    }


    // =========================================================
    // TRANSPORT MODE
    // START -> CAMP
    // =========================================================

    public boolean runRouteToCamp() {
        return runRouteToCamp(routePreference, vehicleProfile);
    }

    public boolean runRouteToCamp(String preferenceValue, String vehicleValue) {
        if (!isGraphLoaded()) { log("[⚠️] กรุณาโหลดแผนที่ก่อน"); return false; }
        if (depot == null || refugeeCamp == null) { log("[⚠️] กรุณากำหนดจุดเริ่มต้นและเป้าหมายก่อน"); return false; }
        if (depot.getId().equals(refugeeCamp.getId())) { log("[⚠️] จุดเริ่มต้นและเป้าหมายต้องเป็นคนละจุด"); return false; }

        RoutePreference preference = RoutePreference.from(preferenceValue);
        VehicleProfile vehicle = VehicleProfile.from(vehicleValue, false);
        this.routePreference = preference.name();
        this.vehicleProfile = vehicle.name();

        RiskAwareRouter router = new RiskAwareRouter(graph);
        List<Edge> route = router.findRoute(depot, refugeeCamp, preference, vehicle);
        return acceptRiskAwareRoute(route, preference, vehicle);
    }

    // =========================================================
    // RESCUE MODE
    // START -> RESCUE TARGET
    // =========================================================

    public boolean runRescueMission() {
        return runRescueMission(routePreference, vehicleProfile);
    }

    public boolean runRescueMission(String preferenceValue, String vehicleValue) {
        if (!isGraphLoaded()) { log("[⚠️] กรุณาโหลดแผนที่ก่อน"); return false; }
        if (depot == null) { log("[⚠️] กรุณากำหนดจุดเริ่มต้นก่อน"); return false; }
        if (rescuePoints.isEmpty()) { log("[⚠️] กรุณากำหนดผู้ประสบภัยเป็นเป้าหมายก่อน"); return false; }

        // Compatibility guard: endpoint แบบเดิมเคยเลือกเฉพาะ "จุดล่าสุด" เท่านั้น
        // ถ้ามีหลาย request ให้ส่งต่อไป Multi-Vehicle Planner แทน เพื่อไม่ทำข้อมูลหล่น
        if (rescueRequests.size() > 1) {
            int autoFleet = rescueRequests.size();
            int autoCapacity = rescueRequests.stream()
                    .mapToInt(RescueRequest::getPeople)
                    .max()
                    .orElse(1);
            return runMultiVehicleRescue(
                    preferenceValue,
                    vehicleValue,
                    autoFleet,
                    autoCapacity
            );
        }

        Node target = rescuePoints.get(rescuePoints.size() - 1);
        if (depot.getId().equals(target.getId())) { log("[⚠️] จุดเริ่มต้นและเป้าหมายต้องเป็นคนละจุด"); return false; }

        RoutePreference preference = RoutePreference.from(preferenceValue);
        VehicleProfile vehicle = VehicleProfile.from(vehicleValue, true);
        if (vehicle == VehicleProfile.DELIVERY_TRUCK) vehicle = VehicleProfile.RESCUE_TRUCK;
        this.routePreference = preference.name();
        this.vehicleProfile = vehicle.name();

        RiskAwareRouter router = new RiskAwareRouter(graph);
        List<Edge> route = router.findRoute(depot, target, preference, vehicle);
        return acceptRiskAwareRoute(route, preference, vehicle);
    }

    /**
     * Phase 3: จัดรถหลายคันไปยังหลาย Rescue Request
     *
     * รถทุกคันเริ่มจาก depot เดียวกันในเวอร์ชันนำเสนอคืนนี้
     * Planner ใช้ RiskAwareRouter จริงกับ environmental state ล่าสุด
     * ดังนั้น AI + Field Report มีผลต่อ assignment โดยอัตโนมัติ
     */
    public boolean runMultiVehicleRescue(
            String preferenceValue,
            String vehicleValue,
            int requestedFleetSize,
            int requestedVehicleCapacity
    ) {
        if (!isGraphLoaded()) {
            log("[⚠️ Rescue Planner] กรุณาโหลดแผนที่ก่อน");
            return false;
        }
        if (depot == null) {
            log("[⚠️ Rescue Planner] กรุณากำหนดศูนย์/จุดเริ่มต้นก่อน");
            return false;
        }
        if (rescueRequests.isEmpty()) {
            log("[⚠️ Rescue Planner] กรุณาเพิ่มคำขอกู้ภัยอย่างน้อย 1 จุด");
            return false;
        }

        int safeFleet = Math.max(1, Math.min(50, requestedFleetSize));
        int safeCapacity = Math.max(1, Math.min(100, requestedVehicleCapacity));

        RoutePreference preference = RoutePreference.from(preferenceValue);
        VehicleProfile vehicle = VehicleProfile.from(vehicleValue, true);
        if (vehicle == VehicleProfile.DELIVERY_TRUCK) {
            vehicle = VehicleProfile.RESCUE_TRUCK;
        }

        this.routePreference = preference.name();
        this.vehicleProfile = vehicle.name();
        this.fleetSize = safeFleet;
        this.vehicleCapacity = safeCapacity;

        MultiVehicleRescuePlanner planner = new MultiVehicleRescuePlanner(graph);
        MultiVehicleRescuePlanner.PlanResult result = planner.plan(
                depot,
                rescueRequests,
                safeFleet,
                safeCapacity,
                preference,
                vehicle
        );

        rescueAssignments.clear();
        rescueAssignments.addAll(result.assignments());
        rescuePlanTotalPeople = result.totalPeople();
        rescuePlanAssignedPeople = result.assignedPeople();
        rescuePlanUnservedPeople = result.unservedPeople();

        optimalRoute.clear();
        if (!rescueAssignments.isEmpty()) {
            optimalRoute.addAll(rescueAssignments.get(0).getRoute());
        }

        // summary metrics สำหรับกล่องผลลัพธ์เดิม
        routeDistanceMeters = rescueAssignments.stream()
                .mapToDouble(RescueAssignment::getDistanceMeters)
                .sum();
        routeAverageRisk = rescueAssignments.stream()
                .mapToDouble(RescueAssignment::getAverageRisk)
                .average()
                .orElse(0.0);
        routeMaxRisk = rescueAssignments.stream()
                .mapToDouble(RescueAssignment::getMaxRisk)
                .max()
                .orElse(0.0);
        mockRouteCost = 0.0;

        int totalTrips = rescueAssignments.stream()
                .mapToInt(RescueAssignment::getTripCount)
                .sum();
        int totalPickupStops = rescueAssignments.stream()
                .mapToInt(RescueAssignment::getPickupStopCount)
                .sum();

        routeExplanation = "Rescue Planner | ใช้รถ "
                + rescueAssignments.size() + "/" + safeFleet + " คัน"
                + " | " + totalTrips + " รอบ"
                + " | รับ " + totalPickupStops + " ครั้ง"
                + " | ช่วย " + rescuePlanAssignedPeople + "/" + rescuePlanTotalPeople + " คน"
                + (rescuePlanUnservedPeople > 0
                    ? " | ยังรอ " + rescuePlanUnservedPeople + " คน"
                    : " | ครบทุกคำขอ");

        log("[🚨 Multi-Vehicle] " + routeExplanation);

        return !rescueAssignments.isEmpty();
    }


    private void resetRescuePlanStats() {
        rescuePlanTotalPeople = 0;
        rescuePlanAssignedPeople = 0;
        rescuePlanUnservedPeople = 0;
    }


    private boolean acceptRiskAwareRoute(List<Edge> route, RoutePreference preference, VehicleProfile vehicle) {
        if (route == null || route.isEmpty()) {
            optimalRoute.clear();
            mockRouteCost = 0.0;
            clearRouteMetrics();
            log("[⚠️] ไม่พบเส้นทางที่ยานพาหนะชนิดนี้สามารถผ่านได้");
            return false;
        }

        optimalRoute = new ArrayList<>(route);
        routeDistanceMeters = route.stream().mapToDouble(Edge::getDistance).sum();
        routeAverageRisk = route.stream().mapToDouble(Edge::getRiskLevel).average().orElse(0.0);
        routeMaxRisk = route.stream().mapToDouble(Edge::getRiskLevel).max().orElse(0.0);
        RiskAwareRouter router = new RiskAwareRouter(graph);
        mockRouteCost = route.stream().mapToDouble(e -> router.edgeCost(e, preference)).sum();

        routeExplanation = buildRouteExplanation(preference, vehicle);
        log("[🧭 Risk-Aware] " + routeExplanation);
        return true;
    }

    private String buildRouteExplanation(RoutePreference preference, VehicleProfile vehicle) {
        return "โหมด " + preference.name()
                + " | " + vehicle.getDisplayName()
                + " | ระยะทาง " + String.format("%.2f", routeDistanceMeters / 1000.0) + " กม."
                + " | ความเสี่ยงเฉลี่ย " + String.format("%.0f%%", routeAverageRisk * 100.0)
                + " | ความเสี่ยงสูงสุด " + String.format("%.0f%%", routeMaxRisk * 100.0);
    }

    private void clearRouteMetrics() {
        routeDistanceMeters = 0.0;
        routeAverageRisk = 0.0;
        routeMaxRisk = 0.0;
        routeExplanation = "";
    }

    // =========================================================
    // JSON STATE
    // =========================================================

    public Map<String, Object> toStateJson() {

        Map<String, Object> root =
                new LinkedHashMap<>();


        root.put(
                "loaded",
                isGraphLoaded()
        );


        List<Map<String, Object>> nodeList =
                new ArrayList<>();


        List<Map<String, Object>> edgeList =
                new ArrayList<>();


        List<Map<String, Object>> floodList =
                new ArrayList<>();


        List<String> routeEdgeIds =
                new ArrayList<>();


        List<String> rescueIds =
                new ArrayList<>();

        List<Map<String, Object>> rescueRequestList =
                new ArrayList<>();

        List<Map<String, Object>> rescueAssignmentList =
                new ArrayList<>();


        if (
                isGraphLoaded()
        ) {

            /*
             * NODES
             */
            for (
                    Node node :
                    getNodes()
            ) {

                Map<String, Object> n =
                        new LinkedHashMap<>();


                n.put(
                        "id",
                        node.getId()
                );


                n.put(
                        "lat",
                        node.getLatitude()
                );


                n.put(
                        "lon",
                        node.getLongitude()
                );


                nodeList.add(
                        n
                );
            }


            /*
             * EDGES
             */
            for (
                    Edge edge :
                    getEdges()
            ) {

                Map<String, Object> e =
                        new LinkedHashMap<>();


                e.put(
                        "id",
                        edge.getId()
                );


                e.put(
                        "sourceId",
                        edge.getSource()
                                .getId()
                );


                e.put(
                        "targetId",
                        edge.getTarget()
                                .getId()
                );


                e.put(
                        "distance",
                        edge.getDistance()
                );


                e.put(
                        "riskLevel",
                        edge.getRiskLevel()
                );


                e.put(
                        "travelTimeFactor",
                        edge.getTravelTimeFactor()
                );


                /*
                 * passable =
                 * roadPassable &&
                 * trafficAllowed
                 */
                e.put(
                        "passable",
                        edge.isTransportPassable()
                );

                e.put(
                        "transportPassable",
                        edge.isTransportPassable()
                );

                e.put(
                        "rescuePassable",
                        edge.isRescuePassable()
                );


                e.put(
                        "trafficAllowed",
                        safeTrafficAllowed(
                                edge
                        )
                );


                e.put(
                        "reverseEdge",
                        safeReverseEdge(
                                edge
                        )
                );


                e.put(
                        "roadPassable",
                        safeRoadPassable(
                                edge
                        )
                );


                edgeList.add(
                        e
                );
            }


            /*
             * FLOOD
             */
            for (
                    LocalFloodZone zone :
                    floodZones
            ) {

                Map<String, Object> z =
                        new LinkedHashMap<>();


                z.put(
                        "lat",
                        zone.lat
                );


                z.put(
                        "lon",
                        zone.lon
                );


                z.put(
                        "radiusKm",
                        zone.radiusKm
                );


                z.put(
                        "level",
                        zone.level
                );

                z.put("id", zone.id);
                z.put("overrideType", zone.overrideType);
                z.put("reporter", zone.reporter);
                z.put("note", zone.note);
                z.put("createdAtEpochMs", zone.createdAtEpochMs);

                floodList.add(
                        z
                );
            }


            /*
             * ROUTE
             */
            for (
                    Edge edge :
                    optimalRoute
            ) {

                routeEdgeIds.add(
                        edge.getId()
                );
            }


            /*
             * RESCUE TARGET
             */
            for (
                    Node node :
                    rescuePoints
            ) {

                rescueIds.add(
                        node.getId()
                );
            }

            for (RescueRequest request : rescueRequests) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", request.getId());
                r.put("nodeId", request.getNode().getId());
                r.put("people", request.getPeople());
                r.put("priority", request.getPriority());
                r.put("note", request.getNote());
                r.put("createdAtEpochMs", request.getCreatedAtEpochMs());
                rescueRequestList.add(r);
            }

            for (RescueAssignment assignment : rescueAssignments) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("vehicleId", assignment.getVehicleId());
                a.put("vehicleProfile", assignment.getVehicleProfile().name());
                a.put("vehicleDisplayName", assignment.getVehicleProfile().getDisplayName());
                a.put("assignedPeople", assignment.getAssignedPeople());
                a.put("tripCount", assignment.getTripCount());
                a.put("pickupStopCount", assignment.getPickupStopCount());
                a.put("distanceMeters", assignment.getDistanceMeters());
                a.put("averageRisk", assignment.getAverageRisk());
                a.put("maxRisk", assignment.getMaxRisk());

                List<String> assignmentRoute = new ArrayList<>();
                for (Edge edge : assignment.getRoute()) {
                    assignmentRoute.add(edge.getId());
                }
                a.put("routeEdgeIds", assignmentRoute);

                List<Map<String, Object>> legs = new ArrayList<>();
                for (RescueAssignment.Leg leg : assignment.getLegs()) {
                    Map<String, Object> legJson = new LinkedHashMap<>();
                    legJson.put("type", leg.getType());
                    legJson.put("tripNumber", leg.getTripNumber());
                    legJson.put("fromNodeId", leg.getFrom() != null ? leg.getFrom().getId() : null);
                    legJson.put("toNodeId", leg.getTo() != null ? leg.getTo().getId() : null);
                    legJson.put("assignedPeople", leg.getAssignedPeople());
                    legJson.put("slotRemainingAfter", leg.getSlotRemainingAfter());
                    legJson.put("distanceMeters", leg.getDistanceMeters());
                    legJson.put("averageRisk", leg.getAverageRisk());
                    legJson.put("maxRisk", leg.getMaxRisk());

                    if (leg.getRequest() != null) {
                        legJson.put("requestId", leg.getRequest().getId());
                        legJson.put("priority", leg.getRequest().getPriority());
                    } else {
                        legJson.put("requestId", null);
                        legJson.put("priority", null);
                    }

                    List<String> legRoute = new ArrayList<>();
                    for (Edge edge : leg.getRoute()) {
                        legRoute.add(edge.getId());
                    }
                    legJson.put("routeEdgeIds", legRoute);
                    legs.add(legJson);
                }
                a.put("legs", legs);

                rescueAssignmentList.add(a);
            }

        }


        root.put(
                "nodes",
                nodeList
        );


        root.put(
                "edges",
                edgeList
        );


        root.put(
                "floodZones",
                floodList
        );


        root.put(
                "routeEdgeIds",
                routeEdgeIds
        );


        root.put(
                "rescuePointIds",
                rescueIds
        );

        root.put("rescueRequests", rescueRequestList);
        root.put("rescueAssignments", rescueAssignmentList);
        root.put("rescuePlanTotalPeople", rescuePlanTotalPeople);
        root.put("rescuePlanAssignedPeople", rescuePlanAssignedPeople);
        root.put("rescuePlanUnservedPeople", rescuePlanUnservedPeople);


        root.put(
                "depotId",
                depot != null
                        ? depot.getId()
                        : null
        );


        root.put(
                "campId",
                refugeeCamp != null
                        ? refugeeCamp.getId()
                        : null
        );


        root.put(
                "mockRouteCost",
                mockRouteCost
        );

        root.put("routePreference", routePreference);
        root.put("vehicleProfile", vehicleProfile);
        root.put("routeDistanceMeters", routeDistanceMeters);
        root.put("routeAverageRisk", routeAverageRisk);
        root.put("routeMaxRisk", routeMaxRisk);
        root.put("routeExplanation", routeExplanation);

        root.put("aiRiskActive", aiRiskActive);
        root.put("aiRiskThresholdPercent", aiRiskThresholdPercent);
        root.put("aiRiskCellCount", aiRiskCellCount);
        root.put("aiAffectedEdgeCount", aiAffectedEdgeCount);
        root.put("aiMaxRoadRisk", aiMaxRoadRisk);


        /*
         * นับเฉพาะถนนที่ถูกปิดจากสภาพจริง
         *
         * ไม่เอา reverse edge
         * ที่ถูกปิดจากกฎจราจรมาปน
         */
        long blockedEdges =
                0;


        if (
                isGraphLoaded()
        ) {

            for (
                    Edge edge :
                    getEdges()
            ) {

                if (
                        !safeRoadPassable(
                                edge
                        )
                ) {

                    blockedEdges++;
                }

            }

        }


        root.put(
                "blockedEdges",
                blockedEdges
        );


        /*
         * LEGACY
         * เก็บไว้เพื่อ compatibility
         */
        root.put(
                "fleetSize",
                fleetSize
        );


        root.put(
                "vehicleCapacity",
                vehicleCapacity
        );


        root.put(
                "demandWater",
                demandWater
        );


        root.put(
                "demandMedical",
                demandMedical
        );


        root.put(
                "timeStart",
                timeStart
        );


        root.put(
                "timeEnd",
                timeEnd
        );


        return root;
    }


    // =========================================================
    // GETTERS
    // =========================================================

    public Node getDepot() {
        return depot;
    }


    public Node getCamp() {
        return refugeeCamp;
    }


    public List<Node> getRescuePoints() {
        return rescuePoints;
    }


    public List<Edge> getOptimalRoute() {
        return optimalRoute;
    }


    // =========================================================
    // INTERNAL HELPERS
    // =========================================================

    private Node findNodeById(
            String nodeId
    ) {

        if (
                nodeId == null
                        ||
                        graph == null
        ) {

            return null;
        }


        try {

            Method method =
                    graph.getClass()
                            .getMethod(
                                    "getNode",
                                    String.class
                            );


            Object result =
                    method.invoke(
                            graph,
                            nodeId
                    );


            if (
                    result instanceof Node
            ) {

                return
                        (Node)
                                result;
            }

        } catch (Exception ignored) {
        }


        for (
                Node node :
                getNodes()
        ) {

            if (
                    nodeId.equals(
                            node.getId()
                    )
            ) {

                return node;
            }

        }


        return null;
    }


    @SuppressWarnings(
            "unchecked"
    )
    private Collection<Node> getNodes() {

        if (graph == null) {

            return new ArrayList<>();
        }


        try {

            Method method =
                    graph.getClass()
                            .getMethod(
                                    "getAllNodes"
                            );


            Object result =
                    method.invoke(
                            graph
                    );


            if (
                    result instanceof Map
            ) {

                return
                        (
                                (Map<String, Node>)
                                        result
                        )
                                .values();
            }


            if (
                    result instanceof Collection
            ) {

                return
                        (Collection<Node>)
                                result;
            }

        } catch (Exception ignored) {
        }


        return new ArrayList<>();
    }


    @SuppressWarnings(
            "unchecked"
    )
    private Collection<Edge> getEdges() {

        if (graph == null) {

            return new ArrayList<>();
        }


        try {

            Method method =
                    graph.getClass()
                            .getMethod(
                                    "getAllEdges"
                            );


            Object result =
                    method.invoke(
                            graph
                    );


            if (
                    result instanceof Map
            ) {

                return
                        (
                                (Map<String, Edge>)
                                        result
                        )
                                .values();
            }


            if (
                    result instanceof Collection
            ) {

                return
                        (Collection<Edge>)
                                result;
            }

        } catch (Exception ignored) {
        }


        return new ArrayList<>();
    }


    private double calculateRouteCost(
            List<Edge> route,
            double alpha,
            double beta
    ) {
        double total = 0.0;

        for (Edge edge : route) {
            if (edge == null) {
                continue;
            }

            double distance = edge.getDistance();

            if (Double.isNaN(distance)
                    || Double.isInfinite(distance)
                    || distance < 0.0) {
                continue;
            }

            total += distance;
        }

        return total;
    }


    private boolean safeTrafficAllowed(
            Edge edge
    ) {

        try {

            Method method =
                    edge.getClass()
                            .getMethod(
                                    "isTrafficAllowed"
                            );


            Object result =
                    method.invoke(
                            edge
                    );


            if (
                    result instanceof Boolean
            ) {

                return
                        (Boolean)
                                result;
            }

        } catch (Exception ignored) {
        }


        return true;
    }


    private boolean safeReverseEdge(
            Edge edge
    ) {

        try {

            Method method =
                    edge.getClass()
                            .getMethod(
                                    "isReverseEdge"
                            );


            Object result =
                    method.invoke(
                            edge
                    );


            if (
                    result instanceof Boolean
            ) {

                return
                        (Boolean)
                                result;
            }

        } catch (Exception ignored) {
        }


        return false;
    }


    private boolean safeRoadPassable(
            Edge edge
    ) {

        try {

            Method method =
                    edge.getClass()
                            .getMethod(
                                    "isRoadPassable"
                            );


            Object result =
                    method.invoke(
                            edge
                    );


            if (
                    result instanceof Boolean
            ) {

                return
                        (Boolean)
                                result;
            }

        } catch (Exception ignored) {
        }


        return
                edge.isPassable();
    }


    private double distanceKm(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {

        double r =
                6371.0;


        double dLat =
                Math.toRadians(
                        lat2 - lat1
                );


        double dLon =
                Math.toRadians(
                        lon2 - lon1
                );


        double a =
                Math.sin(
                        dLat / 2
                )
                        *
                        Math.sin(
                                dLat / 2
                        )

                        +

                        Math.cos(
                                Math.toRadians(
                                        lat1
                                )
                        )

                                *

                                Math.cos(
                                        Math.toRadians(
                                                lat2
                                        )
                                )

                                *

                                Math.sin(
                                        dLon / 2
                                )

                                *

                                Math.sin(
                                        dLon / 2
                                );


        double c =
                2
                        *
                        Math.atan2(
                                Math.sqrt(a),
                                Math.sqrt(
                                        1 - a
                                )
                        );


        return
                r * c;
    }


    // =========================================================
    // LOCAL FLOOD ZONE
    // =========================================================

    private static class LocalFloodZone {

        String id;
        double lat;
        double lon;
        double radiusKm;
        String level;
        String overrideType;
        String reporter;
        String note;
        long createdAtEpochMs;

        LocalFloodZone(
                String id,
                double lat,
                double lon,
                double radiusKm,
                String level,
                String overrideType,
                String reporter,
                String note,
                long createdAtEpochMs
        ) {
            this.id = id;
            this.lat = lat;
            this.lon = lon;
            this.radiusKm = radiusKm;
            this.level = level == null ? "MEDIUM" : level.toUpperCase();
            this.overrideType = normalizeOverrideType(overrideType);
            this.reporter = reporter == null || reporter.isBlank()
                    ? "Field reporter"
                    : reporter.trim();
            this.note = note == null ? "" : note.trim();
            this.createdAtEpochMs = createdAtEpochMs;
        }

        private static String normalizeOverrideType(String value) {
            if (value == null) return "CONFIRMED_FLOOD";
            String v = value.trim().toUpperCase();
            return "ROAD_CLOSED".equals(v)
                    ? "ROAD_CLOSED"
                    : "CONFIRMED_FLOOD";
        }
    }


}

