# Project Plan: แก้ไขกล่องแสดงผลการคาดการณ์ค้นหา (Search Autocomplete Dropdown Overflow Fix)

> **เอกสารแผนงาน:** `docs/PLAN-search-dropdown-fix.md`  
> **ผู้รับผิดชอบหลัก:** `@[project-planner]` ร่วมกับ `@[frontend-specialist]`  
> **สถานะ:** รอยืนยันเพื่อเริ่มพัฒนา (Planning Phase — NO CODE MODIFIED YET)

---

## 1. ที่มาและปัญหาที่พบ (Problem Statement & Background)

จากการทดสอบใช้งานแถบค้นหาด่วน (Global Quick Search Omnibox) เมื่อพิมพ์คำค้นหา (เช่น "ธน") พบปัญหาในการแสดงผลผลการคาดการณ์ค้นหา (Predictive Search Dropdown) ดังนี้:

1. **ป้าย Badge ลอยทะลุขอบกล่อง (Badges Overflowing Dropdown Container):**
   - ป้ายสถานะ/บทบาท เช่น `"บุคลากร"` (สีเขียว) และ `"ผู้ยื่นคำร้อง"` (สีเทา) หลุดลอยออกไปอยู่นอกกรอบสีขาวของกล่องผลลัพธ์ทางด้านขวา ลอยทับหน้าเว็บภายนอก
   - **สาเหตุเชิงลึก:**
     - ข้อมูลย่อย (`subtitle`) เช่น สังกัดห้องแล็บ `"Machine Learning and Intelligent Systems (MLIS) Lab | ประเภท: -"` มีความยาวมาก
     - โครงสร้าง Flexbox ใน JavaScript เรียกใช้คลาส `.min-width-0` แต่ในไฟล์ CSS รวมของระบบ (`style.css`) ไม่เคยมีการประกาศ Utility `.min-width-0 { min-width: 0 !important; }` ไว้
     - ตามมาตรฐาน CSS Flexbox หากไม่ได้ระบุ `min-width: 0` ข้อความและบล็อกลูกจะคงขนาดขั้นต่ำตามเนื้อหา (`min-width: auto`) ทำให้คำสั่งตัดข้อความ `text-truncate` ไม่ทำงาน และดันให้กว้างเกินกล่อง
     - ตัวป้าย Badge มีคลาส `flex-shrink-0` จึงถูกผลักหลุดออกไปทางขวาตามความกว้างของข้อความ
     - คอนเทนเนอร์ `#globalSearchResults` ไม่ได้กำหนด `overflow-x: hidden` ทำให้ส่วนที่ล้นไม่ถูกตัดทิ้ง

2. **ขนาดความกว้างของกล่องค้นหาแคบเกินไป (Cramped Dropdown Width):**
   - ตัวคลุม `.global-search-container` ถูกฟิกซ์ขนาดไว้ที่ `max-width: 320px; width: 100%;` ซึ่งสำหรับภาษาไทยที่มีทั้งยศตำแหน่งวิชาการ ชื่อ-นามสกุล สังกัด อีเมล และป้าย Badge ความกว้าง 320px ทำให้เนื้อหาถูกบีบอัดมากจนเกินไป

3. **หัวข้อหมวดหมู่แสดงซ้ำซ้อนสลับไปมา (Duplicated Category Headers):**
   - ผลการค้นหาแสดงหัวข้อ `"จัดการบุคลากร"` และ `"ผู้ใช้งานระบบ"` สลับกันไปมาหลายรอบ (เช่น จัดการบุคลากร 2 คน -> ผู้ใช้งานระบบ 2 คน -> จัดการบุคลากร 1 คน -> ผู้ใช้งานระบบ 1 คน)
   - **สาเหตุเชิงลึก:**
     - API `/api/global-search` ส่งรายการเรียงตามคะแนนความเกี่ยวข้อง (Relevance Score) ทำให้รายการของแต่ละหมวดปะปนกัน
     - โค้ดใน `global-search.js` ตรวจสอบเพียง `item.category !== currentCat` เมื่อหมวดสลับไปมา จึงสร้าง `dropdown-header` ซ้ำทุกครั้ง

---

## 2. แนวทางการแก้ไขทางเทคนิค (Technical Solution)

### 2.1 แก้ไขปัญหา Badge ลอยทะลุขอบ และตัดข้อความ (Overflow & Truncation)
1. **เพิ่ม Utility Class ใน CSS (`style.css`):**
   - ประกาศคลาส `.min-width-0 { min-width: 0 !important; }` ให้กับระบบ ซึ่งจะช่วยแก้ไขและป้องกันปัญหานี้ในทุกจุดของโปรเจกต์ (เช่น ใน `search.html`, `notifications.html`, `global-search.js`)
