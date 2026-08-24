/*
 * Replaces inline HTML event attributes across the whole app.
 *
 * The CSP is `script-src 'self' 'nonce-...'` with no 'unsafe-inline', so inline
 * attributes like onclick="foo()" are refused by the browser. A nonce cannot
 * rescue them: nonces apply to <script> elements, never to event-handler
 * attributes, and per CSP3 the presence of a nonce makes the browser ignore
 * 'unsafe-inline' entirely. The only fix is to stop using the attributes.
 *
 * So markup declares intent with data-* attributes and the behaviour lives here.
 * Everything is delegated from the document, which also covers elements that
 * JavaScript creates later.
 *
 * Loaded from academic/base_academic.html, the layout behind every page.
 *
 * ── The data-call dispatcher ────────────────────────────────────────────────
 * Most converted handlers were simply "call a global function, maybe passing
 * something about the element". That shape is expressed declaratively:
 *
 *   <button data-call="showCreateFolderModal">                 -> fn()
 *   <button data-call="renameFilePrompt" data-call-arg="self"> -> fn(el)
 *   <input  data-call-change="uploadFiles" data-call-arg="files">
 *   <input  data-call-change="toggle2FA"   data-call-arg="checked">
 *   <a data-call="deleteFolderPrompt" data-call-arg="closest:.folder-card">
 *   <button data-call="addWorkRow" data-call-args='["rows","title","used"]'>
 *
 * data-call fires on click, data-call-change on change, data-call-input on input.
 * The function must already be a global (window.x) — the dispatcher only looks
 * names up, it never evaluates strings, so no code is constructed at runtime.
 * Names come from our own templates, never from user data; do not wire
 * data-call to a value that a user can influence.
 */
