# รายงานบั๊ก HRCP-KKU-Academic — 6 กันยายน 2569

**ขอบเขต:** รันชุดเทสอัตโนมัติทั้งชุดบน working tree ปัจจุบัน (branch `main`, มีการแก้ค้างอยู่ใน
`DocumentGenerationService.java` / `SignatureStampingTest.java` / CSS / `base_academic.html`)
พร้อมตรวจโค้ดประกอบเพื่อระบุสาเหตุ
**สถานะ: ยังไม่ได้แก้ไขอะไรทั้งสิ้น** — รายงานนี้จัดทำก่อนลงมือตามที่สั่ง

**คำสั่งที่ใช้**
```bash
cd HRCP-KKU-Academic && ./mvnw test -DskipTests=false
```

**ผลลัพธ์**
```
Tests run: 1108,  Failures: 2,  Errors: 9,  Skipped: 7
```

> **หมายเหตุสำคัญ:** รอบนี้ Docker เปิดอยู่ ชั้น migration จึง**รันจริงเป็นครั้งแรก**
> ในรายงานรอบก่อน (surefire-reports ลงวันที่ 5 ก.ย.) `MigrationOnPostgresTest` ขึ้นเป็น
> `tests="9" skipped="9"` — คือ error 9 ข้อด้านล่างนี้ถูกซ่อนอยู่ในรูปของ "skip เงียบ" มาตลอด

---

## สรุปผู้บริหาร

| # | ระดับ | หัวข้อ | ผลกระทบ |
|---|------|--------|---------|
| B-01 | 🔴 สูง | `V16__attachment_checklist_slot.sql` รันไม่ผ่านบน PostgreSQL ที่สร้างใหม่ | แอปสตาร์ตไม่ขึ้นบนฐานข้อมูลใหม่ + V15–V20 ไม่เคยถูกตรวจบน PostgreSQL จริงเลย |
| B-02 | 🟠 กลาง-สูง | เทสเส้นทางผู้ใช้เต็มเส้น (`FullJourneyMockMvcTest`) แดงตั้งแต่บังคับใช้ .p12 | เส้นทาง "ลงนามสำเร็จ" ผ่านหน้าเว็บไม่มีเทสคุมเลยแม้แต่ตัวเดียว |
| B-03 | 🟡 ต่ำ | `RequiredAttachmentNoticeTest` ยึดข้อความบนหน้าจอแบบตรงตัว ที่ถูกแก้คำไปแล้ว | เทสแดงโดยที่ระบบไม่ได้ผิด — บั๊กอยู่ที่ตัวเทส |

**ข้อเสนอลำดับการแก้:** B-01 → B-02 → B-03

---

# 🔴 B-01 — Migration V16 รันไม่ผ่านบน PostgreSQL ที่สร้างใหม่

**อาการ** — error 9 ข้อจาก 9 เทสใน `MigrationOnPostgresTest` ล้วนมาจากสาเหตุเดียวกัน:

```
Failed to execute script V16__attachment_checklist_slot.sql
SQL State  : 42P01
Message    : ERROR: relation "academic_attachment" does not exist
Line       : 5
```

เทสที่พังทั้งหมด (ทุกตัวในคลาส):
`everyMigrationApplies`, `migratingTwiceIsSafe`, `v5PartialUniqueIndexIsEnforced`,
`v6ForeignKeysCascade`, `v11RemovesEnumCheckConstraints`, `v12LetsNewStatusValuesBeStored`,
`v12RemovesEveryRequestEnumCheck`, `v13EnforcesOneLinkPerPublicationPerRequest`,
`v13LinksAreDeletedWithTheirRequest`

## สาเหตุ

**ไม่มี migration ไฟล์ใดสร้างตาราง `academic_attachment` เลย** — มีแต่ไฟล์ที่ `ALTER` มัน 3 ไฟล์:

| ไฟล์ | ทำอะไรกับ `academic_attachment` |
|---|---|
| [V16__attachment_checklist_slot.sql](../../HRCP-KKU-Academic/src/main/resources/db/migration/V16__attachment_checklist_slot.sql) | `ADD COLUMN checklist_item` + สร้าง partial index |
| [V18__attachment_checklist_item_integer.sql](../../HRCP-KKU-Academic/src/main/resources/db/migration/V18__attachment_checklist_item_integer.sql) | `ALTER COLUMN checklist_item TYPE integer` |
| [V20__support_link_attachments.sql](../../HRCP-KKU-Academic/src/main/resources/db/migration/V20__support_link_attachments.sql) | ขยาย `stored_file_path` เป็น 2048, `original_filename` เป็น 500 |

