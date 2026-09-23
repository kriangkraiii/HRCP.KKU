# แผนงาน: ขยายขนาดตราประทับลายเซ็นดิจิทัลในเอกสารทั้งระบบ (Enlarge Digital Signature Stamp Project-Wide)

> เอกสารแผนงานตามคำสั่ง `/plan` สำหรับการขยายขนาดตราประทับลายเซ็นอิเล็กทรอนิกส์ (Digital Signature Stamp) ทั้งในเอกสารราชการ DOCX/PDF และระบบสร้างลายเซ็นทั้งโปรเจกต์

---

## 1. วัตถุประสงค์ (Objective)
ปรับขยายขนาดของตราประทับลายเซ็นดิจิทัล (Digital Signature Stamp) และหมึกลายเซ็น (Ink Signature) ในเอกสารราชการที่ระบบสร้างขึ้น (ทั้ง Phase 1: `doc_1` ถึง `doc_9` และ Phase 2: `p2doc_1` ถึง `p2doc_10`) ให้มีขนาดใหญ่ขึ้น ชัดเจน อ่านข้อมูลใบรับรองได้ง่าย และมีสัดส่วนที่สมดุลสวยงามสอดคล้องกับข้อความกำกับชื่อ-ตำแหน่งในเอกสารจริง โดยไม่ล้นขอบหรือกระทบโครงสร้างตารางของเอกสาร

---

## 2. การวิเคราะห์ปัญหาจากภาพจริง (Root Cause & Evidence Analysis)

จากภาพถ่ายเอกสารที่ผู้ใช้ส่งมา พบว่า:
1. **ขนาดตราประทับในเอกสารเล็กเกินไปอย่างเห็นได้ชัด:**
   - ใต้ลายเซ็นมีข้อความชื่อกำกับ: `(ผู้ช่วยศาสตราจารย์ สมชาย ใจดีวิชาการ)` ซึ่งมีความกว้างประมาณ 5.5 - 6.0 ซม. ในกระดาษ A4
   - แต่ตราประทับลายเซ็นด้านบนมีความกว้างเพียง **3.2 ซม.** และสูงเพียง **1.09 ซม.** เท่านั้น ทำให้ตราประทับดูกระจุกตัวเป็นกล่องเล็กๆ อยู่ตรงกลาง ไม่สมดุลกับชื่อด้านล่าง
2. **ข้อความ Metadata ของใบรับรองตัวเล็กจนอ่านยาก:**
   - ภายในความสูงเพียง 1.09 ซม. (ประมาณ 31 pt) มีข้อความถึง 6 บรรทัด (`Digitally signed by...`, `DN:...`, `email=...`, `Date:...`) ทำให้ข้อความถูกบีบเหลือขนาดเทียบเท่า ~2.7 pt ในการพิมพ์จริง
3. **หมึกลายเซ็นฝั่งซ้าย (Ink Stroke) ไม่ขยายตัว:**
   - ลายเซ็นแบบย่อ/ลายเซ็นสั้น (เช่น ตัวอักษร `V` ในรูปตัวอย่าง) เมื่อวาดหรืออัปโหลด ระบบมีโค้ดจำกัดสเกล `if (scale > 1.0f) scale = 1.0f;` ทำให้หมึกลายเซ็นไม่ถูกขยายให้เต็มกรอบฝั่งซ้าย (200x135 px) หมึกจึงกลายเป็นขีดเล็กๆ จมอยู่ในกรอบ

---

## 3. สาเหตุเชิงเทคนิคในซอร์สโค้ด (Technical Root Causes)

