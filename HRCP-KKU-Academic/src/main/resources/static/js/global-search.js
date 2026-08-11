/**
 * Global Omnibox Search & Dynamic Breadcrumbs
 */

document.addEventListener('DOMContentLoaded', function() {
    initBreadcrumbs();
    initGlobalSearch();
});

function initBreadcrumbs() {
    var currentPageEl = document.getElementById('topbar-current-page');
    if (!currentPageEl) return;

    var path = window.location.pathname;
    var title = document.title ? document.title.replace(' - HRCP KKU', '').replace('HRCP KKU - ', '').trim() : '';

    var pathMap = {
        '/user/academic/dashboard': 'แดชบอร์ดงานวิชาการ',
        '/user/academic/history': 'ประวัติการยื่นคำร้อง',
        '/user/academic/new-request': 'ยื่นคำร้องขอประเมินการสอน',
        '/user/position/dashboard': 'ยื่นขอตำแหน่งทางวิชาการ',
        '/user/academic/documents': 'คลังเอกสาร & ข้อบังคับ',
        '/user/academic/storage': 'ที่เก็บไฟล์ของฉัน',
        '/user/academic/guide': 'คู่มือการใช้งาน',
        '/user/academic/settings': 'การตั้งค่าระบบ',
        '/user/notifications': 'ศูนย์การแจ้งเตือน',
        '/user/profile': 'โปรไฟล์ส่วนตัว',
        '/admin/academic/requests': 'แดชบอร์ดคำร้อง',
        '/admin/academic/staff': 'จัดการบุคลากร',
        '/admin/users': 'จัดการผู้ใช้งาน',
        '/admin/add-admin': 'เพิ่มผู้ใช้ / แอดมิน',
        '/admin/activity-logs': 'ประวัติกิจกรรมระบบ',
        '/admin/file-manager': 'จัดการไฟล์ระบบ (Storage)',
        '/admin/academic/guide': 'คู่มือแอดมิน',
        '/admin/academic/settings': 'การตั้งค่าระบบ',
        '/admin/notifications': 'ศูนย์การแจ้งเตือน',
        '/admin/profile': 'โปรไฟล์ผู้ดูแลระบบ'
    };

    var matched = pathMap[path];
    if (!matched) {
        if (path.includes('/position/request/')) {
            matched = 'รายละเอียดคำร้องขอตำแหน่ง';
        } else if (path.includes('/academic/requests/')) {
            matched = 'รายละเอียดคำร้องประเมิน';
        } else if (title && title !== 'HRCP.KKU' && title !== 'Academic') {
            matched = title;
        } else {
            matched = 'ระบบงานวิชาการ';
        }
    }

    currentPageEl.textContent = matched;
}

