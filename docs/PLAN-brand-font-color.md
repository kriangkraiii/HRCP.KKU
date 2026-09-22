# Project Plan: ปรับแต่งฟอนต์และชุดสีแบรนด์ทางการ (Formal Blue Denim Branding)

> **เอกสารแผนงาน:** `docs/PLAN-brand-font-color.md`  
> **ผู้รับผิดชอบ:** `@[project-planner]` ร่วมกับ `@[frontend-specialist]`  
> **สถานะ:** วางแผนงาน (Planning - Ready for Approval)  
> **อ้างอิงความต้องการ:** ปรับฟอนต์และสีทางการตามภาพตัวอย่าง โดยกำหนดตัวย่อ **CP เป็นสี Blue Denim** และรองรับทั้งหน้า Guest/Login และ Sidebar ของระบบ  

---

## 1. บทวิเคราะห์การออกแบบ (Design & Visual Analysis)

จากการวิเคราะห์ภาพตัวอย่าง 2 ภาพที่ได้รับ:
1. **ภาพที่ 1 (Typography & Background Tone):**
   - พื้นหลัง: สี Dark Navy Denim พร้อมเส้น Grid กราฟิกบางเบาเป็นระเบียบ (Geometric Grid Overlay)
   - หัวเรื่องหลัก: `CP ACAD` ตัวหนาสีขาวสว่าง ชัดเจน ทรงพลัง
   - คำบรรยายระบบ: `CP Academic Evaluation & Promotion System` ใช้เฉดสี **Blue Denim** (นุ่มนวล มั่นคง สุภาพ)
2. **ภาพที่ 2 (Official Emblem & Modern Tech Tracking):**
   - ตราสัญลักษณ์: ตราพระธาตุพนม มหาวิทยาลัยขอนแก่น (Official KKU Emblem) จัดวางเคียงข้าง
   - สไตล์ชื่อย่อ: ตัวอักษรโมเดิร์นแบบ Dual-Tone (เช่น ตัวหน้าสีขาว ตัวหลังสีอัตลักษณ์)
   - คำบรรยายภาษาอังกฤษ: ใช้ตัวพิมพ์ใหญ่ทั้งหมด (Uppercase) พร้อมการกระจายช่องไฟ (`letter-spacing: 0.15em - 0.25em`) เช่น `ACADEMIC REQUEST SERVICES` ซึ่งให้ความรู้สึกเป็นทางการ ทันสมัย และเป็นมาตรฐานสากล
3. **การประยุกต์ใช้กับ CP ACAD:**
   - ใช้ฟอนต์ **`Prompt`** ร่วมกับ **`Sarabun`** (Prompt สำหรับหัวเรื่อง/แบรนด์/ปุ่ม และ Sarabun สำหรับข้อความทางการและฟอร์ม)
   - คำว่า **`CP`** กำหนดเป็นเฉดสี **Blue Denim** (เช่น `#4A76A8` / `#5687B8` / `#3B698C` ตามบริบท Dark/Light)
   - คำว่า **`ACAD`** ใช้สีขาว `#FFFFFF` (ในโหมดมืด/Brand Panel)
   - ซับไตเติล: `CP ACADEMIC EVALUATION & PROMOTION SYSTEM` จัดช่องไฟกว้างสไตล์ภาพที่ 2

---

## 2. ขอบเขตการดำเนินงาน (Scope of Work)

### 2.1 หน้าเข้าสู่ระบบและกลุ่มหน้า Guest (Guest Pages)
- [login.html](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/guest/login.html)
- [first_login.html](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/guest/first_login.html)
- [forgot_password.html](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/guest/forgot_password.html)
- [reset_password.html](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/guest/reset_password.html)
- [set_password.html](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/guest/set_password.html)
- [verify_otp.html](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/guest/verify_otp.html)
- [verify_2fa.html](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/guest/verify_2fa.html)

### 2.2 ไฟล์สไตล์หลักสำหรับ Guest (Design Tokens & CSS)
- [guest.css](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/css/guest.css)
  - เพิ่ม Color Tokens ชุด Blue Denim:
    ```css
    --g-denim-light: #5b92e5;
    --g-denim: #4a76a8;
    --g-denim-dark: #2c4d75;
    --g-denim-deep: #1e3552;
    ```
  - อัปเดตฟอนต์ `--g-font-brand: 'Prompt', sans-serif;`
  - ปรับปรุง `.guest-brand-title` ให้รองรับ `<span class="brand-cp">CP</span> <span class="brand-acad">ACAD</span>`
  - ปรับปรุง `.guest-brand-sub` ให้เป็น Uppercase Tracking สไตล์ภาพที่ 2
  - ปรับปุ่มเข้าสู่ระบบ (`.btn-guest-primary`) ให้เป็นเฉดสี Blue Denim สง่างามแทนสีน้ำเงินสดเดิม

