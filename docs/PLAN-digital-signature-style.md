# Plan: ปรับแต่งตราประทับ Digital Signature Stamp ให้เหมือนรูปแบบ Adobe Acrobat ตามภาพตัวอย่าง

## 📌 Context & Problem Statement
ระบบ `HRCP-KKU-Academic` มีระบบสร้างตราประทับลายเซ็นดิจิทัล (Digital Signature Stamp) สำหรับประเภทพิมพ์ชื่อ (SignatureKind.TYPE) ทั้งทางฝั่ง Java Backend (`UserSignatureService.generateDigitalStampPng`) และฝั่ง Frontend Preview (`signature_pad.js - drawDigitalSignatureStamp`)

จากการเปรียบเทียบภาพตราประทับดิจิทัลมาตรฐาน Adobe Acrobat ที่ผู้ใช้ส่งมา พบความแตกต่างที่ต้องปรับปรุงดังนี้:
1. **เส้นขอบ (Border)**: ปัจจุบันมีกรอบสี่เหลี่ยมสีเทา (`#c8c8c8`) ล้อมรอบ — ในภาพต้นแบบ **ไม่มีกรอบสี่เหลี่ยม** (Borderless / Clean transparent)
2. **ลายน้ำริบบิ้น (Watermark Ribbon)**: ในภาพต้นแบบมีลายน้ำริบบิ้นวงแหวนสีชมพู/แดงโปร่งแสง (Adobe Signature Ribbon Seal) วาดอยู่กึ่งกลางด้านหลังข้อความ
3. **การจัดวางชื่อฝั่งซ้าย**: แสดงชื่อภาษาไทยขนาดใหญ่ ปรับขนาดและการตัดคำให้อ่านง่าย
4. **บล็อกข้อมูล Metadata ฝั่งขวา**:
   - บรรทัดที่ 1: `Digitally signed by [ชื่อ-นามสกุล]` (แสดงป้ายกำกับและชื่อบนบรรทัดเดียวกัน)
   - บรรทัดที่ 2-4: ข้อมูล Distinguished Name (DN) ตามมาตรฐาน X.500:
     - `DN: c=TH, o=Khon Kaen`
     - `University, cn=[ชื่อ-นามสกุล],`
     - `email=[อีเมลผู้ลงนาม]`
   - บรรทัดที่ 5-6: วันเวลาและ Timezone ด้วย **เลขไทย (Thai Numerals)**:
     - `Date: ๒๐๒๖.๐๘.๒๔ ๑๕:๔๗:๐๙`
     - `+๐๗'๐๐'`

---

## 🎯 Proposed Solution Architecture

### 1. Thai Numerals Utility
สร้างฟังก์ชันแปลงตัวเลขอารบิก 0-9 เป็นเลขไทย ๐-๙ สำหรับทั้งฝั่ง Java และ JavaScript:
- Java: `toThaiDigits(String str)` ใน `UserSignatureService`
- JS: `toThaiDigits(str)` ใน `signature_pad.js`

### 2. Adobe Ribbon Vector Watermark
วาดเส้นริบบิ้นโค้งมนกึ่งกลางตราประทับด้วยสีชมพูโปร่งแสง (Alpha ~ 0.25 - 0.35) ทั้งใน:
- Java 2D Graphics: ใช้ `java.awt.geom.Path2D` / `CubicCurve2D` ด้วยสี `new Color(225, 65, 80, 65)`
- HTML5 Canvas: ใช้ `ctx.bezierCurveTo` ด้วยสี `rgba(225, 65, 80, 0.28)`

### 3. Distinguished Name (DN) Formatting & Extraction
ดึงข้อมูล DN จากใบรับรองดิจิทัล `.p12` ที่เปิดใช้งานอยู่ของผู้ใช้ (`UserDigitalCertificate.subjectDn`) หากมี หรือประกอบจากข้อมูลบัญชีผู้ใช้:
- `c=TH`
- `o=Khon Kaen University`
- `cn=${signerName}`
- `email=${signerEmail}`

ตัดขึ้นบรรทัดใหม่อย่างเป็นระเบียบตามภาพตัวอย่าง:
```
DN: c=TH, o=Khon Kaen
University, cn=สุธน เจริญศิริ,
email=sutoch@kku.ac.th
```

### 4. Date & Timezone Formatting
แปลงวันเวลาลงนาม (Signed Timestamp):
- วันที่และเวลา: รูปแบบ `yyyy.MM.dd HH:mm:ss` -> แปลงเป็นเลขไทย เช่น `๒๐๒๖.๐๘.๒๔ ๑๕:๔๗:๐๙`
- ไทม์โซน: รูปแบบ `+07'00'` -> แปลงเป็นเลขไทย `+๐๗'๐๐'`

---

## 📂 Proposed Changes

### Backend Components
1. **`UserSignatureService.java`**
   - เพิ่มฟังก์ชัน `toThaiDigits(String text)`
   - ปรับปรุง `generateDigitalStampPng`:
     - เพิ่มพารามิเตอร์รับอีเมลและ DN (พร้อม overload เดิมเพื่อ backward compatibility)
     - นำกรอบสี่เหลี่ยมสีเทาออก (`drawRect`)
     - วาด Adobe ribbon watermark ด้วย `Graphics2D`
     - จัดวางข้อความฝั่งขวา 6 บรรทัดตามภาพ
   - ปรับปรุง `generateAndStoreDigitalStamp` ให้ส่งต่ออีเมลผู้ลงนาม
2. **`SignatureWorkflowService.java`**
   - ส่งอีเมลของผู้ลงนาม (`step.getSigner().getEmail()`) เข้าไปที่ `generateAndStoreDigitalStamp`
3. **`SignedDocumentRenderer.java`**
   - ส่งอีเมลของผู้ลงนามในขั้นตอนพรีวิวเข้าไปที่ `generateDigitalStampPng`
4. **`UserSignatureController.java`**
   - ส่งข้อมูลอีเมลผู้ใช้ปัจจุบัน (`currentUser.getEmail()`) และ DN ไปยังหน้าแบบฟอร์ม

### Frontend Components
1. **`signature_pad.js`**
   - ปรับปรุง `drawDigitalSignatureStamp`:
     - นำ `ctx.strokeRect` ออก
     - วาดเส้นริบบิ้นโค้งมนสไตล์ Adobe ด้วย `ctx.bezierCurveTo`
     - รองรับพารามิเตอร์อีเมลผู้ใช้
     - จัดวางข้อความ Metadata ฝั่งขวา: `Digitally signed by ...`, `DN: ...`, และวันเวลาเลขไทย
2. **`my_signatures.html`**
   - ส่งผ่านข้อมูล `data-user-email` และ `data-user-dn` ให้สคริปต์ `signature_pad.js` นำไปเรนเดอร์พรีวิว

---

## 🧪 Verification Plan
1. **Unit & Integration Tests**:
   - อัปเดต `SigningPageRenderTest.generateDigitalStampProducesValidPng()` ให้ตรวจสอบภาพตราประทับแบบใหม่
   - รันชุดทดสอบที่เกี่ยวข้อง:
     - `UserSignatureControllerTest`
     - `SigningPageRenderTest`
     - `SignatureStampingTest`
2. **Visual Inspection**:
   - เปรียบเทียบผลลัพธ์ PNG ที่สร้างขึ้นกับภาพอ้างอิงของผู้ใช้แบบพิกเซลต่อพิกเซล
   - ทดสอบหน้าพรีวิว `/esign/my-signatures` เมื่อพิมพ์ชื่อ
   - ทดสอบการลงนามจริงในเอกสารคำร้อง
