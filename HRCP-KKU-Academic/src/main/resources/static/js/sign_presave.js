/**
 * Pre-save before signing — บันทึกเอกสารให้อัตโนมัติก่อนส่งไปลงนาม
 *
 * แผงลงนามเป็น <form> คนละใบกับฟอร์มเอกสาร และส่งไปแค่ requestId/documentType
 * เซิร์ฟเวอร์จึง freeze "แถวล่าสุดใน DB" ไม่ใช่สิ่งที่อยู่บนหน้าจอ — ถ้าผู้ใช้แก้แล้ว
 * กดส่งเลย ข้อมูลที่แก้จะหายเงียบ ๆ ไฟล์นี้ปิดช่องนั้นด้วยการ flush แบบร่างให้เสร็จก่อน
 * แล้วค่อยปล่อยให้ฟอร์มส่งตามปกติ
 *
 * Usage: <form data-presave-doc ...> ในแผงลงนาม + โหลดไฟล์นี้จาก base template
 */
(function () {
  "use strict";

  var SAVING_LABEL = "กำลังบันทึกเอกสาร...";
  var FAIL_MSG =
    "บันทึกเอกสารไม่สำเร็จ จึงยังไม่ได้ส่งไปลงนาม — กรุณาตรวจการเชื่อมต่อแล้วลองใหม่";

  function submitButton(form) {
    return form.querySelector('button[type="submit"], input[type="submit"]');
  }

  function showFailure(form) {
    var box = form.querySelector(".presave-error");
    if (!box) {
      box = document.createElement("div");
      box.className =
        "presave-error alert alert-danger border-0 small mt-3 mb-0";
      box.setAttribute("role", "alert");
      box.innerHTML = '<i class="fas fa-triangle-exclamation me-1"></i>' + FAIL_MSG;
      var btn = submitButton(form);
      if (btn && btn.parentNode) {
        btn.parentNode.insertBefore(box, btn);
      } else {
        form.appendChild(box);
      }
    }
    box.hidden = false;
  }

  function clearFailure(form) {
    var box = form.querySelector(".presave-error");
    if (box) box.hidden = true;
  }

  /*
   * ต้องเป็น capture phase เพื่อให้ยิงก่อน handler ของ data-confirm ใน csp_fallbacks.js
   * ซึ่งเรียก stopImmediatePropagation() ตอนเปิด modal ยืนยัน
   */
  document.addEventListener(
    "submit",
    function (e) {
      var form = e.target;
      if (!form || !form.matches || !form.matches("form[data-presave-doc]")) return;

      /* ตั้งแล้วไม่ล้าง — confirm modal จะ requestSubmit() ซ้ำ ถ้า reset จะวนลูป */
      if (form._preSaveDone) return;

      var docForm = document.querySelector("form[data-auto-draft]");
      if (!docForm || !docForm.__autoDraft || !window.AutoDraft) {
        /* เอกสารล็อกหรือแก้ไม่ได้ ไม่มีอะไรต้องบันทึก — ส่งตามปกติ */
        return;
      }

      e.preventDefault();
      e.stopImmediatePropagation();
      clearFailure(form);

      var btn = submitButton(form);
      var originalHtml = btn ? btn.innerHTML : null;
      if (btn) {
        btn.disabled = true;
        btn.innerHTML =
          '<i class="fas fa-sync-alt fa-spin me-1"></i>' + SAVING_LABEL;
      }

      window.AutoDraft.flushAll().then(function (ok) {
        if (btn) {
          btn.disabled = false;
          btn.innerHTML = originalHtml;
        }
        if (!ok) {
          showFailure(form);
          return;
        }
        form._preSaveDone = true;
        if (typeof form.requestSubmit === "function") {
          form.requestSubmit();
        } else {
          form.submit();
        }
      });
    },
    true
  );
})();
