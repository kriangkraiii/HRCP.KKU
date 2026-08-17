/**
 * Unchecked-checkbox defaults for document forms (Phase 1 + Phase 2).
 *
 * A browser omits unchecked checkboxes from the POST body entirely. The DOCX
 * generator then finds no value for that placeholder and strips it
 * (DocumentGenerationService step 2.5), so an unticked box renders as blank
 * space instead of an empty box in the finished document.
 *
 * On submit this injects a hidden "☐" for every unchecked, named checkbox so
 * the generated document always shows an empty box. Mirrors what auto_draft.js
 * already does when saving drafts, keeping draft and final output identical.
 *
 * Replaces the per-form inline onsubmit handlers that previously did this for
 * doc forms 6/7/9 only.
 */
(function() {
    'use strict';

    var MARKER = 'data-unchecked-default';

    document.addEventListener('DOMContentLoaded', function() {
        document.querySelectorAll('form[action*="/document/"]').forEach(function(form) {
            form.addEventListener('submit', function() {
                // Drop injections from an earlier submit attempt that was
                // blocked by validation, otherwise they accumulate.
                form.querySelectorAll('input[' + MARKER + ']').forEach(function(el) {
                    el.remove();
                });

                form.querySelectorAll('input[type="checkbox"]').forEach(function(cb) {
                    if (cb.checked || cb.disabled || !cb.name) return;
                    var hidden = document.createElement('input');
                    hidden.type = 'hidden';
                    hidden.name = cb.name;
                    hidden.value = '☐';
                    hidden.setAttribute(MARKER, '');
                    form.appendChild(hidden);
                });
            });
        });
    });
})();
