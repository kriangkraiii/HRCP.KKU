/**
 * Position Document Form Validation
 *
 * Blocks saving only when a field that is genuinely mandatory is left empty.
 * "Mandatory" means the element carries the `required` attribute — fields
 * without it are optional and may be submitted blank, which is the normal case
 * for these forms (ผู้ยื่นมักไม่มีข้อมูลครบทุกช่องในเอกสารเดียว).
 *
 * Skipped: fields without `required`, disabled fields (e.g. ส่วนของเจ้าหน้าที่),
 * hidden inputs, checkboxes/radios, and anything inside a hidden section
 * (sectionAsst/sectionAssoc/sectionProf).
 */
(function() {
    'use strict';

    document.addEventListener('DOMContentLoaded', function() {
        var form = document.querySelector('form[action*="/document/"]');
        if (!form) return;

        form.addEventListener('submit', function(e) {
            // Clear previous validation highlights
            form.querySelectorAll('.is-invalid').forEach(function(el) {
                el.classList.remove('is-invalid');
            });
            form.querySelectorAll('.validation-msg').forEach(function(el) {
                el.remove();
            });

            var emptyFields = [];

            // Only fields explicitly marked required are checked
            var fields = form.querySelectorAll(
                'input[required], select[required], textarea[required]');

            fields.forEach(function(field) {
                // Disabled fields are never submitted, so never validate them
                if (field.disabled) return;

                // Checkbox/radio requiredness is left to the browser
                if (field.type === 'hidden' || field.type === 'checkbox' || field.type === 'radio') return;

                // Skip fields inside hidden sections (display:none parents)
                if (!isVisible(field)) return;

                var val = (field.value || '').trim();
                if (!val || val === '-- เลือก --') {
                    emptyFields.push(field);
                }
            });

            if (emptyFields.length > 0) {
                e.preventDefault();

                // Highlight empty fields
                emptyFields.forEach(function(field) {
                    field.classList.add('is-invalid');
                });

                // Scroll to first empty field
                emptyFields[0].scrollIntoView({ behavior: 'smooth', block: 'center' });
                emptyFields[0].focus();

                // Show alert
                var existingAlert = document.getElementById('validationAlert');
                if (existingAlert) existingAlert.remove();

                var alert = document.createElement('div');
                alert.id = 'validationAlert';
                alert.className = 'alert alert-danger alert-dismissible fade show';
                alert.style.cssText = 'position:fixed;top:20px;left:50%;transform:translateX(-50%);z-index:9999;min-width:400px;box-shadow:0 4px 20px rgba(0,0,0,0.15);border-radius:12px;';
                alert.innerHTML = '<i class="fas fa-exclamation-triangle me-2"></i>' +
                    '<strong>กรุณากรอกข้อมูลในช่องที่จำเป็น</strong> — ยังมี ' + emptyFields.length + ' ช่องที่ต้องกรอก' +
                    '<button type="button" class="btn-close" data-bs-dismiss="alert"></button>';
                document.body.appendChild(alert);

                // Auto-dismiss after 5s
                setTimeout(function() {
                    if (alert.parentNode) alert.remove();
                }, 5000);
            }
        });
    });

    function isVisible(el) {
        while (el && el !== document.body) {
            var style = window.getComputedStyle(el);
            if (style.display === 'none') return false;
            el = el.parentElement;
        }
        return true;
    }
})();
