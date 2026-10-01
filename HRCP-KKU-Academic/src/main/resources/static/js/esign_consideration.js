/**
 * ปรับฟอร์มลงนามเมื่อผู้ลงนามเลือก "ไม่เห็นควร"
 *
 * เลือกไม่เห็นควรแล้วฟอร์มนี้ไม่ใช่การลงนามอีกต่อไป แต่เป็นการส่งคำวินิจฉัยว่าเรื่องไม่ควรเดินต่อ
 * จึงต้องปลด required ของลายเซ็นออก ไม่งั้นเบราว์เซอร์ไม่ยอมส่งฟอร์มเลย และกลายเป็น
 * บังคับให้คนเซ็นเอกสารที่ตัวเองไม่เห็นด้วย
 *
 * ส่วนการลงนามปกติ: ปุ่ม "ลงนาม" ไม่ส่งฟอร์มทันที แต่เปิดหน้าต่างยืนยัน (#signConfirmModal) ที่ทวน
 * เอกสาร ลายเซ็น และผลพิจารณา ค่า consent=true ติดไปกับปุ่ม "ยอมรับและลงนาม" ในหน้าต่างนั้นเท่านั้น
 * เบราว์เซอร์ตรวจช่อง required ก่อนถึง submit handler อยู่แล้ว หน้าต่างจึงเปิดเมื่อฟอร์มครบเท่านั้น
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
    var confirmModalEl = document.getElementById('signConfirmModal');
    var confirmBtn = document.getElementById('signConfirmBtn');
    var stoppingNow = false;
    // รหัสผ่าน Digital ID / รหัสจากอีเมล ใช้ยืนยันการลงนาม ไม่เห็นควรไม่ได้ลงนามจึงไม่ต้องกรอก
    var credentials = form.querySelectorAll('[name="digitalCertPin"], [name="emailOtp"]');
    var credentialRequired = Array.prototype.map.call(credentials, function (f) { return f.required; });
    var credentialCards = form.querySelectorAll('[data-signing-credential]');

    function setRequired(name, required) {
      var fields = form.querySelectorAll('[name="' + name + '"]');
      for (var i = 0; i < fields.length; i++) {
        fields[i].required = required;
      }
    }

    function apply(stopping) {
      stoppingNow = stopping;
      setRequired('userSignatureId', !stopping);
      for (var i = 0; i < credentials.length; i++) {
        credentials[i].required = !stopping && credentialRequired[i];
      }
      for (var j = 0; j < credentialCards.length; j++) {
        credentialCards[j].hidden = stopping;
      }
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

    function fillSummary() {
      var sig = form.querySelector('input[name="userSignatureId"]:checked');
      var sigOut = document.getElementById('signConfirmSignature');
      if (sigOut) {
        sigOut.textContent = sig ? (sig.getAttribute('data-display-name') || '-') : '-';
      }
      var choice = form.querySelector('.js-signer-choice:checked');
      var choiceOut = document.getElementById('signConfirmChoice');
      var choiceLabel = document.getElementById('signConfirmChoiceLabel');
      if (choiceOut && choiceLabel) {
        choiceOut.textContent = choice ? choice.value : '';
        choiceOut.hidden = !choice;
        choiceLabel.hidden = !choice;
      }
    }

    form.addEventListener('submit', function (event) {
      if (stoppingNow || !confirmModalEl || !window.bootstrap) {
        return;
      }
      if (event.submitter === confirmBtn) {
        // กันกดซ้ำระหว่างรอเซิร์ฟเวอร์ ปิดหลังค่า consent ของปุ่มถูกเก็บเข้าฟอร์มแล้ว
        setTimeout(function () { confirmBtn.disabled = true; }, 0);
        return;
      }
      event.preventDefault();
      // ยังไม่ได้ส่งจริง ไม่ให้ form_loading.js (ฟังที่ document) ขึ้น spinner ค้างบนปุ่ม "ลงนาม"
      event.stopPropagation();
      fillSummary();
      window.bootstrap.Modal.getOrCreateInstance(confirmModalEl).show();
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
