# แผนงาน: ปรับปรุงปุ่ม Action Link ของการแจ้งเตือนให้ตรงตามประเภทและบริบท (Context-Aware Notification Action Buttons)

> เอกสารแผนงานตามคำสั่ง `/plan` เพื่อแก้ไขปัญหาปุ่มดำเนินการในการแจ้งเตือนแสดงข้อความไม่ตรงกับเรื่องที่แจ้งเตือน (เช่น แจ้งเตือนระบบซิงค์ข้อมูล แต่ปุ่มแสดง "ไปยังคำร้อง / เอกสาร")

---

## 1. ผลการวิเคราะห์สาเหตุ (Root Cause Analysis)

### 1.1 สาเหตุที่พบในโค้ดปัจจุบัน
1. **ข้อความ Hardcode ใน HTML Template:**
   - ใน [`base_academic.html`](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/academic/base_academic.html#L1180-L1189) กล่อง Action Callout ใน Modal กำหนดข้อความตายตัวไว้:
     ```html
     <span>คลิกเพื่อเปิดดูคำร้อง / ดำเนินการต่อ</span>
     <a href="#" id="modalActionLinkBtn" class="notif-modal-action-btn">
         <span>ไปยังคำร้อง / เอกสาร</span>
         <i class="fas fa-arrow-right"></i>
     </a>
     ```
2. **JavaScript ใส่เฉพาะค่า `href` ไม่ได้เปลี่ยนข้อความ:**
   - ใน [`notification.js`](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/static/js/notification.js#L168-L178) เมื่อเปิด Modal ฟังก์ชัน `openReaderModal` ทำการตั้งค่าเพียง `actionBtn.href = link;` และแสดงกล่องด้วย `classList.remove('d-none')` แต่ไม่ได้อัปเดตข้อความบนปุ่มหรือข้อความอธิบายเลย ทำให้ข้อความยังคงเป็น "ไปยังคำร้อง / เอกสาร" เสมอ
3. **เมนู Dropdown ในหน้าศูนย์การแจ้งเตือน (`notifications.html`):**
   - ใน [`notifications.html`](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/HRCP-KKU-Academic/src/main/resources/templates/academic/notifications.html#L224) เมนูสามจุด (Kebab menu) ก็ใช้ข้อความตายตัวว่า `ไปยังคำร้องที่เกี่ยวข้อง` แม้ว่าลิงก์จะเป็นหน้าระบบ เช่น `/admin/external-sync`

---

## 2. การกำหนดประเภทและการปรับข้อความตามบริบท (Mapping Rules)

เมื่อมีการแจ้งเตือน ระบบจะพิจารณาจาก `NotificationType` และ `link` เพื่อสร้างข้อความปุ่ม (Button Text) และข้อความแนะนำ (Prompt Text) ที่เหมาะสม:

| หมวดหมู่ / การแจ้งเตือน | ประเภท (Type) / ลิงก์ (Link) | ข้อความบนปุ่ม (Button Text) | ข้อความคำอธิบาย (Prompt Text) | ไอคอน (Icon) |
|---|---|---|---|---|
| **การซิงค์ข้อมูลภายนอก / ระบบ** | `SYSTEM` (ลิงก์ `/admin/external-sync`) | **ไปยังหน้าซิงค์ข้อมูล** | คลิกเพื่อตรวจสอบสถานะการซิงค์ข้อมูลภายนอก | `fas fa-sync-alt` |
| **การจัดการระบบทั่วไป** | `SYSTEM` (ลิงก์ `/admin/...` อื่นๆ) | **ไปยังหน้าจัดการระบบ** | คลิกเพื่อเปิดดูรายละเอียดหรือดำเนินการต่อ | `fas fa-cog` |
| **คำร้องขอลงนามเอกสาร** | `SIGNATURE_REQUESTED`, `SIGNATURE_REMINDER` (ลิงก์ `/academic/esign/...`) | **ไปยังหน้าลงนามเอกสาร** | คลิกเพื่อเปิดหน้าลงนามเอกสารอิเล็กทรอนิกส์ | `fas fa-file-signature` |
| **ลงนามเอกสารเสร็จ / ปฏิเสธ** | `SIGNATURE_COMPLETED`, `SIGNATURE_DECLINED` | **ดูเอกสารที่ลงนาม** | คลิกเพื่อตรวจสอบเอกสารและสถานะการลงนาม | `fas fa-file-contract` |
| **คำร้องประเมินการสอน** | `ACADEMIC_NEW_REQUEST`, `ACADEMIC_STATUS_UPDATE` (หรือลิงก์ `/user/academic/...`) | **ไปยังคำร้องประเมินการสอน** | คลิกเพื่อเปิดดูคำร้องประเมินการสอน / ดำเนินการต่อ | `fas fa-clipboard-check` |
| **คำร้องขอกำหนดตำแหน่ง** | `POSITION_NEW_REQUEST`, `POSITION_STATUS_UPDATE` (หรือลิงก์ `/user/position/...`) | **ไปยังคำร้องขอตำแหน่ง** | คลิกเพื่อเปิดดูคำร้องขอกำหนดตำแหน่ง / ดำเนินการต่อ | `fas fa-university` |
| **เตือนผลประเมินใกล้หมดอายุ** | `EXPIRY_WARNING` | **ตรวจสอบผลการประเมิน** | คลิกเพื่อตรวจสอบผลการประเมินและวางแผนดำเนินการ | `fas fa-clock` |
| **โปรไฟล์ / ข้อมูลผู้ใช้** | ลิงก์ `/user/profile` หรือ `/admin/profile` | **ไปยังข้อมูลส่วนตัว** | คลิกเพื่อไปยังหน้าแก้ไขข้อมูลส่วนตัว | `fas fa-user` |
| **ลิงก์ทั่วไปอื่นๆ** | ลิงก์ใดๆ ที่มีค่า | **ไปยังหน้าที่เกี่ยวข้อง** | คลิกเพื่อเปิดดูรายละเอียดหรือดำเนินการต่อ | `fas fa-external-link-alt` |
| **ไม่มีลิงก์ (Link ว่างหรือ `#`)** | `link == null` หรือ `link == ''` หรือ `link == '#'` | *(ซ่อนทั้งกล่อง ไม่แสดงปุ่ม)* | *(ซ่อนทั้งกล่อง)* | - |

---

## 3. แผนการดำเนินงาน (Implementation Plan)

### ส่วนที่ 1: ฝั่ง Backend (Java & Entity Helper)
1. **เพิ่ม Helper Methods ใน `Notification.java`:**
   - `getActionBtnText()`: คืนค่าชื่อปุ่มตามกติกาด้านบน
   - `getActionPromptText()`: คืนค่าข้อความคำอธิบาย
   - `getActionIconClass()`: คืนค่าไอคอน FontAwesome ที่สื่อความหมาย
2. **ส่งออกข้อมูลใน `NotificationController.java`:**
   - ใน `/api/notifications/recent` และ `/api/notifications/{id}/detail` ส่งฟิลด์ `actionBtnText`, `actionPromptText`, `actionIconClass` เพิ่มเติมใน JSON Map

### ส่วนที่ 2: ฝั่ง UI Template & JavaScript
1. **ปรับปรุง `base_academic.html` (Reader Modal):**
   - เพิ่ม ID ให้กับ Element ข้อความคำอธิบายและข้อความปุ่ม:
     ```html
     <div id="modalActionCallout" class="notif-modal-action-card d-none">
         <div class="notif-modal-action-text">
             <i id="modalActionPromptIcon" class="fas fa-arrow-circle-right fa-lg"></i>
             <span id="modalActionPromptText">คลิกเพื่อเปิดดูรายละเอียดหรือดำเนินการต่อ</span>
         </div>
         <a href="#" id="modalActionLinkBtn" class="notif-modal-action-btn">
             <span id="modalActionBtnText">ไปยังหน้าที่เกี่ยวข้อง</span>
             <i id="modalActionBtnIcon" class="fas fa-arrow-right"></i>
         </a>
     </div>
     ```
2. **ปรับปรุง `notification.js` (`openReaderModal`):**
   - สร้างฟังก์ชันคำนวณสำรองใน Frontend เผื่อกรณีข้อมูลไม่ได้มาจาก API ตรงๆ:
     `resolveActionInfo(notif)`
   - กำหนดข้อความ `modalActionPromptText.textContent`, `modalActionBtnText.textContent`, และคลาสไอคอนตามบริบท
3. **ปรับปรุง `notifications.html`:**
   - เพิ่ม `th:data-notif-action-text` และ `th:data-notif-action-prompt` ในแถวรายการแจ้งเตือน
   - ปรับเมนู Kebab จากข้อความเดิม `ไปยังคำร้องที่เกี่ยวข้อง` เป็นข้อความพลวัต:
     `<span th:text="${n.actionBtnText}">ไปยังหน้าที่เกี่ยวข้อง</span>`

---

## 4. แผนการตรวจสอบความถูกต้อง (Verification Plan)

1. **ทดสอบการแจ้งเตือนประเภท SYSTEM (เช่น เก็บเกี่ยวผลงานวิจัย / ซิงค์ข้อมูล):**
   - ตรวจสอบว่าปุ่มเปลี่ยนเป็น **"ไปยังหน้าซิงค์ข้อมูล"** และคำอธิบายระบุเรื่องการซิงค์ข้อมูลภายนอกอย่างถูกต้อง ไม่ขึ้นคำว่า "คำร้อง / เอกสาร"
2. **ทดสอบการแจ้งเตือนประเภทคำร้อง (Academic / Position / E-Sign):**
   - ตรวจสอบว่าปุ่มระบุถูกต้องตามประเภท เช่น "ไปยังคำร้องประเมินการสอน", "ไปยังหน้าลงนามเอกสาร", หรือ "ไปยังคำร้องขอตำแหน่ง"
3. **ทดสอบการแจ้งเตือนที่ไม่มี Link:**
   - ตรวจสอบว่ากล่อง Action ซ่อนตัวอย่างเรียบร้อย ไม่แสดงปุ่มเปล่า
4. **ทดสอบชุด Automated Tests:**
   - รัน `./mvnw test -Dtest=AcademicRequestActiveStateTest` เพื่อยืนยันว่าไม่มีผลกระทบต่อระบบเดิม
