/**
 * Notification Center AJAX Interactions
 */

function getCsrf() {
    var tokenMeta = document.querySelector('meta[name="_csrf"]');
    var headerMeta = document.querySelector('meta[name="_csrf_header"]');
    return {
        token: tokenMeta ? tokenMeta.getAttribute('content') : '',
        header: headerMeta ? headerMeta.getAttribute('content') : 'X-CSRF-TOKEN'
    };
}

function updateSidebarBadge(count) {
    var badges = document.querySelectorAll('.notification-sidebar-badge');
    badges.forEach(function(badge) {
        if (count > 0) {
            badge.textContent = count;
            badge.style.display = 'inline-block';
        } else {
            badge.style.display = 'none';
        }
    });

    var topbarDots = document.querySelectorAll('.topbar-badge-dot');
    topbarDots.forEach(function(dot) {
        if (count > 0) {
            dot.style.display = 'inline-block';
        } else {
            dot.style.display = 'none';
        }
    });
}

function showNotificationToast(msg, isSuccess) {
    if (typeof showToast === 'function') {
        showToast(msg, isSuccess !== false);
        return;
    }

    var toastEl = document.createElement('div');
    toastEl.className = 'toast align-items-center text-white ' + (isSuccess !== false ? 'bg-success' : 'bg-danger') + ' border-0 position-fixed bottom-0 end-0 m-3 shadow';
    toastEl.style.zIndex = '99999';
    toastEl.innerHTML = '<div class="d-flex"><div class="toast-body">' + msg + '</div><button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button></div>';
    document.body.appendChild(toastEl);
    if (window.bootstrap && bootstrap.Toast) {
        var toast = new bootstrap.Toast(toastEl, { delay: 3000 });
        toast.show();
        toastEl.addEventListener('hidden.bs.toast', function() { toastEl.remove(); });
    } else {
        setTimeout(function() { toastEl.remove(); }, 3000);
    }
}

function handleRowClick(e, id, link) {
    // If click originated from interactive elements (button, link, dropdown), skip navigation
    if (e.target.closest('button') || e.target.closest('a') || e.target.closest('.dropdown-menu')) {
        return;
    }
    window.location.href = '/notifications/open/' + id;
}

function toggleStar(id, btn) {
    var csrf = getCsrf();
    var headers = { 'Content-Type': 'application/json' };
    if (csrf.token) headers[csrf.header] = csrf.token;

    fetch('/api/notifications/' + id + '/star', {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            var icon = btn.querySelector('i');
            if (data.isStarred) {
                icon.className = 'fas fa-star text-warning';
                btn.setAttribute('title', 'ยกเลิกติดดาว');
            } else {
                icon.className = 'far fa-star text-muted';
                btn.setAttribute('title', 'ติดดาว');
            }
        }
    })
    .catch(function(err) {
        console.error('Star error:', err);
    });
}

function toggleImportant(id, btn) {
    var csrf = getCsrf();
    var headers = { 'Content-Type': 'application/json' };
    if (csrf.token) headers[csrf.header] = csrf.token;

    fetch('/api/notifications/' + id + '/important', {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            var icon = btn.querySelector('i');
            if (data.isImportant) {
                icon.className = 'fas fa-tag text-danger';
                btn.setAttribute('title', 'ยกเลิกสำคัญ');
            } else {
                icon.className = 'far fa-tag text-muted';
                btn.setAttribute('title', 'ทำเครื่องหมายว่าสำคัญ');
            }
        }
    })
    .catch(function(err) {
        console.error('Important error:', err);
    });
}

function markAsRead(id) {
    var csrf = getCsrf();
    var headers = { 'Content-Type': 'application/json' };
    if (csrf.token) headers[csrf.header] = csrf.token;

    fetch('/api/notifications/' + id + '/read', {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            var row = document.getElementById('notif-row-' + id);
            if (row) {
                row.classList.remove('bg-light', 'bg-opacity-50', 'fw-semibold', 'unread-row');
                var newBadge = row.querySelector('.badge.bg-primary');
                if (newBadge && newBadge.textContent.trim() === 'ใหม่') {
                    newBadge.remove();
                }
            }
            if (typeof data.unreadCount !== 'undefined') {
                updateSidebarBadge(data.unreadCount);
            }
        }
    });
}

function markAllAsRead() {
    var csrf = getCsrf();
    var headers = { 'Content-Type': 'application/json' };
    if (csrf.token) headers[csrf.header] = csrf.token;

    fetch('/api/notifications/read-all', {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            showNotificationToast(data.message || 'ทำเครื่องหมายอ่านทั้งหมดแล้ว', true);
            updateSidebarBadge(0);
            setTimeout(function() {
                window.location.reload();
            }, 600);
        }
    });
}

