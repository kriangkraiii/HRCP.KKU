/**
 * Position Document Auto-fill
 * Auto-fills applicant info from user profile + cross-document data (doc 1).
 * Only fills EMPTY fields — never overwrites existing/saved data.
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
    });

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

    function fillSelect(name, value) {
        var el = document.querySelector('[name="' + name + '"]');
        if (el && el.tagName === 'SELECT' && !el.value) {
            // Try exact match
            for (var i = 0; i < el.options.length; i++) {
                if (el.options[i].value === value) {
                    el.value = value;
                    el.dispatchEvent(new Event('change'));
                    return;
                }
            }
        } else if (el && el.tagName === 'INPUT' && !el.value) {
            el.value = value;
        }
    }
})();
