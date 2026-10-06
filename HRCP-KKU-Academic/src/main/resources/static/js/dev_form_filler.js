/**
 * Dev Form Filler — กรอกข้อมูลทดสอบให้ฟอร์มเอกสาร (เฉพาะ app.auth.mode=dev)
 *
 * base_academic.html โหลดไฟล์นี้เฉพาะเมื่อ GlobalModelAdvice ตั้ง devFormFill ไว้
 * ซึ่งเป็นจริงแค่ในโหมด dev — production ไม่มีสคริปต์นี้อยู่บนหน้าเลย
 *
 * มีสองที่:
 *   1. หน้าฟอร์มเอกสาร (form[id^="doc"]) — ปุ่ม "กรอกข้อมูลทดสอบ" เติมช่องที่ยังว่างในหน้านั้น
 *   2. หน้ารายละเอียดคำร้องเฟส 2 — ปุ่ม "กรอกทุกเอกสาร" เปิดฟอร์มทีละฉบับใน iframe ที่ซ่อนไว้
 *      แล้วเติมให้ตามลำดับเลขเอกสาร (ฉบับที่ 1 ก่อน เพราะฉบับอื่นดึงค่าจากฉบับที่ 1)
 *
 * บันทึกผ่าน AutoDraft.flushAll() เท่านั้น — เป็นการบันทึกร่างแบบเดียวกับที่ผู้ใช้พิมพ์เอง
 * ไม่ส่งฟอร์ม จึงไม่เลื่อนสถานะคำร้อง ไม่สร้างไฟล์ และไม่ส่งอีเมล
 *
 * เติมเฉพาะช่องที่ว่าง มองเห็น และแก้ไขได้ ค่าที่มีอยู่แล้วไม่ถูกทับ
 */
