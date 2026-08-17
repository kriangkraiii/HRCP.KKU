/**
 * Auto-Draft Engine — บันทึกแบบร่างอัตโนมัติ (Google Forms Style)
 *
 * Usage:
 *   1. <form data-auto-draft="/api/draft/position/123/1"> ... </form>
 *   2. <script> window.AUTO_DRAFT_ENABLED = true; </script>
 *   3. <script src="/js/auto_draft.js"></script>
 *
 * Debounce: 1.5 seconds after last input change.
 * Dirty tracking: only saves when data actually changed.
 */
(function () {
  "use strict";

  var DEBOUNCE_MS = 1000;

  /* ---------- CSS (injected once) ---------- */
  var styleInjected = false;
  function injectStyles() {
    if (styleInjected) return;
    styleInjected = true;
    var s = document.createElement("style");
    s.textContent =
      ".adb{display:flex;align-items:center;gap:10px;padding:10px 16px;border-radius:8px;font-size:.85rem;font-weight:500;margin-bottom:14px;transition:all .3s;line-height:1.4}" +
      ".adb--idle{background:#f0fdf4;color:#15803d;border:1px solid #86efac}" +
      ".adb--saving{background:#fffbeb;color:#b45309;border:1px solid #fcd34d}" +
      ".adb--saved{background:#f0fdf4;color:#15803d;border:1px solid #86efac}" +
      ".adb--error{background:#fef2f2;color:#b91c1c;border:1px solid #fca5a5}" +
      ".adb--disabled{background:#f3f4f6;color:#6b7280;border:1px solid #d1d5db}" +
      ".adb__icon{font-size:1.05rem;flex-shrink:0;width:20px;text-align:center;position:relative}" +
      "@keyframes adbPulse{0%,80%,100%{opacity:.3}40%{opacity:1}}" +
      ".adb__dots span{animation:adbPulse 1.4s infinite both}" +
      ".adb__dots span:nth-child(2){animation-delay:.2s}" +
      ".adb__dots span:nth-child(3){animation-delay:.4s}";
    document.head.appendChild(s);
  }

  /* ---------- Engine ---------- */
  function Engine(form) {
    this.form = form;
    this.endpoint = form.getAttribute("data-auto-draft");
    this.timer = null;
    this.lastHash = "";
    this.saving = false;

    /* Build UI elements and keep direct references */
    this.bar = document.createElement("div");
    this.iconEl = document.createElement("span");
    this.labelEl = document.createElement("span");

    this.iconEl.className = "adb__icon";
    this.bar.appendChild(this.iconEl);
    this.bar.appendChild(this.labelEl);

    this._setup();
  }

  Engine.prototype._setup = function () {
    if (!this.endpoint) return;
    injectStyles();

    /* Insert bar at top of form */
    this.form.insertBefore(this.bar, this.form.firstChild);

    /* Check if auto-draft is enabled */
    if (!window.AUTO_DRAFT_ENABLED) {
      this._show("disabled");
      return;
    }

    this.lastHash = this._hash();
    this._show("idle");

    var self = this;
    this.form.addEventListener("input", function () { self._onChange(); }, true);
    this.form.addEventListener("change", function () { self._onChange(); }, true);

    /* Watch for DOM changes (dynamic rows added/removed via buttons) */
    if (window.MutationObserver) {
      var observer = new MutationObserver(function (mutations) {
        for (var i = 0; i < mutations.length; i++) {
          if (mutations[i].addedNodes.length || mutations[i].removedNodes.length) {
            self._onChange();
            return;
          }
        }
      });
      observer.observe(this.form, { childList: true, subtree: true });
    }
  };

  Engine.prototype._show = function (state, extra) {
    this.bar.className = "adb adb--" + state;

    switch (state) {
      case "idle":
        this.iconEl.innerHTML = '<i class="fas fa-cloud"></i>';
        this.labelEl.textContent = "บันทึกแบบร่างอัตโนมัติเปิดใช้งาน";
        break;
      case "saving":
        this.iconEl.innerHTML = '<i class="fas fa-sync-alt fa-spin"></i>';
        this.labelEl.innerHTML = 'กำลังบันทึกข้อมูลแบบร่าง<span class="adb__dots"><span>.</span><span>.</span><span>.</span></span>';
        break;
      case "saved":
        this.iconEl.innerHTML = '<span class="fa-stack" style="font-size:.55em;vertical-align:middle"><i class="fas fa-cloud fa-stack-2x"></i><i class="fas fa-check fa-stack-1x fa-inverse" style="font-size:.7em;margin-top:-2px"></i></span>';
        this.labelEl.textContent = "บันทึกฉบับร่างแล้ว — " + (extra || "");
        break;
      case "error":
        this.iconEl.innerHTML = '<i class="fas fa-exclamation-triangle"></i>';
        this.labelEl.textContent = extra || "บันทึกอัตโนมัติล้มเหลว กรุณาลองใหม่";
        break;
      case "disabled":
        this.iconEl.innerHTML = '<i class="fas fa-pause-circle"></i>';
        this.labelEl.textContent = "บันทึกแบบร่างอัตโนมัติปิดอยู่";
        break;
    }
  };

  Engine.prototype._timeStr = function () {
    var d = new Date();
    return "เมื่อเวลา " + d.getHours().toString().padStart(2, "0") + ":" +
           d.getMinutes().toString().padStart(2, "0") + ":" +
           d.getSeconds().toString().padStart(2, "0") + " น.";
  };

  Engine.prototype._onChange = function () {
    if (this.saving) return;
    var self = this;
    clearTimeout(this.timer);
    this.timer = setTimeout(function () { self._save(); }, DEBOUNCE_MS);
  };

  Engine.prototype._hash = function () {
    return JSON.stringify(this._collect());
  };

  Engine.prototype._collect = function () {
    var d = {};
    this.form.querySelectorAll("input,select,textarea").forEach(function (el) {
      var n = el.name;
      if (!n || n === "_csrf" || n === "action") return;
      if (el.type === "checkbox") {
        d[n] = el.checked ? (el.value || "on") : "☐";
      } else if (el.type === "radio") {
        if (el.checked) d[n] = el.value;
      } else {
        d[n] = el.value || "";
      }
    });
    return d;
  };

  Engine.prototype._save = function () {
    if (!window.AUTO_DRAFT_ENABLED) return;
    var h = this._hash();
    if (h === this.lastHash) return;

    this.saving = true;
    this._show("saving");

    var self = this;
    // Read CSRF token from the form input or the <meta name="_csrf"> tag.
    // The cookie is HttpOnly on purpose, so script cannot (and must not) read it.
    var csrfEl = this.form.querySelector('input[name="_csrf"]') ||
                 document.querySelector('input[name="_csrf"]') ||
                 document.querySelector('meta[name="_csrf"]');
    var token = csrfEl ? (csrfEl.value || csrfEl.content || "") : "";
    if (!token) {
    }

    fetch(this.endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": token },
      body: JSON.stringify(this._collect()),
    })
      .then(function (r) {
        if (r.ok) {
          self.lastHash = h;
          self._show("saved", self._timeStr());
        } else {
          self._show("error", "เซิร์ฟเวอร์ตอบกลับ " + r.status);
        }
      })
      .catch(function () {
        self._show("error");
      })
      .finally(function () {
        self.saving = false;
      });
  };

  /* ---------- Init ---------- */
  document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll("form[data-auto-draft]").forEach(function (f) {
      new Engine(f);
    });
  });
})();
