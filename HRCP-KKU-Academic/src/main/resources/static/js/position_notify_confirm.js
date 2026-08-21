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

    let modalEl = document.getElementById('posNotifyConfirmModal');
    let bsModal = null;
    if (!modalEl) {
        modalEl = document.createElement('div');
        modalEl.className = 'modal fade';
        modalEl.id = 'posNotifyConfirmModal';
        modalEl.tabIndex = -1;
        modalEl.setAttribute('aria-hidden', 'true');
        modalEl.style.zIndex = '1065';
        modalEl.innerHTML =
            '<div class="modal-dialog modal-dialog-centered" style="max-width: 500px;">' +
                '<div class="modal-content shadow-lg border-0 rounded-4" style="overflow:hidden;">' +
                    '<div class="modal-header border-0 pb-0 pt-4 px-4 d-flex justify-content-between align-items-center">' +
                        '<h5 class="modal-title fw-bold" style="font-size: 1.15rem; margin:0;"><i class="fas fa-bell text-warning me-2"></i> อัปเดตสถานะและแจ้งเตือนผู้ยื่นคำร้อง</h5>' +
                        '<button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>' +
                    '</div>' +
                    '<div class="modal-body px-4 py-3">' +
                        '<p class="mb-3 text-secondary">ต้องการ<strong>อัปเดตสถานะคำร้อง</strong>และ<strong>ส่งอีเมลแจ้งเตือนผู้ยื่น</strong>หรือไม่?</p>' +
                        (statusInfo ? '<div class="alert alert-success d-flex align-items-center py-2 px-3 mb-3 border-0 rounded-3" style="font-size: 0.9rem;"><i class="fas fa-arrow-circle-right me-2 fs-5"></i><div>' + statusInfo + '</div></div>' : '') +
                        '<div class="alert alert-info py-2 px-3 mb-0 border-0 rounded-3" style="font-size: 0.88rem;">' +
                            '<i class="fas fa-info-circle me-1"></i> หากเอกสารยังไม่เรียบร้อย สามารถเลือก <strong>"ไม่แจ้งเตือน"</strong> เพื่อบันทึกไฟล์โดยยังไม่อัปเดตสถานะได้' +
                        '</div>' +
                    '</div>' +
                    '<div class="modal-footer border-0 pt-0 pb-4 px-4 d-flex justify-content-end gap-2">' +
                        '<button type="button" id="btnPosNoNotify" class="btn btn-outline-secondary px-3">' +
                            '<i class="fas fa-bell-slash me-1"></i> ไม่แจ้งเตือน (บันทึกอย่างเดียว)' +
                        '</button>' +
                        '<button type="button" id="btnPosYesNotify" class="btn btn-primary px-3">' +
                            '<i class="fas fa-check-circle me-1"></i> แจ้งเตือนผู้ยื่นและอัปเดตสถานะ' +
                        '</button>' +
                    '</div>' +
                '</div>' +
            '</div>';
        document.body.appendChild(modalEl);
    }

    function getModalInstance() {
        if (!modalEl) return null;
        if (!bsModal && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
            bsModal = new bootstrap.Modal(modalEl);
        }
        return bsModal;
    }

    function showModal() {
        const m = getModalInstance();
        if (m) m.show();
    }

    function hideModal() {
        const m = getModalInstance();
        if (m) m.hide();
    }

    // Intercept form submit button
    const submitBtns = form.querySelectorAll('button[type="submit"]');
    submitBtns.forEach(btn => {
        btn.type = 'button';
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            if (!form.reportValidity()) return;
            showModal();
        });
    });

    modalEl.addEventListener('click', function(e) {
        if (e.target.closest('#btnPosNoNotify')) {
            sendNotifyField.value = 'false';
            hideModal();
            if (window.setButtonLoading) {
                submitBtns.forEach(btn => window.setButtonLoading(btn, 'กำลังบันทึกเอกสาร...'));
            }
            form.submit();
        } else if (e.target.closest('#btnPosYesNotify')) {
            sendNotifyField.value = 'true';
            hideModal();
            if (window.setButtonLoading) {
                submitBtns.forEach(btn => window.setButtonLoading(btn, 'กำลังบันทึกเอกสาร...'));
            }
            form.submit();
        }
    });
}
