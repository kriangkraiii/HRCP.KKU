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

    let lastInkDataUrl = null;

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
        const typeNotice = document.getElementById('sigTypeNotice');
        if (typeNotice) {
            typeNotice.classList.toggle('d-none', !dataUrl);
        }
        saveButton.disabled = !dataUrl;
    }

    function refreshPreview() {
        if (activeKind === 'DRAW') {
            if (hasStrokes) {
                const trimmed = trimToInk(canvas);
                if (trimmed) {
                    renderStampFromInk(trimmed);
                } else {
                    setPreview(null);
                }
            } else {
                setPreview(null);
            }
        } else if (activeKind === 'TYPE') {
            renderTypedSignature();
        }
    }

    /* ------------------------------------------------------------- stamp utils */

    function getResolvedUserName() {
        const nameFromData = root.dataset.userName || document.body.dataset.userName || (typedTextInput?.dataset?.userName);
        return nameFromData && nameFromData.trim() ? nameFromData.trim() : 'สุธน เจริญศิริ';
    }

    function getResolvedUserTitle() {
        const titleFromData = root.dataset.userTitle || document.body.dataset.userTitle;
        return titleFromData && titleFromData.trim() ? titleFromData.trim() : '';
    }

    function getResolvedUserPosition() {
        const posFromData = root.dataset.userPosition || document.body.dataset.userPosition;
        return posFromData && posFromData.trim() ? posFromData.trim() : '';
    }

    function getResolvedUserEmail() {
        const emailFromData = root.dataset.userEmail || document.body.dataset.userEmail || (typedTextInput?.dataset?.userEmail);
        return emailFromData && emailFromData.trim() ? emailFromData.trim() : 'sutoch@kku.ac.th';
    }

    function toThaiDigits(str) {
        if (!str) return '';
        const thaiZeroCode = 0x0E50;
        return String(str).replace(/[0-9]/g, ch => String.fromCharCode(thaiZeroCode + (ch.charCodeAt(0) - 48)));
    }

    function formatSignerNameWithPosition(rawName, position, title) {
        let clean = (rawName || '').trim();
        if (!clean) clean = 'ผู้ใช้งานระบบ';
        let ttl = (title || '').trim();
        const pos = (position || '').trim();

        // If title is not set, but pos is already an abbreviation (e.g. ผศ., รศ., ศ., ดร., อาจารย์)
        if (!ttl && pos && /^(ผศ\.|รศ\.|ศ\.|อาจารย์|ดร\.|ศ\.\s*ดร|รศ\.\s*ดร|ผศ\.\s*ดร|อ\.ดร\.)/i.test(pos)) {
            ttl = pos;
        }

        // กรณีที่ 2: มีคำนำหน้าทางวิชาการ (เช่น ผศ.ดร., รศ.ดร., ศ.ดร., ผศ., รศ., ศ., ดร., อาจารย์, อ.ดร.)
        if (ttl && /^(ผศ\.|รศ\.|ศ\.|อาจารย์|ดร\.|ศ\.\s*ดร|รศ\.\s*ดร|ผศ\.\s*ดร|อ\.ดร\.)/i.test(ttl)) {
            if (!clean.startsWith(ttl)) {
                clean = clean.replace(/^(นาย|นางสาว|นาง)\s*/, '');
                clean = ttl + (ttl.endsWith('.') ? '' : ' ') + clean;
            }
            return clean;
        }

        // กรณีที่ 3: ไม่มีคำนำหน้าทางวิชาการ -> แสดงเฉพาะชื่อ-นามสกุล โดยไม่ใส่คำนำหน้าทั่วไป (นาย/นาง/นางสาว) และไม่ใส่ตำแหน่งเต็ม (เช่น ผู้ช่วยศาสตราจารย์)
        clean = clean.replace(/^(นาย|นางสาว|นาง)\s*/, '');
        return clean;
    }

    function drawRightMetadata(ctx, signerName, signerPosition, signerEmail, dateObj, rightX, maxRightW) {
        const title = getResolvedUserTitle();
        const formattedName = formatSignerNameWithPosition(signerName, signerPosition, title);

        // Date formatting: Bangkok +07'00' with Thai numerals
        const d = dateObj || new Date();
        const year = d.getFullYear();
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        const hours = String(d.getHours()).padStart(2, '0');
        const mins = String(d.getMinutes()).padStart(2, '0');
        const secs = String(d.getSeconds()).padStart(2, '0');

        const rawDate = `${year}.${month}.${day} ${hours}:${mins}:${secs}`;
        const dateLine = `Date: ${toThaiDigits(rawDate)}`;
        const timeLine = `+${toThaiDigits('07')}'${toThaiDigits('00')}'`;

        ctx.textAlign = 'left';
        ctx.textBaseline = 'alphabetic';
        ctx.fillStyle = '#141414';

        function drawFittedText(str, x, y, baseSize) {
            ctx.font = baseSize + 'px ' + TYPED_FONT;
            const w = ctx.measureText(str).width;
            if (w > maxRightW && w > 0) {
                const scale = maxRightW / w;
                ctx.font = Math.max(Math.floor(baseSize * scale), 10) + 'px ' + TYPED_FONT;
            }
            ctx.fillText(str, x, y);
        }

        const email = signerEmail || 'sutoch@kku.ac.th';

        // 6 lines layout (Option 3 - position prefixed directly in signer name)
        drawFittedText('Digitally signed by ' + formattedName, rightX, 38, 16);
        drawFittedText('DN: c=TH, o=Khon Kaen', rightX, 64, 16);
        drawFittedText('University, cn=' + formattedName + ',', rightX, 88, 16);
        drawFittedText('email=' + email, rightX, 112, 16);
        drawFittedText(dateLine, rightX, 142, 16);
        drawFittedText(timeLine, rightX, 166, 16);
    }

    /**
     * Draws an Adobe Acrobat style digital signature stamp embedding ink (DRAW or UPLOAD).
     */
    function drawDigitalSignatureStampWithInk(canvas, inkSource, signerName, dateObj, email) {
        const width = 540;
        const height = 185;
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d');

        // Background: clean white
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, width, height);

        // Left Column: Draw the ink (canvas or image)
        const inkW = inkSource.width || inkSource.naturalWidth || 1;
        const inkH = inkSource.height || inkSource.naturalHeight || 1;
        const maxW = 200;
        const maxH = 135;
        const scale = Math.min(maxW / inkW, maxH / inkH, 1.0);
        const drawW = Math.round(inkW * scale);
        const drawH = Math.round(inkH * scale);
        const drawX = 115 - Math.round(drawW / 2);
        const drawY = 92 - Math.round(drawH / 2);

        ctx.drawImage(inkSource, drawX, drawY, drawW, drawH);

        // Right Column: Metadata
        const rightX = 260;
        const maxRightW = 265;
        const resolvedEmail = email || getResolvedUserEmail();
        const resolvedName = signerName || getResolvedUserName();
        const resolvedPosition = getResolvedUserPosition();
        drawRightMetadata(ctx, resolvedName, resolvedPosition, resolvedEmail, dateObj, rightX, maxRightW);
    }

    /**
     * Draws an Adobe Acrobat style digital signature stamp with typed Thai text.
     */
    function drawDigitalSignatureStamp(canvas, text, dateObj, email) {
        const width = 540;
        const height = 185;
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d');

        // Background: clean white
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, width, height);

        const clean = (text || '').trim();

        // Left Column: Large Thai Name
        ctx.fillStyle = '#0a0a0a';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';

        const leftCenterX = 115;
        const words = clean.split(/\s+/).filter(Boolean);
        if (words.length >= 2) {
            const line1 = words.slice(0, Math.ceil(words.length / 2)).join(' ');
            const line2 = words.slice(Math.ceil(words.length / 2)).join(' ');

            ctx.font = 'bold 36px ' + TYPED_FONT;
            const maxW = 200;
            const w1 = ctx.measureText(line1).width;
            const w2 = ctx.measureText(line2).width;
            if (w1 > maxW || w2 > maxW) {
                const scale = Math.min(maxW / Math.max(w1, w2), 1.0);
                ctx.font = 'bold ' + Math.max(Math.floor(36 * scale), 18) + 'px ' + TYPED_FONT;
            }
            ctx.fillText(line1, leftCenterX, 75);
            ctx.fillText(line2, leftCenterX, 125);
        } else {
            ctx.font = 'bold 36px ' + TYPED_FONT;
            const w = ctx.measureText(clean).width;
            const maxW = 200;
            if (w > maxW) {
                const scale = maxW / w;
                ctx.font = 'bold ' + Math.max(Math.floor(36 * scale), 18) + 'px ' + TYPED_FONT;
            }
            ctx.fillText(clean, leftCenterX, 100);
        }

        // Right Column: Metadata (Adobe Acrobat format)
        const rightX = 260;
        const maxRightW = 265;
        const resolvedEmail = email || getResolvedUserEmail();
        const resolvedPosition = getResolvedUserPosition();
        drawRightMetadata(ctx, clean, resolvedPosition, resolvedEmail, dateObj, rightX, maxRightW);
    }

    /**
     * Composites the raw ink (from DRAW or UPLOAD) into a 540x185 digital signature stamp.
     */
    function renderStampFromInk(inkDataUrl) {
        if (!inkDataUrl) {
            setPreview(null);
            return;
        }
        lastInkDataUrl = inkDataUrl;
        const img = new Image();
        img.onload = function () {
            const out = document.createElement('canvas');
            drawDigitalSignatureStampWithInk(out, img, getResolvedUserName(), new Date(), getResolvedUserEmail());
            setPreview(out.toDataURL('image/png'));
        };
        img.src = inkDataUrl;
    }

    /**
     * Renders the typed name into an official digital signature stamp.
     */
    function renderTypedSignature() {
        const text = (typedTextInput.value || '').trim();
        if (!text) {
            setPreview(null);
            return;
        }

        const userEmail = getResolvedUserEmail();

        const draw = function () {
            const out = document.createElement('canvas');
            drawDigitalSignatureStamp(out, text, new Date(), userEmail);
            typedFontField.value = TYPED_FONT;
            setPreview(out.toDataURL('image/png'));
        };

        if (document.fonts && document.fonts.ready) {
            document.fonts.ready.then(draw).catch(draw);
        } else {
            draw();
        }
    }

    /* --------------------------------------------------------------- upload */

    function ensurePdfJs() {
        if (window.pdfjsLib) {
            if (!window.pdfjsLib.GlobalWorkerOptions.workerSrc) {
                window.pdfjsLib.GlobalWorkerOptions.workerSrc = '/vendor/pdfjs/pdf.worker.min.js';
            }
            return Promise.resolve(window.pdfjsLib);
        }
        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = '/vendor/pdfjs/pdf.min.js';
            script.onload = () => {
                if (window.pdfjsLib) {
                    window.pdfjsLib.GlobalWorkerOptions.workerSrc = '/vendor/pdfjs/pdf.worker.min.js';
                    resolve(window.pdfjsLib);
                } else {
                    reject(new Error('PDF.js failed to initialize'));
                }
            };
            script.onerror = () => {
                // Fallback to CDN
                const cdn = document.createElement('script');
                cdn.src = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js';
                cdn.onload = () => {
                    if (window.pdfjsLib) {
                        window.pdfjsLib.GlobalWorkerOptions.workerSrc =
                            'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';
                        resolve(window.pdfjsLib);
                    } else {
                        reject(new Error('PDF.js CDN failed'));
                    }
                };
                cdn.onerror = () => reject(new Error('Failed to load PDF.js'));
                document.head.appendChild(cdn);
            };
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
                renderStampFromInk(trimmed);
            }
        } catch (err) {
            console.error('PDF parsing error:', err);
            let msg = 'ไม่สามารถอ่านไฟล์ PDF นี้ได้';
            if (err.name === 'PasswordException') {
                msg = 'ไฟล์ PDF นี้ติดรหัสผ่าน กรุณาใช้ไฟล์ PDF ที่ไม่มีการตั้งรหัสผ่าน';
            } else {
                msg = 'ไม่สามารถอ่านไฟล์ PDF นี้ได้ (' + (err.message || 'เกิดข้อผิดพลาด') + ') กรุณาลองใช้ไฟล์รูปภาพ PNG / JPG แทน';
            }
            window.alert(msg);
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
                const trimmed = trimToInk(canvas);
                if (trimmed) {
                    renderStampFromInk(trimmed);
                } else {
                    setPreview(null);
                }
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

        // Refresh preview for the newly activated tab
        if (kind === 'DRAW') {
            refreshPreview();
        } else if (kind === 'TYPE') {
            renderTypedSignature();
        } else {
            if (lastInkDataUrl) {
                renderStampFromInk(lastInkDataUrl);
            } else {
                setPreview(null);
            }
            uploadInput.value = '';
        }
    }

    tabButtons.forEach(function (btn) {
        btn.addEventListener('click', function () {
            activateTab(btn.dataset.sigTab);
        });
    });

    typedTextInput.addEventListener('input', renderTypedSignature);

    const sigNameInput = document.getElementById('sigName') || document.querySelector('input[name="name"]');
    if (sigNameInput) {
        sigNameInput.addEventListener('input', function () {
            if (activeKind === 'DRAW' || activeKind === 'UPLOAD') {
                if (lastInkDataUrl) {
                    renderStampFromInk(lastInkDataUrl);
                }
            }
        });
    }

    clearButton.addEventListener('click', function () {
        lastInkDataUrl = null;
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
