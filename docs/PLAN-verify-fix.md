# PLAN: Fix Document Verification Modal Submit in E-Signature Inbox

## Context & Root Cause
When clicking "ตรวจสอบทันที" in the verification modal, nothing happened because:
1. The `<script>` was placed **above** the `#verifyCodeModal` markup in `inbox.html`.
2. When the script executed at parse time, `document.getElementById('verifyCodeForm')` returned `null`, preventing the submit event listener from being attached.
3. The `<form>` lacked an `action` and `name="code"` attribute, so fallback browser submit simply reloaded `/esign/inbox`.

---

## Solution Steps

### 1. Fix DOM Structure & Script Lifecycle
- Move `#verifyCodeModal` HTML markup **above** the `<script>` tag or initialize inside `DOMContentLoaded` / ready-state wrapper.
- Add `action="/esign/verify"` and `method="GET"` with `name="code"` on the input so native HTML form submit works even without JS.

### 2. Async Pre-Check & In-Modal Warning Feedback
- Intercept submit via `addEventListener('submit')`:
  - Show spinner `[ ⏳ กำลังตรวจสอบ... ]` on button.
  - Call `/esign/verify/check/{code}` API.
  - **If Found**: Navigate immediately to `/esign/verify/{code}`.
  - **If Not Found**: Display `<div class="alert alert-danger">` inside the modal:
    `❌ ไม่พบเอกสารรหัส "..." ในระบบ กรุณาตรวจสอบรหัสอีกครั้ง`
    Reset button and auto-select input text for easy correction.

### 3. Verification Plan
- Enter valid code `55B5NXJLWW` -> Verifies and redirects to `/esign/verify/55B5NXJLWW`.
- Enter invalid code (e.g. `ABC1234567`) -> Displays red in-modal alert without leaving the page.
- Test Enter key press and click button on both Desktop and Mobile.
