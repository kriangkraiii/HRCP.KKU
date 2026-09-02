# แผนการพัฒนา: ระบบ Graceful Degradation และ Fallback Cache สำหรับ Service เชื่อมต่อภายนอก (CpWebClient)

##  ภาพรวมและปัญหา (Context & Problem)
เมื่อระบบพยายามเชื่อมต่อไปยัง External API เช่น `https://api.computing.kku.ac.th/api/v1/user/list` (เว็บไซต์คณะ เพื่อดึงรายชื่อและรูปบุคลากร) แล้วเกิดเหตุขัดข้องทางเครือข่าย เช่น **Connection Timeout**, เซิร์ฟเวอร์ปลายทางปิดปรับปรุง หรือปัญหา DNS:
1. `CpWebClient` จะเกิด `ResourceAccessException: Connect timed out` และพ่น Log ระดับ `ERROR` ออกมาอย่างตื่นตระหนก แม้จะเป็นเพียงการซิงค์ข้อมูลเสริม (Enrichment) ใน Background Task
2. ไม่มีระบบ **Fallback Snapshot Cache** ทำให้เมื่อ API ปลายทางล่ม ระบบจะไม่สามารถเข้าถึงข้อมูลล่าสุดที่เคยซิงค์สำเร็จได้
3. ค่า Timeout ใน `CpWebClient` ปัจจุบันถูก Hardcoded ไว้ในโค้ด (Connect 10s, Read 30s) ไม่สามารถปรับแต่งผ่าน `application.properties` ได้

---

##  เป้าหมาย (Objectives)
1. **Graceful Degradation & Smart Exception Handling:** ดักจับ `ResourceAccessException` (Timeout, Connection Refused, DNS Failure) และ `RestClientException` แยกเฉพาะ โดยปรับระดับ Log เป็น `WARN` พร้อมข้อความที่ชัดเจน ไม่ให้รบกวนระบบหลัก
2. **Configurable Timeouts:** เพิ่มคอนฟิก `cp.web.connect-timeout-seconds` และ `cp.web.read-timeout-seconds` ใน `CpWebProperties`
3. **Local Snapshot Fallback Cache:** เมื่อดึงข้อมูลสำเร็จ ให้บันทึก Snapshot รายชื่อบุคลากรเก็บไว้ใน Disk/Storage ท้องถิ่น (`cp_directory_snapshot.json`) หากเกิด Timeout ในรอบถัดไป ระบบจะสลับไปอ่านจาก Snapshot Cache อัตโนมัติ ทำให้งานซิงค์และฟีเจอร์อื่นๆ ทำงานต่อเนื่องได้ 100%
4. **Resilient Sync Alerting:** ปรับปรุง `CpDirectorySyncService` ให้ระบุสถานะได้ว่าทำงานสำเร็จผ่าน Live API หรือทำงานผ่าน Fallback Cache พร้อมแจ้งเตือนผู้ดูแลระบบอย่างเหมาะสม

---

##  รายละเอียดการเปลี่ยนแปลงทางเทคนิค (Detailed Technical Breakdown)

### 1. Configuration: `CpWebProperties.java`
- เพิ่ม Properties:
  - `connectTimeoutSeconds` (ค่าเริ่มต้น: 5 วินาที)
  - `readTimeoutSeconds` (ค่าเริ่มต้น: 15 วินาที)
  - `fallbackCacheEnabled` (ค่าเริ่มต้น: true)
  - `maxRetryAttempts` (ค่าเริ่มต้น: 2 ครั้ง พร้อม Backoff สั้นๆ)

### 2. Client Resilience & Cache: `CpWebClient.java`
- **ปรับแต่ง RequestFactory:** ใช้ `connectTimeout` และ `readTimeout` จาก `CpWebProperties`
- **แยกประเภท Exception Handling ใน `get(String url)`:**
  - `ResourceAccessException`: Log `WARN` ("College directory connection timeout/unreachable: {url}")
  - `RestClientResponseException`: Log `WARN` ("College directory returned HTTP status {code}")
  - `Exception`: Log `ERROR` สำหรับข้อผิดพลาดที่ไม่คาดคิด
- **ฟังก์ชัน Local Snapshot Cache:**
  - `saveSnapshot(List<CpPerson> people)`: บันทึกข้อมูล JSON ลงในไดเรกทอรี Cache
  - `loadSnapshot()`: อ่านข้อมูลจาก Snapshot ล่าสุดเมื่อ Live API ไม่สามารถเข้าถึงได้
  - `fetchAllWithFallback()`: พยายามดึงจาก Live API ก่อน หากเกิด Network Failure จะโหลดจาก Snapshot Cache โดยอัตโนมัติ

