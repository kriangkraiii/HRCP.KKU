# Project Plan: Signature Submission Gate & Admin Re-sign Workflow

**File Name:** `docs/PLAN-signature-submission-review.md`  
**Mode:** Plan Mode (Option A + B Hybrid)  
**Created:** 2026-08-24  

---

## 🎯 Goal
Implement a pre-submission applicant signature validation gate and an admin-driven per-document and batch re-sign/revision flow with real-time multi-channel notification (Email + In-App) and form editing capability.

---

## 📋 Task Breakdown

### Phase 1: Backend Signature Completeness & Validation Gate
- Add validation methods in `SignatureWorkflowService`:
  - `areApplicantSignaturesComplete(SignatureModule, Long requestId, List<Integer> docTypes)`
  - `getUnsignedApplicantDocTypes(SignatureModule, Long requestId, List<Integer> docTypes)`
- Integrate validation in `AcademicApplicantController.submitRequest`:
  - Enforce docs 0 and 1 applicant signatures
- Integrate validation in `PositionApplicantController.submitRequest`:
  - Enforce docs 1, 2, 3, 4, and 9 applicant signatures

### Phase 2: Admin Document Re-sign & Return Actions
- Implement `requestDocumentResign` in `SignatureWorkflowService`:
  - Unlock active envelope for the target document
  - Reset / invalidate signer steps
  - Record audit event with admin's reason
- Expose REST / Form POST endpoints in `AcademicAdminController` and `PositionAdminController`
- Support batch request return with document checklist

### Phase 3: Immediate Multi-Channel Notification
- Implement email dispatch with rejection reasons in `SignatureNotifier`
- Create in-app notifications in `NotificationService` linking to document editing URLs

### Phase 4: Frontend UI / UX
- Update applicant request detail pages:
  - Add signature status badges ("ลงนามแล้ว", "รอผู้ยื่นลงนาม", "ต้องแก้ไข/ลงนามใหม่")
  - Disable submit button with descriptive alert box if unsigned docs exist
- Update admin request detail pages:
  - Add "ขอให้ลงนามใหม่ / ส่งกลับแก้ไข" action buttons with reason modals

### Phase 5: Testing & Verification
- Unit & integration tests for signature validation logic
- `./mvnw test` suite run to ensure 100% build pass

---

## 🚀 Execution
Run `/create` or approve `implementation_plan.md` to begin implementation.
