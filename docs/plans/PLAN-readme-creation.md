# PLAN: Project README.md Documentation Creation

> **Goal:** จัดทำไฟล์ `README.md` หลักของ Repository ที่ครอบคลุม สวยงาม เป็นทางการ และมีรายละเอียดทางเทคนิคครบถ้วน โดย**ไม่มีการแตะต้องหรือแก้ไขโค้ดโปรเจกต์ Java/Spring Boot (`HRCP-KKU-Academic`)**
> **Mode:** PLANNING
> **Date:** 2026-09-02

---

## 1. Overview & Objectives

### 1.1 Objective
- สร้างไฟล์ `README.md` ระดับ Root Directory ของโปรเจกต์ **HRCP KKU (ระบบจัดการบุคลากรและการขอกำหนดตำแหน่งทางวิชาการ มหาวิทยาลัยขอนแก่น)**
- รวบรวมข้อมูลสรุปฟีเจอร์, สถาปัตยกรรมระบบ (Architecture), เทคโนโลยี (Tech Stack), วิธีการติดตั้งและรัน Local Development, วิธีการ Deploy ด้วย Docker, ตลอดจนสารบัญเอกสารในโฟลเดอร์ `docs/`
- จัดรูปแบบด้วย Markdown คุณภาพสูง สวยงาม อ่านง่าย พร้อม Badges, โครงสร้างไดเรกทอรี และ Diagram

### 1.2 Constraints & Scope
- [NO] **ห้ามแก้ไข/แตะต้อง** ไฟล์ซอร์สโค้ดใน `HRCP-KKU-Academic/`
- [YES] **เน้นการสร้างไฟล์ `README.md`** ที่ Root Directory และเชื่อมโยงเอกสารใน `docs/` ให้สมบูรณ์

---

## 2. Key Sections of the Proposed README.md

```text
README.md Structure:
├── 1. Header & Project Badges (Java 21, Spring Boot, PostgreSQL, Docker, Passay)
├── 2. Project Overview (วัตถุประสงค์และฟังก์ชันหลักของ HRCP KKU)
├── 3. Key Features (Workflow ตำแหน่งวิชาการ, e-Signature, สร้าง DOCX/PDF, Scopus Harvest, SSO, Audit Log)
├── 4. System Architecture & Tech Stack (Spring Boot, Thymeleaf, PostgreSQL 16, LibreOffice)
├── 5. Directory Structure Map (อธิบายโครงสร้างโฟลเดอร์ที่จัดระเบียบแล้ว)
├── 6. Local Development Setup (ความต้องการระบบ, ขั้นตอน Clone & Run, Database Seed)
├── 7. Docker & Production Deployment (3 Containers: app, db, backup + deploy.sh)
├── 8. Backup & Restore Workflow (pg_dump อัตโนมัติ และการกู้คืน)
├── 9. Security & Compliance (ISMS, Passay, พ.ร.บ. คอมพิวเตอร์)
└── 10. Documentation Index (สารบัญลิงก์ไปยังเอกสารใน docs/)
```

---

## 3. Task Breakdown

### Task 1: สรุปข้อมูลทางเทคนิคและการตั้งค่า (Gather Specs)
- **Agent:** `project-planner`
- **Priority:** P0
- **Input:** `pom.xml`, `docs/deployment.md`, `docs/api-integrations/`, `docs/database/`
- **Output:** สรุปตัวแปรสภาพแวดล้อม (.env), dependencies หลัก, Docker services และ port
- **Verify:** ข้อมูลถูกต้องตรงกับโปรเจกต์จริง

### Task 2: ยกร่างเอกสาร `README.md`
- **Agent:** `project-planner` / `clean-code`
- **Priority:** P1
- **Input:** สเปกและโครงสร้างตามข้อ 2
- **Output:** ไฟล์ `README.md` ฉบับสมบูรณ์ที่ Root Directory
- **Verify:** ตรวจสอบความถูกต้องของลิงก์ภายในเอกสาร (`file:///` หรือ relative link) และความสวยงามของการจัดหน้า

### Task 3: ตรวจสอบความสอดคล้องของเอกสาร (Consistency Check)
- **Agent:** `project-planner`
- **Priority:** P2
- **Input:** `README.md`, `docs/plans/`, `docs/api-integrations/`
- **Output:** ลิงก์เชื่อมโยงไปยัง `docs/` ถูกต้องทุกจุด
- **Verify:** ไม่มี broken link

---

## 4. Phase X: Verification Checklist

- [x] **Target File Created:** มีไฟล์ `README.md` อยู่ที่ Root Directory
- [x] **Code Protection Check:** ไม่มีการแก้ไขโค้ดใดๆ ใน `HRCP-KKU-Academic/`
- [x] **Completeness:** ครอบคลุมการติดตั้ง Local, Deploy, Backup, และโครงสร้างโฟลเดอร์ครบถ้วน
- [x] **Formatting & Links:** รูปแบบ Markdown สวยงาม ลิงก์ไปยัง `docs/` ทำงานถูกต้อง

## [YES] PHASE X COMPLETE
- File Created: [README.md](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/README.md)
- Code Untouched: [YES] 100% Protected
- Verification: [YES] Passed
- Date: 2026-09-02
