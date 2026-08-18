# Project Plan: Smart Auto-Fill & Document Data Inheritance System

**Task Slug:** `doc-auto-fill`  
**Target:** Automatic applicant profile pre-population and cross-document data inheritance across Teaching Evaluation and Position Request systems + Realistic test user seed data.

---

## 1. Context & Objectives

Currently, applicants and administrators often have to re-type personal details (Title, Name, Position, Department, Faculty, Contact) and shared request metadata (Course info, Committee names, Meeting dates, Dean/Head of department names) repeatedly across 17 different document forms (8 in Teaching Evaluation, 9 in Academic Position).

### Objectives:
1. **Applicant Profile Auto-Fill:**
   - Pre-populate applicant identity (`title`, `applicant_name`, `academic_position`, `employee_type`, `department`, `faculty`, `email`, `tel`) across all forms on initial load.
2. **Cross-Document Data Propagation (Data Flow):**
   - **Teaching Evaluation (`AcademicRequest`):**
     - Doc 0 (Course code, name, year, applicant info) &rarr; auto-flows into Doc 1, Doc 3, Doc 4, Doc 5, Doc 6, Doc 7, Doc 8.
     - Doc 2/3 (Committee members, meeting date, time, room) &rarr; auto-flows into Doc 4, Doc 5, Doc 6, Doc 8.
   - **Academic Position (`PositionRequest`):**
     - Doc 1 (ก.พ.ว. 03 applicant info, target position, discipline) &rarr; auto-flows into Doc 2, 3, 4, 6, 7, 8, 9.
     - Doc 8 (Expert committee list) &rarr; auto-flows into review documents.
3. **Realistic Test Account Seed (`user@user.com`):**
   - Seed `user@user.com` with comprehensive, realistic Thai academic credentials (e.g. ผศ.ดร.สมชาย ใจดีวิชาการ, สาขาวิชาวิทยาการคอมพิวเตอร์ วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น) for instant, out-of-the-box testing.

---

## 2. Architecture & Design

### A. Central Auto-Fill Helper (`DocumentDataAutoFillHelper.java`)
Create a dedicated utility/service that accepts:
- Current `UserDtls`
- Target `documentType`
- Request entity (`AcademicRequest` or `PositionRequest`)
- Existing JSON map (if already partially saved)

It merges and returns a unified Map with:
- **Priority 1:** Saved user input in the current document.
- **Priority 2:** Inherited data from linked parent/previous documents (Doc 0, Doc 1, Doc 2, Doc 3, Doc 8).
- **Priority 3:** Default values from applicant profile (`UserDtls`) and faculty directory.

### B. Controller Integration
- `AcademicApplicantController`: Inject pre-filled data into `model.addAttribute("doc0Data", ...)`, `doc1Data`, etc.
- `PositionApplicantController`: Inject pre-filled data into `doc_form_1..9`.
- `AcademicAdminController` & `PositionAdminController`: Inject committee & meeting defaults into admin review forms.

### C. Seed Initializer (`AdminInitializer.java`)
- Enrich `user@user.com` with realistic Thai academic profile data.

---

## 3. Task Breakdown

### Phase 1: Realistic User Initializer
- [ ] Update `AdminInitializer.java` with complete realistic academic data for `user@user.com`.

### Phase 2: Core Auto-Fill Engine (`DocumentDataAutoFillHelper.java`)
- [ ] Implement `DocumentDataAutoFillHelper` for `AcademicRequest`:
  - Profile pre-fill (name, position, department, faculty, email, phone)
  - Doc 0 &rarr; Doc 1..8 inheritance
  - Doc 2/3 (Committee names & meeting info) &rarr; Doc 4, 5, 6, 8 inheritance
- [ ] Implement `DocumentDataAutoFillHelper` for `PositionRequest`:
  - Profile pre-fill for Doc 1..9
  - Doc 1 (ก.พ.ว. 03) &rarr; Doc 2, 3, 4, 6, 7, 9 inheritance

### Phase 3: Controllers & Template Binding
- [ ] Update `AcademicApplicantController.java` to inject auto-filled model attributes.
- [ ] Update `PositionApplicantController.java` to inject auto-filled model attributes.
- [ ] Update `AcademicAdminController.java` & `PositionAdminController.java` for committee propagation.
- [ ] Verify HTML templates respect default pre-filled values.

### Phase 4: Verification & Automated Tests
- [ ] Write unit tests (`DocumentDataAutoFillHelperTest.java`) verifying:
  - Fresh document loads applicant profile.
  - Subsequent documents inherit committee and course data.
  - Existing user modifications are not overwritten.
