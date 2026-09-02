# PLAN: HRCP-KKU-Academic Non-Code Organization & Cleanup

> **Goal:** จัดระเบียบไฟล์เอกสาร, สคริปต์สร้างรายงาน, ไฟล์ผลการทดสอบ UAT และลบไฟล์ Log ตกค้างในโฟลเดอร์โปรเจกต์ `HRCP-KKU-Academic/` โดย**ไม่มีการแตะต้องหรือกระทบโค้ดโปรเจกต์ Java/Spring Boot หรือระบบการ Build ใดๆ ทั้งสิ้น**
> **Mode:** PLANNING
> **Date:** 2026-09-02

---

## 1. Overview & Constraints

### 1.1 Objective
- ปรับโครงสร้างภายในโฟลเดอร์ `HRCP-KKU-Academic/` ให้สะอาด เป็นระเบียบ ไม่เกะกะ
- ย้ายไฟล์เอกสาร UAT, สคริปต์สร้างเอกสาร และรายงาน ออกจาก Root ของโฟลเดอร์โปรเจกต์ เข้าสู่โฟลเดอร์ `docs/` ย่อยอย่างเป็นหมวดหมู่
- จัดกลุ่มไฟล์เอกสารใน `HRCP-KKU-Academic/docs/` (โมดูล 01-09, แผนงาน PLAN-*.md, เอกสารเล่มปริญญานิพนธ์/บทที่ 3, สกรีนช็อต)
- เคลียร์โฟลเดอร์ Log ขยะ `C:` ที่เกิดจากการรัน Logback บน macOS

### 1.2 Absolute Constraints
- ❌ **ห้ามแตะต้อง** ไฟล์หรือโฟลเดอร์ที่เกี่ยวข้องกับโค้ดและการ Build:
  - `src/` (Java Classes, Tests, Thymeleaf Templates, Static Resources, Properties)
  - `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`
  - `Dockerfile`, `docker-compose.yml`, `docker/`
  - `.env.example`, `.classpath`, `.project`, `.settings/`, `.factorypath`
- ✅ **ดำเนินการเฉพาะ** ไฟล์ Markdown, เอกสาร Word (.doc/.docx), สคริปต์สร้างเอกสาร Python/Batch, และโฟลเดอร์ Log ตกค้าง

---

## 2. Target File Structure inside `HRCP-KKU-Academic/`

```text
HRCP-KKU-Academic/
├── .agent/                             # AI Agent Shared Configs (คงเดิม)
├── .env.example                        # Environment Variables Template (คงเดิม)
├── .github/ / .vscode/ / .settings/    # Development Configs (คงเดิม)
├── Dockerfile / docker-compose.yml     # Container Configurations (คงเดิม)
├── docker/                             # Docker Deployment Scripts (คงเดิม)
├── mvnw / mvnw.cmd / pom.xml           # Maven Build Tooling (คงเดิม)
├── src/                                # 🛡️ [PROTECTED] Java Source Code & Resources (คงเดิม 100%)
└── docs/                               # All Subproject Documentation & Assets
    ├── modules/                        # เอกสารรายละเอียด 9 โมดูลหลัก + Flow
    │   ├── 01-system-overview.md
    │   ├── 02-authentication.md
    │   ├── 03-academic-request.md
    │   ├── 04-position-request.md
    │   ├── 05-petition.md
    │   ├── 06-staff-management.md
    │   ├── 07-file-management.md
    │   ├── 08-user-management.md
    │   ├── 09-settings-notifications.md
    │   ├── appendix-status-flows.md
    │   └── GAP-REPORT-flow-vs-implementation.md
    ├── plans/                          # รวม Task Plans (PLAN-*.md) 17 ไฟล์
    ├── thesis-deliverables/            # เอกสารบทที่ 3, UI Design, Screenshots & Doc Gen Scripts
    │   ├── chapter3-methodology.md
    │   ├── chapter3-methodology.docx
    │   ├── ui-design.docx
    │   ├── ui-design-v2.docx
    │   ├── screenshot-descriptions.docx
    │   ├── ssd-descriptions.docx
    │   ├── generate_descriptions.py
    │   ├── generate_ui_docs.py
    │   └── screenshots/
    ├── uat-and-reports/                # เอกสารทดสอบ UAT และ Verification
    │   ├── UAT_Test_Cases.doc
    │   ├── UAT_Test_Cases.docx
    │   └── FONT_AWESOME_VERIFICATION.md
    ├── tools/                          # สคริปต์ยูทิลิตี้สร้างเอกสาร Word/HTML
    │   ├── generate_word.py
    │   ├── generate_html_doc.py
    │   ├── run_word_gen.bat
    │   └── promtp_translate_th_en.md
    └── README.md                       # Subproject Overview
```

