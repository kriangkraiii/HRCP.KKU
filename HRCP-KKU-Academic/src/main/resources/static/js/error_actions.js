/*
 * Button behaviour for the error pages (error.html, error/403.html, error/404.html, error/500.html).
 *
 * This lives in an external file on purpose. The error pages are rendered on the
 * container's ERROR dispatch, which SecurityHeadersFilter does not run on — so no
 * CSP nonce is injected into their markup, and both inline `onclick=` attributes
 * and inline <script> blocks are refused by `script-src 'self' 'nonce-...'`.
 * An external file under /js/ is allowed by the 'self' source with no nonce needed.
 *
 * Each handler is attached only if its button exists, so all error pages can share
 * this one file.
 */
(function () {
    'use strict';

    function canSafelyGoBack() {
        if (window.history.length <= 1 || !document.referrer) {
            return false;
        }
        try {
            var refUrl = new URL(document.referrer, window.location.origin);
            return refUrl.origin === window.location.origin
                && (refUrl.pathname + refUrl.search) !== (window.location.pathname + window.location.search);
        } catch (e) {
            return false;
        }
    }

    var backBtn = document.getElementById('backBtn');
    if (backBtn) {
        backBtn.addEventListener('click', function () {
            if (canSafelyGoBack()) {
                window.history.back();
            } else {
                window.location.assign('/');
            }
        });
    }

    var retryBtn = document.getElementById('retryBtn');
    if (retryBtn) {
        retryBtn.addEventListener('click', function () {
            // Use clean same-origin GET navigation to avoid POST form resubmission prompts
            var target = (window.location.pathname || '/') + (window.location.search || '');
            if (target.indexOf('/') !== 0 || target.indexOf('//') === 0) {
                target = '/';
            }
            window.location.assign(target);
        });
    }
})();
