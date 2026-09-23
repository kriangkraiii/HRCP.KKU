/**
 * Auto-converts <select> elements inside doc forms to <input> + <datalist>
 * so users can BOTH select from options AND type custom values.
 *
 * Usage: include this script at the bottom of any doc_form_X.html
 *        <script src="/js/select_to_datalist.js"></script>
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
    document.querySelectorAll('form[id^="doc"] select.form-select').forEach(function (sel) {
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
        if (!selectedValue && typeof saved[name] === 'string' && saved[name].trim()) {
            selectedValue = saved[name].trim();
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
});
