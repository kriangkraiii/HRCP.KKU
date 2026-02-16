# แผนการพัฒนา: ระบบจัดการสถานะคำร้อง

## ภาพรวม

การพัฒนาระบบจัดการสถานะคำร้องจะเริ่มจากการสร้าง data model และ enum สำหรับสถานะ จากนั้นพัฒนา repository และ service layer เพื่อจัดการ business logic สุดท้ายสร้าง controller และ view สำหรับการแสดงผล การพัฒนาจะเป็นแบบ incremental โดยมีการทดสอบในแต่ละขั้นตอน

## Tasks

- [x] 1. สร้าง Entity classes และ Enum สำหรับสถานะ
  - สร้าง StatusType enum พร้อมกำหนดชื่อ, สี, และไอคอนสำหรับแต่ละสถานะ (RECEIVED, COMMITTEE_ASSIGNED, MEETING_SCHEDULED, RESULT_APPROVED, RESULT_REVISION, REJECTED, COMPLETED)
  - สร้าง PetitionStatus entity พร้อม relationship กับ Petition
  - ปรับปรุง Petition entity ให้มี relationship กับ PetitionStatus (One-to-Many)
  - เพิ่ม helper methods: getCurrentStatus() และ isActive() ใน Petition entity
  - _Requirements: 2.1, 2.4, 3.1, 3.2_

- [x] 1.1 Write unit tests for StatusType enum configuration
  - ทดสอบว่าแต่ละ StatusType มีค่า displayName, color, และ iconClass ที่ถูกต้อง
  - ทดสอบว่า COMPLETED status มีสีเขียวเข้มและไอคอนติ๊กถูก
  - _Requirements: 2.2, 2.3, 4.1-4.7_

- [x] 1.2 Write property test for Petition.isActive() method
  - **Property 3: Completed status is not active**
  - **Validates: Requirements 2.4**

- [x] 2. สร้าง database migration scripts
  - สร้าง migration script สำหรับตาราง petition_statuses
  - เพิ่ม indexes สำหรับ performance (petition_id, created_at)
  - สร้าง migration script สำหรับปรับปรุงตาราง petitions (ถ้าจำเป็น)
  - _Requirements: 3.3_

- [x] 3. สร้าง Repository layer
  - สร้าง PetitionStatusRepository interface
  - สร้าง custom query method findActivePetitionByUserId() ใน PetitionRepository
  - สร้าง query method findAllByUserIdWithStatusHistory() ใน PetitionRepository
  - สร้าง query method findByPetitionIdOrderByCreatedAtAsc() ใน PetitionStatusRepository
  - _Requirements: 1.1, 3.4, 5.1_

- [x] 3.1 Write unit tests for repository queries
  - ทดสอบ findActivePetitionByUserId() คืนค่า empty เมื่อ user มี petition ที่เป็น REJECTED หรือ COMPLETED
  - ทดสอบ findActivePetitionByUserId() คืนค่า petition เมื่อ user มี active petition
  - ทดสอบ findByPetitionIdOrderByCreatedAtAsc() คืนค่า statuses เรียงตามเวลา
  - _Requirements: 1.1, 1.3, 3.4_

- [x] 3.2 Write property test for status history ordering
  - **Property 5: Status history ordering**
  - **Validates: Requirements 3.4**

- [x] 4. สร้าง Exception classes
  - สร้าง ActivePetitionExistsException
  - สร้าง PetitionNotFoundException
  - สร้าง UserNotFoundException (ถ้ายังไม่มี)
  - _Requirements: 1.2_