function initGlobalSearch() {
    var input = document.getElementById('globalSearchInput');
    var dropdown = document.getElementById('globalSearchResults');
    if (!input || !dropdown) return;

    var debounceTimer = null;
    var currentSelectedIndex = -1;

    // Keyboard shortcut Ctrl+K or Cmd+K
    document.addEventListener('keydown', function(e) {
        if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
            e.preventDefault();
            input.focus();
            input.select();
        }
    });

    input.addEventListener('input', function() {
        clearTimeout(debounceTimer);
        var q = input.value.trim();
        if (q.length === 0) {
            dropdown.classList.add('d-none');
            dropdown.innerHTML = '';
            return;
        }

        dropdown.classList.remove('d-none');
        dropdown.innerHTML = '<div class="p-3 text-center text-muted small"><i class="fas fa-spinner fa-spin me-2"></i>กำลังค้นหา...</div>';

        debounceTimer = setTimeout(function() {
            fetch('/api/global-search?q=' + encodeURIComponent(q))
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    renderSearchResults(data.results || [], q);
                })
                .catch(function(err) {
                    console.error('Search error:', err);
                    dropdown.innerHTML = '<div class="p-3 text-center text-danger small">เกิดข้อผิดพลาดในการค้นหา</div>';
                });
        }, 200);
    });

    input.addEventListener('focus', function() {
        if (input.value.trim().length > 0) {
            dropdown.classList.remove('d-none');
        }
    });

    // Keyboard navigation (ArrowDown, ArrowUp, Enter, Escape)
    input.addEventListener('keydown', function(e) {
        var items = dropdown.querySelectorAll('.search-result-item');
        if (items.length === 0) return;

        if (e.key === 'ArrowDown') {
            e.preventDefault();
            currentSelectedIndex = (currentSelectedIndex + 1) % items.length;
            updateItemSelection(items);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            currentSelectedIndex = (currentSelectedIndex - 1 + items.length) % items.length;
            updateItemSelection(items);
        } else if (e.key === 'Enter') {
            e.preventDefault();
            if (currentSelectedIndex >= 0 && currentSelectedIndex < items.length) {
                items[currentSelectedIndex].click();
            }
        } else if (e.key === 'Escape') {
            dropdown.classList.add('d-none');
            input.blur();
        }
    });

    function updateItemSelection(items) {
        items.forEach(function(el, idx) {
            if (idx === currentSelectedIndex) {
                el.classList.add('active', 'bg-light');
                el.scrollIntoView({ block: 'nearest' });
            } else {
                el.classList.remove('active', 'bg-light');
            }
        });
    }

    // Close when clicking outside
    document.addEventListener('click', function(e) {
        if (!e.target.closest('.global-search-container')) {
            dropdown.classList.add('d-none');
        }
    });

    function renderSearchResults(results, query) {
        if (results.length === 0) {
            dropdown.innerHTML = '<div class="p-3 text-center text-muted small"><i class="fas fa-search me-2"></i>ไม่พบผลลัพธ์สำหรับ "<strong>' + escapeHtml(query) + '</strong>"</div>';
            return;
        }

        var html = '<div class="list-group list-group-flush py-1">';
        var currentCat = null;

        results.forEach(function(item) {
            if (item.category !== currentCat) {
                currentCat = item.category;
                html += '<div class="dropdown-header px-3 py-1 text-uppercase fw-bold text-muted" style="font-size: 0.72rem; letter-spacing: 0.5px;">' + escapeHtml(currentCat) + '</div>';
            }

            html += '<a href="' + item.url + '" class="list-group-item list-group-item-action d-flex align-items-center justify-content-between px-3 py-2 border-0 search-result-item" style="text-decoration:none;">';
            html += '<div class="d-flex align-items-center gap-2 min-width-0">';
            html += '<div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 28px; height: 28px; background: rgba(13,71,161,0.08);">';
            html += '<i class="' + (item.icon || 'fas fa-link') + '" style="font-size: 0.85rem;"></i>';
            html += '</div>';
            html += '<div class="min-width-0">';
            html += '<div class="text-dark fw-semibold text-truncate" style="font-size: 0.85rem;">' + highlightKeyword(item.title, query) + '</div>';
            if (item.subtitle) {
                html += '<small class="text-muted text-truncate d-block" style="font-size: 0.75rem;">' + escapeHtml(item.subtitle) + '</small>';
            }
            html += '</div></div>';

            if (item.badge) {
                html += '<span class="badge ' + (item.badgeClass || 'bg-light text-dark') + ' rounded-pill ms-2 flex-shrink-0" style="font-size: 0.7rem;">' + escapeHtml(item.badge) + '</span>';
            }

            html += '</a>';
        });

        html += '</div>';
        dropdown.innerHTML = html;
        currentSelectedIndex = -1;
    }

    function escapeHtml(str) {
        if (!str) return '';
        var div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    function highlightKeyword(text, kw) {
        if (!text) return '';
        var escaped = escapeHtml(text);
        if (!kw) return escaped;
        var regex = new RegExp('(' + kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ')', 'gi');
        return escaped.replace(regex, '<span class="bg-warning bg-opacity-50 text-dark px-1 rounded">$1</span>');
    }
}
