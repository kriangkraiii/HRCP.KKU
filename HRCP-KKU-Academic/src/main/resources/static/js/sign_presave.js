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
    "บันทึกเอกสารไม่สำเร็จ จึงยังไม่ได้ส่งไปลงนาม กรุณาตรวจสอบการเชื่อมต่อแล้วลองใหม่";

  function submitButton(form) {
    return form.querySelector('button[type="submit"], input[type="submit"]');
  }

  function showFailure(form, msg) {
    var box = form.querySelector(".presave-error");
    if (!box) {
      box = document.createElement("div");
      box.className =
        "presave-error alert alert-danger border-0 small mt-3 mb-0";
      box.setAttribute("role", "alert");
      var btn = submitButton(form);
      if (btn && btn.parentNode) {
        btn.parentNode.insertBefore(box, btn);
      } else {
        form.appendChild(box);
      }
    }
    box.innerHTML =
      '<i class="fas fa-triangle-exclamation me-1"></i>' + (msg || FAIL_MSG);
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
        // ด่านเฉพาะของฟอร์ม (เช่น เอกสารที่ 2 ต้องติ๊กครบ 5 ข้อ) ซึ่งเป็นเงื่อนไขที่ไม่ได้อยู่บน
        // [required] จึงหลุดจาก DocRequiredFields — ฟอร์มตั้งแอตทริบิวต์นี้ไว้พร้อมเหตุผล
        var blocked = docForm.getAttribute("data-sign-blocked");
        if (blocked) {
          showFailure(form, blocked);
          return;
        }
        // เอกสารที่กรอกไม่ครบ เซิร์ฟเวอร์ก็ปฏิเสธอยู่แล้ว แต่ตอบได้แค่จำนวนช่อง
        // ดักตรงนี้เพื่อบอกว่าเป็นช่องไหนและพาไปที่ช่องนั้น
        if (window.DocRequiredFields && !window.DocRequiredFields.check(form)) {
          return;
        }
        form._preSaveDone = true;
        // เลื่อนออกไปหลัง event ปัจจุบันจบ — ถ้าแบบร่างบันทึกไว้แล้ว flushAll() resolve ทันที
        // แล้ว .then นี้วิ่งใน microtask ระหว่างที่เบราว์เซอร์ยังส่ง submit รอบแรกอยู่
        // requestSubmit() ช่วงนั้นถูกเบราว์เซอร์ทิ้งเงียบ ๆ (firing submission events)
        // ผลคือกดส่งลงนามแล้วไม่มีอะไรเกิดขึ้น เมื่อผู้ใช้รอให้ขึ้น "บันทึกแล้ว" ก่อนกด
        setTimeout(function () {
          if (typeof form.requestSubmit === "function") {
            form.requestSubmit();
          } else {
            form.submit();
          }
        }, 0);
      });
    },
    true
  );

  /*
   * จัดการการกดปุ่ม "บันทึกฉบับร่าง" ที่อยู่ในแผงลงนาม
   * เพื่อส่งคำสั่งบันทึกไปยังฟอร์มเอกสารหลัก (Parent Doc Form)
   */
  document.addEventListener("click", function (e) {
    var btn = e.target.closest(
      '#panelSaveDraftBtn, [data-action="save-doc-draft"]'
    );
    if (!btn) return;

    var docForm = document.querySelector(
      'form[id^="doc"], form[data-auto-draft], form[action*="/document"], form[action*="/doc/"]'
    );
    if (!docForm) return;

    // ถ้าปุ่มบันทึกเดิมในฟอร์มเอกสารยังมีอยู่และมี Event Listener เฉพาะ (เช่น btnSubmitDoc ใน admin) ให้ trigger ปุ่มนั้น
    var origBtn = docForm.querySelector(
      '#btnSubmitDoc, #doc1SubmitBtn, #doc2SubmitBtn'
    );
    if (origBtn && origBtn !== btn) {
      origBtn.click();
      return;
    }

    // ตรวจสอบความถูกต้องของแบบฟอร์มเบื้องต้น (HTML5 validation)
    if (typeof docForm.reportValidity === "function" && !docForm.reportValidity()) {
      return;
    }

    // กำหนด action=submit ในฟอร์มเอกสาร
    var actionInput = docForm.querySelector('input[name="action"]');
    if (!actionInput) {
      actionInput = document.createElement("input");
      actionInput.type = "hidden";
      actionInput.name = "action";
      docForm.appendChild(actionInput);
    }
    actionInput.value = "submit";

    var originalHtml = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<i class="fas fa-sync-alt fa-spin me-1"></i> กำลังบันทึก...';

    function resetBtn() {
      btn.disabled = false;
      btn.innerHTML = originalHtml;
    }

    docForm.addEventListener("submit", function onDocSubmit(evt) {
      if (evt.defaultPrevented) {
        resetBtn();
      }
    }, { once: true });

    // Flush AutoDraft เพื่อความสดใหม่ของข้อมูลก่อนส่ง
    if (window.AutoDraft && typeof window.AutoDraft.flushAll === "function") {
      window.AutoDraft.flushAll().then(function () {
        if (typeof docForm.requestSubmit === "function") {
          docForm.requestSubmit();
        } else {
          docForm.submit();
        }
      }).catch(function () {
        if (typeof docForm.requestSubmit === "function") {
          docForm.requestSubmit();
        } else {
          docForm.submit();
        }
      });
    } else {
      if (typeof docForm.requestSubmit === "function") {
        docForm.requestSubmit();
      } else {
        docForm.submit();
      }
    }
  });
})();

