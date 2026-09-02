# PLAN: Universal Research Access for Test User Account

##  Executive Summary
ระบบปัจจุบันจำกัดให้ผู้ใช้สามารถค้นหาและเลือกผลงานวิจัย Scopus ได้เฉพาะผลงานที่เป็นของตนเองเท่านั้น (จับคู่ผ่าน `fs_faculty.email == user.email`). 
แผนงานนี้มีเป้าหมายเพื่ออนุญาตให้บัญชีทดสอบที่กำหนดผ่าน `app.user.email` (ค่าเริ่มต้น: `user@user.com`) สามารถค้นหาและเลือกผลงานวิจัย **ทั้งหมดในระบบ (All Scopus Publications)** ได้ เพื่อความสะดวกในการทดสอบกรอกคำร้องและสร้างเอกสารวิชาการ ในขณะที่บัญชีอาจารย์ทั่วไปทุกคนยังคงถูกจำกัดสิทธิ์ให้เห็นเฉพาะงานวิจัยของตนเองตามเดิม 100%

---

##  Security & Data Isolation Analysis (การตรวจสอบความปลอดภัย)

| User Type | Scopus Query Behavior | Isolation Guarantee |
|-----------|----------------------|---------------------|
| **Test User Account** (`app.user.email` e.g., `user@user.com`) | เข้าถึงผลงานทั้งหมด (`publicationRepo.adminSearch` / All publications) | ไม่กระทบผู้ใช้อื่น ใช้สำหรับ Test / Demo |
| **Regular Teacher Accounts** (อาจารย์ทั่วไป) | กรองเฉพาะ `fsUserId` ของตนเอง (`publicationRepo.findOwnedBy`) | **100% Isolated** — ไม่มีโอกาสเห็นงานวิจัยของอาจารย์ท่านอื่น |
| **Unmatched Users** (ไม่มีในฐานข้อมูลคณะ) | คืนค่าว่าง (`Page.empty()`) | ปลอดภัย ไม่มีการดึงข้อมูลผิดพลาด |

---

## ️ Architecture & Implementation Breakdown

### Phase 1: ScopusQueryService Extension
- **File:** `com.ecom.external.service.ScopusQueryService.java`
- **Logic:**
  1. Inject `@Value("${app.user.email:user@user.com}") private String testUserEmail;`
  2. เพิ่ม method `isUniversalAccessUser(UserDtls user)` ตรวจสอบว่า `user.getEmail()` ตรงกับ `testUserEmail` หรือไม่
  3. ใน `listOwn(UserDtls user, ...)`:
     - ถ้าเป็น `isUniversalAccessUser(user)` ➔ เรียก `publicationRepo.adminSearch(null, yearFrom, yearTo, pattern, pageable)` (ค้นหาได้ทั้งหมด)
     - ถ้าเป็นอาจารย์ทั่วไป ➔ ทำงานตามเดิม (`publicationRepo.findOwnedBy(fsUserId, ...)`)
  4. ใน `findOwn(UserDtls user, Long publicationId)` และ `findOwnedByIds(UserDtls user, List<Long> ids)`:
     - ถ้าเป็น `isUniversalAccessUser(user)` ➔ ดึง `publicationRepo.findById(id)` ได้โดยตรง
     - ถ้าเป็นอาจารย์ทั่วไป ➔ ตรวจสอบ ownership `publicationRepo.findByIdAndFsUserId(id, owner)` ตามเดิม
  5. ใน `metricsFor(UserDtls user)`:
     - ถ้าเป็น `isUniversalAccessUser(user)` ➔ คืนค่า metrics รวมหรือค่า default สำหรับการทดสอบ
     - ถ้าเป็นอาจารย์ทั่วไป ➔ ดึง metrics เฉพาะของตนเองตามเดิม

### Phase 2: MyPublicationsApiController Integration
- **File:** `com.ecom.external.controller.MyPublicationsApiController.java`
- **Logic:**
  1. Endpoint `/api/my/publications`: ส่งคืน `linked: true` เสมอสำหรับ test user เพื่อให้ modal picker บนหน้าเว็บเปิดใช้งานได้ทันที
  2. Endpoint `/api/my/publications/citations`: แปลง citation ของผลงานที่เลือกทั้งหมดได้ถูกต้อง
  3. Endpoint `/api/my/publications/metrics`: ส่งคืนข้อมูลตัวชี้วัด papers, citations, h-index

### Phase 3: Configuration & Environment
- **File:** `src/main/resources/application.properties`
- **Logic:**
  - ยืนยันการตั้งค่า `app.user.email=${USER_EMAIL:user@user.com}`

---

##  Verification Plan

### Test Case 1: Test User (`user@user.com`)
1. เข้าสู่ระบบด้วย `user@user.com`
2. ไปที่หน้ายื่นคำร้องขอกำหนดตำแหน่งทางวิชาการ (`/user/academic/request/.../document-1`)
3. เปิด Modal "เลือกผลงานจาก Scopus"
4. **Expected Result:** แสดงรายการงานวิจัยของอาจารย์ทุกคนในระบบ ค้นหาได้ทุกชื่อเรื่อง ทุกวารสาร และสามารถเลือกผลงานเพื่อกรอกลงฟอร์มได้สำเร็จ

### Test Case 2: Regular Teacher Account (`teacher@kku.ac.th`)
1. เข้าสู่ระบบด้วยบัญชีของอาจารย์จริงที่มีผลงานในระบบ
2. เปิด Modal "เลือกผลงานจาก Scopus"
3. **Expected Result:** แสดงเฉพาะผลงานของตนเองเท่านั้น ไม่ปรากฏผลงานของอาจารย์ท่านอื่น
4. ลองยิง API `/api/my/publications/citations?ids=...` ด้วย ID ของอาจารย์ท่านอื่น
5. **Expected Result:** ระบบจะ drop ID ที่ไม่ได้เป็นเจ้าของทิ้งทันที และไม่ส่งข้อมูลกลับมา

---

##  Task Checklist
- [ ] Implement `isUniversalAccessUser` in `ScopusQueryService`
- [ ] Add universal bypass logic in `listOwn`, `findOwn`, `findOwnedByIds`, `metricsFor`
- [ ] Verify `MyPublicationsApiController` behavior
- [ ] Run `./mvnw clean test-compile` to verify build
- [ ] Document changes in walkthrough
