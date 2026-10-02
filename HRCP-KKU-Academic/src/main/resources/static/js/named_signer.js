/**
 * ผู้ลงนามตามชื่อที่กรอกในแบบฟอร์ม (เช่นเอกสารที่ 3: หัวหน้าสาขา รองคณบดี คณบดี เจ้าหน้าที่)
 *
 * แต่ละตำแหน่งในแผงลงนาม ([data-named-signer="<ชื่อช่อง>"]) ไม่มีตัวเลือกผู้ลงนาม ผู้ลงนามคือคนที่ชื่ออยู่ใน
 * ช่องชื่อของตำแหน่งนั้นในแบบฟอร์ม ไฟล์นี้จับคู่ชื่อกับบัญชีให้เห็นทันทีที่พิมพ์/เลือกชื่อ
 * กติกาเดียวกับเซิร์ฟเวอร์ (SignatureWorkflowService.signersNamedInForm) ซึ่งจับคู่ซ้ำเองตอนส่งเสมอ
 */
(function () {
    'use strict';

    var candidates = window.NAMED_SIGNER_CANDIDATES || [];

    function normalize(text) {
        return (text || '').trim().replace(/\s+/g, ' ');
    }

    function fieldFor(name) {
        var all = document.querySelectorAll('[name="' + name + '"]');
        for (var i = 0; i < all.length; i++) {
            if (!all[i].closest('[data-named-signer]')) return all[i];
        }
        return null;
    }

    function bind(box) {
        var field = fieldFor(box.getAttribute('data-named-signer'));
        var label = box.querySelector('[data-named-signer-label]');
        var hint = box.querySelector('[data-named-signer-hint]');
        var hidden = box.querySelector('input[name="signerUserIds"]');

        function show(text, note, problem) {
            label.textContent = text;
            hint.textContent = note;
            hint.classList.toggle('text-danger', problem);
            hint.classList.toggle('text-muted', !problem);
            box.classList.toggle('border-danger', problem);
        }

        function update() {
            hidden.value = '';
            if (!field) {
                show('—', 'ไม่พบช่องชื่อของตำแหน่งนี้ในหน้า', true);
                return;
            }
            var name = normalize(field.value);
            if (!name) {
                show('ยังไม่ได้เลือกชื่อในเอกสาร', 'ตำแหน่งนี้จะยังไม่ถูกส่งในรอบนี้ — เลือกชื่อในเอกสารก่อน', false);
                return;
            }
            // ตัวค้นหาชื่อ (person_picker.js) เก็บรหัสบัญชีของคนที่เลือกไว้ใน "<ช่อง>__signer"
            var chosen = document.querySelector('[name="' + field.name + '__signer"]');
            var seen = {};
            var matches = candidates.filter(function (c) {
                var hit = chosen && chosen.value ? String(c.userId) === chosen.value
                    : normalize(c.displayName) === name;
                if (!hit || seen[c.userId]) return false;
                seen[c.userId] = true;
                return true;
            });
            if (matches.length === 1) {
                // ชื่อที่พิมพ์ลงเอกสารคือชื่อในช่อง — รายการของแผงอาจต่อท้ายบทบาทไว้ จึงไม่ใช้ชื่อจากรายการ
                hidden.value = matches[0].userId;
                show(field.value.trim() + (matches[0].email ? ' (' + matches[0].email + ')' : ''),
                    'ผู้ลงนามตามชื่อที่เลือกในเอกสาร', false);
                return;
            }
            // ช่องที่ดึงมาจากเอกสารก่อนหน้า (เช่น กรรมการในเอกสารที่ 7) แก้ในหน้านี้ไม่ได้ — ต้องแก้ที่ต้นทาง
            var fix = field.readOnly ? 'แก้ที่เอกสารต้นทาง (ดูคำเตือนในแบบฟอร์ม)' : 'กรุณาค้นหาแล้วเลือกใหม่ในช่องชื่อของเอกสาร';
            if (matches.length > 1) {
                show('“' + name + '”', 'มีบัญชีชื่อนี้มากกว่าหนึ่งคน — ' + fix, true);
            } else {
                show('“' + name + '”', 'ยังไม่ได้เลือกจากรายชื่อในระบบ — ' + fix, true);
            }
        }

        if (field) {
            field.addEventListener('input', update);
            field.addEventListener('change', update);
        }
        update();
        // ค่าที่บันทึกไว้ถูกเติมลงฟอร์มด้วยสคริปต์ตอน DOMContentLoaded (ไม่มี input event) — อ่านซ้ำเมื่อหน้าโหลดเสร็จ
        window.addEventListener('load', update);
    }

    function init() {
        document.querySelectorAll('[data-named-signer]').forEach(bind);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
