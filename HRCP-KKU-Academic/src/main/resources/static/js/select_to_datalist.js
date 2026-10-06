/**
 * Auto-converts <select> elements inside doc forms to <input> + <datalist>
 * so users can BOTH select from options AND type custom values.
 *
 * Every input[list] in a doc form then gets a styled dropdown (DatalistCombo)
 * in place of the browser's datalist popup. The list attribute moves to
 * data-combo-list; the <datalist> stays as the source of options.
 *
 * Loaded on every page by base_academic.html.
 */
document.addEventListener('DOMContentLoaded', function () {
    // ค่าที่บันทึกไว้ — ช่องนี้พิมพ์ค่าเองได้ (เช่นคำนำหน้าที่ไม่มีในรายการ) แต่ <select> ที่เซิร์ฟเวอร์
    // เรนเดอร์มาเลือกได้เฉพาะค่าที่มีในรายการ ค่าที่พิมพ์เองจึงหายตอนเปิดฟอร์มซ้ำ (เช่นตอนถูกส่งกลับ
    // ให้แก้) แล้วผู้ใช้ส่งลงนามไม่ได้เพราะช่องบังคับว่าง — อ่านจากข้อมูลที่บันทึกไว้แทน
    var saved = {};
    var holder = document.getElementById('existingDataHolder');
    if (holder && holder.value) {
        try { saved = JSON.parse(holder.value) || {}; } catch (e) { saved = {}; }
    }

    // Target all selects inside doc forms (cards with .card-academic)
    // ช่องชื่อผู้ลงนามเป็นตัวค้นหาชื่อ (person_picker.js) — เลือกได้อย่างเดียว ห้ามแปลงเป็นช่องพิมพ์อิสระ
    document.querySelectorAll('form[id^="doc"] select.form-select:not([data-person-picker])').forEach(function (sel) {
        const name = sel.name;
        const required = sel.required;
        const id = sel.id || ('dl_' + name);
        const datalistId = id + '_list';

        // Collect options (skip empty/placeholder)
        const options = [];
        let selectedValue = '';
        sel.querySelectorAll('option').forEach(function (opt) {
            if (opt.value && opt.value !== '') {
                options.push(opt.textContent.trim() || opt.value);
            }
            if (opt.selected && opt.value) {
                selectedValue = opt.textContent.trim() || opt.value;
            }
        });

        // Create input
        const input = document.createElement('input');
        input.type = 'text';
        input.name = name;
        input.className = 'form-control';
        input.setAttribute('list', datalistId);
        input.placeholder = sel.querySelector('option[value=""]')
            ? sel.querySelector('option[value=""]').textContent.trim()
            : '-- เลือกหรือพิมพ์ --';
        if (required) input.required = true;
        // ช่องคำนำหน้าแบบตำแหน่งวิชาการ — ยกเครื่องหมายตามไป สคริปต์ที่เติมค่าทีหลังจะได้แปลง
        // "ผศ.ดร." เป็น "ผู้ช่วยศาสตราจารย์" เหมือนตอนยังเป็น <select>
        if (sel.hasAttribute('data-academic-title')) input.setAttribute('data-academic-title', '');
        if (!selectedValue && typeof saved[name] === 'string' && saved[name].trim()) {
            selectedValue = window.AcademicTitle
                ? window.AcademicTitle.valueFor(input, saved[name].trim())
                : saved[name].trim();
        }
        if (selectedValue) input.value = selectedValue;
        if (sel.id) input.id = sel.id;
        // ต้องยกสถานะล็อกมาด้วย มิฉะนั้นช่องที่ถูกปิดไว้จะกลับมาแก้ได้ทันทีที่สคริปต์นี้ทำงาน
        // เพราะมันสร้าง element ใหม่ทั้งอัน และรันทีหลังสคริปต์ที่ปิดช่อง
        if (sel.disabled) input.disabled = true;
        if (sel.readOnly) input.readOnly = true;

        // Create datalist
        const datalist = document.createElement('datalist');
        datalist.id = datalistId;
        options.forEach(function (text) {
            const opt = document.createElement('option');
            opt.value = text;
            datalist.appendChild(opt);
        });

        // Replace select with input + datalist
        sel.parentNode.insertBefore(input, sel);
        sel.parentNode.insertBefore(datalist, sel);
        sel.remove();
    });

    // ป๊อปอัปของ <datalist> เบราว์เซอร์วาดเอง แต่ง CSS ไม่ได้ (Chrome ขึ้นกล่องดำทึบ ไม่เข้ากับธีม)
    // จึงถอด list ออกแล้วใช้รายการที่วาดเองแทน — ตัว <datalist> ยังอยู่ในหน้าเป็นแหล่งตัวเลือก
    // และอ่านใหม่ทุกครั้งที่เปิด รายการที่สคริปต์อื่นเติมทีหลังจึงขึ้นด้วย ช่องยังพิมพ์ค่าเองได้เหมือนเดิม
    document.querySelectorAll('form[id^="doc"] input[list]:not([data-person-picker])').forEach(DatalistCombo.attach);
});

