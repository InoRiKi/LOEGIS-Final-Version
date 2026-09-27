# Loegis — Dynamic Risk-Aware Routing Upgrade

เวอร์ชันนี้เพิ่มระบบหาเส้นทางที่พิจารณา **ความเสี่ยงน้ำท่วม + เวลาที่ช้าลง + ความสามารถของยานพาหนะ** แทนการใช้ระยะทางอย่างเดียว

## ฟีเจอร์ที่เพิ่ม

### 1) Route Preference
ผู้ใช้เลือกได้ 3 แบบ
- `FASTEST` — ให้ความสำคัญกับระยะทาง/เวลาเป็นหลัก
- `BALANCED` — สมดุลระหว่างเวลาและความเสี่ยง
- `SAFEST` — เพิ่มน้ำหนักการหลีกเลี่ยงเส้นทางเสี่ยง

### 2) Vehicle Capability
เพิ่มโปรไฟล์ยานพาหนะ
- `DELIVERY_TRUCK` — รถขนส่ง ผ่านได้ถึงน้ำตื้น
- `RESCUE_TRUCK` — รถกู้ภัย ผ่านระดับปานกลางและใช้เส้นฉุกเฉินได้
- `HIGH_WATER_RESCUE` — รถกู้ภัยยกสูง รับความเสี่ยงได้ถึงระดับน้ำลึก

### 3) Dynamic Cost
ระบบใหม่คำนวณต้นทุนถนนจาก

`cost = distanceWeight × (distance × travelTimeFactor) + riskWeight × (distance × riskLevel × 4)`

ก่อนนำถนนมาคำนวณ ระบบจะตรวจว่า VehicleProfile สามารถผ่านถนนนั้นได้หรือไม่ ถ้าไม่ได้ ถนนจะถูกตัดออกจาก candidate route ทันที

### 4) Explainable Route
ผลลัพธ์หน้าเว็บเพิ่ม
- ระยะทางรวม
- ความเสี่ยงเฉลี่ย
- ความเสี่ยงสูงสุด
- คำอธิบายว่าระบบใช้โหมดใดและยานพาหนะชนิดใด

## ไฟล์ที่เพิ่ม

- `src/routing/RoutePreference.java`
  - เก็บน้ำหนัก FASTEST / BALANCED / SAFEST
- `src/routing/RiskAwareRouter.java`
  - Dijkstra-style router ใหม่ที่ใช้ dynamic risk cost และ vehicle capability
- `src/vehicle/VehicleProfile.java`
  - กฎการผ่านน้ำและ reverse edge ของรถแต่ละชนิด

## ไฟล์ที่แก้

### `src/server/SimulationState.java`
- เปลี่ยน route calculation ของหน้าเว็บมาใช้ `RiskAwareRouter`
- รับ preference และ vehicle profile
- คำนวณ route metrics ได้แก่ distance, average risk, max risk
- ส่ง `routeExplanation` และ metrics กลับไปหน้าเว็บ

### `src/server/ApiServer.java`
- `/api/runRoute` และ `/api/runRescueMission` รับ JSON เช่น
```json
{
  "preference": "BALANCED",
  "vehicle": "DELIVERY_TRUCK"
}
```

### `webapp/index.html`
- เพิ่ม dropdown รูปแบบการตัดสินใจ
- เพิ่ม dropdown ประเภทยานพาหนะ
- เพิ่มผลความเสี่ยงเฉลี่ย/สูงสุดและคำอธิบาย route

### `webapp/app.js`
- ส่ง preference/vehicle ไป API
- แสดง route risk metrics
- เปลี่ยน default vehicle ตาม Transport / Rescue mode

### `webapp/style.css`
- เพิ่มรูปแบบ UI สำหรับ Smart Risk-Aware Routing และผลวิเคราะห์ความเสี่ยง

## การทดสอบที่ทำ

- Compile Java 21 ด้วย `javac -cp 'lib/*'` ผ่าน
- `node --check webapp/app.js` ผ่าน
- รัน `ServerApp` และโหลด `hatyai_map.graphml` สำเร็จ
- โหลดได้ 17,164 nodes และ 78,738 edges
- ทดสอบ API `/api/runRoute` ด้วย BALANCED + DELIVERY_TRUCK สำเร็จ
- ทดสอบเพิ่ม DEEP flood zone บนเส้นทางเดิม: รถขนส่งเปลี่ยนจากเส้นทาง 0.30 km ไปเป็นเส้นทางอ้อม 1.35 km โดยอัตโนมัติ

## หมายเหตุ

ระบบเดิมส่วนอื่นยังถูกเก็บไว้ เช่น ACO, A*, Dijkstra และ AI flood prediction เพื่อไม่ให้กระทบฟังก์ชันเดิม โดยหน้าเว็บหลักจะใช้ Risk-Aware Router ในการหาเส้นทางแบบใหม่
