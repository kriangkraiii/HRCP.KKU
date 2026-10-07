/**
 * Publication picker.
 *
 * Replaces typing publication details by hand: the professor picks from their
 * own publications — synced from Scopus and harvested nightly from Crossref,
 * OpenAlex, DBLP, ThaiJO and KKU IR — and the selected rows are written into
 * the form. Each row shows where it came from and the list can be narrowed to
 * one source.
 *
 * The list is always fetched from /api/my/publications, which is scoped to the
 * signed-in user on the server — this script has no way to request anyone
 * else's publications, and never sends an owner id.
 *
 * Usage from a form:
 *   ScopusPicker.open({
 *     container:      'assocResearchRows',      // element holding the rows
 *     titlePrefix:    'assoc_research_working', // <input name="{prefix}_{n}">
 *     quartilePrefix: 'assoc_m3_quartile',      // optional radio group
 *     yearPrefix:     'assoc_used_research_year', // optional year input
 *     addRow: function () { ... }               // called when more rows are needed
 *   });
 *
 * Optional, for forms whose rows are not named {prefix}_{n}:
 *   titleName: function (n) { return 'des_research' + n; },
 *   trackIds:  false,  // do not attach the publication id (the work is put forward on another document)
 *   onRow:     function (n, row) { ... }  // fill the row's other columns from {quartile, dataSource, year}
 */
