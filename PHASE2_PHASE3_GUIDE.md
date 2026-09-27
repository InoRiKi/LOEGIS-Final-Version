# Loegis — Phase 2 + Phase 3 Integration Guide

เวอร์ชันนี้ต่อยอดจาก AI → Road Risk → Risk-Aware Routing และเพิ่ม 2 ระบบ:

1. **Phase 2: Field Report / Manual Override**
2. **Phase 3: Multi-Vehicle Rescue Assignment**

---

## ภาพรวม Flow ใหม่

```text
XGBoost Flood Risk
        ↓
AI Risk on Road (baseline)
        ↓
Field Report / Manual Override
        ↓
Current Road State
        ↓
RiskAwareRouter
        ↓
MultiVehicleRescuePlanner
        ↓
R-01 → Rescue Request A
R-02 → Rescue Request A
R-03 → Rescue Request B
...
```

หลักสำคัญคือ **AI กับ Field Report ไม่ใช่ข้อมูลเดียวกัน**

- AI = ความเสี่ยงพื้นฐาน / prediction
- Field Report = ข้อมูลจริงที่คนในพื้นที่ยืนยัน
- Field Report ถูกใช้ทีหลัง จึง override AI ได้

---

# Phase 2 — Field Report / Manual Override

## 1. หน้าเว็บ

ส่วนเดิม “การจำลองน้ำท่วม” ถูกปรับเป็น **Field Report / Manual Override**

ผู้ใช้เลือกตำแหน่งบนแผนที่ แล้วกรอก:

- `overrideType`
  - `CONFIRMED_FLOOD` = ยืนยันว่ามีน้ำท่วมจริง
  - `ROAD_CLOSED` = ยืนยันว่าถนนใช้ไม่ได้ทั้งหมด
- `level`
  - `SHALLOW`
  - `MEDIUM`
  - `DEEP`
- `reporter` = ผู้รายงาน/แหล่งข้อมูล
- `note` = หมายเหตุ
- `radiusKm` = รัศมีที่รายงานครอบคลุม

API เดิม `/api/addFloodZone` ยังใช้ได้ แต่รับ metadata เพิ่มแล้ว

ตัวอย่าง request:

```json
{
  "lat": 7.0164,
  "lon": 100.4825,
  "radiusKm": 0.25,
  "level": "DEEP",
  "overrideType": "ROAD_CLOSED",
  "reporter": "Rescue Team A",
  "note": "ถนนถูกตัดขาด"
}
```

## 2. SimulationState.addFieldReport()

ไฟล์: `src/server/SimulationState.java`

```java
public boolean addFieldReport(
    double lat,
    double lon,
    double radiusKm,
    String level,
    String overrideType,
    String reporter,
    String note
)
```

method นี้สร้าง Field Report ใหม่ เช่น `FR-001` แล้วเรียก

```java
rebuildEnvironmentalState();
```

เพื่อคำนวณสภาพถนนใหม่ทั้งหมด

## 3. Layer order

ใน `rebuildEnvironmentalState()` ลำดับคือ:

```text
Reset Road State
      ↓
Apply AI Risk
      ↓
Apply Field Reports
```

ดังนั้นข้อมูลภาคสนามมีสิทธิ์ override prediction

### CONFIRMED_FLOOD

ใช้ `applyFloodLevelToEdge()`

- SHALLOW → risk เพิ่ม แต่รถขนส่งยังผ่านได้
- MEDIUM → รถขนส่งผ่านไม่ได้
- DEEP → รถขนส่งผ่านไม่ได้และ cost สูงมาก

### ROAD_CLOSED

ใช้:

```java
private void applyRoadClosedOverride(Edge edge) {
    edge.setRiskLevel(1.0);
    edge.setTravelTimeFactor(10.0);
    edge.setTransportPassable(false);
    edge.setRescuePassable(false);
}
```

ดังนั้นแม้เป็นรถกู้ภัยก็ใช้ถนนนั้นไม่ได้ และ Router ต้องหาเส้นทางใหม่

## 4. Invalidate แผนเดิมทันที

เมื่อมี Field Report ใหม่ ระบบเรียก:

```java
rescueAssignments.clear();
resetRescuePlanStats();
optimalRoute.clear();
```

เหตุผลคือ assignment เดิมอาจไม่ปลอดภัยแล้ว จึงไม่ควรแสดงเป็นแผนปัจจุบัน

---

# Phase 3 — Multi-Vehicle Rescue Assignment

ไฟล์ใหม่:

- `src/rescue/RescueRequest.java`
- `src/rescue/RescueAssignment.java`
- `src/rescue/MultiVehicleRescuePlanner.java`

## 1. RescueRequest

หนึ่งคำขอกู้ภัยเก็บ:

```text
request id
node
people
priority
note
time
```

Priority มี 4 ระดับ:

```text
CRITICAL
HIGH
NORMAL
LOW
```

หน้าเว็บใน Rescue Mode สามารถกดเลือก “จุดหมาย” หลายครั้งได้
แต่ละครั้งจะ **เพิ่ม request ใหม่** แทนการล้างจุดเดิม

ตัวอย่าง:

```text
REQ-001 | Node 5375 | 7 people | CRITICAL
REQ-002 | Node 9376 | 3 people | HIGH
REQ-003 | Node 4009 | 5 people | NORMAL
```

