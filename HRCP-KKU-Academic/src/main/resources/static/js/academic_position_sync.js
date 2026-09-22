/**
 * ให้ช่อง "ตำแหน่งวิชาการ (อังกฤษ)" ขยับตามช่องภาษาไทยที่เลือก
 *
 * คู่ไทย-อังกฤษไม่ได้อยู่ในไฟล์นี้ — มันมาจาก AcademicRank ฝั่งเซิร์ฟเวอร์ แล้วถูกฝังเป็น
 * data-en บนแต่ละ <option> ของ select ภาษาไทย (ดู admin/_academic_position_fields.html)
 * ถ้าเก็บตารางแปลไว้ที่นี่ด้วย วันหนึ่งสองฝั่งจะไม่ตรงกันโดยไม่มีใครรู้
 *
 * ทำงานเฉพาะตอนผู้ใช้เปลี่ยนช่องภาษาไทยเท่านั้น จงใจไม่แตะอะไรตอนโหลดหน้า เพราะค่า EN
 * ที่เก็บไว้อาจเป็นค่าที่แอดมินตั้งใจเลือกทับไว้เอง การ "แก้ให้ถูก" ตอนโหลดคือการลบของเขาทิ้ง
 *
 * นี่เป็นแค่ความสะดวกฝั่งหน้าจอ ปิด JS แล้วสองช่องก็ยังเลือกเองได้ครบ
 */
(function () {
  'use strict';

  function sync(thaiSelect) {
    var form = thaiSelect.form || document;
    var englishSelect = form.querySelector('.js-academic-position-en');
    if (!englishSelect) {
      return;
    }

    var chosen = thaiSelect.options[thaiSelect.selectedIndex];
    // "-- ไม่ระบุ --" และ option ค่าเดิมที่ไม่ใช่ตำแหน่งมาตรฐาน ไม่มี data-en:
    // ปล่อยฝั่งอังกฤษไว้อย่างนั้น ดีกว่าล้างทิ้งโดยที่ไม่มีอะไรมาแทน
    var english = chosen && chosen.getAttribute('data-en');
    if (!english) {
      return;
    }

    for (var i = 0; i < englishSelect.options.length; i++) {
      if (englishSelect.options[i].value === english) {
        englishSelect.selectedIndex = i;
        return;
      }
    }
  }

  document.addEventListener('change', function (event) {
    var target = event.target;
    if (target && target.classList && target.classList.contains('js-academic-position-th')) {
      sync(target);
    }
  });
})();
