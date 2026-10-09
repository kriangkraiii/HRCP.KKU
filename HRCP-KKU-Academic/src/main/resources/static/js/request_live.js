/**
 * อัปเดตสดของหน้าคำร้องเจ้าหน้าที่ (ทั้งสองเฟส)
 *
 * เซิร์ฟเวอร์ส่ง "changed" ผ่าน Server-Sent Events เมื่อคำร้องเปลี่ยน (RequestLiveUpdates) — หน้าดึงตัวเองใหม่
 * แล้วแทนที่เฉพาะช่องที่มี data-live-region (กล่องขั้นต่อไป แถบขั้นตอน รายการเอกสาร) ข้อมูลจึงมาจาก
 * ที่เดียวกับตอนโหลดหน้า ไม่มีทางไม่ตรงกัน
 *
 * ช่องอัปเดตสถานะไม่ถูกแทนที่ — เจ้าหน้าที่อาจกำลังพิมพ์หมายเหตุอยู่ สถานะเปลี่ยนแล้วจึงขึ้นแถบให้รีเฟรชเอง
 */
(function () {
    'use strict';

    var root = document.getElementById('liveRoot');
    if (!root || !window.EventSource || !window.DOMParser) return;

    var url = root.getAttribute('data-live-url');
    var timer = null;
    var pendingWhileHidden = false;
    var busy = false;
    var again = false;
    var lostConnection = false;

    function schedule() {
        if (document.hidden) {
            pendingWhileHidden = true;
            return;
        }
        clearTimeout(timer);
        // หลายเหตุการณ์ติดกัน (ลงนามสองช่องในครั้งเดียว) ดึงครั้งเดียว
        timer = setTimeout(refresh, 600);
    }

    function currentKey() {
        var li = document.querySelector('#nextStep li.ns-current');
        return li ? li.getAttribute('data-step-key') : null;
    }

    function currentTitle() {
        var t = document.querySelector('#nextStep .detail-section-header span');
        return t ? t.textContent.trim() : null;
    }

    /** รายการงานวิจัยที่กางไว้ (เอกสารที่ 9) ต้องยังกางอยู่หลังแทนที่ */
    function workCopiesOpen() {
        return !!document.querySelector('.work-copy-item.show');
    }

    function reopenWorkCopies() {
        document.querySelectorAll('.work-copy-item').forEach(function (el) { el.classList.add('show'); });
        document.querySelectorAll('.work-copy-toggle').forEach(function (b) { b.setAttribute('aria-expanded', 'true'); });
    }

    function refresh() {
        if (busy) { again = true; return; }
        busy = true;
        var beforeKey = currentKey();
        var beforeTitle = currentTitle();
        var keepOpen = workCopiesOpen();

        fetch(window.location.pathname + window.location.search, {
            credentials: 'same-origin', cache: 'no-store', headers: { 'X-Live-Refresh': '1' }
        })
            .then(function (res) { return res.ok ? res.text() : Promise.reject(res.status); })
            .then(function (html) {
                var doc = new DOMParser().parseFromString(html, 'text/html');
                var fresh = doc.getElementById('liveRoot');
                if (!fresh) return; // ถูกพาไปหน้าเข้าสู่ระบบ ฯลฯ — ไม่แตะหน้าเดิม

                document.querySelectorAll('[data-live-region][id]').forEach(function (region) {
                    var replacement = doc.getElementById(region.id);
                    if (replacement) region.replaceWith(document.importNode(replacement, true));
                });
                if (keepOpen) reopenWorkCopies();

                var afterKey = currentKey();
                if (afterKey && afterKey !== beforeKey) {
                    var li = document.querySelector('#nextStep li.ns-current');
                    if (li) li.classList.add('ns-flash');
                }
                var afterTitle = currentTitle();
                var statusChanged = fresh.getAttribute('data-status') !== root.getAttribute('data-status');
                if (statusChanged) {
                    showStatusBar(fresh.getAttribute('data-status-label'));
                } else if (afterTitle && afterTitle !== beforeTitle) {
                    toast('ขั้นต่อไป: ' + afterTitle);
                } else if (afterKey !== beforeKey) {
                    toast('อัปเดตความคืบหน้าแล้ว');
                }
            })
            .catch(function () { /* ครั้งหน้าที่มีเหตุการณ์ค่อยลองใหม่ */ })
            .then(function () {
                busy = false;
                if (again) { again = false; schedule(); }
            });
    }

    function showStatusBar(label) {
        var bar = document.getElementById('liveStatusBar');
        if (!bar) {
            bar = document.createElement('div');
            bar.id = 'liveStatusBar';
            bar.className = 'alert alert-info d-flex align-items-center gap-2 shadow position-fixed bottom-0 start-50 translate-middle-x mb-3';
            bar.style.zIndex = '1080';
            bar.setAttribute('role', 'status');
            var text = document.createElement('span');
            text.className = 'live-status-text';
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'btn btn-sm btn-primary';
            btn.textContent = 'รีเฟรช';
            btn.addEventListener('click', function () { window.location.reload(); });
            var icon = document.createElement('i');
            icon.className = 'fas fa-arrows-rotate';
            bar.append(icon, text, btn);
            document.body.appendChild(bar);
        }
        bar.querySelector('.live-status-text').textContent =
            'สถานะคำร้องเปลี่ยนเป็น “' + label + '” — รีเฟรชเพื่ออัปเดตช่องอัปเดตสถานะ';
    }

    function toast(message) {
        var el = document.createElement('div');
        el.className = 'toast align-items-center text-bg-primary border-0 position-fixed bottom-0 end-0 m-3 show';
        el.style.zIndex = '1080';
        el.setAttribute('role', 'status');
        var body = document.createElement('div');
        body.className = 'toast-body';
        body.textContent = message;
        el.appendChild(body);
        document.body.appendChild(el);
        setTimeout(function () { el.remove(); }, 4000);
    }

    var source = new EventSource(url);
    source.addEventListener('changed', schedule);
    // ต่อใหม่หลังหลุด (เครือข่าย รีสตาร์ทเซิร์ฟเวอร์) — ระหว่างหลุดอาจพลาดเหตุการณ์ไป ดึงใหม่หนึ่งครั้ง
    source.addEventListener('error', function () { lostConnection = true; });
    source.addEventListener('open', function () {
        if (lostConnection) { lostConnection = false; schedule(); }
    });
    document.addEventListener('visibilitychange', function () {
        if (!document.hidden && pendingWhileHidden) {
            pendingWhileHidden = false;
            schedule();
        }
    });
    window.addEventListener('pagehide', function () { source.close(); });
    // กลับมาจากปุ่มย้อนกลับ (bfcache) — การเชื่อมต่อปิดไปแล้ว และหน้าอาจเก่า
    window.addEventListener('pageshow', function (e) { if (e.persisted) window.location.reload(); });
})();
