# Project Plan: แก้ไขปุ่ม "ไปตรวจสอบ" ไม่ทำงานในหน้าระบบภายนอก (External Sync Verify Tab Button Fix)

> **เอกสารแผนงาน:** `docs/PLAN-verify-tab-button.md`  
> **ผู้รับผิดชอบหลัก:** `@[project-planner]` ร่วมกับ `@[frontend-specialist]`  
> **สถานะ:** ดำเนินการเรียบร้อยแล้ว (Implementation Completed)  
> **ประเภทโครงการ:** WEB (Spring Boot Thymeleaf + Bootstrap 5 + Vanilla JS)

---

## 1. ภาพรวมและสาเหตุของปัญหา (Overview & Root Cause Analysis)

### 1.1 ปัญหาที่พบ
ในหน้าจอผู้ดูแลระบบ **ดึงข้อมูลและจัดการระบบภายนอก (`/admin/external-sync`)** ในการ์ดแสดงสถิติ **"รอการยืนยัน"** (ตัวเลขแสดงรายการที่รอตรวจสอบข้อมูลอาจารย์ที่เปลี่ยนแปลงจาก Fund Management):
เมื่อมีรายการค้างตรวจสอบ (`pendingCount > 0`) จะมีปุ่มสีเหลืองปรากฏขึ้น:
```html
<button th:if="${pendingCount > 0}"
        type="button"
        class="btn btn-sm btn-warning mt-2 fw-semibold"
        onclick="switchToChangesTab()">
    <i class="fas fa-arrow-right me-1"></i>ไปตรวจสอบ
</button>
```
**พฤติกรรมผิดปกติ:** ผู้ใช้งานคลิกที่ปุ่ม `[ -> ไปตรวจสอบ ]` แล้ว **หน้าจอไม่มีการตอบสนอง ไม่มีการสลับไปยังแท็บ "ตรวจสอบข้อมูลอาจารย์ที่เปลี่ยน"**

---

### 1.2 สาเหตุเชิงลึก (Root Cause Diagnosis)

1. **ความไม่เข้ากันของการเรียก Bootstrap 5 Tab API:**
   - โค้ดปัจจุบันใน `switchToChangesTab()`:
     ```javascript
     function switchToChangesTab() {
         var tabBtn = document.getElementById('tab-changes-btn');
         if (tabBtn && window.bootstrap && bootstrap.Tab) {
             var tab = new bootstrap.Tab(tabBtn);
             tab.show();
         }
     }
     ```
   - ใน Bootstrap 5 การสร้าง `new bootstrap.Tab(tabBtn)` ซ้ำกับ Element ที่มี Attribute `data-bs-toggle="pill"` มักทำให้เกิดข้อผิดพลาดเงียบ (Silent no-op) หรือไม่สั่ง Transition ให้ Tab Pane สลับแสดงผล เนื่องจาก Instance ถูกสร้างขึ้นแล้วโดย Bootstrap Event Listener
   - การเรียกที่ถูกต้องตามมาตรฐาน Bootstrap 5 คือ `bootstrap.Tab.getOrCreateInstance(tabBtn).show()` หรือสั่ง `tabBtn.click()`

2. **ขาดกลไก Fallback แบบ Direct DOM Toggle:**
   - ในกรณีที่สคริปต์ Bootstrap ยังโหลดไม่เสร็จ, มี Delay, หรือเกิดความผิดพลาดใน Bundle ฟังก์ชัน `switchToChangesTab()` จะข้ามการทำงานไปเฉย ๆ โดยไม่มีกลไก Fallback สลับ Class `.active` และ `.show.active` ด้วยตนเอง (ซึ่งในหน้าอื่นของระบบ เช่น `academic/esign/inbox.html` มีการใส่ Direct DOM fallback ไว้อย่างชัดเจน)

3. **ไม่มีการเลื่อนหน้าจอ (Smooth Scroll) ไปยังส่วนแสดงผล:**
   - แม้แท็บจะถูกสั่งสลับ แต่ผู้ใช้งานยังคงมองอยู่ที่ด้านบนสุดของการ์ดสถิติ ทำให้เข้าใจว่าไม่มีอะไรเกิดขึ้นเนื่องจากไม่มีการ Scroll สายตาไปยังส่วนเนื้อหาการตรวจสอบ

4. **ขาด URL Synchronization & Fallback Link:**
   - คอนโทรลเลอร์ `ExternalSyncPageController` รองรับ Query Parameter `?tab=changes` ไว้อย่างสมบูรณ์แบบอยู่แล้ว (`@RequestParam(defaultValue = "sync") String activeTab`)
   - ปุ่มปัจจุบันเป็น `<button type="button">` ธรรมดา หากเปลี่ยนเป็น `<a th:href="@{/admin/external-sync(tab='changes')}" ...>` ร่วมกับ JavaScript Handler จะทำให้มี Fallback 100% (ทำงานได้แม้ปิดหรือติดขัด JavaScript) และรองรับการ Refresh หน้าเว็บ

---

## 2. เกณฑ์ความสำเร็จ (Success Criteria)

