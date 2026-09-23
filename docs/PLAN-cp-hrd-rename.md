# Project Plan: Rebrand to CP HRD (College of Computing Human Resource Development System)

> **Plan Document:** `docs/PLAN-cp-hrd-rename.md`  
> **Responsible Agents:** `@[project-planner]` alongside `@[frontend-specialist]` and `@[backend-specialist]`  
> **Status:** Planning (Ready for Review & Approval)  
> **Target Branding:**  
> - **Short Name / Brand Display:** `CP HRD` (or `CP-HRD`)  
> - **Official Full Name:** `College of Computing Human Resource Development System`  
> - **Language Policy:** 100% English for system branding and titles (no Thai for main system brand name)  
> - **Target Domain:** `https://hrd.computing.kku.ac.th`

---

## 1. Rationale & Objectives

1. **System Rebranding to Match Domain (`hrd.computing.kku.ac.th`):**
   - The current branding `CP ACAD` / `CP Academic Evaluation & Promotion System` does not align directly with the production subdomain `hrd`.
   - The new name **`CP HRD`** and full title **`College of Computing Human Resource Development System`** reflects the institutional purpose, covers both academic and personnel growth, and matches the official URL.
2. **Language Specification:**
   - As explicitly requested, the system title and branding will be in English (`College of Computing Human Resource Development System` & `CP HRD`), removing old Thai system titles from the headers, `<title>`, and email banners.
3. **Safety & Stability Guardrails:**
   - **Do NOT rename Java packages:** Keep `com.ecom.*` unchanged to prevent breaking Spring component scans, JPA entity reflections, and Flyway/Hibernate mappings.
   - **Do NOT rename root project folders:** Keep directory structure `HRCP.KKU/HRCP-KKU-Academic` intact to preserve IDE workspace settings and build scripts.
   - **Do NOT alter database table schemas:** Retain PostgreSQL entity names as they are already abstracted.

---

## 2. Component Breakdown & Target Changes

### 2.1 Frontend UI & Thymeleaf Templates

| File / Component | Current Text | New Value (CP HRD) |
|---|---|---|
| **Page `<title>`**<br>`academic/base_academic.html` | `CP ACAD - ระบบประเมินผลการสอนและกำหนดตำแหน่งทางวิชาการ \| วิทยาลัยการคอมพิวเตอร์ มข.` | `CP HRD - College of Computing Human Resource Development System` |
| **Sidebar Brand Header**<br>`academic/base_academic.html` | `<h6><span class="brand-cp">CP</span> <span class="brand-acad">ACAD</span></h6>`<br>`<small>CP ACADEMIC EVALUATION &amp; PROMOTION SYSTEM</small>` | `<h6><span class="brand-cp">CP</span> <span class="brand-hrd">HRD</span></h6>`<br>`<small>COLLEGE OF COMPUTING HUMAN RESOURCE DEVELOPMENT SYSTEM</small>` |
| **Sidebar Collapsed Label**<br>`academic/base_academic.html` | `<span class="sidebar-brand-collapsed-label">CP</span>` | `CP` or `HRD` |
| **Footer Brand**<br>`academic/base_academic.html` | `... — CP ACAD (CP Academic Evaluation & Promotion System)` | `... — CP HRD (College of Computing Human Resource Development System)` |
| **Notification Modal Sender**<br>`academic/base_academic.html` | `จาก: ระบบสารสนเทศ CP ACAD` | `From: CP HRD System` (or `ระบบสารสนเทศ CP HRD`) |
| **Login Page Brand & Title**<br>`guest/login.html` | `<title>เข้าสู่ระบบ - CP ACAD \| ...</title>`<br>`.guest-brand-title: CP ACAD`<br>`.guest-brand-sub: CP ACADEMIC EVALUATION &amp; ...` | `<title>Sign In - CP HRD \| College of Computing Human Resource Development System</title>`<br>`.guest-brand-title: <span class="brand-cp">CP</span> <span class="brand-hrd">HRD</span>`<br>`.guest-brand-sub: COLLEGE OF COMPUTING HUMAN RESOURCE DEVELOPMENT SYSTEM` |
| **Guest Auth Pages**<br>`first_login.html`, `forgot_password.html`, `reset_password.html`, `verify_otp.html`, `verify_2fa.html`, `set_password.html` | `<title>... - CP ACAD \| CP Academic Evaluation...</title>`<br>`<div class="guest-brand-title">...</div>`<br>`<div class="guest-brand-sub">...</div>` | Update all titles to English `... - CP HRD \| College of Computing Human Resource Development System`, brand title to `CP HRD`, and subtitle to full English title. |
| **Error Pages**<br>`error.html`, `error/403.html`, `error/404.html`, `error/500.html` | `<title>... \| CP ACAD</title>` | `<title>... \| CP HRD</title>` |
| **CSS Stylesheets**<br>`static/css/style.css`, `static/css/guest.css` | `.brand-acad` style rule | Add `.brand-hrd` (matching `.brand-acad` with bold white text & subtle drop shadow) so both classes work smoothly. |
| **Search & Quick Navigation**<br>`static/js/global-search.js` | `replace(' - CP ACAD', '')...` and checks for `CP ACAD` | Add `CP HRD` and `CP-HRD` stripping and condition bypass so global search accurately extracts page titles. |
| **Real-time Notifications**<br>`static/js/notification.js` | `actorName = ... || 'ระบบสารสนเทศ CP ACAD'` | `actorName = ... || 'CP HRD System'` |

