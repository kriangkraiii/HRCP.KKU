# PLAN: Document Verification Code Input in E-Signature Inbox

## Context & Objective
Provide an intuitive, fast, and responsive document verification code lookup directly on `/esign/inbox`.
Allow users (applicants, signers, committee, admins) to input a verification code (e.g. `55B5NXJLWW`) and immediately view signature audit details and integrity reports.

---

## Proposed UI/UX Architecture

### 1. Desktop Layout (`d-none d-md-flex`)
- **Location**: Top Header Right section of `/esign/inbox` alongside the existing actions ("เริ่มลงนามต่อเนื่อง", "ลายเซ็นของฉัน").
- **Component**: Compact Input Group with security shield icon, uppercase auto-formatting, and submit button:
  ```html
  <div class="input-group">
      <span class="input-group-text bg-white border-end-0 text-primary">
          <i class="fas fa-shield-halved"></i>
      </span>
      <input type="text" class="form-control border-start-0 text-uppercase" placeholder="รหัสตรวจสอบ (เช่น 55B5NXJLWW)" maxlength="20">
      <button type="submit" class="btn btn-outline-primary">
          <i class="fas fa-search me-1"></i> ตรวจสอบ
      </button>
  </div>
  ```

### 2. Mobile Layout (`d-flex d-md-none`)
- **Location**: Icon button / compact action button in header action list.
- **Component**: Clicking opens `#verifyCodeModal` with prominent input field, helpful guidance on where to find the code (bottom of signed PDF or notification email), and clear submit action.

### 3. Backend Fallback Route
- Add `@GetMapping("/verify")` in `SigningController.java` to gracefully handle `/esign/verify?code=...` or standalone `/esign/verify` without resulting in 404 errors.

---

## Tasks Breakdown

| Task # | File | Action | Description |
|---|---|---|---|
| **Task 1** | `SigningController.java` | Modify | Add `@GetMapping({"/verify", "/verify/"})` endpoint with optional query param support |
| **Task 2** | `templates/academic/esign/inbox.html` | Modify | Add Header Quick Search Bar (Desktop) + Verify Modal Trigger (Mobile) + Modal markup + JS verification handler |
| **Task 3** | `templates/academic/esign/verify.html` | Modify | Add quick search input on the verify page for empty/not-found states so users can re-try codes easily |
| **Task 4** | Verification & Testing | Verify | Test desktop quick search, mobile modal, empty code validation, and valid code redirect (`/esign/verify/55B5NXJLWW`) |

---

## Verification Plan

### Manual Verification
1. Navigate to `http://localhost:8081/esign/inbox`
2. **Desktop test**:
   - Enter `55B5NXJLWW` in the header search input and press Enter / click "ตรวจสอบ"
   - Verify page redirects to `/esign/verify/55B5NXJLWW` and displays full digital signature verification report
3. **Mobile responsive test**:
   - Shrink browser viewport below 768px
   - Verify search input collapses to "ตรวจสอบเอกสาร" modal button
   - Click button, enter code in modal, and submit
4. **Error handling**:
   - Enter invalid code (e.g. `INVALID123`) -> redirects and displays friendly "ไม่พบเอกสารตามรหัสที่ระบุ" message with retry input box.