### 2.3 เลย์เอาต์หลักภายในระบบ (Main Academic Layout - Optional / Consistency)
- [base_academic.html](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/academic/base_academic.html)
- [style.css](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/css/style.css)
  - ปรับปรุงแถบ Sidebar Brand ให้มี Typography และสีแบบเดียวกัน (`CP` สี Blue Denim, `ACAD` สีขาว)

---

## 3. รายละเอียดการเปลี่ยนแปลงทางเทคนิค (Technical Specification)

### 3.1 การเชื่อมต่อฟอนต์ (Font Import)
นำเข้า Google Font `Prompt` ควบคู่กับ `Sarabun` เพื่อความคมชัดทั้งภาษาไทยและภาษาอังกฤษ:
```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Prompt:wght@400;500;600;700&family=Sarabun:wght@300;400;500;600;700&display=swap" rel="stylesheet">
```

### 3.2 โครงสร้างแบรนด์ใหม่ (Brand Panel HTML)
```html
<div class="guest-brand">
    <div class="guest-brand-identity">
        <!-- ตราสัญลักษณ์ทางการ (เลือกใช้ตรา มข. หรือตราวิทยาลัย) -->
        <img class="guest-brand-logo" src="/img/kku_logo.png" alt="KKU Logo">
        <div class="guest-brand-text">
            <h1 class="guest-brand-title">
                <span class="brand-cp">CP</span><span class="brand-acad">ACAD</span>
            </h1>
            <p class="guest-brand-sub">ACADEMIC EVALUATION &amp; PROMOTION SYSTEM</p>
        </div>
    </div>
    <div class="guest-footer">
        © <span th:text="${T(java.time.Year).now(T(java.time.ZoneId).of('Asia/Bangkok')).getValue() + 543}">2569</span> 
        วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น
    </div>
</div>
```

### 3.3 ชุดสีและการตกแต่ง (CSS Specification)
```css
/* Blue Denim Palette */
:root {
    --g-denim: #4c80b0;         /* สี Blue Denim มาตรฐาน */
    --g-denim-soft: #689ec9;    /* สี Blue Denim สว่างสำหรับข้อความบนพื้นเข้ม */
    --g-denim-btn: #32628f;     /* สีปุ่ม Blue Denim ทางการ */
    --g-denim-hover: #264e75;   /* สีปุ่ม Hover */
    --g-font-brand: 'Prompt', 'Sarabun', sans-serif;
}

.guest-brand-title {
    font-family: var(--g-font-brand);
    font-size: 2rem;
    font-weight: 700;
    letter-spacing: 0.04em;
    line-height: 1.2;
}

.guest-brand-title .brand-cp {
    color: var(--g-denim-soft);  /* CP สี Blue Denim */
}

.guest-brand-title .brand-acad {
    color: #ffffff;             /* ACAD สีขาวสะอาด */
    margin-left: 6px;
}

.guest-brand-sub {
    font-family: var(--g-font-brand);
    color: rgba(255, 255, 255, 0.75);
    font-size: 0.75rem;
    font-weight: 500;
    letter-spacing: 0.16em;     /* สไตล์ภาพที่ 2 */
    text-transform: uppercase;
    margin-top: 8px;
}
```

---

## 4. แผนการตรวจสอบและทดสอบ (Verification Plan)

### 4.1 ตรวจสอบความถูกต้องของการเรนเดอร์ (Visual Check)
1. **หน้าจอ Desktop และ Mobile:**
   - ตรวจสอบความคมชัดของข้อความ `CP` สี Blue Denim และ `ACAD` สีขาว
   - ตรวจสอบช่องไฟ (Letter spacing) ของ `ACADEMIC EVALUATION & PROMOTION SYSTEM`
   - ตรวจสอบความกลมกลืนของตราสัญลักษณ์ มข. / ตราวิทยาลัย
2. **หน้า Guest ทั้ง 7 หน้า:**
   - เข้าสู่ระบบ (`/signin`)
   - หน้าอื่นๆ เช่น รีเซ็ตรหัสผ่าน, ขอ OTP, ตรวจสอบ 2FA
3. **การแสดงผลฟอนต์ภาษาไทย:**
   - ข้อความภาษาไทยยังคงใช้ Sarabun/Prompt ที่อ่านง่าย ไม่ล้นกรอบ และสระไม่ทับซ้อน
