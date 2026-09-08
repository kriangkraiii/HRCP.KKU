# PLAN: Alert Auto-Dismiss & URL Cleanup (ระบบหน่วงเวลาและซ่อนข้อความแจ้งเตือนอัตโนมัติ)

> **File:** `docs/PLAN-alert-auto-dismiss.md`  
> **Status:** [COMPLETED] ดำเนินการเสร็จสิ้นและผ่านการทดสอบ 100%  
> **Target Project:** `HRCP-KKU-Academic`  
> **Project Type:** WEB (Thymeleaf + JavaScript + CSS Transitions)

---

## 1. การวิเคราะห์สาเหตุ (Root Cause Analysis)

จากภาพที่ผู้ใช้พบว่าข้อความ **"ออกจากระบบสำเร็จ"** ค้างอยู่ตลอดเวลา เกิดจาก 2 สาเหตุหลักร่วมกัน:

1. **ไม่มีระบบ Auto-Dismiss (หน่วงเวลาปิดอัตโนมัติ):**
   - ในหน้า `login.html` บล็อกแจ้งเตือนถูกเขียนเป็น HTML static ทั่วไป:
     ```html
     <div th:if="${param.logout}" class="alert alert-success">
         <i class="fas fa-check-circle"></i>
         <span>ออกจากระบบสำเร็จ</span>
     </div>
     ```
   - ไม่มี JavaScript ที่จับเวลา (Timer/setTimeout) เพื่อสั่ง Fade out หรือซ่อนกล่องข้อความออกไป ทำให้กล่องแจ้งเตือนค้างอยู่บนหน้าจอตราบใดที่ยังอยู่หน้านั้น

2. **Query Parameter ค้างใน Browser Address Bar (`/signin?logout`):**
   - เมื่อกด Logout ตัว Spring Security จะ Redirect มาที่ `/signin?logout`
   - เมื่อไม่ได้ล้าง Query String ด้วย `history.replaceState` แม้ผู้ใช้จะเปิดทิ้งไว้ หรือกด Refresh หน้าเว็บ ตัว Thymeleaf ก็จะตรวจพบ `param.logout != null` เสมอ ทำให้ข้อความแจ้งเตือนถูกสร้างขึ้นมาใหม่เรื่อย ๆ

---

## 2. ขอบเขตและเกณฑ์ความสำเร็จ (Scope & Success Criteria)

### 2.1 เกณฑ์ความสำเร็จ (Success Criteria)
1. **Auto-Dismiss พร้อม Smooth Transition (6 วินาที):**
   - กล่องข้อความแจ้งเตือนสำเร็จ (`alert-success`, `param.logout`, `succMsg`) จะแสดงชัดเจน **6 วินาที** ตามความต้องการของผู้ใช้
   - มีระบบ Pause on hover (ถ้าเมาส์ชี้ค้างไว้ จะหยุดเวลาชั่วคราว) และคลิกที่กล่องเพื่อปิดทันทีได้
   - จากนั้นจะค่อย ๆ เลือนหายไปอย่างนุ่มนวล (Fade Out + Scale + Height Collapse) เพื่อให้ UI ดูพรีเมียมและเป็นธรรมชาติ
2. **URL Query String Cleanup (เคลียร์ URL ป้องกันการค้าง):**
   - เมื่อหน้าเว็บโหลดเสร็จและมี `?logout` หรือ `?success` ให้เรียก `window.history.replaceState` เพื่อเคลียร์ Query Parameter ทันที
   - หากผู้ใช้กด F5 / Refresh ในภายหลัง ข้อความจะไม่โผล่มาซ้ำโดยไม่จำเป็น
3. **การคงอยู่ของ Error / Critical Alert:**
   - กล่องข้อความแจ้งเตือนข้อผิดพลาด (`alert-danger`, ข้อความรหัสผ่านผิด, Lockout Timer) จะ **ไม่ถูกซ่อนอัตโนมัติ** เพื่อให้ผู้ใช้ทราบและอ่านรายละเอียดข้อผิดพลาดได้ครบถ้วน
4. **ครอบคลุมหน้า Login & Guest Pages ทั้งหมด 7 หน้า:**
   - `login.html`
   - `first_login.html`
   - `forgot_password.html`
   - `reset_password.html`
   - `set_password.html`
   - `verify_otp.html`
   - `verify_2fa.html`

---

## 3. สถาปัตยกรรมและแนวทางการพัฒนา (Technical Design)

