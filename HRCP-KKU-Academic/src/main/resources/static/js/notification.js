/**
 * Notification Center & Global Notification Hub AJAX Interactions
 */

function getCsrf() {
    var tokenMeta = document.querySelector('meta[name="_csrf"]');
    var headerMeta = document.querySelector('meta[name="_csrf_header"]');
    var token = tokenMeta ? tokenMeta.getAttribute('content') : '';
    var header = headerMeta ? headerMeta.getAttribute('content') : 'X-CSRF-TOKEN';
    var headers = {};
    if (token) {
        headers[header] = token;
        headers['X-CSRF-TOKEN'] = token;
        headers['X-XSRF-TOKEN'] = token;
    }
    return {
        token: token,
        header: header,
        headers: headers
    };
}

function getCurrentTab() {
    var urlParams = new URLSearchParams(window.location.search);
    return urlParams.get('tab') || 'all';
}

function updateTabCounts(tabCounts) {
    if (!tabCounts) return;
    ['all', 'unread', 'starred', 'important', 'snoozed', 'trash'].forEach(function(tab) {
        var el = document.getElementById('notifCount-' + tab);
        if (el && typeof tabCounts[tab] !== 'undefined') {
            el.textContent = tabCounts[tab];
        }
    });
    if (typeof tabCounts['unread'] !== 'undefined') {
        updateSidebarBadge(tabCounts['unread']);
    }
}

function updateSidebarBadge(count) {
    var cnt = parseInt(count || '0', 10);
    
    // Sidebar badges
    var badges = document.querySelectorAll('.notification-sidebar-badge');
    badges.forEach(function(badge) {
        if (cnt > 0) {
            badge.textContent = cnt;
            badge.style.display = 'inline-block';
        } else {
            badge.style.display = 'none';
        }
    });

    // Topbar bell dot
    var topbarDots = document.querySelectorAll('.topbar-badge-dot, #topbarNotifBadge');
    topbarDots.forEach(function(dot) {
        if (cnt > 0) {
            dot.style.display = 'inline-block';
            dot.setAttribute('title', cnt + ' รายการที่ยังไม่ได้อ่าน');
        } else {
            dot.style.display = 'none';
        }
    });

    // Topbar header unread pill inside dropdown
    var unreadPills = document.querySelectorAll('#topbarUnreadPill, .topbar-unread-pill');
    unreadPills.forEach(function(pill) {
        if (cnt > 0) {
            pill.textContent = cnt;
            pill.style.display = 'inline-block';
        } else {
            pill.style.display = 'none';
        }
    });
}

function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function showNotificationToast(msg, isSuccess) {
    if (typeof showToast === 'function') {
        showToast(msg, isSuccess !== false);
        return;
    }

    var toastEl = document.createElement('div');
    toastEl.className = 'toast align-items-center text-white ' + (isSuccess !== false ? 'bg-success' : 'bg-danger') + ' border-0 position-fixed bottom-0 end-0 m-3 shadow';
    toastEl.style.zIndex = '99999';
    toastEl.innerHTML = '<div class="d-flex"><div class="toast-body">' + escapeHtml(msg) + '</div><button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button></div>';
    document.body.appendChild(toastEl);
    if (window.bootstrap && bootstrap.Toast) {
        var toast = new bootstrap.Toast(toastEl, { delay: 3000 });
        toast.show();
        toastEl.addEventListener('hidden.bs.toast', function() { toastEl.remove(); });
    } else {
        setTimeout(function() { toastEl.remove(); }, 3000);
    }
}

// ================= Global Reader Modal =================

