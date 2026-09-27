# Loegis — AI Flood Risk → Road Routing Integration

## ปัญหาก่อนแก้

เดิม `/api/predictFloodRisk` อ่าน `AI_Model/flood_risk_grid.geojson` แล้วส่ง GeoJSON ไปให้ `app.js` วาด polygon สีบน Leaflet เท่านั้น

ดังนั้น flow เดิมคือ:

```
XGBoost result -> GeoJSON -> renderPrediction() -> สีบนแผนที่
                                      X
                                Road graph ไม่รู้ค่า AI
```

แม้ผู้ใช้จะเห็นพื้นที่สีแดง แต่ `Edge.riskLevel` ของถนนยังเป็น 0 และ `RiskAwareRouter` จึงไม่ได้ใช้ผล AI ในการหาเส้นทาง

---

## Flow ใหม่

```
AI_Model/flood_risk_grid.geojson
          |
          v
FloodRiskRoadMapper
          |
          | spatial matching
          v
Road Edge -> riskLevel + travelTimeFactor
          |
          v
SimulationState
          |
          v
RiskAwareRouter
          |
          v
เส้นทางที่หลีกพื้นที่เสี่ยงเมื่อมีทางเลือก
```

หน้าเว็บยังวาด AI polygon เหมือนเดิม แต่หลังจากโหลดผล AI แล้ว จะเรียก `/api/applyAiFloodRisk` เพิ่ม เพื่อให้ backend map grid ลงบนถนนจริง

---

# 1. ไฟล์ใหม่: `src/ai/FloodRiskRoadMapper.java`

คลาสนี้เป็นตัวเชื่อมระหว่าง GeoJSON ของ AI กับ Graph ของ Loegis

## 1.1 อ่านเฉพาะ grid ที่มีความเสี่ยงตั้งแต่ threshold

ค่า default คือ 25%

```java
if (riskPercent < thresholdPercent) {
    continue;
}
```

จึงไม่จำเป็นต้องเก็บ grid ความเสี่ยงต่ำทั้งหมด 248,363 ช่อง

ข้อมูลปัจจุบันมี grid ที่ >= 25% จำนวน 39,567 ช่อง

## 1.2 สร้าง Spatial Index

ถ้าเอาถนน 78,738 เส้นไปตรวจทีละเส้นกับ grid 39,567 ช่องโดยตรง จะเป็นการเปรียบเทียบหลายพันล้านครั้ง

จึงใช้ spatial hash แบ่งพื้นที่เป็น bucket ขนาดประมาณ 0.002 องศา

```java
Map<Long, List<RiskCell>> buckets
```

เวลาต้องการหาความเสี่ยงของพิกัดหนึ่งจุด จะตรวจเฉพาะ grid ใน bucket ใกล้พิกัดนั้น

## 1.3 Sample ถนน 5 ตำแหน่ง

ไม่ได้ตรวจเฉพาะ midpoint เพราะถนนบางเส้นอาจยาวและพาดผ่านหลาย grid

```java
private static final double[] EDGE_SAMPLES = {
    0.0, 0.25, 0.50, 0.75, 1.0
};
```

แล้วใช้ความเสี่ยงสูงสุดที่พบเป็น risk ของ Edge

```java
maxRisk = Math.max(maxRisk, index.riskAt(lat, lon));
```

---

# 2. แก้ `src/server/SimulationState.java`

เพิ่ม AI layer แยกจาก Manual Flood

```java
private final Map<String, Double> aiRiskByEdgeId = new HashMap<>();
```

เหตุผลที่แยกคือ AI prediction และน้ำท่วมที่ผู้ใช้/เจ้าหน้าที่ระบุเองไม่ใช่ข้อมูลชนิดเดียวกัน

- AI = predicted risk / susceptibility
- Manual Flood = simulation หรือ field report ที่ยืนยันสภาพถนน

## 2.1 `applyAiFloodRisk()`

```java
FloodRiskRoadMapper.Result result = mapper.mapRoadRisk(
    graph,
    FloodRiskConfig.getFloodRiskGeoJsonPath(),
    thresholdPercent
);
```

ผลลัพธ์เป็น Map:

```
Edge ID -> AI risk 0.0 - 1.0
```

จากไฟล์ปัจจุบัน ระบบ map risk ได้ 54,756 Edge จากทั้งหมด 78,738 Edge

## 2.2 `rebuildEnvironmentalState()`

ทุกครั้งที่สถานการณ์เปลี่ยน ระบบสร้างสถานะถนนใหม่ตามลำดับ:

```
Reset Road
   ↓
AI Risk Layer
   ↓
Manual Flood Layer
```

Manual Flood อยู่หลัง AI เพื่อทำหน้าที่เป็น override

เช่น AI บอกความเสี่ยง 40% แต่ภาคสนามยืนยันว่าถนนน้ำลึก ระบบจะใช้สถานะน้ำลึกและปิดถนนสำหรับรถขนส่ง

