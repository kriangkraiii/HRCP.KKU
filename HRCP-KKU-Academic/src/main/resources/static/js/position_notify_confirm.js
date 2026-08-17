/**
 * Position Request Notification & Auto Status Confirm Modal for Admin
 */
function initPositionNotifyConfirm(formId, docType) {
    const form = document.getElementById(formId);
    if (!form) return;

    let sendNotifyField = form.querySelector('input[name="sendNotify"]');
    if (!sendNotifyField) {
        sendNotifyField = document.createElement('input');
        sendNotifyField.type = 'hidden';
        sendNotifyField.name = 'sendNotify';
        sendNotifyField.value = 'false';
        form.appendChild(sendNotifyField);
    }

    const notifyDocTypes = [0, 5, 7, 8];
    if (!notifyDocTypes.includes(docType)) return;

    var statusInfo = '';
    if (docType === 0 || docType === 5 || docType === 7) {
        statusInfo = 'สถานะคำร้องจะเปลี่ยนเป็น: <strong>ตรวจสอบความถูกต้อง/ครบถ้วน (DOCUMENT_VERIFICATION)</strong>';
    } else if (docType === 8) {
        statusInfo = 'สถานะคำร้องจะเปลี่ยนเป็น: <strong>เสนอวาระกลั่นกรองฯ (SCREENING_COMMITTEE)</strong>';
    }

    let panel = document.getElementById('posNotifyConfirmPanel');
    if (!panel) {
        panel = document.createElement('div');
        panel.id = 'posNotifyConfirmPanel';
        panel.style.cssText = 'display:none;position:fixed;top:0;left:0;right:0;bottom:0;z-index:9999;';
        panel.innerHTML =
            '<div id="posNotifyBackdrop" style="position:absolute;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.4);"></div>' +
            '<div style="position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);background:#fff;border-radius:12px;box-shadow:0 8px 32px rgba(0,0,0,0.2);width:90%;max-width:480px;padding:20px;border:0 none;outline:none;">' +
                '<div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;">' +
                    '<h5 style="margin:0;font-size:1.1rem;border:none;"><i class="fas fa-bell text-warning"></i> อัปเดตสถานะและแจ้งเตือนผู้ยื่นคำร้อง</h5>' +
                    '<button type="button" id="posNotifyCloseX" style="background:none;border:none;font-size:1.3rem;cursor:pointer;padding:4px 8px;line-height:1;">&times;</button>' +
                '</div>' +
                '<p style="margin:0 0 10px;">ต้องการ<strong>อัปเดตสถานะคำร้อง</strong>และ<strong>ส่งอีเมลแจ้งเตือนผู้ยื่น</strong>หรือไม่?</p>' +
                (statusInfo ? '<div style="padding:8px 12px;background:#f0fdf4;border:1px solid #bbf7d0;border-radius:8px;font-size:0.88rem;color:#166534;margin-bottom:12px;"><i class="fas fa-arrow-circle-right me-1"></i> ' + statusInfo + '</div>' : '') +
                '<div style="padding:10px 14px;background:#e3f2fd;border-radius:8px;font-size:0.88rem;margin-bottom:16px;border:none;">' +
                    '<i class="fas fa-info-circle me-1"></i> หากเอกสารยังไม่เรียบร้อย สามารถเลือก <strong>"ไม่แจ้งเตือน"</strong> เพื่อบันทึกไฟล์โดยยังไม่อัปเดตสถานะได้' +
                '</div>' +
                '<div style="display:flex;justify-content:flex-end;gap:8px;">' +
                    '<button type="button" id="btnPosNoNotify" class="btn btn-outline-secondary"><i class="fas fa-bell-slash"></i> ไม่แจ้งเตือน (บันทึกอย่างเดียว)</button>' +
                    '<button type="button" id="btnPosYesNotify" class="btn btn-primary"><i class="fas fa-check-circle"></i> แจ้งเตือนผู้ยื่นและอัปเดตสถานะ</button>' +
                '</div>' +
            '</div>';
        document.body.appendChild(panel);
    }

    function showPanel() { if (panel) panel.style.display = 'block'; }
    function hidePanel() { if (panel) panel.style.display = 'none'; }

    // Intercept form submit button
    const submitBtns = form.querySelectorAll('button[type="submit"]');
    submitBtns.forEach(btn => {
        btn.type = 'button';
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            if (!form.reportValidity()) return;
            showPanel();
        });
    });

    panel.addEventListener('click', function(e) {
        if (e.target.closest('#btnPosNoNotify')) {
            sendNotifyField.value = 'false';
            hidePanel();
            if (window.setButtonLoading) {
                submitBtns.forEach(btn => window.setButtonLoading(btn, 'กำลังบันทึกเอกสาร...'));
            }
            form.submit();
        } else if (e.target.closest('#btnPosYesNotify')) {
            sendNotifyField.value = 'true';
            hidePanel();
            if (window.setButtonLoading) {
                submitBtns.forEach(btn => window.setButtonLoading(btn, 'กำลังบันทึกเอกสาร...'));
            }
            form.submit();
        } else if (e.target.closest('#posNotifyCloseX') || e.target.id === 'posNotifyBackdrop') {
            hidePanel();
        }
    });
}