### 3. Service Sync Flow: `CpDirectorySyncService.java`
- ปรับปรุงการเรียกใช้งาน `client.fetchAllWithFallback()`
- ใน `Result`: เพิ่มสถานะ `boolean usedFallbackCache`
- ปรับข้อความสรุปผล (`describe()`):
  - เช่น *"อ่านจากเว็บคณะ (Fallback Cache) 78 คน — ข้อมูลล่าสุดยังคงพร้อมใช้งาน"*

### 4. Unit & Integration Tests
- เพิ่ม `CpWebClientTest.java` เพื่อทดสอบ:
  - การจำลอง Connection Timeout (`ResourceAccessException`) แล้วระบบสลับไปใช้ Local Fallback Cache สำเร็จ
  - การบันทึกและโหลด Snapshot Cache
  - การจัดการเมื่อไม่มีทั้ง Live API และไม่มี Snapshot Cache (ต้องคืน Empty List โดยไม่ Crash)
- ปรับปรุง `CpDirectorySyncServiceTest.java` เพื่อทดสอบกรณี Fallback

---

## [Test] แผนการทดสอบ (Verification Plan)
1. **Automated Unit Tests:**
   - รัน Maven Test สำหรับชุดทดสอบ `CpWebClientTest` และ `CpDirectorySyncServiceTest`
2. **Manual Simulation Verification:**
   - ทดสอบชี้ `cp.web.base-url` ไปยัง IP/Domain จำลองที่เกิด Timeout เพื่อยืนยันว่า Log แสดงผลเป็น `WARN` ไม่เกิด Exception หลุดรอด และระบบสามารถโหลด Fallback Cache ได้อย่างราบรื่น

---

## [YES] สถานะ (อัปเดต 1 กันยายน 2569)

ข้อ 1-4 ทำครบแล้ว รัน `./mvnw test -DskipTests=false` ผ่านทั้งชุด (1,016 เทส, ข้าม 10 เทสที่ต้องใช้ Docker)

| หัวข้อ | สถานะ |
|--------|-------|
| Configurable timeouts + `fallbackCacheEnabled` + `snapshotFilePath` | [YES] |
| `maxRetryAttempts` (ค่าเริ่มต้น 2 ครั้ง, backoff 300ms × ครั้งที่) | [YES] |
| แยก `ResourceAccessException` / `RestClientResponseException` / อื่น ๆ เป็น WARN/ERROR | [YES] |
| `saveSnapshot()` / `loadSnapshot()` / `fetchAllWithFallback()` | [YES] |
| `CpDirectorySyncService.Result.usedFallbackCache` + `describe()` | [YES] |
| `CpWebClientTest` (7 เทส) + `CpDirectorySyncServiceTest` (9 เทส) | [YES] |
| Manual simulation ชี้ base-url ไป host ที่ timeout จริง | ⬜ ต้องทำบนเครื่องจริง |

**สิ่งที่ทำต่างจากแผนเดิม และเหตุผล**

1. **`fetchAllWithFallback()` คืน `DirectoryFetch(people, usedFallbackCache)` ไม่ใช่ `List`** — ผู้เรียก
   ต้องรู้ให้ได้ว่าตัวเลขที่เห็นมาจาก API สดหรือจากแคช ถ้าคืนเป็น `List` เฉย ๆ ข้อมูลนั้นจะหายไป
   `fetchAll()` เดิมยังอยู่และเรียกต่อไปยังเมธอดนี้ ผู้เรียกเดิมจึงไม่ต้องแก้
2. **ยังแจ้งเตือนผ่าน `alerts.success()` ตอนใช้ fallback** — งานซิงค์ยัง "สำเร็จ" จริง (ทุกฟิลด์ถูก
   apply ครบ) การส่งเป็น `failure()` จะทำให้ผู้ดูแลเข้าใจผิดว่าไม่มีอะไรถูกอัปเดต ข้อความสรุป
   ระบุชัดว่าเป็น "ข้อมูลสำรองในเครื่อง" และมี `log.warn` กำกับไว้อีกชั้น
3. **retry เฉพาะ timeout เท่านั้น** — HTTP status หรือ JSON ที่อ่านไม่ออก ยิงซ้ำกี่ครั้งก็ได้คำตอบเดิม
   จึงเลิกทันที ไม่เสียเวลาและไม่รบกวนเซิร์ฟเวอร์ปลายทาง
4. **เทส retry ใช้ stub HTTP server ที่ "รับแล้วเงียบ" ไม่ใช่ "รับแล้วตัดสาย"** — `HttpURLConnection`
   ของ JDK ยิง GET ซ้ำเองเงียบ ๆ เมื่อการเชื่อมต่อถูกตัด จำนวนครั้งที่นับได้จึงเป็นสองเท่าและไม่ได้
   วัดค่า `maxRetryAttempts` จริง ส่วน read timeout นั้น JDK ไม่ยิงซ้ำให้ จำนวนที่นับได้จึงเป็น
   ของ client เองล้วน ๆ
