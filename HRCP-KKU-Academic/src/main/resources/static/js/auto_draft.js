/**
 * Auto-Draft Engine — บันทึกแบบร่างอัตโนมัติ (Google Forms Style)
 *
 * Usage:
 *   1. <form id="myForm" data-auto-draft="/api/draft/position/123/1"> ... </form>
 *   2. <script> window.AUTO_DRAFT_ENABLED = true; </script>
 *   3. <script src="/js/auto_draft.js"></script>
 *
 * The engine binds to all forms with [data-auto-draft] attribute.
 * Debounce: 5 seconds after last input change.
 * Only saves when form data actually changed (dirty tracking).
 * Shows a Google Forms-style persistent status indicator inside each form.
 */
(function () {
  "use strict";

  var DEBOUNCE_MS = 5000;

  // ==================== CSS (injected once) ====================
  var styleInjected = false;
  function injectStyles() {
    if (styleInjected) return;
    styleInjected = true;
    var css = document.createElement("style");
    css.textContent =
      ".auto-draft-bar{display:flex;align-items:center;gap:8px;padding:8px 14px;border-radius:8px;font-size:0.82rem;font-family:'Sarabun',sans-serif;font-weight:500;margin-bottom:12px;transition:all .3s ease}" +
      ".auto-draft-bar.ad-idle{background:#f0fdf4;color:#166534;border:1px solid #bbf7d0}" +
      ".auto-draft-bar.ad-saving{background:#fffbeb;color:#92400e;border:1px solid #fde68a}" +
      ".auto-draft-bar.ad-saved{background:#f0fdf4;color:#166534;border:1px solid #bbf7d0}" +
      ".auto-draft-bar.ad-error{background:#fef2f2;color:#991b1b;border:1px solid #fecaca}" +
      ".auto-draft-bar.ad-disabled{background:#f3f4f6;color:#6b7280;border:1px solid #e5e7eb}" +
      ".auto-draft-bar .ad-icon{font-size:1rem;width:18px;text-align:center}" +
      ".auto-draft-bar .ad-text{flex:1}" +
      ".auto-draft-bar .ad-time{font-size:0.75rem;opacity:0.7}";
    document.head.appendChild(css);
  }

  // ==================== AutoDraftEngine Class ====================
  function AutoDraftEngine(form) {
    this.form = form;
    this.endpoint = form.getAttribute("data-auto-draft");
    this.timer = null;
    this.lastHash = "";
    this.saving = false;
    this.bar = null;
    this._init();
  }

  AutoDraftEngine.prototype._init = function () {
    if (!this.endpoint) return;

    injectStyles();
    this._createBar();

    // Show disabled state if auto-draft is off
    if (!window.AUTO_DRAFT_ENABLED) {
      this._setState("disabled");
      return;
    }

    // Compute initial hash
    this.lastHash = this._computeHash();
    this._setState("idle");

    // Bind input/change events
    var self = this;
    ["input", "change"].forEach(function (evt) {
      self.form.addEventListener(evt, function () { self._onFormChange(); }, true);
    });
  };

  AutoDraftEngine.prototype._createBar = function () {
    this.bar = document.createElement("div");
    this.bar.className = "auto-draft-bar ad-idle";
    this.bar.innerHTML =
      '<i class="ad-icon fas fa-cloud"></i>' +
      '<span class="ad-text"></span>' +
      '<span class="ad-time"></span>';

    // Insert at the top of the form
    if (this.form.firstChild) {
      this.form.insertBefore(this.bar, this.form.firstChild);
    } else {
      this.form.appendChild(this.bar);
    }
  };

  AutoDraftEngine.prototype._setState = function (state, extra) {
    if (!this.bar) return;

    // Reset all state classes
    this.bar.className = "auto-draft-bar ad-" + state;

    var icon = this.bar.querySelector(".ad-icon");
    var text = this.bar.querySelector(".ad-text");
    var time = this.bar.querySelector(".ad-time");

    switch (state) {
      case "idle":
        icon.className = "ad-icon fas fa-cloud";
        text.textContent = "บันทึกแบบร่างอัตโนมัติเปิดใช้งาน";
        time.textContent = "";
        break;
      case "saving":
        icon.className = "ad-icon fas fa-spinner fa-spin";
        text.textContent = "กำลังบันทึก...";
        time.textContent = "";
        break;
      case "saved":
        icon.className = "ad-icon fas fa-cloud";
        text.textContent = "บันทึกแบบร่างอัตโนมัติแล้ว";
        time.textContent = extra || "";
        break;
      case "error":
        icon.className = "ad-icon fas fa-exclamation-triangle";
        text.textContent = extra || "บันทึกอัตโนมัติล้มเหลว";
        time.textContent = "";
        break;
      case "disabled":
        icon.className = "ad-icon fas fa-pause-circle";
        text.textContent = "บันทึกแบบร่างอัตโนมัติปิดอยู่";
        time.textContent = "";
        break;
    }
  };

  AutoDraftEngine.prototype._getTimeString = function () {
    var now = new Date();
    var h = now.getHours().toString().padStart(2, "0");
    var m = now.getMinutes().toString().padStart(2, "0");
    return "เมื่อ " + h + ":" + m;
  };

  AutoDraftEngine.prototype._onFormChange = function () {
    if (this.saving) return;
    var self = this;
    clearTimeout(this.timer);
    this.timer = setTimeout(function () { self._save(); }, DEBOUNCE_MS);
  };

  AutoDraftEngine.prototype._computeHash = function () {
    return JSON.stringify(this._collectData());
  };

  AutoDraftEngine.prototype._collectData = function () {
    var data = {};
    var elements = this.form.querySelectorAll("input, select, textarea");
    elements.forEach(function (el) {
      var name = el.name;
      if (!name || name === "_csrf" || name === "action") return;

      if (el.type === "checkbox") {
        if (el.checked) data[name] = el.value || "✓";
      } else if (el.type === "radio") {
        if (el.checked) data[name] = el.value;
      } else {
        if (el.value) data[name] = el.value;
      }
    });
    return data;
  };

  AutoDraftEngine.prototype._save = function () {
    if (!window.AUTO_DRAFT_ENABLED) return;

    var currentHash = this._computeHash();
    if (currentHash === this.lastHash) return;

    this.saving = true;
    this._setState("saving");

    var self = this;
    var csrfEl =
      document.querySelector('input[name="_csrf"]') ||
      document.querySelector('meta[name="_csrf"]');
    var csrfToken = csrfEl
      ? csrfEl.value || csrfEl.content || ""
      : "";

    fetch(this.endpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-TOKEN": csrfToken,
      },
      body: JSON.stringify(this._collectData()),
    })
      .then(function (res) {
        if (res.ok) {
          self.lastHash = currentHash;
          self._setState("saved", self._getTimeString());
        } else {
          self._setState("error");
        }
      })
      .catch(function (e) {
        console.warn("Auto-draft failed:", e);
        self._setState("error");
      })
      .finally(function () {
        self.saving = false;
      });
  };

  // ==================== Initialize ====================
  document.addEventListener("DOMContentLoaded", function () {
    var forms = document.querySelectorAll("form[data-auto-draft]");
    if (forms.length === 0) return;

    forms.forEach(function (form) {
      new AutoDraftEngine(form);
    });
  });
})();
