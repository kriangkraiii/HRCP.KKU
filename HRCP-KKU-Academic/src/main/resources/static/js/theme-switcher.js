/**
 * Theme Switcher — Light / Dark / System
 * Persists preference per-user via server API (DB-backed).
 * Falls back to server-rendered window.USER_THEME_PREFERENCE.
 */
(function () {
    'use strict';

    /**
     * Get the user's preference from server-rendered variable.
     */
    function getPreference() {
        return window.USER_THEME_PREFERENCE || 'light';
    }

    /**
     * Resolve the effective theme based on preference.
     */
    function resolveTheme(pref) {
        if (pref === 'system') {
            return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
        }
        return pref;
    }

    /**
     * Apply theme to document.
     */
    function applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
    }

    /**
     * Save preference to server via toggle-setting API.
     */
    function saveToServer(pref) {
        // Detect base path: /user/academic/settings or /admin/academic/settings
        var isAdmin = window.location.pathname.indexOf('/admin/') !== -1;
        var basePath = isAdmin ? '/admin/academic/settings' : '/user/academic/settings';

        // Read CSRF token
        var csrfMeta = document.querySelector('meta[name="_csrf"]');
        var csrfInput = document.querySelector('input[name="_csrf"]');
        var token = '';
        if (csrfMeta) token = csrfMeta.content || '';
        else if (csrfInput) token = csrfInput.value || '';
        if (!token) {
            var match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
            if (match) token = decodeURIComponent(match[1]);
        }

        fetch(basePath + '/toggle-setting', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-XSRF-TOKEN': token
            },
            body: 'key=themePreference&value=' + encodeURIComponent(pref)
        }).catch(function () {
            // Silent fail — theme is already applied visually
        });
    }

    /**
     * Set preference: apply immediately + save to server.
     */
    function setPreference(pref) {
        window.USER_THEME_PREFERENCE = pref;
        applyTheme(resolveTheme(pref));
        updateUI(pref);
        saveToServer(pref);
    }

    /**
     * Update the settings UI buttons (if on settings page).
     */
    function updateUI(pref) {
        var buttons = document.querySelectorAll('.theme-option');
        if (!buttons.length) return;
        buttons.forEach(function (btn) {
            if (btn.dataset.theme === pref) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });
    }

    // Apply immediately on script load
    var currentPref = getPreference();
    applyTheme(resolveTheme(currentPref));

    // Listen for OS theme changes (when preference is 'system')
    var mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    mediaQuery.addEventListener('change', function () {
        if (getPreference() === 'system') {
            applyTheme(resolveTheme('system'));
        }
    });

    // On DOM ready, bind button events
    document.addEventListener('DOMContentLoaded', function () {
        updateUI(currentPref);

        document.querySelectorAll('.theme-option').forEach(function (btn) {
            btn.addEventListener('click', function () {
                setPreference(this.dataset.theme);
            });
        });
    });

    // Expose globally for external use
    window.ThemeSwitcher = {
        get: getPreference,
        set: setPreference
    };
})();
