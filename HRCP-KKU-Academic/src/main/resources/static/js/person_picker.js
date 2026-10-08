/**
 * ตัวค้นหาชื่อผู้ลงนาม — ช่องชื่อผู้ลงนามในแบบฟอร์มเลือกได้เฉพาะบัญชีในระบบ ไม่ใช่ข้อความที่พิมพ์เอง
 *
 * ใช้กับ <input data-person-picker name="dean_name" data-role="DEAN" data-position-field="dean_position"
 *              [data-allow-external]>
 *   - พิมพ์เพื่อค้นหา (/api/people) แล้วเลือกจากรายการ — ปล่อยช่องโดยไม่เลือก ชื่อกลับเป็นคนที่เลือกไว้
 *   - เก็บรหัสบัญชีในช่องซ่อน "<name>__signer" (DocumentFieldOwnership.SIGNER_ID_SUFFIX) คู่กับชื่อ
 *     คนที่เลือกคือผู้ลงนามตำแหน่งนั้น (SignatureWorkflowService.signersNamedInForm)
 *   - เติมช่องตำแหน่ง (data-position-field) จากตำแหน่งที่บันทึกไว้ในบัญชี — ยังแก้เองได้
 *   - data-allow-external: เพิ่มผู้ลงนามจากนอก มข. ด้วยอีเมลได้ (ExternalSignerService)
 *   - data-picker-hint: คำอธิบายของช่อง แทนข้อความตั้งต้น (เช่น ช่องที่คนที่เลือกไปลงนามในเอกสารฉบับอื่น)
 *     แสดงเป็นปุ่ม (i) ข้างป้ายกำกับ (info_tip.js) — บรรทัดใต้ช่องเหลือไว้แค่คำเตือนและปุ่มเพิ่มผู้ลงนามภายนอก
 *   - data-distinct-group: ช่องในกลุ่มเดียวกันเลือกคนซ้ำกันไม่ได้ (เช่น กรรมการสามคน)
 *   - ช่องที่สร้างทีหลัง (แถวที่เพิ่มเอง) เรียก window.PersonPicker.attach(input)
 *
 * ดู docs/PLAN-signer-picker.md
 */
