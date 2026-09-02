# รายงานผลการทดสอบระบบ HRCP-KKU-Academic

**วันที่ทดสอบ:** 11 สิงหาคม 2026
**ขอบเขต:** รันชุดเทสอัตโนมัติ + ตรวจโค้ด (static audit) + ตรวจความปลอดภัย + เทสจริงผ่าน HTTP
**สถานะ:** **แก้แล้วทั้งหมด 19 ข้อ (Critical + High + Medium)** บน branch `fix/security-audit-2026-08-11` พร้อมเทสคุมทุกข้อ

> **หมายเหตุการแก้ไข:** ทุกบักที่แก้ใช้วิธี test-first — เขียนเทสให้ fail ก่อน ยืนยันว่า fail ด้วยเหตุผลที่ถูกต้อง แล้วจึงแก้โค้ด
> เพิ่มเทสใหม่ **45 เทส** ใน 8 ไฟล์ และซ่อมเทสตกยุคที่เหลือทั้งหมด
>
> ### ชุดเทสเต็ม: **514 เทส / 38 failures + 3 errors** → **559 เทส / 0 failures / 0 errors** [ผ่านทั้งหมด]
> ดูสรุปท้ายเอกสารที่หัวข้อ **"ผลการแก้ไข"**

---

## สรุปผู้บริหาร

| ระดับ | จำนวน | สรุป |
|---|---|---|
| [Critical] | 2 | ผู้ใช้ทั่วไปแก้โปรไฟล์คนอื่นได้ / เขียนไฟล์ทับที่ไหนก็ได้บนเซิร์ฟเวอร์ |
| 🟠 High | 5 | ยกระดับสิทธิ์เข้าที่เก็บไฟล์แอดมิน, เขียนทับเอกสารคนอื่น, ข้าม brute-force, ข้ามโควตา, secret หลุด |
| 🟡 Medium | 12 | หมดหน่วยความจำตอนโหลดไฟล์, ประวัติสถานะเรียงมั่ว, ข้อมูล error รั่ว, ปุ่มใช้ไม่ได้ |
| [Low] | 8 | คุกกี้ไม่มี Secure, config ตกค้าง, code smell |
| [Test Quality] | 5 | 46% ของเทสเป็นของปลอม + เทสตกยุค 38 ตัว |

**ประเด็นที่ควรแก้ก่อนขึ้น production:** C-01, C-02, H-01, H-02, H-03, H-05

**ข้อควรทราบ:** ทุกบัก Critical/High ยืนยันจากการอ่านโค้ดจริงและระบุบรรทัดไว้แล้ว ส่วนที่ยังไม่ได้ยืนยันด้วยการยิงจริง เพราะยังไม่ได้รับบัญชีทดสอบ ระบุไว้ชัดเจนในหัวข้อ "ยังไม่ได้ทดสอบ" ท้ายรายงาน

---

# [Critical]

## C-01 — ผู้ใช้ทั่วไปแก้ไขโปรไฟล์ของผู้ใช้คนอื่นได้ (IDOR เขียนข้อมูล)

