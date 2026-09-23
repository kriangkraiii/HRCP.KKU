# PLAN: Advanced Admin Dashboard Metrics & Governance Hub (CP HRD)

## 1. Executive Summary & Objective
ยกระดับหน้า **Admin Dashboard Analytics** (`/admin/academic/dashboard`) ในระบบ CP HRD ของวิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น ตามความต้องการของผู้บริหาร:
1. **SLA & Bottleneck Monitor (อันดับ 1):** เฝ้าระวังคำร้องที่ค้างนานเกิน 14 วัน และแสดงระยะเวลาเฉลี่ยในการดำเนินการแต่ละขั้นตอน
2. **Pending E-Signature Pipeline (อันดับ 2):** สรุปสถานะเอกสารแบบฟอร์ม 1-9 ที่อยู่ระหว่างรอลงนามอิเล็กทรอนิกส์ แยกตามบทบาท (คณบดี, รองคณบดี, กรรมการ)
3. **Faculty Headcount by Academic Rank (ข้อมูลคณาจารย์):** สรุปจำนวนและสัดส่วนอาจารย์แต่ละตำแหน่งทางวิชาการปัจจุบันในระบบ (ศ. / รศ. / ผศ. / อาจารย์) พร้อมอัตราส่วนผู้ดำรงตำแหน่งวิชาการ (Academic Rank Coverage %)

---

## 2. Architecture & Data Sources

```
+-----------------------------------------------------------------------------------------+
|                                    CP HRD DASHBOARD                                     |
+-----------------------------------------------------------------------------------------+
| Tier 1: Executive KPIs (5 Cards + Stalled Alert + Pending e-Signs)                      |
| [คำร้องทั้งหมด] [กำลังดำเนินการ] [ผ่านประเมิน] [ส่ง มข.] [ใกล้หมดอายุ 90 วัน] [ค้างนาน >14 วัน] [รอลงนาม e-Sign] |
+-----------------------------------------------------------------------------------------+
| Tier 2: Strategic Insights (3 Columns)                                                  |
| 1. กราฟคำร้องรายเดือน 12 เดือน    2. ตำแหน่งเป้าหมายที่ยื่น    3. ข้อมูลอาจารย์แยกตามตำแหน่งปัจจุบัน (ศ./รศ./ผศ./อ.) |
+-----------------------------------------------------------------------------------------+
| Tier 3: Operational Lifecycle & Governance Hub (Enterprise Tabs)                        |
| Tab 1: ติดตามวันหมดอายุผลประเมิน (3 ปี)                                                    |
| Tab 2: สถิติรายวิชาที่ยื่นประเมินแยกตามปีการศึกษา                                            |
| Tab 3: รายการเอกสารรอลงนามอิเล็กทรอนิกส์ (Pending e-Signatures Watchlist)                   |
| Tab 4: ติดตามคำร้องค้างนานเกินเกณฑ์ (Stalled / SLA Watchlist)                               |
+-----------------------------------------------------------------------------------------+
```

### แหล่งข้อมูลในระบบ (Database Mapping)
1. **SLA & Stalled Requests:**
   - ตาราง `academic_request`, `position_request`
   - ตาราง `request_status_history`, `position_status_history`
   - เกณฑ์: คำร้องที่สถานะยังไม่สิ้นสุด (`status NOT IN (COMPLETED, REJECTED, WITHDRAWN, DRAFT)`) ที่ไม่มีการเคลื่อนไหวเกิน 14 วัน (`DAYS.between(lastUpdated, now) > 14`)
2. **Pending E-Signatures:**
   - ตาราง `signature_step` (เงื่อนไข: `status = ACTIVE`)
   - ตาราง `signature_request` (เงื่อนไข: `status = IN_PROGRESS`)
   - ข้อมูลผู้ลงนาม: `signer.name`, `role`, `documentType`, `createdAt`, `dueAt`
3. **Faculty Headcount by Academic Rank:**
   - ตาราง `staff_member` (เงื่อนไข: `is_active = true`)
   - แปลงตำแหน่งผ่าน `com.ecom.academic.model.AcademicRank.of(staff.getAcademicTitle())`:
     - `PROFESSOR` (ศาสตราจารย์)
     - `ASSOCIATE_PROFESSOR` (รองศาสตราจารย์)
     - `ASSISTANT_PROFESSOR` (ผู้ช่วยศาสตราจารย์)
     - `LECTURER` / อื่นๆ (อาจารย์)

---

## 3. Detailed Component Plan

### 3.1 Data Transfer Objects (DTOs)
- `com.ecom.academic.dto.StalledRequestItem`:
  - `requestCode` (String)
  - `applicantName` (String)
  - `requestType` (String: ประเมินการสอน / ขอตำแหน่ง)
  - `currentStatusLabel` (String)
  - `statusBadgeColor` (String)
  - `lastStatusDate` (String)
  - `stalledDays` (long: จำนวนวันที่ค้างในสถานะนี้)
  - `isCritical` (boolean: ค้างเกิน 30 วัน)
- `com.ecom.academic.dto.PendingSignatureItem`:
  - `signatureRequestId` (Long)
  - `requestCode` (String)
  - `documentTitle` (String: เช่น แบบคำขอประเมิน, คำสั่งแต่งตั้งกรรมการ)
  - `signerName` (String: ชื่อผู้ที่ต้องลงนาม)
  - `signerRole` (String: คณบดี, รองคณบดี, ประธานกรรมการ, กรรมการ)
  - `requestedDate` (String)
  - `waitingDays` (long: รอนานกี่วันแล้ว)
  - `isOverdue` (boolean: เกินกำหนด dueAt)