function openReaderModal(notif) {
    if (!notif) return;

    var modalEl = document.getElementById('notifReaderModal');
    if (!modalEl) {
        if (notif.id) {
            window.location.href = '/notifications/open/' + notif.id;
        }
        return;
    }

    var id = notif.id;
    var title = notif.title || notif.notifTitle || 'การแจ้งเตือน';
    var message = notif.message || notif.notifMessage || '';
    var link = notif.link || notif.notifLink || '';
    var typeLabel = notif.typeLabel || notif.notifTypeLabel || 'การแจ้งเตือน';
    var iconClass = notif.iconClass || notif.notifIcon || 'fas fa-bell text-primary';
    var relativeTime = notif.relativeTime || notif.notifRelativeTime || 'เมื่อสักครู่';
    var formattedDate = notif.formattedDate || notif.notifFormattedDate || '';
    var isImportant = notif.isImportant === true || notif.notifIsImportant === 'true' || notif.notifIsImportant === true;
    var isRead = notif.isRead === true || notif.notifIsRead === 'true' || notif.notifIsRead === true;
    var actorName = notif.actorName || notif.notifActor || 'ระบบสารสนเทศ KKU-HRCP';

    // Populate Modal elements
    var titleEl = document.getElementById('notifReaderModalLabel');
    if (titleEl) titleEl.textContent = title;

    var msgEl = document.getElementById('modalNotifMessage');
    if (msgEl) msgEl.textContent = message;

    var typeEl = document.getElementById('modalNotifTypeBadge');
    if (typeEl) typeEl.textContent = typeLabel;

    var impBadge = document.getElementById('modalNotifImportantBadge');
    if (impBadge) {
        if (isImportant) impBadge.classList.remove('d-none');
        else impBadge.classList.add('d-none');
    }

    var timeEl = document.getElementById('modalNotifRelativeTime');
    if (timeEl) timeEl.textContent = relativeTime;

    var dateEl = document.getElementById('modalNotifFullDate');
    if (dateEl) dateEl.textContent = formattedDate;

    var actorEl = document.getElementById('modalNotifActor');
    if (actorEl) actorEl.textContent = actorName;

    var iconEl = document.getElementById('modalNotifIcon');
    if (iconEl) iconEl.className = iconClass;

    var markUnreadBtn = document.getElementById('modalMarkUnreadBtn');
    if (markUnreadBtn) {
        markUnreadBtn.dataset.notifId = id;
    }

    // Action Link Button & Callout Box
    var actionCallout = document.getElementById('modalActionCallout');
    var actionBtn = document.getElementById('modalActionLinkBtn');
    if (actionBtn) {
        if (link && link.trim() !== '' && link !== '#') {
            actionBtn.href = link;
            if (actionCallout) actionCallout.classList.remove('d-none');
        } else {
            actionBtn.href = '#';
            if (actionCallout) actionCallout.classList.add('d-none');
        }
    }

    // Show modal
    if (window.bootstrap && bootstrap.Modal) {
        var modal = bootstrap.Modal.getOrCreateInstance(modalEl);
        modal.show();
    }

    // Auto mark as read if currently unread
    if (!isRead && id) {
        markAsRead(id);
    }
}

function openNotificationById(id) {
    if (!id) return;
    fetch('/api/notifications/' + id + '/detail')
        .then(function(r) { return r.json(); })
        .then(function(data) {
            if (data.success && data.notification) {
                openReaderModal(data.notification);
            }
        })
        .catch(function(err) {
            console.error('Error fetching notification detail:', err);
            window.location.href = '/notifications/open/' + id;
        });
}

function handleRowClick(e, id, rowEl) {
    // If click originated from interactive elements (button, link, dropdown), skip
    if (e.target.closest('button') || e.target.closest('a') || e.target.closest('.dropdown-menu') || e.target.closest('[data-stop-propagation]')) {
        return;
    }

    if (rowEl && rowEl.dataset && rowEl.dataset.notifTitle) {
        var d = rowEl.dataset;
        openReaderModal({
            id: id,
            title: d.notifTitle,
            message: d.notifMessage,
            link: d.notifLink,
            typeLabel: d.notifTypeLabel,
            iconClass: d.notifIcon,
            relativeTime: d.notifRelativeTime,
            formattedDate: d.notifFormattedDate,
            isImportant: d.notifIsImportant,
            isRead: d.notifIsRead,
            actorName: d.notifActor
        });
    } else {
        openNotificationById(id);
    }
}

// ================= AJAX Actions =================

function markAsRead(id, callback) {
    var csrf = getCsrf();
    var headers = Object.assign({ 'Content-Type': 'application/json' }, csrf.headers);

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
                row.dataset.notifIsRead = 'true';
                var newBadge = row.querySelector('.badge.bg-primary');
                if (newBadge && newBadge.textContent.trim() === 'ใหม่') {
                    newBadge.remove();
                }
                if (getCurrentTab() === 'unread') {
                    row.style.transition = 'all 0.3s';
                    row.style.opacity = '0';
                    setTimeout(function() { row.remove(); }, 300);
                }
            }
            if (data.tabCounts) {
                updateTabCounts(data.tabCounts);
            } else if (typeof data.unreadCount !== 'undefined') {
                updateSidebarBadge(data.unreadCount);
            }
            if (typeof callback === 'function') callback(data);
        }
    });
}

