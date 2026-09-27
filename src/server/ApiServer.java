package src.server;

import src.ai.FloodPredictionResult;
import src.ai.FloodPredictionService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ApiServer
 * ---------
 * รับคำสั่งจากหน้าเว็บ แล้วส่งต่อให้ SimulationState ทำงาน
 *
 * โหมดหลัก:
 *   - /api/runRoute          = หาเส้นทางเลี่ยงไปค่ายผู้ประสบภัย
 *   - /api/runRescueMission  = วางแผนกู้ภัยไปช่วยผู้ประสบภัย
 *
 * AI:
 *   - /api/predictFloodRisk  = ส่งผลพื้นที่เสี่ยงน้ำท่วมจาก GeoJSON
 */
public class ApiServer {

    private SimulationState state;

    private final Deque<String> logBuffer =
            new ArrayDeque<>();

    private static final int MAX_LOG_LINES = 500;


    private final FloodPredictionService floodPredictionService =
            new FloodPredictionService();


    public ApiServer() {

        this.state =
                new SimulationState(
                        this::pushLog
                );
    }


    public ApiServer(
            SimulationState state
    ) {

        this.state = state;
    }


    private synchronized void pushLog(
            String line
    ) {

        logBuffer.addLast(line);

        while (
                logBuffer.size()
                        >
                        MAX_LOG_LINES
        ) {

            logBuffer.removeFirst();
        }
    }


    public void start(
            int port
    ) throws IOException {

        start(
                port,
                "webapp"
        );
    }


    public void start(
            int port,
            String webRoot
    ) throws IOException {

        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress(
                                port
                        ),
                        0
                );


        ExecutorService executor =
                Executors.newFixedThreadPool(
                        4
                );


        server.setExecutor(
                executor
        );


        // =========================================================
        // API ROUTES
        // =========================================================

        server.createContext(
                "/api/load",
                this::handleLoad
        );


        server.createContext(
                "/api/setTrafficMode",
                this::handleSetTrafficMode
        );


        server.createContext(
                "/api/setDepot",
                this::handleSetDepot
        );


        server.createContext(
                "/api/setCamp",
                this::handleSetCamp
        );


        server.createContext(
                "/api/addRescuePoint",
                this::handleAddRescuePoint
        );

        server.createContext(
                "/api/clearRescueRequests",
                this::handleClearRescueRequests
        );


        // =========================================================
        // FLOOD
        // =========================================================

        server.createContext(
                "/api/simulateFlood",
                this::handleSimulateFlood
        );


        server.createContext(
                "/api/addFloodZone",
                this::handleAddFloodZone
        );


        server.createContext(
                "/api/addFloodZonesBatch",
                this::handleAddFloodZonesBatch
        );


        server.createContext(
                "/api/clearFloodZones",
                this::handleClearFloodZones
        );


        // =========================================================
        // RECURRENT FLOOD AI PREDICTION
        // =========================================================

        server.createContext(
                "/api/predictFloodRisk",
                this::handlePredictFloodRisk
        );

        server.createContext(
                "/api/applyAiFloodRisk",
                this::handleApplyAiFloodRisk
        );

        server.createContext(
                "/api/clearAiFloodRisk",
                this::handleClearAiFloodRisk
        );


        // =========================================================
        // FLEET
        // =========================================================

        server.createContext(
                "/api/setupFleet",
                this::handleSetupFleet
        );


        // =========================================================
        // ROUTE
        // =========================================================

        server.createContext(
                "/api/runRoute",
                this::handleRunRoute
        );


        // =========================================================
        // RESCUE
        // =========================================================

        server.createContext(
                "/api/runRescueMission",
                this::handleRunRescueMission
        );

        server.createContext(
                "/api/runMultiVehicleRescue",
                this::handleRunMultiVehicleRescue
        );


        // =========================================================
        // STATE
        // =========================================================

        server.createContext(
                "/api/state",
                this::handleGetState
        );


        server.createContext(
                "/api/logs",
                this::handleGetLogs
        );


        server.createContext(
                "/api/reset",
                this::handleReset
        );


        // =========================================================
        // STATIC FILES
        // =========================================================

        StaticFileHandler staticHandler =
                new StaticFileHandler(
                        webRoot
                );


        server.createContext(
                "/",
                staticHandler
        );


        server.start();


