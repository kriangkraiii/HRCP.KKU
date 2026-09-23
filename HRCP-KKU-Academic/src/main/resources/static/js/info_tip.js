/**
 * ปุ่ม (i) — เปิดคำอธิบายเพิ่มเติมเป็น popover ของ Bootstrap
 *
 * markup มาจาก templates/academic/_info_tip.html สร้าง popover ตอนผู้ใช้แตะครั้งแรก ไม่ใช่ตอนโหลดหน้า
 * วิธีนี้ครอบคลุม element ที่ JavaScript สร้างทีหลังด้วย และหน้าที่มีปุ่มเยอะก็ไม่ต้องสร้างล่วงหน้าทั้งหมด
 *
 * trigger เป็น focus: คลิกเปิด คลิกที่อื่นปิด Tab เข้ามาแล้วเปิด ส่วน Esc ต้องจัดการเอง
 * เพราะ Bootstrap ไม่ปิด popover ให้
 *
 * อยู่ในไฟล์แยก ไม่ใช่ inline เพราะ CSP ของระบบไม่อนุญาต inline script ที่ไม่มี nonce
 */
(function () {
  'use strict';

  var SELECTOR = '.info-tip[data-bs-toggle="popover"]';

  function popoverFor(el) {
    if (!window.bootstrap || !window.bootstrap.Popover) {
      return null;
    }
    return window.bootstrap.Popover.getOrCreateInstance(el);
  }

  // focus มาก่อน click เสมอ สร้าง instance ตอน focus แล้วสั่งเปิด เพราะ instance ที่เพิ่งสร้าง
  // ยังไม่ได้เห็น focus event ครั้งนี้
  document.addEventListener('focusin', function (event) {
    var el = event.target && event.target.closest ? event.target.closest(SELECTOR) : null;
    if (!el || el.hasAttribute('data-info-tip-ready')) {
      return;
    }
    el.setAttribute('data-info-tip-ready', '');
    var popover = popoverFor(el);
    if (popover) {
      popover.show();
    }
  });

  document.addEventListener('keydown', function (event) {
    var el = event.target && event.target.closest ? event.target.closest(SELECTOR) : null;
    if (!el) {
      return;
    }
    if (event.key === 'Escape') {
      var popover = popoverFor(el);
      if (popover) {
        popover.hide();
      }
    } else if (event.key === 'Enter' || event.key === ' ') {
      // span ไม่มีพฤติกรรมปุ่มในตัว ให้ Enter/Space สลับเปิดปิดเหมือนปุ่มจริง
      event.preventDefault();
      var tip = popoverFor(el);
      if (tip) {
        tip.toggle();
      }
    }
  });
})();
