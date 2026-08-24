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
                <div class="esign-viewer-toolbar">
                    <div class="tool-group">
                        <button type="button" class="btn-tool" id="${this.container.id}-btn-zoom-out" title="ย่อ (-)">
                            <i class="fas fa-magnifying-glass-minus"></i>
                        </button>
                        <button type="button" class="btn-tool" id="${this.container.id}-btn-zoom-in" title="ขยาย (+)">
                            <i class="fas fa-magnifying-glass-plus"></i>
                        </button>
                        <button type="button" class="btn-tool" id="${this.container.id}-btn-fit-width" title="พอดีความกว้างหน้าจอ">
                            <i class="fas fa-arrows-left-right"></i> พอดีหน้า
                        </button>
                        <button type="button" class="btn-tool" id="${this.container.id}-btn-actual-size" title="ขนาดจริง (100%)">
                            <i class="fas fa-compress"></i> 100%
                        </button>
                    </div>
                    <div class="tool-group">
                        <span class="page-indicator" id="${this.container.id}-page-indicator">
                            หน้า <span id="${this.container.id}-cur-page">1</span> / <span id="${this.container.id}-total-pages">-</span>
                        </span>
                        <button type="button" class="btn-tool" id="${this.container.id}-btn-fullscreen" title="ดูเต็มจอ">
                            <i class="fas fa-expand"></i>
                        </button>
                        <a href="${this.pdfUrl}" target="_blank" class="btn-tool" title="เปิดแท็บใหม่">
                            <i class="fas fa-arrow-up-right-from-square"></i>
                        </a>
                    </div>
                </div>
                <div class="esign-viewer-pages" id="${this.container.id}-pages-view">
                    <div class="esign-viewer-loading" id="${this.container.id}-loading">
                        <div class="spinner-border text-light" role="status"></div>
                        <span>กำลังจัดเตรียมเอกสารต้นฉบับ...</span>
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
        if (window.pdfjsLib) return;

        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js';
            script.onload = () => {
                if (window.pdfjsLib) {
                    window.pdfjsLib.GlobalWorkerOptions.workerSrc =
                        'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';
                    resolve();
                } else {
                    reject(new Error('PDF.js library failed to initialize'));
                }
            };
            script.onerror = () => reject(new Error('Failed to load PDF.js from CDN'));
            document.head.appendChild(script);
        });
    }

    async loadDocument() {
        try {
            const resp = await fetch(this.pdfUrl, { method: 'HEAD' });
            const contentType = resp.headers.get('Content-Type') || '';
            const previewFormat = resp.headers.get('X-Preview-Format') || '';

            if (previewFormat === 'docx-fallback' || contentType.includes('wordprocessingml')) {
                this.renderDocxNotice();
                return;
            }
        } catch (e) {
            // Ignore HEAD check failure and try standard PDF loading
        }

        const loadingTask = window.pdfjsLib.getDocument({
            url: this.pdfUrl,
            cMapUrl: 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/cmaps/',
            cMapPacked: true
        });

        this.pdfDoc = await loadingTask.promise;
        this.pageCount = this.pdfDoc.numPages;
        if (this.totalPagesEl) {
            this.totalPagesEl.textContent = this.pageCount;
        }

        if (this.loadingEl) {
            this.loadingEl.style.display = 'none';
        }

        await this.calculateFitWidthScale();
        this.currentScale = this.fitWidthScale;
        await this.renderAllPages();
    }

    renderDocxNotice() {
        if (this.pagesViewEl) {
            this.pagesViewEl.innerHTML = `
                <div class="alert alert-warning text-center m-4 p-4 shadow-sm" style="max-width: 600px;">
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

    async calculateFitWidthScale() {
        if (!this.pdfDoc || !this.pagesViewEl) return;
        const page1 = await this.pdfDoc.getPage(1);
        const unscaledViewport = page1.getViewport({ scale: 1.0 });

        const availableWidth = this.pagesViewEl.clientWidth - 48; // subtract padding
        if (availableWidth > 0 && unscaledViewport.width > 0) {
            this.fitWidthScale = Math.min(Math.max(availableWidth / unscaledViewport.width, 0.7), 2.2);
        } else {
            this.fitWidthScale = 1.15;
        }
    }

    async renderAllPages() {
        if (!this.pdfDoc || !this.pagesViewEl) return;

        this.pagesViewEl.innerHTML = '';
        this.pageContainers = [];

        const dpr = window.devicePixelRatio || 1;

        for (let pageNum = 1; pageNum <= this.pageCount; pageNum++) {
            const page = await this.pdfDoc.getPage(pageNum);
            const viewport = page.getViewport({ scale: this.currentScale });

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
            badge.textContent = `หน้า ${pageNum} / ${this.pageCount}`;

            pageContainer.appendChild(canvas);
            pageContainer.appendChild(badge);
            this.pagesViewEl.appendChild(pageContainer);
            this.pageContainers.push(pageContainer);

            const renderContext = {
                canvasContext: ctx,
                viewport: viewport
            };

            await page.render(renderContext).promise;
        }
    }

    zoom(delta) {
        const newScale = Math.min(Math.max(this.currentScale + delta, this.options.minScale), this.options.maxScale);
        if (Math.abs(newScale - this.currentScale) > 0.01) {
            this.currentScale = newScale;
            this.renderAllPages();
        }
    }

    setZoom(scale) {
        this.currentScale = scale;
        this.renderAllPages();
    }

    async fitToWidth() {
        await this.calculateFitWidthScale();
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
        const openTabBtn = this.container.querySelector('a[title="เปิดแท็บใหม่"]');
        if (openTabBtn) {
            openTabBtn.href = newUrl;
        }
        if (this.loadingEl) {
            this.loadingEl.style.display = 'flex';
            this.loadingEl.classList.remove('d-none');
            if (this.pagesViewEl) {
                this.pagesViewEl.innerHTML = '';
                this.pagesViewEl.appendChild(this.loadingEl);
            }
        }
        try {
            await this.loadDocument();
        } catch (err) {
            console.warn('PDF.js reload error, falling back to native frame:', err);
            this.renderNativeFallback();
        }
    }

    renderNativeFallback() {
        this.container.innerHTML = `
            <div class="esign-viewer-wrapper">
                <iframe src="${this.pdfUrl}#view=FitH&toolbar=1"
                        class="esign-fallback-frame"
                        title="เอกสารที่จะลงนาม"></iframe>
            </div>
        `;
    }
}

// Global helper for initializing viewers
window.initEsignPdfViewer = function(containerId, pdfUrl, options) {
    return new EsignPdfViewer(containerId, pdfUrl, options);
};
