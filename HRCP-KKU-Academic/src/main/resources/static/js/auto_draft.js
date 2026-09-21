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
    this.inflight = null;

    /* ให้ภายนอกสั่ง flush ได้ — ปุ่มส่งลงนามใช้ทางนี้บันทึกเอกสารก่อนส่ง */
    form.__autoDraft = this;

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
          var m = mutations[i];
          if (!m.addedNodes.length && !m.removedNodes.length) continue;
          // แถบสถานะเป็นลูกของฟอร์ม การเขียนข้อความลงไปจึงเด้งกลับเข้า observer ตัวเอง
          // แล้วนับเป็น "ผู้ใช้แก้ข้อมูล" — พอบันทึกเสร็จสถานะเลยพลิกกลับไปเป็นยังไม่บันทึก
          if (self.bar.contains(m.target)) continue;
          self._onChange();
          return;
        }
      });
      observer.observe(this.form, { childList: true, subtree: true });
    }
  };

  /*
   * แถบ .adb อยู่บนสุดของฟอร์ม ซึ่งห่างจากปุ่มส่งลงนามท้ายหน้าเป็นหน้าจอ ๆ ผู้ใช้ตอนจะกดส่ง
   * จึงไม่เห็นว่าข้อมูลบันทึกแล้วหรือยัง — สะท้อนสถานะเดียวกันไปไว้ข้างปุ่มนั้นด้วย
   */
  Engine.prototype._mirror = function (state, extra) {
    var nodes = document.querySelectorAll("[data-autodraft-mirror]");
    if (!nodes.length) return;

    var icon, text;
    switch (state) {
      case "dirty":
        icon = "fa-pen text-secondary";
        text = "มีการแก้ไขที่ยังไม่บันทึก — ระบบจะบันทึกให้ก่อนส่ง";
        break;
      case "saving":
        icon = "fa-sync-alt fa-spin text-warning";
        text = "กำลังบันทึก...";
        break;
      case "saved":
        icon = "fa-check-circle text-success";
        text = "บันทึกข้อมูลล่าสุดแล้ว — " + (extra || "");
        break;
      case "error":
        icon = "fa-exclamation-triangle text-danger";
        text = extra || "บันทึกอัตโนมัติล้มเหลว กรุณาลองใหม่";
        break;
      case "disabled":
        icon = "fa-pause-circle text-secondary";
        text = "ปิดบันทึกอัตโนมัติไว้ ระบบจะบันทึกให้ตอนกดส่ง";
        break;
      default: /* idle */
        icon = "fa-check-circle text-success";
        text = "ข้อมูลตรงกับที่บันทึกไว้";
    }

    nodes.forEach(function (el) {
      el.innerHTML = '<i class="fas ' + icon + ' me-1"></i>' + text;
      el.hidden = false;
    });
  };

  Engine.prototype._show = function (state, extra) {
    this.bar.className = "adb adb--" + state;
    this._mirror(state, extra);

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
        this.iconEl.innerHTML = '<i class="fas fa-check-circle"></i>';
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
    /* บอกไว้ก่อน debounce — ผู้ใช้ที่เลื่อนลงไปกดส่งทันทีจะได้ไม่เห็น "บันทึกแล้ว" ที่เก่าไป 1 วิ */
    this._mirror("dirty");
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
      // ช่องที่ถูกปิดไม่ถูกส่งไปกับฟอร์มจริงอยู่แล้ว ร่างอัตโนมัติก็ไม่ควรส่ง — เอกสารที่ล็อก
      // ทั้งฉบับจะได้ไม่ยิง POST เปล่าทุกครั้งที่มีอะไรขยับบนหน้า
      if (el.disabled) return;
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

  /**
   * เขียนแบบร่างขึ้นเซิร์ฟเวอร์
   *
   * @param force บันทึกแม้ผู้ใช้ปิดบันทึกอัตโนมัติไว้ — ใช้ตอนผู้ใช้สั่งเอง (flush)
   * @return Promise<boolean> true เมื่อข้อมูลบนหน้าจอถึงเซิร์ฟเวอร์แล้ว (รวมกรณีไม่มีอะไรเปลี่ยน)
   */
  Engine.prototype._save = function (force) {
    if (!this.endpoint) return Promise.resolve(true);
    if (!force && !window.AUTO_DRAFT_ENABLED) {
      this._mirror("disabled");
      return Promise.resolve(true);
    }
    var data = this._collect();
    // ทุกช่องถูกปิด แปลว่าเอกสารล็อกอยู่ ไม่มีอะไรให้บันทึก
    if (Object.keys(data).length === 0) return Promise.resolve(true);
    var h = JSON.stringify(data);
    // พิมพ์แล้วลบกลับเป็นเหมือนเดิมก็มาถึงตรงนี้ ต้องล้าง "ยังไม่บันทึก" ที่ _onChange ขึ้นไว้
    if (h === this.lastHash) {
      this._mirror("idle");
      return Promise.resolve(true);
    }

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

    this.inflight = fetch(this.endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-HRCP-CT": token },
      body: h,
    })
      .then(function (r) {
        if (r.ok) {
          self.lastHash = h;
          self._show("saved", self._timeStr());
          return true;
        }
        // เซิร์ฟเวอร์ส่งเหตุผลมาให้เป็นภาษาไทยอยู่แล้ว การขึ้นแค่เลขสถานะทำให้ผู้ใช้
        // เห็น "เซิร์ฟเวอร์ตอบกลับ 409" โดยไม่รู้ว่าเอกสารถูกล็อกเพราะกำลังเวียนลงนาม
        return r
          .json()
          .then(function (body) {
            self._show("error", (body && body.error) || "เซิร์ฟเวอร์ตอบกลับ " + r.status);
            return false;
          })
          .catch(function () {
            self._show("error", "เซิร์ฟเวอร์ตอบกลับ " + r.status);
            return false;
          });
      })
      .catch(function () {
        self._show("error");
        return false;
      })
      .finally(function () {
        self.saving = false;
        self.inflight = null;
      });

    return this.inflight;
  };

  /**
   * บันทึกทันทีโดยไม่รอ debounce และไม่สนว่าผู้ใช้ปิดบันทึกอัตโนมัติไว้หรือไม่
   *
   * @return Promise<boolean> true เมื่อข้อมูลบนหน้าจอถูกบันทึกเรียบร้อย
   */
  Engine.prototype.flush = function () {
    clearTimeout(this.timer);
    var self = this;
    if (this.inflight) {
      /* มีรอบที่ยิงไปแล้วค้างอยู่ — รอให้จบก่อน แล้วค่อยเก็บส่วนที่พิมพ์ระหว่างนั้น */
      return this.inflight.then(function () { return self._save(true); });
    }
    return this._save(true);
  };

  /* ---------- Public API ---------- */
  window.AutoDraft = {
    /**
     * บังคับบันทึกทุกฟอร์มที่เปิดบันทึกแบบร่างไว้ในหน้านี้
     *
     * @return Promise<boolean> false เมื่อมีอย่างน้อยหนึ่งฟอร์มบันทึกไม่สำเร็จ
     */
    flushAll: function () {
      var pending = [];
      document.querySelectorAll("form[data-auto-draft]").forEach(function (f) {
        if (f.__autoDraft) pending.push(f.__autoDraft.flush());
      });
      return Promise.all(pending).then(function (results) {
        return results.every(Boolean);
      });
    },
  };

  /* ---------- Init ---------- */
  document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll("form[data-auto-draft]").forEach(function (f) {
      new Engine(f);
    });
  });
})();