### 3.1 ขนาดพิมพ์ในเอกสารถูกจำกัดไว้ที่ 3.2 ซม. x 1.2 ซม.
ในไฟล์ [DocumentGenerationService.java](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/java/com/ecom/academic/service/DocumentGenerationService.java#L470-L485):
```java
/** Printed width of a stamped signature. Fits the dotted line in every template. */
private static final long SIGNATURE_WIDTH_EMU = (long) (3.2 * EMU_PER_CM); // 3.2 cm = 1,152,000 EMU

/** Ceiling on printed height, so a tall image cannot push the following line down. */
private static final long SIGNATURE_MAX_HEIGHT_EMU = (long) (1.2 * EMU_PER_CM); // 1.2 cm = 432,000 EMU

private static final long SIGNATURE_LINE_HEIGHT_TWIPS = SIGNATURE_MAX_HEIGHT_EMU / EMU_PER_TWIP; // 680 twips
```
- อัตราส่วนของรูปภาพตราประทับคือ `540 x 185` (อัตราส่วน 2.92 : 1)
- เมื่อกำหนดความกว้าง 3.2 ซม. ความสูงของรูปภาพจริงจะอยู่ที่:
  $$3.2 \times \frac{185}{540} \approx 1.096 \text{ ซม.}$$
- ส่งผลให้รูปภาพมีขนาดเล็กมากเมื่อเทียบกับมาตรฐานเอกสารราชการทั่วไป

### 3.2 ตัวคูณสเกลภาพหมึกลายเซ็นถูกจำกัดไม่ให้ขยายขึ้น (Scale Cap at 1.0x)
1. ในฝั่ง Java [UserSignatureService.java](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/java/com/ecom/academic/service/UserSignatureService.java#L453-L456):
   ```java
   float scale = Math.min((float) maxW / inkImg.getWidth(), (float) maxH / inkImg.getHeight());
   if (scale > 1.0f) {
       scale = 1.0f; // <--- ลายเซ็นขนาดเล็กจะไม่ถูกขยายให้เต็มพื้นที่ 200x135
   }
   ```
2. ในฝั่ง JavaScript [signature_pad.js](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/js/signature_pad.js#L413):
   ```js
   const scale = Math.min(maxW / inkW, maxH / inkH, 1.0); // <--- ถูกจำกัดเพดานที่ 1.0 เท่าเช่นกัน
   ```

---

## 4. ข้อเสนอแนวทางการขยายขนาด (Proposed Dimension Adjustments)

### 4.1 การเปรียบเทียบขนาดพิมพ์ในเอกสาร (Document Print Dimensions)

| รายการ | ขนาดเดิม (Current) | ตัวเลือก ก: ขยายปานกลาง (+56%) | **ตัวเลือก ข: แนะนำ (+62%)** | ตัวเลือก ค: ขยายใหญ่ (+72%) |
|---|---|---|---|---|
| **ความกว้าง (Width)** | **3.2 ซม.** (90.7 pt) | **5.0 ซม.** (141.7 pt) | **5.2 ซม.** (147.4 pt) | **5.5 ซม.** (155.9 pt) |
| **ความสูงจริง (Height)** | **1.10 ซม.** (31.1 pt) | **1.71 ซม.** (48.5 pt) | **1.78 ซม.** (50.5 pt) | **1.88 ซม.** (53.4 pt) |
| **เพดานความสูง (Max Height)** | 1.2 ซม. (432k EMU) | 1.8 ซม. (648k EMU) | 1.9 ซม. (684k EMU) | 2.0 ซม. (720k EMU) |
| **ความสูงบรรทัด (Twips)** | 680 twips | 1,020 twips | 1,077 twips | 1,133 twips |
| **ขนาดฟอนต์ Metadata จริง** | ~2.7 pt (อ่านยากมาก) | ~7.5 pt (คมชัด ชัดเจน) | ~7.8 pt (อ่านสบายตา) | ~8.3 pt (ใหญ่ชัดเจน) |
| **ความกว้างเทียบกับชื่อกำกับ** | ~50% ของชื่อ | ~85% ของชื่อ | ~90-95% ของชื่อ (พอดีสวยงาม) | ~100% ของชื่อ |
| **ความปลอดภัยในตาราง 2 คอลัมน์** | ปลอดภัยมาก | ปลอดภัย 100% | ปลอดภัย 100% (ไม่ล้นขอบ 8 ซม.) | อาจชิดขอบในบางเทมเพลต |

> **ข้อแนะนำ:** เลือก **ตัวเลือก ข (5.2 ซม. x 1.78 ซม.)** หรือ **ตัวเลือก ก (5.0 ซม. x 1.71 ซม.)** เนื่องจาก:
> - กว้างพอดีกับความกว้างของชื่อ-ตำแหน่งภาษาไทยด้านล่าง
> - ในตาราง 2 คอลัมน์ (เช่น ผู้ขอประเมิน vs หัวหน้าสาขา) แต่ละคอลัมน์กว้าง 7.5 - 8.0 ซม. การใช้ 5.0 - 5.2 ซม. จะเว้นขอบสวยงาม ไม่ดันข้อความตกบรรทัด
> - เมื่อมีคำว่า "ลงชื่อ" นำหน้าในบรรทัดเดียวกัน (กว้าง ~1.5 ซม.) ผลรวม 1.5 + 5.0 = 6.5 ซม. ยังคงอยู่ในระยะปลอดภัยของคอลัมน์ 8.0 ซม.

### 4.2 การปรับปรุงการขยายสัดส่วนหมึกลายเซ็น (Ink Auto-Fit Enhancement)
- ปลดล็อกข้อจำกัด `scale <= 1.0f` ใน `UserSignatureService.java` และ `signature_pad.js`
- อนุญาตให้ลายเซ็นขนาดเล็กขยายขึ้น (Auto-scale up) ได้สูงสุดถึง **2.2x - 2.5x** ของขนาดเดิม เพื่อให้เส้นลายเซ็นเติมเต็มกล่องซ้ายมือ (200x135 px) อย่างสวยงาม คมชัด และไม่จม

---

## 5. แผนการดำเนินงาน (Task Breakdown)

### ส่วนที่ 1: ปรับค่าคงที่ขนาดใน `DocumentGenerationService.java`
- ปรับขนาด `SIGNATURE_WIDTH_EMU` จาก `3.2 * EMU_PER_CM` เป็น `5.0 * EMU_PER_CM` (หรือ `5.2`)
- ปรับขนาด `SIGNATURE_MAX_HEIGHT_EMU` จาก `1.2 * EMU_PER_CM` เป็น `1.8 * EMU_PER_CM` (หรือ `1.9`)
- คำนวณ `SIGNATURE_LINE_HEIGHT_TWIPS` สอดคล้องกันอัตโนมัติ

### ส่วนที่ 2: ปรับปรุงการสเกลหมึกลายเซ็นใน `UserSignatureService.java`
- ปรับฟังก์ชัน `generateDigitalStampFromImage(...)` ในการคำนวณ `scale`:
  - ให้สามารถขยายลายเซ็นที่มีขนาดเล็กให้เต็มกรอบ `maxW = 200`, `maxH = 135` ได้ โดยกำหนดเพดาน `scale <= 2.5f`

### ส่วนที่ 3: ปรับปรุงการแสดงผลและ Preview ใน `signature_pad.js`
- ปรับ `drawDigitalSignatureStampWithInk(...)` ให้สเกลหมึกลายเซ็นฝั่งซ้ายสอดคล้องกับฝั่ง Java (สเกลสูงสุด 2.5x)
- ตรวจสอบความคมชัดของการแสดงผล Preview ในกล่อง `.sig-choice-thumb` และ `.sig-preview-box`

### ส่วนที่ 4: ตรวจสอบและอัปเดตชุดทดสอบ (Automated Unit & Integration Tests)
- ตรวจสอบชุดทดสอบ:
  - `SignatureStampingTest.java` (26 tests ครอบคลุมเอกสารทุกฉบับใน Phase 1 และ Phase 2)
  - `SigningPageRenderTest.java` (19 tests)
  - `DocumentRenderTest.java`
- ทดสอบเรนเดอร์เอกสารจริง (`doc_1.docx`, `doc_2.docx`, `p2doc_1.docx`) และแปลงเป็น PDF ด้วย LibreOffice เพื่อตรวจดูความสวยงามจริง

---

## 6. ผู้รับผิดชอบ (Agent Assignments)
- **ผู้วางแผน (Project Planner):** กำหนดขนาดที่เหมาะสมและจัดทำเอกสารแผนงาน
- **ผู้เชี่ยวชาญฝั่งเซิร์ฟเวอร์ (Backend Specialist):** ปรับปรุง `DocumentGenerationService.java` และ `UserSignatureService.java`
- **ผู้เชี่ยวชาญฝั่งหน้าบ้าน (Frontend Specialist):** ปรับปรุง `signature_pad.js` สำหรับการพรีวิวและการบันทึกลายเซ็น
- **ผู้เชี่ยวชาญการทดสอบ (Quality Assurance):** ตรวจสอบการเรนเดอร์ไฟล์ DOCX/PDF ทุกประเภท

---

## 7. แผนการตรวจสอบความถูกต้อง (Verification Plan)
- [ ] สัดส่วนตราประทับใน DOCX/PDF ขยายเป็นขนาดเป้าหมาย (~5.0 - 5.2 ซม.)
- [ ] ข้อความ Metadata 6 บรรทัดอ่านง่าย คมชัดทั้งในจอและเมื่อพิมพ์ลงกระดาษ A4
- [ ] หมึกลายเซ็นสั้น/ลายเซ็นย่อขยายเต็มกรอบอย่างสมดุล ไม่แตก และไม่จม
- [ ] ในเอกสารที่มี 2 คอลัมน์ (เช่น `doc_1`, `doc_2`) ลายเซ็นไม่ทับซ้อนและตารางไม่แตก
- [ ] ชุดทดสอบ `mvn test -Dtest=SignatureStampingTest,SigningPageRenderTest` ผ่าน 100%
