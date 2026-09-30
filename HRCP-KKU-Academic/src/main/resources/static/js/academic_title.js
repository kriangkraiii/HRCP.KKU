/**
 * คำนำหน้าในเอกสารขอตำแหน่ง (เฟส 2) ใช้กติกาเดียวกับเอกสารประเมินการสอน (เฟส 1):
 * เลือกได้แค่ตำแหน่งวิชาการ ไม่มี ดร. ต่อท้าย
 *
 * คำนำหน้าที่เก็บไว้ (ทะเบียนบุคลากร ข้อมูลที่บันทึกไว้ก่อนหน้า) มักเป็น "ผศ.ดร." "ผศ. ดร."
 * หรือชื่อเต็ม — แปลงให้ตรงกับตัวเลือกในฟอร์มก่อนใส่ ไม่งั้นช่องจะค้างที่ "-- เลือก --"
 *
 * ช่องที่ใช้กติกานี้ติด data-academic-title ไว้ ช่องอื่นผ่านไปตามเดิม — select_to_datalist.js
 * เปลี่ยน <select> เป็น <input> + <datalist> และยกเครื่องหมายนี้ตามไปด้วย จึงต้องรับทั้งสองแบบ
 */
(function () {
    'use strict';

    var RANKS = {
        'อ.': 'อาจารย์',
        'อาจารย์': 'อาจารย์',
        'ผศ.': 'ผู้ช่วยศาสตราจารย์',
        'ผู้ช่วยศาสตราจารย์': 'ผู้ช่วยศาสตราจารย์',
        'รศ.': 'รองศาสตราจารย์',
        'รองศาสตราจารย์': 'รองศาสตราจารย์',
        'ศ.': 'ศาสตราจารย์',
        'ศาสตราจารย์': 'ศาสตราจารย์',
        'นาย': 'นาย',
        'นาง': 'นาง',
        'นางสาว': 'นางสาว'
    };

    /** "ผศ.ดร." → "ผู้ช่วยศาสตราจารย์", "ดร." → "อาจารย์" ค่าที่ไม่รู้จักคืนค่าเดิม */
    function normalize(value) {
        var raw = String(value == null ? '' : value).trim();
        var squashed = raw.replace(/\s+/g, '');
        var rank = squashed.split('ดร.').join('');
        if (rank === '' && squashed !== '') {
            return 'อาจารย์';
        }
        return RANKS[rank] || raw;
    }

    function isTitleField(el) {
        return !!el && typeof el.hasAttribute === 'function' && el.hasAttribute('data-academic-title');
    }

    /** ค่าที่จะใส่ลงช่อง — แปลงเฉพาะช่องคำนำหน้า */
    function valueFor(el, value) {
        return isTitleField(el) ? normalize(value) : value;
    }

    window.AcademicTitle = { normalize: normalize, isTitleField: isTitleField, valueFor: valueFor };
})();
