# Per-Point Rescue Count

## ปัญหาเดิม
จำนวนผู้ประสบภัยถูกกำหนดจากช่อง `จำนวนคนของจุดถัดไป` ที่ซ่อนอยู่ใน Advanced settings ทำให้ผู้ใช้ไม่รู้ชัดว่าค่าที่กรอกจะผูกกับจุดใด แม้ backend จะรองรับ `RescueRequest.people` แยกต่อจุดอยู่แล้ว

## สิ่งที่แก้

### 1. ถามจำนวนคนทันทีเมื่อวางจุด
ใน `webapp/app.js` ฟังก์ชัน `setTargetNode()` จะเรียก `askRescuePointDetails(node)` ก่อนส่งข้อมูลไป `/api/addRescuePoint`

```js
const details = await askRescuePointDetails(node);
if (!details) return;

payload.people = details.people;
payload.priority = details.priority;
payload.replaceExisting = false;
```

ดังนั้นแต่ละจุดมีข้อมูลของตัวเอง เช่น

- REQ-001 = 2 คน
- REQ-002 = 6 คน
- REQ-003 = 1 คน

### 2. หน้าต่างเพิ่มจุดแบบ minimal
หลังคลิกแผนที่จะมี popup ให้เลือก

- จำนวนผู้ประสบภัย 1–200 คน
- ความเร่งด่วน Normal / High / Critical / Low

กด `เพิ่มจุดนี้` แล้วจึงบันทึก request จริง

### 3. Queue แสดงจำนวนของแต่ละจุด
จากเดิมที่เห็นเพียงจำนวนจุดและจำนวนคนรวม ตอนนี้แสดง

```text
3 จุด    รวม 9 คน
REQ-001  2 คน
REQ-002  6 คน
REQ-003  1 คน
```

### 4. Marker บนแผนที่แสดงจำนวนคน
Marker สีแดงของผู้ประสบภัยจะแสดงเลขจำนวนคน เช่น `2`, `6`, `1` แทนตัว B ทำให้มองแผนที่แล้วเห็น demand ของแต่ละจุดทันที

## Backend
ไม่ต้องเปลี่ยนโครงสร้าง backend เพราะระบบเดิมมีข้อมูลต่อจุดอยู่แล้ว:

```java
public class RescueRequest {
    private final int people;
}
```

และ API รับ `people` ต่อ request อยู่แล้ว

```java
state.addRescueRequestById(nodeId, people, priority, note, replaceExisting);
```

`MultiVehicleRescuePlanner` จึงใช้จำนวนคนของแต่ละจุดโดยตรงในการคำนวณ Slot และ Rescue Loop

## ตัวอย่าง
รถ 1 คัน ความจุ 4 คน

- REQ-001 = 2 คน
- REQ-002 = 5 คน

ระบบสามารถวางแผนเป็น

```text
รอบ 1: Start → REQ-001 รับ 2 → REQ-002 รับ 2 → Start
รอบ 2: Start → REQ-002 รับอีก 3 → Start
```

จำนวนที่เหลือของ REQ-002 ถูกเก็บแยกจาก REQ-001 และลดลงตามจำนวนที่รถรับจริง
