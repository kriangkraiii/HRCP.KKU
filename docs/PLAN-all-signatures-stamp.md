# Plan: ขยายรูปแบบตราประทับ Digital Signature Stamp ให้ครอบคลุมทุกประเภท (DRAW, UPLOAD, TYPE)

## 📌 Context & Objectives
จากผลการทดสอบการใช้งานตราประทับดิจิทัลรูปแบบใหม่สไตล์ Adobe Acrobat (ไร้ขอบ, มีลายน้ำริบบิ้น, มีบล็อก DN, และแสดงวันเวลาด้วยเลขไทย) ผู้ใช้มีความประสงค์ที่จะให้ **ลายเซ็นทุกประเภท ทั้งแบบวาด (DRAW), อัปโหลดรูปภาพ (UPLOAD), และพิมพ์ชื่อ (TYPE)** มีการจัดวางและประทับข้อมูลตราประทับดิจิทัลเหมือนกันทั้งหมด

เป้าหมายของงานนี้:
1. **หน้าสร้างลายเซ็น (`/esign/my-signatures`)**:
   - **แบบวาด (DRAW)**: เมื่อผู้ใช้วาดลายเซ็น ลายเส้นที่วาดจะถูกจัดวางอยู่ฝั่งซ้าย พร้อมลายน้ำริบบิ้นกึ่งกลาง และบล็อกข้อมูล Metadata DN + วันเวลาเลขไทยอยู่ฝั่งขวา
   - **แบบอัปโหลด (UPLOAD)**: เมื่อผู้ใช้อัปโหลดไฟล์รูปลายเซ็นหรือ PDF ลายเซ็นที่ตัดขอบพื้นหลังแล้วจะถูกจัดวางอยู่ฝั่งซ้าย พร้อมลายน้ำริบบิ้นกึ่งกลาง และบล็อกข้อมูล Metadata DN + วันเวลาเลขไทยอยู่ฝั่งขวา
   - **แบบพิมพ์ชื่อ (TYPE)**: ใช้ฟอนต์ภาษาไทยจัดวางฝั่งซ้าย (เสร็จสมบูรณ์แล้วในเฟสแรก)
2. **กระบวนการลงนามจริงในระบบ Workflow (`SignatureWorkflowService`)**:
   - ทุกประเภทลายเซ็น (DRAW, UPLOAD, TYPE) เมื่อผู้ลงนามกดยืนยันการลงนาม ระบบจะสร้างตราประทับที่มี **วันและเวลาจริง ณ วินาทีที่ลงนาม (`step.getSignedAt()`)** เสมอ

---

## 🎯 Architecture & Technical Design

### 1. Unified 540x185 Stamp Layout
ตราประทับของทั้ง 3 ประเภทจะมีขนาดมาตรฐานเท่ากันคือ **540 x 185 พิกเซล**:
- **พื้นที่ฝั่งซ้าย (X: 0 ถึง 230, กึ่งกลาง X=115, Y=100)**:
  - `TYPE`: แสดงชื่อ-นามสกุลภาษาไทยด้วยฟอนต์ Sarabun หนา 36px (หรือตัดคำ 2 บรรทัด)
  - `DRAW`: วาดลายเส้นที่ผู้ใช้วาดด้วยมือ ย่อ/ขยายสัดส่วนให้อยู่ในกรอบ 200 x 135 พิกเซล
  - `UPLOAD`: แสดงภาพลายเซ็นที่อัปโหลด ย่อ/ขยายสัดส่วนให้อยู่ในกรอบ 200 x 135 พิกเซล
- **พื้นที่กึ่งกลาง (X: 198 ถึง 282, Y: 15 ถึง 165)**:
  - วาดลายน้ำริบบิ้น Adobe Signature Ribbon Seal ด้วยสีชมพู/แดงโปร่งแสง (`rgba(230, 75, 95, ~0.30)`)
- **พื้นที่ฝั่งขวา (X: 260 ถึง 525)**:
  - บรรทัดที่ 1: `Digitally signed by [ชื่อ-นามสกุล]`
  - บรรทัดที่ 2: `DN: c=TH, o=Khon Kaen`
  - บรรทัดที่ 3: `University, cn=[ชื่อ-นามสกุล],`
  - บรรทัดที่ 4: `email=[อีเมลผู้ลงนาม]`
  - บรรทัดที่ 5: `Date: [ปี.เดือน.วัน ชั่วโมง:นาที:วินาที ด้วยเลขไทย]`
  - บรรทัดที่ 6: `+[เขตเวลา ๐๗'๐๐' ด้วยเลขไทย]`

---

## 📂 Proposed Changes