## 2.3 AI ไม่ถูกตีความเป็นระดับน้ำจริง

AI model ให้ flood probability/susceptibility จึงไม่ควรสรุปว่า 80% = น้ำลึก

AI layer จึงทำแค่:

```java
edge.setRiskLevel(normalizedRisk);
```

และเพิ่ม travel penalty:

```text
25-49%  -> x1.20
50-74%  -> x1.60
75-100% -> x2.20
```

แต่ไม่ set `transportPassable = false`

ถนนจะถูกปิดจริงจาก Manual Flood / field report เท่านั้น

---

# 3. แก้ `src/vehicle/VehicleProfile.java`

เดิม `riskLevel` ถูกใช้เป็นเหมือนระดับน้ำ ทำให้รถอาจถูกห้ามผ่านเพียงเพราะ AI ให้ probability สูง

แก้เป็น:

```java
return reverseAllowed
        ? edge.isRescuePassable()
        : edge.isRoadPassable();
```

ดังนั้น:

- AI risk = ใช้ตัดสินใจว่า "ควรหลีกเลี่ยงหรือไม่"
- confirmed/manual flood = ใช้ตัดสินว่า "ผ่านได้หรือไม่ได้"

---

# 4. แก้ `src/server/ApiServer.java`

เพิ่ม API 2 ตัว

## เปิด AI risk บน road graph

```text
POST /api/applyAiFloodRisk
```

body:

```json
{
  "thresholdPercent": 25
}
```

## ปิด AI risk

```text
POST /api/clearAiFloodRisk
```

ปิดเฉพาะ AI layer และยังคง Manual Flood Zone ไว้

---

# 5. แก้ `webapp/app.js`

เดิม:

```javascript
renderPrediction(geojson);
```

จบตรงนี้

ตอนนี้หลัง render จะเรียก:

```javascript
const state = await postJson(
    API.APPLY_AI_RISK,
    { thresholdPercent: 25 }
);

appState = state;
renderState(state);
```

ทำให้ `state.edges[].riskLevel` เปลี่ยนจริง และถนนบนแผนที่ถูกวาดตาม risk ใหม่

เมื่อกดหาเส้นทาง `RiskAwareRouter` ก็ใช้ค่าเดียวกันทันที

---

# 6. RiskAwareRouter ใช้ AI ยังไง

ใน `src/routing/RiskAwareRouter.java`

```java
double distanceTimeCost =
        edge.getDistance()
        * Math.max(1.0, edge.getTravelTimeFactor());

double riskPenalty =
        edge.getDistance()
        * edge.getRiskLevel()
        * 4.0;
```

แล้วรวมตาม RoutePreference

```java
return preference.distanceWeight() * distanceTimeCost
        + preference.riskWeight() * riskPenalty;
```

ดังนั้นเส้นทางไม่ได้เลือกจากระยะทางอย่างเดียวอีกต่อไป

---

# 7. การทดสอบ

Graph หาดใหญ่:

```text
Nodes: 17,164
Edges: 78,738
AI grid >= 25%: 39,567
Edges ที่ได้รับ AI risk: 54,756
```

ตัวอย่างทดสอบ SAFEST:

ก่อนเปิด AI:

```text
Distance = 7.20 km
Edges = 48
Average risk = 0%
```

หลังเปิด AI:

```text
Distance = 9.45 km
Edges = 63
Average risk ≈ 41.3%
```

ระบบยอมเดินทางไกลขึ้นเพื่อหลีกเลี่ยงพื้นที่ที่มี penalty สูง

เมื่อปิด AI:

```text
Distance = 7.20 km
Edges = 48
```

กลับเป็นเส้นทางเดิม แสดงว่า AI layer มีผลต่อ routing จริง

ทดสอบ layer ซ้อนกันแล้ว:

- เปิด AI
- เพิ่ม Manual Flood แบบ DEEP
- ปิด AI

Manual Flood ยังอยู่ และถนนที่ถูกยืนยันเป็น DEEP ยังคงถูกปิดสำหรับรถขนส่ง

---

# ข้อจำกัดที่ยังมี

ผลใน `flood_risk_grid.geojson` ปัจจุบันยังเป็นผล prediction ที่สร้างไว้ล่วงหน้า ไม่ใช่ข้อมูล real-time

ดังนั้นเวอร์ชันนี้แก้ปัญหา:

> "AI กับ routing แยกกัน"

ให้กลายเป็น:

> "AI prediction ถูกนำไปใช้กับ road graph และ routing จริง"

แต่ขั้นต่อไปสำหรับการใช้งานจริงคือเปลี่ยน input ของ FloodPredictionService จาก static GeoJSON ไปเป็น prediction ที่สร้างจากข้อมูลล่าสุด เช่น rainfall / satellite / field data
