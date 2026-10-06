/*
 * ส่งเอกสารที่แก้ไขแล้ว (Flow ข้อ 13) — สะสมไฟล์และลิงก์หลายรายการ แสดงรายการที่แนบ
 * แล้วยืนยันด้วย modal ก่อนส่ง (เตือนให้นำฉบับจริงยื่นนักทรัพยากรบุคคล)
 *
 * input[type=file] เลือกซ้ำแล้วไฟล์เดิมหาย จึงเก็บรายการไว้เองแล้วเติมกลับเข้าช่อง name="files"
 * ผ่าน DataTransfer ก่อนส่งฟอร์ม
 */
(function () {
    'use strict';

    var form = document.getElementById('revisionForm');
    if (!form) return;

    var MAX_FILE_BYTES = 75 * 1024 * 1024;
    var MAX_TOTAL_BYTES = 100 * 1024 * 1024;
    var ALLOWED = ['pdf', 'doc', 'docx', 'zip'];
    var maxItems = parseInt(form.getAttribute('data-max-items'), 10) || 10;

    var picker = document.getElementById('revisionFilePicker');
    var filesField = document.getElementById('revisionFilesField');
    var linkFields = document.getElementById('revisionLinkFields');
    var linkRow = document.getElementById('revisionLinkRow');
    var linkUrl = document.getElementById('revisionLinkUrl');
    var linkTitle = document.getElementById('revisionLinkTitle');
    var linkError = document.getElementById('revisionLinkError');
    var list = document.getElementById('revisionPendingList');
    var empty = document.getElementById('revisionEmpty');
    var count = document.getElementById('revisionCount');
    var submitBtn = document.getElementById('revisionSubmitBtn');
    var modalEl = document.getElementById('confirmRevisionModal');
    var confirmList = document.getElementById('revisionConfirmList');
    var confirmCount = document.getElementById('revisionConfirmCount');

    var files = [];
    var links = [];

    function itemCount() { return files.length + links.length; }

    function totalBytes() {
        return files.reduce(function (sum, f) { return sum + f.size; }, 0);
    }

    function sizeLabel(bytes) {
        return bytes < 1024 * 1024
            ? Math.max(1, Math.round(bytes / 1024)) + ' KB'
            : (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    }

    function extOf(name) {
        var dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : '';
    }

    function iconFor(ext) {
        if (ext === 'pdf') return 'fa-file-pdf';
        if (ext === 'doc' || ext === 'docx') return 'fa-file-word';
        if (ext === 'zip') return 'fa-file-zipper';
        return 'fa-file';
    }

    function showError(msg) {
        linkError.textContent = msg;
        linkError.classList.toggle('d-none', !msg);
    }

    function row(icon, name, meta, onRemove) {
        var li = document.createElement('li');
        li.className = 'revision-file';
        var i = document.createElement('i');
        i.className = 'fas fa-fw ' + icon;
        var span = document.createElement('span');
        span.className = 'revision-file-name';
        span.textContent = name;
        span.title = name;
        var small = document.createElement('small');
        small.className = 'text-muted text-nowrap';
        small.textContent = meta;
        li.appendChild(i);
        li.appendChild(span);
        li.appendChild(small);
        if (onRemove) {
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'btn btn-sm btn-link text-danger p-0';
            btn.setAttribute('aria-label', 'นำ ' + name + ' ออก');
            btn.innerHTML = '<i class="fas fa-xmark"></i>';
            btn.addEventListener('click', onRemove);
            li.appendChild(btn);
        }
        return li;
    }

    function fillRows(target, removable) {
        target.innerHTML = '';
        files.forEach(function (f, idx) {
            target.appendChild(row(iconFor(extOf(f.name)), f.name, sizeLabel(f.size),
                removable ? function () { files.splice(idx, 1); render(); } : null));
        });
        links.forEach(function (l, idx) {
            target.appendChild(row('fa-link', l.title || l.url, 'ลิงก์',
                removable ? function () { links.splice(idx, 1); render(); } : null));
        });
    }

    function render() {
        fillRows(list, true);
        count.textContent = itemCount();
        empty.classList.toggle('d-none', itemCount() > 0);
        submitBtn.disabled = itemCount() === 0;
    }

    picker.addEventListener('change', function () {
        var rejected = [];
        Array.prototype.forEach.call(picker.files, function (f) {
            if (ALLOWED.indexOf(extOf(f.name)) < 0) {
                rejected.push(f.name + ' (ไม่รองรับชนิดไฟล์นี้)');
            } else if (f.size > MAX_FILE_BYTES) {
                rejected.push(f.name + ' (เกิน 75 MB)');
            } else if (itemCount() >= maxItems) {
                rejected.push(f.name + ' (เกิน ' + maxItems + ' รายการ)');
            } else if (totalBytes() + f.size > MAX_TOTAL_BYTES) {
                rejected.push(f.name + ' (ขนาดรวมเกิน 100 MB)');
            } else if (!files.some(function (x) { return x.name === f.name && x.size === f.size; })) {
                files.push(f);
            }
        });
        picker.value = '';
        showError(rejected.length ? 'แนบไม่ได้: ' + rejected.join(', ') : '');
        render();
    });

    document.getElementById('revisionShowLink').addEventListener('click', function () {
        linkRow.classList.remove('d-none');
        linkUrl.focus();
    });

    function addLink() {
        var url = linkUrl.value.trim();
        if (!/^https?:\/\/\S+$/i.test(url)) {
            showError('ลิงก์ต้องขึ้นต้นด้วย http:// หรือ https://');
            return;
        }
        if (itemCount() >= maxItems) {
            showError('แนบได้ไม่เกิน ' + maxItems + ' รายการ');
            return;
        }
        links.push({ url: url, title: linkTitle.value.trim() });
        linkUrl.value = '';
        linkTitle.value = '';
        showError('');
        render();
    }

    document.getElementById('revisionAddLink').addEventListener('click', addLink);
    [linkUrl, linkTitle].forEach(function (el) {
        el.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') { e.preventDefault(); addLink(); }
        });
    });

    submitBtn.addEventListener('click', function () {
        if (itemCount() === 0) return;
        fillRows(confirmList, false);
        confirmCount.textContent = itemCount();
        bootstrap.Modal.getOrCreateInstance(modalEl).show();
    });

    form.addEventListener('submit', function (e) {
        if (itemCount() === 0) { e.preventDefault(); return; }

        var dt = new DataTransfer();
        files.forEach(function (f) { dt.items.add(f); });
        filesField.files = dt.files;

        linkFields.innerHTML = '';
        links.forEach(function (l) {
            ['linkUrl', 'linkTitle'].forEach(function (name) {
                var input = document.createElement('input');
                input.type = 'hidden';
                input.name = name;
                input.value = name === 'linkUrl' ? l.url : l.title;
                linkFields.appendChild(input);
            });
        });

        var confirmBtn = document.getElementById('revisionConfirmSubmit');
        confirmBtn.disabled = true;
        confirmBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span> กำลังส่ง...';
    });

    render();
})();
