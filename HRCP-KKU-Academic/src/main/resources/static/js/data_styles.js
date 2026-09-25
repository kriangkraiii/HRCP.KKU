/*
 * ค่าสไตล์ที่มาจากข้อมูล (สีสถานะ, ความกว้างแถบความคืบหน้า, ดีเลย์แอนิเมชัน)
 *
 * CSP ของระบบ (style-src แบบ nonce ไม่มี 'unsafe-inline') ตัด style="..." ทุกตัวทิ้ง
 * ค่าที่คำนวณจากข้อมูลจึงใส่ผ่าน data-* แล้วสคริปต์นี้กำหนดผ่าน CSSOM (el.style.x = ...)
 * ซึ่ง CSP อนุญาต — รับเฉพาะคุณสมบัติที่ระบุไว้ด้านล่าง ไม่ใช่ข้อความสไตล์อิสระ
 *
 *   data-bg="#1565c0"          → background-color
 *   data-fg="#1565c0"          → color
 *   data-border="#1565c0"      → border-color
 *   data-width="42"            → width: 42%
 *   data-var-stagger="120ms"   → --stagger: 120ms   (ตัวแปร CSS ใด ๆ ขึ้นต้นด้วย data-var-)
 *
 * ค่าสีรับเฉพาะรูปแบบสี (#hex, rgb(), hsl(), var(--x)) กันการฉีดค่าที่ไม่ใช่สี
 */
(function () {
    'use strict';

    var COLOR = /^(#[0-9a-fA-F]{3,8}|(rgb|rgba|hsl|hsla)\([\d\s.,%]+\)|var\(--[\w-]+(,\s*#[0-9a-fA-F]{3,8})?\)|[a-zA-Z]+)$/;
    var SIMPLE = /^[\w\s.,%()#-]+$/;

    function color(value) {
        value = (value || '').trim();
        return COLOR.test(value) ? value : null;
    }

    function apply(el) {
        var bg = color(el.getAttribute('data-bg'));
        if (bg) el.style.backgroundColor = bg;
        var fg = color(el.getAttribute('data-fg'));
        if (fg) el.style.color = fg;
        var border = color(el.getAttribute('data-border'));
        if (border) el.style.borderColor = border;

        var width = parseFloat(el.getAttribute('data-width'));
        if (!isNaN(width)) el.style.width = Math.max(0, Math.min(100, width)) + '%';

        for (var i = 0; i < el.attributes.length; i++) {
            var attr = el.attributes[i];
            if (attr.name.indexOf('data-var-') === 0 && SIMPLE.test(attr.value)) {
                el.style.setProperty('--' + attr.name.slice(9), attr.value.trim());
            }
        }
    }

    function applyAll(root) {
        (root || document)
            .querySelectorAll('[data-bg],[data-fg],[data-border],[data-width],[data-styled]')
            .forEach(apply);
    }

    window.applyDataStyles = applyAll;
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { applyAll(); });
    } else {
        applyAll();
    }
})();
