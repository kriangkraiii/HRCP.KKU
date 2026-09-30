/**
 * เตือนเจ้าหน้าที่ระหว่างพิมพ์ ถ้าค่าที่กรอก (หมายเหตุ เลขที่หนังสือ ฯลฯ) ยาวเกินช่องในไฟล์ลงนาม
 *
 * ช่องในไฟล์ลงนามมีขนาดตายตัว ถ้าไม่เตือนตรงนี้ ค่าที่ยาวเกินจะไปล้มตอนคนถัดไปลงนาม ซึ่งแก้ช่องนั้นไม่ได้
 * เซิร์ฟเวอร์เป็นคนวัด (LateFieldFitApiController) ด้วยฟอนต์และกติกาเดียวกับตอนเขียนลงไฟล์จริง
 * การส่งเวียนก็ตรวจซ้ำอีกชั้น — ไฟล์นี้แค่บอกให้รู้ก่อน
 *
 * ตั้งค่าผ่าน window.LATE_FIELD_FIT = { formId, url } (ดู _admin_field_lock :: script)
 */
(function () {
    'use strict';

    var cfg = window.LATE_FIELD_FIT;
    if (!cfg || !cfg.url) return;
    var form = document.getElementById(cfg.formId);
    if (!form) return;

    var MESSAGE = 'ยาวเกินช่องในเอกสาร กรุณาย่อให้สั้นลง';
    var timer = null;
    var seq = 0;

    function token() {
        var el = form.querySelector('input[name="_csrf"]') ||
            document.querySelector('input[name="_csrf"]') ||
            document.querySelector('meta[name="_csrf"]');
        return el ? (el.value || el.content || '') : '';
    }

    function values() {
        var out = {};
        form.querySelectorAll('input[name]:not([type]), input[name][type="text"], textarea[name]').forEach(function (el) {
            if (!el.disabled) out[el.name] = el.value || '';
        });
        return out;
    }

    function mark(problems) {
        form.querySelectorAll('.late-fit-invalid').forEach(function (el) {
            el.classList.remove('late-fit-invalid', 'is-invalid');
        });
        form.querySelectorAll('.late-fit-feedback').forEach(function (el) { el.remove(); });
        problems.forEach(function (p) {
            var el = form.querySelector('[name="' + p.field + '"]');
            if (!el) return;
            el.classList.add('is-invalid', 'late-fit-invalid');
            var fb = document.createElement('div');
            fb.className = 'invalid-feedback late-fit-feedback';
            fb.textContent = MESSAGE;
            el.insertAdjacentElement('afterend', fb);
        });
    }

    function check() {
        var mine = ++seq;
        fetch(cfg.url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-HRCP-CT': token() },
            body: JSON.stringify(values())
        })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (body) {
                // คำตอบที่มาช้ากว่าการพิมพ์ครั้งล่าสุดทิ้งไป
                if (mine === seq && body && body.problems) mark(body.problems);
            })
            .catch(function () { /* เตือนไม่ได้ก็ไม่เป็นไร การส่งเวียนตรวจซ้ำอยู่แล้ว */ });
    }

    form.addEventListener('input', function (e) {
        if (!e.target.name) return;
        clearTimeout(timer);
        timer = setTimeout(check, 400);
    });
    // ค่าที่กรอกไว้แล้วตั้งแต่ก่อนเปิดหน้า (เช่นหมายเหตุที่บันทึกไว้) ต้องเตือนทันที
    check();
})();
