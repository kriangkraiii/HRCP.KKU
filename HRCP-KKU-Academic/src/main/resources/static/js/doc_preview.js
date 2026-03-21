/**
 * Real-time DOCX Document Preview Engine
 * กรอกข้อมูลในฟอร์ม → ส่ง AJAX ไป server → ได้ DOCX กลับมา → render ด้วย docx-preview
 * ใช้ library: https://cdn.jsdelivr.net/npm/docx-preview/dist/docx-preview.min.js
 */
class DocPreviewEngine {
    constructor(formId, docType, previewBasePath) {
        this.form = document.getElementById(formId);
        this.docType = docType;
        this.previewBasePath = previewBasePath || '/api/academic/preview';
        this.overlay = null;
        this.renderContainer = null;
        this.debounceTimer = null;
        this.isVisible = false;
        this.tabs = null;
        this.activeTab = 0;
        this.init();
    }

    /** อ่านค่า XSRF-TOKEN จาก cookie (ตั้งโดย CookieCsrfTokenRepository) */
    getCsrfToken() {
        const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : '';
    }

    init() {
        this.loadLibrary().then(() => {
            this.createOverlay();
            this.bindInputs();
        });
    }

    /** โหลด JSZip + docx-preview library จาก CDN (ถ้ายังไม่มี) */
    loadLibrary() {
        const loadScript = (src) => new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = src;
            script.onload = resolve;
            script.onerror = () => reject(new Error('ไม่สามารถโหลด: ' + src));
            document.head.appendChild(script);
        });
        return (async () => {
            // docx-preview ต้องใช้ JSZip เป็น dependency
            if (!window.JSZip) {
                await loadScript('https://cdn.jsdelivr.net/npm/jszip@3.10.1/dist/jszip.min.js');
            }
            if (!window.docx) {
                await loadScript('https://cdn.jsdelivr.net/npm/docx-preview@0.3.7/dist/docx-preview.min.js');
            }
        })();
    }

    /** สร้าง Preview Overlay พร้อม render container */
    createOverlay() {
        this.overlay = document.createElement('div');
        this.overlay.className = 'docx-preview-overlay';
        this.overlay.id = 'docxPreviewOverlay';

        this.overlay.innerHTML = `
            <div class="docx-preview-container">
                <div class="docx-preview-toolbar">
                    <div class="docx-toolbar-left">
                        <i class="fas fa-file-word"></i>
                        <span>ตัวอย่างเอกสารที่ ${this.docType}</span>
                        <span class="docx-live-badge" id="docxLiveBadge">LIVE</span>
                    </div>
                    <div class="docx-toolbar-right">
                        <button class="docx-toolbar-btn" id="docxDownloadBtn" title="ดาวน์โหลด">
                            <i class="fas fa-download"></i> ดาวน์โหลด
                        </button>
                        <button class="docx-toolbar-btn" id="docxRefreshBtn" title="รีเฟรช">
                            <i class="fas fa-sync-alt"></i> รีเฟรช
                        </button>
                        <button class="docx-toolbar-btn docx-toolbar-close" id="docxCloseBtn" title="ปิด">
                            <i class="fas fa-times"></i> ปิด
                        </button>
                    </div>
                </div>
                <div class="docx-preview-body" id="docxPreviewBody">
                    <div class="docx-loading" id="docxLoading">
                        <div class="docx-spinner"></div>
                        <span>กำลังสร้างเอกสาร...</span>
                    </div>
                    <div class="docx-render-area" id="docxRenderArea"></div>
                </div>
            </div>
        `;

        document.body.appendChild(this.overlay);

        // Events
        document.getElementById('docxCloseBtn').addEventListener('click', () => this.hide());
        document.getElementById('docxRefreshBtn').addEventListener('click', () => this.loadDocx());
        document.getElementById('docxDownloadBtn').addEventListener('click', () => this.downloadDocx());

        this.overlay.addEventListener('click', (e) => {
            if (e.target === this.overlay) this.hide();
        });

        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && this.isVisible) this.hide();
        });

        this.renderContainer = document.getElementById('docxRenderArea');

        // render tabs ถ้ามีการตั้งค่าไว้ก่อน overlay ถูกสร้าง
        if (this.tabs) this.renderTabs();
    }

    /** ผูก event listeners กับทุก input สำหรับ real-time update */
    bindInputs() {
        if (!this.form) return;
        const inputs = this.form.querySelectorAll('input, textarea, select');
        inputs.forEach(el => {
            el.addEventListener('input', () => this.debouncedUpdate());
            el.addEventListener('change', () => this.debouncedUpdate());
        });
    }

    /** Debounce: รอ 800ms หลังพิมพ์เสร็จค่อยอัพเดท */
    debouncedUpdate() {
        if (!this.isVisible) return;
        const badge = document.getElementById('docxLiveBadge');
        if (badge) {
            badge.textContent = 'กำลังรอ...';
            badge.classList.add('waiting');
        }
        clearTimeout(this.debounceTimer);
        this.debounceTimer = setTimeout(() => {
            this.loadDocx();
        }, 800);
    }

    /** ดึงข้อมูลจาก form เป็น object */
    getFormData() {
        const data = {};
        if (!this.form) return data;

        const formData = new FormData(this.form);
        for (const [key, value] of formData.entries()) {
            data[key] = value;
        }

        // ดึงค่า hidden inputs ที่ถูก set จาก checkbox toggle
        this.form.querySelectorAll('input[type="hidden"]').forEach(hidden => {
            if (hidden.name) {
                data[hidden.name] = hidden.value;
            }
        });

        // ดึงค่า radio ที่เลือก
        this.form.querySelectorAll('input[type="radio"]:checked').forEach(rb => {
            data[rb.name] = rb.value;
        });

        // ดึงค่า checkbox — checked = ☑ (or its value), unchecked = ☐
        this.form.querySelectorAll('input[type="checkbox"]').forEach(cb => {
            if (cb.checked) {
                data[cb.name] = cb.value || '☑';
            } else if (!data[cb.name]) {
                data[cb.name] = '☐';
            }
        });

        // ส่ง committee_index เมื่อมี tabs
        if (this.tabs) {
            data['committee_index'] = this.tabs[this.activeTab].index.toString();
        }

        return data;
    }

    /** ส่งข้อมูลไป server แล้ว render DOCX */
    async loadDocx() {
        const loading = document.getElementById('docxLoading');
        const badge = document.getElementById('docxLiveBadge');

        loading.style.display = 'flex';

        try {
            const formData = this.getFormData();

            const response = await fetch(`${this.previewBasePath}/${this.docType}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-XSRF-TOKEN': this.getCsrfToken()
                },
                body: JSON.stringify(formData)
            });

            if (!response.ok) {
                throw new Error('Server error: ' + response.status);
            }

            const blob = await response.blob();

            // ใช้ docx-preview render DOCX blob ลงใน container
            this.renderContainer.innerHTML = '';
            await window.docx.renderAsync(blob, this.renderContainer, null, {
                className: 'docx-rendered',
                inWrapper: true,
                ignoreWidth: false,
                ignoreHeight: false,
                ignoreFonts: false,
                breakPages: true,
                ignoreLastRenderedPageBreak: false,
                experimental: true,
                trimXmlDeclaration: true,
                useBase64URL: true,
                renderHeaders: true,
                renderFooters: true,
                renderFootnotes: true,
                renderEndnotes: true,
                renderDrawing: true
            });

            if (badge) {
                badge.textContent = 'LIVE';
                badge.classList.remove('waiting', 'error');
            }
        } catch (err) {
            console.error('DOCX preview error:', err);
            this.renderContainer.innerHTML = `
                <div style="padding:40px;text-align:center;color:#ef5350;">
                    <i class="fas fa-exclamation-triangle" style="font-size:2rem;"></i>
                    <p style="margin-top:10px;">เกิดข้อผิดพลาดในการสร้างตัวอย่างเอกสาร</p>
                    <p style="font-size:0.85rem;color:#999;">${err.message}</p>
                </div>`;
            if (badge) {
                badge.textContent = 'ERROR';
                badge.classList.add('error');
                badge.classList.remove('waiting');
            }
        } finally {
            loading.style.display = 'none';
        }
    }

    /** ตั้งค่า tabs สำหรับ preview (เช่น กรรมการ 3 ท่าน) */
    setTabs(tabsList) {
        this.tabs = tabsList;
        this.activeTab = 0;
        if (this.overlay) {
            this.renderTabs();
        }
    }

    /** สร้าง tab bar ใน overlay */
    renderTabs() {
        if (!this.tabs || !this.overlay) return;

        const existing = this.overlay.querySelector('.docx-tab-bar');
        if (existing) existing.remove();

        const tabBar = document.createElement('div');
        tabBar.className = 'docx-tab-bar';

        this.tabs.forEach((tab, idx) => {
            const btn = document.createElement('button');
            btn.className = 'docx-tab' + (idx === this.activeTab ? ' active' : '');
            btn.textContent = tab.label;
            btn.addEventListener('click', () => {
                this.activeTab = idx;
                tabBar.querySelectorAll('.docx-tab').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                this.loadDocx();
            });
            tabBar.appendChild(btn);
        });

        const toolbar = this.overlay.querySelector('.docx-preview-toolbar');
        toolbar.after(tabBar);
    }

    /** ดาวน์โหลดเอกสาร DOCX ปัจจุบัน */
    async downloadDocx() {
        const formData = this.getFormData();
        try {
            const response = await fetch(`${this.previewBasePath}/${this.docType}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-XSRF-TOKEN': this.getCsrfToken()
                },
                body: JSON.stringify(formData)
            });
            if (!response.ok) throw new Error('Server error: ' + response.status);
            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            let filename = 'doc_' + this.docType;
            if (this.tabs) {
                filename += '_' + this.tabs[this.activeTab].label;
            }
            a.download = filename + '.docx';
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
        } catch (err) {
            console.error('Download error:', err);
        }
    }

    /** เปิด preview */
    show() {
        this.isVisible = true;
        this.overlay.classList.add('active');
        document.body.style.overflow = 'hidden';
        this.loadDocx();
    }

    /** ปิด preview */
    hide() {
        this.isVisible = false;
        this.overlay.classList.remove('active');
        document.body.style.overflow = '';
    }
}

/** Global instance */
let docPreview = null;

/** Initialize DOCX preview */
function initDocPreview(formId, docType, previewBasePath) {
    docPreview = new DocPreviewEngine(formId, docType, previewBasePath);
}
