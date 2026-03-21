// Storage operations JavaScript
var currentFolderId = null;

function initStorage(folderId) {
    currentFolderId = folderId;
    initDragDrop();
}

function initDragDrop() {
    var body = document.body;
    body.addEventListener('dragover', function(e) {
        e.preventDefault();
        var dz = document.getElementById('dropZone');
        if (dz) dz.style.display = 'block';
    });
    body.addEventListener('dragleave', function(e) {
        if (!e.relatedTarget || e.relatedTarget === document.documentElement) {
            var dz = document.getElementById('dropZone');
            if (dz) dz.style.display = 'none';
        }
    });
    body.addEventListener('drop', function(e) {
        e.preventDefault();
        var dz = document.getElementById('dropZone');
        if (dz) dz.style.display = 'none';
        if (e.dataTransfer.files.length > 0) uploadFiles(e.dataTransfer.files);
    });
}

function uploadFiles(files) {
    for (var i = 0; i < files.length; i++) {
        var fd = new FormData();
        fd.append('file', files[i]);
        if (currentFolderId) fd.append('folderId', currentFolderId);
        fd.append(csrfHeaderName === 'X-CSRF-TOKEN' ? '_csrf' : csrfHeaderName, csrfToken);

        fetch('/admin/file-manager/storage/upload', { method: 'POST', body: fd })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                showToast(data.message, data.success);
                if (data.success) setTimeout(function() { location.reload(); }, 800);
            })
            .catch(function() { showToast('อัปโหลดล้มเหลว', false); });
    }
    document.getElementById('fileUploadInput').value = '';
}

function showCreateFolderModal() {
    var name = prompt('ชื่อโฟลเดอร์ใหม่:');
    if (!name || !name.trim()) return;
    var fd = new FormData();
    fd.append('name', name.trim());
    if (currentFolderId) fd.append('parentId', currentFolderId);
    fd.append(csrfHeaderName === 'X-CSRF-TOKEN' ? '_csrf' : csrfHeaderName, csrfToken);

    fetch('/admin/file-manager/storage/folder/create', { method: 'POST', body: fd })
        .then(function(r) { return r.json(); })
        .then(function(data) {
            showToast(data.message, data.success);
            if (data.success) setTimeout(function() { location.reload(); }, 800);
        });
}

function renameFolderPrompt(card) {
    var id = card.getAttribute('data-id');
    var oldName = card.getAttribute('data-name');
    var name = prompt('เปลี่ยนชื่อโฟลเดอร์:', oldName);
    if (!name || !name.trim() || name === oldName) return;
    var fd = new FormData();
    fd.append('id', id);
    fd.append('name', name.trim());
    fd.append(csrfHeaderName === 'X-CSRF-TOKEN' ? '_csrf' : csrfHeaderName, csrfToken);

    fetch('/admin/file-manager/storage/folder/rename', { method: 'POST', body: fd })
        .then(function(r) { return r.json(); })
        .then(function(data) {
            showToast(data.message, data.success);
            if (data.success) setTimeout(function() { location.reload(); }, 800);
        });
}

function deleteFolderPrompt(card) {
    var id = card.getAttribute('data-id');
    var name = card.getAttribute('data-name');
    showConfirm('ลบโฟลเดอร์',
        'ต้องการลบโฟลเดอร์ <strong>' + name + '</strong> และไฟล์ทั้งหมดข้างในหรือไม่?<br><small class="text-danger">การดำเนินการนี้ไม่สามารถกู้คืนได้!</small>',
        function() {
            var fd = new FormData();
            fd.append('id', id);
            fd.append(csrfHeaderName === 'X-CSRF-TOKEN' ? '_csrf' : csrfHeaderName, csrfToken);
            fetch('/admin/file-manager/storage/folder/delete', { method: 'POST', body: fd })
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    showToast(data.message, data.success);
                    if (data.success) setTimeout(function() { location.reload(); }, 800);
                });
        });
}

function renameFilePrompt(btn) {
    var id = btn.getAttribute('data-id');
    var oldName = btn.getAttribute('data-name');
    var name = prompt('เปลี่ยนชื่อไฟล์:', oldName);
    if (!name || !name.trim() || name === oldName) return;
    var fd = new FormData();
    fd.append('id', id);
    fd.append('name', name.trim());
    fd.append(csrfHeaderName === 'X-CSRF-TOKEN' ? '_csrf' : csrfHeaderName, csrfToken);

    fetch('/admin/file-manager/storage/file/rename', { method: 'POST', body: fd })
        .then(function(r) { return r.json(); })
        .then(function(data) {
            showToast(data.message, data.success);
            if (data.success) setTimeout(function() { location.reload(); }, 800);
        });
}

function deleteFilePrompt(btn) {
    var id = btn.getAttribute('data-id');
    var name = btn.getAttribute('data-name');
    showConfirm('ย้ายไปถังขยะ',
        'ต้องการย้าย <strong>' + name + '</strong> ไปถังขยะหรือไม่?',
        function() {
            var fd = new FormData();
            fd.append('id', id);
            fd.append(csrfHeaderName === 'X-CSRF-TOKEN' ? '_csrf' : csrfHeaderName, csrfToken);
            fetch('/admin/file-manager/storage/file/delete', { method: 'POST', body: fd })
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    showToast(data.message, data.success);
                    if (data.success) {
                        var row = document.getElementById('sfile-' + id);
                        if (row) { row.classList.add('removing'); setTimeout(function() { row.remove(); }, 300); }
                    }
                });
        });
}

function restoreStorageFile(btn) {
    var id = btn.getAttribute('data-id');
    var fd = new FormData();
    fd.append('id', id);
    fd.append(csrfHeaderName === 'X-CSRF-TOKEN' ? '_csrf' : csrfHeaderName, csrfToken);
    fetch('/admin/file-manager/storage/file/restore', { method: 'POST', body: fd })
        .then(function(r) { return r.json(); })
        .then(function(data) {
            showToast(data.message, data.success);
            if (data.success) {
                var row = document.getElementById('strash-' + id);
                if (row) { row.classList.add('removing'); setTimeout(function() { row.remove(); }, 300); }
            }
        });
}

function permanentDeleteStorageFile(btn) {
    var id = btn.getAttribute('data-id');
    var name = btn.getAttribute('data-name');
    showConfirm('ลบถาวร',
        '⚠️ ลบ <strong>' + name + '</strong> ถาวร?<br><small class="text-danger">ไม่สามารถกู้คืนได้!</small>',
        function() {
            var fd = new FormData();
            fd.append('id', id);
            fd.append(csrfHeaderName === 'X-CSRF-TOKEN' ? '_csrf' : csrfHeaderName, csrfToken);
            fetch('/admin/file-manager/storage/file/permanent-delete', { method: 'POST', body: fd })
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    showToast(data.message, data.success);
                    if (data.success) {
                        var row = document.getElementById('strash-' + id);
                        if (row) { row.classList.add('removing'); setTimeout(function() { row.remove(); }, 300); }
                    }
                });
        });
}

function emptyStorageTrash() {
    showConfirm('ล้างถังขยะทั้งหมด',
        '⚠️ ลบไฟล์ทั้งหมดในถังขยะถาวร?<br><small class="text-danger">ไม่สามารถกู้คืนได้!</small>',
        function() {
            fetch('/admin/file-manager/storage/empty-trash', { method: 'POST', headers: getCsrfHeaders() })
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    showToast(data.message, data.success);
                    if (data.success) setTimeout(function() { location.reload(); }, 1000);
                });
        });
}
