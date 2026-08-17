/*
 * Button behaviour for the error pages (error.html, error/404.html, error/500.html).
 *
 * This lives in an external file on purpose. The error pages are rendered on the
 * container's ERROR dispatch, which SecurityHeadersFilter does not run on — so no
 * CSP nonce is injected into their markup, and both inline `onclick=` attributes
 * and inline <script> blocks are refused by `script-src 'self' 'nonce-...'`.
 * An external file under /js/ is allowed by the 'self' source with no nonce needed.
 *
 * Each handler is attached only if its button exists, so all three pages can share
 * this one file.
 */
(function () {
    'use strict';

    var backBtn = document.getElementById('backBtn');
    if (backBtn) {
        backBtn.addEventListener('click', function () {
            history.back();
        });
    }

    var retryBtn = document.getElementById('retryBtn');
    if (retryBtn) {
        retryBtn.addEventListener('click', function () {
            location.reload();
        });
    }
})();
