/**
 * Position Document Form Validation
 * Prevents saving if any visible required fields are empty.
 * Only validates fields inside visible sections (display !== 'none').
 * 
 * Excludes: hidden fields, checkboxes, radio buttons, and fields
 * inside hidden position-specific sections (sectionAsst/sectionAssoc/sectionProf).
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

            // Get all input/select/textarea in the form
            var fields = form.querySelectorAll('input[type="text"], input[type="email"], input[type="tel"], input[type="number"], input[type="date"], select, textarea');

            fields.forEach(function(field) {
                // Skip hidden fields, checkboxes, radio
                if (field.type === 'hidden' || field.type === 'checkbox' || field.type === 'radio') return;

                // Skip fields inside hidden sections (display:none parents)
                if (!isVisible(field)) return;

                // Skip fields with specific names that are optional
                if (field.name === '_csrf' || field.name === 'action') return;

                // Check if empty
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
                    '<strong>กรุณากรอกข้อมูลให้ครบทุกช่อง</strong> — ยังมี ' + emptyFields.length + ' ช่องที่ยังไม่ได้กรอก' +
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
