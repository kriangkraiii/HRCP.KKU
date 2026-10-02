/**
 * ช่องเวลาแบบ 24 ชั่วโมง หน้าตาเหมือนช่องเวลาเดิม (ช่องเดียว + ไอคอนนาฬิกา)
 *
 * <p>input type="time" แสดง AM/PM ตามการตั้งค่าของเครื่อง หน้าเว็บบังคับให้เป็น 24 ชั่วโมงไม่ได้
 * จึงเป็นช่องข้อความ HH:MM — พิมพ์เองได้ (0930 → 09:30) หรือกดไอคอนเลือกชั่วโมง/นาทีจากรายการ
 *
 * <p>ค่าที่บันทึกอยู่ในช่องซ่อนตาม data-target ในรูปแบบที่พิมพ์ลงหนังสือราชการ (09.30)
 * ช่องที่มองเห็นไม่มี name จึงไม่ถูกส่งไปกับฟอร์ม และถูกล็อกตามช่องซ่อน (_admin_field_lock)
 *
 * <pre>
 * &lt;input type="hidden" name="x"&gt;
 * &lt;div class="time24" data-target="x"&gt;
 *   &lt;input type="text" class="form-control time24-input" data-target="x"&gt;
 * &lt;/div&gt;
 * </pre>
 */
(function () {
    'use strict';

    var TIME = /^\s*(\d{1,2})\s*[.:]?\s*(\d{2})/;

    function pad(n) { return (n < 10 ? '0' : '') + n; }

    /** "9.30", "09:30", "0930", "09.30 น." → "09:30"; อย่างอื่น → null */
    function parse(text) {
        var m = TIME.exec(text || '');
        if (!m || +m[1] > 23 || +m[2] > 59) return null;
        return pad(+m[1]) + ':' + m[2];
    }

    function list(values, cls) {
        var col = document.createElement('div');
        col.className = 'time24-col ' + cls;
        values.forEach(function (v) {
            var b = document.createElement('button');
            b.type = 'button';
            b.className = 'dropdown-item text-center';
            b.textContent = v;
            b.dataset.value = v;
            col.appendChild(b);
        });
        return col;
    }

    function init(root) {
        var input = root.querySelector('.time24-input');
        var form = root.closest('form');
        var stored = form && form.querySelector('[name="' + root.dataset.target + '"]');
        if (!input || !stored) return;

        input.setAttribute('autocomplete', 'off');
        input.setAttribute('inputmode', 'numeric');
        input.setAttribute('maxlength', '5');
        if (!input.placeholder) input.placeholder = '--:--';

        var hours = [], minutes = [];
        for (var h = 0; h < 24; h++) hours.push(pad(h));
        for (var m = 0; m < 60; m += 5) minutes.push(pad(m));

        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'time24-btn';
        btn.setAttribute('aria-label', 'เลือกเวลา');
        btn.dataset.target = root.dataset.target;
        btn.innerHTML = '<i class="far fa-clock"></i>';
        root.appendChild(btn);

        var menu = document.createElement('div');
        menu.className = 'dropdown-menu time24-menu';
        var hourCol = list(hours, 'time24-hours');
        var minuteCol = list(minutes, 'time24-minutes');
        menu.appendChild(hourCol);
        menu.appendChild(minuteCol);
        root.appendChild(menu);

        input.value = parse(stored.value) || '';

        function save() {
            var t = parse(input.value);
            stored.value = t ? t.replace(':', '.') : '';
            // ให้บันทึกร่างอัตโนมัติและตัวอย่างเอกสารเห็นการเปลี่ยน (ฟังที่ฟอร์ม)
            stored.dispatchEvent(new Event('input', { bubbles: true }));
        }

        function mark(col, value) {
            col.querySelectorAll('.dropdown-item').forEach(function (b) {
                b.classList.toggle('active', b.dataset.value === value);
            });
            var active = col.querySelector('.active');
            if (active) col.scrollTop = active.offsetTop - col.offsetTop; // ค่าที่เลือกอยู่บนสุด แบบช่องเวลาเดิม
        }

        function open() {
            if (input.disabled || input.readOnly) return;
            var t = parse(input.value);
            menu.classList.add('show');
            mark(hourCol, t ? t.slice(0, 2) : null);
            mark(minuteCol, t ? t.slice(3) : null);
        }

        function close() { menu.classList.remove('show'); }

        btn.addEventListener('click', function () {
            if (menu.classList.contains('show')) close(); else open();
        });

        hourCol.addEventListener('click', function (e) {
            var b = e.target.closest('.dropdown-item');
            if (!b) return;
            var t = parse(input.value);
            input.value = b.dataset.value + ':' + (t ? t.slice(3) : '00');
            input.classList.remove('is-invalid');
            mark(hourCol, b.dataset.value);
            save();
        });

        minuteCol.addEventListener('click', function (e) {
            var b = e.target.closest('.dropdown-item');
            if (!b) return;
            var t = parse(input.value);
            input.value = (t ? t.slice(0, 2) : '00') + ':' + b.dataset.value;
            input.classList.remove('is-invalid');
            save();
            close();
        });

        input.addEventListener('input', function (e) {
            // "." แบบหนังสือราชการ (9.30) ใช้แทน ":" ได้ — เหลือแค่ตัวเลขกับ ":"
            // ":" ที่ระบบเติมให้แล้วผู้ใช้พิมพ์ซ้ำ (09: แล้วกด .) ให้เหลือตัวเดียว
            var v = input.value.replace(/\./g, ':').replace(/[^\d:]/g, '').replace(/:+/g, ':');
            if (!(e.inputType && e.inputType.indexOf('delete') === 0)) {
                if (/^\d{4}$/.test(v)) v = v.slice(0, 2) + ':' + v.slice(2);      // 0930 → 09:30
                else if (/^([01]\d|2[0-3])$/.test(v)) v = v + ':';                // 09 → 09:
            }
            input.value = v.slice(0, 5);
        });

        input.addEventListener('blur', function () {
            var t = parse(input.value);
            if (input.value && !t) {
                input.classList.add('is-invalid');
                return;
            }
            input.classList.remove('is-invalid');
            input.value = t || '';
            save();
        });

        input.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') close();
            if (e.key === 'ArrowDown' && e.altKey) open();
        });

        document.addEventListener('click', function (e) {
            if (!root.contains(e.target)) close();
        });
    }

    // หลังสคริปต์ของหน้าเติมค่าที่บันทึกไว้ลงช่องซ่อน (DOMContentLoaded ที่ลงทะเบียนก่อนสคริปต์นี้)
    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('.time24').forEach(init);
    });
})();