### Phase 1: Frontend Composition (`signature_pad.js` & `my_signatures.html`)
1. **`signature_pad.js`**:
   - เพิ่มฟังก์ชัน `renderStampFromInk(inkCanvasOrImage, signerName, signerEmail, dateObj)`:
     - รองรับการรับภาพหมึกจาก Canvas (DRAW) หรือ Image (UPLOAD)
     - จัดวางภาพหมึกลงในช่องฝั่งซ้ายของ Canvas 540x185
     - วาดลายน้ำริบบิ้นตรงกลาง
     - วาดข้อความ Metadata 6 บรรทัด
   - ปรับปรุง `refreshPreview()` ให้เรียก `renderStampFromInk` สำหรับ `DRAW`
   - ปรับปรุง `handleUpload()` และ `handlePdfUpload()` ให้เรียก `renderStampFromInk` สำหรับ `UPLOAD`
   - เชื่อมโยงชื่อและอีเมลของผู้ใช้เข้าสู่ตัวสร้างพรีวิว
2. **`my_signatures.html`**:
   - ส่ง `th:data-user-name="${currentUser != null ? currentUser.name : ''}"` ควบคู่กับ `data-user-email`
   - ปรับคำอธิบาย `sigTypeNotice` ให้ครอบคลุมทุกประเภทลายเซ็น (DRAW, UPLOAD, TYPE)

### Phase 2: Backend Image Compositing & Dynamic Timestamp Refresh (`UserSignatureService.java` & `SignatureWorkflowService.java`)
1. **`UserSignatureService.java`**:
   - เพิ่มเมธอด:
     ```java
     public byte[] generateDigitalStampFromImage(byte[] rawOrStampPng, String signerName, String signerEmail, LocalDateTime signedAt)
     ```
     - ตรวจสอบขนาดภาพ: หากเป็นภาพตราประทับ 540x185 อยู่แล้ว จะดึงเฉพาะส่วนลายเซ็นฝั่งซ้ายมา Re-render วันเวลาใหม่ ณ วินาทีที่ลงนาม
     - หากเป็นภาพลายเซ็นดิบ (Raw Ink) จะจัดขนาดและวางลงในฝั่งซ้ายของตราประทับ 540x185
   - เพิ่มเมธอด:
     ```java
     public String generateAndStoreDigitalStampFromImage(byte[] rawOrStampPng, String signerName, String signerEmail, LocalDateTime signedAt)
     ```
2. **`SignatureWorkflowService.java`**:
   - บรรทัด 724-735: ขยายเงื่อนไขการสร้าง Fresh Digital Stamp จากเดิมที่ตรวจเฉพาะ `SignatureKind.TYPE` ให้ครอบคลุมทั้ง `SignatureKind.DRAW` และ `SignatureKind.UPLOAD` โดยเรียก `generateAndStoreDigitalStampFromImage` เพื่อให้ตราประทับในเอกสารจริงได้รับเวลา `step.getSignedAt()` ที่แท้จริงเสมอ

---

## 🧪 Verification Plan

### Automated Tests
1. **`UserSignatureServiceTest` / `SigningPageRenderTest`**:
   - ทดสอบ `generateDigitalStampFromImage` ด้วยภาพตัวอย่างลายเซ็นแบบหมึก
   - ทดสอบการตัดแยกหมึกฝั่งซ้ายและประกอบเป็นตราประทับ 540x185 พร้อมวันเวลาเลขไทย
2. **`SignatureStampingTest`**:
   - ยืนยันว่าการฝังภาพตราประทับ 540x185 ทั้ง 3 ประเภทลงใน DOCX ทุก Template ทำงานถูกต้อง 100%
3. **Full Suite (`./mvnw test`)**:
   - ยืนยัน 1,307 การทดสอบผ่านทั้งหมด

### Manual Verification
1. เปิดหน้า `/esign/my-signatures`:
   - วาดลายเซ็นในแท็บ **วาดลายเซ็น (DRAW)** -> กล่องตัวอย่างต้องแสดงลายเซ็นที่วาดคู่กับลายน้ำและ Metadata
   - อัปโหลดรูปลายเซ็นในแท็บ **อัปโหลด (UPLOAD)** -> กล่องตัวอย่างต้องแสดงภาพลายเซ็นคู่กับลายน้ำและ Metadata
   - พิมพ์ชื่อในแท็บ **พิมพ์ชื่อ (TYPE)** -> กล่องตัวอย่างต้องแสดงชื่อภาษาไทยคู่กับลายน้ำและ Metadata
2. ทดสอบลงนามในเอกสารคำร้องจริงเพื่อตรวจสอบตราประทับบนไฟล์ PDF และ Word