- [x] 5. Implement PetitionService
  - [x] 5.1 Implement canUserSubmitPetition() method
    - ตรวจสอบว่า user มี active petition หรือไม่โดยใช้ repository query
    - _Requirements: 1.1_
  
  - [x] 5.2 Implement getActivePetition() method
    - คืนค่า Optional<Petition> ของ active petition
    - _Requirements: 1.1_
  
  - [x] 5.3 Implement createPetition() method
    - ตรวจสอบว่า user สามารถยื่นคำร้องได้ (ไม่มี active petition)
    - โยน ActivePetitionExistsException ถ้ามี active petition
    - สร้าง Petition entity และบันทึกลง database
    - เพิ่มสถานะเริ่มต้น RECEIVED โดยอัตโนมัติ
    - _Requirements: 1.2, 1.3, 1.4_
  
  - [x] 5.4 Implement addStatus() method
    - สร้าง PetitionStatus entity พร้อม timestamp
    - บันทึกลง database
    - _Requirements: 3.3_
  
  - [x] 5.5 Implement getPetitionWithHistory() และ getUserPetitions() methods
    - โหลด petition พร้อม status history
    - _Requirements: 5.1_

- [x] 5.6 Write property test for active petition blocking
  - **Property 1: Active petition blocking**
  - **Validates: Requirements 1.2**

- [x] 5.7 Write property test for terminal status allows submission
  - **Property 2: Terminal status allows submission**
  - **Validates: Requirements 1.3**

- [x] 5.8 Write property test for status addition timestamp
  - **Property 4: Status addition includes timestamp**
  - **Validates: Requirements 3.3**

- [x] 5.9 Write property test for status history integrity
  - **Property 6: Status history contains only actual records**
  - **Validates: Requirements 5.1**

- [x] 5.10 Write unit tests for PetitionService
  - ทดสอบ createPetition() โยน exception เมื่อมี active petition
  - ทดสอบ createPetition() สำเร็จเมื่อไม่มี active petition
  - ทดสอบ createPetition() เพิ่มสถานะ RECEIVED อัตโนมัติ
  - ทดสอบ addStatus() บันทึก timestamp ถูกต้อง
  - ทดสอบ edge cases: user ไม่มีคำร้อง, petition ไม่พบ
  - _Requirements: 1.2, 1.3, 1.4, 3.3_

- [x] 6. Checkpoint - ตรวจสอบ Service layer
  - รัน tests ทั้งหมดให้ผ่าน
  - ถามผู้ใช้หากมีคำถาม

- [x] 7. สร้าง PetitionController
  - [x] 7.1 Implement GET /petitions/new endpoint
    - ตรวจสอบว่า user สามารถยื่นคำร้องได้
    - ถ้าไม่ได้ แสดงหน้า cannot_submit.html พร้อมข้อมูล active petition
    - ถ้าได้ แสดงฟอร์มยื่นคำร้อง
    - _Requirements: 1.2_
  
  - [x] 7.2 Implement POST /petitions/create endpoint
    - รับข้อมูลจากฟอร์ม (title, description)
    - Validate ข้อมูลด้วย @Valid
    - เรียก petitionService.createPetition()
    - จัดการ ActivePetitionExistsException และแสดง error message
    - Redirect ไปหน้ารายละเอียดคำร้องเมื่อสำเร็จ
    - _Requirements: 1.2, 1.3_
  
  - [x] 7.3 Implement GET /petitions/{id} endpoint
    - โหลด petition พร้อม status history
    - ส่งข้อมูลไปยัง view
    - _Requirements: 5.1, 6.1_
  
  - [x] 7.4 Implement GET /petitions/my-petitions endpoint
    - โหลดคำร้องทั้งหมดของ user
    - แสดงรายการคำร้องพร้อมสถานะปัจจุบัน
    - _Requirements: 5.1_

- [x] 7.5 Write unit tests for PetitionController
  - ทดสอบ GET /petitions/new แสดงฟอร์มเมื่อไม่มี active petition
  - ทดสอบ GET /petitions/new แสดง cannot_submit เมื่อมี active petition
  - ทดสอบ POST /petitions/create สร้างคำร้องสำเร็จ
  - ทดสอบ POST /petitions/create ล้มเหลวเมื่อมี validation errors
  - ทดสอบ GET /petitions/{id} แสดงรายละเอียดถูกต้อง
  - _Requirements: 1.2, 1.3, 5.1, 6.1_