2. **ปรับแต่ง Dropdown Item ใน CSS:**
   - กำหนดให้ `.global-search-dropdown`:
     - `overflow-x: hidden !important;`
     - ปรับความกว้างให้เหมาะสม เช่น `min-width: 380px;` หรือ `min-width: 420px;` (และจำกัดไม่ให้เกินหน้าจอมือถือ `max-width: calc(100vw - 32px);`)
   - กำหนดให้ `.global-search-dropdown .search-result-item`:
     - ป้องกันการล้นด้วย `width: 100%; max-width: 100%; overflow: hidden;`
     - ส่วนแสดงเนื้อหาฝั่งซ้าย (`flex-grow: 1; min-width: 0;`)
     - ส่วนป้าย Badge ฝั่งขวา (`flex-shrink: 0; margin-left: auto;`) ให้อยู่ชิดขอบในของกล่องเสมอ

### 2.2 ปรับปรุงการจัดกลุ่มหมวดหมู่ (Category Grouping)
- ใน `global-search.js`:
  - ก่อนที่จะวน Loop แสดงผล ให้จัดกลุ่มผลการค้นหาตาม Category (Group by category) หรือเรียงลำดับรายการตามหมวดหมู่โดยคงลำดับความเกี่ยวข้องภายในหมวดหมู่ไว้
  - ทำให้หัวข้อหมวดหมู่ เช่น `"จัดการบุคลากร"` แสดงเพียงครั้งเดียว และรวบรวมบุคลากรที่เกี่ยวข้องทั้งหมดไว้ในหมวดนั้น จากนั้นจึงตามด้วยหมวด `"ผู้ใช้งานระบบ"`

---

## 3. แผนการดำเนินงานและไฟล์เป้าหมาย (Task Breakdown)

| ลำดับ | รายการงาน | ไฟล์เป้าหมาย | รายละเอียดการแก้ไข |
|:---:|:---|:---|:---|
| **Phase 1** | **เพิ่ม CSS Rules & ป้องกัน Overflow** | `src/main/resources/static/css/style.css` | 1. เพิ่ม `.min-width-0 { min-width: 0 !important; }`<br>2. เพิ่มสไตล์ให้ `.global-search-dropdown` (`overflow-x: hidden`, ปรับ `min-width: 400px;`)<br>3. จัดแต่ง Layout ของ `.search-result-item` ให้ตัดข้อความและวาง Badge ให้เรียบร้อย |
| **Phase 2** | **ปรับโครงสร้าง DOM & จัดกลุ่มหมวดหมู่** | `src/main/resources/static/js/global-search.js` | 1. ปรับ `renderSearchResults` ให้จัดกลุ่มรายการตามหมวดหมู่ก่อนเรนเดอร์ (แก้หัวข้อหมวดซ้ำ)<br>2. ปรับโครงสร้าง HTML ภายใน item ให้มี `min-width: 0` และ `flex-grow: 1` ที่สมบูรณ์ |
| **Phase 3** | **ปรับแต่งตำแหน่ง Dropdown ใน Template** | `src/main/resources/templates/academic/base_academic.html` | 1. ปรับขนาดและตำแหน่งของ `#globalSearchResults` ให้ responsive ไม่ตกขอบจอ<br>2. ตรวจสอบการแสดงผลทั้งธีมสว่างและธีมมืด (Dark mode) |
| **Phase 4** | **ตรวจสอบและทดสอบ (Verification)** | Browser & Tests | 1. ทดสอบค้นหาคำค้นยาวและคำค้นที่มีหลายหมวด เช่น "ธน", "พิพัธน์"<br>2. ตรวจสอบว่าป้าย Badge อยู่ในกรอบอย่างสวยงาม ไม่ล้นออกมา<br>3. ตรวจสอบการจัดกลุ่มหมวดหมู่ไม่ซ้ำซ้อน<br>4. ตรวจสอบบนหน้าจอ Desktop, Tablet และ Mobile |

---

## 4. แผนการทดสอบและเกณฑ์การตรวจรับ (Verification Criteria)

- [ ] เมื่อพิมพ์คำค้นหา เช่น `"ธน"` หรือข้อความที่มีคำอธิบายยาว ป้ายสถานะ `"บุคลากร"` และ `"ผู้ยื่นคำร้อง"` ต้องอยู่ภายในกรอบสีขาวของกล่องค้นหา ไม่หลุดลอยออกมาภายนอก
- [ ] ข้อความชื่อและสังกัดที่ยาวเกินไป จะถูกตัดด้วย `...` (Ellipsis) อย่างเรียบร้อย
- [ ] หัวข้อหมวดหมู่ เช่น `"จัดการบุคลากร"`, `"ผู้ใช้งานระบบ"` จะต้องไม่แสดงซ้ำซ้อน
- [ ] ความกว้างของกล่องผลลัพธ์อ่านง่ายสบายตา และไม่ล้นออกนอกจอเมื่อเปิดบนหน้าจอขนาดเล็ก
