/**
 * บอกผู้ใช้ว่าเหลือช่องไหนต้องกรอก ก่อนส่งเอกสารไปลงนาม
 *
 * เซิร์ฟเวอร์กันเอกสารที่กรอกไม่ครบอยู่แล้ว (SignatureWorkflowService.createEnvelope)
 * แต่ตอบได้แค่ว่า "ขาดกี่ช่อง" เพราะไม่รู้จักผังของฟอร์ม ไฟล์นี้เติมส่วนที่ขาด:
 * ชี้ให้เห็นว่าเป็นช่องไหน พาไปที่ช่องแรกที่ว่าง แล้วไฮไลต์ไว้
 *
 * การแบ่งหน้าที่:
 *   - Java (DocumentCompleteness) รู้ว่าช่องไหน "ถ้ามี" ส่งมาทาง data attribute
 *   - DOM รู้ว่าช่องไหนใช้กับตำแหน่งที่ขอ — ส่วนของ ศ./รศ. ที่ไม่เกี่ยวถูกซ่อนอยู่
 *     จึงข้ามไปเองโดยไม่ต้องเขียนกติกาตำแหน่งซ้ำที่นี่
 */
(function () {
    'use strict';

    var SKIP_TYPES = ['hidden', 'checkbox', 'radio', 'file', 'submit', 'button', 'reset'];
    var TRAILING_INDEX = /(\d+)$/;

    function toSet(raw) {
        var set = Object.create(null);
        (raw || '').split(',').forEach(function (item) {
            var key = item.trim();
            if (key) set[key] = true;
        });
        return set;
    }

    function toList(raw) {
        return (raw || '').split(',').map(function (s) { return s.trim(); })
            .filter(function (s) { return s.length > 0; });
    }

    /** ช่องที่ผู้ใช้มองไม่เห็นตอนนี้ไม่ใช่ช่องที่เขาต้องกรอก */
    function isVisible(el) {
        return el.offsetParent !== null || el.getClientRects().length > 0;
    }

    function isExtraRepeatedRow(name, prefixes) {
        for (var i = 0; i < prefixes.length; i++) {
            if (name.indexOf(prefixes[i]) !== 0) continue;
            var m = TRAILING_INDEX.exec(name);
            if (m) return m[1] !== '1';
        }
        return false;
    }

    /** ข้อความบนป้ายกำกับของช่อง เพื่อบอกผู้ใช้เป็นภาษาคน ไม่ใช่ชื่อตัวแปร */
    function labelOf(el) {
        if (el.id) {
            var forLabel = document.querySelector('label[for="' + el.id + '"]');
            if (forLabel) return clean(forLabel.textContent);
        }
        var wrapper = el.closest('.col, [class*="col-"], .mb-3, .form-group, td');
        if (wrapper) {
            var inner = wrapper.querySelector('label');
            if (inner) return clean(inner.textContent);
        }
        return el.getAttribute('placeholder') || el.name;
    }

    function clean(text) {
        return (text || '').replace(/\s+/g, ' ').replace(/\*$/, '').trim();
    }

    function findMissing(docForm, optionalRaw, repeatableRaw) {
        var optional = toSet(optionalRaw);
        var prefixes = toList(repeatableRaw);
        var missing = [];

        docForm.querySelectorAll('input[name], select[name], textarea[name]').forEach(function (el) {
            var type = (el.getAttribute('type') || '').toLowerCase();
            if (SKIP_TYPES.indexOf(type) !== -1) return;
            if (el.disabled || el.readOnly) return;
            if (!el.name || el.name === '_csrf' || el.name === 'action') return;
            if (optional[el.name]) return;
            if (el.hasAttribute('data-optional')) return;
            if (isExtraRepeatedRow(el.name, prefixes)) return;
            if (!isVisible(el)) return;
            if ((el.value || '').trim() !== '') return;

            missing.push({ el: el, label: labelOf(el) });
        });

        return missing;
    }

    function highlight(missing) {
        missing.forEach(function (item) { item.el.classList.add('is-invalid'); });
        missing[0].el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        try {
            missing[0].el.focus({ preventScroll: true });
        } catch (e) {
            /* บางเบราว์เซอร์ไม่รับ options — ไม่สำคัญพอจะให้พัง */
        }
    }

    function clearHighlight(docForm) {
        docForm.querySelectorAll('.is-invalid').forEach(function (el) {
            el.classList.remove('is-invalid');
        });
    }

    function report(panelForm, missing) {
        var box = panelForm.querySelector('.required-fields-error');
        if (!box) {
            box = document.createElement('div');
            box.className = 'required-fields-error alert alert-warning border-0 small mt-3 mb-0';
            box.setAttribute('role', 'alert');
            var anchor = panelForm.querySelector('button[type="submit"]');
            if (anchor && anchor.parentNode) {
                anchor.parentNode.insertBefore(box, anchor);
            } else {
                panelForm.appendChild(box);
            }
        }

        var names = missing.slice(0, 8).map(function (item) { return item.label; });
        var more = missing.length > names.length
            ? ' และอีก ' + (missing.length - names.length) + ' ช่อง'
            : '';
        box.innerHTML = '<i class="fas fa-triangle-exclamation me-1"></i>'
            + '<strong>ยังส่งไปลงนามไม่ได้ — กรอกข้อมูลไม่ครบ ' + missing.length + ' ช่อง</strong>'
            + '<div class="mt-1">' + names.join(', ') + more + '</div>';
        box.hidden = false;
    }

    function clearReport(panelForm) {
        var box = panelForm.querySelector('.required-fields-error');
        if (box) box.hidden = true;
    }

    window.DocRequiredFields = {
        /**
         * @return true เมื่อกรอกครบและส่งต่อได้
         */
        check: function (panelForm) {
            var docForm = document.querySelector('form[data-auto-draft]');
            if (!docForm) return true;

            clearHighlight(docForm);
            clearReport(panelForm);

            var missing = findMissing(docForm,
                panelForm.getAttribute('data-optional-fields'),
                panelForm.getAttribute('data-repeatable-prefixes'));

            if (missing.length === 0) return true;

            report(panelForm, missing);
            highlight(missing);
            return false;
        },
    };
})();
