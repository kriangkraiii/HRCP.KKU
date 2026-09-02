# PLAN: Workspace File Organization & Cleanup

> **Goal:** จัดระเบียบไฟล์เอกสาร สคริปต์ และรายงานที่กระจัดกระจายอยู่ใน Root directory และ `docs/` ให้เป็นหมวดหมู่อย่างเป็นระเบียบ สะอาดตา โดย**ไม่แตะต้องโค้ดโปรเจกต์ Java/Spring Boot (`HRCP-KKU-Academic`)**
> **Mode:** PLANNING
> **Date:** 2026-09-02

---

## 1. Overview & Constraints

### 1.1 Objective
- ปรับโครงสร้าง Root directory ให้สะอาด มีเฉพาะไฟล์คอนฟิกหลักของ Repository และตัวแอปพลิเคชัน
- จัดหมวดหมู่ไฟล์ใน `docs/` ออกเป็นกลุ่มที่ชัดเจน (API Integrations, Database Scripts, Reports & Security, Reference Documents, Plans)
- รักษาความสมบูรณ์ของการทำงาน ไม่แตะต้องไฟล์ซอร์สโค้ดโปรเจกต์ `HRCP-KKU-Academic` ใดๆ ทั้งสิ้น
- คงไฟล์สคริปต์จำเป็นใน Root เช่น `deploy.sh` เพื่อไม่ให้กระทบต่อ Workflow การ Deploy

### 1.2 Absolute Constraints
- ❌ **ห้ามแก้ไข/ย้าย/ลบ** ไฟล์หรือโฟลเดอร์ใดๆ ภายใน `HRCP-KKU-Academic/`
- ❌ **ห้ามแตะต้อง** ไฟล์คอนฟิกเครื่องมือและการพัฒนา (`.agent/`, `.vscode/`, `.metadata/`, `.venv/`, `.gitignore`, `.github/`)
- ✅ **เน้นเฉพาะ** การจัดระเบียบไฟล์เอกสาร Markdown, รายงาน JSON/HTML, สคริปต์ SQL, และไฟล์ PDF อ้างอิง

---

## 2. Target File Structure

```text
/HRCP.KKU (Project Root)
├── .agent/                             # AI Agent Workflows & Skills
├── .github/                            # CI/CD Workflows
├── .gitignore                          # Git ignore configuration
├── .metadata/ / .vscode/ / .venv/      # Workspace / Environment configs
├── deploy.sh                           # Production Deployment script (Root entry)
├── HRCP-KKU-Academic/                  # [PROTECTED] Spring Boot Web Application
└── docs/                               # All documentation & resources
    ├── api-integrations/               # เอกสาร API และการเชื่อมต่อระบบภายนอก
    │   ├── EXTERNAL_API_SCOPUS.md
    │   ├── EXTERNAL_DATA_MAPPING_ANALYSIS.md
    │   ├── EXTERNAL_USERS_API.md
    │   ├── SCOPUS_DATA_DICTIONARY.md
    │   └── SSO_SETUP.md
    ├── database/                       # SQL scripts, Migrations & Seeds
    │   ├── migrations/
    │   │   └── add_database_indexes.sql
    │   └── seeds/
    │       └── roles_positions_users_combined.sql
    ├── isms/                           # Information Security Management System
    │   ├── 01-information-security-policy.md
    │   ├── 02-risk-assessment.md
    │   ├── 03-incident-response-plan.md
    │   ├── 04-asset-inventory.md
    │   └── 05-access-control-policy.md
    ├── plans/                          # Task Plans & Feature Proposals
    │   ├── PLAN-workspace-file-cleanup.md
    │   └── PLAN-*.md (ย้ายจาก docs/)
    ├── reports/                        # Security Audits & Quality Reports
    │   ├── Report-security.json
    │   ├── bug-report-2026-08-11.md
    │   └── workflow-report.html
    ├── references/                     # คู่มือและเอกสารระเบียบข้อบังคับ
    │   └── Flow การขอกำหนดตำแหน่งทางวิชาการ (ผศ.รศ.ศ.).pdf
    ├── data-request-research/          # โครงการย่อยขอข้อมูลงานวิจัย (คงเดิม)
    └── deployment.md                   # คู่มือและขั้นตอนการ Deploy
```