- `com.ecom.academic.dto.FacultyRankStat`:
  - `totalFaculty` (long)
  - `professorCount` (long)
  - `associateProfessorCount` (long)
  - `assistantProfessorCount` (long)
  - `lecturerCount` (long)
  - `promotedPercentage` (double: ร้อยละอาจารย์ที่มีตำแหน่งวิชาการ)

### 3.2 Service Layer Updates (`DashboardAnalyticsService.java`)
- `getStalledRequestAnalytics()`:
  - ดึงคำร้องทั้งหมดที่กำลังดำเนินการ คำนวณวันที่ค้างเทียบกับ `updatedAt` หรือประวัติสถานะล่าสุด
  - จัดเรียงตามจำนวนวันที่ค้างมากที่สุดลงมา (Descending)
  - สรุปยอดรวมคำร้องที่ค้างเกิน 14 วัน และเกิน 30 วัน
- `getPendingSignatureAnalytics()`:
  - Query `signatureStepRepository` สำหรับ steps ที่สถานะ `ACTIVE` ใน request ที่ `IN_PROGRESS`
  - สรุปยอดค้างลงนามแยกตามบทบาท (By Role: คณบดี, รองคณบดี, กรรมการ)
  - ลิสต์รายการเอกสารรอลงนามพร้อมจำนวนวันที่รอ
- `getFacultyRankAnalytics()`:
  - ดึงข้อมูลจาก `staffMemberRepository.findByIsActiveTrueOrderByFirstNameAscLastNameAsc()`
  - จัดกลุ่มนับตาม `AcademicRank` 4 กลุ่ม (ศ., รศ., ผศ., อาจารย์)
  - คำนวณร้อยละสัดส่วนเพื่อใช้แสดงใน Chart และสถิติสรุป

### 3.3 Controller Layer Updates (`AcademicAdminController.java`)
- อัปเดตเมธอด `@GetMapping("/dashboard")`:
  - เรียกใช้ 3 เมธอดวิเคราะห์ใหม่
  - ส่งข้อมูลลงใน `Model`:
    - `stalledRequests` / `stalledSummary`
    - `pendingSignatures` / `signatureSummary`
    - `facultyRankStats` / `facultyRankLabels` / `facultyRankValues`

### 3.4 Frontend UI / Template Updates (`dashboard.html` & `style.css`)
- **Tier 1 (KPI Badges / Indicators):**
  - เพิ่ม Alert Badge สำหรับ *"คำร้องค้างนานเกิน 14 วัน"* และ *"เอกสารรอลงนาม e-Sign"* เพื่อให้เห็นทันทีเมื่อเปิดหน้า
- **Tier 2 (Strategic Charts):**
  - ปรับการ์ดสรุปหรือเพิ่มคอลัมน์แสดง **"สัดส่วนตำแหน่งวิชาการของคณาจารย์ในระบบ (Faculty Academic Ranks)"** ด้วยกราฟแท่งแนวนอน (Horizontal Bar) หรือ Donut Chart พร้อมตัวเลขกำกับชัดเจน
- **Tier 3 (Operational Tabs):**
  - เพิ่ม 2 แท็บใหม่ใน Enterprise Tab Bar:
    - **แท็บ: เอกสารรอลงนามอิเล็กทรอนิกส์ (e-Signatures):** แสดงการ์ดย่อยสรุปค้างที่ใคร + ตารางเอกสารที่รอลงนาม
    - **แท็บ: คำร้องที่ค้างนานเกินเกณฑ์ (Stalled / SLA):** แสดงตารางคำร้องที่ไม่มีการเคลื่อนไหวเกิน 14 วัน พร้อมจำนวนวันและปุ่มติดตาม

---

## 4. Design Guidelines & Constraints
- **100% Emoji-Free:** ใช้งานเฉพาะ FontAwesome Icons ทางการเท่านั้น
- **Formal Academic Tone:** ข้อความภาษาไทยทางการระดับมหาวิทยาลัย
- **Performance:** ใช้ Client-side filter ร่วมกับ DTO ที่คำนวณเบ็ดเสร็จจาก Backend โดยไม่เกิด N+1 query
- **Responsive & Dark Mode:** รองรับทั้งหน้าจอเดสก์ท็อป แท็บเล็ต และโหมดสีเข้ม

---

## 5. Verification Checklist & Testing Plan
1. **Unit Tests (`DashboardAnalyticsServiceTest.java`):**
   - ทดสอบ `testStalledRequestsCalculation`: คำนวณวันที่ค้างถูกต้อง (>14 วัน, >30 วัน)
   - ทดสอบ `testPendingSignaturesGrouping`: จัดกลุ่มตามบทบาทถูกต้อง
   - ทดสอบ `testFacultyRankCategorization`: จัดกลุ่ม ศ., รศ., ผศ., อาจารย์ จาก `StaffMember` ถูกต้อง
2. **Build Verification:**
   - `./mvnw test-compile` ผ่านสมบูรณ์
   - `./mvnw test` ผ่าน 100% (0 errors, 0 failures)
3. **Template Integrity:**
   - สแกน Regex ไม่มีอีโมจิหลุดรอด
   - การแสดงผลภาษาไทยถูกต้อง ไม่ทับซ้อน