---

### 2.2 Backend & Email Templates

| File / Component | Current Text | New Value (CP HRD) |
|---|---|---|
| **`EmailTemplateHelper.java`** | `public static final String SENDER_SYSTEM_NAME = "ระบบตำแหน่งทางวิชาการ วิทยาลัยการคอมพิวเตอร์ มข.";` | `public static final String SENDER_SYSTEM_NAME = "CP HRD - College of Computing Human Resource Development System";` |
| **`EmailTemplateHelper.java`** (Hero Header) | `CP ACAD (CP Academic Evaluation &amp; Promotion System)` | `CP HRD (College of Computing Human Resource Development System)` |
| **`EmailTemplateHelper.java`** (Test & Feedback) | `...ในระบบ CP ACAD...` | `...ในระบบ CP HRD (College of Computing Human Resource Development System)...` |
| **`CommonUtil.java`** | `helper.setSubject("รีเซ็ตรหัสผ่าน - ระบบ CP ACAD...");`<br>`helper.setSubject("รหัส OTP สำหรับเข้าสู่ระบบครั้งแรก - ระบบ CP ACAD...");` | `helper.setSubject("Password Reset - CP HRD (College of Computing, KKU)");`<br>`helper.setSubject("First-time Login OTP - CP HRD (College of Computing, KKU)");` |
| **`FeedbackService.java`** | `[CP ACAD] รายงาน: ...` | `[CP HRD] Report: ...` |
| **`SystemAlertService.java`** | `[CP ACAD] ...` | `[CP HRD] ...` |
| **`AcademicSettingsController.java`** | `[CP ACAD] ทดสอบการส่งอีเมลผ่าน KKU SMTP Relay...` | `[CP HRD] KKU SMTP Relay Test...` |
| **`EvaluationExpiryScheduler.java`** | `... - CP ACAD` | `... - CP HRD` |

---

### 2.3 Configuration & Metadata

| File | Property / Tag | New Value |
|---|---|---|
| **`pom.xml`** | `<name>` | `CP_HRD` |
| **`pom.xml`** | `<description>` | `CP HRD - College of Computing Human Resource Development System` |
| **`application.properties`** | `spring.application.name` | `CP-HRD` |
| **`application.properties`** | Header comment block | `# CP HRD (College of Computing Human Resource Development System)` |

---

## 3. Verification & Testing Plan

1. **Compilation Check:**
   - Execute `./mvnw test-compile` to ensure all Java files compile without syntax or type errors.
2. **Visual & UI Inspection:**
   - Verify `/guest/login` (Sign In page) renders:
     - Brand Title: **CP HRD**
     - Subtitle: **COLLEGE OF COMPUTING HUMAN RESOURCE DEVELOPMENT SYSTEM**
     - Browser Tab Title: English clean title
   - Verify `/user/academic/dashboard` and `/admin/academic/requests`:
     - Sidebar brand and subtitle
     - Collapsed mini-sidebar badge
     - Page `<title>`
     - Footer text
3. **Search & Notification Check:**
   - Verify that search dropdown properly strips `CP HRD` from document titles.
   - Verify notification dialog displays actor name as `CP HRD System`.
4. **Email Preview Verification:**
   - Check `EmailTemplateHelper` rendering with test subject `[CP HRD]`.
