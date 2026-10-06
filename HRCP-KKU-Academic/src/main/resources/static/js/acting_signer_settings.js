/**
 * ผู้รักษาการแทน (หน้าตั้งค่าผู้ลงนาม) — เติมตำแหน่งเต็มของหัวหน้าสาขาวิชาตามสาขาของคนที่เลือก
 *
 * ตำแหน่งคณบดี/รองคณบดีเป็นตำแหน่งของคนที่ไม่อยู่ ไม่ขึ้นกับผู้รักษาการแทน เซิร์ฟเวอร์เติมไว้แล้ว
 * ส่วนหัวหน้าสาขาวิชาคือสาขาของผู้รักษาการแทนเอง จึงต้องตามการเลือก — กติกาเดียวกับ
 * ActingSignerService.headOf ค่าที่แอดมินพิมพ์เองไม่ถูกทับ
 */
(function () {
    'use strict';

    function headOf(department) {
        if (!department || /ห้องปฏิบัติการ|\blab/i.test(department)) return null;
        var programme = department.trim().replace(/^(?:สาขาวิชา|สาขา)\s*/, '').trim();
        return programme ? 'หัวหน้าสาขาวิชา' + programme : null;
    }

    document.querySelectorAll('.acting-signer-form[data-slot="head"]').forEach(function (form) {
        var select = form.querySelector('select[name="actingUserId"]');
        var input = form.querySelector('input[name="positionTitle"]');
        if (!select || !input) return;

        input.addEventListener('input', function () { input.dataset.auto = 'false'; });
        // searchable_select.js ยิง change บน <select> เดิมเมื่อเลือก
        select.addEventListener('change', function () {
            if (input.dataset.auto !== 'true' && input.value.trim()) return;
            var option = select.options[select.selectedIndex];
            var position = headOf(option ? option.getAttribute('data-department') : null);
            if (position) {
                input.value = position;
                input.dataset.auto = 'true';
            }
        });
    });
})();