var DatalistCombo = (function () {
    var seq = 0;

    function optionsOf(input) {
        var list = document.getElementById(input.dataset.comboList);
        if (!list) return [];
        var seen = {};
        var out = [];
        list.querySelectorAll('option').forEach(function (o) {
            var v = (o.value || o.textContent || '').trim();
            if (v && !seen[v]) { seen[v] = true; out.push(v); }
        });
        return out;
    }

    function attach(input) {
        if (input.dataset.comboList) return;
        input.dataset.comboList = input.getAttribute('list');
        input.removeAttribute('list');
        input.classList.add('dl-combo-input');
        input.setAttribute('autocomplete', 'off');
        input.setAttribute('role', 'combobox');
        input.setAttribute('aria-autocomplete', 'list');
        input.setAttribute('aria-expanded', 'false');

        var menu = document.createElement('ul');
        menu.id = 'dlCombo' + (++seq);
        menu.className = 'searchable-select-dropdown searchable-select-options dl-combo-menu';
        menu.setAttribute('role', 'listbox');
        input.setAttribute('aria-controls', menu.id);
        document.body.appendChild(menu);

        var items = [];
        var active = -1;

        function isOpen() { return menu.classList.contains('show'); }

        function locked() { return input.disabled || input.readOnly; }

        function place() {
            var r = input.getBoundingClientRect();
            menu.style.left = r.left + 'px';
            menu.style.width = r.width + 'px';
            var below = window.innerHeight - r.bottom;
            var up = below < Math.min(menu.scrollHeight, 280) + 8 && r.top > below;
            menu.classList.toggle('is-dropup', up);
            menu.style.top = up ? '' : r.bottom + 'px';
            menu.style.bottom = up ? (window.innerHeight - r.top) + 'px' : '';
        }

        function setActive(i) {
            if (active >= 0 && items[active]) items[active].classList.remove('is-focused');
            active = i;
            if (active >= 0 && items[active]) {
                items[active].classList.add('is-focused');
                items[active].scrollIntoView({ block: 'nearest' });
                input.setAttribute('aria-activedescendant', items[active].id);
            } else {
                input.removeAttribute('aria-activedescendant');
            }
        }

        // filter: ข้อความที่พิมพ์ — ว่างหรือ null คือแสดงทุกตัวเลือก
        function open(filter) {
            if (locked()) return;
            var q = (filter || '').trim().toLowerCase();
            var current = input.value.trim();
            var values = optionsOf(input).filter(function (v) {
                return !q || v.toLowerCase().indexOf(q) !== -1;
            });
            if (!values.length) { close(); return; }

            menu.innerHTML = '';
            items = values.map(function (v, i) {
                var li = document.createElement('li');
                li.id = menu.id + '_' + i;
                li.className = 'searchable-select-item';
                li.setAttribute('role', 'option');
                li.dataset.value = v;
                li.textContent = v;
                if (v === current) {
                    li.classList.add('is-selected');
                    li.setAttribute('aria-selected', 'true');
                }
                menu.appendChild(li);
                return li;
            });
            active = -1;
            menu.classList.add('show');
            input.setAttribute('aria-expanded', 'true');
            place();
            var sel = menu.querySelector('.is-selected');
            if (sel) sel.scrollIntoView({ block: 'nearest' });
        }

        function close() {
            if (!isOpen()) return;
            menu.classList.remove('show');
            input.setAttribute('aria-expanded', 'false');
            input.removeAttribute('aria-activedescendant');
            active = -1;
        }

        function choose(li) {
            input.value = li.dataset.value;
            close();
            // สคริปต์ของหน้าฟังทั้งสองแบบ (เช่นช่องตำแหน่งที่ขอฟัง change บน document)
            input.dispatchEvent(new Event('input', { bubbles: true }));
            input.dispatchEvent(new Event('change', { bubbles: true }));
        }

        input.addEventListener('mousedown', function () {
            if (isOpen()) close(); else open(null);
        });
        input.addEventListener('input', function (e) {
            // เหตุการณ์ที่ choose() ส่งเองไม่ใช่การพิมพ์ — ไม่ต้องเปิดรายการกลับขึ้นมา
            if (e.isTrusted) open(input.value);
        });
        input.addEventListener('blur', close);
        input.addEventListener('keydown', function (e) {
            if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                e.preventDefault();
                if (!isOpen()) { open(null); return; }
                var n = items.length;
                setActive(e.key === 'ArrowDown' ? (active + 1) % n : (active <= 0 ? n - 1 : active - 1));
            } else if (e.key === 'Enter' && isOpen() && active >= 0) {
                e.preventDefault(); // อย่าให้ Enter ที่ใช้เลือกตัวเลือกส่งฟอร์มไปด้วย
                choose(items[active]);
            } else if (e.key === 'Escape' && isOpen()) {
                e.preventDefault();
                close();
            }
        });

        // กดค้างบนรายการไม่ให้ช่องเสียโฟกัส มิฉะนั้น blur ปิดรายการก่อนที่ click จะถึง
        menu.addEventListener('mousedown', function (e) { e.preventDefault(); });
        menu.addEventListener('click', function (e) {
            var li = e.target.closest('.searchable-select-item');
            if (li) choose(li);
        });

        window.addEventListener('scroll', function () { if (isOpen()) place(); }, true);
        window.addEventListener('resize', function () { if (isOpen()) place(); });
    }

    return { attach: attach, optionsOf: optionsOf };
})();
