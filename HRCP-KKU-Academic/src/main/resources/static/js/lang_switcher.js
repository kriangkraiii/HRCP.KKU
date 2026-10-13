/*
 * Language switcher (TH <-> EN) backed by the Google Translate website widget.
 *
 * Shared by templates/academic/base_academic.html and templates/guest/login.html.
 * Expected markup: #google_translate_element, #langDropdownBtn, #langDropdownIcon,
 * #langDropdownMenu (.lang-dropdown-item[data-lang]), #langCurrentLabel,
 * #langLoadingOverlay, #langLoadingTitle, #langLoadingSub.
 *
 * Why it is built this way:
 * - Google intermittently answers element.js with a 302 to google.com/sorry (rate limit
 *   for a shared campus IP). The CSP blocks that redirect, so the script fails to load.
 *   We alternate between the two official hosts and retry with backoff.
 * - The widget is only loaded when it is actually needed (user picks EN, or the
 *   googtrans cookie says EN), which keeps the request volume to Google low.
 * - The loading overlay follows the real translation progress (Thai characters left in
 *   the DOM + DOM quiet period) instead of a fixed timer.
 */
(function () {
    'use strict';

    var SCRIPT_URLS = [
        'https://translate.google.com/translate_a/element.js?cb=googleTranslateElementInit',
        'https://translate.googleapis.com/translate_a/element.js?cb=googleTranslateElementInit'
    ];
    var MAX_SCRIPT_ATTEMPTS = 6;
    var WIDGET_READY_TIMEOUT_MS = 20000;
    var TRANSLATE_TIMEOUT_MS = 12000;
    var DOM_QUIET_MS = 500;
    var THAI_CHARS = /[\u0E00-\u0E7F]/g;

    var enginePromise = null;
    var originalThai = null; // Thai chars on the untranslated page, baseline for progress
    var busy = false;

    window.googleTranslateElementInit = function () {
        try {
            // Default layout on purpose: it is the only one that renders <select class="goog-te-combo">,
            // which we drive programmatically (InlineLayout.SIMPLE renders a link + iframe instead).
            new google.translate.TranslateElement({
                pageLanguage: 'th',
                includedLanguages: 'en,th',
                autoDisplay: false
            }, 'google_translate_element');
        } catch (e) {
            console.warn('[LangSwitcher] Google Translate init failed:', e);
        }
    };

    // ---------- Cookie helpers ----------
    function getCookie(name) {
        var match = document.cookie.match(new RegExp('(^|;\\s*)' + name + '=([^;]*)'));
        return match ? decodeURIComponent(match[2]) : null;
    }

    function cookieDomains() {
        return ['', window.location.hostname, '.' + window.location.hostname];
    }

    function setGoogTransCookie(lang) {
        var expired = 'googtrans=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/';
        cookieDomains().forEach(function (d) {
            document.cookie = expired + (d ? '; domain=' + d : '');
            if (lang === 'en') {
                document.cookie = 'googtrans=/th/en; path=/' + (d ? '; domain=' + d : '');
            }
        });
    }

    function cookieLang() {
        var value = getCookie('googtrans');
        return value && value.indexOf('/en') !== -1 ? 'en' : 'th';
    }

    // ---------- Engine loading (script + widget <select>) ----------
    function injectScript(attempt) {
        return new Promise(function (resolve, reject) {
            var script = document.createElement('script');
            script.src = SCRIPT_URLS[attempt % SCRIPT_URLS.length];
            script.async = true;
            script.onload = resolve;
            script.onerror = function () {
                script.remove();
                reject(new Error('script load failed'));
            };
            document.head.appendChild(script);
        });
    }

    function loadScriptWithRetry(onAttempt) {
        var attempt = 0;
        function tryOnce() {
            onAttempt(attempt);
            return injectScript(attempt).catch(function (err) {
                attempt++;
                if (attempt >= MAX_SCRIPT_ATTEMPTS) throw err;
                return new Promise(function (r) { setTimeout(r, 400 * attempt); }).then(tryOnce);
            });
        }
        return tryOnce();
    }

    function waitFor(predicate, timeoutMs) {
        return new Promise(function (resolve, reject) {
            var started = Date.now();
            (function poll() {
                var value = predicate();
                if (value) return resolve(value);
                if (Date.now() - started > timeoutMs) return reject(new Error('timeout'));
                setTimeout(poll, 100);
            })();
        });
    }

    function findCombo() {
        var combo = document.querySelector('#google_translate_element select.goog-te-combo');
        return combo && combo.options.length > 0 ? combo : null;
    }

    /** Resolves with the widget's <select> once Google Translate is fully usable. */
    function loadEngine(onStage) {
        if (enginePromise) return enginePromise;
        enginePromise = loadScriptWithRetry(function (attempt) {
            onStage('script', attempt);
        })
            .then(function () {
                onStage('widget');
                return waitFor(findCombo, WIDGET_READY_TIMEOUT_MS);
            })
            .catch(function (err) {
                enginePromise = null; // allow a later retry
                throw err;
            });
        return enginePromise;
    }

    // ---------- Translation progress tracking ----------
    function isVisible(el) {
        return el.checkVisibility ? el.checkVisibility() : el.offsetParent !== null;
    }

    /** Thai characters in visible page text (our own switcher UI excluded). */
    function countThaiChars(skipRoot) {
        if (!document.body) return 0;
        var visibility = new Map();
        var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
            acceptNode: function (node) {
                var parent = node.parentElement;
                if (!parent || /^(SCRIPT|STYLE|NOSCRIPT|TEXTAREA)$/.test(parent.nodeName)) {
                    return NodeFilter.FILTER_REJECT;
                }
                if (skipRoot && skipRoot.contains(parent)) return NodeFilter.FILTER_REJECT;
                if (parent.closest('.notranslate,[translate="no"]')) return NodeFilter.FILTER_REJECT;
                if (!visibility.has(parent)) visibility.set(parent, isVisible(parent));
                return visibility.get(parent) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
            }
        });
        var total = 0;
        var node;
        while ((node = walker.nextNode())) {
            var m = node.nodeValue.match(THAI_CHARS);
            if (m) total += m.length;
        }
        return total;
    }

    function isTranslatedClass() {
        var cls = document.documentElement.classList;
        return cls.contains('translated-ltr') || cls.contains('translated-rtl');
    }

    /**
     * Resolves once the page content reflects {@code lang} and the DOM has been quiet for
     * DOM_QUIET_MS. onProgress(0..1) reports the measured share of text already switched.
     * originalThai = Thai chars of the untranslated page (used to measure the TH restore).
     */
    function waitForTranslation(lang, originalThai, ignoreRoot, onProgress) {
        return new Promise(function (resolve, reject) {
            var startThai = countThaiChars(ignoreRoot);
            var lastMutation = Date.now();
            var sawContentChange = false;
            var started = Date.now();

            var observer = new MutationObserver(function (mutations) {
                for (var i = 0; i < mutations.length; i++) {
                    if (ignoreRoot && ignoreRoot.contains(mutations[i].target)) continue;
                    lastMutation = Date.now();
                    sawContentChange = true;
                    return;
                }
            });
            observer.observe(document.body, { childList: true, subtree: true, characterData: true });

            var timer = setInterval(function () {
                var current = countThaiChars(ignoreRoot);
                var progress;
                if (lang === 'en') {
                    progress = startThai > 0 ? 1 - current / startThai : (isTranslatedClass() ? 1 : 0);
                } else {
                    progress = originalThai > 0 ? current / originalThai : (sawContentChange ? 1 : 0);
                }
                onProgress(Math.max(0, Math.min(1, progress)));

                var quiet = Date.now() - lastMutation >= DOM_QUIET_MS;
                var reached = false;
                if (lang === 'en') {
                    reached = isTranslatedClass() && (sawContentChange || progress >= 0.5 || startThai === 0 || (Date.now() - started >= 1000 && quiet));
                } else {
                    reached = sawContentChange || progress >= 0.9 || (Date.now() - started >= 1000 && quiet);
                }
                if (reached && quiet) {
                    finish(resolve);
                } else if (Date.now() - started > TRANSLATE_TIMEOUT_MS) {
                    if (isTranslatedClass() || progress > 0 || lang === 'th') finish(resolve);
                    else finish(reject, new Error('translate timeout'));
                }
            }, 200);

            function finish(fn, arg) {
                clearInterval(timer);
                observer.disconnect();
                fn(arg);
            }
        });
    }

    // ---------- UI ----------
    document.addEventListener('DOMContentLoaded', function () {
        var btn = document.getElementById('langDropdownBtn');
        var icon = document.getElementById('langDropdownIcon');
        var menu = document.getElementById('langDropdownMenu');
        var label = document.getElementById('langCurrentLabel');
        var overlay = document.getElementById('langLoadingOverlay');
        var titleEl = document.getElementById('langLoadingTitle');
        var subEl = document.getElementById('langLoadingSub');
        var items = menu ? Array.prototype.slice.call(menu.querySelectorAll('.lang-dropdown-item')) : [];
        if (!btn || !menu) return;

        // Our own UI must never be rewritten by Google Translate.
        [btn, menu, overlay].forEach(function (el) {
            if (el) { el.classList.add('notranslate'); el.setAttribute('translate', 'no'); }
        });
        var spinnerIcon = overlay ? overlay.querySelector('.lang-loading-spinner i') : null;
        var activeLang = cookieLang();

        function renderActive(lang) {
            activeLang = lang;
            if (label) label.textContent = lang.toUpperCase();
            items.forEach(function (it) { it.classList.toggle('active', it.dataset.lang === lang); });
        }

        function setText(title, sub) {
            if (titleEl) titleEl.textContent = title;
            if (subEl) subEl.textContent = sub;
        }

        function setBusy(on, withOverlay) {
            busy = on;
            if (icon) icon.className = on ? 'fas fa-circle-notch fa-spin lang-icon' : 'fas fa-language lang-icon';
            btn.setAttribute('aria-busy', on ? 'true' : 'false');
            btn.disabled = on;
            if (overlay && withOverlay !== undefined) {
                overlay.classList.toggle('active', withOverlay);
                overlay.setAttribute('aria-hidden', withOverlay ? 'false' : 'true');
            }
            if (spinnerIcon) spinnerIcon.className = 'fas fa-circle-notch fa-spin';
        }

        function stageText(lang, stage, attempt) {
            var en = lang === 'en';
            if (stage === 'script') {
                setText(en ? 'Switching to English...' : 'กำลังเปลี่ยนเป็นภาษาไทย...',
                    attempt > 0
                        ? 'กำลังเชื่อมต่อบริการแปลภาษาอีกครั้ง (ครั้งที่ ' + (attempt + 1) + '/' + MAX_SCRIPT_ATTEMPTS + ')'
                        : 'กำลังเชื่อมต่อบริการแปลภาษา...');
            } else if (stage === 'widget') {
                setText(en ? 'Switching to English...' : 'กำลังเปลี่ยนเป็นภาษาไทย...', 'กำลังเตรียมตัวแปลภาษา...');
            }
        }

        function showError() {
            setText('เปลี่ยนภาษาไม่สำเร็จ', 'เชื่อมต่อบริการแปลภาษา (Google Translate) ไม่ได้ กรุณาลองใหม่อีกครั้ง');
            if (spinnerIcon) spinnerIcon.className = 'fas fa-exclamation-triangle';
            var timer = null;
            var close = function () {
                clearTimeout(timer);
                setBusy(false, false);
                if (overlay) overlay.removeEventListener('click', close);
            };
            if (overlay) overlay.addEventListener('click', close);
            timer = setTimeout(close, 3500);
        }

        function notifyChange(lang) {
            try {
                window.dispatchEvent(new CustomEvent('app:language-change', { detail: { lang: lang } }));
            } catch (e) { /* old browsers */ }
        }

        /** Runs the switch; withOverlay=false is used for the silent auto-translate on page load. */
        function translateTo(lang, withOverlay) {
            setBusy(true, withOverlay);
            stageText(lang, 'script', 0);
            if (originalThai === null) originalThai = countThaiChars(overlay);

            return loadEngine(function (stage, attempt) { stageText(lang, stage, attempt); })
                .then(function (combo) {
                    setText(lang === 'en' ? 'Translating to English...' : 'กำลังเปลี่ยนเป็นภาษาไทย...',
                        'กำลังแปลเนื้อหา... 0%');
                    if (combo.value !== lang) {
                        combo.value = lang;
                        combo.dispatchEvent(new Event('change'));
                    } else if (lang === 'en' && !isTranslatedClass()) {
                        combo.dispatchEvent(new Event('change'));
                    }
                    return waitForTranslation(lang, originalThai, overlay, function (p) {
                        if (subEl) subEl.textContent = 'กำลังแปลเนื้อหา... ' + Math.round(p * 100) + '%';
                    });
                })
                .then(function () {
                    if (subEl) subEl.textContent = 'เสร็จสิ้น 100%';
                    setGoogTransCookie(lang);
                    renderActive(lang);
                    notifyChange(lang);
                    setTimeout(function () { setBusy(false, false); }, 250);
                })
                .catch(function (err) {
                    console.warn('[LangSwitcher] switch to ' + lang + ' failed:', err);
                    renderActive(cookieLang());
                    if (withOverlay) showError(); else setBusy(false, false);
                });
        }

        function selectLang(lang) {
            closeMenu(false);
            if (busy || lang === activeLang) return;

            // Widget was never loaded (or failed): the page is still in Thai, nothing to undo.
            if (lang === 'th' && !enginePromise && !isTranslatedClass()) {
                setGoogTransCookie('th');
                renderActive('th');
                notifyChange('th');
                return;
            }
            translateTo(lang, true);
        }

        function openMenu() {
            menu.classList.add('show');
            btn.setAttribute('aria-expanded', 'true');
            var first = menu.querySelector('.lang-dropdown-item');
            if (first) first.focus();
        }

        function closeMenu(restoreFocus) {
            if (!menu.classList.contains('show')) return;
            menu.classList.remove('show');
            btn.setAttribute('aria-expanded', 'false');
            if (restoreFocus !== false) btn.focus();
        }

        renderActive(activeLang);

        btn.addEventListener('click', function (e) {
            e.stopPropagation();
            if (menu.classList.contains('show')) closeMenu(); else openMenu();
        });
        btn.addEventListener('keydown', function (e) {
            if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                e.preventDefault();
                if (!menu.classList.contains('show')) openMenu();
            } else if (e.key === 'Escape') {
                closeMenu();
            }
        });

        items.forEach(function (item, idx) {
            item.addEventListener('click', function (e) {
                e.stopPropagation();
                selectLang(this.dataset.lang);
            });
            item.addEventListener('keydown', function (e) {
                if (e.key === 'ArrowDown') {
                    e.preventDefault();
                    (items[idx + 1] || items[0]).focus();
                } else if (e.key === 'ArrowUp') {
                    e.preventDefault();
                    (items[idx - 1] || items[items.length - 1]).focus();
                } else if (e.key === 'Escape') {
                    closeMenu();
                } else if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    selectLang(this.dataset.lang);
                }
            });
        });

        document.addEventListener('click', function () { closeMenu(false); });

        // User chose English earlier: translate this page too (button spinner only, no blocking overlay).
        if (activeLang === 'en') translateTo('en', false);
    });
})();
