# Project Plan: Update Project Logo (HRCP.KKU)

**Task Slug:** `update-project-logo`  
**Target:** Transition entire project to new CP HR KKU logo assets (`logo_transparent_background.png` & `logo_no_P.png`).

---

## 1. Scope of Work

1. **Asset Processing & Optimization:**
   - Trim excessive transparent padding from `logo_transparent_background.png` (from 1024x1024 to 657x251) so UI headers and sidebars don't shrink.
   - Save trimmed asset as `cphr_logo.png`, `cp_logo.png`, and update `cphr_new.png`.
   - Generate square icon for Favicon (`favicon.png`).

2. **Web Interface Update:**
   - Guest Auth Pages (`login.html`, `first_login.html`, `verify_2fa.html`, `verify_otp.html`, `set_password.html`, `reset_password.html`, `forgot_password.html`)
   - Main Sidebar & Dashboard Layout (`base_academic.html`)
   - Error Pages (`403.html`, `404.html`, `500.html`, `error.html`)

3. **Email Notification Templates:**
   - Update `EmailTemplateHelper.java` and `static/img/cp_logo.png` to use the new CP HR KKU logo.

4. **Verification & Testing:**
   - Execute unit tests `EmailTemplateHelperTest`.
   - Dispatch live sample email to `kriangkrai.p@kkumail.com` for final review.
