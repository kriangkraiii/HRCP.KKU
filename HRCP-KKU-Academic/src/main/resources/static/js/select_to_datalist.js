/**
 * Auto-converts <select> elements inside doc forms to <input> + <datalist>
 * so users can BOTH select from options AND type custom values.
 *
 * Usage: include this script at the bottom of any doc_form_X.html
 *        <script src="/js/select_to_datalist.js"></script>
 */
document.addEventListener('DOMContentLoaded', function () {
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
        if (selectedValue) input.value = selectedValue;
        if (sel.id) input.id = sel.id;

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
