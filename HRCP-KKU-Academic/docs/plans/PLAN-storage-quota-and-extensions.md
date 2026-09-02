# แผนการพัฒนา (Project Plan): จำกัดประเภทไฟล์จัดเก็บเป็น PDF, DOCX, DOC, ZIP ทั้งระบบ และลดโควตาพื้นที่ผู้ใช้ทั่วไปเป็น 512MB (Storage Quota & Extensions Standardization)

##  ข้อมูลเบื้องต้น (Overview & Context)
- **ความต้องการของผู้ใช้ (User Request):**
  1. คลังจัดเก็บไฟล์ (Storage Manager / File Storage) ทั้งของ Admin และ User (ทั้งโปรเจค) อนุญาตให้จัดเก็บได้เฉพาะไฟล์: **`.pdf`, `.docx`, `.doc`, `.zip`** เท่านั้น (ไม่อนุญาตไฟล์ Office ตระกูล Spreadsheet/Presentation อื่นๆ เช่น xls, xlsx, ppt, pptx, txt, rtf, odt, ods)
  2. ปรับลดขนาดโควตาพื้นที่จัดเก็บไฟล์ของผู้ใช้ทั่วไป (User Storage Quota) จากเดิม 1GB (1,073,741,824 bytes) ลงมาเหลือ **512MB** (536,870,912 bytes)

---

##  วัตถุประสงค์ (Objectives)
1. กำหนด Whitelist นามสกุลไฟล์ในระบบจัดเก็บไฟล์ (File Storage System) ทั้งหมดให้ตรงกันทั้งระบบคือ **`.pdf`, `.docx`, `.doc`, `.zip`**
2. ปรับลดขนาดพื้นที่จัดเก็บไฟล์สูงสุดของผู้ใช้ทั่วไป (`app.storage.user.max-bytes`) เป็น **512 MB** (536,870,912 bytes)
3. ตรวจสอบความถูกต้องทั้ง Client-side (JavaScript, file input accept attribute) และ Server-side (Service validation, Spring properties)
4. เพิ่ม Automated Unit Tests เพื่อยืนยันว่าไฟล์ที่อนุญาต (PDF, DOCX, DOC, ZIP) สามารถอัปโหลดได้ตามปกติ และไฟล์ประเภทอื่นถูกปฏิเสธ พร้อมยืนยันโควตา 512MB

---

## ️ รายละเอียดการปรับปรุง (Proposed Changes)

### 1. Configuration & Properties
- **ไฟล์:** `src/main/resources/application.properties`
  - ปรับปรุง Section 8 (Storage Quota):
    ```properties
    # 8. Storage quota
    app.storage.user.max-bytes=${USER_STORAGE_MAX_BYTES:536870912}
    app.storage.user.allowed-extensions=pdf,doc,docx,zip

    app.storage.admin.max-bytes=${ADMIN_STORAGE_MAX_BYTES:10737418240}
    app.storage.admin.allowed-extensions=pdf,doc,docx,zip
    ```

### 2. Service Layer
- **ไฟล์:** `src/main/java/com/ecom/academic/service/UserStorageService.java`
  - ปรับค่า Default `@Value` ใน Constructor:
    - `maxStorageBytes`: `536870912` (512MB)
    - `allowedExtensionsStr`: `"pdf,doc,docx,zip"`
- **ไฟล์:** `src/main/java/com/ecom/academic/service/AdminStorageService.java`
  - ปรับค่า Default `@Value` ใน Constructor:
    - `allowedExtensionsStr`: `"pdf,doc,docx,zip"`

### 3. Frontend / UI & JavaScript
- **ไฟล์:** `src/main/resources/static/js/admin-storage.js`
  - อัปเดต `var ALLOWED_EXTENSIONS = ['pdf', 'doc', 'docx', 'zip'];`
- **ไฟล์:** `src/main/resources/templates/academic/admin/storage_content.html`
  - กำหนด `accept=".pdf,.doc,.docx,.zip"` บน `<input type="file" id="fileUploadInput">`
  - อัปเดตข้อความ Dropzone แนะนำนามสกุลไฟล์
- **ไฟล์:** `src/main/resources/templates/academic/applicant/user_storage.html`
  - กำหนด `accept=".pdf,.doc,.docx,.zip"` บน `<input type="file" id="fileUploadInput">`
  - เพิ่ม Client-side Validation ตรวจสอบนามสกุลไฟล์ในฟังก์ชัน `uploadFiles` เพื่อแจ้งเตือนทันทีก่อนอัปโหลด
  - อัปเดตข้อความ Dropzone แนะนำนามสกุลไฟล์และขนาดโควตา 512MB

### 4. Automated Tests
- **สร้างไฟล์:** `src/test/java/com/ecom/academic/service/StorageQuotaAndExtensionsTest.java`
  - ทดสอบ `UserStorageService.validateFileType` กับ `.pdf`, `.docx`, `.doc`, `.zip` (สำเร็จ)
  - ทดสอบ `UserStorageService.validateFileType` กับ `.xlsx`, `.ppt`, `.txt`, `.png` (ถูกปฏิเสธ)
  - ทดสอบ `AdminStorageService.validateFileType`
  - ทดสอบ `UserStorageService.validateStorageQuota` ตรวจสอบขีดจำกัด 512 MB (536,870,912 bytes)

---

##  แผนการทดสอบ (Verification Plan)

### Automated Tests
- รัน `./mvnw test -Dtest=StorageQuotaAndExtensionsTest,ChunkedUploadSecurityTest`
- รัน `./mvnw test` เพื่อตรวจสอบทั้งโปรเจค

### Manual Verification
- ทดสอบเข้าหน้า `/user/academic/storage` เพื่อดูการแสดงผลโควตา 512 MB
- ทดสอบอัปโหลดไฟล์ `.pdf`, `.docx`, `.doc`, `.zip` สำเร็จ
- ทดสอบอัปโหลดไฟล์ `.xlsx` หรือ `.txt` ระบบจะแจ้งเตือนไม่อนุญาต

---

##  ลำดับขั้นตอนการดำเนินงาน (Execution Steps)
- [ ] **Phase 1:** ปรับปรุง `application.properties` สำหรับโควตา 512MB และ Whitelist นามสกุล `pdf,doc,docx,zip`
- [ ] **Phase 2:** ปรับปรุง `UserStorageService.java` และ `AdminStorageService.java`
- [ ] **Phase 3:** ปรับปรุง `admin-storage.js`, `storage_content.html`, และ `user_storage.html`
- [ ] **Phase 4:** สร้าง Unit Tests `StorageQuotaAndExtensionsTest.java` และรัน `./mvnw test`
