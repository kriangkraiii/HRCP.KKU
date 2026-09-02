# เอกสารออกแบบระบบ HRCP-KKU-Academic

## ระบบจัดการคำร้องบุคลากรทางวิชาการ — วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น

---

##  โครงสร้างเอกสาร (Documentation Structure)

```text
docs/
├── modules/                        # เอกสารสเปกระบบ 9 โมดูลหลัก + แผนภาพสถานะ
├── plans/                          # รวม Task Plans (PLAN-*.md)
├── thesis-deliverables/            # เล่มโปรเจกต์บทที่ 3, UI Design, Screenshots & Doc Gen Scripts
├── uat-and-reports/                # เอกสารทดสอบ UAT และ Verification
└── tools/                          # สคริปต์ยูทิลิตี้สร้าง Word/HTML
```

---

##  สารบัญโมดูลระบบ (System Modules)

| ลำดับ | เอกสาร | คำอธิบาย |
|:---:|:---|:---|
| 1 | [ภาพรวมระบบ](modules/01-system-overview.md) | ตัวแสดง (Actors), Use Case Diagram ระดับระบบ |
| 2 | [ระบบยืนยันตัวตน](modules/02-authentication.md) | เข้าสู่ระบบ, OTP, ลืมรหัสผ่าน |
| 3 | [คำร้องประเมินผลการสอน (Phase 1)](modules/03-academic-request.md) | สร้าง/จัดการคำร้องประเมินผลการสอน |
| 4 | [คำร้องขอกำหนดตำแหน่ง (Phase 2)](modules/04-position-request.md) | สร้าง/จัดการคำร้องขอกำหนดตำแหน่ง |
| 5 | [คำร้องทั่วไป](modules/05-petition.md) | ยื่นคำร้องทั่วไป/อุทธรณ์ |
| 6 | [จัดการบุคลากร](modules/06-staff-management.md) | CRUD บุคลากร/กรรมการ |
| 7 | [จัดการไฟล์](modules/07-file-management.md) | ถังขยะ, กู้คืน, ลบถาวร |
| 8 | [จัดการผู้ใช้งาน](modules/08-user-management.md) | CRUD ผู้ใช้/แอดมิน |
| 9 | [การตั้งค่าและแจ้งเตือน](modules/09-settings-notifications.md) | Auto-Draft, อีเมล, แจ้งเตือนหมดอายุ |
| A | [ภาคผนวก — แผนภาพสถานะ](modules/appendix-status-flows.md) | State diagrams ทั้ง 3 ประเภทคำร้อง |
| B | [Gap Report](modules/GAP-REPORT-flow-vs-implementation.md) | รายงานเปรียบเทียบ Flow การทำงานกับการอิมพลีเมนต์ |

---

##  เอกสารเล่มปริญญานิพนธ์และ UI Specs (Thesis Deliverables)

อยู่ในโฟลเดอร์ `thesis-deliverables/`:
- **บทที่ 3 (วิธีดำเนินงาน):** `chapter3-methodology.md` และ `chapter3-methodology.docx`
- **UI Design & Descriptions:** `ui-design.docx`, `ui-design-v2.docx`, `screenshot-descriptions.docx`, `ssd-descriptions.docx`
- **สคริปต์สร้างเอกสารภาพหน้าจอ:** `generate_descriptions.py`, `generate_ui_docs.py`
- **ภาพหน้าจอระบบ:** โฟลเดอร์ `screenshots/`

---

##  ผลการทดสอบและ UAT (UAT & Reports)

อยู่ในโฟลเดอร์ `uat-and-reports/`:
- **UAT Test Cases:** `UAT_Test_Cases.doc`, `UAT_Test_Cases.docx`
- **Verification Reports:** `FONT_AWESOME_VERIFICATION.md`

---

## ️ เครื่องมือและสคริปต์สร้างเอกสาร (Tools)

อยู่ในโฟลเดอร์ `tools/`:
- `generate_word.py`: สคริปต์ Python แปลงผลการทดสอบ Unit Tests เป็นตารางผลการทดสอบในเอกสาร Word
- `generate_html_doc.py`: สคริปต์สร้างรายงาน HTML
- `run_word_gen.bat`: Batch runner
