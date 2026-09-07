/**
 * Global Omnibox Search & Dynamic Breadcrumbs
 */

function initGlobalApp() {
    initBreadcrumbs();
    initGlobalSearch();
    handleHashHighlight();
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initGlobalApp);
} else {
    initGlobalApp();
}

window.addEventListener('load', function() { handleHashHighlight(); });
window.addEventListener('hashchange', function() { handleHashHighlight(); });



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
        // คลังไฟล์ส่วนตัวของผู้ยื่นถูกยกเลิก
        // '/user/academic/storage': 'ที่เก็บไฟล์ของฉัน',
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
    var inFlight = null;

    // ต้องตรงกับ SearchQueryNormalizer.MIN_QUERY_LENGTH ฝั่งเซิร์ฟเวอร์ ไม่งั้น
    // ผู้ใช้จะเห็น "ไม่พบผลลัพธ์" ทั้งที่เซิร์ฟเวอร์แค่ปฏิเสธคำค้นที่สั้นเกินไป
    var MIN_QUERY_LENGTH = 2;

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

        if (q.length < MIN_QUERY_LENGTH) {
            if (inFlight) { inFlight.abort(); inFlight = null; }
            dropdown.innerHTML = '<div class="p-3 text-center text-muted small">'
                + '<i class="fas fa-keyboard me-2"></i>พิมพ์อย่างน้อย ' + MIN_QUERY_LENGTH + ' ตัวอักษร</div>';
            return;
        }

        dropdown.innerHTML = '<div class="p-3 text-center text-muted small"><i class="fas fa-spinner fa-spin me-2"></i>กำลังค้นหา...</div>';

        debounceTimer = setTimeout(function() {
            // ยกเลิกคำค้นก่อนหน้าเสมอ ที่ debounce 200ms การตอบกลับช้าของ "ป"
            // ทับผลของ "ประเมิน" ได้จริง และผู้ใช้จะเห็นผลลัพธ์ของคำที่ตัวเองลบไปแล้ว
            if (inFlight) { inFlight.abort(); }
            inFlight = new AbortController();
            var signal = inFlight.signal;

            fetch('/api/global-search?q=' + encodeURIComponent(q), { signal: signal })
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    renderSearchResults(data.results || [], q, data);
                })
                .catch(function(err) {
                    if (err && err.name === 'AbortError') return;
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

        // Enter with nothing highlighted means "search", not "do nothing" —
        // which is what it did before, and is the reflex of anyone who has used
        // a search box.
        if (e.key === 'Enter' && items.length === 0) {
            var typed = input.value.trim();
            if (typed.length >= MIN_QUERY_LENGTH) {
                e.preventDefault();
                window.location.href = '/search?q=' + encodeURIComponent(typed);
            }
            return;
        }

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
            } else {
                // Nothing picked with the arrows: take them to the full list
                // rather than swallowing the keystroke.
                var typed = input.value.trim();
                if (typed.length >= MIN_QUERY_LENGTH) {
                    window.location.href = '/search?q=' + encodeURIComponent(typed);
                }
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

    // ปิด dropdown เมื่อคลิกผลการค้นหา และหากเป็นลิงก์ในหน้าเดียวกันให้เลื่อนไปที่ข้อมูลทันที
    dropdown.addEventListener('click', function(e) {
        var link = e.target.closest('.search-result-item');
        if (!link) return;
        dropdown.classList.add('d-none');
        var href = link.getAttribute('href');
        if (href && href.indexOf('#') !== -1) {
            var parts = href.split('#');
            var targetPath = parts[0];
            var hash = '#' + parts[1];
            var currentFull = window.location.pathname + window.location.search;
            if (targetPath === window.location.pathname || targetPath === currentFull) {
                e.preventDefault();
                if (window.location.hash !== hash) {
                    window.location.hash = hash;
                }
                if (typeof window.handleHashHighlight === 'function') {
                    setTimeout(function() {
                        window.handleHashHighlight();
                    }, 40);
                }
            }
        }
    });

    /** The full results page for the current term. */
    function allResultsUrl(query) {
        return '/search?q=' + encodeURIComponent(query);
    }

    /**
     * แนบ Anchor Hash ต่อท้าย URL หากยังไม่มี เพื่อให้คลิกแล้วกระโดดตรงไปยังแถวข้อมูลทันที
     */
    function enrichSearchUrl(item) {
        var url = item.url;
        if (!url) return '#';
        if (url.indexOf('#') === -1 && item.entityType && item.entityId) {
            var prefixMap = {
                'STAFF_MEMBER': 'staff-',
                'SYSTEM_USER': 'user-',
                'COMMITTEE_MEMBER': 'committee-',
                'ACADEMIC_REQUEST': 'req-',
                'POSITION_REQUEST': 'pos-req-',
                'ADMIN_FILE': 'row-',
                'PUBLICATION': 'pub-'
            };
            var prefix = prefixMap[item.entityType];
            if (prefix) {
                url += '#' + prefix + item.entityId;
            }
        }
        return safeUrl(url);
    }

    function renderSearchResults(results, query, data) {
        if (results.length === 0) {
            dropdown.innerHTML = '<div class="p-3 text-center text-muted small">'
                + '<i class="fas fa-search me-2"></i>ไม่พบผลลัพธ์สำหรับ "<strong>'
                + escapeHtml(query) + '</strong>"</div>';
            return;
        }

        var html = '<div class="list-group list-group-flush py-1">';

        // The dropdown deliberately shows only a handful. Without a way through
        // to the full list, a search with more behind it looks like a search
        // that found only these.
        var approximate = !!(data && data.approximate);
        if (approximate) {
            html += '<div class="px-3 py-2 small text-muted border-bottom">'
                + '<i class="fas fa-lightbulb me-2"></i>ผลลัพธ์ใกล้เคียง</div>';
        }

        // จัดกลุ่มตาม Category เพื่อไม่ให้หัวข้อหมวดหมู่แสดงซ้ำซ้อนสลับไปมา
        var grouped = {};
        var categoryOrder = [];
        results.forEach(function(item) {
            var cat = item.category || 'ผลลัพธ์อื่น ๆ';
            if (!grouped[cat]) {
                grouped[cat] = [];
                categoryOrder.push(cat);
            }
            grouped[cat].push(item);
        });

        categoryOrder.forEach(function(cat) {
            html += '<div class="dropdown-header px-3 py-1 text-uppercase fw-bold text-muted" style="font-size: 0.72rem; letter-spacing: 0.5px;">' + escapeHtml(cat) + '</div>';

            grouped[cat].forEach(function(item) {
                var targetUrl = enrichSearchUrl(item);
                html += '<a href="' + escapeAttr(targetUrl) + '" class="list-group-item list-group-item-action d-flex align-items-center justify-content-between px-3 py-2 border-0 search-result-item" style="text-decoration:none;">';
                html += '<div class="d-flex align-items-center gap-2 min-width-0 flex-grow-1 overflow-hidden me-2">';
                html += '<div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 28px; height: 28px; background: rgba(13,71,161,0.08);">';
                html += '<i class="' + escapeAttr(item.icon || 'fas fa-link') + '" style="font-size: 0.85rem;"></i>';
                html += '</div>';
                html += '<div class="min-width-0 flex-grow-1 overflow-hidden">';
                html += '<div class="text-dark fw-semibold text-truncate" style="font-size: 0.85rem;" title="' + escapeAttr(item.title) + '">' + highlightKeyword(item.title, query) + '</div>';
                if (item.subtitle) {
                    html += '<small class="text-muted text-truncate d-block" style="font-size: 0.75rem;" title="' + escapeAttr(item.subtitle) + '">' + escapeHtml(item.subtitle) + '</small>';
                }
                html += '</div></div>';

                if (item.badge) {
                    html += '<span class="badge ' + escapeAttr(item.badgeClass || 'bg-light text-dark') + ' rounded-pill flex-shrink-0 ms-auto" style="font-size: 0.7rem;">' + escapeHtml(item.badge) + '</span>';
                }

                html += '</a>';
            });
        });

        html += '<a href="' + escapeAttr(allResultsUrl(query)) + '"'
            + ' class="list-group-item list-group-item-action text-center py-2 border-0 border-top'
            + ' search-result-item small fw-semibold text-primary"'
            + ' style="text-decoration:none;">'
            + 'ดูผลลัพธ์ทั้งหมด <i class="fas fa-arrow-right ms-1"></i></a>';

        html += '</div>';
        dropdown.innerHTML = html;
        currentSelectedIndex = -1;
    }

    /**
     * ปลอดภัยเฉพาะบริบท "ข้อความ" เท่านั้น
     *
     * การ serialize text node จะ escape ให้แค่ & < > — เครื่องหมายคำพูดไม่ถูกแตะ
     * จึงใช้กับค่าที่จะไปอยู่ในแอตทริบิวต์ไม่ได้ ต้องใช้ escapeAttr แทน
     */
    function escapeHtml(str) {
        if (!str) return '';
        var div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    /** สำหรับค่าที่ไปอยู่ในแอตทริบิวต์ที่คร่อมด้วยเครื่องหมายคำพูด */
    function escapeAttr(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    /**
     * ยอมเฉพาะ URL ที่เป็น path ภายในหรือ http(s) เท่านั้น
     *
     * ตอนนี้ url ทุกตัวยังเป็นค่าคงที่จากเซิร์ฟเวอร์ แต่ไฟล์แนบชนิดลิงก์
     * (AcademicAttachment.fileType = "LINK", V20) เก็บ URL ที่ผู้ยื่นกรอกเอง
     * ยาวได้ถึง 2048 ตัวอักษร เมื่อค่าพวกนั้นเข้ามาอยู่ในผลการค้นหา
     * href="javascript:..." จะกลายเป็นสคริปต์ที่รันตอนแอดมินคลิก
     */
    function safeUrl(url) {
        if (!url) return '#';
        return /^(https?:\/\/|\/(?!\/))/i.test(String(url)) ? String(url) : '#';
    }

    function highlightKeyword(text, kw) {
        if (!text) return '';
        var escaped = escapeHtml(text);
        if (!kw) return escaped;
        var regex = new RegExp('(' + kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ')', 'gi');
        return escaped.replace(regex, '<span class="bg-warning bg-opacity-50 text-dark px-1 rounded">$1</span>');
    }
}

/**
 * Find target element by hash with robust multi-strategy fallback:
 * - Exact query selector / getElementById
 * - Alias prefixes (#req-X -> #pending-req-X, #pos-req-X)
 * - Data attribute matching [data-id], [data-request-id], [data-staff-id], [data-user-id], [data-pub-id]
 * - Tr matching
 */
function findTargetByHash(rawHash) {
    if (!rawHash || rawHash.length <= 1) return null;
    var target = null;
    try {
        target = document.querySelector(rawHash);
    } catch (e) {}

    if (!target) {
        try {
            var rawId = decodeURIComponent(rawHash.substring(1));
            target = document.getElementById(rawId);
        } catch (e2) {}
    }

    if (!target) {
        var hashId = rawHash.replace(/^#/, '');
        if (hashId.startsWith('req-')) {
            var numId = hashId.substring(4);
            target = document.getElementById('pending-req-' + numId)
                || document.querySelector('[data-request-id="' + numId + '"]')
                || document.querySelector('tr[id*="req-' + numId + '"]');
        } else if (hashId.startsWith('pending-req-')) {
            var numId = hashId.substring(12);
            target = document.getElementById('req-' + numId)
                || document.querySelector('[data-request-id="' + numId + '"]');
        } else if (hashId.startsWith('pos-req-')) {
            var numId = hashId.substring(8);
            target = document.getElementById('req-' + numId)
                || document.querySelector('[data-request-id="' + numId + '"]');
        } else if (hashId.startsWith('staff-')) {
            var numId = hashId.substring(6);
            target = document.querySelector('[data-staff-id="' + numId + '"]')
                || document.querySelector('tr[id*="staff-' + numId + '"]');
        } else if (hashId.startsWith('user-')) {
            var numId = hashId.substring(5);
            target = document.querySelector('[data-user-id="' + numId + '"]')
                || document.querySelector('tr[id*="user-' + numId + '"]');
        } else if (hashId.startsWith('pub-')) {
            var numId = hashId.substring(4);
            target = document.querySelector('[data-pub-id="' + numId + '"]')
                || document.querySelector('tr[id*="pub-' + numId + '"]');
        }
    }

    return target;
}

/**
 * Handle URL hash deep-link:
 * - Detects #staff-X, #user-X, #req-X, #row-X, #committee-X, #pub-X with multi-strategy fallback
 * - Retries finding target element if table or content is still rendering
 * - Automatically activates enclosing Bootstrap tab or accordion if hidden
 * - Smoothly centers element in viewport
 * - Flashes vibrant 3-stage pulse animation on row and cells (3.5s duration)
 */
function handleHashHighlight(attempt) {
    attempt = attempt || 1;
    var rawHash = window.location.hash;
    if (!rawHash || rawHash.length <= 1) return;

    var target = findTargetByHash(rawHash);

    if (!target) {
        if (attempt < 8) {
            setTimeout(function() {
                handleHashHighlight(attempt + 1);
            }, attempt * 100);
        }
        return;
    }

    // 1. If target is inside an inactive Bootstrap tab pane, activate that tab first
    try {
        var tabPane = target.closest('.tab-pane');
        if (tabPane && !tabPane.classList.contains('active')) {
            var tabId = tabPane.getAttribute('id');
            if (tabId) {
                var tabTrigger = document.querySelector('[data-bs-target="#' + tabId + '"], [href="#' + tabId + '"]');
                if (tabTrigger) {
                    if (typeof bootstrap !== 'undefined' && bootstrap.Tab) {
                        var tab = bootstrap.Tab.getOrCreateInstance(tabTrigger);
                        tab.show();
                    } else {
                        tabTrigger.click();
                    }
                }
            }
        }
    } catch (errTab) {}

    // 2. If target is inside a collapsed Bootstrap accordion/element, expand it
    try {
        var collapseEl = target.closest('.collapse');
        if (collapseEl && !collapseEl.classList.contains('show')) {
            if (typeof bootstrap !== 'undefined' && bootstrap.Collapse) {
                var bsCollapse = bootstrap.Collapse.getOrCreateInstance(collapseEl);
                bsCollapse.show();
            }
        }
    } catch (errCol) {}

    // 3. Smooth scroll to center & apply pulse highlight animation
    setTimeout(function() {
        try {
            target.scrollIntoView({ behavior: 'smooth', block: 'center' });
        } catch (errScroll) {
            target.scrollIntoView();
        }

        // Find child cells if table row
        var cells = [];
        if (target.matches && target.matches('tr')) {
            cells = Array.from(target.querySelectorAll('td, th'));
        } else if (target.tagName && target.tagName.toLowerCase() === 'tr') {
            cells = Array.from(target.getElementsByTagName('td')).concat(Array.from(target.getElementsByTagName('th')));
        }

        // Remove old highlight classes first
        target.classList.remove('search-target-highlight');
        cells.forEach(function(c) {
            c.classList.remove('search-target-highlight-cell');
        });

        // Trigger reflow to restart CSS animation cleanly
        void target.offsetWidth;

        // Apply highlight classes
        target.classList.add('search-target-highlight');
        cells.forEach(function(c) {
            c.classList.add('search-target-highlight-cell');
        });

        // Web Animations API: True 3-Pulse Flashing Animation (3.5s duration)
        try {
            var isDark = document.documentElement.getAttribute('data-theme') === 'dark'
                || document.body.classList.contains('dark-theme');

            var bgBright = isDark ? 'rgba(234, 179, 8, 0.55)' : '#fde047';
            var bgDim = isDark ? 'rgba(234, 179, 8, 0.15)' : 'rgba(254, 240, 138, 0.25)';
            var bgSoft = isDark ? 'rgba(234, 179, 8, 0.25)' : 'rgba(254, 240, 138, 0.40)';
            var borderColor = isDark ? '#facc15' : '#d97706';
            var borderDim = isDark ? 'rgba(250, 204, 21, 0.3)' : 'rgba(217, 119, 6, 0.3)';

            // Animate target row outline (3 bright flashes over 3.5 seconds)
            if (typeof target.animate === 'function') {
                target.animate([
                    { outline: '3px solid ' + borderColor, outlineOffset: '-2px', offset: 0.0 },
                    { outline: '3px solid ' + borderDim, outlineOffset: '-2px', offset: 0.15 },
                    { outline: '3px solid ' + borderColor, outlineOffset: '-2px', offset: 0.30 },
                    { outline: '3px solid ' + borderDim, outlineOffset: '-2px', offset: 0.45 },
                    { outline: '3px solid ' + borderColor, outlineOffset: '-2px', offset: 0.60 },
                    { outline: '2px solid ' + borderDim, outlineOffset: '-2px', offset: 0.80 },
                    { outline: '0px solid transparent', outlineOffset: '-2px', offset: 1.0 }
                ], {
                    duration: 3500,
                    easing: 'ease-out'
                });
            }

            // Animate each cell background & border
            if (cells.length > 0) {
                cells.forEach(function(cell, idx) {
                    if (typeof cell.animate === 'function') {
                        var isFirst = (idx === 0);
                        var isLast = (idx === cells.length - 1);
                        var lBorder = isFirst ? '4px' : '0px';
                        var rBorder = isLast ? '-4px' : '0px';
                        var lDim = isFirst ? '2px' : '0px';
                        var rDim = isLast ? '-2px' : '0px';

                        var shadowBright = 'inset ' + lBorder + ' 3px 0 0 ' + borderColor + ', inset ' + rBorder + ' -3px 0 0 ' + borderColor;
                        var shadowDim = 'inset ' + lDim + ' 1px 0 0 ' + borderDim + ', inset ' + rDim + ' -1px 0 0 ' + borderDim;

                        cell.animate([
                            { backgroundColor: bgBright, boxShadow: shadowBright, offset: 0.0 },
                            { backgroundColor: bgDim, boxShadow: shadowDim, offset: 0.15 },
                            { backgroundColor: bgBright, boxShadow: shadowBright, offset: 0.30 },
                            { backgroundColor: bgDim, boxShadow: shadowDim, offset: 0.45 },
                            { backgroundColor: bgBright, boxShadow: shadowBright, offset: 0.60 },
                            { backgroundColor: bgSoft, boxShadow: shadowDim, offset: 0.80 },
                            { backgroundColor: 'transparent', boxShadow: 'none', offset: 1.0 }
                        ], {
                            duration: 3500,
                            easing: 'ease-out'
                        });
                    }
                });
            }
        } catch (animErr) {
            console.warn('Web Animations API fallback notice:', animErr);
        }

        // Clean up classes after 3.8 seconds
        setTimeout(function() {
            target.classList.remove('search-target-highlight');
            cells.forEach(function(c) {
                c.classList.remove('search-target-highlight-cell');
            });
        }, 3800);
    }, 120);
}

window.handleHashHighlight = handleHashHighlight;



