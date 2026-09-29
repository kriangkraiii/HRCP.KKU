# Project Plan: นำการตั้งค่าบันทึกแบบร่างอัตโนมัติออก และบังคับเปิดใช้งานเสมอ (Enforce Auto-Draft)

> **เอกสารแผนงาน:** `docs/PLAN-enforce-autodraft.md`  
> **ผู้รับผิดชอบหลัก:** `@[project-planner]` ร่วมกับ `@[backend-specialist]` และ `@[frontend-specialist]`  
> **สถานะ:** ดำเนินการสำเร็จและผ่านการทดสอบ (Implemented & Verified)  
> **คำสั่ง:** `/plan`

---

## 1. ที่มาและวัตถุประสงค์ (Background & Objective)

### 1.1 ปัญหาและความต้องการของผู้ใช้
- ในหน้าการตั้งค่าผู้ใช้ (`/user/academic/settings` และ `/admin/academic/settings`) ในไฟล์ [`settings.html`](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/academic/settings.html) มีสวิตช์เปิด-ปิด:
  > **💾 บันทึกแบบร่างอัตโนมัติ**  
  > *บันทึกข้อมูลในฟอร์มอัตโนมัติทุก 1 วินาทีเมื่อมีการแก้ไข*
- **คำสั่งของผู้ใช้:** *"บันทึกแบบร่างอัตโนมัติในการตั้งค่าเอาออก เพราะจะทำให้ปิดไม่ได้ ต้องเปิดใช้เท่านั้น /plan"*
- **เหตุผลทางธุรกิจและประสบการณ์ผู้ใช้ (UX/Safety):**
  - ฟังก์ชัน Auto-Draft มีความสำคัญยิ่งยวดต่อการป้องกันข้อมูลสูญหาย (Data Loss Prevention) ในระหว่างที่อาจารย์หรือบุคลากรกรอกเอกสารคำร้องขอกำหนดตำแหน่งทางวิชาการและเอกสารประเมินผลการสอนที่มีความยาวและซับซ้อน
  - หากอนุญาตให้ผู้ใช้ปิดสวิตช์นี้ได้ จะเกิดความเสี่ยงสูงที่ผู้ใช้ปิดโดยไม่ตั้งใจแล้วพิมพ์เอกสารไม่ทันบันทึก ทำให้เกิดปัญหาข้อมูลหาย
  - จึงต้องการ**นำสวิตช์นี้ออกจากหน้าตั้งค่า** และ**บังคับให้ระบบเปิดใช้งาน Auto-Draft ตลอดเวลา (100% Enforced: Always Enabled)** สำหรับผู้ใช้ทุกคน

---

## 2. การวิเคราะห์ผลกระทบเชิงเทคนิค (Technical Impact Analysis)

```mermaid
flowchart TD
    subgraph Current ["สภาพปัจจุบัน (ผู้ใช้ปิดได้)"]
        UI1["settings.html: autoDraftToggle Switch"] -->|Submit / AJAX| Ctrl1["AcademicSettingsController"]
        Ctrl1 -->|user.setAutoDraftEnabled(false)| DB1[("DB: auto_draft_enabled = false")]
        DB1 --> Base1["base_academic.html (AUTO_DRAFT_ENABLED = false)"]
        Base1 --> JS1["auto_draft.js (หยุดทำงาน ไม่เซฟแบบร่าง)"]
    end

    subgraph Proposed ["สถาปัตยกรรมใหม่ (บังคับเปิดใช้ตลอดเวลา)"]
        UI2["settings.html: นำสวิตช์ออก สะอาดตา"] -.->|ไม่มีปุ่มให้ปิด| Safe["ผู้ใช้ไม่สามารถกดปิดได้"]
        Ctrl2["AcademicSettingsController: enforce true เสมอ"] --> DB2[("DB: auto_draft_enabled = true")]
        Base2["base_academic.html: window.AUTO_DRAFT_ENABLED = true"]
        Base2 --> JS2["auto_draft.js: ทำงานตลอดเวลา ปกป้องข้อมูล 100%"]
    end
```

### 🔴 จุดวิกฤตที่ต้องระวังเป็นพิเศษ (Critical Gotcha):
ใน [`AcademicSettingsController.java`](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/AcademicSettingsController.java) บรรทัดที่ 319:
```java
user.setAutoDraftEnabled(autoDraftEnabled != null);
```
- หากเราเพียงแค่ลบ input checkbox ออกจากไฟล์ HTML โดยไม่ได้แก้ Controller: เมื่อผู้ใช้กดปุ่ม "บันทึกการตั้งค่า" เบราว์เซอร์จะไม่ส่งพารามิเตอร์ `autoDraftEnabled` มา ทำให้ค่าเป็น `null` และ Controller จะเซ็ต `user.setAutoDraftEnabled(false)` ทันที!
- **การแก้ไข:** ต้องแก้ Controller ให้บังคับเป็น `true` เสมอ (`user.setAutoDraftEnabled(true);`) และลบพารามิเตอร์ออกจาก method signature อย่างปลอดภัย

---

## 3. รายละเอียดการดำเนินการในแต่ละไฟล์ (Detailed Scope of Work)

