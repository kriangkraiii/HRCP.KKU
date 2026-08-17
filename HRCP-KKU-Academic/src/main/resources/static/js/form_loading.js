/**
 * Global Form & Action Loading State Handler
 * - Prevents double submissions
 * - Provides real-time visual feedback with spinners on submit buttons
 * - Handles download buttons/links with automatic restore
 */
(function () {
    'use strict';

    /**
     * Helper to set loading state on a button/element
     */
    window.setButtonLoading = function (btn, customText) {
        if (!btn || btn.dataset.btnLoading === 'true') return;

        // Save original attributes and dimensions
        btn.dataset.btnLoading = 'true';
        btn.dataset.originalHtml = btn.innerHTML;
        btn.dataset.originalWidth = btn.offsetWidth + 'px';

        // Fix width so button doesn't shrink or expand
        if (btn.offsetWidth > 0) {
            btn.style.minWidth = btn.offsetWidth + 'px';
        }

        // Determine appropriate loading text if not provided
        var text = customText;
        if (!text) {
            var rawText = (btn.innerText || btn.textContent || '').trim();
            if (rawText.includes('บันทึก') || rawText.includes('สร้าง')) {
                text = 'กำลังบันทึก...';
            } else if (rawText.includes('อัปโหลด')) {
                text = 'กำลังอัปโหลด...';
            } else if (rawText.includes('ส่ง') || rawText.includes('ยื่น')) {
                text = 'กำลังส่งข้อมูล...';
            } else if (rawText.includes('ลบ')) {
                text = 'กำลังลบข้อมูล...';
            } else if (rawText.includes('อนุมัติ') || rawText.includes('ยืนยัน')) {
                text = 'กำลังดำเนินการ...';
            } else if (rawText.includes('ค้นหา')) {
                text = 'กำลังค้นหา...';
            } else if (rawText.includes('ดาวน์โหลด')) {
                text = 'กำลังเตรียมไฟล์...';
            } else {
                text = 'กำลังดำเนินการ...';
            }
        }

        // Apply loading spinner & text
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span> ' + text;
        btn.classList.add('disabled');
        btn.style.pointerEvents = 'none';

        // Disabling the button via disabled property after current event tick
        setTimeout(function () {
            btn.disabled = true;
        }, 10);

        // Auto-restore safety timeout in case page does not navigate (e.g. validation error or file download)
        var timeoutDuration = 15000;
        if (btn.dataset.loadingTimeout) {
            timeoutDuration = parseInt(btn.dataset.loadingTimeout, 10);
        }
        setTimeout(function () {
            window.restoreButtonLoading(btn);
        }, timeoutDuration);
    };

    /**
     * Helper to restore button to original state
     */
    window.restoreButtonLoading = function (btn) {
        if (!btn || btn.dataset.btnLoading !== 'true') return;
        if (btn.dataset.originalHtml) {
            btn.innerHTML = btn.dataset.originalHtml;
        }
        btn.disabled = false;
        btn.classList.remove('disabled');
        btn.style.pointerEvents = '';
        btn.style.minWidth = '';
        delete btn.dataset.btnLoading;
        delete btn.dataset.originalHtml;
        delete btn.dataset.originalWidth;
    };

    // Track the last clicked submit button
    var lastClickedSubmitBtn = null;
    document.addEventListener('click', function (e) {
        var btn = e.target.closest('button[type="submit"], input[type="submit"], button.btn-academic, button.btn-save-status');
        if (btn) {
            lastClickedSubmitBtn = btn;
        }
    }, true);

    // Global Form Submit Handler
    document.addEventListener('submit', function (e) {
        var form = e.target;
        if (!form || form.tagName !== 'FORM') return;

        // Skip if form is marked to ignore loading state
        if (form.hasAttribute('data-no-loading')) return;

        // Skip if target opens in new tab/window
        if (form.getAttribute('target') === '_blank') return;

        // Find the active submit button
        var submitBtn = lastClickedSubmitBtn;
        if (!submitBtn || !form.contains(submitBtn)) {
            submitBtn = form.querySelector('button[type="submit"]:not([data-no-loading]), input[type="submit"]:not([data-no-loading]), button.btn-academic:not([data-no-loading])');
        }

        if (submitBtn && !submitBtn.hasAttribute('data-no-loading')) {
            window.setButtonLoading(submitBtn);
        }
    });

    // Global Download / Export Button Handler
    document.addEventListener('click', function (e) {
        var link = e.target.closest('a[href*="/export"], a[href*="/download"], a.btn-download, button.btn-download');
        if (!link) return;

        // Skip if opened in new tab
        if (link.getAttribute('target') === '_blank') return;

        // Temporarily show loading on download button (restore after 4 seconds since browser download doesn't reload page)
        link.dataset.loadingTimeout = '4000';
        window.setButtonLoading(link, 'กำลังเตรียมไฟล์...');
    });

})();