(function () {
    'use strict';

    /* Resolve the single argument a handler wants from its element. */
    function resolveArg(el) {
        var spec = el.dataset.callArg;
        if (!spec) return undefined;
        if (spec === 'self') return el;
        if (spec === 'files') return el.files;
        if (spec === 'checked') return el.checked;
        if (spec === 'value') return el.value;
        if (spec.indexOf('closest:') === 0) return el.closest(spec.slice(8));
        return undefined;
    }

    /* Supports plain globals and one level of dotted access ("ScopusPicker.open"). */
    function resolveFn(name) {
        var parts = name.split('.');
        var ctx = window;
        for (var i = 0; i < parts.length - 1; i++) {
            ctx = ctx[parts[i]];
            if (!ctx) return null;
        }
        var fn = ctx[parts[parts.length - 1]];
        return typeof fn === 'function' ? { fn: fn, ctx: ctx } : null;
    }

    function invoke(el, name) {
        var found = resolveFn(name);
        if (!found) {
            // Loud on purpose: a typo or a renamed function would otherwise be a
            // button that silently does nothing.
            console.error('[csp_fallbacks] no function named "' + name + '"');
            return;
        }
        var args = [];
        var rawArgs = el.dataset.callArgs;
        if (rawArgs) {
            try {
                args = JSON.parse(rawArgs);
            } catch (err) {
                console.error('[csp_fallbacks] bad data-call-args on', el, err);
                return;
            }
        }
        // data-call-arg may be combined with data-call-args, in which case the
        // element-derived value is appended — covers toggleCourse(2, this.checked).
        var arg = resolveArg(el);
        if (arg !== undefined) args.push(arg);
        found.fn.apply(found.ctx, args);
    }

    document.addEventListener('click', function (e) {
        var el = e.target.closest('[data-call]');
        if (!el) return;
        // These were nearly all <button> or href="#" anchors that ended in
        // "return false" or preventDefault().
        if (el.tagName === 'A' || el.dataset.callPrevent === '1') e.preventDefault();
        invoke(el, el.dataset.call);
    });

    document.addEventListener('change', function (e) {
        var el = e.target.closest('[data-call-change]');
        if (!el) return;
        invoke(el, el.dataset.callChange);
    });

    document.addEventListener('input', function (e) {
        var el = e.target.closest('[data-call-input]');
        if (!el) return;
        invoke(el, el.dataset.callInput);
    });

    /*
     * Image fallbacks — replaces onerror="this.src='...'".
     *
     * The `error` event does NOT bubble, so a delegated listener only sees it in
     * the capture phase. Two shapes are supported:
     *   data-fallback="/img/x.png"          -> swap src
     *   data-fallback-mode="hide-show-next" -> hide img, reveal next sibling
     */
    document.addEventListener(
        'error',
        function (e) {
            var img = e.target;
            if (!img || img.tagName !== 'IMG') return;

            if (img.dataset.fallbackMode === 'hide-show-next') {
                img.style.display = 'none';
                var next = img.nextElementSibling;
                if (next) next.style.display = 'flex';
                return;
            }

            var fallback = img.dataset.fallback;
            if (!fallback) return;
            // Guard against a loop when the fallback image is itself missing.
            if (img.dataset.fallbackApplied === '1') return;
            img.dataset.fallbackApplied = '1';
            img.src = fallback;
        },
        true
    );

    /*
     * Global in-app confirm dialog — replaces browser native window.confirm.
     * Accessible via window.appConfirm(message, options) -> Promise<boolean>.
     */
    function getConfirmModalInstance() {
        var el = document.getElementById('appGlobalConfirmModal');
        if (!el) return null;
        if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
            return bootstrap.Modal.getOrCreateInstance(el, { backdrop: 'static', keyboard: true });
        }
        return null;
    }

    window.appConfirm = function (message, options) {
        options = options || {};
        var title = options.title || 'ยืนยันการดำเนินการ';
        var confirmText = options.confirmText || 'ยืนยัน';
        var cancelText = options.cancelText || 'ยกเลิก';
        var variant = options.variant;

        if (!variant) {
            var msgLower = (message || '').toLowerCase();
            if (msgLower.indexOf('ลบ') !== -1 || msgLower.indexOf('ยกเลิก') !== -1 || msgLower.indexOf('ปฏิเสธ') !== -1 || msgLower.indexOf('โมฆะ') !== -1 || msgLower.indexOf('deactivate') !== -1 || msgLower.indexOf('delete') !== -1) {
                variant = 'danger';
            } else if (msgLower.indexOf('คำเตือน') !== -1 || msgLower.indexOf('ระวัง') !== -1 || msgLower.indexOf('warn') !== -1) {
                variant = 'warning';
            } else {
                variant = 'primary';
            }
        }

        return new Promise(function (resolve) {
            var modalEl = document.getElementById('appGlobalConfirmModal');
            if (!modalEl || typeof bootstrap === 'undefined') {
                resolve(window.confirm(message));
                return;
            }

            var modal = getConfirmModalInstance();
            var titleEl = document.getElementById('appGlobalConfirmModalLabel');
            var msgEl = document.getElementById('confirmModalMessage');
            var okBtn = document.getElementById('confirmModalOkBtn');
            var cancelBtn = document.getElementById('confirmModalCancelBtn');
            var iconBadge = document.getElementById('confirmModalIconBadge');
            var iconEl = document.getElementById('confirmModalIcon');
            var accentBar = document.getElementById('confirmModalAccentBar');

            if (titleEl) titleEl.textContent = title;
            if (msgEl) msgEl.textContent = message;
            if (okBtn) okBtn.textContent = confirmText;
            if (cancelBtn) cancelBtn.textContent = cancelText;

            if (accentBar) accentBar.className = 'confirm-modal-accent-bar ' + (variant !== 'primary' ? variant : '');
            if (iconBadge) iconBadge.className = 'confirm-modal-icon-badge ' + (variant !== 'primary' ? variant : '');
            if (okBtn) {
                okBtn.className = 'btn px-4 py-2 shadow-sm ' + (variant === 'danger' ? 'btn-danger' : (variant === 'warning' ? 'btn-warning' : 'btn-primary'));
            }

            if (iconEl) {
                if (variant === 'danger') {
                    iconEl.className = 'fas fa-triangle-exclamation';
                } else if (variant === 'warning') {
                    iconEl.className = 'fas fa-exclamation-circle';
                } else if (message.indexOf('ส่ง') !== -1 || message.indexOf('ลงนาม') !== -1) {
                    iconEl.className = 'fas fa-paper-plane';
                } else {
                    iconEl.className = 'fas fa-circle-question';
                }
            }

            var resolved = false;

            function handleConfirm() {
                if (resolved) return;
                resolved = true;
                if (modal) modal.hide();
                resolve(true);
            }

            // Replace OK button with fresh listener to prevent stacking
            if (okBtn) {
                var newOkBtn = okBtn.cloneNode(true);
                okBtn.parentNode.replaceChild(newOkBtn, okBtn);
                newOkBtn.addEventListener('click', handleConfirm);
            }

            function onHidden() {
                modalEl.removeEventListener('hidden.bs.modal', onHidden);
                if (!resolved) {
                    resolved = true;
                    resolve(false);
                }
            }
            modalEl.addEventListener('hidden.bs.modal', onHidden);

            if (modal) modal.show();
        });
    };

    /*
     * Form confirmations — replaces onsubmit="return confirm('...')".
     * Intercepts forms with data-confirm and displays the in-app confirmation modal.
     */
    document.addEventListener('submit', function (e) {
        var form = e.target;
        if (!form || !form.dataset) return;
        var message = form.dataset.confirm;
        if (!message) return;

        if (form._inAppConfirmed) {
            form._inAppConfirmed = false;
            return;
        }

        e.preventDefault();
        e.stopImmediatePropagation();

        var title = form.dataset.confirmTitle || 'ยืนยันการดำเนินการ';
        var okText = form.dataset.confirmOk || (form.dataset.confirmVariant === 'danger' ? 'ยืนยันลบ' : 'ยืนยัน');

        window.appConfirm(message, {
            title: title,
            confirmText: okText,
            variant: form.dataset.confirmVariant
        }).then(function (confirmed) {
            if (confirmed) {
                form._inAppConfirmed = true;
                if (typeof form.requestSubmit === 'function') {
                    form.requestSubmit();
                } else {
                    form.submit();
                }
            }
        });
    });

    /*
     * Button & Link confirmations with data-confirm.
     */
    document.addEventListener('click', function (e) {
        var trigger = e.target.closest('[data-confirm]');
        if (!trigger || trigger.tagName === 'FORM') return;

        // If inside a form with data-confirm and this is a submit button, let submit handler handle it
        if (trigger.type === 'submit' && trigger.form && trigger.form.dataset && trigger.form.dataset.confirm) {
            return;
        }

        var message = trigger.dataset.confirm;
        if (!message) return;

        if (trigger._inAppConfirmed) {
            trigger._inAppConfirmed = false;
            return;
        }

        e.preventDefault();
        e.stopImmediatePropagation();

        var title = trigger.dataset.confirmTitle || 'ยืนยันการดำเนินการ';
        var okText = trigger.dataset.confirmOk || 'ยืนยัน';

        window.appConfirm(message, {
            title: title,
            confirmText: okText,
            variant: trigger.dataset.confirmVariant
        }).then(function (confirmed) {
            if (confirmed) {
                trigger._inAppConfirmed = true;
                if (trigger.tagName === 'A' && trigger.href && trigger.href !== '#' && !trigger.href.startsWith('javascript:')) {
                    window.location.href = trigger.href;
                } else if (trigger.form && trigger.type === 'submit') {
                    trigger.form.submit();
                } else {
                    trigger.click();
                }
            }
        });
    });

    /*
     * Generic row/block removal —
     * replaces onclick="this.closest('.border').remove()", including on buttons
     * injected via innerHTML by the position document forms.
     */
    document.addEventListener('click', function (e) {
        var btn = e.target.closest('[data-action="removeClosest"]');
        if (!btn) return;
        e.preventDefault();
        var selector = btn.dataset.closest;
        if (!selector) return;
        var target = btn.closest(selector);
        if (target) target.remove();
    });

    /* Elements that exist only to swallow the click — was onclick="return false;". */
    document.addEventListener('click', function (e) {
        if (e.target.closest('[data-action="noop"]')) e.preventDefault();
    });

    /* Was onclick="event.stopPropagation();" on controls inside clickable rows. */
    document.addEventListener('click', function (e) {
        if (e.target.closest('[data-stop-propagation]')) e.stopPropagation();
    });

    /*
     * Proxy a click onto another element — was
     * onclick="document.getElementById('fileUploadInput').click()", the visible
     * button that opens a hidden <input type="file">.
     */
    document.addEventListener('click', function (e) {
        var btn = e.target.closest('[data-click-target]');
        if (!btn) return;
        e.preventDefault();
        var target = document.querySelector(btn.dataset.clickTarget);
        if (target) target.click();
    });

    /*
     * Write a fixed value into another field on change — was
     * onchange="document.getElementById('notWish').value='☐'" on the paired
     * radio buttons in the position forms.
     */
    document.addEventListener('change', function (e) {
        var el = e.target.closest('[data-set-value-target]');
        if (!el) return;
        var target = document.querySelector(el.dataset.setValueTarget);
        if (target) target.value = el.dataset.setValue || '';
    });
})();
