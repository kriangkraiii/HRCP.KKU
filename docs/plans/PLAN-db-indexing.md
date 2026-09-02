# Project Plan: Comprehensive Database Indexing for HRCP.KKU

## 📌 Executive Summary
วางแผนและกำหนดกลยุทธ์การทำ Indexing ให้กับตารางฐานข้อมูลทั้งหมดในโปรเจกต์ HRCP.KKU (Spring Data JPA / PostgreSQL) เพื่อเพิ่มประสิทธิภาพการค้นหา, การ JOIN ผ่าน Foreign Key, การกรองสถานะ (Filter), และการเรียงลำดับเวลา (Sorting) ให้พร้อมรับการทำงานระดับ Production และป้องกันปัญหา Performance Degradation เมื่อข้อมูลมีขนาดใหญ่ขึ้น

---

## 🎯 Scope of Work

### Phase 1: High Priority (Core & High-Traffic Tables)
1. **`user_dtls` (`UserDtls.java`)**
   - `email` (Login, SSO, Check Exists)
   - `reset_token` (Password Reset)
   - `role` (Admin / Faculty filtering)
   - `applicant_id` (Applicant lookup)
2. **`notifications` (`Notification.java`)**
   - `(recipient_id, is_deleted, is_read)` (Composite index for badge counts & unread tab)
   - `(recipient_id, is_deleted, is_starred)` (Starred tab)
   - `(recipient_id, is_deleted, is_important)` (Important tab)
   - `(recipient_id, created_at DESC)` (Chronological notification feed)
3. **`academic_request` (`AcademicRequest.java`)**
   - `applicant_id` (Foreign Key - อาจารย์ดูคำขอตนเอง)
   - `current_status` (Admin filter ตามสถานะคำขอ)
   - `(created_at DESC)` (เรียงลำดับรายการคำขอ)
4. **`position_request` (`PositionRequest.java`)**
   - `applicant_id` (Foreign Key)
   - `current_status`
   - `(applicant_id, current_status)` (เช็ค Active request ป้องกันยื่นซ้ำ)
   - `(created_at DESC)`

---

### Phase 2: Medium Priority (Child & Document Tables)
1. **`academic_attachment` & `position_attachment`**
   - `(request_id, is_deleted)`
   - `(request_id, uploaded_at DESC)`
2. **`academic_document` & `position_document`**
   - `(request_id, document_type, is_deleted)`
   - `(request_id, is_draft, is_deleted)`
3. **`request_status_history` & `position_status_history`**
   - `(request_id, changed_at DESC)` (Timeline ประวัติสถานะ)
4. **`position_document_edit_log`**
   - `(request_id, edited_at DESC)`

---

### Phase 3: Lower Priority (Petitions, Staff Directory, File Storage)
1. **`petitions` & `petition_statuses`**
   - `petitions`: `(user_id, created_at DESC)`
   - `petition_statuses`: `(petition_id, created_at ASC)`
2. **`staff_member`**
   - `email`
   - `fs_user_id`
   - `citizen_id`
   - `(first_name, last_name)`
3. **`user_folder`, `user_file`, `admin_folder`, `admin_file`**
   - `user_file`: `(owner_id, folder_id, is_deleted)`
   - `user_folder`: `(owner_id, parent_id)`
   - `admin_file`: `(folder_id, is_deleted)`
   - `admin_folder`: `parent_id`

---

## 🛠️ Implementation Strategy Matrix

| Entity Model | Proposed Index Name | Columns / Order | Target Query / Use Case |
|---|---|---|---|
| `UserDtls` | `idx_user_email` | `email` | `findByEmail`, Login, SSO sync |
| `UserDtls` | `idx_user_reset_token` | `reset_token` | `findByResetToken` |
| `UserDtls` | `idx_user_role` | `role` | `findByRole` |
| `Notification` | `idx_notif_recipient_active` | `recipient_id, is_deleted, is_read` | Unread badge count, Tab feed |
| `Notification` | `idx_notif_created` | `created_at DESC` | Feed ordering |
| `AcademicRequest` | `idx_acad_req_applicant` | `applicant_id` | `findByApplicantId` |
| `AcademicRequest` | `idx_acad_req_status` | `current_status` | `findByStatus` |
| `AcademicRequest` | `idx_acad_req_created` | `created_at DESC` | `findAllOrderByCreatedAtDesc` |
| `PositionRequest` | `idx_pos_req_applicant` | `applicant_id` | `findByApplicantId` |
| `PositionRequest` | `idx_pos_req_app_status` | `applicant_id, current_status` | `findActiveByApplicantId` |
| `PositionRequest` | `idx_pos_req_status` | `current_status` | Filter status list |
| `PositionRequest` | `idx_pos_req_created` | `created_at DESC` | `findAllOrderByCreatedAtDesc` |
| `AcademicAttachment` | `idx_acad_att_req_del` | `request_id, is_deleted` | File list filtering |
| `PositionAttachment` | `idx_pos_att_req_del` | `request_id, is_deleted` | File list filtering |
| `AcademicDocument` | `idx_acad_doc_req_type` | `request_id, document_type` | `findByRequestIdAndDocumentType` |
| `PositionDocument` | `idx_pos_doc_req_type` | `request_id, document_type, is_draft` | `findByRequestIdAndDocType` |
| `RequestStatusHistory` | `idx_req_hist_req_date` | `request_id, changed_at DESC` | Status timeline order |
| `PositionStatusHistory` | `idx_pos_hist_req_date` | `request_id, changed_at DESC` | Status timeline order |
| `StaffMember` | `idx_staff_email` | `email` | Staff lookup |
| `StaffMember` | `idx_staff_fs_uid` | `fs_user_id` | Directory sync matching |
| `UserFile` | `idx_userfile_owner_folder` | `owner_id, folder_id, is_deleted` | File manager tab |
| `AdminFile` | `idx_adminfile_folder_del` | `folder_id, is_deleted` | Admin file manager |

---

## 🧪 Verification & Validation Checklist
- [ ] 1. ตรวจสอบ Syntax ของ JPA `@Table(indexes = { ... })` บนทุก Entity
- [ ] 2. คอมไพล์โปรเจกต์ผ่าน `./mvnw clean test-compile` (Zero errors)
- [ ] 3. ตรวจสอบว่าไม่มีชื่อ Index ซ้ำกันระหว่าง Table ต่างๆ (ใช้ prefix ชัดเจน เช่น `idx_acad_...`, `idx_pos_...`)
- [ ] 4. จัดเตรียม Migration SQL Script สำหรับใช้รันบน Production Database (กรณี `spring.jpa.hibernate.ddl-auto=none` บน Production)