        if (
                !staticHandler.rootExists()
        ) {

            System.out.println(
                    "[⚠️ Warning] ไม่พบโฟลเดอร์ webapp/ ที่ path: "
                            + staticHandler.rootPath()
            );
        }


        System.out.println(
                "[🌐 Server] เปิดให้บริการที่ http://localhost:"
                        + port
                        + "  (กด Ctrl+C เพื่อปิด)"
        );
    }


    // ==================================================================
    // LOAD MAP
    // ==================================================================

    private void handleLoad(
            HttpExchange ex
    ) throws IOException {

        if (
                !requireMethod(
                        ex,
                        "POST"
                )
        ) {

            return;
        }


        Map<String, Object> body =
                readJsonBody(
                        ex
                );


        String path =
                String.valueOf(
                        body.getOrDefault(
                                "mapFilePath",
                                "hatyai_map.graphml"
                        )
                );


        boolean respectOneWay =
                Boolean.parseBoolean(
                        String.valueOf(
                                body.getOrDefault(
                                        "respectOneWay",
                                        "true"
                                )
                        )
                );


        boolean ok =
                state.loadMap(
                        path,
                        respectOneWay
                );


        respondState(
                ex,
                ok
        );
    }


    // ==================================================================
    // TRAFFIC MODE
    // ==================================================================

    private void handleSetTrafficMode(
            HttpExchange ex
    ) throws IOException {

        if (
                !requireMethod(
                        ex,
                        "POST"
                )
        ) {

            return;
        }


        Map<String, Object> body =
                readJsonBody(
                        ex
                );


        boolean respectOneWay =
                Boolean.parseBoolean(
                        String.valueOf(
                                body.getOrDefault(
                                        "respectOneWay",
                                        "true"
                                )
                        )
                );


        boolean ok =
                state.setTrafficMode(
                        respectOneWay
                );


        respondState(
                ex,
                ok
        );
    }


    // ==================================================================
    // SET DEPOT
    // ==================================================================

    private void handleSetDepot(
            HttpExchange ex
    ) throws IOException {

        if (
                !requireMethod(
                        ex,
                        "POST"
                )
        ) {

            return;
        }


        Map<String, Object> body =
                readJsonBody(
                        ex
                );


        String nodeId =
                String.valueOf(
                        body.get(
                                "nodeId"
                        )
                );


        boolean ok =
                state.setDepotById(
                        nodeId
                );


        respondState(
                ex,
                ok
        );
    }


    // ==================================================================
    // SET CAMP / SHELTER
    // ==================================================================

    private void handleSetCamp(
            HttpExchange ex
    ) throws IOException {

        if (
                !requireMethod(
                        ex,
                        "POST"
                )
        ) {

            return;
        }


        Map<String, Object> body =
                readJsonBody(
                        ex
                );


        String nodeId =
                String.valueOf(
                        body.get(
                                "nodeId"
                        )
                );


        if (
                body.containsKey(
                        "water"
                )
        ) {

            double water =
                    toDouble(
                            body.get(
                                    "water"
                            ),
                            500.0
                    );


            double medical =
                    toDouble(
                            body.get(
                                    "medical"
                            ),
                            50.0
                    );


            double startMin =
                    toDouble(
                            body.get(
                                    "startMin"
                            ),
                            60.0
                    );


            double endMin =
                    toDouble(
                            body.get(
                                    "endMin"
                            ),
                            180.0
                    );


            state.setDemandParams(
                    water,
                    medical,
                    startMin,
                    endMin
            );
        }


        boolean ok =
                state.setCampById(
                        nodeId
                );


        respondState(
                ex,
                ok
        );
    }


    // ==================================================================
    // ADD RESCUE POINT
    // ==================================================================

    private void handleAddRescuePoint(
            HttpExchange ex
    ) throws IOException {

        if (!requireMethod(ex, "POST")) {
            return;
        }

        Map<String, Object> body = readJsonBody(ex);

        String nodeId = String.valueOf(body.get("nodeId"));
        int people = (int) toDouble(body.get("people"), 1.0);
        String priority = String.valueOf(body.getOrDefault("priority", "NORMAL"));
        String note = String.valueOf(body.getOrDefault("note", ""));
        boolean replaceExisting = Boolean.parseBoolean(
                String.valueOf(body.getOrDefault("replaceExisting", "true"))
        );

        boolean ok = state.addRescueRequestById(
                nodeId,
                people,
                priority,
                note,
                replaceExisting
        );

        respondState(ex, ok);
    }


    private void handleClearRescueRequests(
            HttpExchange ex
    ) throws IOException {
        if (!requireMethod(ex, "POST")) {
            return;
        }
        respondState(ex, state.clearRescueRequests());
    }


    // ==================================================================
    // FLOOD SIMULATION
    // ==================================================================

    private void handleSimulateFlood(
            HttpExchange ex
    ) throws IOException {

        if (
                !requireMethod(
                        ex,
                        "POST"
                )
        ) {

            return;
        }


        Map<String, Object> body =
                readJsonBody(
                        ex
                );


        double lat =
                toDouble(
                        body.get(
                                "lat"
                        ),
                        7.0100
                );


        double lon =
                toDouble(
                        body.get(
                                "lon"
                        ),
                        100.4700
                );


        double radiusKm =
                toDouble(
                        body.get(
                                "radiusKm"
                        ),
                        2.5
                );


        String level =
                String.valueOf(
                        body.getOrDefault(
                                "level",
                                "MEDIUM"
                        )
                );


        boolean ok =
                state.addFloodZone(
                        lat,
                        lon,
                        radiusKm,
                        level
                );


        respondState(
                ex,
                ok
        );
    }


    private void handleAddFloodZone(
            HttpExchange ex
    ) throws IOException {

        if (
                !requireMethod(
                        ex,
                        "POST"
                )
        ) {

            return;
        }


        Map<String, Object> body =
                readJsonBody(
                        ex
                );


        double lat =
                toDouble(
                        body.get(
                                "lat"
                        ),
                        7.0100
                );


        double lon =
                toDouble(
                        body.get(
                                "lon"
                        ),
                        100.4700
                );


        double radiusKm =
                toDouble(
                        body.get(
                                "radiusKm"
                        ),
                        1.0
                );


        String level =
                String.valueOf(
                        body.getOrDefault(
                                "level",
                                "MEDIUM"
                        )
                );

        String overrideType =
                String.valueOf(
                        body.getOrDefault(
                                "overrideType",
                                "CONFIRMED_FLOOD"
                        )
                );

        String reporter =
                String.valueOf(
                        body.getOrDefault(
                                "reporter",
                                "Field reporter"
                        )
                );

        String note =
                String.valueOf(
                        body.getOrDefault(
                                "note",
                                ""
                        )
                );


        boolean ok =
                state.addFieldReport(
                        lat,
                        lon,
                        radiusKm,
                        level,
                        overrideType,
                        reporter,
                        note
                );


        respondState(
                ex,
                ok
        );
    }


    @SuppressWarnings("unchecked")
    private void handleAddFloodZonesBatch(
            HttpExchange ex
    ) throws IOException {

        if (!requireMethod(ex, "POST")) {
            return;
        }

        Map<String, Object> body = readJsonBody(ex);
        Object zonesObj = body.get("zones");

        if (!(zonesObj instanceof List)) {
            sendJson(
                    ex,
                    400,
                    Map.of(
                            "ok", false,
                            "message", "zones must be an array"
                    )
            );
            return;
        }

        List<?> rawZones = (List<?>) zonesObj;
        List<Map<String, Object>> zones = new java.util.ArrayList<>();

        for (Object item : rawZones) {
            if (item instanceof Map) {
                zones.add((Map<String, Object>) item);
            }
        }

        boolean ok = state.addFloodZonesBatch(zones);

        pushLog(
                ok
                        ? "[🤖🌊 AI] เพิ่มพื้นที่น้ำท่วมจากผลทำนาย " + zones.size() + " พื้นที่"
                        : "[❌ AI] เพิ่มพื้นที่น้ำท่วมจากผลทำนายไม่สำเร็จ"
        );

        respondState(ex, ok);
    }


    private void handleClearFloodZones(
            HttpExchange ex
    ) throws IOException {

        if (
                !requireMethod(
                        ex,
                        "POST"
                )
        ) {

            return;
        }


        boolean ok =
                state.clearFloodZones();


        respondState(
                ex,
                ok
        );
    }


    // ==================================================================
    // RECURRENT FLOOD AI PREDICTION
    // ==================================================================
    //
    // GET /api/predictFloodRisk
    //
    // อ่าน:
    //
    // D:\Loegis-main\AI_Model\flood_risk_grid.geojson
    //
    // แล้วส่งผล Recurrent Flood probability GeoJSON กลับไปให้ app.js
    //
    // ไม่ต้องให้ Browser อ่าน D:\ โดยตรง
    // ==================================================================

    private void handlePredictFloodRisk(
            HttpExchange ex
    ) throws IOException {

        if (!requireMethod(ex, "GET")) {
            return;
        }

        FloodPredictionResult result =
                floodPredictionService.loadPrediction();

        if (!result.isSuccess()) {
            pushLog("[❌ AI] " + result.getMessage());
            sendRawJson(
                    ex,
                    result.getStatusCode(),
                    result.toErrorJson()
            );
            return;
        }

        byte[] bytes =
                result.getGeoJson().getBytes(
                        StandardCharsets.UTF_8
                );

        ex.getResponseHeaders().set(
                "Content-Type",
                "application/geo+json; charset=UTF-8"
        );
        ex.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                "*"
        );
        ex.getResponseHeaders().set(
                "Cache-Control",
                "no-cache, no-store, must-revalidate"
        );
        ex.getResponseHeaders().set(
                "Pragma",
                "no-cache"
        );

        ex.sendResponseHeaders(200, bytes.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
            os.flush();
        }

        pushLog("[🤖 AI] โหลดผล Recurrent Flood prediction สำเร็จ");
    }


    /**
     * POST /api/applyAiFloodRisk
     * body: { "thresholdPercent": 25 }
     *
     * ให้ server map GeoJSON -> Edge โดยตรง แล้วอัปเดต state สำหรับ routing
     */
    private void handleApplyAiFloodRisk(
            HttpExchange ex
    ) throws IOException {

        if (!requireMethod(ex, "POST")) {
            return;
        }

        Map<String, Object> body = readJsonBody(ex);

        double thresholdPercent = toDouble(
                body.get("thresholdPercent"),
                25.0
        );

        boolean ok = state.applyAiFloodRisk(thresholdPercent);
        respondState(ex, ok);
    }


    /**
     * POST /api/clearAiFloodRisk
     * ปิดเฉพาะ AI layer แต่ไม่ลบ Manual Flood Zone
     */
    private void handleClearAiFloodRisk(
            HttpExchange ex
    ) throws IOException {

        if (!requireMethod(ex, "POST")) {
            return;
        }

        boolean ok = state.clearAiFloodRisk();
        respondState(ex, ok);
    }


    // ==================================================================
    // FLEET
    // ==================================================================

    private void handleSetupFleet(
            HttpExchange ex
    ) throws IOException {

        if (
                !requireMethod(
                        ex,
                        "POST"
                )
        ) {

            return;
        }


        Map<String, Object> body =
                readJsonBody(
                        ex
                );


        int fleetSize =
                (int)
                        toDouble(
                                body.get(
                                        "fleetSize"
                                ),
                                2.0
                        );


        double capacity =
                toDouble(
                        body.get(
                                "capacity"
                        ),
                        1000.0
                );


        boolean ok =
                state.setupFleet(
                        fleetSize,
                        capacity
                );


        respondState(
                ex,
                ok
        );
    }


    // ==================================================================
    // ROUTE
    // ==================================================================

    private void handleRunRoute(
            HttpExchange ex
    ) throws IOException {

        if (
                !requireMethod(
                        ex,
                        "POST"
                )
        ) {

            return;
        }


        Map<String, Object> body = readJsonBody(ex);
        String preference = String.valueOf(body.getOrDefault("preference", "BALANCED"));
        String vehicle = String.valueOf(body.getOrDefault("vehicle", "DELIVERY_TRUCK"));

        boolean ok = state.runRouteToCamp(preference, vehicle);

        respondState(ex, ok);
    }


    // ==================================================================
    // RESCUE
    // ==================================================================

    private void handleRunRescueMission(
            HttpExchange ex
    ) throws IOException {

        if (
                !requireMethod(
                        ex,
                        "POST"
                )
        ) {

            return;
        }


        Map<String, Object> body = readJsonBody(ex);
        String preference = String.valueOf(body.getOrDefault("preference", "BALANCED"));
        String vehicle = String.valueOf(body.getOrDefault("vehicle", "RESCUE_TRUCK"));

        boolean ok = state.runRescueMission(preference, vehicle);

        respondState(ex, ok);
    }


    private void handleRunMultiVehicleRescue(
            HttpExchange ex
    ) throws IOException {
        if (!requireMethod(ex, "POST")) {
            return;
        }

        Map<String, Object> body = readJsonBody(ex);
        String preference = String.valueOf(body.getOrDefault("preference", "SAFEST"));
        String vehicle = String.valueOf(body.getOrDefault("vehicle", "RESCUE_TRUCK"));
        int fleetSize = (int) toDouble(body.get("fleetSize"), 3.0);
        int capacity = (int) toDouble(body.get("vehicleCapacity"), 4.0);

        boolean ok = state.runMultiVehicleRescue(
                preference,
                vehicle,
                fleetSize,
                capacity
        );

        respondState(ex, ok);
    }


    // ==================================================================
    // GET STATE
    // ==================================================================

    private void handleGetState(
            HttpExchange ex
    ) throws IOException {

        if (
                !requireMethod(
                        ex,
                        "GET"
                )
        ) {

            return;
        }


        respondState(
                ex,
                true
        );
    }


    // ==================================================================
    // GET LOGS
    // ==================================================================

    private void handleGetLogs(
            HttpExchange ex
    ) throws IOException {

        if (
                !requireMethod(
                        ex,
                        "GET"
                )
        ) {

            return;
        }


        Map<String, Object> result =
                new LinkedHashMap<>();


        synchronized (this) {

            result.put(
                    "lines",
                    new java.util.ArrayList<>(
                            logBuffer
                    )
            );
        }


        sendJson(
                ex,
                200,
                result
        );
    }


    // ==================================================================
    // RESET
    // ==================================================================

    private void handleReset(
            HttpExchange ex
    ) throws IOException {

        if (
                !requireMethod(
                        ex,
                        "POST"
                )
        ) {

            return;
        }


        synchronized (this) {

            logBuffer.clear();
        }


        this.state =
                new SimulationState(
                        this::pushLog
                );


        pushLog(
                "[🔄] รีเซ็ตการจำลองเรียบร้อย พร้อมเริ่มใหม่"
        );


        respondState(
                ex,
                true
        );
    }


    // ==================================================================
    // HTTP METHOD
    // ==================================================================

    private boolean requireMethod(
            HttpExchange ex,
            String method
    ) throws IOException {

        ex.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                "*"
        );


        ex.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Content-Type"
        );


        ex.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, POST, OPTIONS"
        );


        if (
                ex.getRequestMethod()
                        .equalsIgnoreCase(
                                "OPTIONS"
                        )
        ) {

            ex.sendResponseHeaders(
                    204,
                    -1
            );


            return false;
        }


        if (
                !ex.getRequestMethod()
                        .equalsIgnoreCase(
                                method
                        )
        ) {

            sendJson(
                    ex,
                    405,
                    Map.of(
                            "error",
                            "Method not allowed"
                    )
            );


            return false;
        }


        return true;
    }


    // ==================================================================
    // READ JSON BODY
    // ==================================================================

    private Map<String, Object> readJsonBody(
            HttpExchange ex
    ) throws IOException {

        try (
                InputStream is =
                        ex.getRequestBody()
        ) {

            byte[] bytes =
                    is.readAllBytes();


            String body =
                    new String(
                            bytes,
                            StandardCharsets.UTF_8
                    )
                            .trim();


            if (
                    body.isEmpty()
            ) {

                return new LinkedHashMap<>();
            }


            return Json.parseObject(
                    body
            );

        }
        catch (
                Exception e
        ) {

            return new LinkedHashMap<>();
        }
    }


    // ==================================================================
    // NUMBER
    // ==================================================================

    private double toDouble(
            Object value,
            double fallback
    ) {

        if (
                value == null
        ) {

            return fallback;
        }


        if (
                value instanceof Number
        ) {

            return (
                    (Number)
                            value
            )
                    .doubleValue();
        }


        try {

            return Double.parseDouble(
                    value.toString()
            );

        }
        catch (
                NumberFormatException e
        ) {

            return fallback;
        }
    }


    // ==================================================================
    // STATE RESPONSE
    // ==================================================================

    private void respondState(
            HttpExchange ex,
            boolean ok
    ) throws IOException {

        Map<String, Object> result =
                state.toStateJson();


        result.put(
                "ok",
                ok
        );


        sendJson(
                ex,
                200,
                result
        );
    }


    // ==================================================================
    // SEND JSON OBJECT
    // ==================================================================

    private void sendJson(
            HttpExchange ex,
            int statusCode,
            Map<String, Object> payload
    ) throws IOException {

        String json =
                Json.stringify(
                        payload
                );


        sendRawJson(
                ex,
                statusCode,
                json
        );
    }


    // ==================================================================
    // SEND RAW JSON
    // ==================================================================

    private void sendRawJson(
            HttpExchange ex,
            int statusCode,
            String json
    ) throws IOException {

        byte[] bytes =
                json.getBytes(
                        StandardCharsets.UTF_8
                );


        ex.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8"
        );


        ex.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                "*"
        );


        ex.sendResponseHeaders(
                statusCode,
                bytes.length
        );


        try (
                OutputStream os =
                        ex.getResponseBody()
        ) {

            os.write(
                    bytes
            );
        }
    }


    // ==================================================================
    // ESCAPE JSON STRING
    // ==================================================================

    private String escapeJson(
            String value
    ) {

        if (
                value == null
        ) {

            return "";
        }


        return value
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                )
                .replace(
                        "\r",
                        "\\r"
                )
                .replace(
                        "\n",
                        "\\n"
                );
    }


    // ==================================================================
    // STATIC FILE HANDLER
    // ==================================================================

    private static class StaticFileHandler
            implements HttpHandler {

        private final Path root;


        StaticFileHandler(
                String webRoot
        ) {

            this.root =
                    Path.of(
                                    webRoot
                            )
                            .toAbsolutePath()
                            .normalize();
        }


        boolean rootExists() {

            return Files.isDirectory(
                    root
            );
        }


        String rootPath() {

            return root.toString();
        }


        @Override
        public void handle(
                HttpExchange ex
        ) throws IOException {

            String requestPath =
                    URLDecoder.decode(
                            ex.getRequestURI()
                                    .getPath(),
                            StandardCharsets.UTF_8
                    );


            if (
                    requestPath.equals("/")
                            ||
                            requestPath.isEmpty()
            ) {

                requestPath =
                        "/index.html";
            }


            Path filePath =
                    root.resolve(
                                    requestPath.substring(1)
                            )
                            .normalize();


            if (
                    !filePath.startsWith(
                            root
                    )
                            ||
                            !Files.exists(
                                    filePath
                            )
                            ||
                            Files.isDirectory(
                                    filePath
                            )
            ) {

                byte[] notFound =
                        "404 Not Found"
                                .getBytes(
                                        StandardCharsets.UTF_8
                                );


                ex.sendResponseHeaders(
                        404,
                        notFound.length
                );


                try (
                        OutputStream os =
                                ex.getResponseBody()
                ) {

                    os.write(
                            notFound
                    );
                }


                return;
            }


            String contentType =
                    guessContentType(
                            filePath.toString()
                    );


            byte[] content =
                    Files.readAllBytes(
                            filePath
                    );


            ex.getResponseHeaders().set(
                    "Content-Type",
                    contentType
            );


            ex.sendResponseHeaders(
                    200,
                    content.length
            );


            try (
                    OutputStream os =
                            ex.getResponseBody()
            ) {

                os.write(
                        content
                );
            }
        }


        private String guessContentType(
                String filename
        ) {

            if (
                    filename.endsWith(
                            ".html"
                    )
            ) {

                return "text/html; charset=UTF-8";
            }


            if (
                    filename.endsWith(
                            ".css"
                    )
            ) {

                return "text/css; charset=UTF-8";
            }


            if (
                    filename.endsWith(
                            ".js"
                    )
            ) {

                return "application/javascript; charset=UTF-8";
            }


            if (
                    filename.endsWith(
                            ".json"
                    )
            ) {

                return "application/json; charset=UTF-8";
            }


            if (
                    filename.endsWith(
                            ".geojson"
                    )
            ) {

                return "application/geo+json; charset=UTF-8";
            }


            if (
                    filename.endsWith(
                            ".graphml"
                    )
                            ||
                            filename.endsWith(
                                    ".xml"
                            )
            ) {

                return "application/xml; charset=UTF-8";
            }


            if (
                    filename.endsWith(
                            ".svg"
                    )
            ) {

                return "image/svg+xml";
            }


            if (
                    filename.endsWith(
                            ".png"
                    )
            ) {

                return "image/png";
            }


            if (
                    filename.endsWith(
                            ".jpg"
                    )
                            ||
                            filename.endsWith(
                                    ".jpeg"
                            )
            ) {

                return "image/jpeg";
            }


            return "application/octet-stream";
        }
    }
}