---

## 3. Task Breakdown

### Task 1: สร้างโครงสร้างโฟลเดอร์ปลายทาง
- **Agent:** `project-planner` / `bash-linux`
- **Priority:** P0
- **Input:** รายการโฟลเดอร์ใหม่ภายใต้ `docs/`
- **Output:** สร้างโฟลเดอร์ `docs/api-integrations/`, `docs/database/migrations/`, `docs/database/seeds/`, `docs/plans/`, `docs/reports/`, `docs/references/`
- **Verify:** โฟลเดอร์ปลายทางถูกสร้างเรียบร้อย

### Task 2: จัดหมวดหมู่ไฟล์ใน Root Directory
- **Agent:** `project-planner` / `bash-linux`
- **Priority:** P1
- **Input:** ไฟล์ใน Root (`EXTERNAL_*.md`, `SCOPUS_*.md`, `SSO_SETUP.md`, `*.pdf`, `Report-security.json`, `*.sql`)
- **Output:** ย้ายไฟล์เข้าโฟลเดอร์ปลายทางที่จัดเตรียมไว้
- **Verify:** Root directory สะอาด ไม่มีไฟล์ลอย เหลือเฉพาะ `deploy.sh`, `.gitignore` และโฟลเดอร์ระบบ

### Task 3: จัดหมวดหมู่ไฟล์ในโฟลเดอร์ `docs/`
- **Agent:** `project-planner` / `bash-linux`
- **Priority:** P1
- **Input:** ไฟล์ `PLAN-*.md`, `workflow-report.html`, `bug-report-*.md`, `migrations/`
- **Output:**
  - ย้าย `docs/PLAN-*.md` เข้าสู่ `docs/plans/`
  - ย้าย `docs/workflow-report.html` และ `docs/bug-report-*.md` เข้าสู่ `docs/reports/`
  - จัดระเบียบ `docs/migrations/add_database_indexes.sql` เข้าสู่ `docs/database/migrations/`
- **Verify:** `docs/` มีโครงสร้างที่ชัดเจน เป็นระเบียบและค้นหาได้ง่าย

### Task 4: ตรวจสอบและอัปเดตการอ้างอิง Path ภายในเอกสาร
- **Agent:** `project-planner` / `clean-code`
- **Priority:** P2
- **Input:** เอกสารที่ถูกย้าย
- **Output:** ตรวจสอบและอัปเดต Relative path ภายในเอกสาร (เช่น ใน `EXTERNAL_DATA_MAPPING_ANALYSIS.md` ที่อ้างถึง `roles_positions_users_combined.sql`)
- **Verify:** ลิงก์ภายในเอกสารไม่เสีย

---

## 4. Phase X: Verification Checklist

- [x] **Code Protection Check:** ไม่มีการแก้ไข เปลี่ยนแปลง หรือลบไฟล์ใดๆ ใน `HRCP-KKU-Academic/`
- [x] **Config Protection Check:** ไฟล์ `.agent`, `.vscode`, `.gitignore`, `.github`, `.venv` ไม่ได้รับผลกระทบ
- [x] **Root Cleanliness Check:** Root Directory มีเฉพาะโฟลเดอร์หลัก, `deploy.sh`, และไฟล์คอนฟิก
- [x] **Documentation Structure Check:** ไฟล์เอกสารทั้งหมดถูกจัดเข้าโฟลเดอร์ตามหมวดหมู่อย่างเป็นระเบียบ

## ✅ PHASE X COMPLETE
- Code Untouched: ✅ 100% Protected (No changes to `HRCP-KKU-Academic`)
- Structure: ✅ Clean & Categorized
- Date: 2026-09-02