function markAsUnread(id, callback) {
    var csrf = getCsrf();
    var headers = Object.assign({ 'Content-Type': 'application/json' }, csrf.headers);

    fetch('/api/notifications/' + id + '/unread', {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            showNotificationToast('ทำเครื่องหมายว่ายังไม่ได้อ่านแล้ว', true);
            var row = document.getElementById('notif-row-' + id);
            if (row) {
                row.classList.add('bg-light', 'bg-opacity-50', 'fw-semibold', 'unread-row');
                row.dataset.notifIsRead = 'false';
            }
            if (data.tabCounts) {
                updateTabCounts(data.tabCounts);
            } else if (typeof data.unreadCount !== 'undefined') {
                updateSidebarBadge(data.unreadCount);
            }
            // Close modal if open
            var modalEl = document.getElementById('notifReaderModal');
            if (modalEl && window.bootstrap && bootstrap.Modal) {
                var modal = bootstrap.Modal.getInstance(modalEl);
                if (modal) modal.hide();
            }
            if (typeof callback === 'function') callback(data);
        }
    });
}

function markAllAsRead() {
    var csrf = getCsrf();
    var headers = Object.assign({ 'Content-Type': 'application/json' }, csrf.headers);

    fetch('/api/notifications/read-all', {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            showNotificationToast(data.message || 'ทำเครื่องหมายอ่านทั้งหมดแล้ว', true);
            if (data.tabCounts) {
                updateTabCounts(data.tabCounts);
            } else {
                updateSidebarBadge(0);
            }
            
            // Update table rows if on notifications page
            document.querySelectorAll('.notification-row.unread-row').forEach(function(row) {
                row.classList.remove('bg-light', 'bg-opacity-50', 'fw-semibold', 'unread-row');
                row.dataset.notifIsRead = 'true';
                var badge = row.querySelector('.badge.bg-primary');
                if (badge && badge.textContent.trim() === 'ใหม่') badge.remove();
            });

            // Update Topbar Dropdown list if open
            loadTopbarNotifications();
        }
    });
}

function toggleStar(id, btn) {
    var csrf = getCsrf();
    var headers = Object.assign({ 'Content-Type': 'application/json' }, csrf.headers);

    fetch('/api/notifications/' + id + '/star', {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            var icon = btn.querySelector('i');
            if (data.isStarred) {
                icon.className = 'fa-star fas text-warning';
                btn.setAttribute('title', 'ยกเลิกติดดาว');
            } else {
                icon.className = 'fa-star far text-muted';
                btn.setAttribute('title', 'ติดดาว');
                if (getCurrentTab() === 'starred') {
                    var row = document.getElementById('notif-row-' + id);
                    if (row) {
                        row.style.transition = 'all 0.3s';
                        row.style.opacity = '0';
                        setTimeout(function() { row.remove(); }, 300);
                    }
                }
            }
            if (data.tabCounts) {
                updateTabCounts(data.tabCounts);
            }
        }
    })
    .catch(function(err) {
        console.error('Star error:', err);
    });
}

function toggleImportant(id, btn) {
    var csrf = getCsrf();
    var headers = Object.assign({ 'Content-Type': 'application/json' }, csrf.headers);

    fetch('/api/notifications/' + id + '/important', {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            var icon = btn.querySelector('i');
            if (data.isImportant) {
                icon.className = 'fas fa-bookmark text-danger';
                btn.setAttribute('title', 'ยกเลิกสำคัญ');
            } else {
                icon.className = 'far fa-bookmark text-muted';
                btn.setAttribute('title', 'ทำเครื่องหมายว่าสำคัญ');
                if (getCurrentTab() === 'important') {
                    var row = document.getElementById('notif-row-' + id);
                    if (row) {
                        row.style.transition = 'all 0.3s';
                        row.style.opacity = '0';
                        setTimeout(function() { row.remove(); }, 300);
                    }
                }
            }
            if (data.tabCounts) {
                updateTabCounts(data.tabCounts);
            }
        }
    })
    .catch(function(err) {
        console.error('Important error:', err);
    });
}