---

## 3. Task Breakdown

### Task 1: สร้างโครงสร้างโฟลเดอร์ปลายทางใน `HRCP-KKU-Academic/docs/`
- **Agent:** `project-planner` / `bash-linux`
- **Priority:** P0
- **Input:** รายการโฟลเดอร์ย่อย: `modules/`, `plans/`, `thesis-deliverables/`, `uat-and-reports/`, `tools/`
- **Output:** สร้างไดเรกทอรีครบถ้วน
- **Verify:** ไดเรกทอรีพร้อมใช้งาน

### Task 2: เคลียร์และย้ายไฟล์จาก Root ของ `HRCP-KKU-Academic/`
- **Agent:** `project-planner` / `bash-linux`
- **Priority:** P1
- **Input:** `FONT_AWESOME_VERIFICATION.md`, `UAT_Test_Cases.*`, `generate_*.py`, `run_word_gen.bat`, `promtp_translate_th_en.md`, โฟลเดอร์ `C:`
- **Output:**
  - ย้ายไฟล์ UAT เข้า `docs/uat-and-reports/`
  - ย้ายสคริปต์และพรอมต์เข้า `docs/tools/`
  - ลบโฟลเดอร์ `C:` (Log artifact)
- **Verify:** Root ของ `HRCP-KKU-Academic/` สะอาด มีเฉพาะไฟล์ build/configs/src

### Task 3: จัดระเบียบไฟล์ภายใน `HRCP-KKU-Academic/docs/`
- **Agent:** `project-planner` / `bash-linux`
- **Priority:** P1
- **Input:** ไฟล์โมดูล 01-09, `PLAN-*.md`, `chapter3-*`, `ui-design*`, `screenshots/`, `generate_*.py`
- **Output:** ย้ายไฟล์เข้าโฟลเดอร์ย่อยตามหมวดหมู่อย่างเป็นระบบ
- **Verify:** ไฟล์ทั้งหมดถูกจัดเก็บเป็นหมวดหมู่ ค้นหาและบำรุงรักษาง่าย

### Task 4: ตรวจสอบความถูกต้องและทดสอบ Build
- **Agent:** `project-planner` / `clean-code`
- **Priority:** P2
- **Input:** โปรเจกต์หลังจัดระเบียบ
- **Output:** ตรวจสอบสถานะ git status และรัน `./mvnw test-compile` หรือ compile check
- **Verify:** ซอร์สโค้ดและระบบการ Build ไม่ได้รับผลกระทบใดๆ ทั้งสิ้น

---

## 4. Phase X: Verification Checklist

- [x] **Core Code Protected:** โฟลเดอร์ `src/`, `pom.xml`, `mvnw`, `Dockerfile` ไม่ถูกแก้ไข (Protected 100%)
- [x] **Build Validation:** ซอร์สโค้ดและ dependency ไม่เปลี่ยนแปลง
- [x] **Clean Root:** ไม่มีไฟล์เอกสารหรือ script ลอยอยู่ใน Root ของ `HRCP-KKU-Academic/`
- [x] **Docs Reorganized:** เอกสารทั้งหมดถูกจัดเก็บในหมวดหมู่ที่เหมาะสมใน `docs/` (`modules/`, `plans/`, `thesis-deliverables/`, `uat-and-reports/`, `tools/`)

## ✅ PHASE X COMPLETE
- Subproject Organized: ✅ Clean & Categorized
- Core Source Untouched: ✅ 100% Protected
- Date: 2026-09-02