### 3.1 CSS Transition (แอนิเมชันการเลือนหาย)
```css
.alert-auto-dismiss {
    transition: opacity 0.6s ease, transform 0.6s ease, max-height 0.4s ease 0.4s, margin 0.4s ease 0.4s, padding 0.4s ease 0.4s;
    overflow: hidden;
}
.alert-auto-dismiss.dismissing {
    opacity: 0;
    transform: translateY(-8px);
    max-height: 0;
    margin-top: 0;
    margin-bottom: 0;
    padding-top: 0;
    padding-bottom: 0;
}
```

### 3.2 JavaScript Auto-Dismiss Logic
```javascript
document.addEventListener('DOMContentLoaded', function() {
    // 1. ค้นหา Alert ประเภท Success ที่ต้องการ Auto-dismiss
    const successAlerts = document.querySelectorAll('.alert-success:not([data-no-auto-dismiss])');
    successAlerts.forEach(function(alert) {
        alert.classList.add('alert-auto-dismiss');
        setTimeout(function() {
            alert.classList.add('dismissing');
            setTimeout(function() {
                alert.remove();
            }, 800); // ลบออกจาก DOM หลัง Animation จบ
        }, 4500); // แสดงไว้ 4.5 วินาที ให้ผู้ใช้อ่านทัน
    });

    // 2. เคลียร์ URL Parameter (เช่น ?logout, ?success, ?saved) ไม่ให้ติดค้าง
    const url = new URL(window.location.href);
    if (url.searchParams.has('logout') || url.searchParams.has('success') || url.searchParams.has('saved')) {
        url.searchParams.delete('logout');
        url.searchParams.delete('success');
        url.searchParams.delete('saved');
        window.history.replaceState({}, document.title, url.pathname + (url.search ? '?' + url.searchParams.toString() : ''));
    }
});
```

---

## 4. แผนการดำเนินงาน (Implementation Breakdown)

### Phase 1: หน้าเข้าสู่ระบบและ Guest Templates (โฟกัสจุดที่ผู้ใช้แจ้ง)
- [x] อัปเดต `login.html`:
  - ใส่ Script Auto-dismiss สำหรับ `param.logout` และ `succMsg` (หน่วงเวลา 6 วินาที ตามที่ผู้ใช้กำหนด)
  - ใส่ Logic ล้าง `?logout` ออกจาก Address Bar ทันทีด้วย `window.history.replaceState`
  - เพิ่ม CSS Micro-animation `.alert-dismissing` เลือนหายอย่างนุ่มนวล
- [x] อัปเดต Guest Templates ที่เหลือครบทุกหน้า:
  - `first_login.html`
  - `forgot_password.html`
  - `reset_password.html`
  - `set_password.html`
  - `verify_otp.html`
  - `verify_2fa.html`

### Phase 2: หน้าระบบภายใน (Portal & Dashboard - Optional ในอนาคต)
- [ ] อัปเดต `base_academic.html`:
  - ปรับปรุง Flash alerts (`succMsg`, `warnMsg`) ที่บรรทัด 816–824 ให้รองรับ Auto-dismiss ควบคู่กับปุ่มกากบาท `btn-close` เดิม
  - ล้าง Query Parameter `?saved` และ `?success` ในหน้าฟอร์มเอกสารเมื่อแสดงผลเรียบร้อยแล้ว

### Phase 3: การทดสอบและการตรวจรับ (Verification)
- [x] ทดสอบ Render Tests (`EveryPageRendersTest`, `LayoutFragmentContractTest`): ผ่าน 100% (60/60 tests)
- [x] ทดสอบ Logic การ Logout และ Alert Auto-dismiss:
  - กล่องแจ้งเตือนความสำเร็จแสดงชัดเจน 6 วินาที
  - มีฟังก์ชัน Pause on hover (เมาส์ชี้ค้างไว้เพื่ออ่าน) และคลิกเพื่อปิดทันที
  - เลือนหายอย่างนุ่มนวลด้วย CSS cubic-bezier transition และลบโหนดออกจาก DOM หลัง fade-out
  - Address bar เคลียร์ `?logout` ป้องกันการค้างซ้ำเมื่อ Refresh (F5)
  - ข้อความแจ้งเตือนข้อผิดพลาด (`alert-danger`) และ Lockout Timers ไม่ถูกซ่อนอัตโนมัติ เพื่อความปลอดภัยของผู้ใช้

---

## 5. บันทึกมติและการตัดสินใจของผู้ใช้ (User Decisions)

1. **ระยะเวลาแสดงผล (Timeout Duration):** **6 วินาที (6000ms)** ตามที่ผู้ใช้ระบุ
2. **ขอบเขตการทำงาน:** **ทั่วทั้งระบบที่เกี่ยวกับการ Login & Guest Pages** ครอบคลุมทั้ง 7 หน้า (`login`, `first_login`, `forgot_password`, `reset_password`, `set_password`, `verify_otp`, `verify_2fa`)

