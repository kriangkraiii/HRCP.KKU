/**
 * Behaviour for the /search results page.
 *
 * Only one thing needs scripting: ticking a facet or changing the sort should
 * re-run the search without hunting for a submit button. Everything else on the
 * page is a plain form, so it keeps working with this file blocked.
 *
 * The handler is reached through the data-call dispatcher in csp_fallbacks.js
 * rather than an inline onchange. The Content-Security-Policy has no
 * 'unsafe-inline' and a nonce cannot rescue an event attribute, so an inline
 * handler here would simply never fire.
 */
(function () {
    'use strict';

    /**
     * Re-runs the search, always from page one.
     *
     * Resetting the page is the point: narrowing a result set while sitting on
     * page 4 usually leaves fewer than four pages, and the browser would land on
     * an empty page that looks like "no results".
     */
    window.submitSearchForm = function submitSearchForm() {
        var form = document.getElementById('searchForm');
        if (!form) return;

        var page = form.querySelector('input[name="page"]');
        if (!page) {
            page = document.createElement('input');
            page.type = 'hidden';
            page.name = 'page';
            form.appendChild(page);
        }
        page.value = '0';

        form.submit();
    };
})();
