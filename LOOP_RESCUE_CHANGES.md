# Loegis — Multi-stop / Multi-trip Rescue Loop

## ปัญหาเดิม
Planner เดิมผูก assignment แบบ `รถ 1 คัน -> Rescue Request 1 จุด` ทำให้เมื่อจำนวนรถน้อยกว่าจำนวนจุด ผู้ประสบภัยบางจุดไม่ถูกวางแผนต่อ แม้ว่ารถจะยังมี capacity เหลือ หรือสามารถกลับฐานเพื่อออกเที่ยวใหม่ได้

## Logic ใหม่
รถแต่ละคันมี state ของตัวเอง:

- `currentNode` ตำแหน่งปัจจุบัน
- `capacityLeft` จำนวนที่นั่ง/slot ที่ยังเหลือ
- `tripNumber` รอบปัจจุบัน
- `mission` เส้นทางทั้งหมดของรถคันนั้น

Planner ทำงานเป็น loop:

1. รถเริ่มจาก Start/Depot พร้อม capacity เต็ม
2. เลือก Rescue Request ที่เหมาะสมจากตำแหน่งปัจจุบัน โดยใช้ RiskAwareRouter
3. รับคน `min(capacityLeft, peopleWaiting)`
4. ถ้ายังมี slot เหลือ รถเลือกจุดถัดไปต่อจากตำแหน่งปัจจุบัน ไม่กลับ Start โดยไม่จำเป็น
5. ถ้ารถเต็มและยังมีผู้ประสบภัยค้างอยู่ รถกลับ Start
6. เมื่อกลับ Start จะ reset capacity และเพิ่ม `tripNumber`
7. เริ่มเที่ยวใหม่ด้วยรถคันเดิม
8. ทำซ้ำจนช่วยครบ หรือไม่มีเส้นทางที่เข้าถึงจุดที่เหลือได้
9. เมื่อภารกิจจบ รถที่รับคนแล้วจะกลับ Start

### ตัวอย่าง
รถ 1 คัน ความจุ 3 คน
- REQ-001 = 2 คน
- REQ-002 = 2 คน

ผล:

`รอบ 1: Start -> REQ-001(2) -> REQ-002(1) -> Start`

`รอบ 2: Start -> REQ-002(1) -> Start`

## ไฟล์ที่แก้

### `src/rescue/MultiVehicleRescuePlanner.java`
เปลี่ยนจาก Coverage-first assignment แบบหนึ่งรถต่อหนึ่ง assignment เป็น stateful multi-stop / multi-trip planner

เมธอดสำคัญ:
- `chooseBestCandidate(...)` เลือกจุดต่อไปจากตำแหน่งปัจจุบัน
- `returnToDepot(...)` สร้างเส้นทางกลับฐานเมื่อรถเต็มหรือจบทริป
- `VehicleState` เก็บตำแหน่ง, slot ที่เหลือ, เลขรอบ และ mission ของรถ

### `src/rescue/RescueAssignment.java`
เปลี่ยนความหมายของ Assignment ให้เป็น "ภารกิจทั้งหมดของรถหนึ่งคัน"

ภายในมี `Leg` สองชนิด:
- `PICKUP` เดินทางไปจุดผู้ประสบภัย
- `RETURN` เดินทางกลับ Start

แต่ละ leg เก็บ:
- tripNumber
- from/to node
- requestId
- จำนวนคนที่รับ
- slot ที่เหลือหลังรับ
- route edges
- distance/risk

### `src/server/SimulationState.java`
JSON ของ `rescueAssignments` เพิ่ม:
- `tripCount`
- `pickupStopCount`
- `legs[]`

แต่ละ leg มี `type`, `tripNumber`, `requestId`, `assignedPeople`, `slotRemainingAfter`, `routeEdgeIds`

### `webapp/app.js`
Result UI แสดงผลเป็นหนึ่งการ์ดต่อรถ เช่น:

`R-01   4 คน`

`2 รอบ • 3 จุด • 41.70 km`

`รอบ 1: Start → REQ-001 (2) → REQ-002 (1) → Start`

`รอบ 2: Start → REQ-002 (1) → Start`

Route บนแผนที่ยังใช้สีเดียวต่อรถ แม้รถจะมีหลาย stop/หลายรอบ เพื่อลดความรก

## ผลทดสอบ

### Test A — มี slot เหลือ
รถ 1 คัน, capacity 4, REQ-001 1 คน, REQ-002 2 คน

ผล: รอบเดียว รับครบ 2 จุด รวม 3 คน

### Test B — ต้องเริ่มรอบใหม่
รถ 1 คัน, capacity 3, REQ-001 2 คน, REQ-002 2 คน

ผล:
- รอบ 1 รับ REQ-001 2 คน + REQ-002 1 คน แล้วกลับ Start
- รอบ 2 รับ REQ-002 ที่เหลือ 1 คน แล้วกลับ Start
- assigned 4, unserved 0

### Test C — รถน้อยกว่าจุด
รถ 2 คัน, capacity 2, Rescue Request 3 จุด จุดละ 2 คน

ผล:
- ช่วยครบทั้ง 3 จุด
- รถหนึ่งคันออก 2 รอบ
- assigned 6, unserved 0

## แนวคิดเวลาอธิบาย
ระบบไม่ได้จับคู่ `รถหนึ่งคันกับหนึ่งจุด` อีกต่อไป แต่สร้าง Route Mission ให้รถแต่ละคันตาม capacity จริง หากรถยังมีที่ว่างจะรับจุดถัดไปต่อทันทีเพื่อลดการกลับฐานโดยไม่จำเป็น และเมื่อเต็มจึงกลับศูนย์เพื่อส่งผู้ประสบภัย/reset capacity ก่อนเริ่มรอบใหม่