- [x] เมื่อคลิกปุ่ม **"ไปตรวจสอบ"** หน้าจอจะสลับไปยังแท็บ **"ตรวจสอบข้อมูลอาจารย์ที่เปลี่ยน" (`#tab-changes`)** ทันที 100%
- [x] มีกลไก Fallback 3 ชั้น (1. `tabBtn.click()` / `bootstrap.Tab`, 2. Direct DOM class toggle, 3. Native SSR Link `?tab=changes`)
- [x] เมื่อสลับแท็บ หน้าจอจะเลื่อน (Smooth Scroll) ไปยังส่วนหัวของแท็บหรือตารางข้อมูลอย่างนุ่มนวล
- [x] URL บน Browser Bar จะถูกอัปเดตเป็น `?tab=changes` โดยไม่ต้องโหลดหน้าใหม่ทั้งหมด (ผ่าน `history.pushState`) ทำให้หากผู้ใช้กด Refresh จะยังคงอยู่ที่แท็บเดิม
- [x] ปุ่มแท็บด้านบน (`#tab-sync-btn` และ `#tab-changes-btn`) แสดงสถานะ Active ถูกต้องตรงกัน

---

## 3. สแตกเทคโนโลยีที่เกี่ยวข้อง (Tech Stack)

| ส่วนประกอบ | เทคโนโลยี | รายละเอียด |
|---|---|---|
| **Frontend Template** | Thymeleaf 3.x + HTML5 | `src/main/resources/templates/admin/external_sync.html` |
| **CSS Framework** | Bootstrap 5.3 + Custom CSS | Nav Pills, Tab Panes, Animation Classes |
| **Client Script** | Vanilla JavaScript (ES6) | Tab event triggers, DOM fallback, Scroll Into View |
| **Backend Controller** | Spring Boot 3.x Java | `ExternalSyncPageController.java` (`activeTab` model attribute) |

---

## 4. แผนการดำเนินงาน (Task Breakdown)

### Task 1: ปรับปรุงปุ่ม "ไปตรวจสอบ" ให้มี SSR Fallback
- **ID:** `TASK-BTN-FALLBACK`
- **Agent:** `@[frontend-specialist]`
- **Skill:** `clean-code`, `frontend-design`
- **Priority:** P1
- **Target File:** `src/main/resources/templates/admin/external_sync.html` (บรรทัด ~95-101)
- **Input:** โค้ดเดิม `<button onclick="switchToChangesTab()">`
- **Output:** ปรับเป็น `<a th:if="${pendingCount > 0}" th:href="@{/admin/external-sync(tab='changes')}" class="btn btn-sm btn-warning mt-2 fw-semibold" onclick="switchToChangesTab(event)">`
- **Verify:** เมื่อคลิก หรือเปิดในแท็บใหม่ จะพาไปยังแท็บ changes ได้ถูกต้องเสมอ

---

### Task 2: เขียนฟังก์ชัน `switchToChangesTab()` ใหม่ให้ทนทาน (Triple-Guard Switcher)
- **ID:** `TASK-JS-TAB-SWITCH`
- **Agent:** `@[frontend-specialist]`
- **Skill:** `clean-code`, `javascript-pro`
- **Priority:** P0
- **Target File:** `src/main/resources/templates/admin/external_sync.html` (บรรทัด ~914-927)
- **Input:** ฟังก์ชัน `switchToChangesTab()` เดิม
- **Output:**
  1. รับ `event` และสั่ง `if (e) e.preventDefault();`
  2. สั่ง `tabBtn.click()` เพื่อทริกเกอร์กลไกหลักของ Bootstrap 5
  3. หากไม่สลับ ใช้ `bootstrap.Tab.getOrCreateInstance(tabBtn).show()`
  4. ทำ Direct DOM Fallback (นำ class `active` / `show` ไปใส่ `#tab-changes-btn` และ `#tab-changes`)
  5. อัปเดต `history.pushState(null, '', '?tab=changes')`
  6. สั่ง `scrollIntoView({ behavior: 'smooth', block: 'start' })` ไปยัง `#syncTabs`
- **Verify:** ทดสอบคลิกปุ่ม "ไปตรวจสอบ" แล้วแท็บสลับทันทีและเลื่อนหน้าจออย่างลื่นไหล

---

### Task 3: ตรวจสอบและประสานกับการโหลดแท็บผ่าน URL Parameter
- **ID:** `TASK-URL-PARAM-CHECK`
- **Agent:** `@[frontend-specialist]`
- **Skill:** `clean-code`
- **Priority:** P1
- **Target File:** `src/main/resources/templates/admin/external_sync.html`
- **Input:** กลไก `DOMContentLoaded` สำหรับอ่าน `?tab=changes`
- **Output:** ปรับปรุงให้เรียกใช้งาน `switchToChangesTab()` ได้อย่างปลอดภัย ไม่เกิด Race Condition กับการเรนเดอร์ของ Thymeleaf
- **Verify:** ทดสอบเปิด URL `/admin/external-sync?tab=changes` แล้วแท็บ "ตรวจสอบข้อมูลอาจารย์ที่เปลี่ยน" ถูกเปิดค้างไว้ถูกต้อง

---

## 5. แผนการตรวจสอบคุณภาพ (Phase X: Final Verification)

- [x] **Functional Test:** คลิกปุ่ม "ไปตรวจสอบ" บนการ์ด "รอการยืนยัน" แล้วสลับไปที่แท็บรายการอาจารย์ทันที
- [x] **DOM & Class Verification:** ตรวจสอบว่า Class `active` ย้ายจากแท็บแรกมาแท็บที่สอง และ Tab Pane สลับความชัดเจน (Opacity/Display) ถูกต้อง
- [x] **Smooth Scroll Test:** หน้าจอเลื่อนลงมาที่บริเวณเนื้อหาที่ต้องตรวจสอบอย่างชัดเจน
- [x] **Browser Compatibility:** ทดสอบทั้งในกรณีที่มีและไม่มี Bootstrap Event Listener
- [x] **No Console Errors:** ไม่มีข้อผิดพลาด JavaScript ใน Developer Tools Console
