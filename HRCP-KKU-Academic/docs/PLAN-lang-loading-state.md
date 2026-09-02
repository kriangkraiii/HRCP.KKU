# PLAN-lang-loading-state.md - Language Switching Loading State

## 1. Context & Objectives
- **Goal**: Implement an instant visual loading state (Glassmorphism backdrop overlay + micro button spinner) for language switching (TH ⇄ EN).
- **Strategy**: No disruption to existing Google Translate integration. Enhance perceived performance and debounce user clicks.

## 2. Task Breakdown

### Task 1: UI & Styling Setup
- Create `#langLoadingOverlay` in `base_academic.html` and `login.html`.
- Modern KKU Glassmorphism design with spinner and bilingual text ("กำลังเปลี่ยนภาษา... / Changing language...").
- CSS fade-in / fade-out animations.

### Task 2: Interaction & Automation Logic
- Update `selectLang(lang)` to immediately trigger the loading overlay and button spinner.
- Disable button interactions to prevent double-submits.
- Add `MutationObserver` on `documentElement` / body to auto-dismiss loading overlay once DOM translation is complete.
- Add a 2.5s maximum safety timeout.

### Task 3: Resource Hint Optimization
- Add `<link rel="preconnect" href="https://translate.google.com">` and `<link rel="preconnect" href="https://translate.googleapis.com">`.

## 3. Verification Checklist
- [ ] Language switch TH -> EN displays overlay and button spinner immediately.
- [ ] Language switch EN -> TH restores Thai smoothly.
- [ ] Overlay closes automatically upon completion or timeout without getting stuck.
- [ ] UI remains responsive and free of console errors.
