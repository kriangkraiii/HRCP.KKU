# ชุดทดสอบ HRCP-KKU

ชุดทดสอบนี้เขียนขึ้นเพื่อครอบคลุมกระบวนการตาม
`Flow การขอกำหนดตำแหน่งทางวิชาการ (ผศ./รศ./ศ.).pdf` (33 ขั้นตอน)
ช่องว่างที่พบระหว่างทางอยู่ใน [docs/GAP-REPORT-flow-vs-implementation.md](../../../docs/GAP-REPORT-flow-vs-implementation.md)

---

## วิธีรัน

```bash
# ชุดหลัก — ไม่ต้องใช้ Docker ไม่ต้องใช้เบราว์เซอร์ (~2 นาที)
./mvnw test -DskipTests=false

# ชุดเบราว์เซอร์จริง + PostgreSQL จริง — ต้องเปิด Docker ก่อน
./mvnw verify -Pe2e -DskipTests=false
```

> `skipTests` ตั้งค่าเริ่มต้นเป็น `true` ใน `pom.xml` — ต้องส่ง `-DskipTests=false` เสมอ

---

## กติกาสำคัญ: ห้ามเทสส่งอีเมลถึงคนจริง

การรันเทสจะ**ไม่มีทางเปิดการเชื่อมต่อ SMTP** เพราะ `NoRealMailConfig` แทนที่ bean `mailSender`
ของ Spring Boot ด้วย `RecordingMailSender` ซึ่งบันทึกข้อความไว้แทนการส่ง
ประตูกัน `GuardedJavaMailSender` ของระบบจริงยังอยู่ในเส้นทางครบถ้วน จึงถูกทดสอบไปด้วย

นอกจากนี้ `RecordingMailSender` จะ **fail ทันที** ถ้ามีเทสจ่าหน้าถึงที่อยู่นอกโดเมนสงวน
ให้ใช้ `@example.invalid` (RFC 2606) สำหรับ fixture ทุกตัว — `TestDataFactory.DOMAIN` มีให้แล้ว

`MailSafetyNetTest` เป็นตาข่ายชั้นสุดท้าย: fail ถ้ามีใครเอา `@Disabled` ออกจาก
`SendAllTestEmailsTest` (ซึ่งยิง smtp.gmail.com จริง) หรือมีเทสใหม่ที่อ้างถึง SMTP ภายนอก

---

## โครงสร้าง

| แพ็กเกจ | ครอบคลุม | ต้องใช้ Docker |
|---------|----------|:--------------:|
| `com.ecom.support` | โครงพื้นฐาน: `AbstractFlowTest`, `TestDataFactory`, `RecordingMailSender` | – |
| `com.ecom.flow` | **เส้นทางผู้ใช้จริงตั้งแต่ต้นจนจบ ข้อ 1-31** ผ่าน HTTP จริง | ไม่ |
| `com.ecom.academic` | เฟส 1: ลำดับสถานะ, คุณสมบัติผู้ยื่น, วันหมดอายุผลประเมิน | ไม่ |
| `com.ecom.research` | งานวิจัย/Scopus: การจำกัดสิทธิ์, ความครอบคลุมของ picker, กติกาห้ามยื่นซ้ำ | ไม่ |
| `com.ecom.notification` | อีเมลและกล่องข้อความในระบบ ทั้งสองเฟส + การแยกของแต่ละคน | ไม่ |
| `com.ecom.migration` | รัน Flyway V0-V11 บน PostgreSQL จริง | **ใช่** |
| `com.ecom.e2e` | เบราว์เซอร์จริง (Playwright): Scopus picker, เส้นทางเต็ม, สิทธิ์เข้าถึง | **ใช่** |

เทสเดิมในโปรเจกต์ (`com.ecom.config`, `com.ecom.service`, ฯลฯ) ยังอยู่ครบและรันได้ตามปกติ

---

## เทสที่ `@Disabled` ไว้เป็น executable spec

เทสเหล่านี้อธิบายพฤติกรรมที่ **ควรจะเป็น** แต่ยัง implement ไม่ได้ในโครงสร้างปัจจุบัน
ทุกตัวมีรหัส GAP กำกับ และมีเทสคู่กันที่ **ผ่าน** เพื่อบันทึกพฤติกรรมวันนี้ไว้ —
เมื่อแก้ช่องว่างแล้ว เทสที่ผ่านจะเริ่ม fail ซึ่งเป็นสัญญาณให้เปิดตัว `@Disabled` แทน

| เทส | ช่องว่าง |
|-----|---------|
| `ResearchReuseLockSpec.TheRule` | GAP-11/12 — ไม่มีการผูกผลงานวิจัยกับคำร้อง |
| `ScopusPickerCoverageTest.everySectionThatTakesPublicationsShouldOfferThePicker` | GAP-10 — ปุ่มเลือกจาก Scopus ขาด 8 จาก 11 กลุ่ม |
| `EvaluationEligibilityTest.completedPassShouldBeEligible` | GAP-20 — นิยาม "ผลประเมินที่ใช้ได้" ขัดกันสองที่ |
| `EvaluationEligibilityTest.thaiMonthNameExpiryShouldBeHonoured` | GAP-21 — วันหมดอายุที่เป็นชื่อเดือนไทยไม่ถูกบังคับ |
| `AcademicStatusFlowTest.closedRequestsShouldStayClosed` | GAP-31 — เอกสารที่ 8 ปลุกคำร้องที่ถูกปฏิเสธ |
| `AcademicStatusFlowTest.illegalTransitionsShouldBeRejected` | GAP-30 — ไม่มี guard การเปลี่ยนสถานะ |

---

## ถ้าเทสชั้น Docker ถูก skip ทั้งหมด

`@EnabledIfDockerAvailable` จะข้ามเทสให้เองเมื่อต่อ Docker ไม่ได้ ซึ่งบน Docker Desktop
มักเกิดจาก **socket ไม่ได้ถูกสร้าง** — ตรวจด้วย:

```bash
ls -la /var/run/docker.sock        # ต้องมีจริง ไม่ใช่ symlink ที่ชี้ไปที่ว่าง
```

ถ้าไม่มี ให้เปิดใน Docker Desktop → **Settings → Advanced →
"Allow the default Docker socket to be used"** แล้ว restart Docker Desktop
(ต้องใส่รหัสผ่านเครื่อง) จากนั้นเทสชั้นนี้จะรันเอง

---

## การเขียนเทสเพิ่ม

สืบทอดจาก `AbstractFlowTest` เพื่อให้ได้ context เดียวกัน (Spring cache ใช้ซ้ำ = เร็ว),
`MockMvc` ที่ผ่าน filter chain จริง, `TestDataFactory` และตัวบันทึกอีเมล

**ข้อควรระวังที่เคยพลาดมาแล้ว:** `CsrfFilter` ทำงาน **ก่อน** การยืนยันตัวตน POST ที่ไม่มี
CSRF token จึงถูกเด้งไป `/signin?expired=true` ซึ่งเป็น 3xx — `status().is3xxRedirection()`
จะผ่านทั้งที่ request ไม่เคยถึงตัว controller ให้ใช้ `expectAccepted(...)` ที่ตรวจปลายทางจริง
และอย่าลืม `.with(csrf())` ในทุก POST
