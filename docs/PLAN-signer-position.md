# Plan: เพิ่มตำแหน่ง (Position) ในตราประทับ Digital Signature Stamp

## 📌 Context & Objectives
ผู้ใช้ต้องการให้ตราประทับลายเซ็นดิจิทัล (Digital Signature Stamp) แสดง **ตำแหน่ง (Position)** ของผู้ลงนามด้วย หากในโปรไฟล์ผู้ใช้งานมีระบุตำแหน่งไว้ (เช่น ตำแหน่งทางวิชาการ: ผู้ช่วยศาสตราจารย์, รองศาสตราจารย์, ศาสตราจารย์, อาจารย์ หรือตำแหน่งบริหาร)

---

## 🔍 ข้อมูลปัจจุบันในระบบ
1. **Model `UserDtls`**:
   - `academicPosition`: ตำแหน่งทางวิชาการภาษาไทย (เช่น อาจารย์, ผู้ช่วยศาสตราจารย์, รองศาสตราจารย์, ศาสตราจารย์)
   - `academicPositionEn`: ตำแหน่งทางวิชาการภาษาอังกฤษ (เช่น Assistant Professor, Associate Professor)
   - `title`: คำนำหน้าชื่อ (เช่น ผศ.ดร., รศ.ดร., ศ.ดร., ดร., นาย, นาง, นางสาว)
2. **Model `SignatureStep`**:
   - มีฟิลด์ `signerPositionSnapshot` อยู่แล้ว ซึ่งระบบบันทึกค่า `signer.getAcademicPosition()` ไว้ตอนสร้างเอกสารเวียนลงนาม
3. **การแสดงผลปัจจุบัน**:
   - แสดงเฉพาะชื่อ-นามสกุล, DN มหาวิทยาลัยขอนแก่น, อีเมล, และวันเวลาเลขไทย ยังไม่มีการดึงฟิลด์ตำแหน่งมาใส่ในข้อความตราประทับ

---

## 🎨 ทางเลือกในการจัดวางตำแหน่ง (Design Options)

### ทางเลือกที่ 1 (มาตรฐาน X.500 DN: `title=...`):
แทรกแอตทริบิวต์ `title=[ตำแหน่ง]` ในบล็อก Distinguished Name (DN)

### ทางเลือกที่ 2 (บรรทัดเฉพาะแยกต่างหากใต้ชื่อ):
แสดงบรรทัดตำแหน่งโดยตรงใต้บรรทัด Digitally signed by

### ✅ ทางเลือกที่ 3 (กำหนดให้มีเฉพาะกรณีที่ 2 และ 3 เท่านั้น):
1. **กรณีที่ 2: มีคำนำหน้าทางวิชาการ (Academic Title/Rank)** (เช่น `ผศ.ดร.`, `รศ.ดร.`, `ศ.ดร.`, `ผศ.`, `รศ.`, `ศ.`, `ดร.`, `อาจารย์`, `อ.ดร.`):
   นำคำนำหน้าทางวิชาการมาแสดงหน้าชื่อเสมอ
   ```text
   Digitally signed by ผศ.ดร.เกรียงไกร เกียรติบูรณกุล
   DN: c=TH, o=Khon Kaen
   University, cn=ผศ.ดร.เกรียงไกร เกียรติบูรณกุล,
   email=admin@admin.com
   Date: ๒๕๖๙.๐๙.๑๑ ๑๖:๑๐:๑๔
   +๐๗'๐๐'
   ```