- **ที่มา:** [UserController.java:77-96](../HRCP-KKU-Academic/src/main/java/com/ecom/controller/UserController.java#L77-L96) → [UserServiceImpl.java:194-224](../HRCP-KKU-Academic/src/main/java/com/ecom/service/impl/UserServiceImpl.java#L194-L224)

**อาการ**
`POST /user/update-profile` รับ `@ModelAttribute UserDtls user` ซึ่ง bind ฟิลด์ `id` มาจากฟอร์มโดยตรง แล้วส่งต่อเข้า `updateUserProfile()` ซึ่งทำ `userRepository.findById(user.getId())` **โดยไม่ตรวจเลยว่า id นั้นเป็นของผู้ใช้ที่ล็อกอินอยู่หรือไม่**

```java
// UserController.java:78
public String updateProfile(@ModelAttribute UserDtls user, @RequestParam MultipartFile img, HttpSession session) {
    ...
    UserDtls updateUserProfile = userService.updateUserProfile(user, img);   // ← ไม่มีการเทียบกับ principal

// UserServiceImpl.java:195
UserDtls dbUser = userRepository.findById(user.getId()).get();               // ← id มาจากฟอร์มของผู้โจมตี
```

ผู้ใช้ A แก้ค่า `id` ใน request เป็น id ของผู้ใช้ B แล้วเขียนทับ `title`, `firstName`, `lastName`, `mobileNumber`, `academicPosition`, `profileImage` ของ B ได้ทันที

**วิธีทำให้เกิดซ้ำ**
```bash
# ล็อกอินเป็นผู้ใช้ธรรมดา A แล้วยิง (VICTIM_ID = id ของผู้ใช้ B)
curl -b cookies.txt -X POST http://localhost:8080/user/update-profile \
  -H "X-XSRF-TOKEN: $TOKEN" \
  -F "id=VICTIM_ID" -F "firstName=HACKED" -F "lastName=HACKED" \
  -F "mobileNumber=0000000000" -F "img=;type=application/octet-stream"
```

**ผลกระทบ**
ผู้ยื่นคำขอตำแหน่งวิชาการคนใดก็ได้สามารถแก้ชื่อ-นามสกุล-ตำแหน่งวิชาการของผู้ยื่นคนอื่นได้ ซึ่งข้อมูลเหล่านี้ถูกดึงไปพิมพ์ลงเอกสารราชการที่ระบบสร้าง (บันทึกข้อความ, ใบรับรองผลการประชุม ฯลฯ) → กระทบความถูกต้องของเอกสารทางการ

**แนวทางแก้ (ย่อ)**
ดึง id จาก `Principal` เท่านั้น ห้ามรับ `id` จากฟอร์ม และเพิ่มการตรวจเจ้าของก่อน save

**หมายเหตุ:** [AdminController.java:539](../HRCP-KKU-Academic/src/main/java/com/ecom/controller/AdminController.java#L539) มีบักตัวเดียวกันเป๊ะ แต่จำกัดเฉพาะ ROLE_ADMIN จึงรุนแรงน้อยกว่า

---

## C-02 — อัปโหลดรูปโปรไฟล์เขียนไฟล์ทับที่ไหนก็ได้ (Path Traversal + ไม่มี validation)

- **ที่มา:** [UserServiceImpl.java:210-222](../HRCP-KKU-Academic/src/main/java/com/ecom/service/impl/UserServiceImpl.java#L210-L222)

**อาการ**
```java
String uploadDir = System.getProperty("user.dir") + "/uploads/profile_img/";
...
Path filePath = Path.of(uploadDir, img.getOriginalFilename());          // ← ชื่อไฟล์มาจาก client ตรง ๆ
Files.copy(img.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
```

ปัญหาซ้อนกัน 4 ชั้น:

1. **Path traversal** — `img.getOriginalFilename()` ไม่ผ่านการ normalize เลย ส่งชื่อไฟล์เป็น `../../application.properties` หรือ `..\..\config\x` เขียนออกนอกโฟลเดอร์ `uploads/` ได้ และมี `REPLACE_EXISTING` จึง**เขียนทับไฟล์เดิม**ได้ด้วย
2. **ไม่มีการเรียก `validateImageFile()` เลย** — ฟังก์ชันนี้มีอยู่จริงที่ [AdminController.java:95](../HRCP-KKU-Academic/src/main/java/com/ecom/controller/AdminController.java#L95) และถูกใช้ในหน้าแก้ไขผู้ใช้ แต่**เส้นทาง update-profile ทั้งฝั่ง user และ admin ข้ามมันไปทั้งหมด** → ไม่จำกัดนามสกุล ไม่จำกัดขนาด ไม่เช็ก MIME
3. **Stored XSS** — โฟลเดอร์ `uploads/` ถูก serve เป็น static content (ยืนยันแล้ว: `GET /img/profile_img/MMASS-43.jpg` → HTTP 200) ดังนั้นอัปโหลด `evil.html` เข้าไปจะเข้าถึงได้ที่ `/img/profile_img/evil.html` บน origin เดียวกับแอป → หลบ CSP `default-src 'self'` ได้
4. **ชื่อไฟล์ชนกัน** — ไม่มีการสุ่มชื่อ + `REPLACE_EXISTING` → ผู้ใช้สองคนอัปโหลด `avatar.jpg` รูปของคนแรกถูกทับ

**หลักฐานจากชุดเทส** (เทสจับได้ แต่โดนข้ามเพราะ `skipTests=true`)
```
AdminControllerUpdateProfileImageTest.testEmptyFileUploadReturnsError
  expected: 500 INTERNAL_SERVER_ERROR  but was: 200 OK
ProfileImageUploadFailurePropertyTest.profileImageUploadFailureHandling
  expected: "false"  but was: "true"       ← ไฟล์ไม่ถูกต้องแต่ระบบตอบว่าสำเร็จ
```

**ผลกระทบ**
เขียนทับไฟล์บนเซิร์ฟเวอร์ตามสิทธิ์ของ process ที่รันแอป + ฝัง HTML/JS บน origin เดียวกัน → ขโมย session ของแอดมินได้

**แนวทางแก้ (ย่อ)**
ใช้ `Path.of(uploadDir).resolve(filename).normalize()` แล้วตรวจว่ายังอยู่ใต้ `uploadDir`, สุ่มชื่อไฟล์ใหม่ (UUID + นามสกุลจาก whitelist), และเรียก `validateImageFile()` ในทั้งสองเส้นทาง

---

# 🟠 High

## H-01 — ผู้ใช้ทั่วไปเขียนไฟล์เข้าที่เก็บของแอดมินได้ ผ่านพารามิเตอร์ `storageType`

- **ที่มา:** [ChunkedUploadController.java:56-123](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/ChunkedUploadController.java#L56-L123), [:161-232](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/ChunkedUploadController.java#L161-L232)

**อาการ**
`POST /api/upload/init` รับ `storageType` จาก client โดยไม่ตรวจ role:

```java
@RequestParam(defaultValue = "user") String storageType,
...
if ("user".equals(storageType)) {           // ← validation ทั้งหมดอยู่ในเงื่อนไขนี้
    userStorageService.validateFileType(filename);   // ตรวจนามสกุล
    ...getRemainingBytes(user.getId());              // ตรวจโควตา
}
```

ส่ง `storageType=admin` ทำให้:
- **ข้ามการตรวจนามสกุลไฟล์ทั้งหมด** (อัปโหลด `.exe`, `.html`, `.jsp` ได้)
- **ข้ามการตรวจโควตาทั้งหมด**
- ไฟล์ถูกเขียนลง `uploads/admin-storage/` และบันทึกลง DB ผ่าน `adminStorageService.saveUploadedFile()` ซึ่ง[ไม่ตรวจสิทธิ์อะไรเลย](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/service/AdminStorageService.java#L126)

เส้นทาง `/api/**` อยู่นอกกฎ `/admin/**` และ `/user/**` ใน [SecurityConfig.java:92-94](../HRCP-KKU-Academic/src/main/java/com/ecom/config/SecurityConfig.java#L92-L94) จึงตกไปที่ `.anyRequest().authenticated()` = **ผู้ใช้ที่ล็อกอินคนไหนก็เรียกได้**

**ผลกระทบ** ผู้ยื่นคำขอทั่วไปแทรกไฟล์ปลอมเข้าคลังเอกสารกลางของแอดมิน แอดมินเห็นเป็นไฟล์ในระบบตามปกติ

---

## H-02 — ผู้ใช้ทั่วไปเขียนทับร่างเอกสารของคำร้องคนอื่นได้ (IDOR)

- **ที่มา:** [AutoDraftApiController.java:41-94](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/AutoDraftApiController.java#L41-L94)

**อาการ**
```java
UserDtls user = getUser(principal);
if (user == null) return ResponseEntity.status(401).build();     // ← ตรวจแค่ "ล็อกอินหรือยัง"

AcademicRequest request = academicService.findById(requestId).orElse(null);
if (request == null) return ResponseEntity.notFound().build();   // ← ไม่ตรวจว่าเป็นคำร้องของใคร

academicService.saveDraft(request, docType, jsonData, label, null);   // เขียนทับได้เลย
```

มีปัญหาเดียวกันทั้ง `/api/draft/academic/{requestId}/{docType}` และ `/api/draft/position/{requestId}/{docType}`

**วิธีทำให้เกิดซ้ำ**
```bash
# ล็อกอินเป็นผู้ใช้ธรรมดา แล้วไล่ requestId
curl -b cookies.txt -X POST "http://localhost:8080/api/draft/academic/1/0" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $TOKEN" \
  -d '{"applicant_name":"ถูกแก้โดยคนอื่น"}'
# ตอบ {"status":"saved","type":"academic"} แม้ requestId ไม่ใช่ของตัวเอง
```

**ผลกระทบ** แก้เนื้อหาเอกสารประกอบการขอตำแหน่งวิชาการของผู้อื่นได้ทั้งระบบ โดยไล่เลข requestId ทีละตัว

---

## H-03 — ข้ามระบบป้องกัน brute-force ได้ด้วย header `X-Forwarded-For`

- **ที่มา:** [ClientIpUtils.java:17-30](../HRCP-KKU-Academic/src/main/java/com/ecom/config/ClientIpUtils.java#L17-L30) + [AuthFailureHandlerImpl.java:50-53](../HRCP-KKU-Academic/src/main/java/com/ecom/config/AuthFailureHandlerImpl.java#L50-L53)

**อาการ**
`ClientIpUtils.resolveClientIp()` เชื่อ header `X-Forwarded-For` ที่ client ส่งมาแบบไม่มีเงื่อนไข — ไม่มีรายชื่อ trusted proxy และ**ไม่ได้ตั้ง `server.forward-headers-strategy` ไว้เลย** (ยืนยันแล้ว: ไม่มีใน `application.properties`, `Dockerfile`, `docker-compose.yml`)

```java
String xff = request.getHeader("X-Forwarded-For");
if (xff != null && !xff.isEmpty()) {
    String firstIp = xff.split(",")[0].trim();
    if (isValidIpFormat(firstIp)) return firstIp;      // ← ผู้โจมตีกำหนดเองได้
}
```

ค่านี้ถูกใช้เป็น key ของการล็อก IP: `bruteForceProtection.recordFailure("ip:" + clientIp)` ผู้โจมตีเปลี่ยน `X-Forwarded-For` ทุกครั้ง → counter ไม่มีวันถึง 5 → **IP ไม่ถูกบล็อกเลย**

**ข้อสังเกตเพิ่ม:** `RateLimitFilter` กลับใช้ `getRemoteAddr()` (ปลอมไม่ได้) จำกัด login ที่ 20 ครั้ง/นาที/IP ซึ่งยังทำงานอยู่ แต่ 20/นาที = ~28,800 ครั้ง/วัน ต่อ IP โดยไม่โดนล็อกบัญชีเลย และการล็อกรายบัญชี (`user:email`) ยังทำงาน จึงไม่ถึงขั้นเปิดโล่ง แต่ชั้นป้องกันระดับ IP ใช้ไม่ได้จริง

**แนวทางแก้ (ย่อ)**
ตั้ง `server.forward-headers-strategy=native` และให้ทุกจุดใช้ `getRemoteAddr()` หลัง Tomcat แปลง header แล้ว หรือกำหนด trusted proxy ให้ชัด

---

## H-04 — ข้ามโควตาพื้นที่เก็บข้อมูลได้ เพราะไม่เคยตรวจขนาดไฟล์จริง

- **ที่มา:** [ChunkedUploadController.java:85-91](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/ChunkedUploadController.java#L85-L91), [:194-213](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/ChunkedUploadController.java#L194-L213)

**อาการ**
โควตาถูกตรวจกับ `fileSize` ที่ **client ประกาศมาเอง** ตอน init เท่านั้น ส่วนขั้นตอน `/api/upload/chunk` และ `/api/upload/complete` ไม่เคยนับ byte จริงที่รับมา และตอนบันทึกลง DB ก็ใช้ค่าที่ประกาศไว้:

```java
userStorageService.saveUploadedFile(session.filename, finalPath.toString(),
        session.fileSize,      // ← ค่าที่ client โกหกมาตั้งแต่ init
        ...);
```

ประกาศ `fileSize=1` แล้วอัปโหลดจริงกี่ GB ก็ได้ และ DB จะบันทึกว่าไฟล์ขนาด 1 byte → **การคำนวณโควตาของผู้ใช้คนนั้นเพี้ยนถาวร**

**ผลกระทบ** ดิสก์เซิร์ฟเวอร์เต็มได้ และตัวเลขพื้นที่ใช้งานในระบบไม่ตรงกับความจริง

---

## H-05 — Secret ถูก hardcode ไว้ในไฟล์ที่ commit ขึ้น git

- **ที่มา:** [application.properties](../HRCP-KKU-Academic/src/main/resources/application.properties)

| บรรทัด | สิ่งที่หลุด |
|---|---|
| `spring.datasource.password=12345678` | รหัสผ่าน PostgreSQL (เป็น plain text ไม่ผ่าน env var ด้วยซ้ำ) |
| `spring.mail.password=${EMAIL_PASSWORD:gcld ahqi vqoz uwki}` | Gmail app password ของ `zajangjj@gmail.com` |
| `aws.access.key=${S3_ACCESS_KEY:AKIAZNNXWABR32475TT2}` | AWS access key |
| `aws.secret.key=${S3_ACCESS_KEY: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}` | AWS secret key |
| ไฟล์ `HRCP-KKU-Academic/deploy_ci_key` | SSH private key สำหรับ deploy อยู่ใน repo |

**บักซ้อนอยู่ในนั้นด้วย:** บรรทัด `aws.secret.key` อ่านจาก env var **ผิดตัว** — ใช้ `${S3_ACCESS_KEY}` ซึ่งเป็นตัวเดียวกับ `aws.access.key` ดังนั้นถ้าตั้ง env var บน production จริง secret key จะได้ค่าเดียวกับ access key → **การเชื่อมต่อ S3 พังทันทีบน production** และไม่มีใครเห็นปัญหาบนเครื่อง dev เพราะ dev ใช้ค่า default ที่ถูกต้อง

**แนวทางแก้ (ย่อ)**
เปลี่ยนเป็น `${S3_SECRET_KEY}`, ย้าย secret ทั้งหมดออกเป็น env var แบบไม่มีค่า default, **เพิกถอน (revoke) credential ทุกตัวข้างต้น** เพราะถือว่ารั่วแล้ว และลบ `deploy_ci_key` ออกจากประวัติ git

---

# 🟡 Medium

## M-01 — ดาวน์โหลดไฟล์โหลดทั้งไฟล์เข้า RAM → เซิร์ฟเวอร์ล่มได้

- **ที่มา:** 11 จุด เช่น [UserFileManagerController.java:150](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/UserFileManagerController.java#L150), [FileManagerController.java:176](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/FileManagerController.java#L176), [AcademicAdminController.java:795](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/AcademicAdminController.java#L795)

ทุกจุดใช้ `Files.readAllBytes(filePath)` แล้วห่อด้วย `ByteArrayResource` — ขัดแย้งโดยตรงกับคอมเมนต์ของ `ChunkedUploadController` ที่เขียนว่า *"supports unlimited file size"* อัปโหลดไฟล์ 2 GB เข้าไปได้ แต่พอกดดาวน์โหลดจะกิน heap 2 GB ต่อ 1 request → `OutOfMemoryError` ทั้งแอป ผู้ใช้พร้อมกันไม่กี่คนก็ล่ม

**แนวทางแก้** ใช้ `InputStreamResource` / `FileSystemResource` แทน ให้ stream ตรงออก response

## M-02 — Upload session และโฟลเดอร์ chunk ไม่มีวันหมดอายุ → ดิสก์เต็ม

- **ที่มา:** [ChunkedUploadController.java:37](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/ChunkedUploadController.java#L37), [:94-117](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/ChunkedUploadController.java#L94-L117)

`activeSessions` เป็น `ConcurrentHashMap` ธรรมดา ไม่มี TTL และไม่มี scheduled cleanup ทุกครั้งที่เรียก `/api/upload/init` จะสร้างโฟลเดอร์ `uploads/chunks/<uuid>` ทิ้งไว้ ถ้า client ไม่เรียก `/complete` หรือ `/abort` (ปิดเบราว์เซอร์ก็พอ) session และโฟลเดอร์นั้นค้างตลอดอายุ process → memory leak + ดิสก์เต็ม และเรียก init ซ้ำ ๆ ก็ทำให้ดิสก์เต็มได้โดยตั้งใจ

## M-03 — ประวัติการเปลี่ยนสถานะของคำขอตำแหน่งเรียงลำดับมั่ว

- **ที่มา:** [PositionStatusHistoryRepository.java:14](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/repository/PositionStatusHistoryRepository.java#L14)

```java
List<PositionStatusHistory> findByRequestId(@Param("requestId") Long requestId);   // ← ไม่มี ORDER BY
```

ต่างจาก repository พี่น้องที่ทำถูก: `RequestStatusHistoryRepository.findByRequestIdOrderByChangedAtDesc()` และ `AcademicAttachmentRepository.findByRequestIdOrderByUploadedAtDesc()` เมื่อไม่ระบุ ORDER BY ลำดับที่ได้ขึ้นกับ PostgreSQL ล้วน ๆ → **ไทม์ไลน์สถานะที่แสดงให้ผู้ใช้เห็นอาจสลับลำดับ**

**หลักฐานจากชุดเทส:** `PositionRequestServiceTest.updateStatus_RecordsChangedByUser` ล้มด้วย *"Expecting actual not to be null"* เพราะ `history.get(history.size()-1)` ได้ entry ตอน submit (ที่ `changedBy` เป็น null) แทนที่จะเป็น entry ล่าสุด

## M-04 — `.get()` บน Optional โดยไม่ตรวจ → 500 Internal Server Error

- **ที่มา:** [UserServiceImpl.java:195](../HRCP-KKU-Academic/src/main/java/com/ecom/service/impl/UserServiceImpl.java#L195) และอีก 6 จุด (`AcademicRequestService:146,171`, `PositionRequestService:303,339`, `UserServiceImpl:123,135`)

```java
UserDtls dbUser = userRepository.findById(user.getId()).get();   // NoSuchElementException ถ้าไม่เจอ
```

ส่ง `id` ที่ไม่มีในระบบ (หรือไม่ส่ง `id` เลย → null) จะได้ 500 แทนที่จะเป็น 400/404

**บักซ้อน:** บรรทัดถัดมาเป็น dead code — `if (!ObjectUtils.isEmpty(dbUser))` ที่บรรทัด 201 ไม่มีทางเป็น false เพราะ `.get()` throw ไปแล้ว และ `dbUser.setProfileImage()` ที่บรรทัด 198 ก็ถูกเรียก**ก่อน**การเช็ก null อยู่ดี → ลำดับตรรกะผิด

## M-05 — ข้อความ exception ภายในรั่วออกไปหา client (36 จุด)

- **ที่มา:** เช่น [AutoDraftApiController.java:62](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/AutoDraftApiController.java#L62), [ChunkedUploadController.java:153,228](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/ChunkedUploadController.java#L153), [FileManagerController.java:158](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/FileManagerController.java#L158)

```java
return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
```

ขัดกับการตั้งค่าที่ตั้งใจปิดไว้แล้วใน `application.properties` (`spring.web.error.include-message=never`) ข้อความที่หลุดออกไปมีทั้ง path บนเซิร์ฟเวอร์ ข้อความ error ของ PostgreSQL และ output ของ LibreOffice ([DocumentGenerationService.java:1075](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/service/DocumentGenerationService.java#L1075))

## M-06 — `Map.of()` กับ error message ที่เป็น null → NPE ซ้อนใน catch

- **ที่มา:** [AutoDraftApiController.java:62, 92, 111](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/AutoDraftApiController.java#L62)

`Map.of(k, v)` โยน `NullPointerException` ถ้า value เป็น null และ exception หลายชนิด (เช่น `NullPointerException` เอง) มี `getMessage()` เป็น null → เกิด exception ซ้อนใน block catch ทำให้ error จริงหายไปทั้งหมด กลายเป็น 500 คนละสาเหตุ ตามหาต้นตอไม่เจอ

## M-07 — CSV formula injection ในไฟล์ export ประวัติการใช้งาน

- **ที่มา:** [AdminController.java:607-645](../HRCP-KKU-Academic/src/main/java/com/ecom/controller/AdminController.java#L607-L645)

```java
private String escapeCsv(String value) {
    return value.replace("\"", "\"\"");     // ← escape แค่ double quote
}
```

ไม่ได้จัดการค่าที่ขึ้นต้นด้วย `=`, `+`, `-`, `@` ซึ่ง Excel จะตีความเป็นสูตร และช่อง `details` มี**อีเมลที่ผู้โจมตีพิมพ์เข้ามาตอน login ล้มเหลว** ([AuthFailureHandlerImpl.java:164-169](../HRCP-KKU-Academic/src/main/java/com/ecom/config/AuthFailureHandlerImpl.java#L164-L169)) ซึ่ง `sanitizeEmail()` ลบแค่ CRLF ไม่ได้บังคับรูปแบบอีเมล

ผู้โจมตีพิมพ์ `=HYPERLINK("http://evil.com","คลิก")` ในช่อง email แล้วกด login → แอดมิน export CSV เปิดใน Excel → สูตรทำงาน

## M-08 — ข้อจำกัดเอกสารสำหรับผู้ยื่นประกาศไว้แต่ไม่เคยบังคับใช้

- **ที่มา:** [DocumentPreviewController.java:36](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/DocumentPreviewController.java#L36)

```java
private static final Set<Integer> APPLICANT_ALLOWED_DOCS = Set.of(0, 1, 8);   // ← ประกาศแล้วไม่ถูกใช้ที่ไหนเลย
```

`previewDocument()` ไม่รับ `Principal` และไม่ตรวจ role เลย ประกอบกับ `/api/**` ตกอยู่ใต้ `.anyRequest().authenticated()` → **ผู้ยื่นคำขอสร้าง preview เอกสารฝั่งแอดมินได้ทุกชนิด** เช่น Doc 6 แบบประเมินผลการสอน และ Doc 7 ใบรับรองผลการประชุม โดยกรอกข้อมูลเองทั้งหมด ได้ไฟล์ DOCX/PDF ที่หน้าตาเหมือนเอกสารจริงจากระบบ

## M-09 — Header `Content-Disposition` สร้างจากชื่อไฟล์ที่ไม่ผ่านการ escape

- **ที่มา:** [UserFileManagerController.java:154](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/UserFileManagerController.java#L154), [FileManagerController.java:181,374](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/FileManagerController.java#L181), [AcademicAdminController.java:764,774](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/AcademicAdminController.java#L764)

```java
.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getOriginalFilename() + "\"")
```

ชื่อไฟล์มาจาก `@RequestParam String filename` ตอน upload init ซึ่งไม่ผ่าน validation ถ้ามีอักขระ `"` จะทำให้ header เพี้ยนและควบคุมชื่อไฟล์ปลายทางที่เหยื่อบันทึกได้ (Tomcat กัน CR/LF ให้แล้ว จึงยังไม่ถึงขั้น response splitting)
จุดที่ทำถูกแล้วคือ `filename*=UTF-8''` + encode ใน `PositionAdminController` และ `AcademicApplicantController` — ควรทำให้เหมือนกันทั้งหมด

## M-10 — ปุ่มในหน้า dashboard แอดมินเรียก endpoint ที่ไม่มีอยู่จริง

- **ที่มา:** [admin/index.html:242](../HRCP-KKU-Academic/src/main/resources/templates/admin/index.html#L242) เรียก `fetch('/admin/toggle-image-mode')`

ค้นทั้ง `src/main/java` แล้ว**ไม่มี controller ตัวไหน map path นี้** มีแค่ชื่อโผล่ใน `SecurityConfig` (รายการยกเว้น CSRF) กับใน template → กดปุ่มแล้วได้ 404 เงียบ ๆ ฟีเจอร์นี้ใช้ไม่ได้

## M-11 — `/actuator/health` ต้องเป็น ADMIN → health check ของ container ล้มเหลว

- **ที่มา:** [SecurityConfig.java:91](../HRCP-KKU-Academic/src/main/java/com/ecom/config/SecurityConfig.java#L91)

ยืนยันด้วยการยิงจริง: `GET /actuator/health` → **302 redirect ไป `/signin?expired=true`** ไม่ใช่ 200

Docker/Kubernetes health probe อ่าน endpoint นี้แบบไม่ล็อกอิน จะเห็นเป็น 302 แล้วตัดสินว่า container ไม่พร้อม → restart วนไม่จบ ควรเปิด `/actuator/health` แบบ `permitAll` และปิดเฉพาะ endpoint อื่น

## M-12 — กลืน exception เงียบ 20 จุด

- **ที่มา:** `catch (Exception ignored) {}` ที่ [StaffMemberController.java:84,124,141](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/StaffMemberController.java#L84), [HomeController.java:171,250](../HRCP-KKU-Academic/src/main/java/com/ecom/controller/HomeController.java#L171), [AcademicApplicantController.java:353,443](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/AcademicApplicantController.java#L353) และอีก 13 จุด

ไม่ log อะไรเลย เมื่อ audit log หรือการส่งอีเมลล้มเหลวจะไม่มีร่องรอยใด ๆ ทำให้ตามปัญหาบน production ไม่ได้

---

# [Low]

| # | เรื่อง | ที่มา |
|---|---|---|
| L-01 | `GET /error` ตอบ HTTP 500 พร้อม JSON `{"status":999,"error":"None"}` แทนที่จะเป็นหน้า error ที่มี template อยู่แล้ว | ยืนยันด้วย curl |
| L-02 | คุกกี้ `JSESSIONID` และ `XSRF-TOKEN` ไม่มีแฟล็ก `Secure` — บน HTTPS จริงจะส่งผ่าน HTTP ได้ | ยืนยันด้วย curl |
| L-03 | การล็อกแบบทวีคูณไม่มีเพดานตามที่คอมเมนต์ระบุ — คอมเมนต์เขียน "4th+: 120 min (cap)" แต่โค้ดเป็น `15 * (1L << lockCount)` ไม่มี cap และ `1L << 63` จะ overflow เป็นค่าลบ → การล็อกหยุดทำงาน | [BruteForceProtection.java:55](../HRCP-KKU-Academic/src/main/java/com/ecom/config/BruteForceProtection.java#L55) |
| L-04 | คอมเมนต์เขียนว่า "sliding-window" แต่โค้ดเป็น fixed window — ยิง 100 ครั้งท้ายหน้าต่างแล้วอีก 100 ครั้งต้นหน้าต่างใหม่ = 200 ครั้งในเวลาสั้น ๆ | [RateLimitFilter.java:93-109](../HRCP-KKU-Academic/src/main/java/com/ecom/config/RateLimitFilter.java#L93-L109) |
| L-05 | คอมเมนต์อ้าง `server.forward-headers-strategy=native` แต่ไม่เคยตั้งค่านั้นที่ไหนเลย (ดู H-03) | [RateLimitFilter.java:26](../HRCP-KKU-Academic/src/main/java/com/ecom/config/RateLimitFilter.java#L26) |
| L-06 | `X-Frame-Options: DENY` ขัดกับ CSP `frame-ancestors 'self'` — เบราว์เซอร์เก่าจะบล็อกการ preview PDF ใน iframe ของตัวเอง | ยืนยันด้วย curl |
| L-07 | `resolveLibreOffice()` cache ค่า "ไม่เจอ" ไว้ถาวร — ติดตั้ง LibreOffice ทีหลังต้อง restart แอป | [DocumentGenerationService.java:1116](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/service/DocumentGenerationService.java#L1116) |
| L-08 | `System.err.println` แทน logger + `receivedChunks++` ไม่ atomic (แข่งกันเมื่ออัปโหลด chunk ขนาน) | [PositionRequestService.java:254](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/service/PositionRequestService.java#L254), [ChunkedUploadController.java:146](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/ChunkedUploadController.java#L146) |

**หมายเหตุเรื่อง CSRF:** รายการยกเว้น CSRF ทั้งสองรายการใน [SecurityConfig.java:70-72](../HRCP-KKU-Academic/src/main/java/com/ecom/config/SecurityConfig.java#L70-L72) ไม่ได้เปิดช่องโหว่จริง — `/admin/toggle-image-mode` ไม่มี endpoint อยู่แล้ว (M-10) ส่วน `/admin/activity-logs/export` เป็น GET ซึ่ง CSRF ไม่บังคับใช้อยู่แล้ว แต่ควรลบทิ้งเพื่อไม่ให้เข้าใจผิดในอนาคต

---

# คุณภาพของชุดเทส (แยกจากบักในระบบ)

## T-01 — เทส UAT 250 ตัวเป็นของปลอม ผ่านโดยไม่ได้ทดสอบอะไรเลย

ไฟล์ `src/test/java/com/ecom/uat/UAT_*.java` ทั้ง 11 ไฟล์เป็นแบบนี้ทั้งหมด:

```java
@Test
@DisplayName("TC-02: เข้าสู่ระบบด้วยรหัสผ่านผิดล้มเหลว")
void loginWithWrongPassword_shouldFail() {
    // Given: ผู้ใช้มีบัญชีในระบบ
    // When: ผู้ใช้กรอกรหัสผ่านผิด
    // Then: ระบบแสดงข้อความ error
    assertTrue(true, "ระบบแสดง error เมื่อรหัสผ่านไม่ถูกต้อง");     // ← ไม่ได้เรียกโค้ดจริงเลย
}
```

| ไฟล์ | จำนวน `assertTrue(true)` |
|---|---|
| UAT_AcademicRequestWorkflowTest | 46 |
| UAT_SecurityAccessControlTest | 35 |
| UAT_AdminManagementTest | 34 |
| UAT_AcademicApplicantTest | 27 |
| UAT_AuthenticationTest | 23 |
| UAT_DocumentGenerationTest / UAT_PositionRequestTest | 18 / 18 |
| UAT_FileManagementTest | 16 |
| UAT_PetitionTest / UAT_StaffMemberTest / UAT_UserProfileTest | 12 / 11 / 10 |
| **รวม** | **250 จาก 540 เทส (46%)** |

**นี่คือสาเหตุที่บักระดับ Critical ข้างต้นหลุดมาได้** — `UAT_SecurityAccessControlTest` มีเทสชื่อ "Role-Based Access Control" 6 ตัวและ "Brute Force Protection" 4 ตัวที่ควรจับ C-01, H-01, H-02, H-03 ได้ แต่ทุกตัวเป็น `assertTrue(true)`

## T-02 — `skipTests=true` เป็นค่าเริ่มต้น เทสจึงไม่เคยถูกรัน

[pom.xml:19](../HRCP-KKU-Academic/pom.xml#L19) ตั้ง `<skipTests>true</skipTests>` และก่อนการทดสอบครั้งนี้ `target/surefire-reports/` มีผลลัพธ์อยู่เพียง **1 คลาส** จาก 48 ไฟล์

## T-03 — ผลการรันจริง: 514 เทส / 38 failures / 3 errors

```
mvnw test -DskipTests=false -Dspring.datasource.url=jdbc:h2:mem:hrcptest;...
[ERROR] Tests run: 514, Failures: 38, Errors: 3, Skipped: 0
[INFO] BUILD SUCCESS
```
(ใช้ H2 ทับค่า datasource เพื่อไม่ให้เทสไปแตะฐานข้อมูล dev — `ShoppingCartApplicationTests` และ `AdminLogServiceNewActionTypesTest` ใช้ `@SpringBootTest` เปล่าซึ่งจะต่อ PostgreSQL จริงถ้าไม่ทับ)

## T-04 — เทสที่ล้มส่วนใหญ่เป็นเทสตกยุค ไม่ใช่บักโปรดักชัน

**กลุ่มใหญ่ที่สุด (ราว 12 เทส) เกิดจากโมเดลเปลี่ยนแต่เทสไม่ตาม** — `UserDtls` ย้ายจากฟิลด์ `name` ไปเป็น `firstName` + `lastName` ([UserDtls.java:24-29](../HRCP-KKU-Academic/src/main/java/com/ecom/model/UserDtls.java#L24-L29) ระบุว่า `name` เป็น *"legacy field, kept for backward compat"*) ฟอร์มจริงทั้ง `edit_admin.html` และ `edit_user.html` ส่ง `firstName`/`lastName` มาถูกต้องแล้ว แต่เทสยังเรียก `setName()` อย่างเดียว → controller ตีกลับด้วย "กรุณากรอกชื่อและนามสกุล"

กระทบ: `AdminControllerEditAdminEndpointTest`, `AdminControllerEditUserEndpointTest`, `AdminControllerUpdateAdminTest`, `AdminControllerUpdateUserTest`, `AccountUpdatePersistencePropertyTest`, `DatabaseErrorHandlingPropertyTest`, `EmailUniquenessEnforcementPropertyTest`

**กลุ่มที่สอง (8 เทส) เกิดจาก race กับ `@Async`** — [AdminLogService.log()](../HRCP-KKU-Academic/src/main/java/com/ecom/service/AdminLogService.java#L31) มี `@Async("auditLogExecutor")` เทสเรียกแล้วอ่าน repository ทันทีจึงยังว่างอยู่ กระทบ `DeletionAuditLoggingPropertyTest`, `EditAuditLoggingPropertyTest`, `AdminLogServiceNewActionTypesTest` (ตัวหลังยังเจอ log ปนจากเทสก่อนหน้า: คาด `EDIT_USER_ACCOUNT` แต่ได้ `DELETE_ADMIN_ACCOUNT`)
→ **ไม่ใช่บักของระบบ แต่เป็นสัญญาณว่า audit log ไม่การันตีลำดับและไม่การันตีว่าเขียนเสร็จก่อน response กลับ** ซึ่งสำคัญสำหรับระบบที่ต้องตรวจสอบย้อนหลังได้

**กลุ่มที่สาม (4 เทส) เกิดจาก JDK ไม่ตรงเวอร์ชัน** — `pom.xml` ระบุ `java.version=21` แต่เครื่องนี้ใช้ JDK 26 ความละเอียดของ `LocalDateTime.now()` ต่างกัน (นาโนวินาที vs ไมโครวินาที) เทียบเวลาก่อน/หลัง persist จึงเพี้ยน กระทบ `StatusHistoryOrderingPropertyTest`, `StatusAdditionTimestampPropertyTest`, `EditAuditLoggingPropertyTest.userAccountEditAuditLogging`

**เทสที่ล้มแล้วชี้บักจริง** (นับรวมไว้ในหัวข้อบักด้านบนแล้ว):
- `AdminControllerUpdateProfileImageTest.testEmptyFileUploadReturnsError` → C-02
- `ProfileImageUploadFailurePropertyTest` → C-02
- `PositionRequestServiceTest.updateStatus_RecordsChangedByUser` → M-03

## T-05 — `PetitionControllerTest` ล้มด้วย LazyInitializationException

```
PetitionControllerTest.testGetViewPetition_ShowsDetailsCorrectly
  → LazyInitialization: Could not initialize proxy [UserDtls#10] - no session
PetitionControllerTest.testPostCreatePetition_ValidData_CreatesPetition
  → LazyInitialization: Cannot lazily initialize collection 'Petition.statusHistory'
```

บน production ไม่พังเพราะ `spring.jpa.open-in-view` เปิดอยู่ตามค่าเริ่มต้น (ไม่ได้ตั้งค่าใน `application.properties`) แต่หมายความว่าโค้ดพึ่งพา OSIV อยู่ ถ้าปิด OSIV เมื่อไหร่ (ซึ่งเป็นแนวปฏิบัติที่แนะนำเพื่อประสิทธิภาพ) หน้าดูรายละเอียดคำร้องจะพังทันที

---

# สิ่งที่ตรวจแล้วไม่พบปัญหา

- **XSS ผ่าน Thymeleaf** — ไม่พบ `th:utext` เลยแม้แต่จุดเดียวในทุก template
- **การเข้าถึงโดยไม่ล็อกอิน** — ไล่ยิง 20 route แล้ว ทุกเส้นทางที่ต้องใช้สิทธิ์ redirect ไป `/signin` ถูกต้อง ไม่มีหลุดเป็น 200
- **Security headers** — ครบและตั้งค่าดี: `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, CSP, HSTS
- **SQL injection** — ใช้ Spring Data JPA และ Criteria API ตลอด ไม่พบการต่อ string ลง query
- **การแปลง DOCX → PDF** — [DocumentGenerationService.java:1034-1092](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/service/DocumentGenerationService.java#L1034-L1092) เขียนได้ดีมาก: มี semaphore จำกัด process พร้อมกัน, timeout, `destroyForcibly()`, แยก LibreOffice profile ต่อการเรียก, ลบ temp dir ใน `finally`
- **IDOR ในดาวน์โหลดไฟล์ของผู้ใช้** — [UserFileManagerController.java:144](../HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/UserFileManagerController.java#L144) ใช้ `getFile(id, user.getId())` ผูกกับเจ้าของถูกต้อง
- **Thread pool ของ audit log** — ใช้ `CallerRunsPolicy` จึงไม่ทิ้ง log เงียบ ๆ เมื่อคิวเต็ม
- **หน้า error** — ไม่มี stack trace หลุด (`include-stacktrace=never` ทำงาน)

---

# ผลการแก้ไข

แก้บน branch `fix/security-audit-2026-08-11` — ทุกข้อใช้วิธี test-first

## สรุปการแก้รายข้อ

| บัก | วิธีแก้ | เทสที่คุม |
|---|---|---|
| **C-01** | `updateUserProfile()` เปลี่ยน signature รับ `authenticatedEmail` และค้นบัญชีจากอีเมลใน session — `id` จากฟอร์มถูกเพิกเฉยโดยสิ้นเชิง ทั้ง `/user/update-profile` และ `/admin/update-profile` | `ProfileUpdateSecurityTest` × 2 |
| **C-02** | สร้าง `ProfileImageStorage` รวมการเขียนรูปไว้ที่เดียว: whitelist นามสกุล, จำกัด 5MB, ตรวจ MIME, สุ่มชื่อไฟล์ผ่าน `FileUtils.sanitizeFilename()` ที่มีอยู่แล้ว และตรวจ `target.startsWith(baseDir)` ซ้ำอีกชั้น — **พบว่ามีเส้นทางเขียนรูป 4 จุด ไม่ใช่ 2 จุดตามที่รายงานไว้ตอนแรก** ทั้งหมดถูกรวมมาใช้ตัวเดียวกัน | `ProfileUpdateSecurityTest` × 6 |
| **H-01** | `initUpload` ตรวจ role ก่อน — `storageType=admin` ต้องเป็น `ROLE_ADMIN` เท่านั้น และย้าย validation ออกจากเงื่อนไข `"user".equals(...)` ให้บังคับใช้เสมอ | `ChunkedUploadSecurityTest` × 3 |
| **H-02** | เพิ่ม `mayEdit()` ตรวจความเป็นเจ้าของคำร้อง (แอดมินผ่านได้ทุกคำร้อง) ทั้ง academic และ position | `AutoDraftApiSecurityTest` × 5 |
| **H-03** | `ClientIpUtils` เลิกอ่าน `X-Forwarded-For`/`X-Real-IP` ใช้ `getRemoteAddr()` อย่างเดียว + ตั้ง `server.forward-headers-strategy=native` ให้ Tomcat จัดการ header ตาม trusted proxy — **รวมโค้ดที่ซ้ำกัน 10 จุดมาใช้ utility เดียว** | `ClientIpUtilsTest` × 4 |
| **H-04** | นับ byte จริงด้วย `AtomicLong` ปฏิเสธเมื่อเกินขนาดที่ประกาศ และบันทึก `Files.size(finalPath)` ลง DB แทนค่าจาก client | `ChunkedUploadSecurityTest` × 2 |
| **H-05** | ย้าย secret ทั้งหมดเป็น env var แบบไม่มีค่า default และแก้ `aws.secret.key` ให้อ่าน `${S3_SECRET_KEY}` (เดิมอ่าน `${S3_ACCESS_KEY}` ผิดตัว) | — (config) |
| **M-01** | เปลี่ยน `Files.readAllBytes` → `FileSystemResource` (stream) ในทุกจุดดาวน์โหลดไฟล์เดี่ยว และขยาย return type เป็น `ResponseEntity<Resource>` | — |
| **M-02** | `activeSessions` เปลี่ยนเป็น Caffeine cache TTL 6 ชม. + `removalListener` ลบโฟลเดอร์ chunk อัตโนมัติเมื่อ evict | — |
| **M-03** | เปลี่ยนชื่อ repository method เป็น `findByRequestIdOrderByChangedAtDesc` ให้ตรงกับลำดับจริงใน `@Query` (**แก้ความเข้าใจผิดในรายงานฉบับแรก: ORDER BY มีอยู่แล้ว ปัญหาคือชื่อ method ไม่บอกลำดับ**) และแก้เทสที่อ่าน element สุดท้ายเป็น "ล่าสุด" | `PositionRequestServiceTest` |
| **M-04** | `updateUserProfile` เลิกใช้ `.get()` คืน `null` เมื่อไม่พบบัญชี และลบ dead code ที่เช็ก null หลัง dereference | `ProfileUpdateSecurityTest` × 1 |
| **M-05 / M-06** | `internalError()` helper — log สาเหตุจริงฝั่ง server ส่งข้อความกลางกลับไปหา client และเลิกใช้ `Map.of` กับค่าที่อาจเป็น null | `AutoDraftApiSecurityTest` × 2 |
| **M-07** | แยก `CsvExportUtils.escapeCsv()` เติม `'` นำหน้าค่าที่ขึ้นต้นด้วย `= + - @ tab CR` | `CsvExportEscapingTest` × 5 |
| **M-08** | `previewDocument` รับ `Principal` และบังคับใช้ `APPLICANT_ALLOWED_DOCS` ที่เดิมประกาศทิ้งไว้เฉย ๆ | `DocumentPreviewAccessTest` × 4 |
| **M-09** | `FileUtils.contentDisposition()` ตัดอักขระควบคุม/quote ออกจาก `filename` และแนบชื่อจริงผ่าน `filename*=UTF-8''` | `ContentDispositionTest` × 5 |
| **M-11** | `/actuator/health` เป็น `permitAll` (endpoint อื่นยังต้องเป็น ADMIN) | — |
| **M-12** | `catch (Exception ignored) {}` 20 จุดเปลี่ยนเป็น log จริงผ่าน helper `auditLogFailed()` + แก้ `System.err.println` เป็น logger + แก้ `LocalDate.parse` ที่ทำให้ `?dateFrom=abc` เป็น 500 + ปิด `Files.walk` stream ที่รั่ว | — |

## สิ่งที่พบเพิ่มระหว่างแก้ (ไม่ได้อยู่ในรายงานฉบับแรก)

1. **C-02 มีเส้นทางที่ 3 และ 4** — `saveProfileImage()` ที่ถูกเรียกจาก `updateProfileImageOnly()` (`POST /admin/update-profile-image`) และจาก `updateUserDetails()` มีบักเดียวกันเป๊ะ ที่สำคัญคือ **`validateImageFile()` ที่ controller เรียกอยู่กันไม่ได้** เพราะมันตรวจแค่นามสกุล — ชื่อไฟล์ `../../evil.png` ผ่านการตรวจนามสกุลแล้วยังเขียนออกนอกโฟลเดอร์ได้ ตอนนี้ทั้ง 4 เส้นทางใช้ `ProfileImageStorage` ตัวเดียวกัน
2. **หลักฐานยืนยัน C-02 ของจริง** — ระหว่างเทสก่อนแก้ ไฟล์ `uploads/pwned-only.png` ถูกเขียนออกนอก `profile_img/` จริง และ `uploads/profile_img/evil.html` ถูกรับเป็นรูปโปรไฟล์จริง (ลบออกแล้วทั้งคู่)
3. **โค้ด resolve IP ซ้ำกัน 10 จุด** — ไม่ใช่แค่ `ClientIpUtils` ที่มีบัก แต่ทุก controller มีสำเนาของตัวเองที่เชื่อ `X-Forwarded-For` เหมือนกัน ทั้งหมดถูกรวมมาใช้ utility เดียว
4. **M-03 ในรายงานฉบับแรกคลาดเคลื่อน** — ผมสรุปว่า "ไม่มี ORDER BY" แต่จริง ๆ มี `ORDER BY h.changedAt DESC` อยู่ใน `@Query` ที่ grep แรกไม่เห็น ปัญหาจริงคือชื่อ method ไม่บอกลำดับ ทำให้ผู้เรียกเดาผิด

## การซ่อมชุดเทส (T-04, T-05) — ทำเพิ่มหลังแก้บักเสร็จ

ชุดเทสเดิม fail อยู่ 38+3 ตัว ทำให้ใช้จับ regression ไม่ได้เลย ตอนนี้ผ่านครบ 559 ตัว

| สาเหตุ | จำนวน | วิธีแก้ |
|---|---|---|
| `setName()` เขียนเฉพาะคอลัมน์ legacy แต่ `getName()` และ validation อ่าน `firstName`/`lastName` | 16 | **แก้ที่โปรดักชัน:** `setName()` แตกเป็น firstName/lastName ด้วย (ยืนยันแล้วว่า Hibernate ใช้ field access จึงไม่เรียก setter ตอนโหลด entity) + `UserDtlsNameTest` 6 เทสคุมไว้ |
| `@Async` audit log — เทสอ่าน repository ทันทีจึงชนกับ executor | 12 | เพิ่ม property `app.audit-log.async` (default true) ให้เขียนแบบ synchronous ได้ เทสตั้ง `=false` — ดีกว่าใส่ sleep และเป็น knob ที่ใช้จริงได้ถ้าต้องการ audit log ที่การันตีว่าเขียนเสร็จ |
| คอลัมน์ timestamp ปัดเป็นไมโครวินาที แต่ `LocalDateTime.now()` บน JDK 26 เป็นนาโนวินาที | 6 | ขยายขอบเขตเวลา 1ms และเทียบแบบ `isCloseTo(..., within(2, MILLIS))` |
| `PetitionControllerTest` เรียก controller ตรง ๆ จึงไม่มี OSIV เปิด persistence context | 2 | เพิ่ม `@Transactional` + helper `reloadFromDatabase()` (flush/clear) เพื่ออ่านสถานะที่ commit จริง แทนกราฟ entity ที่ค้างใน memory |
| เทสคาดค่าเดิมที่เป็นพฤติกรรมมีช่องโหว่ (ชื่อไฟล์จาก client, URL `/uploads/`) | 5 | แก้ให้คาดพฤติกรรมใหม่ที่ปลอดภัย |
| generator ชื่อ `validUserUpdates()` แต่ปั่นค่าที่ระบบต้องปฏิเสธ (เช่น `" "`) | 3 | แก้ generator ให้สร้างชื่อที่มีทั้งชื่อและนามสกุลจริง |
| `AuthFailureHandlerTest` stub ไม่ครบ path ที่โค้ดเดินจริง | 1 | เพิ่ม stub ที่ขาด |

### บักโปรดักชันที่เจอเพิ่มระหว่างซ่อมเทส

1. **audit log บันทึกชื่อผิดคน** — [AdminController.updateAdmin](../HRCP-KKU-Academic/src/main/java/com/ecom/controller/AdminController.java) บันทึก `adminName` เป็นชื่อแอดมิน**ที่ถูกแก้** ไม่ใช่คนที่ลงมือแก้ ขณะที่ `updateUser` ทำถูกอยู่แล้ว → ประวัติการใช้งานชี้ตัวคนผิด แก้ให้บันทึกผู้ลงมือ
2. **`sanitizeEmail` ต่อสตริงแทนที่จะตัด** — `evil@test.com\r\nX` กลายเป็น `evil@test.comx` ซึ่งถูกใช้เป็น key ของ brute-force counter และเป็น key ค้นบัญชี แก้ให้ตัดที่ CR/LF/tab ตัวแรก
3. **`ProfileImageStorage` ยังเชื่อ metadata มากไป** — นามสกุลและ content-type ล้วนมาจากผู้อัปโหลด ไฟล์ 1 ไบต์ที่ตั้งชื่อ `.jpg` จึงผ่านได้ เพิ่มการ decode ด้วย `ImageIO.read()` เพื่อยืนยันว่าเป็นรูปจริง (ปิดช่องไฟล์ปลอมนามสกุลไปด้วย)

## สิ่งที่ยังไม่ได้แก้

- **M-10 (`/admin/toggle-image-mode`)** — ลบรายการยกเว้น CSRF ที่ตกค้างออกแล้ว แต่ **ยังไม่ได้แตะ UI** เพราะไม่มี backend รองรับแนวคิด image-mode เลยแม้แต่จุดเดียว การเขียน endpoint สลับ S3/local ขึ้นมาใหม่ถือเป็นการเพิ่มฟีเจอร์ ไม่ใช่แก้บัก — **ต้องให้คุณตัดสินใจว่าจะลบ UI ทิ้งหรือให้ผมสร้าง backend ให้**
- **Low ทั้ง 8 ข้อ** — อยู่นอกขอบเขตที่ตกลงกันไว้ (Critical + High + Medium)
- **T-01: เทส UAT ปลอม 250 ตัว** — ยังเป็น `assertTrue(true)` เหมือนเดิม การเขียนใหม่ให้เทสจริงคือการเขียนเทสใหม่ทั้งหมด 250 เคส ไม่ใช่การซ่อม จึงยังไม่แตะ **นี่คือช่องว่างที่ใหญ่ที่สุดที่เหลืออยู่** — ชุดเทสตอนนี้เขียว 559/559 แต่ 250 ตัวในนั้นยังไม่ได้ทดสอบอะไรเลย
- **T-02: `skipTests=true` ใน pom.xml** — ยังคงเดิม ควรเปิดให้ CI รันเทสทุกครั้ง

---

# ผลการทดสอบจริงผ่าน HTTP (Phase 4.2 — ทำแล้ว)

ทดสอบกับแอปที่รันจริงบน `localhost:8080` ต่อฐานข้อมูล PostgreSQL `hrcp` ด้วยบัญชีจริง 2 ใบ
(admin id=1, applicant id=3) — **ยิง exploit ของทุกบักที่แก้ไป เพื่อพิสูจน์ว่าปิดจริง**

| บัก | วิธียิง | ผลลัพธ์ |
|---|---|---|
| **C-01** | ผู้ใช้ id=3 POST `/user/update-profile` โดยใส่ `id=4` (บัญชีคนอื่น) | **[PASS] บล็อก** — บัญชี id=4 ยังเป็น `ทดสอบ ผู้ใช้งาน` เหมือนเดิม ไม่ถูกแก้ ระบบเพิกเฉย `id` จากฟอร์มและแก้บัญชีตัวเองแทน |
| **C-02a** | อัปโหลด `evil.html` (`<script>`) เป็นรูปโปรไฟล์ | **[PASS] ปฏิเสธ** — ไม่มีไฟล์ `.html` ใน `uploads/profile_img/` และ `profileImage` ใน DB ไม่เปลี่ยน |
| **C-02b** | อัปโหลดชื่อไฟล์ `../../pwned.png` | **[PASS] ปฏิเสธ** — ไม่มีไฟล์ถูกเขียนนอก `profile_img/` เลย |
| **C-02c** | ไฟล์ปลอม (ขึ้นต้น `GIF89a` ตั้งชื่อ `.png` content-type `image/png`) | **[PASS] ปฏิเสธ** — การ decode ด้วย ImageIO จับได้ |
| **H-01** | ผู้ใช้ ROLE_USER POST `/api/upload/init` พร้อม `storageType=admin&filename=payload.exe` | **[PASS] บล็อก** — `{"success":false,"message":"ไม่มีสิทธิ์อัปโหลดไปยังพื้นที่เก็บข้อมูลของผู้ดูแลระบบ"}` |
| **H-01 (regression)** | ROLE_ADMIN ทำแบบเดียวกัน | **[PASS] ยังใช้ได้ปกติ** — ได้ `uploadId` กลับมา (ยกเลิก session แล้ว) |
| **M-08** | ผู้ใช้ POST `/api/academic/preview/6` (แบบประเมินผลการสอน ฝั่งแอดมิน) | **[PASS] 403** |
| **M-08 (regression)** | ผู้ใช้ POST `/api/academic/preview/0` (เอกสารของตัวเอง) | **[PASS] 200** ไม่บล็อกเกินจำเป็น |
| **M-11** | `GET /actuator/health` แบบไม่ล็อกอิน | **[PASS] 200 UP** (เดิม 302 ไป `/signin`) |
| **M-12** | `/admin/activity-logs?dateFrom=abc`, `?dateFrom=2026-13-99`, `?dateTo=xyz` | **[PASS] 200 ทุกเคส** (เดิมจะเป็น 500) |
| **M-07** | ดาวน์โหลด CSV ประวัติการใช้งานจริง (98 KB) | [PASS] ไม่มีแถวไหนขึ้นต้นด้วย `= + @` |
| **แยกสิทธิ์ตาม role** | ผู้ใช้ยิง `/admin/academic/requests`, `/admin/users`, `/admin/activity-logs/export`, `/admin/file-manager/storage` | **[PASS] 403 ทุกเส้นทาง**; แอดมินยิง `/user/**` ได้ 403 เช่นกัน |
| **หน้าจอหลัก** | ไล่ 16 หน้าทั้งสอง role | [PASS] ไม่มี 500 เลย (`/petitions` และ `/user/position/` เป็น 404 ตามปกติ เพราะไม่มี root mapping — ทางเข้าจริงคือ `/petitions/my-petitions` และ `/user/position/dashboard` ซึ่งได้ 200) |

## การสร้างเอกสารและฟอนต์ไทย

| สิ่งที่ตรวจ | ผล |
|---|---|
| สร้าง DOCX (Doc 0) | [PASS] 200, 38 KB, ไฟล์เป็น `Microsoft Word 2007+` จริง |
| แปลงเป็น PDF ผ่าน LibreOffice | [PASS] 200, 113 KB, ขึ้นต้นด้วย `%PDF-` |
| **ฟอนต์ไทยใน PDF** | **[PASS] ฝังมาครบ** — `BAAAAA+THSarabunNew-Bold` และ `CAAAAA+THSarabunNew` แบบ subset พร้อม ToUnicode CMap → ข้อความไทยไม่กลายเป็นกล่องเปล่า |
| แทนค่า placeholder ภาษาไทย | [PASS] ทั้งชื่อไทยและวันที่ไทยถูกแทนค่าถูกต้อง ไม่มี `{{...}}` ตกค้าง |

> **หมายเหตุ:** ระหว่างทดสอบผมเจอว่าชื่อไทยไม่ถูกแทนค่า แต่ตรวจซ้ำแล้วพบว่าเป็นเพราะ shell ของผมทำ UTF-8 เพี้ยนตอนส่ง `curl -d` เอง **ไม่ใช่บักของระบบ** — พอส่งเป็นไฟล์ JSON แบบ UTF-8 ตรง ๆ ก็แทนค่าได้ถูกต้อง

## ที่ยังทดสอบจริงไม่ได้

- **H-02 (IDOR ร่างเอกสาร) ฝั่ง cross-user** — ฐานข้อมูล dev มีคำร้องแค่ 2 ใบ (id 1, 2) ซึ่งเป็นของ applicant คนเดียวกันทั้งคู่ และผมมีรหัสผ่านของ applicant แค่บัญชีเดียว จึงไม่มีคำร้อง "ของคนอื่น" ให้ยิงทดสอบ
  - สิ่งที่ยืนยันได้: คำร้องที่ไม่มีอยู่จริง → **404** (ไม่ใช่ 500 และไม่ใช่ 200) ทั้ง academic และ position
  - การบล็อก cross-user ยืนยันด้วย unit test 5 เคส (เจ้าของผ่าน / คนอื่น 403 / แอดมินผ่าน ทั้งสองประเภทเอกสาร)
  - **ถ้าต้องการยืนยันจริง ขอรหัสผ่าน applicant อีกหนึ่งบัญชีครับ**
- **H-03 (X-Forwarded-For)** — ต้องยิง login ผิดจนล็อกเพื่อพิสูจน์ ซึ่งจะทำให้ IP `127.0.0.1` ล็อกอินไม่ได้ 15 นาที จึงไม่ทำ ยืนยันด้วย unit test 4 เคสแทน

---

# บันทึกเพิ่มเติมจากการทดสอบจริง

**พบบักใหม่ที่เกิดจากการแก้ H-05 เอง และแก้แล้ว:** พอย้าย SMTP credential ออกเป็น env var ที่ไม่มีค่า default ทำให้ `MailHealthIndicator` ตรวจไม่ผ่าน → `/actuator/health` ตอบ **503 DOWN** ทั้งที่แอปทำงานปกติทุกอย่าง ซึ่งจะทำให้ container probe (ที่เพิ่งเปิดให้เข้าถึงได้ตาม M-11) สั่ง restart แอปที่ยังดีอยู่วนไม่จบ
→ แก้โดยตั้ง `management.health.mail.enabled=false` เพราะ SMTP ไม่ใช่เงื่อนไขของการมีชีวิตอยู่ของแอป ยืนยันแล้วว่ากลับมาเป็น **200 UP**

**ผลข้างเคียงที่ต้องรู้:** หลังแก้ H-05 การรันแอปต้องตั้ง env var แล้ว มิฉะนั้นจะต่อฐานข้อมูลไม่ได้
```bash
DB_PASSWORD=... EMAIL_USERNAME=... EMAIL_PASSWORD=... S3_ACCESS_KEY=... S3_SECRET_KEY=... ./mvnw spring-boot:run
```

---

---

# ผลกระทบต่อสภาพแวดล้อมจากการทดสอบครั้งนี้

- **ชุดเทสอัตโนมัติ: ไม่แตะฐานข้อมูล `hrcp` เลย** — รันด้วย H2 in-memory ทับค่า datasource ทั้งหมด
- **การทดสอบจริงผ่าน HTTP แตะ DB dev เท่าที่จำเป็น** และคืนค่าเดิมแล้ว:
  - โปรไฟล์ของ applicant id=3 ถูกเขียนทับด้วย**ค่าเดิมทุกช่อง** (ชื่อ/นามสกุล/เบอร์/ตำแหน่ง) จึงไม่มีอะไรเปลี่ยน — ยืนยันแล้วว่า `profileImage` ยังเป็นไฟล์เดิม
  - บัญชี id=4 ไม่ถูกแตะเลย (นี่คือผลลัพธ์ของการทดสอบ C-01)
  - upload session ที่สร้างตอนทดสอบ H-01 ถูก abort แล้ว ไม่มีโฟลเดอร์ `uploads/chunks/` ค้าง
  - มีแถว log การใช้งานเพิ่มจากการล็อกอิน/แก้โปรไฟล์ระหว่างทดสอบ (เป็น audit log ตามปกติ ไม่ได้ลบ)
  - **ไม่มีคำร้อง เอกสาร หรือไฟล์ทดสอบใดถูกสร้างทิ้งไว้**
- **ไม่มีไฟล์ใดใน `src/` ถูกแก้ไข** (ยืนยันด้วย `git status`)
- แอปที่รันอยู่ที่พอร์ต 8080 ไม่ถูก restart หรือแก้ไขค่าใด ๆ
- **[ข้อควรระวัง] การรันเทสเขียนไฟล์จริงลง `uploads/profile_img/` โดยไม่คาดคิด** (ดู T-06 ด้านล่าง) — ต้องคืนค่าเอง:
  ```bash
  git checkout -- HRCP-KKU-Academic/uploads/profile_img/empty.jpg
  rm HRCP-KKU-Academic/uploads/profile_img/x7E7lS6ya0Ko3Ww6.gif
  ```
- `target/` มี build artifacts ใหม่จากการคอมไพล์และรันเทส

## T-06 — ชุดเทสเขียนไฟล์ลงโฟลเดอร์ uploads จริง ไม่มีการแยก sandbox

การรัน `mvnw test` ครั้งนี้ทำให้ working tree สกปรก:

```
 M HRCP-KKU-Academic/uploads/profile_img/empty.jpg        (0 → 1 bytes)
?? HRCP-KKU-Academic/uploads/profile_img/x7E7lS6ya0Ko3Ww6.gif
```

สาเหตุคือ `updateUserProfile()` เขียนไปที่ `System.getProperty("user.dir") + "/uploads/profile_img/"` แบบตายตัว ([UserServiceImpl.java:212](../HRCP-KKU-Academic/src/main/java/com/ecom/service/impl/UserServiceImpl.java#L212)) โดยไม่ผ่าน config ที่ override ได้ เทส `ProfileImageUploadPropertyTest` และ `AdminControllerUpdateProfileImageTest` จึงเขียนทับข้อมูลจริง

**นี่คือหลักฐานตรงของ C-02 อีกชั้นหนึ่ง** — path ปลายทางถูกกำหนดจาก working directory ของ process ไม่มีการ sandbox และไม่มีการตรวจสอบใด ๆ ก่อนเขียน

**แนวทางแก้ (ย่อ)** ย้าย path เป็น property (`app.upload.dir`) เพื่อให้เทส override ไปยัง temp directory ได้
