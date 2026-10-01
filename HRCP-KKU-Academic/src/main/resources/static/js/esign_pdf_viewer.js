/**
 * E-Signature Document Interactive PDF Viewer (PDF.js Engine)
 *
 * Renders official documents at crisp resolution with full-width fit,
 * continuous vertical scrolling across all pages, zoom controls, and fullscreen mode.
 */
class EsignPdfViewer {
    constructor(containerId, pdfUrl, options = {}) {
        this.container = document.getElementById(containerId);
        this.pdfUrl = pdfUrl;
        this.options = Object.assign({
            initialFit: 'width', // 'width' or 'actual'
            minScale: 0.5,
            maxScale: 3.0,
            scaleStep: 0.15
        }, options);

        this.pdfDoc = null;
        this.pageCount = 0;
        this.currentPage = 1;
        this.currentScale = 1.0;
        this.fitWidthScale = 1.0;
        this.pageRendering = false;
        this.pageContainers = [];
        this.isFullscreen = false;
        this.loadSeq = 0;
        this.renderSeq = 0;

        if (this.container) {
            this.init();
        }
    }

    async init() {
        this.renderSkeleton();
        try {
            await this.ensurePdfJsLoaded();
            await this.loadDocument();
        } catch (err) {
            console.warn('PDF.js rendering error, falling back to native frame:', err);
            this.renderNativeFallback();
        }
    }

