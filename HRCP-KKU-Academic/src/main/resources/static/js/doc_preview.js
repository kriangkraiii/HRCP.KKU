/**
 * Document Preview Engine
 *
 * กรอกข้อมูลในฟอร์ม → กด "อัปเดตตัวอย่าง" → ส่ง AJAX ไป server
 * → server สร้าง DOCX แล้วแปลงเป็น PDF ด้วย LibreOffice → แสดงใน <iframe>
 *
 * ทำไมต้องเป็น PDF: docx-preview แปลง OOXML เป็น HTML แล้วให้เบราว์เซอร์จัดหน้า
 * ซึ่งไม่มี layout engine ของ Word — ตาราง/ฟอนต์/การแบ่งหน้าจึงไม่ตรงกับไฟล์จริง
 * การให้ LibreOffice จัดหน้าฝั่ง server เป็นทางเดียวที่ได้ผลตรงกับ Word
 *
 * ถ้า server ไม่มี LibreOffice จะส่ง DOCX กลับมาพร้อมหัว X-Preview-Format:
 * docx-fallback → ตกไป render ด้วย docx-preview (ตัวอย่างแบบประมาณ) + แบนเนอร์เตือน
 */
class DocPreviewEngine {
    constructor(formId, docType, previewBasePath) {
        this.form = document.getElementById(formId);
        this.docType = docType;
        this.previewBasePath = previewBasePath || '/api/academic/preview';
        this.overlay = null;
        this.body = null;
        this.frame = null;
        this.renderContainer = null;
        this.isVisible = false;
        this.tabs = null;
        this.activeTab = 0;
        this.currentBlobUrl = null;
        this.loadedHash = null;
        this.docxLibPromise = null;
        this.init();
    }

    /** อ่านค่า XSRF-TOKEN จาก cookie (ตั้งโดย CookieCsrfTokenRepository) */
    getCsrfToken() {
        const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : '';
    }

    init() {
        this.createOverlay();
        this.bindInputs();
    }

