/**
 * พาผู้ใช้จาก "บันทึกแล้ว" ไปยังแผงลงนาม
 *
 * หน้าฟอร์มเอกสารยาวมาก แผงลงนามอยู่ท้ายสุดใต้ฟอร์ม ผู้ใช้ที่เพิ่งกดบันทึกจึงไม่เห็น
 * และไม่รู้ว่ายังต้องเซ็นต่อ สคริปต์นี้ทำสองอย่าง: ปุ่มในกล่องชี้ทางเลื่อนไปที่แผง
 * และล้าง ?saved=1 ออกจาก URL เพื่อไม่ให้กล่องค้างอยู่เมื่อผู้ใช้กดรีเฟรช
 */
(function () {
    'use strict';

    var PANEL_ID = 'signaturePanel';

    function scrollToPanel() {
        var panel = document.getElementById(PANEL_ID);
        if (!panel) return;
        panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    document.addEventListener('click', function (e) {
        var trigger = e.target.closest('[data-goto-signature-panel]');
        if (!trigger) return;
        e.preventDefault();
        scrollToPanel();
    });

    document.addEventListener('DOMContentLoaded', function () {
        var params = new URLSearchParams(window.location.search);
        if (!params.has('saved')) return;

        params.delete('saved');
        var query = params.toString();
        var newUrl = window.location.pathname + (query ? '?' + query : '') + window.location.hash;
        try {
            window.history.replaceState({}, document.title, newUrl);
        } catch (err) {
            /* ประวัติการเข้าชมแก้ไม่ได้ในบางบริบท — ไม่ใช่เรื่องคอขาดบาดตาย */
        }
    });
})();
