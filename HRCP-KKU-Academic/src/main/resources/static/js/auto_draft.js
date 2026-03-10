/**
 * Auto-Draft Engine — บันทึกแบบร่างอัตโนมัติ
 *
 * Usage:
 *   1. <form id="myForm" data-auto-draft="/api/draft/position/123/1"> ... </form>
 *   2. <script> window.AUTO_DRAFT_ENABLED = true; </script>
 *   3. <script src="/js/auto_draft.js"></script>
 *
 * The engine binds to all forms with [data-auto-draft] attribute.
 * Debounce: 5 seconds after last input change.
 * Only saves when form data actually changed (dirty tracking).
 */
(function () {
  "use strict";

  const DEBOUNCE_MS = 5000;
  const TOAST_DURATION = 2500;

  class AutoDraftEngine {
    constructor(form) {
      this.form = form;
      this.endpoint = form.getAttribute("data-auto-draft");
      this.timer = null;
      this.lastHash = "";
      this.saving = false;
      this.init();
    }

    init() {
      if (!this.endpoint) return;

      // Compute initial hash to avoid saving unchanged data
      this.lastHash = this.computeHash();

      // Bind all inputs
      const events = ["input", "change"];
      events.forEach((evt) => {
        this.form.addEventListener(evt, () => this.onFormChange(), true);
      });
    }

    onFormChange() {
      if (this.saving) return;
      clearTimeout(this.timer);
      this.timer = setTimeout(() => this.save(), DEBOUNCE_MS);
    }

    computeHash() {
      return JSON.stringify(this.collectData());
    }

    collectData() {
      const data = {};
      const elements = this.form.querySelectorAll("input, select, textarea");
      elements.forEach((el) => {
        const name = el.name;
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
    }

    async save() {
      if (!window.AUTO_DRAFT_ENABLED) return;

      const currentHash = this.computeHash();
      if (currentHash === this.lastHash) return; // No changes

      this.saving = true;
      this.showIndicator("saving");

      try {
        const csrfToken =
          document.querySelector('input[name="_csrf"]')?.value ||
          document.querySelector('meta[name="_csrf"]')?.content;

        const res = await fetch(this.endpoint, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-CSRF-TOKEN": csrfToken || "",
          },
          body: JSON.stringify(this.collectData()),
        });

        if (res.ok) {
          this.lastHash = currentHash;
          this.showToast("บันทึกแบบร่างอัตโนมัติสำเร็จ", "success");
        } else {
          this.showToast("บันทึกอัตโนมัติล้มเหลว", "error");
        }
      } catch (e) {
        console.warn("Auto-draft failed:", e);
        this.showToast("บันทึกอัตโนมัติล้มเหลว", "error");
      } finally {
        this.saving = false;
        this.hideIndicator();
      }
    }

    showIndicator(type) {
      let indicator = document.getElementById("autoDraftIndicator");
      if (!indicator) {
        indicator = document.createElement("div");
        indicator.id = "autoDraftIndicator";
        indicator.style.cssText = `
                    position: fixed; bottom: 20px; right: 20px; z-index: 9999;
                    padding: 10px 18px; border-radius: 10px;
                    font-size: 0.85rem; font-family: 'Sarabun', sans-serif;
                    box-shadow: 0 4px 20px rgba(0,0,0,0.15);
                    transition: all 0.3s ease; opacity: 0;
                    display: flex; align-items: center; gap: 8px;
                `;
        document.body.appendChild(indicator);
      }
      indicator.style.background = "#fff3cd";
      indicator.style.color = "#856404";
      indicator.style.opacity = "1";
      indicator.innerHTML =
        '<i class="fas fa-spinner fa-spin"></i> กำลังบันทึก...';
    }

    hideIndicator() {
      const indicator = document.getElementById("autoDraftIndicator");
      if (indicator) indicator.style.opacity = "0";
    }

    showToast(message, type) {
      let toast = document.getElementById("autoDraftToast");
      if (!toast) {
        toast = document.createElement("div");
        toast.id = "autoDraftToast";
        toast.style.cssText = `
                    position: fixed; bottom: 20px; right: 20px; z-index: 10000;
                    padding: 12px 20px; border-radius: 10px;
                    font-size: 0.85rem; font-family: 'Sarabun', sans-serif;
                    box-shadow: 0 4px 20px rgba(0,0,0,0.15);
                    transition: all 0.4s ease; opacity: 0;
                    display: flex; align-items: center; gap: 8px;
                `;
        document.body.appendChild(toast);
      }

      if (type === "success") {
        toast.style.background = "linear-gradient(135deg, #e8f5e9, #c8e6c9)";
        toast.style.color = "#2e7d32";
        toast.innerHTML = '<i class="fas fa-check-circle"></i> ' + message;
      } else {
        toast.style.background = "linear-gradient(135deg, #ffebee, #ffcdd2)";
        toast.style.color = "#c62828";
        toast.innerHTML =
          '<i class="fas fa-exclamation-circle"></i> ' + message;
      }

      toast.style.opacity = "1";
      toast.style.transform = "translateY(0)";

      setTimeout(() => {
        toast.style.opacity = "0";
        toast.style.transform = "translateY(10px)";
      }, TOAST_DURATION);
    }
  }

  // Initialize on DOM ready
  document.addEventListener("DOMContentLoaded", function () {
    if (!window.AUTO_DRAFT_ENABLED) return;

    document.querySelectorAll("form[data-auto-draft]").forEach((form) => {
      new AutoDraftEngine(form);
    });
  });
})();
