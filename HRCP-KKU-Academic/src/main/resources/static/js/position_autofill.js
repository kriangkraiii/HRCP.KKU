/**
 * Position Document Auto-fill
 * Auto-fills applicant info from user profile + cross-document data (doc 1).
 * Only fills EMPTY fields — never overwrites existing/saved data.
 *
 * Three sources, in increasing order of authority:
 *   1. the faculty directory synced from the Fund Management platform
 *      (/api/my/profile — the caller's own record only)
 *   2. the local user profile        — window.AUTOFILL_USER
 *   3. what was already typed in doc 1 — window.AUTOFILL_DOC1
 *
 * Because every write goes through fillInput/fillSelect, which skip non-empty
 * fields, an applicant can always correct anything that was filled in for them.
 *
 * Usage: Include this script after Thymeleaf inline vars:
 *   window.AUTOFILL_USER = { title, name, academicPosition, email, mobileNumber };
 *   window.AUTOFILL_DOC1 = { ...doc1 JSON data };
 */
(function() {
    'use strict';

    document.addEventListener('DOMContentLoaded', function() {
        // Wait a tick so existingData restore runs first
        setTimeout(runAutofill, 50);
        // The directory lookup is a network call, so it lands after the local
        // sources. That ordering is intentional: anything already present wins.
        setTimeout(fillFromDirectory, 60);
    });

    /**
     * Pulls this professor's own record from the synced faculty directory and
     * fills the personal details the request forms ask for.
     *
     * A missing or unlinked record is not an error — the form simply stays as
     * the local profile left it.
     */
    function fillFromDirectory() {
        fetch('/api/my/profile', {
            headers: { 'Accept': 'application/json' },
            credentials: 'same-origin'
        })
            .then(function(r) { return r.ok ? r.json() : null; })
            .then(function(p) {
                if (!p || !p.linked) return;

                if (p.prefix) fillSelect('title', p.prefix);
                if (p.displayName) fillInput('applicant_name', fullName(p));
                if (p.positionTitle) fillInput('current_position', p.positionTitle);
                if (p.email) fillInput('email', p.email);
                if (p.tel) fillInput('phone_mobile', p.tel);
                if (p.firstName) fillInput('applicant_firstname', p.firstName);
                if (p.lastName) fillInput('applicant_lastname', p.lastName);
                if (p.prefix && p.firstName) {
                    fillInput('title_name', p.prefix + ' ' + fullName(p));
                }
                if (p.positionEn) fillInput('current_position_en', p.positionEn);
                if (p.nameEn) fillInput('applicant_name_en', p.nameEn);
            })
            .catch(function(e) {
                console.debug('Directory autofill unavailable:', e);
            });
    }

    function fullName(p) {
        return [p.firstName, p.lastName].filter(Boolean).join(' ');
    }

    function runAutofill() {
        var user = window.AUTOFILL_USER || {};
        var doc1 = {};
        try {
            if (window.AUTOFILL_DOC1 && typeof window.AUTOFILL_DOC1 === 'string') {
                doc1 = JSON.parse(window.AUTOFILL_DOC1);
            } else if (window.AUTOFILL_DOC1 && typeof window.AUTOFILL_DOC1 === 'object') {
                doc1 = window.AUTOFILL_DOC1;
            }
        } catch(e) { doc1 = {}; }

        // Layer 1: User profile → form fields
        if (user.title) fillSelect('title', user.title);
        if (user.name) fillInput('applicant_name', user.name);
        if (user.academicPosition) fillInput('current_position', user.academicPosition);
        if (user.email) fillInput('email', user.email);
        if (user.mobileNumber) fillInput('phone_mobile', user.mobileNumber);

        // Doc 7 splits name into firstname + lastname
        if (user.name) {
            var parts = user.name.trim().split(/\s+/);
            if (parts.length >= 2) {
                fillInput('applicant_firstname', parts[0]);
                fillInput('applicant_lastname', parts.slice(1).join(' '));
            } else if (parts.length === 1) {
                fillInput('applicant_firstname', parts[0]);
            }
        }

        // Layer 2: Doc 1 cross-fill → other docs
        if (doc1.target_position) {
            fillSelect('request_position', doc1.target_position);
        }
        if (doc1.major) fillInput('major', doc1.major);
        if (doc1.major_code) fillInput('major_code', doc1.major_code);
        if (doc1.department) fillInput('department', doc1.department);
        if (doc1.faculty) fillInput('faculty', doc1.faculty);
        if (doc1.university) fillInput('university', doc1.university);
        if (doc1.title) fillSelect('title', doc1.title);
        if (doc1.applicant_name) fillInput('applicant_name', doc1.applicant_name);
        if (doc1.current_position) fillInput('current_position', doc1.current_position);

        // Doc 7: split name from doc1
        if (doc1.applicant_name) {
            var nameParts = doc1.applicant_name.trim().split(/\s+/);
            if (nameParts.length >= 2) {
                fillInput('applicant_firstname', nameParts[0]);
                fillInput('applicant_lastname', nameParts.slice(1).join(' '));
            }
        }

        // Doc 9: title_name = title + name combo
        if (doc1.title && doc1.applicant_name) {
            fillInput('title_name', doc1.title + doc1.applicant_name);
        } else if (user.title && user.name) {
            fillInput('title_name', user.title + user.name);
        }

        // Doc 2: status from user profile — map title to status
        if (user.title) {
            var statusMap = { 'นาย': 'ข้าราชการ', 'นาง': 'ข้าราชการ', 'นางสาว': 'ข้าราชการ' };
            // Don't override — status could be different
        }
    }

    function fillInput(name, value) {
        var el = document.querySelector('[name="' + name + '"]');
        if (el && !el.value && el.type !== 'radio' && el.type !== 'checkbox') {
            el.value = value;
        }
    }

    /** เทียบค่าแบบไม่สนช่องว่าง — ทะเบียนบุคลากรเขียน "ผศ. ดร." บ้าง "ผศ.ดร." บ้าง */
    function squash(value) {
        return String(value == null ? '' : value).replace(/\s+/g, '');
    }

    function fillSelect(name, value) {
        var el = document.querySelector('[name="' + name + '"]');
        if (!el) return;

        if (el.tagName === 'SELECT') {
            if (el.value) return;
            var wanted = squash(value);
            for (var i = 0; i < el.options.length; i++) {
                if (squash(el.options[i].value) === wanted) {
                    el.value = el.options[i].value;
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                    return;
                }
            }
            // คำนำหน้าในทะเบียนบุคลากรเป็นข้อความอิสระ (เช่น "ผศ.ดร." "ดร." "ว่าที่ ร.ต.")
            // รายการในฟอร์มจึงไม่ครบทุกแบบ — เติมตัวเลือกที่ดึงมาให้เลย
            // มิฉะนั้นช่องคำนำหน้าจะค้างที่ "-- เลือก --" ทั้งที่ดึงข้อมูลมาได้แล้ว
            var opt = document.createElement('option');
            opt.value = value;
            opt.textContent = value;
            el.appendChild(opt);
            el.value = value;
            el.dispatchEvent(new Event('change', { bubbles: true }));
        } else if (el.tagName === 'INPUT' && !el.value) {
            el.value = value;
            el.dispatchEvent(new Event('input', { bubbles: true }));
        }
    }
})();