function snoozeNotification(id, duration) {
    var csrf = getCsrf();
    var headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
    if (csrf.token) headers[csrf.header] = csrf.token;

    fetch('/api/notifications/' + id + '/snooze?duration=' + encodeURIComponent(duration), {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            showNotificationToast(data.message || 'เลื่อนการแจ้งเตือนแล้ว', true);
            if (typeof data.unreadCount !== 'undefined') {
                updateSidebarBadge(data.unreadCount);
            }
            var row = document.getElementById('notif-row-' + id);
            if (row) {
                row.style.opacity = '0.5';
                setTimeout(function() { window.location.reload(); }, 500);
            }
        }
    });
}

function unsnoozeNotification(id) {
    var csrf = getCsrf();
    var headers = { 'Content-Type': 'application/json' };
    if (csrf.token) headers[csrf.header] = csrf.token;

    fetch('/api/notifications/' + id + '/unsnooze', {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            showNotificationToast('ยกเลิกการเลื่อนเตือนแล้ว', true);
            setTimeout(function() { window.location.reload(); }, 500);
        }
    });
}

function deleteNotification(id) {
    var csrf = getCsrf();
    var headers = { 'Content-Type': 'application/json' };
    if (csrf.token) headers[csrf.header] = csrf.token;

    fetch('/api/notifications/' + id + '/delete', {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            showNotificationToast(data.message || 'ย้ายไปถังขยะแล้ว', true);
            if (typeof data.unreadCount !== 'undefined') {
                updateSidebarBadge(data.unreadCount);
            }
            var row = document.getElementById('notif-row-' + id);
            if (row) {
                row.style.transition = 'all 0.3s';
                row.style.opacity = '0';
                setTimeout(function() { row.remove(); }, 300);
            }
        }
    });
}

function restoreNotification(id) {
    var csrf = getCsrf();
    var headers = { 'Content-Type': 'application/json' };
    if (csrf.token) headers[csrf.header] = csrf.token;

    fetch('/api/notifications/' + id + '/restore', {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            showNotificationToast(data.message || 'กู้คืนแล้ว', true);
            var row = document.getElementById('notif-row-' + id);
            if (row) {
                row.style.transition = 'all 0.3s';
                row.style.opacity = '0';
                setTimeout(function() { row.remove(); }, 300);
            }
        }
    });
}

function permanentDeleteNotification(id) {
    if (!confirm('ต้องการลบการแจ้งเตือนนี้ถาวรหรือไม่? (ไม่สามารถกู้คืนได้)')) return;

    var csrf = getCsrf();
    var headers = { 'Content-Type': 'application/json' };
    if (csrf.token) headers[csrf.header] = csrf.token;

    fetch('/api/notifications/' + id + '/permanent-delete', {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            showNotificationToast(data.message || 'ลบถาวรแล้ว', true);
            var row = document.getElementById('notif-row-' + id);
            if (row) {
                row.remove();
            }
        }
    });
}

function emptyTrash() {
    if (!confirm('ต้องการล้างถังขยะการแจ้งเตือนทั้งหมดหรือไม่? (ไม่สามารถกู้คืนได้)')) return;

    var csrf = getCsrf();
    var headers = { 'Content-Type': 'application/json' };
    if (csrf.token) headers[csrf.header] = csrf.token;

    fetch('/api/notifications/empty-trash', {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            showNotificationToast(data.message || 'ล้างถังขยะแล้ว', true);
            setTimeout(function() { window.location.reload(); }, 600);
        }
    });
}

/*
 * Wiring for the notification list.
 *
 * The rows and dropdown items used to carry th:onclick="'toggleStar(' + ${n.id} + ...)"
 * and friends. CSP refuses inline handler attributes, so the template now emits
 * data-notif-action / data-notif-id / data-notif-duration and the routing lives
 * here. Delegated from the document, so it survives re-renders.
 */
document.addEventListener('click', function (e) {
    var el = e.target.closest('[data-notif-action]');
    if (el) {
        var action = el.dataset.notifAction;
        // Every one of these sat on an <a href="#"> or a button inside a row that
        // is itself clickable, so swallow the default and stop the row navigating.
        e.preventDefault();
        e.stopPropagation();

        var id = parseInt(el.dataset.notifId || '0', 10);
        switch (action) {
            case 'star':            toggleStar(id, el); break;
            case 'important':       toggleImportant(id, el); break;
            case 'markRead':        markAsRead(id); break;
            case 'snooze':          snoozeNotification(id, el.dataset.notifDuration); break;
            case 'unsnooze':        unsnoozeNotification(id); break;
            case 'delete':          deleteNotification(id); break;
            case 'restore':         restoreNotification(id); break;
            case 'permanentDelete': permanentDeleteNotification(id); break;
            case 'clearSearch':
                var tab = document.querySelector('[name=tab]');
                window.location.href = '?tab=' + (tab ? tab.value : '');
                break;
            default:
                console.error('[notification] unknown data-notif-action: ' + action);
        }
        return;
    }

    // Row click -> open the notification. handleRowClick already ignores clicks
    // that came from a button, link or dropdown.
    var row = e.target.closest('[data-notif-id]:not([data-notif-action])');
    if (row) {
        handleRowClick(e, parseInt(row.dataset.notifId || '0', 10), null);
    }
});
