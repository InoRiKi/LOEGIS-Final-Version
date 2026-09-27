# Loegis — Multi-Rescue Fix + Minimal UI

## ปัญหาที่แก้

### 1) กดผู้ประสบภัยหลายจุด แต่ route ไปแค่จุดล่าสุด
สาเหตุมี 2 ชั้น:

- หน้าเว็บยังใช้ `/api/runRescueMission` เมื่อกดปุ่มหาเส้นทางในโหมดกู้ภัย
- `SimulationState.runRescueMission()` เดิมเลือก `rescuePoints.get(rescuePoints.size() - 1)` จึงใช้เฉพาะจุดล่าสุด

การแก้:

- ใน `webapp/app.js` ปุ่มหลักในโหมดกู้ภัยเรียก `runMultiRescuePlan()` และ `/api/runMultiVehicleRescue` โดยตรง
- ใน `SimulationState.runRescueMission()` เพิ่ม compatibility guard: ถ้ามีหลาย `rescueRequests` จะส่งต่อไป `runMultiVehicleRescue()` แทน

## 2) Planner เดิมอาจส่งรถซ้ำจุดเดียวจนจุดอื่นไม่ได้รถ

`src/rescue/MultiVehicleRescuePlanner.java` เปลี่ยนจาก Greedy ล้วนเป็น **Coverage-first 2 pass**

### Pass 1 — Coverage
คำนวณ route จาก depot ไปทุก request แล้วให้แต่ละจุดที่เข้าถึงได้มีรถอย่างน้อย 1 คันก่อน ถ้าจำนวนรถเพียงพอ

### Pass 2 — Extra capacity
ถ้ายังมีรถเหลือ ค่อยส่งรถเพิ่มไปจุดที่ยังมีผู้ประสบภัยรออยู่ โดยใช้ priority + route cost + people factor

ผลคือถ้ามี 3 จุดและรถ 3 คันขึ้นไป จะเห็น route ไปครบทั้ง 3 จุด (ถ้าแต่ละจุดเข้าถึงได้)

## 3) UI กู้ภัยเรียบง่ายขึ้น

### หน้าหลักใน Rescue Mode
เหลือ flow หลัก:

1. เลือกจุดเริ่มต้น
2. กด `เพิ่มผู้ประสบภัย`
3. คลิกแผนที่ซ้ำได้หลายจุด
4. กด `วางแผนกู้ภัย N จุด`

ช่องเทคนิคถูกซ่อนใน `<details>` ชื่อ `ตั้งค่าขั้นสูง`

ค่าที่ซ่อน:
- จำนวนคนของจุดถัดไป
- Priority
- จำนวนรถ
- ความจุต่อคัน

`routePreference` และ `vehicleProfile` ถูกซ่อนใน Rescue Mode และใช้ค่าเริ่มต้น `BALANCED + RESCUE_TRUCK` เพื่อลดความสับสน

## 4) ผลลัพธ์แบบ Minimal เปิด/ปิดได้

ย้ายผลกู้ภัยจาก sidebar ไปเป็น floating panel บนแผนที่

ปุ่ม:
`ผลกู้ภัย N จุด`

ภายในแสดงเฉพาะ:
- จำนวนรถ
- จำนวนจุด
- จำนวนคนที่ช่วยได้/ยังรอ
- รถคันไหน -> request ไหน
- จำนวนคน
- ระยะทาง
- Priority

ผู้ใช้กด X เพื่อปิด และกดปุ่มผลกู้ภัยเพื่อเปิดใหม่ได้

## ไฟล์ที่แก้

### `src/rescue/MultiVehicleRescuePlanner.java`
- เปลี่ยนเป็น Coverage-first planner
- route ของแต่ละ request คำนวณหนึ่งครั้ง
- Pass 1 กระจายรถให้หลายจุด
- Pass 2 เติม capacity

### `src/server/SimulationState.java`
- เพิ่ม guard ใน `runRescueMission()` เพื่อไม่ให้หลาย request ถูกลดเหลือจุดล่าสุด

### `webapp/index.html`
- Simplify Rescue Planner
- ย้าย advanced settings ไปใน `<details>`
- เพิ่ม floating rescue result panel

### `webapp/app.js`
- Rescue mode ใช้ `/api/runMultiVehicleRescue` จากปุ่มหลัก
- จำนวนรถ default ตามจำนวน request จนกว่าผู้ใช้จะปรับเอง
- เพิ่มเปิด/ปิด result panel
- เปลี่ยน label `จุดหมาย` เป็น `เพิ่มผู้ประสบภัย` ใน Rescue Mode

### `webapp/style.css`
- Minimal rescue UI
- Floating result panel
- Compact assignment cards

## ผลทดสอบ

ทดสอบบน `hatyai_map.graphml`

- Nodes: 17,164
- Edges: 78,738
- Depot: Node 3817
- Requests: 5375, 6500, 8000
- Fleet: 3

ผล:
- R-01 -> REQ-001
- R-02 -> REQ-003
- R-03 -> REQ-002

`unique request targets = 3` จึงยืนยันว่า 3 รถถูกกระจายไปครบ 3 จุด

ทดสอบทั้ง `/api/runMultiVehicleRescue` และ legacy `/api/runRescueMission` แล้วได้หลายจุดเหมือนกัน