(function () {
    'use strict';

    // ช่องสารบรรณ (DocumentFieldOwnership.OFFICE_FIELDS) ออกเลขหลังลงนามครบ — เติมก่อนจะทำให้
    // เอกสารดูเหมือนออกเลขไปแล้ว
    var SKIP_NAMES = ['memo_no', 'date'];

    // ช่องติ๊กที่ความหมายเป็น "ไม่/ยังไม่" — ติ๊กคู่กับฝั่งตรงข้ามแล้วเอกสารขัดกันเอง
    var NEGATIVE_CHECKBOX = /(^|_)not_|pending|confidential/;

    var THAI_MONTHS = ['มกราคม', 'กุมภาพันธ์', 'มีนาคม', 'เมษายน', 'พฤษภาคม', 'มิถุนายน',
        'กรกฎาคม', 'สิงหาคม', 'กันยายน', 'ตุลาคม', 'พฤศจิกายน', 'ธันวาคม'];

    function thaiDigits(s) {
        return String(s).replace(/[0-9]/g, function (d) { return '๐๑๒๓๔๕๖๗๘๙'[d]; });
    }

    function thaiToday() {
        var d = new Date();
        return thaiDigits(d.getDate()) + ' ' + THAI_MONTHS[d.getMonth()] + ' พ.ศ. ' + thaiDigits(d.getFullYear() + 543);
    }

    function rowOf(name) {
        var m = /(\d+)$/.exec(name);
        return m ? m[1] : '1';
    }

    // ลำดับมีผล — กติกาแรกที่ตรงชนะ
    var RULES = [
        [/email/, 'test.applicant@kku.ac.th'],
        [/phone|mobile|(^|_)tel/, '0812345678'],
        [/birth_date/, '๑๒ พฤษภาคม พ.ศ. ๒๕๒๘'],
        [/lecturer_appointment_date/, '๑ มิถุนายน พ.ศ. ๒๕๕๘'],
        [/assistant_appointment_date|currentpositiondate/, '๑ ตุลาคม พ.ศ. ๒๕๖๒'],
        [/associate_appointment_date/, '๑ ตุลาคม พ.ศ. ๒๕๖๖'],
        [/book_publish_date/, '๑ มีนาคม พ.ศ. ๒๕๖๗'],
        [/date/, thaiToday],
        [/^(years|count_year|[dm]_years)$/, '10'],
        [/^(months|count_month|[dm]_months)$/, '3'],
        [/days$/, '15'],
        [/^age$/, '40'],
        // DocumentCompleteness.SEMESTER_YEAR — ภาคทับปี พ.ศ. ตัวเลขอารบิก
        [/semester|academic_year/, '1/2568'],
        [/education_year/, '๒๕๕๗'],
        [/_year(_\d+)?$/, '๒๕๖๖'],
        [/salary/, '45,000'],
        [/hours/, '3'],
        [/impact/, '2.5'],
        [/count|total_stories/, '3'],
        [/_level_\d+$/, 'ดีมาก'],
        [/teaching_level/, 'ปริญญาตรี'],
        [/teaching_subject|subject_name|^subject$/, 'CP101 การเขียนโปรแกรมคอมพิวเตอร์'],
        [/education_degree/, 'วิทยาศาสตรดุษฎีบัณฑิต'],
        [/education_major/, 'วิทยาการคอมพิวเตอร์'],
        [/education_institution/, 'มหาวิทยาลัยขอนแก่น'],
        [/education_country/, 'ไทย'],
        [/firstname/, 'สมชาย'],
        [/lastname/, 'ทดสอบ'],
        [/title_name/, 'ผู้ช่วยศาสตราจารย์ สมชาย ทดสอบ'],
        [/dean_position|^position_title$/, 'คณบดีวิทยาลัยการคอมพิวเตอร์'],
        [/hr_officer_position/, 'นักทรัพยากรบุคคล ชำนาญการ'],
        [/request_position|applicant_position/, 'ผู้ช่วยศาสตราจารย์'],
        [/other_position/, 'กรรมการบริหารหลักสูตร วิทยาลัยการคอมพิวเตอร์ ๑ มกราคม พ.ศ. ๒๕๖๕'],
        [/speaker/, 'วิทยากรรับเชิญ การประชุมวิชาการนานาชาติ ICSEC ๒๕๖๗'],
        [/position/, 'อาจารย์'],
        [/names?$|_name_|^name/, 'สมชาย ทดสอบ'],
        [/faculty/, 'วิทยาลัยการคอมพิวเตอร์'],
        [/university|affiliation/, 'มหาวิทยาลัยขอนแก่น'],
        [/code/, '0103'],
        [/department|major/, 'วิทยาการคอมพิวเตอร์'],
        [/thesis/, 'การพัฒนาแบบจำลองการเรียนรู้เชิงลึกสำหรับการจำแนกภาพทางการแพทย์'],
        [/working|title|paper|research|article|book/, function (n) {
            return 'ผลงานทดสอบที่ ' + thaiDigits(rowOf(n)) + ': การวิเคราะห์ข้อมูลขนาดใหญ่ด้วยการเรียนรู้ของเครื่อง';
        }]
    ];

    function labelOf(el) {
        if (el.id) {
            var forLabel = document.querySelector('label[for="' + el.id + '"]');
            if (forLabel) return forLabel.textContent.trim();
        }
        var box = el.closest('div');
        var label = box && box.querySelector('label');
        return label ? label.textContent.replace(/\*/g, '').trim() : '';
    }

    function textFor(el) {
        var n = (el.name || '').toLowerCase();
        for (var i = 0; i < RULES.length; i++) {
            if (RULES[i][0].test(n)) {
                var v = RULES[i][1];
                return typeof v === 'function' ? v(n) : v;
            }
        }
        if (el.tagName === 'TEXTAREA') {
            return 'ข้อมูลทดสอบสำหรับตรวจสอบระบบ' + (labelOf(el) ? ' — ' + labelOf(el) : '');
        }
        return 'ทดสอบ ' + (labelOf(el) || el.name);
    }

    function optionsFor(el) {
        if (el.dataset.comboList && window.DatalistCombo) return window.DatalistCombo.optionsOf(el);
        var list = el.list;
        if (!list) return [];
        return Array.prototype.map.call(list.options, function (o) { return o.value; }).filter(Boolean);
    }

    function fire(el) {
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
    }

    function editable(el) {
        if (el.disabled || el.readOnly || el.type === 'hidden') return false;
        if (SKIP_NAMES.indexOf(el.name) !== -1) return false;
        // ส่วนที่ซ่อนตามตำแหน่งที่ขอ — DocumentCompleteness ไม่นับอยู่แล้ว และไม่ควรมีเนื้อหาในเอกสาร
        return el.getClientRects().length > 0;
    }

    /** เลือกคนแรกที่ยังไม่ซ้ำในกลุ่ม ผ่านตัว picker เอง ช่องรหัสบัญชีซ่อนจึงตรงกับชื่อ */
    function fillPicker(input) {
        var picker = input.__personPicker;
        if (!picker) return Promise.resolve(false);
        return picker.searchPeople('')
            .then(function (people) {
                var taken = picker.takenByOthers();
                var person = people.filter(function (p) { return taken.indexOf(String(p.userId)) < 0; })[0];
                return person ? picker.choose(person) : false;
            })
            .catch(function () { return false; });
    }

    /**
     * เติมช่องว่างทั้งหมดในฟอร์ม แล้วบันทึกร่าง
     *
     * @return Promise<{filled:number, saved:boolean, locked:boolean}>
     */
    function fillForm(form) {
        if (!form) return Promise.resolve({ filled: 0, saved: false, locked: true });
        // ประตูของผู้ยื่นถอด data-auto-draft ออกเมื่อเอกสารล็อก — ไม่มีอะไรให้เติม
        if (!form.hasAttribute('data-auto-draft')) {
            return Promise.resolve({ filled: 0, saved: false, locked: true });
        }

        var filled = 0;
        var radios = {};
        var pickers = [];

        form.querySelectorAll('input, textarea, select').forEach(function (el) {
            if (!el.name || !editable(el)) return;

            if (el.type === 'radio') {
                if (!radios[el.name]) radios[el.name] = [];
                radios[el.name].push(el);
                return;
            }
            if (el.type === 'checkbox') {
                if (!el.checked && !NEGATIVE_CHECKBOX.test(el.name)) {
                    el.checked = true;
                    fire(el);
                    filled++;
                }
                return;
            }
            if (el.value && el.value.trim()) return;

            if (el.hasAttribute('data-person-picker')) {
                pickers.push(el);
                return;
            }

            var value;
            if (el.tagName === 'SELECT') {
                var opt = Array.prototype.find.call(el.options, function (o) { return o.value; });
                value = opt ? opt.value : '';
            } else {
                var opts = optionsFor(el);
                value = opts.length ? opts[0] : textFor(el);
                if (el.type === 'number' && isNaN(Number(value))) value = '1';
            }
            if (!value) return;
            el.value = value;
            fire(el);
            filled++;
        });

        Object.keys(radios).forEach(function (name) {
            var group = radios[name];
            if (group.some(function (r) { return r.checked; })) return;
            group[0].checked = true;
            fire(group[0]);
            filled++;
        });

        // ทีละช่อง — takenByOthers() ต้องเห็นคนที่ช่องก่อนหน้าเลือกไปแล้ว
        var chain = Promise.resolve();
        pickers.forEach(function (input) {
            chain = chain.then(function () {
                return fillPicker(input).then(function (ok) { if (ok) filled++; });
            });
        });

        return chain.then(function () {
            if (!window.AutoDraft) return false;
            return window.AutoDraft.flushAll();
        }).then(function (saved) {
            return { filled: filled, saved: !!saved, locked: false };
        });
    }

    // ---------------------------------------------------------------- UI

    function bar(html) {
        var box = document.createElement('div');
        box.className = 'dev-fill-bar';
        box.innerHTML = '<span class="dev-fill-tag">DEV</span>' + html + '<span class="dev-fill-status" role="status"></span>';
        return box;
    }

    function setStatus(box, text, tone) {
        var s = box.querySelector('.dev-fill-status');
        s.textContent = text;
        s.dataset.tone = tone || '';
    }

    function mountFormButton(form) {
        var box = bar('<button type="button" class="btn btn-sm dev-fill-btn"><i class="fas fa-wand-magic-sparkles me-1"></i>กรอกข้อมูลทดสอบ</button>');
        form.parentNode.insertBefore(box, form);
        var btn = box.querySelector('button');
        btn.addEventListener('click', function () {
            btn.disabled = true;
            setStatus(box, 'กำลังกรอก...');
            fillForm(form).then(function (r) {
                btn.disabled = false;
                if (r.locked) setStatus(box, 'เอกสารนี้ล็อกอยู่ — ไม่ได้กรอก', 'warn');
                else if (!r.saved) setStatus(box, 'กรอก ' + r.filled + ' ช่อง แต่บันทึกร่างไม่สำเร็จ', 'error');
                else setStatus(box, 'กรอก ' + r.filled + ' ช่อง · บันทึกร่างแล้ว', 'ok');
            });
        });
    }

    var DOC_LINK = /^\/(user|admin)\/position\/request\/\d+\/document\/(\d+)$/;

    function docLinks() {
        var seen = {};
        var out = [];
        document.querySelectorAll('a[href]').forEach(function (a) {
            var url = new URL(a.getAttribute('href'), location.href);
            var m = DOC_LINK.exec(url.pathname);
            if (!m || seen[url.pathname]) return;
            seen[url.pathname] = true;
            out.push({ path: url.pathname, type: Number(m[2]), anchor: a });
        });
        return out.sort(function (a, b) { return a.type - b.type; });
    }

    /** เปิดฟอร์มใน iframe นอกจอ — ต้องมี layout จริง ไม่อย่างนั้นทุกช่องนับว่ามองไม่เห็น */
    function fillInFrame(path) {
        return new Promise(function (resolve) {
            var frame = document.createElement('iframe');
            frame.className = 'dev-fill-frame';
            frame.setAttribute('aria-hidden', 'true');
            frame.tabIndex = -1;
            var done = false;
            var timeout = setTimeout(function () { finish({ error: 'หมดเวลา' }); }, 30000);

            function finish(result) {
                if (done) return;
                done = true;
                clearTimeout(timeout);
                frame.remove();
                resolve(result);
            }

            frame.addEventListener('load', function () {
                // รอสคริปต์ของหน้าเติมค่าที่บันทึกไว้และค่าจากโปรไฟล์ (fetch /api/my/profile) ก่อน
                setTimeout(function () {
                    try {
                        var w = frame.contentWindow;
                        if (!w.DevFormFill) { finish({ error: 'เปิดฟอร์มไม่ได้' }); return; }
                        w.DevFormFill.fill().then(finish, function () { finish({ error: 'กรอกไม่สำเร็จ' }); });
                    } catch (e) {
                        finish({ error: 'เปิดฟอร์มไม่ได้' });
                    }
                }, 1200);
            });
            frame.src = path;
            document.body.appendChild(frame);
        });
    }

    function mountFillAll(links) {
        var box = bar('<button type="button" class="btn btn-sm dev-fill-btn"><i class="fas fa-wand-magic-sparkles me-1"></i>กรอกทุกเอกสาร (' + links.length + ' ฉบับ)</button>');
        var card = links[0].anchor.closest('.card');
        if (card && card.parentNode) card.parentNode.insertBefore(box, card);
        else (document.querySelector('section .container') || document.body).prepend(box);

        var list = document.createElement('ol');
        list.className = 'dev-fill-log';
        box.appendChild(list);

        var btn = box.querySelector('button');
        btn.addEventListener('click', function () {
            btn.disabled = true;
            list.innerHTML = '';
            var failed = 0;
            var chain = Promise.resolve();
            links.forEach(function (link) {
                var li = document.createElement('li');
                li.textContent = 'เอกสารที่ ' + link.type + ' — รอ';
                list.appendChild(li);
                chain = chain.then(function () {
                    li.textContent = 'เอกสารที่ ' + link.type + ' — กำลังกรอก...';
                    setStatus(box, 'กำลังกรอกเอกสารที่ ' + link.type);
                    return fillInFrame(link.path).then(function (r) {
                        if (r.error) { failed++; li.dataset.tone = 'error'; li.textContent = 'เอกสารที่ ' + link.type + ' — ' + r.error; }
                        else if (r.locked) { li.dataset.tone = 'warn'; li.textContent = 'เอกสารที่ ' + link.type + ' — ล็อกอยู่ ข้าม'; }
                        else if (!r.saved) { failed++; li.dataset.tone = 'error'; li.textContent = 'เอกสารที่ ' + link.type + ' — กรอก ' + r.filled + ' ช่อง แต่บันทึกไม่สำเร็จ'; }
                        else { li.dataset.tone = 'ok'; li.textContent = 'เอกสารที่ ' + link.type + ' — กรอก ' + r.filled + ' ช่อง บันทึกแล้ว'; }
                    });
                });
            });
            chain.then(function () {
                btn.disabled = false;
                if (failed) {
                    setStatus(box, 'เสร็จ แต่มี ' + failed + ' ฉบับที่ไม่สำเร็จ', 'error');
                } else {
                    setStatus(box, 'เสร็จทุกฉบับ กำลังโหลดหน้าใหม่...', 'ok');
                    setTimeout(function () { location.reload(); }, 1200);
                }
            });
        });
    }

    function docForm() {
        return document.querySelector('form[id^="doc"][data-auto-draft]') || document.querySelector('form[id^="doc"]');
    }

    window.DevFormFill = {
        fill: function () { return fillForm(docForm()); }
    };

    document.addEventListener('DOMContentLoaded', function () {
        // อยู่ใน iframe ของ "กรอกทุกเอกสาร" — หน้าแม่สั่งเอง ไม่ต้องมีปุ่ม
        if (window.top !== window.self) return;
        var form = docForm();
        if (form) {
            mountFormButton(form);
            return;
        }
        var links = docLinks();
        if (links.length) mountFillAll(links);
    });
})();