2. **กรณีที่ 3: ไม่มีคำนำหน้าทางวิชาการในโปรไฟล์** (หรือคำนำหน้าเป็น นาย, นาง, นางสาว หรือไม่ได้ระบุ):
   แสดงเฉพาะชื่อ-นามสกุลโดยตรง (ตัดคำนำหน้าทั่วไปออก และไม่นำตำแหน่งเต็มอย่าง "ผู้ช่วยศาสตราจารย์" มาต่อหน้าชื่อ)
   ```text
   Digitally signed by กอเอ๋ยกอไก่ อ้ายยังจำได้มั้ย
   DN: c=TH, o=Khon Kaen
   University, cn=กอเอ๋ยกอไก่ อ้ายยังจำได้มั้ย,
   email=admin@admin.com
   Date: ๒๕๖๙.๐๙.๑๑ ๑๖:๑๐:๑๔
   +๐๗'๐๐'
   ```
*(ตัดกรณีที่ 1 ที่เคยนำคำว่า "ผู้ช่วยศาสตราจารย์" มาไว้หน้าชื่อออกทั้งหมด)*

---

## 📂 Proposed Changes

### 1. Frontend (`my_signatures.html` & `signature_pad.js`)
- **`my_signatures.html`**:
  - ส่ง `th:data-user-position="${currentUser != null ? (currentUser.academicPosition ?: '') : ''}"` ในฟอร์มสร้างลายเซ็น
  - ส่ง `th:data-user-title="${currentUser != null ? (currentUser.title ?: '') : ''}"`
- **`signature_pad.js`**:
  - เพิ่มฟังก์ชัน `getResolvedUserPosition()` ดึงตำแหน่งจาก attribute
  - ปรับปรุง `drawRightMetadata` ให้รับพารามิเตอร์ `signerPosition`
  - หากมีตำแหน่ง ให้แสดงผลตามรูปแบบที่ตกลง (ถ้าไม่มีตำแหน่ง ให้แสดงตามรูปแบบเดิมโดยไม่มีช่องว่างแหว่ง)

### 2. Backend Java (`UserSignatureService.java` & `SignatureWorkflowService.java`)
- **`UserSignatureService.java`**:
  - เพิ่มฟังก์ชัน overload สำหรับ `generateDigitalStampPng` และ `generateDigitalStampFromImage` ที่รับ `signerPosition`
  - ปรับแต่ง `drawRightMetadata(g2, signerName, signerPosition, signerEmail, signedAt, ...)`
  - รองรับ Fallback อัตโนมัติ: หาก `signerPosition` เป็น `null` หรือว่าง ให้แสดงผล 6 บรรทัดเหมือนเดิมโดยไม่กระทบความกว้าง/ความสูง
- **`SignatureWorkflowService.java`**:
  - ส่ง `step.getSignerPositionSnapshot()` ไปยัง `userSignatureService.generateAndStoreDigitalStamp`
- **`SignedDocumentRenderer.java`**:
  - ส่ง `step.getSignerPositionSnapshot()` หรือ `user.getAcademicPosition()` ไปยังตัวเรนเดอร์พรีวิวเอกสาร

---

## 🧪 Verification Plan
1. **Unit & Render Tests (`SigningPageRenderTest.java`)**:
   - ทดสอบการสร้างตราประทับกรณีมีตำแหน่ง (ระบุ `ผู้ช่วยศาสตราจารย์`) -> ตรวจสอบว่าภาพเรนเดอร์สำเร็จและมีข้อความตำแหน่ง
   - ทดสอบการสร้างตราประทับกรณีไม่มีตำแหน่ง (เป็น `null` หรือค่าว่าง) -> ตรวจสอบว่าภาพคงขนาด 540x185 และเรนเดอร์ปกติ
2. **Workflow Test (`SignatureWorkflowServiceTest.java`)**:
   - ทดสอบว่าในขั้นตอนลงนามจริง บันทึกตำแหน่งและตราประทับมีข้อมูลครบถ้วน
3. **Manual Verification**:
   - ตรวจสอบหน้า `/esign/my-signatures` ของบัญชีที่มีตำแหน่งและไม่มีตำแหน่ง
   - สังเกตตัวอย่างลายเซ็น (Preview) ทั้ง 3 แท็บ (วาด, อัปโหลด, พิมพ์ชื่อ)