ตารางนี้มีอยู่บนเครื่อง production เพราะยุคก่อนใช้ `ddl-auto=update` แล้ว Hibernate สร้างให้
ปัจจุบัน [application.properties:54](../../HRCP-KKU-Academic/src/main/resources/application.properties#L54)
ตั้งเป็น `ddl-auto=${DDL_AUTO:validate}` — **Hibernate ไม่สร้างตารางให้อีกแล้ว**
และ `V0__baseline.sql` เป็นไฟล์ว่างโดยตั้งใจ (baseline-version=0)

## ผลกระทบจริง — แยกเป็น 2 ระดับ

**1. ระดับ production ที่รันอยู่ตอนนี้: ยังไม่พัง**
ฐานข้อมูลเดิมมีตารางนี้อยู่แล้ว V16/V18/V20 จึงรันผ่านไปแล้ว

**2. ระดับฐานข้อมูลใหม่: พังทันที และแอปสตาร์ตไม่ขึ้น**
สภาพแวดล้อมใด ๆ ที่เริ่มจาก PostgreSQL เปล่า — staging ใหม่, สร้าง volume ของ
`docker-compose.yml` ขึ้นใหม่, เครื่อง dev ที่เพิ่งลง, การกู้ระบบจากศูนย์ —
Flyway จะรัน V1…V20 แล้วตายที่ V16 → context ของ Spring ขึ้นไม่ได้ → **บริการล่ม**

ข้อจำกัดนี้ถูกบันทึกไว้แล้วที่ [application.properties:87](../../HRCP-KKU-Academic/src/main/resources/application.properties#L87)
("เพราะ V0 เป็น baseline เปล่า migration ที่มีอยู่จึงสร้างตารางให้แค่บางส่วน")
แต่ยังไม่มีอะไรบังคับให้เรื่องนี้ถูกตรวจ

**3. ผลข้างเคียงที่สำคัญไม่แพ้กัน — V15 ถึง V20 ไม่เคยถูกตรวจบน PostgreSQL จริงเลย**
`MigrationOnPostgresTest` ตายตั้งแต่ V16 ทุกครั้ง ทำให้ **V17 (drop user storage),
V19 (สร้าง `user_digital_certificate` + ALTER `signature_step`) และ V20 ไม่เคยถูกรันจนจบ**
ซ้ำ assertion ที่บรรทัด 179 ก็ยังหยุดอยู่แค่ `"14"`:

```java
// MigrationOnPostgresTest.java:179
.contains("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14");
```

→ ถึงจะแก้ให้รันผ่าน ก็ยังไม่มีอะไรยืนยันว่า V15–V20 ทำงานถูก

## วิธีทำให้เกิดซ้ำ

```bash
docker run --rm -e POSTGRES_PASSWORD=x -p 5433:5432 -d --name hrcp-fresh postgres:16-alpine
# ชี้แอปไปที่ฐานเปล่านี้แล้วสตาร์ต → Flyway ตายที่ V16
# หรือเรียกเทสตรง ๆ (เร็วกว่า):
cd HRCP-KKU-Academic && ./mvnw test -DskipTests=false -Dtest=MigrationOnPostgresTest
```

## แนวทางแก้ที่เสนอ (ยังไม่ได้ทำ)

มีสองชั้นที่ต้องตัดสินใจแยกกัน:

**ชั้นที่ 1 — ปิดช่องของเทส (จำเป็น, ไม่มีข้อโต้แย้ง)**
เพิ่ม `academic_attachment` เข้าไปใน `PRE_EXISTING_SCHEMA`
([MigrationOnPostgresTest.java:57-128](../../HRCP-KKU-Academic/src/test/java/com/ecom/migration/MigrationOnPostgresTest.java#L57-L128))
ให้มีรูปร่างแบบ **ก่อน V16** คือยังไม่มี `checklist_item` และ
`stored_file_path VARCHAR(255)` / `original_filename VARCHAR(255)`
เพื่อให้ V16/V18/V20 ได้พิสูจน์ว่ามันแก้ของจริง ไม่ใช่ผ่านเพราะไม่มีอะไรให้แก้
จากนั้นขยาย assertion บรรทัด 179 ให้ครบถึง `"20"` และเพิ่มเทสต่อ V15/V17/V19/V20

**ชั้นที่ 2 — ปิดช่องของ production (ต้องให้เจ้าของระบบตัดสินใจ)**
ตอนนี้ระบบ **ไม่สามารถสร้างฐานข้อมูลใหม่จากศูนย์ได้เลย** ทางเลือก:
- (ก) เขียน `V0__baseline.sql` ให้เป็น baseline จริง — `CREATE TABLE IF NOT EXISTS` ทุกตาราง
  ที่ยุค `ddl-auto=update` เคยสร้าง (ฐานเดิมไม่กระทบ เพราะ Flyway ข้าม V0 อยู่แล้วที่ `baseline-version=0`
  และ `IF NOT EXISTS` กันซ้ำอีกชั้น)
- (ข) รับสภาพไว้เท่านี้ แล้วเขียนขั้นตอน bootstrap ฐานใหม่ไว้ใน `docs/deployment.md` ให้ชัด
  (ตั้ง `DDL_AUTO=update` รันครั้งแรก แล้วกลับเป็น `validate`)

**ข้อเสนอ:** ทำ (ก) — เพราะ (ข) ต้องอาศัยคนจำขั้นตอนถูกในวันที่ระบบล่ม ซึ่งเป็นวันที่แย่ที่สุดที่จะพึ่งความจำ

---

# 🟠 B-02 — เทสเส้นทางผู้ใช้เต็มเส้นแดงมาตั้งแต่บังคับใช้ .p12 และเส้นทางลงนามสำเร็จไม่มีเทสคุมเลย

**เทสที่พัง:** `flow/FullJourneyMockMvcTest.aProfessorWalksTheWholeProcess`

```
[ลงนามเอกสารที่ 0 แล้วต้องถูกบันทึกว่าลงนามเสร็จ]
Expecting value to be true but was false
```

## สาเหตุ

คอมมิต `eb76933 Add .p12 e-signing` และ `aee90b7 Require .p12` เพิ่มด่านบังคับใบรับรองดิจิทัล
ไว้**หน้าสุด**ของ endpoint ลงนาม:

```java
// SigningController.java:233-239
UserDtls me = currentUser(principal);
var certOpt = digitalCertificateService.findActive(me);
if (certOpt.isEmpty()) {
    redirectAttributes.addFlashAttribute("errorMsg",
        "คุณยังไม่ได้ติดตั้งใบรับรอง Digital ID (.p12) ...");
    return "redirect:/esign/sign/" + stepId;      // ← ออกก่อนถึง workflow.sign()
}
```

แต่ [TestDataFactory.java](../../HRCP-KKU-Academic/src/test/java/com/ecom/support/TestDataFactory.java)
มีแค่ `signatureFor(owner)` ที่สร้าง **ภาพลายเซ็น** — **ไม่มีเมธอดใดติดตั้งใบรับรอง .p12 เลย**
(`grep -n "certificate\|p12" TestDataFactory.java` ได้ผลว่าง)

ผู้ใช้ในเทสจึงไม่มีใบรับรอง → ถูกเด้งกลับที่บรรทัด 238 → `isApplicantSignatureCompleted()` เป็น `false`

## ปัญหาซ้อนที่ร้ายกว่าตัวเทสแดง

**assertion ที่ควรจับได้กลับปล่อยผ่าน** — บรรทัด 376-379 ของเทสตรวจปลายทาง redirect ด้วยคำนำหน้า `"/"`:

```java
// FullJourneyMockMvcTest.java:376-379
expectAccepted(mvc.perform(post("/esign/sign/" + myStep.getId())
        .param("userSignatureId", ...).param("consent", "true")
        .with(csrf()).with(asProfessor())), "/");     // ← "/" เป็นคำนำหน้าของทุก path
```

`/esign/sign/123` ก็ขึ้นต้นด้วย `"/"` การถูกเด้งกลับเพราะไม่มีใบรับรองจึงผ่าน `expectAccepted` ไปได้
เทสเลยไม่ได้ fail ตรงจุดที่พังจริง แต่ไปโผล่อีกสามบรรทัดถัดมาแทน

**และเส้นทาง "ลงนามสำเร็จ" ไม่มีเทสคุมเลยแม้แต่ตัวเดียว:**

```
tests ที่ POST /esign/sign/  → SigningPageRenderTest, FullJourneyMockMvcTest  (2 ไฟล์)
tests ที่แตะ UserDigitalCertificateService → UserDigitalCertificateServiceTest  (1 ไฟล์ ระดับ service ล้วน)
```

ไม่มีไฟล์ใดที่ **ติดตั้งใบรับรองแล้วลงนามผ่าน controller จนสำเร็จ** → ด่าน .p12 ทั้งด่าน
(ทั้งกรณีไม่มีใบรับรอง / ใบรับรองหมดอายุ / PIN ผิด / ลงนามผ่าน) ไม่ถูกตรวจโดยอัตโนมัติ

## แนวทางแก้ที่เสนอ (ยังไม่ได้ทำ)

1. เพิ่ม `digitalCertificate(UserDtls owner)` ใน `TestDataFactory` — สร้าง .p12 self-signed
   ในหน่วยความจำ (BouncyCastle มีอยู่ใน `pom.xml` แล้ว) แล้วลงทะเบียนผ่าน
   `UserDigitalCertificateService.registerCertificate(user, p12Bytes, filename, pin)`
2. เรียกใน setup ของ `FullJourneyMockMvcTest` และส่ง `digitalCertPin` ไปกับ POST ลงนาม
3. **แก้ `expectAccepted(..., "/")` ให้ระบุปลายทางจริง** — ไม่ใช่ `"/"` ที่ยอมรับทุกอย่าง
4. เพิ่มเทสด่าน .p12 โดยตรง: ไม่มีใบรับรอง / ใบรับรองหมดอายุ / PIN ผิด → ต้องถูกปฏิเสธพร้อมข้อความ

---

# 🟡 B-03 — เทสยึดข้อความบนหน้าจอแบบตรงตัว ที่ถูกแก้คำไปแล้ว (บั๊กอยู่ที่เทส ไม่ใช่ที่ระบบ)

**เทสที่พัง:** `academic/RequiredAttachmentNoticeTest.thePageSaysAttachmentsAreRequiredBeforeAnythingIsTyped`

```java
// RequiredAttachmentNoticeTest.java:58
.contains("ต้องแนบไฟล์อย่างน้อย 1 ไฟล์");
```

ค้นทั้ง `templates/` แล้ว **ไม่มีข้อความนี้เหลืออยู่** แต่หน้าจอ**ยังบอกเรื่องนี้ครบ** ด้วยคำใหม่:

| ที่ | ข้อความปัจจุบัน |
|---|---|
| [document_1_form.html:214](../../HRCP-KKU-Academic/src/main/resources/templates/academic/applicant/document_1_form.html#L214) | `จำเป็นต้องแนบ (อย่างน้อย 1 รายการ)` (badge สีแดงข้างหัวข้อ) |
| [document_1_form.html:335](../../HRCP-KKU-Academic/src/main/resources/templates/academic/applicant/document_1_form.html#L335) | `ยังไม่มีเอกสารหรือลิงก์แนบ กรุณาอัปโหลดไฟล์หรือแนบลิงก์อย่างน้อย 1 รายการ` |

อีกสองเทสในคลาสเดียวกันผ่านปกติ (การปฏิเสธฝั่ง server และข้อความตอนถูกเด้งกลับ ยังทำงานถูก)

**ประเมิน:** เจตนาของกติกา (GAP-37) ยังถูกบังคับใช้อยู่ครบ นี่คือเทสตกยุคจากการแก้คำในคอมมิต
`aee90b7 Require .p12; unify attachments & improve UI` ไม่ใช่ความถดถอยของระบบ

## แนวทางแก้ที่เสนอ (ยังไม่ได้ทำ)

อัปเดต assertion ให้ตรงกับคำปัจจุบัน และ**เลิกยึดประโยคเต็ม** — ตรวจที่ badge ซึ่งเป็นตัวประกาศกติกา
(`จำเป็นต้องแนบ`) แทนการล็อกทั้งประโยค เพื่อไม่ให้แดงอีกทุกครั้งที่มีคนขัดเกลาคำ
เก็บ assertion เชิงลบไว้เหมือนเดิม (ต้องไม่มีคำที่ทำให้เข้าใจว่าเป็นทางเลือก)

---

# รอบที่ 2 — ตรวจชั้น UI/UX โดยเฉพาะ

หลังส่งรายงานรอบแรก มีคำสั่งให้ตรวจชั้น UI/UX ให้ครอบคลุม จึงสร้างเครื่องมือตรวจสองตัว
แล้วรันกับทั้งระบบ พบบั๊กเพิ่ม **6 ข้อ แก้แล้วทั้งหมด**

| # | ระดับ | หัวข้อ | สถานะ |
|---|------|--------|-------|
| UI-01 | 🟠 กลาง | สคริปต์ 133 บรรทัดในหน้ารายการคำร้อง (แท็บจาก URL + autocomplete) ไม่เคยถูกส่งไปเบราว์เซอร์ | [แก้แล้ว] |
| UI-02 | 🟡 ต่ำ | สคริปต์แปลงวันที่เป็น พ.ศ. ในหน้าบันทึกกิจกรรม ไม่เคยถูกส่งไปเบราว์เซอร์ | [แก้แล้ว] |
| UI-03 | 🟠 กลาง-สูง | ฟีเจอร์ "ขอให้ผู้ยื่นแก้ไขและลงนามใหม่" กดแล้วเด้งไปหน้า login ทั้งสองเฟส | [แก้แล้ว] |
| UI-04 | 🟡 ต่ำ | `GET /admin/users` ที่ไม่มี `?type=` ตอบ 400 หน้าเปล่า | [แก้แล้ว] |
| UI-05 | 🔴 สูง | ผู้ยื่นที่มีคำร้องเดินอยู่ 2 ฉบับ ทำให้แดชบอร์ดตอบ **500 ถาวร** | [แก้แล้ว] |
| UI-06 | 🟡 ต่ำ | เจ้าหน้าที่เปิดเอกสารของคำร้องที่ยังเป็นแบบร่าง ได้ข้อความผิดเรื่อง | [แก้แล้ว] |

---

## 🔴 UI-05 — ผู้ยื่นที่มีคำร้องเดินอยู่ 2 ฉบับ ทำให้แดชบอร์ดตอบ 500 ถาวร

**อาการ** — `IncorrectResultSizeDataAccessException: Query did not return a unique result: 2 results were returned`
ที่ `PositionRequestService.hasActiveRequest` → HTTP 500

**สาเหตุ** — `findActiveByApplicantId` ประกาศคืน `Optional<PositionRequest>` ซึ่งแปลว่า
"มีได้ไม่เกินหนึ่งแถว" แต่ SQL ที่มันยิงไม่ได้รับประกันเรื่องนั้น ระบบตั้งใจให้ผู้ยื่นมีคำร้อง
ที่ยังเดินอยู่ครั้งละหนึ่งฉบับจริง และ `hasActiveRequest` คือด่านที่บังคับกติกานั้น
แต่ **"ควรมีหนึ่ง" กับ "มีหนึ่งเสมอ" ไม่ใช่เรื่องเดียวกัน**

**เส้นทางที่ทำให้เกิดจริงโดยไม่ต้องผ่าน `createRequest` เลย:**

1. คำร้อง A ถูกปฏิเสธ → `REJECTED` ซึ่งเป็นสถานะปลายทาง (`isTerminal()`)
2. ด่านจึงตอบว่าไม่มีคำร้องค้าง ผู้ยื่นสร้างคำร้อง B ได้ตามกติกา
3. เจ้าหน้าที่ย้อนสถานะ A ออกจาก `REJECTED` ผ่าน `POST /admin/position/request/{id}/status`

ตอนนั้น A และ B ต่างก็ยังเดินอยู่ **ทุกหน้าที่เรียกด่านนี้ตอบ 500 ทันทีและถาวร** —
แดชบอร์ดคำร้องตำแหน่ง, แดชบอร์ดการประเมินการสอน และหน้าสร้างคำร้องใหม่
ผู้ยื่นแก้เองไม่ได้เลย และ**ด่านที่มีไว้กันไม่ให้เกิดสภาพนี้ กลายเป็นสิ่งแรกที่พังเมื่อมันเกิดขึ้น**

**แก้แล้ว** — เปลี่ยนทั้ง `findActiveByApplicantId` และ `findDraftByApplicantId` ให้คืน `List`
พร้อม `ORDER BY createdAt DESC`; `hasActiveRequest` ใช้ `!isEmpty()`; `findDraftByApplicant`
เอาฉบับใหม่สุด
มีเทสกำกับที่ `academic/ActiveRequestGuardTest` (4 เทส) — **ยืนยันแล้วว่าย้อนโค้ดกลับเป็นแบบเดิม
เทสนี้ fail ด้วย 500 ตัวเดิม**

---

## 🟠 UI-03 — ฟีเจอร์ "ขอให้ผู้ยื่นแก้ไขและลงนามใหม่" กดแล้วเด้งไปหน้า login

`resignForm` ในหน้ารายละเอียดคำร้อง **ทั้งสองเฟส** ประกาศเป็น

```html
<form id="resignForm" method="post" action="">
```

`action` ถูกตั้งด้วย JavaScript ตอนเปิด modal จึงใช้ `th:action` ไม่ได้ — และ **Thymeleaf
เติม CSRF token ให้เฉพาะฟอร์มที่มี `th:action` เท่านั้น** ฟอร์มนี้จึงไม่มี token เลย
ตรวจแล้วว่าไม่มีสคริปต์กลางตัวใดเติมให้ด้วย (ที่มีใน `static/js/` เป็นการใส่ token
ลง header/FormData ของ AJAX ทั้งหมด ไม่ใช่ native form submit)

ผลคือ `CsrfFilter` ปฏิเสธแล้วเด้งไป `/signin?expired=true` — เจ้าหน้าที่กรอกเหตุผลเสร็จ
กดส่ง แล้วเจอหน้าเข้าสู่ระบบ เหมือน **"จู่ ๆ ก็หลุดออกจากระบบ"** โดยไม่มีคำอธิบาย

**แก้แล้ว** — เติม `<input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}">`
ในทั้งสองไฟล์ และ `EveryPageRendersTest` บังคับกติกา "ทุกฟอร์ม POST ต้องมี CSRF token" ต่อไป

---

## 🟠 UI-01 / 🟡 UI-02 — สคริปต์ที่วางนอก `<section>` ไม่เคยถึงเบราว์เซอร์

ทุกหน้าในระบบประกาศตัวเองว่า

```html
<html th:replace="~{academic/base_academic :: layout(~{::section})}">
```

Thymeleaf จะ**แทนที่เอกสารทั้งฉบับ**ด้วย layout แล้วส่งเข้าไปเฉพาะสิ่งที่ `~{::section}`
เลือกได้ — คือ element `<section>` เท่านั้น มาร์กอัปอื่นในไฟล์ รวมถึง `<script>`
ที่วางท้ายไฟล์ตามความเคยชินของหน้า HTML ธรรมดา **ถูกทิ้งทั้งหมดโดยไม่มีคำเตือน**

ไม่มี error ใน log ไม่มี 500 หน้ายัง render สวยงามตามปกติ มีแค่ฟีเจอร์ที่เงียบหายไป

| ไฟล์ | สิ่งที่หายไป |
|---|---|
| `academic/admin/requests.html` (133 บรรทัด) | เลือกแท็บจาก `?type=` ในลิงก์ + **กล่อง autocomplete ของช่องค้นหา** (สร้างรายการจากแถวในตาราง, ไฮไลต์คำ, เลื่อนด้วยลูกศร) |
| `admin/activity_logs.html` (21 บรรทัด) | ป้ายวันที่แบบ พ.ศ. ข้างช่องกรองวันที่ (`dateFromThai`, `dateToThai`) |

**แก้แล้ว** — ย้ายสคริปต์เข้าไปใน `<section>` ทั้งสองไฟล์ และเพิ่ม
`render/LayoutFragmentContractTest` ที่ fail ทันทีถ้ามีใครวาง `<script>` นอก `<section>` อีก

---

## เครื่องมือที่สร้างขึ้นในรอบนี้

| ไฟล์ | ครอบคลุมอะไร |
|---|---|
| `src/test/java/com/ecom/render/LayoutFragmentContractTest.java` | อ่านเทมเพลตทั้ง 86 ไฟล์ ตรวจว่าไม่มี `<script>` ตกอยู่นอก `<section>` — ไม่ต้องมี Spring รันจบใน 0.1 วินาที |
| `src/test/java/com/ecom/render/RenderedPage.java` | ตัวช่วยตรวจหน้าที่ render แล้วด้วย DOM จริง (jsoup) แทน `contains()` ที่เปราะ |
| `src/test/java/com/ecom/render/EveryPageRendersTest.java` | เปิด **55 หน้า** ครอบคลุมแบบฟอร์มเอกสารครบทุกชนิดทั้งสองบทบาท (เดิมมีเทสเรียกแค่ `document/1` ชนิดเดียว) |
| `src/test/java/com/ecom/academic/ActiveRequestGuardTest.java` | กันบั๊ก UI-05 ไม่ให้ถอยหลัง |

**สิ่งที่ `RenderedPage` ตรวจต่อหนึ่งหน้า:** ตอบ 200 และเป็น `text/html` · `<body>` ไม่ว่าง ·
ไม่มีแอตทริบิวต์ `th:*` หลงเหลือ · ไม่มี `${}` `*{}` `#{}` โผล่ให้ผู้ใช้เห็น ·
ไฟล์ที่หน้าอ้างถึงใน `/js/ /css/ /vendor/` มีอยู่จริง · ทุกฟอร์ม POST มี CSRF token ·
ไม่มี `id` ซ้ำในหน้าเดียว

---

## ผลการรันหลังแก้

```
ก่อนแก้:  Tests run: 1108,  Failures: 2,  Errors: 9,  Skipped: 7
หลังแก้:  Tests run: 1168,  Failures: 2,  Errors: 9,  Skipped: 7
```

เทสเพิ่มขึ้น 60 ตัวและ**ผ่านทั้งหมด** ส่วนที่ยังแดงคือ B-01/B-02/B-03 จากรอบแรกเป๊ะ ๆ
(`MigrationOnPostgresTest` 9, `FullJourneyMockMvcTest` 1, `RequiredAttachmentNoticeTest` 1)
— **ไม่มีอะไรถอยหลังจากการแก้รอบนี้**

---

## สิ่งที่ยังไม่ครอบคลุมหลังรอบนี้

เทสชุดใหม่จับ "หน้าถูกส่งออกมาครบและมีโครงสร้างถูกต้อง" ได้แล้ว แต่ **ยังไม่แตะ**:

- **พฤติกรรม JavaScript ตอนรันจริง** — ปุ่มที่กดแล้วไม่มีอะไรเกิดขึ้น, ส่วนที่ควรโผล่แล้วไม่โผล่
  เมื่อเลือกตำแหน่ง, แถวที่เพิ่มแล้ว index ผิด, error ใน console ต้องใช้เบราว์เซอร์จริง (Phase 3 ของแผน)
- **ทุกอย่างที่ต้องมองด้วยตา** — layout เพี้ยน, dark mode สีผิด, ปุ่มซ้อนกัน, จอมือถือ
  ต้องใช้การเทียบภาพ (Phase 4.2)
- **เอกสารที่ระบบสร้าง** — ลายเซ็นถูกตัด, ฟิลด์ว่าง ต้อง render PDF แล้วดูระดับพิกเซล (Phase 4.3)
- **การกรอกแล้วบันทึกแล้วเปิดใหม่** — เทสนี้เปิดหน้าเปล่าอย่างเดียว ยังไม่ได้กรอกและบันทึก

---

# รอบที่ 3 — ปิด B-01/B-02/B-03 และเปิดชั้นเบราว์เซอร์

## B-01 [ปิดแล้ว] — migration รันครบทุกไฟล์บน PostgreSQL จริงแล้ว

เพิ่ม `academic_attachment` เข้า `PRE_EXISTING_SCHEMA` ในรูปร่าง **ก่อน V16**
(ยังไม่มี `checklist_item`, คอลัมน์ยาว 255) V16/V18/V20 จึงต้องพิสูจน์ว่าแก้ของจริง
พร้อมเปลี่ยนการตรวจเวอร์ชันจากรายการที่เขียนมือค้างไว้ที่ `"14"` เป็น **อ่านชื่อไฟล์
ในโฟลเดอร์ migration** — เพิ่มไฟล์ใหม่แล้วเทสตามเองโดยไม่ต้องแก้

เพิ่มการตรวจที่ไม่เคยมี: V15/V19 สร้างตารางครบ · V17 ลบ `user_file`/`user_folder` จริง ·
V18 ขยาย `checklist_item` เป็น `integer` · V20 ขยายคอลัมน์เป็น 2048/500
**ผล: 11 เทสเขียว — V15 ถึง V20 เพิ่งถูกตรวจบน PostgreSQL จริงเป็นครั้งแรก**

> **ยังค้างและต้องให้ตัดสินใจ:** ระบบยังสร้างฐานข้อมูลใหม่จากศูนย์ไม่ได้
> (ไม่มี migration ใดสร้าง `academic_attachment`) ข้อเสนอในรอบแรกที่ให้เขียน DDL ลง
> `V0__baseline.sql` **ใช้ไม่ได้** — `baseline-version=0` ทำให้ Flyway ข้าม V0 เสมอ
> แม้บนฐานเปล่า ทางที่เหลือคือแก้ V16 ให้มี `CREATE TABLE IF NOT EXISTS` นำหน้า
> (แต่ต้องทำ `flyway repair` เพราะ checksum เปลี่ยน) หรือเขียนขั้นตอน bootstrap
> ไว้ใน `docs/deployment.md` — เป็นการตัดสินใจเรื่อง deployment ไม่ใช่เรื่องโค้ด

## B-02 [ปิดแล้ว] — ด่าน .p12 มีเทสคุมครบทั้งสี่ทางออก

เพิ่ม `support/TestCertificates` (สร้าง .p12 ใช้งานได้จริงในหน่วยความจำ ทั้งแบบปกติและ
แบบหมดอายุ) และ `TestDataFactory.digitalCertificateFor(user)` ที่ติดตั้งผ่านเส้นทางจริง
`FullJourneyMockMvcTest` เดินจบเส้นได้แล้ว

**และแก้ assertion ที่หลวมจนไร้ความหมาย** — `expectAccepted(..., "/")` ใช้ `"/"` เป็นคำนำหน้า
ซึ่งเป็นคำนำหน้าของ *ทุก* path การถูกปฏิเสธจึงผ่านไปได้ ตอนนี้ตรวจว่าปลายทาง
**ไม่ใช่หน้าลงนามเดิม** ซึ่งเป็นที่ที่ระบบเด้งกลับเมื่อปฏิเสธ

เทสใหม่ `signature/DigitalIdGateTest` (4 เทส): ไม่มีใบรับรอง / ใบรับรองหมดอายุ /
รหัสผ่านผิด / ลงนามสำเร็จ — เดิมไม่มีอะไรคุมเลยแม้แต่ตัวเดียว

## B-03 [ปิดแล้ว] — เลิกล็อกถ้อยคำทั้งประโยค

เปลี่ยนจากค้นประโยคเต็มเป็นตรวจวลีสั้นสองท่อนที่เป็นแก่นของกติกา (`จำเป็นต้องแนบ`
+ `อย่างน้อย 1`) เทสจะไม่แดงอีกทุกครั้งที่มีคนขัดเกลาคำ ซึ่งเป็นพฤติกรรมที่สอนให้คน
เลิกสนใจผลเทส

---

## เปิดชั้นเบราว์เซอร์ให้รันจริงเป็นครั้งแรก

`PlaywrightTestBase` เดิมผูกกับ PostgreSQL ผ่าน Testcontainers + `@EnabledIfDockerAvailable`
และถูก `pom.xml` กันออกจาก `mvn test` โดยให้ไปรันใน `-Pe2e` ที่ CI ไม่เคยเรียก
— สามชั้นที่ทับกันจนชั้นนี้ **ไม่เคยรันจริงแม้แต่ครั้งเดียว**

**เปลี่ยนเป็น:** ใช้ H2 เหมือนเทสอื่น → รันใน `mvn test` ทุกครั้งโดยไม่ต้องมี Docker ·
เปลี่ยนชื่อเป็น `*BrowserTest` · ลบ `<exclude>**/*E2ETest.java</exclude>` ออกจาก surefire ·
แทนโปรไฟล์ `e2e` ด้วย `-Pci` ที่ตั้ง `hrcp.tools.required=true` ให้เครื่องมือที่ขาด **fail แทน skip**

### ด่านจับข้อผิดพลาดของเบราว์เซอร์อัตโนมัติ

คลาสฐานดัก `console error`, JavaScript ที่ตาย และ response ที่ ≥ 400 ของ origin ตัวเอง
แล้วทำให้เป็นความล้มเหลวของเทส — **เทสเบราว์เซอร์ทุกตัวจึงกลายเป็นเครื่องดักบั๊กในตัว
โดยคนเขียนไม่ต้องเพิ่ม assertion อะไรเลย** และมันเจอของทันทีในการรันครั้งแรก

---

## 🟠 UI-08 [แก้แล้ว] — สคริปต์ถูกโหลดซ้ำสองรอบทุกหน้าแบบฟอร์ม

```
JavaScript ตาย: SyntaxError: Identifier 'DocPreviewEngine' has already been declared
JavaScript ตาย: SyntaxError: Identifier 'SearchableSelect' has already been declared
```

`base_academic.html` โหลด `doc_preview.js` ไว้ **ท้ายหน้า** แต่แบบฟอร์มเอกสารเรียก
`initDocPreview(...)` ทันทีในเนื้อหาซึ่งอยู่ก่อนหน้านั้น **เทมเพลต 30 ไฟล์จึงต้อง
include ซ้ำเองเพื่อให้เรียกได้ทัน** เบราว์เซอร์เห็น URL ต่างกัน (`?v=3.0` กับไม่มี query)
จึงโหลดและรันทั้งสองรอบ รอบที่สองโยน SyntaxError ทิ้งไว้ทุกหน้า
เช่นเดียวกับ `searchable_select.js` ที่ `_signature_panel.html` และ `signer_settings.html` โหลดซ้ำ

**ผลกระทบ:** console เต็มไปด้วย SyntaxError ตลอดเวลา ซึ่งเป็นสภาพที่ทำให้บั๊กจริง
มองไม่เห็น — ใครเปิด DevTools มาดูก็จะเจอกองข้อผิดพลาดที่ "ปกติ" จนเลิกอ่าน

**แก้แล้ว** — ย้าย include ของ layout ขึ้นไปไว้**ก่อน**จุดแทรกเนื้อหา แล้วลบตัวซ้ำ
ในเทมเพลตทั้ง 32 จุด เหลือโหลดจุดเดียว

## 🟡 UI-07 [ยังไม่แก้ — ต้องตัดสินใจ] — CSP บล็อก Google Translate ทุกหน้า

`SecurityHeadersFilter.contentSecurityPolicy` อนุญาต `https://*.gstatic.com` ใน `img-src`
แต่**ไม่ได้อนุญาตใน `style-src`** ขณะที่ widget โหลด stylesheet จาก
`https://www.gstatic.com/_/translate_http/.../el_main_css` → **ถูกบล็อกทุกหน้า**
ปุ่มเปลี่ยนภาษาจึงแสดงผลไม่ถูกต้อง และ widget ยังยัด inline style เข้ามาอีกหลายสิบจุด
ซึ่ง `style-src` ก็บล็อกเพราะไม่มี `'unsafe-inline'`

**ทำไมยังไม่แก้:** ส่วนแรกแก้ง่ายและปลอดภัย (เติม `https://www.gstatic.com` ใน `style-src`)
แต่ส่วน inline style ต้องใส่ `'unsafe-inline'` ซึ่งเป็น**การลดความเข้มของ CSP**
เป็นการแลกเปลี่ยนด้านความปลอดภัยที่ควรให้เจ้าของระบบตัดสิน ไม่ใช่ตัดสินแทน

ระหว่างนี้ด่านจับ error กรองเสียงจาก widget ตัวนี้ออก โดยระบุเหตุผลและชี้กลับมาที่ข้อนี้ไว้ใน
`PlaywrightTestBase.isKnownTranslateWidgetNoise` — เมื่อ UI-07 ถูกแก้ ให้ลบเมธอดนั้นทิ้ง

## เทสที่กดบันทึกแล้วไม่เคยบันทึกจริง

`ScopusPickerBrowserTest.aPickedPublicationSurvivesReopeningTheForm` ล้มเพราะฟอร์ม
**ไม่เคยถูกส่ง** — `position_form_validate.js` เรียก `preventDefault()` เมื่อช่อง `required`
(`applicant_name`, `title`, `target_position`) ยังว่าง เทสกรอกแค่ตัวเดียว
การกดบันทึกจึงไม่เกิดอะไรขึ้น แล้วไปล้มที่ assertion ถัดไปโดยชี้ผิดที่

แก้ให้เทสกรอกครบเหมือนผู้ใช้จริง และให้ `saveDocumentOne()` **ยืนยันว่ามีการส่งฟอร์มจริง**
(URL ต้องเปลี่ยน และต้องไม่มีแถบเตือนค้าง) เพื่อให้ครั้งหน้าล้มตรงจุด

---

# รอบที่ 4 — ตรวจเอกสารระดับพิกเซล (รายงานเพื่อตัดสินใจ ยังไม่แก้)

สร้าง `visual/ImageDiff` และ `visual/DocumentRenderTest` ที่เดินท่อจนสุด:
`template → DOCX → PDF (LibreOffice) → ภาพ 150 dpi → นับพิกเซล`

**วิธีวัดว่าลายเซ็นถูกตัด:** ประทับภาพ **สีม่วงแดงล้วน** ซึ่งไม่มีในเอกสารจริงเลย
จึงแยกหมึกลายเซ็นออกจากตัวอักษรได้แน่นอน ภาพต้นฉบับมีแถบสีชิดขอบบนสุดและล่างสุด
ถ้าส่วนบนถูกเฉือน แถบบนจะหายทั้งแถบ และอัตราส่วน สูง:กว้าง ของกรอบหมึกจะผิดไปทันที
(เทียบเป็นอัตราส่วนจึงไม่ต้องสนใจว่าเอกสารย่อขยายภาพเท่าไร)

ประทับ **ทีละช่อง** ต่อการเรนเดอร์หนึ่งครั้ง — รอบแรกประทับครบทุกช่องพร้อมกัน
ทำให้กรอบที่วัดได้ครอบลายเซ็นหลายอันรวมกัน แล้วได้ผลลวง เช่น 189×529 พิกเซล
ซึ่งเป็นกรอบที่กินลายเซ็นสองอันที่วางซ้อนกันในแนวตั้ง

**ผลรัน: 28 เทส — 27 เทสลายเซ็นผ่านหมด, 1 เทสล้ม**

---

## ⚠️ ผลที่ต้องตัดสินใจ: เทสพิกเซลยังจับบั๊กลายเซ็นถูกตัด "ไม่ได้"

ทดลองย้อน `DocumentGenerationService.java:852` กลับเป็นค่าเดิมก่อนแก้:

```java
w:lineRule=\"atLeast\"   →   w:lineRule=\"exact\"
```

แล้วรันใหม่ — **ผลออกมาเท่าเดิมเป๊ะ: เทสลายเซ็นทั้ง 27 ตัวยังผ่านหมด**
แปลว่าเครื่องมือที่เพิ่งสร้างขึ้นมาเพื่อจับบั๊กนี้โดยเฉพาะ **ยังแยกความต่างของสองค่านี้ไม่ออก**

### สิ่งนี้บอกอะไร

`docs/PLAN-signature-crop-fix.md` วิเคราะห์สาเหตุไว้ว่า `w:lineRule="exact"` ที่ตรึงความสูง
บรรทัดไว้ 680 twips ทำให้ LibreOffice เฉือนส่วนบนของภาพออกราว 25-30%
**การวิเคราะห์นั้นตั้งขึ้นจากภาพหน้าจอที่ผู้ใช้ส่งมาเทียบกับการอ่านโค้ด ไม่เคยมีการเรนเดอร์จริง
เพื่อยืนยัน** และตอนนี้เมื่อเรนเดอร์จริงแล้ววัดพิกเซล ผลไม่สนับสนุนคำอธิบายนั้น

มีความเป็นไปได้สามทาง ซึ่งต้องเลือกทางเดินก่อนจะเชื่อผลใด ๆ:

1. **ภาพที่ใช้ทดสอบยังไม่ถึงเงื่อนไขที่ทำให้เกิดบั๊ก** — ใช้ภาพ 300×100 (กว้าง:สูง = 3:1)
   ซึ่งถูกย่อจนสูงพอดี 1.2 ซม. ตามเพดาน `SIGNATURE_MAX_HEIGHT_EMU` ลายเซ็นจริงที่มี
   เส้นตวัดสูงอาจมีสัดส่วนต่างจากนี้มากจนเป็นคนละเงื่อนไข
2. **เอกสารที่เทสครอบส่วนใหญ่ไม่ได้เดินเข้าเส้นทางที่มีปัญหา** —
   `DocumentGenerationService:711` ใช้ `stampOntoSignatureLine` (ไม่ตรึงความสูง) เมื่อ
   เทมเพลตมีเส้นจุดประ "ลงชื่อ ......" ส่วน `:721` ที่ใช้ `withFixedSignatureHeight`
   เดินเมื่อไม่มีเส้นจุดประเท่านั้น ยังไม่ได้นับว่าจาก 27 ช่องมีกี่ช่องที่เข้าเส้นทางหลัง
3. **การวิเคราะห์ใน PLAN ไม่ตรงกับสาเหตุจริง** — อาการที่ผู้ใช้เห็นอาจมาจากเรื่องอื่น
   เช่น การย่อภาพ, ระยะขอบใน template, หรือ LibreOffice คนละเวอร์ชันกับที่ผู้ใช้เปิดดู

### ที่ต้องตัดสินใจ

- **จะยืนยันด้วยลายเซ็นจริงไหม** — ถ้ามีไฟล์ภาพลายเซ็นตัวที่ผู้ใช้รายงานว่าถูกตัด
  พร้อมภาพหน้าจอเอกสารที่ออกมา จะชี้ได้ทันทีว่าเป็นข้อ 1 หรือข้อ 3
- **จะนับก่อนไหมว่าเอกสารไหนเดินเส้นทาง `withFixedSignatureHeight` จริง** —
  ถ้าเป็นเพียงไม่กี่ฉบับ ควรโฟกัสเทสไปที่ฉบับเหล่านั้นด้วยสัดส่วนภาพที่สมจริง
- **การแก้เป็น `atLeast` ที่ค้างอยู่ใน working tree จะเก็บไว้ไหม** — ตอนนี้
  **ไม่มีหลักฐานว่ามันแก้อะไร และไม่มีหลักฐานว่ามันทำอะไรเสีย** เทสทั้ง 27 ตัวผ่านทั้งสองแบบ

> ระหว่างที่ยังไม่ยืนยัน **ไม่ควรถือว่า `docs/PLAN-signature-crop-fix.md` ปิดงานแล้ว**
> เทสที่ผ่านโดยไม่สามารถ fail ได้ ไม่ได้ให้ความมั่นใจอะไรเลย — เป็นสภาพเดียวกับ
> `SignatureStampingTest` เดิมที่ยืนยันสตริงซึ่งเป็นตัวการเสียเอง

---

## สิ่งที่เทสรอบนี้ยืนยันได้จริง

แม้จะยังไม่ discriminate เรื่อง lineRule แต่ชุดนี้ครอบสิ่งที่ไม่เคยมีอะไรตรวจมาก่อน:

| ตรวจอะไร | ผล |
|---|---|
| ทุกเอกสารที่ลงนามได้ แปลงเป็น PDF สำเร็จ (27 ช่องลงนาม × ทุกฉบับ) | ผ่าน |
| ลายเซ็นที่ประทับไป **มองเห็นจริง**ในหน้า PDF ที่เรนเดอร์ออกมา | ผ่าน |
| ไม่มี `{{` หรือ `${` หลงเหลือในเอกสารที่ออกไปถึงผู้ใช้ | ผ่าน |

เดิม `DocumentPdfPreviewTest` ตรวจแค่ว่าไฟล์ขึ้นต้นด้วย `%PDF-` และยาวเกิน 1000 ไบต์
ซึ่งผ่านได้แม้เอกสารจะว่างเปล่าหรือลายเซ็นหายไปทั้งอัน

## เทสที่ล้มอยู่ 1 ตัว — เป็นบั๊กของเทสที่ผมเพิ่งเขียนเอง ไม่ใช่ของระบบ

`noPlaceholderSurvivesIntoThePdf` ล้มที่ `.contains("ศ.ดร.ทดสอบ หนึ่ง")`
**ตรวจแล้วว่าค่าอยู่ในเอกสารจริงครบทั้งสามชื่อ** — assertion เข้มเกินไปเรื่องข้อความต้องติดกัน
ขณะที่การสกัดข้อความจาก PDF แทรกการขึ้นบรรทัดคั่นระหว่างคำนำหน้ากับชื่อ

**ยังไม่แก้ตามที่สั่ง** ชุดเทสจึงแดงอยู่ 1 ตัวจากไฟล์ที่เพิ่มเข้ามารอบนี้เอง
แก้ได้ด้วยการตรวจทีละส่วนของชื่อแทนการตรวจสตริงเต็ม

---

## ยังไม่ได้ทำในเฟสนี้

**การเทียบภาพหน้าจอ (4.2)** — light/dark × เดสก์ท็อป/มือถือ ยังไม่ได้สร้าง
เพราะควรรู้ผลการตัดสินใจเรื่องข้างบนก่อน ว่าจะลงทุนกับชั้นเทียบภาพในรูปแบบไหน
`ImageDiff` ที่สร้างไว้แล้วใช้ร่วมกันได้ทันทีเมื่อตัดสินใจแล้ว

---

## ทำไมบั๊กพวกนี้ถึงหลุดมาถึงการเทสมือ

ทั้งสามข้อมีรากเดียวกัน — **ชุดเทสไม่ได้ถูกรันในจังหวะที่ควรรัน**:

- [.github/workflows/deploy.yml](../../.github/workflows/deploy.yml) รันเทสเฉพาะตอน push เข้า branch `deploy`
  → งานที่ยังไม่ถึงขั้น deploy ไม่มีอะไรตรวจ
  (แก้แล้วด้วย `ci.yml` ที่รันเทสครบทุกชั้นเมื่อ push ขึ้น branch `test`)
- ชั้น migration ติด `@EnabledIfDockerAvailable` ที่ **skip เงียบ** เมื่อ Docker ไม่เปิด
  → error 9 ข้อของ B-01 ถูกกลบเป็น "skipped" มาตลอด
- ชั้นเบราว์เซอร์ถูก `<exclude>**/*E2ETest.java</exclude>` ใน surefire และ CI ไม่เคยส่ง `-Pe2e`
  → ไม่เคยรันเลยสักครั้งใน CI

รายละเอียดเชิงโครงสร้างและแผนปิดช่องว่างทั้งหมด อยู่ในแผนที่อนุมัติแล้ว
(`~/.claude/plans/users-kriangkrai-developer-projects-ecl-gleaming-wall.md`) — ข้อ B-01/B-02/B-03
คือรายการงานของ Phase 0

---

## ยังไม่ได้ตรวจ

- **ไม่ได้ยืนยันบั๊กลายเซ็นถูกตัดด้วยภาพจริง** — `SignatureStampingTest` ที่แก้เป็น `atLeast`
  ค้างอยู่ใน working tree และผ่าน แต่เป็นการตรวจ **สตริง XML** ไม่ใช่พิกเซลใน PDF
  จึงยังไม่มีหลักฐานว่าปัญหาใน `docs/PLAN-signature-crop-fix.md` หายจริง (Phase 4.3 ของแผน)
- **ชั้นเบราว์เซอร์** — `FullJourneyE2ETest` / `ScopusPickerE2ETest` ไม่ได้รันในรอบนี้
  (ถูก exclude ออกจาก `mvn test`) จึงยังไม่ทราบสถานะจริง
- **skipped 7 ตัวที่เหลือ** ยังไม่ได้ไล่ดูว่าแต่ละตัวถูกข้ามด้วยเหตุผลอะไร