function snoozeNotification(id, duration) {
    var csrf = getCsrf();
    var headers = Object.assign({ 'Content-Type': 'application/x-www-form-urlencoded' }, csrf.headers);

    fetch('/api/notifications/' + id + '/snooze?duration=' + encodeURIComponent(duration), {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            showNotificationToast(data.message || 'เลื่อนการแจ้งเตือนแล้ว', true);
            if (data.tabCounts) {
                updateTabCounts(data.tabCounts);
            } else if (typeof data.unreadCount !== 'undefined') {
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
    var headers = Object.assign({ 'Content-Type': 'application/json' }, csrf.headers);

    fetch('/api/notifications/' + id + '/unsnooze', {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            showNotificationToast('ยกเลิกการเลื่อนเตือนแล้ว', true);
            if (data.tabCounts) {
                updateTabCounts(data.tabCounts);
            }
            setTimeout(function() { window.location.reload(); }, 500);
        }
    });
}

function deleteNotification(id) {
    var csrf = getCsrf();
    var headers = Object.assign({ 'Content-Type': 'application/json' }, csrf.headers);

    fetch('/api/notifications/' + id + '/delete', {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            showNotificationToast(data.message || 'ย้ายไปถังขยะแล้ว', true);
            if (data.tabCounts) {
                updateTabCounts(data.tabCounts);
            } else if (typeof data.unreadCount !== 'undefined') {
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
    var headers = Object.assign({ 'Content-Type': 'application/json' }, csrf.headers);

    fetch('/api/notifications/' + id + '/restore', {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            showNotificationToast(data.message || 'กู้คืนแล้ว', true);
            if (data.tabCounts) {
                updateTabCounts(data.tabCounts);
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

function permanentDeleteNotification(id) {
    if (!confirm('ต้องการลบการแจ้งเตือนนี้ถาวรหรือไม่? (ไม่สามารถกู้คืนได้)')) return;

    var csrf = getCsrf();
    var headers = Object.assign({ 'Content-Type': 'application/json' }, csrf.headers);

    fetch('/api/notifications/' + id + '/permanent-delete', {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            showNotificationToast(data.message || 'ลบถาวรแล้ว', true);
            if (data.tabCounts) {
                updateTabCounts(data.tabCounts);
            }
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
    var headers = Object.assign({ 'Content-Type': 'application/json' }, csrf.headers);

    fetch('/api/notifications/empty-trash', {
        method: 'POST',
        headers: headers
    })
    .then(function(r) { return r.json(); })
    .then(function(data) {
        if (data.success) {
            showNotificationToast(data.message || 'ล้างถังขยะแล้ว', true);
            if (data.tabCounts) {
                updateTabCounts(data.tabCounts);
            }
            setTimeout(function() { window.location.reload(); }, 600);
        }
    });
}

// ================= Topbar Dropdown Loader =================

var cachedTopbarNotifs = [];

function loadTopbarNotifications() {
    var listContainer = document.getElementById('topbarNotifList');
    if (!listContainer) return;

    fetch('/api/notifications/recent?limit=5')
        .then(function(r) { return r.json(); })
        .then(function(data) {
            if (data.success) {
                cachedTopbarNotifs = data.notifications || [];
                updateSidebarBadge(data.unreadCount);

                if (cachedTopbarNotifs.length === 0) {
                    listContainer.innerHTML = '<div class="p-4 text-center text-muted">' +
                        '<i class="fas fa-bell-slash fa-2x mb-2 text-muted opacity-50"></i>' +
                        '<div class="small">ไม่มีการแจ้งเตือนใหม่</div></div>';
                    return;
                }

                var html = '';
                cachedTopbarNotifs.forEach(function(n, idx) {
                    var unreadClass = !n.isRead ? 'bg-light bg-opacity-75 fw-semibold' : '';
                    var unreadDot = !n.isRead ? '<span class="badge bg-primary rounded-circle p-1 me-1" style="width: 7px; height: 7px;"> </span>' : '';
                    var impBadge = n.isImportant ? '<span class="badge bg-danger ms-1" style="font-size: 0.65rem;">สำคัญ</span>' : '';
                    var typeBadge = '<span class="badge bg-light text-dark border ms-1" style="font-size: 0.65rem;">' + escapeHtml(n.typeLabel || 'ทั่วไป') + '</span>';
                    
                    html += '<div class="list-group-item list-group-item-action p-3 border-0 border-bottom topbar-notif-item ' + unreadClass + '" ' +
                            'data-topbar-notif-idx="' + idx + '" style="cursor: pointer; transition: background-color 0.15s;">' +
                                '<div class="d-flex align-items-start gap-2">' +
                                    '<div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0 mt-1" style="width: 32px; height: 32px; background: rgba(13,71,161,0.08);">' +
                                        '<i class="' + escapeHtml(n.iconClass || 'fas fa-bell text-primary') + '" style="font-size: 0.85rem;"></i>' +
                                    '</div>' +
                                    '<div class="flex-grow-1 min-width-0">' +
                                        '<div class="d-flex justify-content-between align-items-center mb-1">' +
                                            '<div class="d-flex align-items-center">' + unreadDot + '<strong class="text-dark small text-truncate" style="max-width: 170px;">' + escapeHtml(n.title) + '</strong>' + impBadge + '</div>' +
                                            '<small class="text-muted" style="font-size: 0.72rem;">' + escapeHtml(n.relativeTime) + '</small>' +
                                        '</div>' +
                                        '<p class="mb-0 text-muted small text-truncate" style="font-size: 0.8rem;">' + escapeHtml(n.message) + '</p>' +
                                    '</div>' +
                                '</div>' +
                            '</div>';
                });
                listContainer.innerHTML = html;
            }
        })
        .catch(function(err) {
            console.error('Failed to load topbar notifications:', err);
            listContainer.innerHTML = '<div class="p-3 text-center text-danger small">ไม่สามารถโหลดการแจ้งเตือนได้</div>';
        });
}

// ================= Event Delegation & Initializer =================

document.addEventListener('DOMContentLoaded', function () {
    // Topbar Dropdown Show Event
    var topbarContainer = document.getElementById('topbarNotificationContainer');
    if (topbarContainer) {
        topbarContainer.addEventListener('show.bs.dropdown', function () {
            loadTopbarNotifications();
        });
    }

    // Topbar Mark All Read Button
    var topbarMarkAllBtn = document.getElementById('topbarMarkAllReadBtn');
    if (topbarMarkAllBtn) {
        topbarMarkAllBtn.addEventListener('click', function (e) {
            e.preventDefault();
            e.stopPropagation();
            markAllAsRead();
        });
    }

    // Reader Modal Mark Unread Button
    var modalMarkUnreadBtn = document.getElementById('modalMarkUnreadBtn');
    if (modalMarkUnreadBtn) {
        modalMarkUnreadBtn.addEventListener('click', function (e) {
            e.preventDefault();
            var notifId = modalMarkUnreadBtn.dataset.notifId;
            if (notifId) {
                markAsUnread(notifId);
            }
        });
    }
});

/*
 * Global Click Delegation
 */
document.addEventListener('click', function (e) {
    // 1. Data-call buttons (e.g. data-call="markAllAsRead" or "emptyTrash")
    var callEl = e.target.closest('[data-call]');
    if (callEl) {
        e.preventDefault();
        var fn = callEl.dataset.call;
        if (fn === 'markAllAsRead') markAllAsRead();
        else if (fn === 'emptyTrash') emptyTrash();
        return;
    }

    // 2. Topbar item click -> open in reader modal
    var topbarItem = e.target.closest('.topbar-notif-item');
    if (topbarItem) {
        e.preventDefault();
        var idx = parseInt(topbarItem.dataset.topbarNotifIdx || '0', 10);
        if (cachedTopbarNotifs && cachedTopbarNotifs[idx]) {
            openReaderModal(cachedTopbarNotifs[idx]);
        }
        return;
    }

    // 3. Dropdown action items inside row
    var el = e.target.closest('[data-notif-action]');
    if (el) {
        var action = el.dataset.notifAction;
        e.preventDefault();
        e.stopPropagation();

        var id = parseInt(el.dataset.notifId || '0', 10);
        switch (action) {
            case 'star':            toggleStar(id, el); break;
            case 'important':       toggleImportant(id, el); break;
            case 'markRead':        markAsRead(id); break;
            case 'markUnread':      markAsUnread(id); break;
            case 'viewDetail':      openNotificationById(id); break;
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

    // 4. Notification table row click -> open Reader Modal
    var row = e.target.closest('[data-notif-id]:not([data-notif-action])');
    if (row && !row.classList.contains('topbar-notif-item')) {
        handleRowClick(e, parseInt(row.dataset.notifId || '0', 10), row);
    }
});
