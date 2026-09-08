/**
 * Auto-dismiss for Success Alerts on Guest & Login pages
 * Configured timeout: 6 seconds (6000ms) with smooth fade-out and URL query cleanup.
 */
(function () {
    'use strict';

    var DISMISS_DELAY = 6000; // หน่วงเวลา 6 วินาทีตามที่กำหนด

    function initAutoDismiss() {
        // เลือกกล่องแจ้งเตือนความสำเร็จบนหน้า Guest (ไม่รวม alert-danger เพื่อให้ผู้ใช้อ่าน Error ได้เสมอ)
        var alerts = document.querySelectorAll('.alert.alert-success:not([data-no-auto-dismiss])');
        if (!alerts || alerts.length === 0) return;

        alerts.forEach(function (alert) {
            var timeoutId = null;
            var startTime = Date.now();
            var remainingTime = DISMISS_DELAY;

            function dismiss() {
                alert.classList.add('alert-dismissing');
                setTimeout(function () {
                    if (alert.parentNode) {
                        alert.parentNode.removeChild(alert);
                    }
                }, 700);
            }

            function startTimer(delay) {
                timeoutId = setTimeout(dismiss, delay);
                startTime = Date.now();
            }

            // เริ่มนับถอยหลัง 6 วินาที
            startTimer(remainingTime);

            // เมื่อเมาส์ชี้ค้างไว้ ให้หยุดนับเวลาชั่วคราว (Pause on hover) เพื่อให้อ่านข้อความได้สะดวก
            alert.addEventListener('mouseenter', function () {
                if (timeoutId) {
                    clearTimeout(timeoutId);
                    timeoutId = null;
                    remainingTime -= (Date.now() - startTime);
                    if (remainingTime < 1000) remainingTime = 1000;
                }
            });

            // เมื่อเมาส์ออกจากกล่อง ให้นับเวลาต่อจากที่เหลือ
            alert.addEventListener('mouseleave', function () {
                if (!timeoutId && !alert.classList.contains('alert-dismissing')) {
                    startTimer(remainingTime);
                }
            });

            // ให้ผู้ใช้คลิกที่กล่องเพื่อปิดทันทีได้
            alert.style.cursor = 'pointer';
            alert.setAttribute('title', 'คลิกเพื่อปิดข้อความนี้');
            alert.addEventListener('click', function () {
                if (timeoutId) clearTimeout(timeoutId);
                dismiss();
            });
        });

        // เคลียร์ Query String ที่ค้างใน Address Bar (เช่น ?logout, ?success, ?saved)
        // เพื่อป้องกันไม่ให้ข้อความแสดงขึ้นมาอีกเมื่อผู้ใช้กด Refresh หน้าเว็บ
        if (window.history && window.history.replaceState) {
            try {
                var url = new URL(window.location.href);
                var paramsToClean = ['logout', 'success', 'saved', 'succMsg'];
                var cleaned = false;
                paramsToClean.forEach(function (param) {
                    if (url.searchParams.has(param)) {
                        url.searchParams.delete(param);
                        cleaned = true;
                    }
                });
                if (cleaned) {
                    var newSearch = url.searchParams.toString();
                    var newUrl = url.pathname + (newSearch ? '?' + newSearch : '') + url.hash;
                    window.history.replaceState({}, document.title, newUrl);
                }
            } catch (e) {
                // Ignore URL parsing errors on older environments
            }
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initAutoDismiss);
    } else {
        initAutoDismiss();
    }
})();
