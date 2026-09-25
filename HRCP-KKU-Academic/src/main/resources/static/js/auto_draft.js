/**
 * Auto-Draft Engine — บันทึกแบบร่างอัตโนมัติ (Google Forms Style)
 *
 * Usage:
 *   1. <form data-auto-draft="/api/draft/position/123/1"> ... </form>
 *   2. <script> window.AUTO_DRAFT_ENABLED = true; </script>
 *   3. <script src="/js/auto_draft.js"></script>
 *
 * Debounce: 1 second after last input change.
 * Dirty tracking: only saves when data actually changed.
 * UI: ชิปสถานะลอยติดหน้าจอ (.adb — สไตล์อยู่ใน style.css / dark-theme.css)
 *     และสะท้อนสถานะเดียวกันไปยังทุก element ที่มี [data-autodraft-mirror]
 */
(function () {
  "use strict";

  var DEBOUNCE_MS = 1000;
  /* บันทึกเสร็จแล้วเน้นสีอยู่เท่านี้ ก่อนจะจางลงเป็นโทนเงียบ */
  var QUIET_MS = 4000;

  /* ---------- ระบบสองภาษา (I18N) ---------- */
  var I18N = {
    th: {
      timeSuffix: " น.",
      chip: {
        idle: "บันทึกอัตโนมัติ",
        dirty: "ยังไม่บันทึก",
        saving: "กำลังบันทึก",
        saved: "บันทึกแล้ว ",
        error: "บันทึกไม่สำเร็จ",
        disabled: "ปิดบันทึกอัตโนมัติ"
      },
      mirror: {
        idle: "บันทึกข้อมูลล่าสุดเรียบร้อยแล้ว",
        dirty: "มีข้อมูลยังไม่บันทึก (ระบบจะบันทึกให้อัตโนมัติ)",
        saving: "กำลังบันทึกข้อมูล…",
        saved: "บันทึกข้อมูลแล้วเมื่อ ",
        error: "บันทึกไม่สำเร็จ กรุณาลองใหม่อีกครั้ง",
        disabled: "ปิดบันทึกอัตโนมัติ (จะบันทึกเมื่อกดส่ง)"
      }
    },
    en: {
      timeSuffix: "",
      chip: {
        idle: "Auto-save on",
        dirty: "Unsaved",
        saving: "Saving",
        saved: "Saved ",
        error: "Save failed",
        disabled: "Auto-save off"
      },
      mirror: {
        idle: "All changes saved",
        dirty: "Unsaved changes (will auto-save)",
        saving: "Saving changes…",
        saved: "Saved at ",
        error: "Save failed, please retry",
        disabled: "Auto-save off (saves on submit)"
      }
    }
  };

  function getCurrentLang() {
    // 1. ตรวจจาก googtrans cookie
    var match = document.cookie.match(/(?:^|;\s*)googtrans=([^;]*)/);
    if (match) {
      var val = decodeURIComponent(match[1]);
      if (val.indexOf("/en") !== -1) return "en";
      if (val.indexOf("/th") !== -1) return "th";
    }
    // 2. ตรวจจากป้ายบอกภาษาบน Navbar
    var label = document.getElementById("langCurrentLabel");
    if (label) {
      var txt = label.textContent.trim().toUpperCase();
      if (txt === "EN") return "en";
      if (txt === "TH") return "th";
    }
    // 3. ตรวจจาก attribute lang หรือ class ของ <html>
    var html = document.documentElement;
    var langAttr = (html.getAttribute("lang") || "").toLowerCase();
    if (langAttr.startsWith("en") || html.classList.contains("translated-ltr")) {
      return "en";
    }
    return "th";
  }

  var activeEngines = [];
  function reRenderAllEngines() {
    activeEngines.forEach(function (e) {
      e._reRender();
    });
  }

  // ดักฟังการสลับภาษาจาก Topbar
  window.addEventListener("app:language-change", reRenderAllEngines);

  // ดักฟังการเปลี่ยนแปลงจาก Google Translate ที่เปลี่ยน attribute ของ <html>
  if (window.MutationObserver) {
    var lastObservedLang = getCurrentLang();
    var htmlObserver = new MutationObserver(function () {
      var current = getCurrentLang();
      if (current !== lastObservedLang) {
        lastObservedLang = current;
        reRenderAllEngines();
      }
    });
    htmlObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ["lang", "class"]
    });
  }

  /* ---------- ชิปสถานะ (ลอยติดหน้าจอ ใช้ร่วมกันทั้งหน้า) ----------
   *
   * เดิมแถบสถานะถูกแทรกเป็นลูกตัวแรกของฟอร์ม พอผู้ใช้เลื่อนลงไปกรอกข้อมูลก็หลุดจอไป
   * ตัวชิปจึงย้ายมาเป็น position:fixed อยู่มุมขวาล่างเพื่อไม่ให้บังส่วนหัว
   * และทำเป็นตัวเดียวต่อหน้า เผื่อหน้าไหนมีหลายฟอร์มจะได้ไม่ซ้อนกัน (CSS: style.css)
   */
  var dock = null;
  var quietTimer = null;

  function getDock() {
    if (dock) return dock;
    dock = document.createElement("div");
    dock.className = "adb notranslate";
    dock.setAttribute("role", "status");
    dock.setAttribute("aria-live", "polite");

    var icon = document.createElement("span");
    icon.className = "adb__icon";
    var label = document.createElement("span");
    label.className = "adb__label";

    dock.appendChild(icon);
    dock.appendChild(label);
    dock.iconEl = icon;
    dock.labelEl = label;

    document.body.appendChild(dock);
    return dock;
  }

  /* ---------- Engine ---------- */
  function Engine(form) {
    this.form = form;
    this.endpoint = form.getAttribute("data-auto-draft");
    this.timer = null;
    this.lastHash = "";
    this.lastSavedDate = null;
    this.lastSavedAt = "";
    this.currentState = "idle";
    this.currentExtra = null;
    this.saving = false;
    this.inflight = null;

    /* ให้ภายนอกสั่ง flush ได้ — ปุ่มส่งลงนามใช้ทางนี้บันทึกเอกสารก่อนส่ง */
    form.__autoDraft = this;
    activeEngines.push(this);

    this._setup();
  }

  Engine.prototype._setup = function () {
    if (!this.endpoint) return;

    this.bar = getDock();
    this.iconEl = this.bar.iconEl;
    this.labelEl = this.bar.labelEl;

    /* Check if auto-draft is enabled */
    if (!window.AUTO_DRAFT_ENABLED) {
      this._show("disabled");
      return;
    }

    this.lastHash = this._hash();
    this._show("idle");

    var self = this;

    /* ผู้ใช้แตะฟอร์มจริงแล้วหรือยัง — นับเฉพาะ event ที่ isTrusted (มาจากคนจริง)
       สคริปต์บนหน้า (เติมวันที่, datalist, ค่าตั้งต้น) แก้ฟอร์มหลังโหลดได้ ถ้านับด้วย
       แค่เปิดฟอร์มแล้วปิดไปก็เกิดแบบร่างที่มีแต่ค่าที่ระบบเติมให้ และไม่ถูกลบทิ้งเป็นแบบร่างว่าง
       ต้องผูกก่อน listener ของ _onChange เพื่อให้ธงถูกตั้งก่อนตัดสินใจบันทึก */
    this.touched = false;
    var markTouched = function (e) { if (e.isTrusted) self.touched = true; };
    ["input", "change", "click", "keydown", "paste", "drop"].forEach(function (type) {
      self.form.addEventListener(type, markTouched, true);
    });

    this.form.addEventListener("input", function () { self._onChange(); }, true);
    this.form.addEventListener("change", function () { self._onChange(); }, true);

    /* Watch for DOM changes (dynamic rows added/removed via buttons) */
    if (window.MutationObserver) {
      var observer = new MutationObserver(function (mutations) {
        for (var i = 0; i < mutations.length; i++) {
          var m = mutations[i];
          if (!m.addedNodes.length && !m.removedNodes.length) continue;
          // ชิปสถานะย้ายออกไปอยู่นอกฟอร์มแล้ว แต่กันไว้อีกชั้น เผื่อมีใครย้ายกลับเข้ามา:
          // ถ้าการเขียนข้อความลงชิปเด้งกลับเข้า observer ตัวเอง จะถูกนับเป็น "ผู้ใช้แก้ข้อมูล"
          // แล้วพอบันทึกเสร็จสถานะจะพลิกกลับไปเป็นยังไม่บันทึกทันที
          if (self.bar.contains(m.target)) continue;
          self._onChange();
          return;
        }
      });
      observer.observe(this.form, { childList: true, subtree: true });
    }
  };

  /*
   * ชิปสถานะลอยอยู่ด้านล่างจอ ส่วนแผงลงนามอยู่ท้ายหน้า ผู้ใช้ที่กำลังจะกดส่งจึงมองคนละจุด
   * — สะท้อนสถานะเดียวกันไปไว้ข้างปุ่มส่งด้วย (ถ้อยคำยาวกว่าเพราะมีที่ว่างมากกว่าบนชิป)
   */
  Engine.prototype._mirror = function (state, extra) {
    var nodes = document.querySelectorAll("[data-autodraft-mirror]");
    if (!nodes.length) return;

    var lang = getCurrentLang();
    var dict = I18N[lang] || I18N.th;

    var icon, text;
    switch (state) {
      case "dirty":
        icon = "fa-pen text-secondary";
        text = dict.mirror.dirty;
        break;
      case "saving":
        icon = "fa-sync-alt fa-spin text-warning";
        text = dict.mirror.saving;
        break;
      case "saved":
        icon = "fa-check-circle text-success";
        text = dict.mirror.saved + (extra || "");
        break;
      case "error":
        icon = "fa-exclamation-triangle text-danger";
        text = extra || dict.mirror.error;
        break;
      case "disabled":
        icon = "fa-pause-circle text-secondary";
        text = dict.mirror.disabled;
        break;
      default: /* idle */
        icon = "fa-check-circle text-success";
        text = dict.mirror.idle;
    }

    nodes.forEach(function (el) {
      el.classList.add("notranslate");
      el.innerHTML = '<i class="fas ' + icon + ' me-1"></i>' + text;
      el.hidden = false;
    });
  };

  Engine.prototype._show = function (state, extra, force) {
    var bar = this.bar;
    if (!bar) return;

    this.currentState = state;
    this.currentExtra = extra;

    var lang = getCurrentLang();
    var dict = I18N[lang] || I18N.th;

    var icon, text;
    switch (state) {
      case "dirty":
        icon = "fa-pen";
        text = dict.chip.dirty;
        break;
      case "saving":
        icon = "fa-sync-alt fa-spin";
        text = dict.chip.saving;
        break;
      case "saved":
        icon = "fa-check-circle";
        text = dict.chip.saved + (extra || "");
        break;
      case "error":
        icon = "fa-exclamation-triangle";
        text = extra || dict.chip.error;
        break;
      case "disabled":
        icon = "fa-pause-circle";
        text = dict.chip.disabled;
        break;
      default: /* idle */
        icon = "fa-cloud";
        text = dict.chip.idle;
    }

    /*
     * _onChange ยิงทุกคีย์สโตรก ถ้าเขียน DOM ใหม่ทุกครั้งจะเปลืองและ screen reader
     * (aria-live) จะอ่านข้อความเดิมซ้ำไม่หยุด — ข้ามไปถ้าสถานะกับข้อความยังเหมือนเดิม
     * ยกเว้นตอนชิปจางเป็นโทนเงียบแล้ว ต้องปล่อยให้เน้นสีใหม่เพื่อยืนยันว่าบันทึกอีกรอบแล้วจริง
     */
    if (!force && bar.hrcpState === state && bar.hrcpText === text &&
        !bar.classList.contains("adb--quiet")) {
      return;
    }
    bar.hrcpState = state;
    bar.hrcpText = text;

    clearTimeout(quietTimer);
    bar.className = "adb notranslate adb--" + state;
    this._mirror(state, extra);

    this.iconEl.innerHTML = '<i class="fas ' + icon + '"></i>';
    if (state === "saving") {
      /* จุดไข่ปลาเคลื่อนไหว บอกว่ายังทำงานอยู่ ไม่ได้ค้าง */
      this.labelEl.innerHTML = text +
        '<span class="adb__dots"><span>.</span><span>.</span><span>.</span></span>';
    } else {
      this.labelEl.textContent = text;
    }
    /* ข้อความผิดพลาดจากเซิร์ฟเวอร์ยาวกว่าชิป CSS จะตัดท้ายให้ — เก็บฉบับเต็มไว้ใน tooltip */
    bar.title = text;

    if (state === "saved") {
      quietTimer = setTimeout(function () { bar.classList.add("adb--quiet"); }, QUIET_MS);
    }
  };

  /* สั่ง render ใหม่ทันทีตามภาษาปัจจุบัน */
  Engine.prototype._reRender = function () {
    if (!this.bar) return;
    var state = this.currentState || "idle";
    var extra = this.currentExtra;
    if (state === "saved" && this.lastSavedDate) {
      extra = this._formatTime(this.lastSavedDate);
      this.lastSavedAt = extra;
    }
    this._show(state, extra, true);
  };

  /* กลับไปบอกว่า "ตรงกับที่บันทึกไว้" โดยไม่ต้องเน้นสีใหม่ — ใช้ตอนผู้ใช้แก้แล้วแก้กลับเหมือนเดิม */
  Engine.prototype._showSynced = function () {
    if (this.lastSavedDate) {
      var extra = this._formatTime(this.lastSavedDate);
      this._show("saved", extra);
      this.bar.classList.add("adb--quiet");
    } else {
      this._show("idle");
    }
  };

  Engine.prototype._formatTime = function (d) {
    if (!d) d = new Date();
    var h = d.getHours().toString().padStart(2, "0");
    var m = d.getMinutes().toString().padStart(2, "0");
    var lang = getCurrentLang();
    var suffix = (I18N[lang] || I18N.th).timeSuffix;
    return h + ":" + m + suffix;
  };

  Engine.prototype._timeStr = function () {
    return this._formatTime(new Date());
  };

  Engine.prototype._onChange = function () {
    /* ผู้ใช้ยังไม่ได้แตะ — สิ่งที่เปลี่ยนมาจากสคริปต์บนหน้า ไม่ต้องเริ่มนับเวลาบันทึกอัตโนมัติ */
    if (!this.touched) {
      return;
    }
    /* บอกไว้ก่อน debounce — ผู้ใช้ที่เลื่อนลงไปกดส่งทันทีจะได้ไม่เห็น "บันทึกแล้ว" ที่เก่าไป 1 วิ */
    this._show("dirty");
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
    // บันทึกอัตโนมัติรอจนผู้ใช้แตะฟอร์มจริง — flush (force) มาจากการกระทำของผู้ใช้ เช่นก่อนลงนาม จึงผ่านได้
    if (force) this.touched = true;
    if (!force && !this.touched) return Promise.resolve(true);
    var data = this._collect();
    // ทุกช่องถูกปิด แปลว่าเอกสารล็อกอยู่ ไม่มีอะไรให้บันทึก
    if (Object.keys(data).length === 0) return Promise.resolve(true);
    var h = JSON.stringify(data);
    // พิมพ์แล้วลบกลับเป็นเหมือนเดิมก็มาถึงตรงนี้ ต้องล้าง "ยังไม่บันทึก" ที่ _onChange ขึ้นไว้
    // แต่ถ้าเป็นการสั่งบันทึกโดยตรง (force) และในเซสชันนี้ยังไม่เคยบันทึกสำเร็จขึ้นเซิร์ฟเวอร์มาก่อน
    // ต้องยอมให้ส่งข้อมูลขึ้นเซิร์ฟเวอร์เพื่อให้มีข้อมูลในฐานข้อมูล
    if (h === this.lastHash && (!force || this.lastSavedDate != null)) {
      this._showSynced();
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
          self.lastSavedDate = new Date();
          self.lastSavedAt = self._formatTime(self.lastSavedDate);
          self._show("saved", self.lastSavedAt);
          return true;
        }
        // เซิร์ฟเวอร์ส่งเหตุผลมาให้เป็นภาษาไทยอยู่แล้ว การขึ้นแค่เลขสถานะทำให้ผู้ใช้
        // เห็น "เซิร์ฟเวอร์ตอบกลับ 409" โดยไม่รู้ว่าเอกสารถูกล็อกเพราะกำลังเวียนลงนาม
        return r
          .json()
          .then(function (body) {
            var msg = (body && body.error);
            if (!msg) {
              var lang = getCurrentLang();
              msg = (lang === "en" ? "Server returned " : "เซิร์ฟเวอร์ตอบกลับ ") + r.status;
            }
            self._show("error", msg);
            return false;
          })
          .catch(function () {
            var lang = getCurrentLang();
            self._show("error", (lang === "en" ? "Server returned " : "เซิร์ฟเวอร์ตอบกลับ ") + r.status);
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
