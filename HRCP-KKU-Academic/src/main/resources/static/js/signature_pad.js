/**
 * Signature capture for the "ลายเซ็นของฉัน" page.
 *
 * Three ways in — draw, upload an image, type a name — and exactly one way out:
 * a trimmed, transparent PNG data URL in a hidden input. Normalising in the
 * browser means the server, the DOCX stamper and the signing page all deal with
 * a single format.
 *
 * Loaded as an external file rather than inlined because the app sends a strict
 * CSP (`script-src 'self' 'nonce-...'`); a separate file needs no nonce plumbing.
 */
(function () {
    'use strict';

    const root = document.getElementById('signatureEditor');
    if (!root) {
        return;
    }

    const canvas = document.getElementById('sigCanvas');
    const ctx = canvas.getContext('2d');
    const imageDataField = document.getElementById('sigImageData');
    const kindField = document.getElementById('sigKind');
    const typedTextInput = document.getElementById('sigTypedText');
    const typedFontField = document.getElementById('sigTypedFont');
    const uploadInput = document.getElementById('sigUpload');
    const previewImg = document.getElementById('sigPreview');
    const previewEmpty = document.getElementById('sigPreviewEmpty');
    const saveButton = document.getElementById('sigSaveBtn');
    const clearButton = document.getElementById('sigClearBtn');
    const tabButtons = Array.from(root.querySelectorAll('[data-sig-tab]'));
    const panels = {
        DRAW: document.getElementById('sigPanelDraw'),
        UPLOAD: document.getElementById('sigPanelUpload'),
        TYPE: document.getElementById('sigPanelType')
    };

    // The ink is always black regardless of the page theme: this image is
    // stamped onto a white document, so a theme-coloured stroke would come out
    // invisible or wrong in the PDF.
    const INK = '#111111';

    /**
     * Face for typed signatures.
     *
     * Sarabun is the only Thai font this app self-hosts (it runs on an internal
     * network with no guaranteed outbound access, so webfont CDNs are not an
     * option) and it ships upright only. Rather than add a handwriting font as a
     * new binary dependency, the slant below is applied on the canvas — that
     * gives the signature look without another asset to vendor and licence. Drop
     * a script face into static/vendor/fonts and change this to use it.
     */
    const TYPED_FONT = "'Sarabun', sans-serif";

    /** Horizontal shear for the synthetic oblique. Roughly a 12° lean. */
    const TYPED_SLANT = -0.21;

    let drawing = false;
    let hasStrokes = false;
    let lastX = 0;
    let lastY = 0;
    let activeKind = 'DRAW';

    /* ---------------------------------------------------------------- canvas */

    /**
     * Sizes the backing store to the element's CSS size times the device pixel
     * ratio. Without this the canvas is stretched from its default 300x150 and
     * strokes land away from the cursor on any HiDPI screen.
     */
    function resizeCanvas() {
        const ratio = window.devicePixelRatio || 1;
        const rect = canvas.getBoundingClientRect();
        const snapshot = hasStrokes ? canvas.toDataURL('image/png') : null;

        canvas.width = Math.round(rect.width * ratio);
        canvas.height = Math.round(rect.height * ratio);
        ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
        ctx.lineWidth = 2.5;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';
        ctx.strokeStyle = INK;

        if (snapshot) {
            const img = new Image();
            img.onload = function () {
                ctx.drawImage(img, 0, 0, rect.width, rect.height);
            };
            img.src = snapshot;
        }
    }

    function pointerPos(event) {
        const rect = canvas.getBoundingClientRect();
        return { x: event.clientX - rect.left, y: event.clientY - rect.top };
    }

    function startStroke(event) {
        if (activeKind !== 'DRAW') {
            return;
        }
        drawing = true;
        const pos = pointerPos(event);
        lastX = pos.x;
        lastY = pos.y;
        // A tap with no movement should still leave a mark.
        ctx.beginPath();
        ctx.moveTo(lastX, lastY);
        ctx.lineTo(lastX, lastY);
        ctx.stroke();
        hasStrokes = true;
        canvas.setPointerCapture(event.pointerId);
        event.preventDefault();
    }

    function continueStroke(event) {
        if (!drawing) {
            return;
        }
        const pos = pointerPos(event);
        ctx.beginPath();
        ctx.moveTo(lastX, lastY);
        ctx.lineTo(pos.x, pos.y);
        ctx.stroke();
        lastX = pos.x;
        lastY = pos.y;
        hasStrokes = true;
        event.preventDefault();
    }

    function endStroke(event) {
        if (!drawing) {
            return;
        }
        drawing = false;
        if (event.pointerId !== undefined && canvas.hasPointerCapture(event.pointerId)) {
            canvas.releasePointerCapture(event.pointerId);
        }
        refreshPreview();
    }

    // Pointer events cover mouse, pen and touch in one set of handlers.
    canvas.addEventListener('pointerdown', startStroke);
    canvas.addEventListener('pointermove', continueStroke);
    canvas.addEventListener('pointerup', endStroke);
    canvas.addEventListener('pointercancel', endStroke);
    canvas.addEventListener('pointerleave', endStroke);

    /* ----------------------------------------------------------------- trim */

    /**
     * Crops the transparent margin around the ink.
     *
     * Matters for placement: the stamper scales a signature to a fixed width, so
     * an image padded with empty space would render the actual signature far
     * smaller than the one next to it. Returns null when the canvas is blank.
     */
    function trimToInk(source) {
        const w = source.width;
        const h = source.height;
        if (!w || !h) {
            return null;
        }

        const sourceCtx = source.getContext('2d');
        let pixels;
        try {
            pixels = sourceCtx.getImageData(0, 0, w, h).data;
        } catch (e) {
            // A tainted canvas cannot be read back; fall back to the untrimmed image.
            return source.toDataURL('image/png');
        }

        let top = h, left = w, right = -1, bottom = -1;
        for (let y = 0; y < h; y++) {
            for (let x = 0; x < w; x++) {
                // Alpha only: the ink is opaque, the background is transparent.
                if (pixels[(y * w + x) * 4 + 3] > 8) {
                    if (y < top) { top = y; }
                    if (y > bottom) { bottom = y; }
                    if (x < left) { left = x; }
                    if (x > right) { right = x; }
                }
            }
        }
        if (right < 0 || bottom < 0) {
            return null;
        }

        const pad = 6;
        left = Math.max(0, left - pad);
        top = Math.max(0, top - pad);
        right = Math.min(w - 1, right + pad);
        bottom = Math.min(h - 1, bottom + pad);

        const out = document.createElement('canvas');
        out.width = right - left + 1;
        out.height = bottom - top + 1;
        out.getContext('2d').drawImage(source, left, top, out.width, out.height,
            0, 0, out.width, out.height);
        return out.toDataURL('image/png');
    }

    /**
     * Converts white/light background of scanned signatures or PDFs into transparent pixels.
     */
    function processCanvasBackground(sourceCanvas) {
        const out = document.createElement('canvas');
        out.width = sourceCanvas.width;
        out.height = sourceCanvas.height;
        const outCtx = out.getContext('2d');
        outCtx.drawImage(sourceCanvas, 0, 0);

        let imgData;
        try {
            imgData = outCtx.getImageData(0, 0, out.width, out.height);
        } catch (e) {
            return sourceCanvas;
        }

        const data = imgData.data;
        let whiteCount = 0;
        const total = out.width * out.height;

        for (let i = 0; i < data.length; i += 4) {
            const r = data[i], g = data[i + 1], b = data[i + 2], a = data[i + 3];
            if (a > 100 && r > 220 && g > 220 && b > 220) {
                whiteCount++;
            }
        }

        // If at least 20% is white background, convert light pixels to transparent
        if (whiteCount / total > 0.20) {
            for (let i = 0; i < data.length; i += 4) {
                const r = data[i], g = data[i + 1], b = data[i + 2], a = data[i + 3];
                if (r > 215 && g > 215 && b > 215) {
                    data[i + 3] = 0; // completely transparent
                } else if (r > 175 && g > 175 && b > 175) {
                    // Soft alpha edge
                    const brightness = (r + g + b) / 3;
                    const factor = (215 - brightness) / 40;
                    data[i + 3] = Math.round(a * Math.max(0, Math.min(1, factor)));
                }
            }
            outCtx.putImageData(imgData, 0, 0);
        }
        return out;
    }

    /** Draws an already-loaded image onto a fresh transparent canvas, scaled to fit. */
    function canvasFromImage(img, maxW, maxH) {
        const scale = Math.min(maxW / img.width, maxH / img.height, 1);
        const out = document.createElement('canvas');
        out.width = Math.max(1, Math.round(img.width * scale));
        out.height = Math.max(1, Math.round(img.height * scale));
        out.getContext('2d').drawImage(img, 0, 0, out.width, out.height);
        return processCanvasBackground(out);
    }

    /* -------------------------------------------------------------- preview */

    function setPreview(dataUrl) {
        imageDataField.value = dataUrl || '';
        if (dataUrl) {
            previewImg.src = dataUrl;
            previewImg.classList.remove('d-none');
            previewEmpty.classList.add('d-none');
        } else {
            previewImg.removeAttribute('src');
            previewImg.classList.add('d-none');
            previewEmpty.classList.remove('d-none');
        }
        saveButton.disabled = !dataUrl;
    }

    function refreshPreview() {
        if (activeKind === 'DRAW') {
            setPreview(hasStrokes ? trimToInk(canvas) : null);
        } else if (activeKind === 'TYPE') {
            renderTypedSignature();
        }
        // UPLOAD sets its preview when the file finishes loading.
    }

    /* ----------------------------------------------------------------- type */

    /**
     * Renders the typed name to a canvas in a handwriting face.
     *
     * Waits on document.fonts before measuring: drawing while the webfont is
     * still loading silently falls back to a system font, which then gets baked
     * into the saved PNG.
     */
    function renderTypedSignature() {
        const text = (typedTextInput.value || '').trim();
        if (!text) {
            setPreview(null);
            return;
        }

        const draw = function () {
            const fontSpec = '64px ' + TYPED_FONT;
            const measure = document.createElement('canvas').getContext('2d');
            measure.font = fontSpec;
            const width = Math.ceil(measure.measureText(text).width) + 90;

            const out = document.createElement('canvas');
            out.width = Math.min(width, 1500);
            out.height = 160;
            const outCtx = out.getContext('2d');
            outCtx.font = fontSpec;
            outCtx.fillStyle = INK;
            outCtx.textBaseline = 'middle';
            outCtx.textAlign = 'center';

            outCtx.translate(0, out.height / 2);
            outCtx.transform(1, 0, TYPED_SLANT, 1, 0, 0);
            outCtx.fillText(text, out.width / 2, 0);
            outCtx.setTransform(1, 0, 0, 1, 0, 0);

            typedFontField.value = TYPED_FONT;
            setPreview(trimToInk(out));
        };

        if (document.fonts && document.fonts.ready) {
            document.fonts.ready.then(draw).catch(draw);
        } else {
            draw();
        }
    }

    /* --------------------------------------------------------------- upload */

    function ensurePdfJs() {
        if (window.pdfjsLib) return Promise.resolve(window.pdfjsLib);
        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js';
            script.onload = () => {
                if (window.pdfjsLib) {
                    window.pdfjsLib.GlobalWorkerOptions.workerSrc =
                        'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';
                    resolve(window.pdfjsLib);
                } else {
                    reject(new Error('PDF.js failed'));
                }
            };
            script.onerror = () => reject(new Error('Failed to load PDF.js'));
            document.head.appendChild(script);
        });
    }

    async function handlePdfUpload(file) {
        try {
            previewEmpty.textContent = 'กำลังประมวลผลไฟล์ PDF...';
            const pdfjs = await ensurePdfJs();
            const buffer = await file.arrayBuffer();
            const pdf = await pdfjs.getDocument({ data: buffer }).promise;
            const page = await pdf.getPage(1);
            const viewport = page.getViewport({ scale: 2.0 });

            const pdfCanvas = document.createElement('canvas');
            pdfCanvas.width = viewport.width;
            pdfCanvas.height = viewport.height;
            const pdfCtx = pdfCanvas.getContext('2d');

            await page.render({ canvasContext: pdfCtx, viewport: viewport }).promise;

            const processed = processCanvasBackground(pdfCanvas);
            const trimmed = trimToInk(processed);

            if (!trimmed) {
                window.alert('ไม่พบลายเซ็นหรือเนื้อหาในหน้าแรกของไฟล์ PDF');
                setPreview(null);
            } else {
                setPreview(trimmed);
            }
        } catch (err) {
            console.error('PDF parsing error:', err);
            window.alert('ไม่สามารถอ่านไฟล์ PDF นี้ได้ กรุณาลองใช้ไฟล์รูปภาพ PNG / JPG แทน');
            setPreview(null);
        } finally {
            previewEmpty.textContent = 'ยังไม่มีลายเซ็น — วาด อัปโหลด หรือพิมพ์ชื่อทางด้านซ้าย';
        }
    }

    uploadInput.addEventListener('change', function () {
        const file = uploadInput.files && uploadInput.files[0];
        if (!file) {
            setPreview(null);
            return;
        }

        const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
        const isImage = file.type.startsWith('image/') || /\.(png|jpe?g|webp)$/i.test(file.name);

        if (!isPdf && !isImage) {
            window.alert('กรุณาเลือกไฟล์รูปภาพ (PNG, JPG) หรือไฟล์ PDF');
            uploadInput.value = '';
            return;
        }

        if (isPdf) {
            handlePdfUpload(file);
            return;
        }

        const reader = new FileReader();
        reader.onload = function () {
            const img = new Image();
            img.onload = function () {
                const canvas = canvasFromImage(img, 1500, 500);
                setPreview(trimToInk(canvas));
            };
            img.onerror = function () {
                window.alert('ไม่สามารถอ่านไฟล์รูปภาพนี้ได้');
            };
            img.src = reader.result;
        };
        reader.readAsDataURL(file);
    });

    /* ----------------------------------------------------------------- tabs */

    function activateTab(kind) {
        activeKind = kind;
        kindField.value = kind;

        tabButtons.forEach(function (btn) {
            const isActive = btn.dataset.sigTab === kind;
            btn.classList.toggle('active', isActive);
            btn.setAttribute('aria-selected', isActive ? 'true' : 'false');
        });
        Object.keys(panels).forEach(function (key) {
            if (panels[key]) {
                panels[key].classList.toggle('d-none', key !== kind);
            }
        });

        // Each tab owns its own artwork, so switching clears what the previous
        // one produced rather than saving a signature the user is no longer
        // looking at.
        if (kind === 'DRAW') {
            refreshPreview();
        } else if (kind === 'TYPE') {
            renderTypedSignature();
        } else {
            setPreview(null);
            uploadInput.value = '';
        }
    }

    tabButtons.forEach(function (btn) {
        btn.addEventListener('click', function () {
            activateTab(btn.dataset.sigTab);
        });
    });

    typedTextInput.addEventListener('input', renderTypedSignature);

    clearButton.addEventListener('click', function () {
        if (activeKind === 'DRAW') {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            hasStrokes = false;
        } else if (activeKind === 'TYPE') {
            typedTextInput.value = '';
        } else {
            uploadInput.value = '';
        }
        setPreview(null);
    });

    /* ----------------------------------------------------------------- init */

    window.addEventListener('resize', resizeCanvas);
    resizeCanvas();
    activateTab(root.dataset.initialKind || 'DRAW');
    if (activeKind === 'TYPE') {
        renderTypedSignature();
    }
})();