    /**
     * โหลด JSZip + docx-preview จาก CDN — เรียกเฉพาะตอนต้องใช้โหมด fallback
     * เท่านั้น เส้นทางปกติ (PDF) ไม่ต้องโหลด library ใด ๆ เลย
     */
    loadDocxLibrary() {
        if (this.docxLibPromise) return this.docxLibPromise;

        const loadScript = (src) => new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = src;
            script.onload = resolve;
            script.onerror = () => reject(new Error('ไม่สามารถโหลด: ' + src));
            document.head.appendChild(script);
        });

        this.docxLibPromise = (async () => {
            if (!window.JSZip) {
                await loadScript('https://cdn.jsdelivr.net/npm/jszip@3.10.1/dist/jszip.min.js');
            }
            if (!window.docx) {
                await loadScript('https://cdn.jsdelivr.net/npm/docx-preview@0.3.7/dist/docx-preview.min.js');
            }
        })();
        return this.docxLibPromise;
    }

    /** สร้าง Preview Overlay */
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
                        <span class="docx-status-badge" id="docxStatusBadge">ล่าสุด</span>
                    </div>
                    <div class="docx-toolbar-right">
                        <button class="docx-toolbar-btn docx-btn-update" id="docxRefreshBtn" title="อัปเดตตัวอย่าง">
                            <i class="fas fa-sync-alt"></i> อัปเดตตัวอย่าง
                        </button>
                        <button class="docx-toolbar-btn" id="docxDownloadPdfBtn" title="ดาวน์โหลด PDF">
                            <i class="fas fa-file-pdf"></i> PDF
                        </button>
                        <button class="docx-toolbar-btn" id="docxDownloadBtn" title="ดาวน์โหลด Word">
                            <i class="fas fa-download"></i> Word
                        </button>
                        <button class="docx-toolbar-btn docx-toolbar-close" id="docxCloseBtn" title="ปิด">
                            <i class="fas fa-times"></i> ปิด
                        </button>
                    </div>
                </div>
                <div class="docx-fallback-banner" id="docxFallbackBanner">
                    <i class="fas fa-exclamation-triangle"></i>
                    <span>ตัวอย่างนี้เป็นแบบประมาณ (เลย์เอาต์อาจไม่ตรงกับ Word)
                        — ต้องติดตั้ง LibreOffice บนเซิร์ฟเวอร์เพื่อดูตัวอย่างที่ตรงกับไฟล์จริง</span>
                </div>
                <div class="docx-preview-body" id="docxPreviewBody">
                    <div class="docx-loading" id="docxLoading">
                        <div class="docx-spinner"></div>
                        <span>กำลังสร้างเอกสาร...</span>
                    </div>
                    <iframe class="docx-preview-iframe" id="docxPreviewFrame" title="ตัวอย่างเอกสาร"></iframe>
                    <div class="docx-render-area" id="docxRenderArea"></div>
                </div>
            </div>
        `;

        document.body.appendChild(this.overlay);

        document.getElementById('docxCloseBtn').addEventListener('click', () => this.hide());
        document.getElementById('docxRefreshBtn').addEventListener('click', () => this.loadPreview());
        document.getElementById('docxDownloadBtn').addEventListener('click', () => this.download('docx'));
        document.getElementById('docxDownloadPdfBtn').addEventListener('click', () => this.download('pdf'));

        this.overlay.addEventListener('click', (e) => {
            if (e.target === this.overlay) this.hide();
        });

        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && this.isVisible) this.hide();
        });

        this.body = document.getElementById('docxPreviewBody');
        this.frame = document.getElementById('docxPreviewFrame');
        this.renderContainer = document.getElementById('docxRenderArea');

        window.addEventListener('resize', () => {
            if (this.isVisible) this.fitFallbackToWidth();
        });

        // render tabs ถ้ามีการตั้งค่าไว้ก่อน overlay ถูกสร้าง
        if (this.tabs) this.renderTabs();
    }

    /**
     * ผูก listener บาง ๆ กับ input — แค่เปลี่ยนสถานะบน badge ไม่ยิง request
     * (การแปลง PDF ใช้เวลาหลักวินาที จึงไม่เหมาะกับการอัปเดตอัตโนมัติทุกครั้งที่พิมพ์)
     */
    bindInputs() {
        if (!this.form) return;
        this.form.querySelectorAll('input, textarea, select').forEach(el => {
            el.addEventListener('input', () => this.markStale());
            el.addEventListener('change', () => this.markStale());
        });
    }

    markStale() {
        if (!this.isVisible) return;
        if (this.hashFormData() === this.loadedHash) return;
        this.setStatus('stale');
    }

    /** อัปเดต badge สถานะ: fresh | stale | loading | error */
    setStatus(state) {
        const badge = document.getElementById('docxStatusBadge');
        if (!badge) return;
        badge.classList.remove('stale', 'error', 'loading');
        if (state === 'stale') {
            badge.textContent = 'ข้อมูลเปลี่ยนแล้ว — กดอัปเดต';
            badge.classList.add('stale');
        } else if (state === 'loading') {
            badge.textContent = 'กำลังอัปเดต...';
            badge.classList.add('loading');
        } else if (state === 'error') {
            badge.textContent = 'ผิดพลาด';
            badge.classList.add('error');
        } else {
            badge.textContent = 'ล่าสุด';
        }
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

    /** hash ของข้อมูลฟอร์ม — ใช้ข้ามการยิง request ซ้ำเมื่อไม่มีอะไรเปลี่ยน */
    hashFormData() {
        const json = JSON.stringify(this.getFormData());
        let h = 5381;
        for (let i = 0; i < json.length; i++) {
            h = ((h << 5) + h + json.charCodeAt(i)) | 0;
        }
        return json.length + ':' + h;
    }

    /** POST ข้อมูลฟอร์มไป server */
    fetchPreview(format) {
        const url = `${this.previewBasePath}/${this.docType}?format=${format}`;
        return fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-XSRF-TOKEN': this.getCsrfToken()
            },
            body: JSON.stringify(this.getFormData())
        });
    }

    /** ดึงเอกสารจาก server แล้วแสดงผล — ข้ามถ้าข้อมูลไม่เปลี่ยนจากที่แสดงอยู่ */
    async loadPreview() {
        const hash = this.hashFormData();
        if (hash === this.loadedHash) {
            this.setStatus('fresh');
            return;
        }

        const loading = document.getElementById('docxLoading');
        loading.style.display = 'flex';
        this.setStatus('loading');

        try {
            const response = await this.fetchPreview('pdf');
            if (!response.ok) {
                throw new Error('Server error: ' + response.status);
            }

            const format = response.headers.get('X-Preview-Format') || 'pdf';
            const blob = await response.blob();

            if (format === 'pdf') {
                this.showPdf(blob);
            } else {
                await this.showDocxFallback(blob);
            }

            this.loadedHash = hash;
            this.setStatus('fresh');
        } catch (err) {
            console.error('Preview error:', err);
            this.showError(err.message);
            this.setStatus('error');
        } finally {
            loading.style.display = 'none';
        }
    }

    /** แสดง PDF ใน iframe — viewer ของเบราว์เซอร์จัดการ zoom/เลื่อนหน้า/พิมพ์ให้เอง */
    showPdf(blob) {
        this.releaseBlobUrl();
        this.currentBlobUrl = URL.createObjectURL(blob);

        this.setBanner(false);
        this.renderContainer.style.display = 'none';
        this.renderContainer.innerHTML = '';
        this.frame.style.display = 'block';
        this.frame.src = this.currentBlobUrl;
    }

    /** โหมดสำรอง: render DOCX ด้วย docx-preview (เลย์เอาต์เป็นแค่ค่าประมาณ) */
    async showDocxFallback(blob) {
        await this.loadDocxLibrary();

        this.releaseBlobUrl();
        this.frame.removeAttribute('src');
        this.frame.style.display = 'none';
        this.renderContainer.style.display = 'flex';
        this.setBanner(true);

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
        this.fitFallbackToWidth();
    }

    showError(message) {
        this.releaseBlobUrl();
        this.frame.removeAttribute('src');
        this.frame.style.display = 'none';
        this.setBanner(false);
        this.renderContainer.style.display = 'flex';
        this.renderContainer.innerHTML = `
            <div style="padding:40px;text-align:center;color:#ef5350;">
                <i class="fas fa-exclamation-triangle" style="font-size:2rem;"></i>
                <p style="margin-top:10px;">เกิดข้อผิดพลาดในการสร้างตัวอย่างเอกสาร</p>
                <p style="font-size:0.85rem;color:#999;">${message}</p>
            </div>`;
    }

    setBanner(show) {
        const banner = document.getElementById('docxFallbackBanner');
        if (banner) banner.style.display = show ? 'flex' : 'none';
    }

    releaseBlobUrl() {
        if (this.currentBlobUrl) {
            URL.revokeObjectURL(this.currentBlobUrl);
            this.currentBlobUrl = null;
        }
    }

    /**
     * ย่อหน้ากระดาษให้พอดีความกว้าง — ใช้เฉพาะโหมด fallback
     * (โหมด PDF ไม่ต้องใช้ เพราะ viewer ของเบราว์เซอร์จัดการเอง)
     */
    fitFallbackToWidth() {
        const wrapper = this.renderContainer.querySelector('.docx-wrapper');
        const page = wrapper && wrapper.querySelector('section.docx');
        if (!wrapper || !page) return;

        wrapper.style.setProperty('--docx-zoom', 1);
        wrapper.style.marginBottom = '';

        const pageWidth = page.getBoundingClientRect().width;
        const available = this.body.clientWidth - 40;
        if (pageWidth <= 0 || available <= 0) return;

        const zoom = Math.min(1, available / pageWidth);
        wrapper.style.setProperty('--docx-zoom', zoom);
        // transform ไม่ลดพื้นที่ที่ element กิน — ชดเชยความสูงเองไม่ให้เหลือช่องว่างท้ายหน้า
        if (zoom < 1) {
            const scaledHeight = wrapper.getBoundingClientRect().height;
            wrapper.style.marginBottom = `-${scaledHeight * (1 - zoom) / zoom}px`;
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
                this.loadPreview();
            });
            tabBar.appendChild(btn);
        });

        const toolbar = this.overlay.querySelector('.docx-preview-toolbar');
        toolbar.after(tabBar);
    }

    /** ดาวน์โหลดเอกสารปัจจุบัน (docx = ไฟล์ต้นฉบับ, pdf = ที่แปลงแล้ว) */
    async download(format) {
        try {
            const response = await this.fetchPreview(format);
            if (!response.ok) throw new Error('Server error: ' + response.status);

            const actualFormat = response.headers.get('X-Preview-Format') === 'pdf' ? 'pdf' : 'docx';
            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            let filename = 'doc_' + this.docType;
            if (this.tabs) {
                filename += '_' + this.tabs[this.activeTab].label;
            }
            a.download = filename + '.' + actualFormat;
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
        this.loadPreview();
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

/** Initialize document preview */
function initDocPreview(formId, docType, previewBasePath) {
    docPreview = new DocPreviewEngine(formId, docType, previewBasePath);
}
