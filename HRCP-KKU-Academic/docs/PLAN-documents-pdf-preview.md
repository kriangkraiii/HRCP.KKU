# PLAN-documents-pdf-preview.md - Fix PDF Preview Overlay in Documents Page

## 1. Context & Problem Statement
- In `/user/academic/documents`, clicking a document to view its PDF preview resulted in the preview appearing at the bottom of the page below the footer rather than as a centered, full-screen viewport modal.
- Cause: The `#pdfOverlay` element is trapped inside the main content / section layout and lacks strict viewport fixed inset styling and body detachment.

## 2. Task Breakdown

### Task 1: Modal Architecture & CSS Styling
- Define `.doc-pdf-overlay` CSS rules with `position: fixed !important`, `inset: 0 !important`, `z-index: 999999 !important`.
- Design an elegant dark header with PDF icon, document title, download action, and close button.
- Ensure proper sizing for the iframe container (`flex: 1`, full width and height).

### Task 2: JS Lifecycle & Teleportation
- Automatically append `#pdfOverlay` directly to `document.body` on DOMContentLoaded and on `openPdfOverlay`.
- Handle scroll locking (`overflow = 'hidden'`) when open, and clean up (`overflow = ''`, `iframe.src = ''`) on close.
- Support close via button, ESC key, and backdrop click.

## 3. Verification Checklist
- [ ] Clicking any PDF in `/user/academic/documents` opens a full-screen centered modal.
- [ ] No elements escape below the footer.
- [ ] Close button and ESC key close the modal cleanly.