### 3.1 ฝั่งหน้าบ้าน (Frontend UI & Templates)
1. **[`settings.html`](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/academic/settings.html):**
   - ลบบล็อก HTML สวิตช์บันทึกแบบร่างอัตโนมัติ (บรรทัด 45–58):
     ```html
     <!-- ลบส่วนนี้ออก -->
     <div class="d-flex justify-content-between align-items-center py-3 border-bottom">
         <div>
             <h6 class="mb-1"><i class="fas fa-save text-success"></i> บันทึกแบบร่างอัตโนมัติ</h6>
             <small class="text-muted">บันทึกข้อมูลในฟอร์มอัตโนมัติทุก 1 วินาทีเมื่อมีการแก้ไข</small>
         </div>
         <div class="form-check form-switch">
             <input class="form-check-input setting-toggle u-switch-lg" type="checkbox"
                 data-key="autoDraftEnabled" id="autoDraftToggle" ...>
         </div>
     </div>
     ```
   - ปรับการแสดงผลของ Card "การตั้งค่าทั่วไป" ให้เรียบร้อย สวยงาม โดยเหลือการตั้งค่า "การแจ้งเตือนทางอีเมล" และ "ธีมสี"
2. **[`base_academic.html`](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/academic/base_academic.html):**
   - บรรทัดที่ 199 ปรับให้ `window.AUTO_DRAFT_ENABLED = true;` โดยตรง เพื่อให้สคริปต์ `auto_draft.js` ทั่วทั้งระบบทำงานเสมอ

### 3.2 ฝั่งหลังบ้าน (Backend Controllers & Services)
1. **[`AcademicSettingsController.java`](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/AcademicSettingsController.java):**
   - ใน `saveSettings(...)`: ตัดพารามิเตอร์ `Boolean autoDraftEnabled` ออก และตั้งค่า `user.setAutoDraftEnabled(true);` เสมอ
   - ใน `saveUserSettings(...)` และ `saveAdminSettings(...)`: ตัด `@RequestParam(value = "autoDraftEnabled", required = false)` ออก
   - ใน `toggleSetting(...)`: หากมีการเรียก AJAX ส่ง key `autoDraftEnabled` เข้ามา ให้ตั้งค่าเป็น `true` เสมอ หรือปฏิเสธคำขอปิด
2. **[`AutoDraftApiController.java`](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/java/com/ecom/academic/controller/AutoDraftApiController.java):**
   - ในเมธอด `toggleAutoDraft(...)` (บรรทัด 223–239): บังคับให้คืนค่า `{ "enabled": true }` และตั้งค่าผู้ใช้เป็น `true` เสมอ เพื่อป้องกันการ bypass ผ่าน API endpoint
3. **[`UserDtls.java`](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/java/com/ecom/model/UserDtls.java):**
   - ตรวจสอบให้แน่ใจว่า default value ของ `autoDraftEnabled` เป็น `true` และหากมีค่าเป็น `null` ให้ fallback เป็น `true`

---

## 4. แผนงานการดำเนินงาน (Task Breakdown)

| รหัสงาน | ชื่องาน | รายละเอียดการดำเนินงาน |
| :--- | :--- | :--- |
| **TASK-1** | **UI Removal in Settings Page** | ลบสวิตช์ `autoDraftToggle` และคำบรรยายออกจาก `settings.html` |
| **TASK-2** | **Backend Form & Helper Update** | ปรับปรุง `AcademicSettingsController.java` ไม่รับค่าปิด และบังคับ `user.setAutoDraftEnabled(true)` เสมอ |
| **TASK-3** | **API Lock & Protection** | ล็อค endpoint `/user/auto-draft-toggle` ใน `AutoDraftApiController.java` ให้คงสถานะ `enabled: true` เสมอ |
| **TASK-4** | **Global Script Enforcement** | ปรับ `base_academic.html` ให้ `window.AUTO_DRAFT_ENABLED = true;` ทั่วทั้งระบบ |
| **TASK-5** | **Build & Regression Verification** | รัน `./mvnw test` ตรวจสอบ Controller และ Service ทั้งหมดว่าทำงานถูกต้อง 100% |

---

## 5. การตรวจสอบความถูกต้อง (Verification Checklist)

- [x] ในหน้าตั้งค่า (`/user/academic/settings` และ `/admin/academic/settings`) ไม่มีสวิตช์ "บันทึกแบบร่างอัตโนมัติ" ปรากฏอีกต่อไป
- [x] เมื่อกดบันทึกการตั้งค่าใดๆ (เช่น เปลี่ยนธีม หรือ เปิด/ปิดแจ้งเตือนอีเมล) ค่า `auto_draft_enabled` ของผู้ใช้ต้องคงเป็น `true` ไม่เปลี่ยนเป็น `false`
- [x] ในหน้ากรอกคำร้อง (เช่น คำร้องขอประเมินผลการสอน / ตำแหน่งทางวิชาการ) ฟังก์ชัน Auto-Draft ทำงานอัตโนมัติเมื่อพิมพ์แก้ไขข้อความ
- [x] หน้าเว็บมีตัวแปร `window.AUTO_DRAFT_ENABLED === true` เสมอ
- [x] รันการทดสอบ Maven ทั้งหมดผ่าน 0 errors