    renderSkeleton() {
        this.container.innerHTML = `
            <div class="esign-viewer-wrapper">
                <!-- แถบเครื่องมือ (ซูม/พอดีหน้า/เต็มจอ) ถูกเอาออก — ใช้งานไม่สะดวกและชอบค้าง
                     เอกสารพอดีความกว้างอัตโนมัติ และปรับตามเมื่อขนาดหน้าจอเปลี่ยน -->
                <div class="esign-viewer-pages" id="${this.container.id}-pages-view"></div>
                <!-- ม่านโหลดอยู่ใน wrapper ไม่ใช่ใน pages-view: renderAllPages แทนที่ลูกของ pages-view
                     ทั้งหมด ถ้าม่านอยู่ข้างในจะถูกลบทิ้งตั้งแต่เริ่มวาด ก่อนเอกสารใหม่จะพร้อม -->
                <div class="esign-viewer-loading" id="${this.container.id}-loading">
                    <div class="esign-loading-card">
                        <div class="esign-spinner-wrapper">
                            <div class="esign-spinner"></div>
                            <div class="esign-spinner-icon"><i class="fas fa-file-signature"></i></div>
                        </div>
                        <h6 class="esign-loading-title" id="${this.container.id}-loading-title">กำลังจัดเตรียมตัวอย่างเอกสาร...</h6>

                        <div class="esign-loading-progress-bar">
                            <div class="esign-loading-progress-val"></div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        this.wrapperEl = this.container.querySelector('.esign-viewer-wrapper');
        this.pagesViewEl = document.getElementById(`${this.container.id}-pages-view`);
        this.curPageEl = document.getElementById(`${this.container.id}-cur-page`);
        this.totalPagesEl = document.getElementById(`${this.container.id}-total-pages`);
        this.loadingEl = document.getElementById(`${this.container.id}-loading`);

        this.bindEvents();
    }

    /** แสดงม่านโหลดทับเอกสารเดิม — เอกสารเดิมยังอยู่ข้างหลังจนกว่าฉบับใหม่จะวาดเสร็จ */
    showLoading(title) {
        if (!this.loadingEl) return;
        const titleEl = document.getElementById(`${this.container.id}-loading-title`);
        if (titleEl && title) titleEl.textContent = title;
        clearTimeout(this.hideTimer);
        this.loadingEl.style.display = 'flex';
        this.loadingEl.style.opacity = '1';
    }

    hideLoading() {
        if (!this.loadingEl) return;
        this.loadingEl.style.opacity = '0';
        clearTimeout(this.hideTimer);
        this.hideTimer = setTimeout(() => {
            if (this.loadingEl) this.loadingEl.style.display = 'none';
        }, 250);
    }

    bindEvents() {
        document.getElementById(`${this.container.id}-btn-zoom-in`)?.addEventListener('click', () => this.zoom(this.options.scaleStep));
        document.getElementById(`${this.container.id}-btn-zoom-out`)?.addEventListener('click', () => this.zoom(-this.options.scaleStep));
        document.getElementById(`${this.container.id}-btn-fit-width`)?.addEventListener('click', () => this.fitToWidth());
        document.getElementById(`${this.container.id}-btn-actual-size`)?.addEventListener('click', () => this.setZoom(1.0));
        document.getElementById(`${this.container.id}-btn-fullscreen`)?.addEventListener('click', () => this.toggleFullscreen());

        // Scroll listener to update page counter
        this.pagesViewEl?.addEventListener('scroll', () => {
            this.updateCurrentPageOnScroll();
        });

        // Resize observer
        if (window.ResizeObserver && this.pagesViewEl) {
            let resizeTimer;
            const observer = new ResizeObserver(() => {
                clearTimeout(resizeTimer);
                resizeTimer = setTimeout(() => {
                    if (this.pdfDoc && this.options.initialFit === 'width') {
                        this.fitToWidth();
                    }
                }, 200);
            });
            observer.observe(this.pagesViewEl);
        }
    }

    async ensurePdfJsLoaded() {
        if (window.pdfjsLib) {
            if (!window.pdfjsLib.GlobalWorkerOptions.workerSrc) {
                window.pdfjsLib.GlobalWorkerOptions.workerSrc = '/vendor/pdfjs/pdf.worker.min.js';
            }
            return;
        }

        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = '/vendor/pdfjs/pdf.min.js';
            script.onload = () => {
                if (window.pdfjsLib) {
                    window.pdfjsLib.GlobalWorkerOptions.workerSrc = '/vendor/pdfjs/pdf.worker.min.js';
                    resolve();
                } else {
                    reject(new Error('PDF.js library failed to initialize'));
                }
            };
            script.onerror = () => reject(new Error('Failed to load PDF.js'));
            document.head.appendChild(script);
        });
    }

    /**
     * โหลดเอกสารที่ this.pdfUrl แล้วสลับเข้าหน้าจอทีเดียวเมื่อวาดครบทุกหน้า
     *
     * @param seq ลำดับการโหลด — ผู้ใช้เปลี่ยนลายเซ็นซ้ำระหว่างโหลด รอบที่เก่ากว่าต้องไม่ทับรอบใหม่
     */
    async loadDocument(seq = ++this.loadSeq) {
        const url = this.pdfUrl;
        this.renderSeq++;
        try {
            const resp = await fetch(url, { method: 'HEAD' });
            const contentType = resp.headers.get('Content-Type') || '';
            const previewFormat = resp.headers.get('X-Preview-Format') || '';
            // หน้าลงนามฟังเพื่อเตือนผู้ลงนาม เช่นความเห็นยาวเกินช่องในเอกสาร (ดู SigningController.preview)
            if (seq === this.loadSeq) {
                document.dispatchEvent(new CustomEvent('esign:preview-headers', {
                    detail: { url: url, warning: resp.headers.get('X-Preview-Warning') || '' }
                }));
            }

            if (previewFormat === 'docx-fallback' || contentType.includes('wordprocessingml')) {
                if (seq !== this.loadSeq) return;
                this.renderDocxNotice();
                this.hideLoading();
                return;
            }
        } catch (e) {
            // Ignore HEAD check failure and try standard PDF loading
        }

        const loadingTask = window.pdfjsLib.getDocument({
            url: url,
            cMapUrl: '/vendor/pdfjs/cmaps/',
            cMapPacked: true
        });

        const pdfDoc = await loadingTask.promise;
        if (seq !== this.loadSeq) {
            pdfDoc.destroy();
            return;
        }

        const scale = await this.calculateFitWidthScale(pdfDoc);
        const rendered = await this.renderAllPages(pdfDoc, scale, seq);
        if (!rendered || seq !== this.loadSeq) {
            pdfDoc.destroy();
            return;
        }

        const previous = this.pdfDoc;
        this.pdfDoc = pdfDoc;
        this.pageCount = pdfDoc.numPages;
        this.fitWidthScale = scale;
        this.currentScale = scale;
        if (this.totalPagesEl) {
            this.totalPagesEl.textContent = this.pageCount;
        }
        if (previous && previous !== pdfDoc) previous.destroy();

        this.hideLoading();
    }

    renderDocxNotice() {
        if (this.pagesViewEl) {
            this.pagesViewEl.innerHTML = `
                <div class="alert alert-warning text-center m-4 p-4 shadow-sm u-maxw-600px">
                    <i class="fas fa-file-word fa-3x text-primary mb-3"></i>
                    <h5>ระบบกำลังแสดงเอกสารในรูปแบบต้นฉบับ DOCX</h5>
                    <p class="text-secondary small mb-3">
                        เซิร์ฟเวอร์ยังไม่ได้เปิดใช้งาน LibreOffice PDF Converter ท่านสามารถเปิดดูหรือดาวน์โหลดไฟล์เพื่อตรวจสอบเนื้อหาก่อนลงนามได้
                    </p>
                    <a href="${this.pdfUrl}" class="btn btn-primary btn-sm">
                        <i class="fas fa-download me-1"></i> ดาวน์โหลดเอกสาร DOCX เพื่อตรวจสอบ
                    </a>
                </div>
            `;
        }
    }

    async calculateFitWidthScale(pdfDoc = this.pdfDoc) {
        if (!pdfDoc || !this.pagesViewEl) return this.fitWidthScale;
        const page1 = await pdfDoc.getPage(1);
        const unscaledViewport = page1.getViewport({ scale: 1.0 });

        const availableWidth = this.pagesViewEl.clientWidth - 48; // subtract padding
        if (availableWidth > 0 && unscaledViewport.width > 0) {
            return Math.min(Math.max(availableWidth / unscaledViewport.width, 0.7), 2.2);
        }
        return 1.15;
    }

    /**
     * วาดทุกหน้าลงคอนเทนเนอร์ที่ยังไม่ติดหน้าจอ แล้วแทนที่ของเดิมทีเดียว
     * เดิมล้างหน้าจอก่อนแล้ววาดทีละหน้า เอกสารจึงหายแล้วค่อย ๆ โผล่ ไม่เปลี่ยนพร้อมม่านโหลด
     *
     * @return false ถ้ามีการวาดรอบใหม่แซงไปก่อน (ไม่แตะหน้าจอ)
     */
    async renderAllPages(pdfDoc = this.pdfDoc, scale = this.currentScale, loadSeq = null) {
        if (!pdfDoc || !this.pagesViewEl) return false;
        const isLoad = loadSeq !== null;
        const renderSeq = isLoad ? this.renderSeq : ++this.renderSeq;
        const startedLoad = this.loadSeq;
        const stale = () => isLoad
            ? loadSeq !== this.loadSeq
            : (renderSeq !== this.renderSeq || startedLoad !== this.loadSeq);

        const dpr = window.devicePixelRatio || 1;
        const pageCount = pdfDoc.numPages;
        const staged = [];

        for (let pageNum = 1; pageNum <= pageCount; pageNum++) {
            const page = await pdfDoc.getPage(pageNum);
            const viewport = page.getViewport({ scale: scale });

            const pageContainer = document.createElement('div');
            pageContainer.className = 'esign-page-container';
            pageContainer.dataset.pageNum = pageNum;
            pageContainer.style.width = `${Math.round(viewport.width)}px`;

            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');

            canvas.width = Math.round(viewport.width * dpr);
            canvas.height = Math.round(viewport.height * dpr);
            canvas.style.width = `${Math.round(viewport.width)}px`;
            canvas.style.height = `${Math.round(viewport.height)}px`;

            ctx.scale(dpr, dpr);

            const badge = document.createElement('div');
            badge.className = 'esign-page-badge';
            badge.textContent = `หน้า ${pageNum} / ${pageCount}`;

            pageContainer.appendChild(canvas);
            pageContainer.appendChild(badge);
            staged.push(pageContainer);

            await page.render({ canvasContext: ctx, viewport: viewport }).promise;

            if (stale()) return false;
        }

        this.pagesViewEl.replaceChildren(...staged);
        this.pageContainers = staged;
        return true;
    }

    zoom(delta) {
        const newScale = Math.min(Math.max(this.currentScale + delta, this.options.minScale), this.options.maxScale);
        if (Math.abs(newScale - this.currentScale) > 0.01) {
            this.currentScale = newScale;
            this.renderAllPages(this.pdfDoc, newScale);
        }
    }

    setZoom(scale) {
        this.currentScale = scale;
        this.renderAllPages(this.pdfDoc, scale);
    }

    async fitToWidth() {
        this.fitWidthScale = await this.calculateFitWidthScale();
        this.setZoom(this.fitWidthScale);
    }

    updateCurrentPageOnScroll() {
        if (!this.pagesViewEl || this.pageContainers.length === 0) return;

        const viewTop = this.pagesViewEl.scrollTop;
        const viewHeight = this.pagesViewEl.clientHeight;

        for (let i = 0; i < this.pageContainers.length; i++) {
            const el = this.pageContainers[i];
            const top = el.offsetTop - this.pagesViewEl.offsetTop;
            const bottom = top + el.clientHeight;

            if (bottom >= viewTop + (viewHeight / 3)) {
                const pNum = i + 1;
                if (this.currentPage !== pNum) {
                    this.currentPage = pNum;
                    if (this.curPageEl) {
                        this.curPageEl.textContent = pNum;
                    }
                }
                break;
            }
        }
    }

    toggleFullscreen() {
        this.isFullscreen = !this.isFullscreen;
        if (this.wrapperEl) {
            this.wrapperEl.classList.toggle('fullscreen-mode', this.isFullscreen);
            const btn = document.getElementById(`${this.container.id}-btn-fullscreen`);
            if (btn) {
                btn.innerHTML = this.isFullscreen ? '<i class="fas fa-compress"></i>' : '<i class="fas fa-expand"></i>';
            }
            setTimeout(() => this.fitToWidth(), 150);
        }
    }

    async loadNewPdf(newUrl) {
        if (!newUrl) return;
        this.pdfUrl = newUrl;
        const seq = ++this.loadSeq;
        this.showLoading('กำลังอัปเดตตัวอย่างพร้อมลายเซ็น...');

        if (this.frameEl) {
            this.loadIntoFrame(newUrl, seq);
            return;
        }

        try {
            await this.loadDocument(seq);
        } catch (err) {
            if (seq !== this.loadSeq) return;
            console.warn('PDF.js reload error, falling back to native frame:', err);
            this.renderNativeFallback();
        }
    }

    /** โหมดสำรอง (iframe): ม่านโหลดหายเมื่อ iframe โหลดฉบับใหม่เสร็จจริง ไม่ใช่ตามเวลาที่เดาไว้ */
    loadIntoFrame(url, seq) {
        const done = () => {
            if (seq === this.loadSeq) this.hideLoading();
        };
        this.frameEl.onload = done;
        // กันค้าง: ถ้าเบราว์เซอร์ไม่ยิง onload (บางตัวไม่ยิงกับ PDF) ก็ไม่ปล่อยม่านทิ้งไว้ตลอด
        setTimeout(done, 15000);
        this.frameEl.src = url + '#view=FitH&toolbar=1';
    }

    renderNativeFallback() {
        this.container.innerHTML = `
            <div class="esign-viewer-wrapper">
                <div class="esign-viewer-loading" id="${this.container.id}-loading">
                    <div class="esign-loading-card">
                        <div class="esign-spinner-wrapper">
                            <div class="esign-spinner"></div>
                            <div class="esign-spinner-icon"><i class="fas fa-file-signature"></i></div>
                        </div>
                        <h6 class="esign-loading-title" id="${this.container.id}-loading-title">กำลังจัดเตรียมตัวอย่างเอกสาร...</h6>

                        <div class="esign-loading-progress-bar">
                            <div class="esign-loading-progress-val"></div>
                        </div>
                    </div>
                </div>
                <iframe class="esign-fallback-frame"
                        id="${this.container.id}-frame"
                        title="เอกสารที่จะลงนาม"></iframe>
            </div>
        `;

        this.loadingEl = document.getElementById(`${this.container.id}-loading`);
        this.frameEl = document.getElementById(`${this.container.id}-frame`);

        if (this.frameEl) {
            this.loadIntoFrame(this.pdfUrl, this.loadSeq);
        }
    }
}

// Global helper for initializing viewers
window.initEsignPdfViewer = function(containerId, pdfUrl, options) {
    return new EsignPdfViewer(containerId, pdfUrl, options);
};
