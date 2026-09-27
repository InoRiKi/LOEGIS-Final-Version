# Loegis — AI + Field Report + Multi-Vehicle Rescue

เวอร์ชันนำเสนอที่รวม:

- AI Flood Risk → Road Risk
- Risk-Aware Routing
- Field Report / Manual Override
- Multi-Vehicle Rescue Assignment

## Run

เปิดโปรเจกต์ด้วย Java 21 แล้วรัน:

```bash
java -cp "out:lib/*" src.server.ServerApp
```

จากนั้นเปิด `http://localhost:8080`

Windows CMD ใช้ classpath separator เป็น `;`:

```bat
java -cp "out;lib/*" src.server.ServerApp
```

อ่านรายละเอียดระบบใหม่ได้ที่ `PHASE2_PHASE3_GUIDE.md`

## Per-point rescue count
เวอร์ชันนี้ให้กรอกจำนวนผู้ประสบภัยทันทีเมื่อเลือกแต่ละจุดบนแผนที่ แต่ละ `RescueRequest` จึงมีจำนวนคนของตัวเอง และ Rescue Loop ใช้จำนวนเหล่านี้คำนวณ capacity/รอบรถจริง ดู `PER_POINT_RESCUE_COUNT.md`