(function () {
    'use strict';

    var SUFFIX = '__signer';
    var DEFAULT_HINT = 'เลือกจากรายชื่อในระบบ — ผู้ที่เลือกคือผู้ลงนามตำแหน่งนี้';
    var DUPLICATE_HINT = 'คนนี้ถูกเลือกในช่องอื่นแล้ว — แต่ละช่องต้องเป็นคนละคน กรุณาเลือกใหม่ ';

    function csrfToken() {
        var el = document.querySelector('input[name="_csrf"]') || document.querySelector('meta[name="_csrf"]');
        return el ? (el.value || el.content || '') : '';
    }

    function el(tag, className, text) {
        var node = document.createElement(tag);
        if (className) node.className = className;
        if (text != null) node.textContent = text;
        return node;
    }

    /** ป้ายกำกับของช่อง — ที่วางปุ่ม (i) คำอธิบาย */
    function labelFor(input) {
        if (input.id) {
            var byFor = document.querySelector('label[for="' + input.id + '"]');
            if (byFor) return byFor;
        }
        var box = input.closest('[class*="col-"], .mb-3, .form-group, td');
        var label = box && box.querySelector('label');
        if (label) return label;
        var row = input.closest('.row');
        return row ? row.querySelector('label') : null;
    }

    // context: ช่องไหนของเอกสารไหน (inviteContext) — เซิร์ฟเวอร์ใช้หาผู้รักษาการแทนของตำแหน่งนั้น
    function search(q, role, context) {
        var params = new URLSearchParams();
        if (q) params.set('q', q);
        if (role) params.set('role', role);
        if (context && context.module) {
            params.set('module', context.module);
            params.set('documentType', String(context.documentType));
            params.set('field', context.field);
        }
        return fetch('/api/people?' + params.toString(), { headers: { 'Accept': 'application/json' } })
            .then(function (r) { return r.ok ? r.json() : []; })
            .catch(function () { return []; });
    }

    function Picker(input) {
        this.input = input;
        this.form = input.form;
        this.role = input.getAttribute('data-role') || '';
        this.positionField = input.getAttribute('data-position-field');
        this.allowExternal = input.hasAttribute('data-allow-external');
        this.hintText = input.getAttribute('data-picker-hint') || DEFAULT_HINT;
        this.group = input.getAttribute('data-distinct-group');
        this.hidden = this.ensureHidden();
        this.selectedName = null;
        this.timer = null;
        this.seq = 0;
        // dev_form_filler.js เลือกผู้ลงนามผ่านตัว picker เพื่อให้ช่องรหัสบัญชีซ่อนตรงกับชื่อ
        input.__personPicker = this;
        this.build();
    }

    /** ช่องซ่อนเก็บรหัสบัญชี — สร้างทันทีที่สคริปต์โหลด ก่อนสคริปต์ของหน้าเติมค่าที่บันทึกไว้ (DOMContentLoaded) */
    Picker.prototype.ensureHidden = function () {
        var name = this.input.name + SUFFIX;
        var scope = this.form || document;
        var hidden = scope.querySelector('input[name="' + name + '"]');
        if (!hidden) {
            hidden = document.createElement('input');
            hidden.type = 'hidden';
            hidden.name = name;
            this.input.insertAdjacentElement('afterend', hidden);
        }
        return hidden;
    };

    Picker.prototype.build = function () {
        var self = this;
        var wrap = el('div', 'position-relative');
        this.input.parentNode.insertBefore(wrap, this.input);
        wrap.appendChild(this.input);
        this.input.setAttribute('autocomplete', 'off');
        this.input.removeAttribute('list');
        if (!this.input.getAttribute('placeholder') || /พิมพ์ชื่อ/.test(this.input.getAttribute('placeholder'))) {
            this.input.setAttribute('placeholder', 'พิมพ์เพื่อค้นหาชื่อในระบบ...');
        }

        // รายการอยู่ใต้ <body> แบบ fixed: .card-academic มี transform (แอนิเมชัน/hover) ซึ่งกัก fixed ไว้ในการ์ด
        // และทำให้การ์ดถัดไปทับรายการ — z-index เหนือ modal (1055) เผื่อช่องที่อยู่ใน modal
        this.menu = el('div', 'dropdown-menu shadow-sm person-picker-menu');
        this.menu.style.position = 'fixed';
        this.menu.style.zIndex = '1070';
        this.menu.style.overflowY = 'auto';
        document.body.appendChild(this.menu);
        this.reposition = function () { self.place(); };

        // บรรทัดใต้ช่อง: ข้อความตัวแรกเป็นคำเตือน (ว่างเมื่อไม่มีอะไรผิด) ตามด้วยปุ่มเพิ่มผู้ลงนามภายนอก
        this.hint = el('div', 'form-text person-picker-hint');
        this.hint.appendChild(document.createTextNode('')); // mark() เขียนคำเตือนลงโหนดนี้
        wrap.appendChild(this.hint);
        // คำอธิบายของช่องเป็นปุ่ม (i) ข้างป้ายกำกับ — ไม่มีป้ายกำกับก็วางไว้ในบรรทัดใต้ช่อง
        this.tip = window.InfoTip ? window.InfoTip.create(this.hintText) : null;
        if (this.tip) {
            var label = labelFor(this.input);
            if (label) {
                label.appendChild(this.tip);
            } else {
                this.hint.appendChild(this.tip);
                this.tipInHint = true;
            }
        }
        if (this.allowExternal) {
            var add = el('button', 'btn btn-link btn-sm p-0 align-baseline', '+ เพิ่มผู้ลงนามภายนอก');
            add.type = 'button';
            add.addEventListener('click', function () { self.openExternal(); });
            this.hint.appendChild(add);
        }

        if (this.input.disabled || this.input.readOnly) {
            this.hint.hidden = true; // เอกสารล็อก — แค่แสดงค่า ไม่มีปุ่มเพิ่มผู้ลงนามให้กด
            return;
        }
        this.hint.hidden = !this.allowExternal && !this.tipInHint;
        this.input.addEventListener('focus', function () { self.query(); });
        // เลือกแล้วช่องยังโฟกัสอยู่ คลิกซ้ำจึงไม่เกิด focus — เปิดรายการให้เลือกคนใหม่
        this.input.addEventListener('click', function () {
            if (!self.menu.classList.contains('show')) self.query();
        });
        this.input.addEventListener('input', function (e) {
            // choose() ส่ง input ให้บันทึกร่างอัตโนมัติ — ไม่ใช่การพิมพ์ ห้ามเปิดรายการกลับขึ้นมา
            if (!e.isTrusted) return;
            clearTimeout(self.timer);
            self.timer = setTimeout(function () { self.query(); }, 250);
        });
        this.input.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') self.close();
            if (e.key === 'Enter') e.preventDefault(); // ไม่ส่งฟอร์มทั้งที่ยังไม่ได้เลือกคน
        });
        this.input.addEventListener('blur', function () {
            // ปล่อยช่องโดยไม่เลือกจากรายการ: ชื่อที่พิมพ์ค้างไว้ไม่ใช่ผู้ลงนาม — กลับเป็นคนที่เลือกไว้
            setTimeout(function () { self.close(); self.restore(); }, 200);
        });
    };

    /** ค่าที่บันทึกไว้ถูกเติมลงฟอร์มตอน DOMContentLoaded — จำไว้เป็นคนที่เลือกไว้ */
    Picker.prototype.sync = function () {
        if (this.selectedName == null) {
            this.selectedName = this.input.value || '';
        }
        if (this.input.disabled || this.input.readOnly) return; // เอกสารล็อก — ไม่เตือนสิ่งที่แก้ไม่ได้
        // ชื่อที่บันทึกไว้ก่อนมีตัวค้นหา (ไม่มีรหัสบัญชีคู่กัน) — ให้เลือกใหม่จากรายชื่อ
        this.mark(!this.input.value || !!this.hidden.value);
        // ร่างเก่าที่เลือกคนเดียวกันไว้สองช่อง — ช่องหลังต้องเลือกใหม่
        if (this.isDuplicate(this.hidden.value)) this.mark(false, DUPLICATE_HINT);
    };

    Picker.prototype.restore = function () {
        var typed = this.input.value.trim();
        if (!typed) {
            if (this.hidden.value || this.selectedName) this.choose(null);
            return;
        }
        if (typed !== this.selectedName) {
            this.input.value = this.selectedName || '';
        }
        this.mark(!this.input.value || !!this.hidden.value);
    };

    Picker.prototype.mark = function (ok, message) {
        this.input.classList.toggle('is-invalid', !ok);
        this.hint.classList.toggle('text-danger', !ok);
        if (!ok) {
            this.hint.firstChild.textContent = message || 'ชื่อนี้ยังไม่ได้เลือกจากรายชื่อในระบบ — กรุณาค้นหาแล้วเลือกใหม่ ';
        } else {
            this.hint.firstChild.textContent = '';
        }
        // ช่องที่ถูกล็อก (เช่น เอกสารที่ 9 ผูกกับผู้ขอ) ไม่มีปุ่มเพิ่มผู้ลงนามให้กด
        this.hint.hidden = this.input.readOnly || this.input.disabled
                || (ok && !this.allowExternal && !this.tipInHint);
    };

    Picker.prototype.query = function () {
        // ช่องที่สคริปต์ของหน้าล็อกไว้ภายหลัง (เช่น เอกสารที่ 9 ผูกกับผู้ขอตามสถานะที่ติ๊ก)
        if (this.input.disabled || this.input.readOnly) return;
        var self = this;
        var q = this.input.value.trim();
        // ช่องที่แสดงชื่อคนที่เลือกอยู่ — โฟกัสแล้วแสดงคนที่แนะนำ ไม่ใช่ค้นด้วยชื่อเดิม
        if (q === this.selectedName) q = '';
        var seq = ++this.seq;
        this.searchPeople(q).then(function (people) {
            // ผลที่กลับมาช้ากว่าการเลือกหรือการพิมพ์ครั้งถัดไป — ไม่ใช่รายการที่ต้องแสดงแล้ว
            if (seq === self.seq) self.render(people, q);
        });
    };

    /** ค้นรายชื่อสำหรับช่องนี้ — dev_form_filler.js ใช้ทางเดียวกันเพื่อให้ได้รายการเดียวกับที่ผู้ใช้เห็น */
    Picker.prototype.searchPeople = function (q) {
        return search(q, this.role, this.inviteContext());
    };

    /** รหัสบัญชีที่ช่องอื่นในกลุ่มเดียวกันเลือกไว้แล้ว */
    Picker.prototype.takenByOthers = function () {
        if (!this.group) return [];
        var self = this;
        var scope = this.form || document;
        var taken = [];
        scope.querySelectorAll('[data-person-picker][data-distinct-group="' + this.group + '"]').forEach(function (other) {
            if (other === self.input) return;
            var hidden = scope.querySelector('input[name="' + other.name + SUFFIX + '"]');
            if (hidden && hidden.value) taken.push(hidden.value);
        });
        return taken;
    };

    /** ช่องที่อยู่ก่อนหน้าในกลุ่มเลือกคนนี้ไว้แล้ว (ช่องแรกที่เลือกถือว่าถูก ช่องหลังต้องเปลี่ยน) */
    Picker.prototype.isDuplicate = function (userId) {
        if (!this.group || !userId) return false;
        var self = this;
        var scope = this.form || document;
        var before = true;
        var dup = false;
        scope.querySelectorAll('[data-person-picker][data-distinct-group="' + this.group + '"]').forEach(function (other) {
            if (other === self.input) { before = false; return; }
            if (!before) return;
            var hidden = scope.querySelector('input[name="' + other.name + SUFFIX + '"]');
            if (hidden && hidden.value === String(userId)) dup = true;
        });
        return dup;
    };

    Picker.prototype.render = function (people, q) {
        var self = this;
        this.menu.replaceChildren();
        var taken = this.takenByOthers();
        people = people.filter(function (p) { return taken.indexOf(String(p.userId)) < 0; });
        if (!people.length) {
            this.menu.appendChild(el('div', 'dropdown-item-text small text-muted',
                q ? 'ไม่พบชื่อ “' + q + '” ในระบบ' : 'พิมพ์ชื่อ อีเมล หรือตำแหน่งเพื่อค้นหา'));
        }
        var headed = { rec: false, other: false };
        people.forEach(function (p) {
            var group = p.recommended ? 'rec' : 'other';
            if (!headed[group]) {
                headed[group] = true;
                self.menu.appendChild(el('h6', 'dropdown-header',
                    p.recommended ? 'ผู้ที่มีบทบาทตรงตำแหน่งนี้' : 'บัญชีอื่นในระบบ'));
            }
            var item = el('button', 'dropdown-item text-wrap');
            item.type = 'button';
            item.appendChild(el('div', 'fw-semibold', p.name));
            var detail = [p.position, p.affiliation, p.email].filter(Boolean).join(' · ');
            if (detail || p.external || p.acting) {
                var line = el('div', 'small text-muted', detail);
                if (p.acting) line.appendChild(el('span', 'badge bg-warning-subtle text-warning-emphasis ms-1', 'รักษาการแทน'));
                if (p.external) line.appendChild(el('span', 'badge bg-secondary-subtle text-secondary ms-1', 'ภายนอก'));
                item.appendChild(line);
            }
            // mousedown ก่อน blur — ไม่งั้นรายการปิดก่อนคลิกถึง
            item.addEventListener('mousedown', function (e) { e.preventDefault(); self.choose(p); });
            self.menu.appendChild(item);
        });
        if (this.allowExternal) {
            var add = el('button', 'dropdown-item text-primary small', '+ เพิ่มผู้ลงนามภายนอก ด้วยอีเมล');
            add.type = 'button';
            add.addEventListener('mousedown', function (e) { e.preventDefault(); self.close(); self.openExternal(); });
            this.menu.appendChild(el('div', 'dropdown-divider'));
            this.menu.appendChild(add);
        }
        this.menu.classList.add('show');
        this.place();
        window.addEventListener('scroll', this.reposition, true);
        window.addEventListener('resize', this.reposition);
    };

    /** วางรายการใต้ช่อง — ที่ว่างด้านล่างไม่พอก็เปิดขึ้นด้านบนแทน */
    Picker.prototype.place = function () {
        var rect = this.input.getBoundingClientRect();
        var below = window.innerHeight - rect.bottom - 8;
        var above = rect.top - 8;
        var up = below < 200 && above > below;
        var s = this.menu.style;
        s.left = rect.left + 'px';
        s.width = rect.width + 'px';
        s.maxHeight = Math.max(120, Math.min(320, up ? above : below)) + 'px';
        if (up) {
            s.top = 'auto';
            s.bottom = (window.innerHeight - rect.top + 2) + 'px';
        } else {
            s.top = (rect.bottom + 2) + 'px';
            s.bottom = 'auto';
        }
    };

    Picker.prototype.close = function () {
        clearTimeout(this.timer);
        this.seq++;
        this.menu.classList.remove('show');
        window.removeEventListener('scroll', this.reposition, true);
        window.removeEventListener('resize', this.reposition);
    };

    Picker.prototype.choose = function (person) {
        // ช่องอื่นในกลุ่มเลือกคนนี้ไปแล้ว (เช่น เพิ่มคนนอก มข. ด้วยอีเมลที่มีบัญชีอยู่แล้ว) — ไม่รับ
        if (person && this.takenByOthers().indexOf(String(person.userId)) >= 0) {
            this.close();
            this.mark(false, DUPLICATE_HINT);
            return false;
        }
        this.input.value = person ? person.name : '';
        this.hidden.value = person ? String(person.userId) : '';
        this.selectedName = this.input.value;
        if (person && person.position && this.positionField) {
            var pos = (this.form || document).querySelector('[name="' + this.positionField + '"]');
            if (pos && !pos.disabled && !pos.readOnly) {
                pos.value = person.position;
                pos.dispatchEvent(new Event('input', { bubbles: true }));
            }
        }
        this.close();
        this.mark(true);
        // บันทึกร่างอัตโนมัติฟัง input/change ของฟอร์ม — ช่องซ่อนถูกเก็บไปด้วยในรอบเดียวกัน
        this.input.dispatchEvent(new Event('input', { bubbles: true }));
        this.input.dispatchEvent(new Event('change', { bubbles: true }));
        return true;
    };

    /**
     * เอกสารที่เชิญมาลงนาม — อ่านจาก data-auto-draft ของฟอร์ม (/api/draft/{academic|position}/{id}/{doc})
     * ให้หนังสือเชิญบอกได้ว่าเป็นคำร้องของใคร และท่านอยู่ในฐานะอะไร
     */
    Picker.prototype.inviteContext = function () {
        var form = this.form || this.input.closest('form');
        var url = form ? form.getAttribute('data-auto-draft') : null;
        var m = url ? url.match(/\/api\/draft\/(academic|position)\/(\d+)\/(\d+)/) : null;
        if (!m) return {};
        return { module: m[1].toUpperCase(), requestId: Number(m[2]), documentType: Number(m[3]), field: this.input.name };
    };

    /** เพิ่มผู้ลงนามจากนอก มข. — สร้างบัญชีภายนอก + อีเมลเชิญ แล้วเลือกคนนั้นทันที */
    Picker.prototype.openExternal = function () {
        var self = this;
        var modal = externalModal();
        modal.onSubmit = function (data, done) {
            var context = self.inviteContext();
            Object.keys(context).forEach(function (k) { data[k] = context[k]; });
            fetch('/api/people/external', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'X-HRCP-CT': csrfToken() },
                body: JSON.stringify(data)
            }).then(function (r) {
                return r.json().then(function (body) { return { ok: r.ok, body: body }; });
            }).then(function (res) {
                if (res.ok) {
                    done(self.choose(res.body) ? null : 'คนนี้ถูกเลือกในช่องอื่นแล้ว — แต่ละช่องต้องเป็นคนละคน');
                } else {
                    done((res.body && res.body.error) || 'เพิ่มผู้ลงนามไม่สำเร็จ');
                }
            }).catch(function () { done('เพิ่มผู้ลงนามไม่สำเร็จ'); });
        };
        modal.show();
    };

    var sharedModal = null;

    function externalModal() {
        if (sharedModal) return sharedModal;
        var root = el('div', 'modal fade');
        root.tabIndex = -1;
        root.id = 'externalSignerModal';
        var dialog = el('div', 'modal-dialog modal-dialog-centered');
        var content = el('form', 'modal-content');
        var header = el('div', 'modal-header');
        header.appendChild(el('h5', 'modal-title', 'เพิ่มผู้ลงนามภายนอก'));
        var close = el('button', 'btn-close');
        close.type = 'button';
        close.setAttribute('data-bs-dismiss', 'modal');
        header.appendChild(close);
        var body = el('div', 'modal-body');
        body.appendChild(el('p', 'small text-muted',
            'ระบบจะสร้างบัญชีผู้ลงนามภายนอก และส่งหนังสือเชิญทางอีเมล ซึ่งระบุคำร้องที่เกี่ยวข้อง ฐานะของผู้ลงนาม '
            + 'และวิธีลงนาม ผู้ลงนามเข้าระบบด้วย KKU SSO ด้วยอีเมลนี้ และลงนามด้วย Digital ID ของหน่วยงานตัวเอง '
            + 'หรือยืนยันตัวตนทางอีเมลหากไม่มี'));
        var fields = [
            ['title', 'คำนำหน้า / ตำแหน่งทางวิชาการ', true, 'เช่น รศ.ดร., นาย, นาง'],
            ['firstName', 'ชื่อ', true, ''],
            ['lastName', 'นามสกุล', true, ''],
            ['email', 'อีเมล', true, 'name@example.ac.th'],
            ['affiliation', 'หน่วยงาน', false, 'เช่น มหาวิทยาลัยเชียงใหม่']
        ];
        var inputs = {};
        fields.forEach(function (f) {
            var group = el('div', 'mb-2');
            var label = el('label', 'form-label small mb-1', f[1] + (f[2] ? ' *' : ''));
            var input = el('input', 'form-control form-control-sm');
            input.type = f[0] === 'email' ? 'email' : 'text';
            input.required = f[2];
            input.placeholder = f[3];
            group.appendChild(label);
            group.appendChild(input);
            body.appendChild(group);
            inputs[f[0]] = input;
        });
        var error = el('div', 'alert alert-danger small py-2 mb-0');
        error.hidden = true;
        body.appendChild(error);
        var footer = el('div', 'modal-footer');
        var cancel = el('button', 'btn btn-outline-secondary', 'ยกเลิก');
        cancel.type = 'button';
        cancel.setAttribute('data-bs-dismiss', 'modal');
        var submit = el('button', 'btn btn-academic', 'เพิ่มและเลือกผู้ลงนาม');
        submit.type = 'submit';
        footer.appendChild(cancel);
        footer.appendChild(submit);
        content.appendChild(header);
        content.appendChild(body);
        content.appendChild(footer);
        dialog.appendChild(content);
        root.appendChild(dialog);
        document.body.appendChild(root);

        var api = {
            onSubmit: null,
            show: function () {
                Object.keys(inputs).forEach(function (k) { inputs[k].value = ''; });
                error.hidden = true;
                submit.disabled = false;
                window.bootstrap.Modal.getOrCreateInstance(root).show();
            }
        };
        content.addEventListener('submit', function (e) {
            e.preventDefault();
            if (!content.reportValidity()) return;
            var data = {};
            Object.keys(inputs).forEach(function (k) { data[k] = inputs[k].value.trim(); });
            submit.disabled = true;
            api.onSubmit(data, function (problem) {
                submit.disabled = false;
                if (problem) {
                    error.textContent = problem;
                    error.hidden = false;
                } else {
                    window.bootstrap.Modal.getOrCreateInstance(root).hide();
                }
            });
        });
        sharedModal = api;
        return api;
    }

    var pickers = [];
    document.querySelectorAll('input[data-person-picker]').forEach(function (input) {
        pickers.push(new Picker(input));
    });
    // ค่าที่บันทึกไว้ถูกเติมตอน DOMContentLoaded/โหลดเสร็จ — อ่านหลังจากนั้น
    var loaded = document.readyState === 'complete';
    function syncAll() { loaded = true; pickers.forEach(function (p) { p.sync(); }); }
    if (loaded) {
        syncAll();
    } else {
        window.addEventListener('load', syncAll);
    }

    window.PersonPicker = {
        /**
         * ผูกตัวค้นหาชื่อกับช่องที่สร้างหลังโหลดหน้า — ตั้งค่า name และ value (ถ้ามี) ก่อนเรียก
         * ช่องซ่อนรหัสบัญชีถูกสร้างต่อท้ายช่องนี้ ("<name>__signer")
         */
        attach: function (input) {
            if (!input || input.__personPicker) return input && input.__personPicker;
            var picker = new Picker(input);
            pickers.push(picker);
            if (loaded) picker.sync();
            return picker;
        }
    };
})();
