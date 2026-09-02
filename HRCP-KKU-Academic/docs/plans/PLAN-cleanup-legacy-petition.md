# แผนการลบและคลีนระบบคำร้องต้นแบบเดิม (Legacy Prototype Petition Cleanup Plan)

## 📌 บทนำและเป้าหมาย
ลบไฟล์ซอร์สโค้ด, เทมเพลต, ไฟล์สไตล์ และชุดทดสอบของระบบต้นแบบ `Petition` (`/petitions/*`) ซึ่งเป็นระบบคำร้องรุ่นแรกที่ไม่ได้ใช้งานแล้ว (ถูกแทนที่ด้วย `AcademicRequest` และ `PositionRequest` อย่างสมบูรณ์ 100%) โดยการลบครั้งนี้จะทำให้โครงสร้างโปรเจกต์สะอาด เป็นระเบียบ (Clean Architecture) และไม่มีผลกระทบต่อระบบงานหลักใดๆ

---

## 🗂️ รายการไฟล์ที่จะดำเนินการ

### 1. ลบไฟล์ Source Code ใน `src/main/java` [DELETE]
- `src/main/java/com/ecom/academic/controller/PetitionController.java`
- `src/main/java/com/ecom/academic/service/PetitionService.java`
- `src/main/java/com/ecom/academic/model/Petition.java`
- `src/main/java/com/ecom/academic/model/PetitionStatus.java`
- `src/main/java/com/ecom/academic/repository/PetitionRepository.java`
- `src/main/java/com/ecom/academic/repository/PetitionStatusRepository.java`
- `src/main/java/com/ecom/academic/dto/PetitionForm.java`
- `src/main/java/com/ecom/exception/ActivePetitionExistsException.java`
- `src/main/java/com/ecom/exception/PetitionNotFoundException.java`

### 2. ลบโฟลเดอร์เทมเพลตและไฟล์ CSS [DELETE]
- `src/main/resources/templates/petition/` (โฟลเดอร์และไฟล์ทั้งหมด: `new.html`, `cannot_submit.html`, `list.html`, `view.html`)
- `src/main/resources/static/css/petition-status.css`

### 3. ปรับแก้ไฟล์ที่มีการอ้างอิง Exception และ Entity [MODIFY]
- `src/main/java/com/ecom/exception/GlobalExceptionHandler.java` (นำ `handlePetitionNotFound` ออก)
- `src/test/java/com/ecom/academic/model/DatabaseIndexVerificationTest.java` (นำ `Petition.class` และ `PetitionStatus.class` ออกจากลิสต์ตรวจสอบ)
- `src/test/java/com/ecom/exception/GlobalExceptionHandlerTest.java` (นำ Test case ของ PetitionNotFoundException ออก)
- `src/test/java/com/ecom/exception/ExceptionClassesTest.java` (นำ Test case ของ ActivePetitionExistsException และ PetitionNotFoundException ออก)

### 4. ลบไฟล์ Unit & Integration Tests ของ Petition [DELETE]
- `src/test/java/com/ecom/academic/controller/PetitionControllerTest.java`
- `src/test/java/com/ecom/academic/service/PetitionServiceTest.java`
- `src/test/java/com/ecom/academic/repository/PetitionRepositoryTest.java`
- `src/test/java/com/ecom/academic/model/PetitionIsActivePropertyTest.java`
- `src/test/java/com/ecom/academic/service/StatusAdditionTimestampPropertyTest.java`
- `src/test/java/com/ecom/academic/service/StatusHistoryIntegrityPropertyTest.java`
- `src/test/java/com/ecom/academic/repository/StatusHistoryOrderingPropertyTest.java`
- `src/test/java/com/ecom/academic/service/ActivePetitionBlockingPropertyTest.java`
- `src/test/java/com/ecom/academic/integration/PetitionIntegrationTest.java`
- `src/test/java/com/ecom/uat/UAT_PetitionTest.java`

---

## 🧪 แผนการทดสอบและตรวจสอบ (Verification Plan)
1. **Compilation Check:** รัน `./mvnw clean test-compile` เพื่อยืนยันว่าคอมไพล์ผ่าน 100% ไม่มี Class หรือ Reference ที่ขาดหาย
2. **Full Test Suite:** รัน `./mvnw test` เพื่อยืนยันว่าการทดสอบของทุกโมดูลหลัก (Academic Request, Position Request, E-Sign, Staff, Security, User) ผ่านครบถ้วน 0 failures, 0 errors
