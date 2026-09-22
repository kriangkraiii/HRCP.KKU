/**
 * ปรับฟอร์มลงนามเมื่อผู้ลงนามเลือก "ไม่เห็นควร"
 *
 * เลือกไม่เห็นควรแล้วฟอร์มนี้ไม่ใช่การลงนามอีกต่อไป แต่เป็นการส่งคำวินิจฉัยว่าเรื่องไม่ควรเดินต่อ
 * จึงต้องปลด required ของลายเซ็นกับความยินยอมออก ไม่งั้นเบราว์เซอร์ไม่ยอมส่งฟอร์มเลย และกลายเป็น
 * บังคับให้คนเซ็นเอกสารที่ตัวเองไม่เห็นด้วย
 *
 * ตัวที่ตัดสินจริงคือ SigningController ซึ่งแยกทางจากค่า signerChoice ที่ส่งมา ปิด JS แล้วยิง POST
 * ตรงก็ได้ผลเหมือนกัน ไฟล์นี้เป็นแค่ความสะดวกฝั่งหน้าจอ
 *
 * อยู่แยกไฟล์ ไม่ใช่ inline ใน sign.html เพราะข้อความปุ่มที่อยู่ในสคริปต์จะติดไปกับ HTML ทุกครั้ง
 * แม้หน้านั้นจะไม่ได้แสดงปุ่มเลย ซึ่งทำให้เทสต์ที่ตรวจว่า "ห้ามมีปุ่มลงนามเมื่อใบรับรองหมดอายุ"
 * เจอข้อความนั้นแล้วรายงานผิด
 */
(function () {
  'use strict';

  function init() {
    var form = document.getElementById('signForm');
    if (!form) {
      return;
    }

    var comment = document.getElementById('signerComment');
    var mark = document.getElementById('commentRequiredMark');
    var warning = document.getElementById('notApprovedWarning');
    var help = document.getElementById('commentHelp');
    var submit = document.getElementById('signSubmitBtn');
    var signLabel = submit ? submit.innerHTML : '';

    function setRequired(name, required) {
      var fields = form.querySelectorAll('[name="' + name + '"]');
      for (var i = 0; i < fields.length; i++) {
        fields[i].required = required;
      }
    }

    function apply(stopping) {
      setRequired('userSignatureId', !stopping);
      setRequired('consent', !stopping);
      if (comment) {
        comment.required = stopping;
      }
      if (mark) {
        mark.hidden = !stopping;
      }
      if (warning) {
        warning.hidden = !stopping;
      }
      if (help) {
        help.hidden = stopping;
      }
      if (submit) {
        submit.classList.toggle('btn-academic', !stopping);
        submit.classList.toggle('btn-danger', stopping);
        submit.innerHTML = stopping
          ? '<i class="fas fa-circle-xmark"></i> ' + (submit.getAttribute('data-stop-label') || '')
          : signLabel;
      }
      form.setAttribute('data-confirm',
        stopping && submit ? (submit.getAttribute('data-stop-confirm') || '') : '');
    }

    form.addEventListener('change', function (event) {
      var target = event.target;
      if (target && target.classList && target.classList.contains('js-signer-choice')) {
        apply(target.getAttribute('data-stops') === 'true');
      }
    });

    var checked = form.querySelector('.js-signer-choice:checked');
    apply(!!checked && checked.getAttribute('data-stops') === 'true');
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
