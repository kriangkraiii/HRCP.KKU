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
        // ช่องในตาราง — ใช้หัวคอลัมน์เดียวกัน
        var cell = el.closest('td');
        var table = cell && cell.closest('table');
        if (table) {
            var th = table.querySelectorAll('thead th')[cell.cellIndex];
            if (th && clean(th.textContent)) return clean(th.textContent);
        }
        return el.getAttribute('placeholder') || el.name;
    }

    function clean(text) {
        return (text || '').replace(/\s+/g, ' ').replace(/\*$/, '').trim();
    }

    /** เลขที่หนังสือที่มีแต่รหัสหน่วยงาน (เช่น "อว 660301.26.8/") ยังไม่มีเลข */
    var BARE_PREFIX = /\/\s*$/;

    /**
     * @param opts.officeNumbers  true ตอนกดบันทึกเลขที่หนังสือ — ช่องที่มีแต่รหัสหน่วยงานนับว่ายังว่าง
     *                            (ตอนส่งลงนาม/ส่งต่อไม่นับ เพราะเลขออกให้หลังลงนามครบ)
     */
    function findMissing(docForm, optionalRaw, repeatableRaw, opts) {
        var optional = toSet(optionalRaw);
        var prefixes = toList(repeatableRaw);
        var officeNumbers = !!(opts && opts.officeNumbers);
        var missing = [];

        // หมายเหตุของช่องติ๊กเจ้าหน้าที่ตรวจคู่กับช่องติ๊กในรอบล่าง ไม่ใช่ช่องบังคับเดี่ยว ๆ —
        // ติ๊กแล้วหมายเหตุว่างได้ (เหมือน DocumentCompleteness.missingAdminFields ฝั่งเซิร์ฟเวอร์)
        var notes = Object.create(null);
        docForm.querySelectorAll('input[type="checkbox"][data-required-check][data-note]').forEach(function (cb) {
            notes[cb.getAttribute('data-note')] = true;
        });

        docForm.querySelectorAll('input[name], select[name], textarea[name]').forEach(function (el) {
            var type = (el.getAttribute('type') || '').toLowerCase();
            if (SKIP_TYPES.indexOf(type) !== -1) return;
            if (el.disabled || el.readOnly) return;
            if (!el.name || el.name === '_csrf' || el.name === 'action') return;
            if (optional[el.name] || notes[el.name]) return;
            if (el.hasAttribute('data-optional')) return;
            if (isExtraRepeatedRow(el.name, prefixes)) return;
            if (!isVisible(el)) return;
            var value = (el.value || '').trim();
            if (value !== '' && !(officeNumbers && el.name === 'memo_no' && BARE_PREFIX.test(value))) return;

            missing.push({ el: el, label: labelOf(el) });
        });

        // ช่องติ๊กของเจ้าหน้าที่ (data-required-check): ต้องติ๊ก หรือถ้าไม่ติ๊กต้องเขียนหมายเหตุ
        // ในช่องที่ data-note ชี้ไว้ — ช่องติ๊กถูกข้ามในรอบบน เพราะช่องติ๊กทั่วไปไม่ติ๊กก็ได้
        docForm.querySelectorAll('input[type="checkbox"][data-required-check]').forEach(function (cb) {
            if (cb.disabled || cb.checked || !isVisible(cb)) return;
            var target = cb.getAttribute('data-target');
            var stored = target ? docForm.querySelector('[name="' + target + '"]') : null;
            if (stored && stored.disabled) return;
            var noteName = cb.getAttribute('data-note');
            var note = noteName ? docForm.querySelector('[name="' + noteName + '"]') : null;
            if (note && (note.value || '').trim() !== '') return;
            missing.push({ el: cb, label: cb.getAttribute('data-label') || labelOf(cb) });
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
        var title = panelForm.getAttribute('data-required-title') || 'ยังส่งไปลงนามไม่ได้';
        box.innerHTML = '<i class="fas fa-triangle-exclamation me-1"></i>'
            + '<strong>' + title + ' — กรอกข้อมูลไม่ครบ ' + missing.length + ' ช่อง</strong>'
            + '<div class="mt-1">' + names.join(', ') + more + '</div>';
        box.hidden = false;
    }

    function clearReport(panelForm) {
        var box = panelForm.querySelector('.required-fields-error');
        if (box) box.hidden = true;
    }

    /**
     * เซิร์ฟเวอร์ปฏิเสธการส่งลงนามเพราะกรอกไม่ครบ แล้วส่งชื่อตัวแปรของช่องที่ขาดมา — แปลงเป็นชื่อช่อง
     * จากป้ายกำกับในฟอร์ม และไฮไลต์ช่องที่มองเห็น รอให้สคริปต์ของฟอร์มเติมค่าและสร้างแถวก่อน
     */
    function explainServerMissing() {
        var holder = document.querySelector('.missing-field-names[data-missing-fields]');
        if (!holder) return;
        var docForm = document.querySelector('form[id^="doc"]') || document;
        var labels = [];
        var found = [];
        toList(holder.getAttribute('data-missing-fields')).forEach(function (name) {
            var el = docForm.querySelector('[name="' + name + '"]');
            // ช่องซ่อนพาไปที่ช่องที่ผู้ใช้กรอกจริงซึ่งอยู่คู่กัน
            if (el && el.type === 'hidden') {
                var pair = el.previousElementSibling;
                el = pair && /^(INPUT|SELECT|TEXTAREA)$/.test(pair.tagName) ? pair : null;
            }
            // กติกาที่ไม่ใช่ช่องเดียว (เช่น เอกสารที่ 9 ต้องติ๊กสถานะอย่างน้อยหนึ่งข้อ) — ส่วนของฟอร์มประกาศตัวด้วย data-missing-key
            var group = el ? null : docForm.querySelector('[data-missing-key="' + name + '"]');
            if (group) {
                var groupLabel = group.getAttribute('data-missing-label') || name;
                if (labels.indexOf(groupLabel) === -1) labels.push(groupLabel);
                if (isVisible(group)) found.push({ el: group, label: groupLabel });
                return;
            }
            var label = el ? labelOf(el) : name;
            if (labels.indexOf(label) === -1) labels.push(label);
            if (el && isVisible(el)) found.push({ el: el, label: label });
        });
        holder.textContent = 'ช่องที่ยังขาด: ' + labels.join(', ');
        if (found.length) highlight(found);
    }

    document.addEventListener('DOMContentLoaded', function () {
        setTimeout(explainServerMissing, 600);
    });

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

        /**
         * ตรวจฟอร์มเอกสารก่อนกดบันทึกเลขที่หนังสือ — ต้องกรอกทุกช่องที่ยังเปิดให้กรอก
         *
         * @param docForm ฟอร์มเอกสาร
         * @param anchor  ปุ่มที่กด ข้อความเตือนจะขึ้นเหนือกลุ่มปุ่มนี้
         * @return true เมื่อกรอกครบ
         */
        checkForm: function (docForm, anchor) {
            if (!docForm) return true;
            clearHighlight(docForm);
            var host = anchor ? anchor.parentNode : docForm;
            var old = host.querySelector(':scope > .required-fields-error');
            if (old) old.hidden = true;

            var missing = findMissing(docForm,
                docForm.getAttribute('data-optional-fields'),
                docForm.getAttribute('data-repeatable-prefixes'),
                { officeNumbers: true });
            if (missing.length === 0) return true;

            var box = old;
            if (!box) {
                box = document.createElement('div');
                box.className = 'required-fields-error alert alert-warning border-0 small w-100 mb-0';
                box.setAttribute('role', 'alert');
                host.insertBefore(box, host.firstChild);
            }
            var names = missing.slice(0, 8).map(function (item) { return item.label; });
            var more = missing.length > names.length
                ? ' และอีก ' + (missing.length - names.length) + ' ช่อง'
                : '';
            box.innerHTML = '<i class="fas fa-triangle-exclamation me-1"></i>'
                + '<strong>ยังบันทึกไม่ได้ — กรอกข้อมูลไม่ครบ ' + missing.length + ' ช่อง</strong>'
                + '<div class="mt-1">' + names.join(', ') + more + '</div>';
            box.hidden = false;
            highlight(missing);
            return false;
        },
    };
})();