## 2. Vehicle fleet

หน้าเว็บกำหนด:

- จำนวนรถ `fleetSize`
- ความจุต่อคัน `vehicleCapacity`
- vehicle profile
- route preference

รถถูกสร้างเป็น ID:

```text
R-01
R-02
R-03
...
```

ในเวอร์ชันนี้รถทุกคันเริ่มจาก Depot เดียวกัน เพื่อให้ logic เสถียรและอธิบายง่ายในการนำเสนอ

## 3. Planner คำนวณเส้นทางจริงทุก candidate

สำหรับรถแต่ละคัน Planner จะทดลอง:

```java
router.findRoute(
    depot,
    request.getNode(),
    preference,
    profile
);
```

ดังนั้นไม่ได้ใช้แค่ระยะเส้นตรง แต่ใช้ **Road Graph จริง + AI Risk + Field Report + Vehicle Capability**

## 4. Assignment Score

เริ่มจาก route cost ของ `RiskAwareRouter`

```text
routeCost = distance/time cost + flood risk penalty
```

แล้วคูณด้วย priority factor

```text
CRITICAL = 0.40
HIGH     = 0.62
NORMAL   = 1.00
LOW      = 1.20
```

คะแนนต่ำ = ควรส่งรถไปก่อน

มี people factor เพิ่มเล็กน้อยเพื่อช่วยจุดที่มีคนรอจำนวนมาก

```java
score = routeCost
      * request.priorityFactor()
      * peopleFactor;
```

จึงไม่ใช่แค่ “จุดใกล้สุด”

## 5. คนมากกว่าความจุรถ

ถ้า:

```text
REQ-001 = 7 คน
รถ 1 คัน = 4 คน
```

Planner สามารถได้:

```text
R-01 → REQ-001 = 4 คน
R-02 → REQ-001 = 3 คน
```

จากนั้นรถคันต่อไปจึงไป request อื่น

## 6. Output

API ใหม่:

```text
POST /api/runMultiVehicleRescue
```

request:

```json
{
  "preference": "SAFEST",
  "vehicle": "RESCUE_TRUCK",
  "fleetSize": 4,
  "vehicleCapacity": 4
}
```

response มี:

```text
rescueRequests
rescueAssignments
rescuePlanTotalPeople
rescuePlanAssignedPeople
rescuePlanUnservedPeople
```

แต่ละ assignment มี:

```text
vehicleId
targetNodeId
requestId
priority
assignedPeople
distanceMeters
averageRisk
maxRisk
routeEdgeIds
```

หน้าเว็บจะแสดง route ของรถหลายคันคนละเส้นบนแผนที่

---

# API ที่เพิ่ม

```text
POST /api/clearRescueRequests
POST /api/runMultiVehicleRescue
```

และ API เดิมได้รับความสามารถเพิ่ม:

```text
POST /api/addRescuePoint
```

รองรับ:

```json
{
  "nodeId": "5375",
  "people": 7,
  "priority": "CRITICAL",
  "note": "มีผู้สูงอายุ",
  "replaceExisting": false
}
```

---

# Demo ที่ทดสอบแล้ว

Test setup:

```text
Depot: Node 3817
REQ-001: 7 คน, CRITICAL
REQ-002: 3 คน, HIGH
REQ-003: 5 คน, NORMAL
Fleet: 4 คัน
Capacity: 4 คน/คัน
Mode: SAFEST
```

ผลก่อน Field Report:

```text
R-01 → REQ-001 | 4 คน | 4.95 km
R-02 → REQ-001 | 3 คน | 4.95 km
R-03 → REQ-002 | 3 คน | 4.80 km
R-04 → REQ-003 | 4 คน | 6.30 km

Total = 15 คน
Assigned = 14 คน
Waiting = 1 คน
```

จากนั้นเพิ่ม Field Report:

```text
FR-001
ROAD_CLOSED
Rescue Team A
```

ระบบล้าง assignment เก่าทันที

คำนวณใหม่:

```text
R-01 → REQ-001 | route เปลี่ยน 4.95 → 5.25 km
R-02 → REQ-001 | route เปลี่ยน 4.95 → 5.25 km
```

แสดงว่า Field Report มีผลกับ Multi-Vehicle Planner จริง

AI integration ก็ทดสอบร่วมกันแล้ว:

```text
Risk grids >= 25% = 39,567
Affected road edges = 54,756
```

ดังนั้น flow ทั้งหมดทำงานร่วมกันได้:

```text
AI Prediction
    ↓
Road Risk
    ↓
Field Override
    ↓
Risk-Aware Routing
    ↓
Multi-Vehicle Assignment
```

---

# ข้อจำกัดที่ควรพูดตรง ๆ ตอนนำเสนอ

เวอร์ชันนี้เป็น **decision-support prototype**

- รถทุกคันเริ่มจาก Depot เดียวกัน
- Planner เป็น greedy explainable assignment ไม่ใช่ global optimization เต็มรูปแบบ
- AI ทำนาย flood risk ไม่ใช่ water depth
- Field Report จำลองช่องทางรับข้อมูลภาคสนาม ยังไม่ได้เชื่อมระบบแจ้งเหตุภายนอกจริง

ข้อดีคือ architecture พร้อมต่อยอดไปยัง GPS fleet, sensor, crowdsourcing หรือ dispatch center ได้ในอนาคต