- [x] 8. สร้าง Global Exception Handler
  - สร้าง @ControllerAdvice class
  - เพิ่ม @ExceptionHandler สำหรับ PetitionNotFoundException
  - เพิ่ม @ExceptionHandler สำหรับ UserNotFoundException
  - _Requirements: 1.2_

- [x] 9. สร้าง Thymeleaf templates
  - [x] 9.1 สร้าง petition/view.html
    - แสดงรายละเอียดคำร้อง (title, description, created date)
    - แสดงชื่อผู้ยื่นคำร้อง (fullName หรือ username)
    - แสดง status timeline พร้อมสี, ไอคอน, และเวลา
    - ใช้ CSS สำหรับแสดงสีตาม StatusType
    - แสดงเฉพาะสถานะที่เกิดขึ้นจริง (loop ผ่าน statusHistory)
    - _Requirements: 4.1-4.7, 5.1, 6.1, 6.2, 6.3_
  
  - [x] 9.2 สร้าง petition/cannot_submit.html
    - แสดงข้อความแจ้งเตือนเป็นภาษาไทย
    - แสดงข้อมูล active petition ที่มีอยู่
    - แสดงลิงก์ไปยังหน้ารายละเอียดคำร้องที่กำลังดำเนินการ
    - _Requirements: 1.2_
  
  - [x] 9.3 สร้าง petition/new.html
    - สร้างฟอร์มยื่นคำร้อง (title, description)
    - เพิ่ม validation messages
    - _Requirements: 1.3, 1.4_
  
  - [x] 9.4 สร้าง petition/list.html
    - แสดงรายการคำร้องของ user
    - แสดงสถานะปัจจุบันของแต่ละคำร้องพร้อมสีและไอคอน
    - _Requirements: 4.1-4.7, 5.1_
  
  - [x] 9.5 สร้าง CSS styles สำหรับสถานะ
    - กำหนด CSS classes สำหรับแต่ละสี (status-blue, status-orange, etc.)
    - สร้าง timeline styles สำหรับแสดงประวัติสถานะ
    - _Requirements: 4.1-4.7_

- [x] 10. เพิ่ม Font Awesome สำหรับไอคอน
  - เพิ่ม Font Awesome CDN link ใน templates
  - ตรวจสอบว่าไอคอนแสดงผลถูกต้อง
  - _Requirements: 2.2, 4.1-4.7_

- [x] 11. Write integration tests
  - ทดสอบ end-to-end flow: สร้างคำร้อง → เพิ่มสถานะ → ตรวจสอบ history
  - ทดสอบ user ไม่สามารถยื่นคำร้องซ้ำเมื่อมี active petition
  - ทดสอบ user สามารถยื่นคำร้องใหม่หลังจากคำร้องเดิมเสร็จสิ้น
  - ทดสอบ status history แสดงผลถูกต้องตามลำดับเวลา
  - _Requirements: 1.2, 1.3, 3.4, 5.1_

- [x] 12. Checkpoint สุดท้าย - ตรวจสอบระบบทั้งหมด
  - รัน tests ทั้งหมดให้ผ่าน (unit tests, property tests, integration tests)
  - ทดสอบ UI ด้วยตนเองว่าสีและไอคอนแสดงผลถูกต้อง
  - ตรวจสอบข้อความภาษาไทยแสดงผลถูกต้อง
  - ถามผู้ใช้หากมีคำถามหรือต้องการปรับปรุง

## หมายเหตุ

- Tasks ที่มีเครื่องหมาย `*` เป็น optional และสามารถข้ามได้เพื่อ MVP ที่เร็วขึ้น
- แต่ละ task อ้างอิง requirements เฉพาะเจาะจงเพื่อการตรวจสอบได้
- Checkpoints ช่วยให้มั่นใจว่าการพัฒนาเป็นไปอย่างถูกต้องในแต่ละขั้นตอน
- Property tests ตรวจสอบความถูกต้องแบบ universal
- Unit tests ตรวจสอบกรณีเฉพาะเจาะจงและ edge cases
- การใช้ JUnit-Quickcheck สำหรับ property-based testing ต้องเพิ่ม dependency ใน pom.xml ก่อน