(function (window, document) {
    'use strict';

    var MODAL_ID = 'scopusPickerModal';

    /** dataSource ของผลงาน → ชื่อที่แสดง (ลำดับนี้คือลำดับในตัวกรอง) */
    var SOURCES = [
        ['SCOPUS', 'Scopus'],
        ['OPENALEX', 'OpenAlex'],
        ['CROSSREF', 'Crossref'],
        ['DBLP', 'DBLP'],
        ['THAIJO', 'ThaiJO'],
        ['KKUIR', 'KKU IR']
    ];

    function sourceLabel(code) {
        for (var i = 0; i < SOURCES.length; i++) {
            if (SOURCES[i][0] === code) return SOURCES[i][1];
        }
        return code || 'Scopus';
    }
    var state = {
        target: null,
        publications: [],
        selected: new Set(),
        loaded: false,
        /** True while the picker is writing rows, so its own events are ignored. */
        filling: false
    };

    // ---------------------------------------------------------------- markup

    function ensureModal() {
        var existing = document.getElementById(MODAL_ID);
        if (existing) return existing;

        var wrapper = document.createElement('div');
        wrapper.innerHTML = [
            '<div class="modal fade" id="' + MODAL_ID + '" tabindex="-1" aria-hidden="true">',
            '  <div class="modal-dialog modal-xl modal-dialog-scrollable">',
            '    <div class="modal-content">',
            '      <div class="modal-header">',
            '        <h5 class="modal-title"><i class="fas fa-book"></i> เลือกผลงานจากฐานข้อมูลผลงาน</h5>',
            '        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="ปิด"></button>',
            '      </div>',
            '      <div class="modal-body">',
            '        <div class="row g-2 mb-3">',
            '          <div class="col-md-5">',
            '            <input type="text" class="form-control" id="spSearch" placeholder="ค้นหาชื่อเรื่อง / วารสาร / DOI">',
            '          </div>',
            '          <div class="col-md-3">',
            '            <select class="form-select" id="spSource" aria-label="แหล่งข้อมูล">',
            '              <option value="">ทุกแหล่งข้อมูล</option>',
            SOURCES.map(function (s) { return '              <option value="' + s[0] + '">' + s[1] + '</option>'; }).join(''),
            '            </select>',
            '          </div>',
            '          <div class="col-md-2">',
            '            <input type="number" class="form-control" id="spYearFrom" placeholder="ปีเริ่มต้น">',
            '          </div>',
            '          <div class="col-md-2">',
            '            <input type="number" class="form-control" id="spYearTo" placeholder="ปีสิ้นสุด">',
            '          </div>',
            '        </div>',
            '        <div class="d-flex justify-content-between align-items-center mb-2">',
            '          <small class="text-muted" id="spCount">กำลังโหลด…</small>',
            '          <div>',
            '            <button type="button" class="btn btn-sm btn-outline-secondary" id="spSelectAll">เลือกทั้งหมดในหน้านี้</button>',
            '            <button type="button" class="btn btn-sm btn-outline-secondary" id="spClear">ล้างที่เลือก</button>',
            '          </div>',
            '        </div>',
            '        <div class="alert alert-info py-2 small">',
            '          <i class="fas fa-info-circle"></i> ',
            '          รายการที่เลือกจะถูกใส่ในช่องว่างช่องแรก และ<strong>ยังแก้ไขข้อความได้เอง</strong>ทีหลัง',
            '          — ข้อมูลที่กรอกไว้แล้วจะไม่ถูกเขียนทับ หากต้องการกรอกเองทั้งหมดก็ไม่ต้องใช้หน้านี้',
            '          <br>ผลงานในคำร้องที่ยังไม่มีมติสภามหาวิทยาลัยจะไม่แสดงในรายการนี้ — ผลงานจากคำร้องที่มีมติแล้วเลือกได้ แต่ต้องระบุว่า "เคยใช้" ในเอกสารที่ ๑',
            '        </div>',
            '        <div id="spList" class="list-group"></div>',
            '      </div>',
            '      <div class="modal-footer">',
            '        <span class="me-auto text-muted small" id="spSelectedCount">เลือกแล้ว 0 รายการ</span>',
            '        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">ยกเลิก</button>',
            '        <button type="button" class="btn btn-primary" id="spConfirm">ใส่ในแบบฟอร์ม</button>',
            '      </div>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('');

        var modal = wrapper.firstChild;
        document.body.appendChild(modal);
        wireEvents(modal);
        return modal;
    }

    function wireEvents(modal) {
        var search = modal.querySelector('#spSearch');
        var yearFrom = modal.querySelector('#spYearFrom');
        var yearTo = modal.querySelector('#spYearTo');
        var source = modal.querySelector('#spSource');

        var reload = debounce(function () { load(); }, 300);
        search.addEventListener('input', reload);
        source.addEventListener('change', reload);
        yearFrom.addEventListener('change', reload);
        yearTo.addEventListener('change', reload);

        modal.querySelector('#spSelectAll').addEventListener('click', function () {
            state.publications.forEach(function (p) { state.selected.add(p.id); });
            render();
        });
        modal.querySelector('#spClear').addEventListener('click', function () {
            state.selected.clear();
            render();
        });
        modal.querySelector('#spConfirm').addEventListener('click', applySelection);
    }

    // ------------------------------------------------------------------ data

    function load() {
        var modal = document.getElementById(MODAL_ID);
        var params = new URLSearchParams();
        var q = modal.querySelector('#spSearch').value.trim();
        var from = modal.querySelector('#spYearFrom').value.trim();
        var to = modal.querySelector('#spYearTo').value.trim();
        var source = modal.querySelector('#spSource').value;

        if (q) params.set('q', q);
        if (source) params.set('source', source);
        if (from) params.set('year_from', from);
        if (to) params.set('year_to', to);
        params.set('size', '200');

        setStatus('กำลังโหลด…');

        fetch('/api/my/publications?' + params.toString(), {
            headers: { 'Accept': 'application/json' },
            credentials: 'same-origin'
        })
            .then(function (r) {
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            })
            .then(function (body) {
                state.publications = body.data || [];
                state.loaded = true;
                if (!body.linked) {
                    setStatus('');
                    showNotice('ไม่พบข้อมูลของท่านในระบบ Fund Management — ' +
                        'อีเมลที่ใช้เข้าสู่ระบบอาจไม่ตรงกับที่ลงทะเบียนไว้ กรุณาแจ้งผู้ดูแลระบบ');
                    return;
                }
                render();
            })
            .catch(function (e) {
                console.error('Scopus picker load failed:', e);
                setStatus('');
                showNotice('โหลดรายการผลงานไม่สำเร็จ กรุณาลองใหม่อีกครั้ง');
            });
    }

    // ----------------------------------------------------------------- views

    function render() {
        var list = document.getElementById('spList');
        list.innerHTML = '';

        if (!state.publications.length) {
            showNotice('ไม่พบผลงานที่ตรงกับเงื่อนไข');
            setStatus('พบ 0 รายการ');
            updateSelectedCount();
            return;
        }

        state.publications.forEach(function (p) {
            list.appendChild(renderRow(p));
        });
        setStatus('พบ ' + state.publications.length + ' รายการ');
        updateSelectedCount();
    }

    function renderRow(p) {
        var item = document.createElement('label');
        item.className = 'list-group-item d-flex gap-2 align-items-start';

        var cb = document.createElement('input');
        cb.className = 'form-check-input flex-shrink-0 mt-1';
        cb.type = 'checkbox';
        cb.checked = state.selected.has(p.id);
        cb.addEventListener('change', function () {
            if (cb.checked) state.selected.add(p.id);
            else state.selected.delete(p.id);
            updateSelectedCount();
        });

        var body = document.createElement('div');
        body.innerHTML =
            '<div class="fw-semibold">' + escapeHtml(p.title || '(ไม่มีชื่อเรื่อง)') + '</div>' +
            '<div class="small text-muted">' +
            '<span class="badge text-bg-light border me-1" title="แหล่งข้อมูล">' + escapeHtml(sourceLabel(p.dataSource)) + '</span>' +
            escapeHtml(p.publicationName || '') +
            (p.year ? ' · ' + p.year : '') +
            (p.quartile ? ' · <span class="badge bg-success">' + escapeHtml(p.quartile) + '</span>' : '') +
            (typeof p.citedBy === 'number' ? ' · อ้างอิง ' + p.citedBy : '') +
            (p.type ? ' · ' + escapeHtml(p.type) : '') +
            '</div>' +
            (p.doi ? '<div class="small text-muted">DOI: ' + escapeHtml(p.doi) + '</div>' : '');

        item.appendChild(cb);
        item.appendChild(body);
        return item;
    }

    function showNotice(message) {
        var list = document.getElementById('spList');
        list.innerHTML = '<div class="alert alert-warning mb-0">' + escapeHtml(message) + '</div>';
    }

    function setStatus(text) {
        var el = document.getElementById('spCount');
        if (el) el.textContent = text;
    }

    function updateSelectedCount() {
        var el = document.getElementById('spSelectedCount');
        if (el) el.textContent = 'เลือกแล้ว ' + state.selected.size + ' รายการ';
    }

    // ------------------------------------------------------------ apply

    /**
     * Writes the ticked publications into the form.
     *
     * Citations are formatted server-side so the wording stays identical to what
     * the generated Word document will contain.
     */
    function applySelection() {
        var target = state.target;
        if (!target || !state.selected.size) {
            closeModal();
            return;
        }

        var ids = Array.from(state.selected);
        fetch('/api/my/publications/citations?ids=' + ids.join(','), {
            headers: { 'Accept': 'application/json' },
            credentials: 'same-origin'
        })
            .then(function (r) { return r.json(); })
            .then(function (body) {
                var rows = body.data || [];
                fillRows(target, rows);
                closeModal();
            })
            .catch(function (e) {
                console.error('Scopus picker apply failed:', e);
                alert('ไม่สามารถใส่ข้อมูลลงแบบฟอร์มได้ กรุณาลองใหม่');
            });
    }

    /**
     * Fills one row per publication.
     *
     * Filling starts at the first empty slot and rows are appended as needed, so
     * anything the professor already typed is left alone. Every value lands in a
     * normal input — it stays editable afterwards, and a professor who prefers
     * to type everything by hand can simply ignore the picker.
     */
    function fillRows(target, rows) {
        // setValue fires a synthetic input event so the auto-draft picks the row
        // up; the manual-edit listener above must not read that as the professor
        // retyping the line.
        state.filling = true;
        try {
            fillRowsInternal(target, rows);
        } finally {
            state.filling = false;
        }
    }

    function titleNameOf(target, n) {
        return target.titleName ? target.titleName(n) : target.titlePrefix + '_' + n;
    }

    function fillRowsInternal(target, rows) {
        var startAt = firstEmptyIndex(target);
        var needed = startAt + rows.length - 1;

        // Grow the container until every publication has a row to land in.
        // The attempt cap matters: if addRow ever stops producing inputs under
        // the expected name, an uncapped loop would hang the browser tab.
        var attempts = 0;
        while (countRows(target) < needed && typeof target.addRow === 'function') {
            var before = countRows(target);
            target.addRow();
            if (countRows(target) === before || ++attempts > 500) {
                console.warn('Scopus picker: addRow stopped adding rows for', target.titlePrefix);
                break;
            }
        }

        var filled = 0;
        rows.forEach(function (row, i) {
            var n = startAt + i;
            if (!setValue(titleNameOf(target, n), row.citation)) {
                return;
            }
            filled++;
            if (target.trackIds !== false) {
                rememberPublicationId(target.titlePrefix, n, row.id);
            }
            if (typeof target.onRow === 'function') {
                target.onRow(n, row);
            }

            if (target.quartilePrefix && row.quartile) {
                var radio = document.querySelector(
                    '[name="' + target.quartilePrefix + '_' + n + '"][value="' + row.quartile + '"]');
                if (radio) radio.checked = true;
            }
            if (target.yearPrefix && row.year) {
                // Thai forms use the Buddhist year.
                setValue(target.yearPrefix + '_' + n, String(row.year + 543));
            }
        });

        if (filled < rows.length) {
            alert('ใส่ได้ ' + filled + ' จาก ' + rows.length + ' รายการ — ' +
                'กรุณากด "เพิ่มงานวิจัย" เพื่อเพิ่มช่องแล้วเลือกอีกครั้ง');
        } else {
            toast(filled + ' รายการถูกใส่ในแบบฟอร์มแล้ว (แก้ไขข้อความได้ตามต้องการ)');
        }
    }

    function firstEmptyIndex(target) {
        for (var n = 1; n <= 200; n++) {
            var el = document.querySelector('[name="' + titleNameOf(target, n) + '"]');
            if (!el) return n;
            if (!el.value.trim()) return n;
        }
        return 1;
    }

    function countRows(target) {
        var n = 0;
        while (document.querySelector('[name="' + titleNameOf(target, n + 1) + '"]')) n++;
        return n;
    }

    /**
     * Keeps the publication id behind a citation line, in a hidden field beside it.
     *
     * The citation itself stays free text the professor may reword, which is the
     * point — but the wording alone cannot say which paper a line is, so the
     * server had no way to know what a request actually puts forward, and the rule
     * that the same work cannot be submitted twice had nothing to check (GAP-11).
     * The id now travels with the form like any other field.
     *
     * The field is created here rather than in the eleven row templates: every
     * group would otherwise need the same edit, and a group whose author forgot it
     * would lose the link silently.
     */
    function rememberPublicationId(titlePrefix, n, publicationId) {
        var name = titlePrefix + '_scopus_id_' + n;
        var hidden = document.querySelector('[name="' + name + '"]');
        if (!hidden) {
            var title = document.querySelector('[name="' + titlePrefix + '_' + n + '"]');
            if (!title) return;
            hidden = document.createElement('input');
            hidden.type = 'hidden';
            hidden.name = name;
            title.insertAdjacentElement('afterend', hidden);
        }
        hidden.value = publicationId == null ? '' : String(publicationId);
    }

    /** The hidden id field paired with a title input named {prefix}_{n}. */
    function findIdFieldFor(titleName) {
        var m = /^(.+)_(\d+)$/.exec(titleName);
        if (!m) return null;
        return document.querySelector('[name="' + m[1] + '_scopus_id_' + m[2] + '"]');
    }

    /**
     * Drops the id when a filled line is typed over by hand.
     *
     * A line the professor rewrote from scratch is a different piece of work, and
     * leaving the old id attached would spend the wrong publication. Clearing on
     * any manual edit errs towards under-locking — the cost is a duplicate staff
     * can see, rather than a paper locked away that nobody ever submitted.
     *
     * Delegated from document and registered once: rows are created long after
     * this file runs, and there are eleven groups of them.
     */
    document.addEventListener('input', function (event) {
        var el = event.target;
        if (!el || !el.name || el.type === 'hidden') return;
        var hidden = findIdFieldFor(el.name);
        if (hidden && hidden.value && !state.filling) {
            hidden.value = '';
        }
    });

    /** Returns false when no such field exists, so the caller can report a shortfall. */
    function setValue(name, value) {
        var el = document.querySelector('[name="' + name + '"]');
        if (!el) return false;
        el.value = value;
        // Let the auto-draft listener notice the change and save it.
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        return true;
    }

    // ------------------------------------------------------------- metrics

    /**
     * Fills the Scopus paper count, citation total and h-index.
     * These are computed from the same synced data the picker lists.
     */
    function fillMetrics(prefix) {
        fetch('/api/my/publications/metrics', {
            headers: { 'Accept': 'application/json' },
            credentials: 'same-origin'
        })
            .then(function (r) { return r.json(); })
            .then(function (m) {
                setValue(prefix + '_scopus_stories_count', m.papers);
                setValue(prefix + '_scopus_citation_count', m.citations);
                setValue(prefix + '_h_index', m.hIndex);
                toast('เติมตัวเลข Scopus แล้ว (' + m.papers + ' เรื่อง, อ้างอิง ' +
                    m.citations + ', h-index ' + m.hIndex + ')');
            })
            .catch(function (e) {
                console.error('Scopus metrics failed:', e);
                alert('ดึงตัวเลข Scopus ไม่สำเร็จ');
            });
    }

    // --------------------------------------------------------------- helpers

    function openModal() {
        var el = ensureModal();
        if (window.bootstrap && window.bootstrap.Modal) {
            window.bootstrap.Modal.getOrCreateInstance(el).show();
        } else {
            el.classList.add('show');
            el.style.display = 'block';
        }
    }

    function closeModal() {
        var el = document.getElementById(MODAL_ID);
        if (!el) return;
        if (window.bootstrap && window.bootstrap.Modal) {
            var inst = window.bootstrap.Modal.getInstance(el);
            if (inst) inst.hide();
        } else {
            el.classList.remove('show');
            el.style.display = 'none';
        }
    }

    function toast(message) {
        var box = document.createElement('div');
        box.className = 'alert alert-success position-fixed';
        box.style.cssText = 'bottom:76px;right:24px;z-index:2000;box-shadow:0 4px 12px rgba(0,0,0,.15);border-radius:10px;';
        box.textContent = message;
        document.body.appendChild(box);
        setTimeout(function () { box.remove(); }, 4000);
    }

    function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function debounce(fn, ms) {
        var t;
        return function () {
            clearTimeout(t);
            t = setTimeout(fn, ms);
        };
    }

    window.ScopusPicker = {
        open: function (target) {
            state.target = target;
            state.selected.clear();
            openModal();
            load();
        },
        fillMetrics: fillMetrics,

        /**
         * Puts a publication id back beside a citation line.
         *
         * Exposed for the form's own restore step. The hidden fields live only in
         * the DOM the picker built, so reopening a saved form used to come back
         * without them: the next save then posted a citation with no id attached
         * and the server, correctly reading the form as the truth, dropped the
         * link — after which the work counted as never submitted and could be put
         * forward again. Restoring the field is what keeps a reopened form saying
         * the same thing it said when it was closed.
         */
        rememberId: rememberPublicationId
    };
})(window, document);
