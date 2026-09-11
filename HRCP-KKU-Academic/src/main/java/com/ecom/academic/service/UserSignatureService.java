package com.ecom.academic.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.SignatureKind;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.repository.UserSignatureRepository;
import com.ecom.model.UserDtls;
import com.ecom.service.SignatureImageStorage;

/**
 * Manages each person's reusable signature library.
 *
 * <p>Signing a document never creates a signature here — it picks one that
 * already exists. Keeping creation separate from signing means the evidence
 * trail for a signature and the act of applying it stay independent.
 */
@Service
public class UserSignatureService {

    private static final Logger log = LoggerFactory.getLogger(UserSignatureService.class);

    /**
     * How many signatures one person may keep.
     *
     * <p>A cap rather than unlimited storage: these are picked from a dropdown at
     * signing time, and a list long enough to scroll makes choosing the wrong one
     * easy. Three or four covers the real cases — a formal signature, an initial,
     * a typed fallback.
     */
    public static final int MAX_PER_USER = 5;

    private final UserSignatureRepository repository;
    private final SignatureImageStorage storage;

    public UserSignatureService(UserSignatureRepository repository, SignatureImageStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    public List<UserSignature> findMine(UserDtls owner) {
        return repository.findByUserIdAndIsDeletedFalseOrderByIsDefaultDescCreatedAtDesc(owner.getId());
    }

    public Optional<UserSignature> findMine(Long id, UserDtls owner) {
        return repository.findByIdAndUserIdAndIsDeletedFalse(id, owner.getId());
    }

    public Optional<UserSignature> findById(Long id) {
        return repository.findById(id).filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()));
    }

    public Optional<UserSignature> findDefault(UserDtls owner) {
        return repository.findByUserIdAndIsDefaultTrueAndIsDeletedFalse(owner.getId());
    }

    public long countMine(UserDtls owner) {
        return repository.countByUserIdAndIsDeletedFalse(owner.getId());
    }

    /** Raw image bytes for a signature the given user owns, or null. */
    public byte[] readImage(Long id, UserDtls owner) {
        return findMine(id, owner)
                .map(sig -> storage.read(sig.getImagePath()))
                .orElse(null);
    }

    /** The outcome of a create or update attempt: either the row, or why not. */
    public record SaveResult(UserSignature signature, String error) {
        public boolean ok() {
            return error == null;
        }

        static SaveResult failed(String error) {
            return new SaveResult(null, error);
        }
    }

    /**
     * Stores a new signature for someone.
     *
     * @param dataUrl the canvas PNG data URL produced in the browser
     */
    @Transactional
    public SaveResult create(UserDtls owner, String dataUrl, SignatureKind kind,
            String name, String typedText, String typedFont, boolean makeDefault) {

        if (countMine(owner) >= MAX_PER_USER) {
            return SaveResult.failed("เก็บลายเซ็นได้สูงสุด " + MAX_PER_USER
                    + " รายการ — ลบรายการเดิมก่อนเพิ่มใหม่");
        }

        byte[] png = storage.decodeDataUrl(dataUrl);
        if (png == null) {
            return SaveResult.failed("ไม่พบข้อมูลรูปลายเซ็น กรุณาวาด อัปโหลด หรือพิมพ์ชื่อก่อนบันทึก");
        }

        SignatureImageStorage.StoredImage stored = storage.store(png);
        if (stored == null) {
            return SaveResult.failed("รูปลายเซ็นไม่ถูกต้องหรือมีขนาดใหญ่เกินไป (จำกัด 1 MB)");
        }

        UserSignature signature = new UserSignature();
        signature.setUser(owner);
        signature.setKind(kind);
        signature.setName(cleanName(name));
        signature.setImagePath(stored.filename());
        signature.setWidthPx(stored.width());
        signature.setHeightPx(stored.height());
        if (kind == SignatureKind.TYPE) {
            signature.setTypedText(typedText);
            signature.setTypedFont(typedFont);
        }

        // The first signature someone saves becomes their default: otherwise they
        // would have a library with nothing pre-selected and an extra step at
        // every signing.
        boolean isFirst = countMine(owner) == 0;
        applyDefault(signature, owner, makeDefault || isFirst);

        return new SaveResult(repository.save(signature), null);
    }

    /**
     * Replaces an existing signature's image and/or metadata.
     *
     * <p>A new image supersedes the old file, which is deleted only after the row
     * points at the replacement — losing the new one and keeping a dangling
     * reference would be worse than briefly keeping both.
     */
    @Transactional
    public SaveResult update(Long id, UserDtls owner, String dataUrl, SignatureKind kind,
            String name, String typedText, String typedFont, boolean makeDefault) {

        UserSignature signature = findMine(id, owner).orElse(null);
        if (signature == null) {
            return SaveResult.failed("ไม่พบลายเซ็นที่ต้องการแก้ไข");
        }

        String previousImage = signature.getImagePath();
        String replacedImage = null;

        byte[] png = storage.decodeDataUrl(dataUrl);
        if (png != null) {
            SignatureImageStorage.StoredImage stored = storage.store(png);
            if (stored == null) {
                return SaveResult.failed("รูปลายเซ็นไม่ถูกต้องหรือมีขนาดใหญ่เกินไป");
            }
            signature.setImagePath(stored.filename());
            signature.setWidthPx(stored.width());
            signature.setHeightPx(stored.height());
            replacedImage = previousImage;
        }

        signature.setKind(kind);
        signature.setName(cleanName(name));
        if (kind == SignatureKind.TYPE) {
            signature.setTypedText(typedText);
            signature.setTypedFont(typedFont);
        } else {
            signature.setTypedText(null);
            signature.setTypedFont(null);
        }
        applyDefault(signature, owner, makeDefault);

        UserSignature saved = repository.save(signature);

        if (replacedImage != null) {
            storage.deleteIfPresent(replacedImage);
        }
        return new SaveResult(saved, null);
    }

    /**
     * Soft-deletes a signature and removes its image file.
     *
     * <p>The row survives because signed documents reference it as evidence; the
     * file goes because the owner asked for their image to be gone, and every
     * document that already used it holds its own copy of the stamped image.
     */
    @Transactional
    public boolean delete(Long id, UserDtls owner) {
        UserSignature signature = findMine(id, owner).orElse(null);
        if (signature == null) {
            return false;
        }
        signature.setIsDeleted(true);
        signature.setIsDefault(false);
        repository.save(signature);
        storage.deleteIfPresent(signature.getImagePath());
        log.info("Signature {} soft-deleted by its owner", id);
        return true;
    }

    /** Makes one signature the owner's default, clearing any previous one. */
    @Transactional
    public boolean makeDefault(Long id, UserDtls owner) {
        UserSignature signature = findMine(id, owner).orElse(null);
        if (signature == null) {
            return false;
        }
        applyDefault(signature, owner, true);
        repository.save(signature);
        return true;
    }

    /**
     * Sets the default flag, clearing the previous default first.
     *
     * <p>The flush matters: a partial unique index allows only one default per
     * person, so without forcing the clearing UPDATE out before the INSERT the
     * two would race inside the same transaction and violate the constraint.
     */
    private void applyDefault(UserSignature signature, UserDtls owner, boolean makeDefault) {
        if (!makeDefault) {
            if (signature.getIsDefault() == null) {
                signature.setIsDefault(false);
            }
            return;
        }
        repository.clearDefaultFor(owner.getId());
        repository.flush();
        signature.setIsDefault(true);
    }

    private String cleanName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
    }

    /**
     * Converts ASCII digits 0-9 into Thai digits ๐-๙.
     */
    public static String toThaiDigits(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append((char) ('\u0E50' + (c - '0')));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Generates a digital stamp PNG with exact timestamp and stores it, returning the stored filename.
     */
    public String generateAndStoreDigitalStamp(String signerName, java.time.LocalDateTime signedAt) {
        return generateAndStoreDigitalStamp(signerName, null, null, signedAt);
    }

    public String generateAndStoreDigitalStamp(String signerName, String signerEmail, java.time.LocalDateTime signedAt) {
        return generateAndStoreDigitalStamp(signerName, null, signerEmail, signedAt);
    }

    public String generateAndStoreDigitalStamp(String signerName, String signerPosition, String signerEmail, java.time.LocalDateTime signedAt) {
        byte[] png = generateDigitalStampPng(signerName, signerPosition, signerEmail, signedAt);
        if (png == null) {
            return null;
        }
        var stored = storage.store(png);
        return stored != null ? stored.filename() : null;
    }

    public byte[] generateDigitalStampPng(String signerName, java.time.LocalDateTime signedAt) {
        return generateDigitalStampPng(signerName, null, null, signedAt);
    }

    public byte[] generateDigitalStampPng(String signerName, String signerEmail, java.time.LocalDateTime signedAt) {
        return generateDigitalStampPng(signerName, null, signerEmail, signedAt);
    }

    public byte[] generateDigitalStampPng(String signerName, String signerPosition, String signerEmail, java.time.LocalDateTime signedAt) {
        if (signerName == null || signerName.isBlank()) {
            return null;
        }
        int width = 540;
        int height = 185;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = img.createGraphics();
        try {
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);

            // Background: clean white
            g2.setColor(java.awt.Color.WHITE);
            g2.fillRect(0, 0, width, height);

            String clean = signerName.trim();

            // Font setup
            java.awt.Font thaiFont = getThaiFont(36, java.awt.Font.BOLD);

            // Left Column: Large Name (Signer)
            g2.setColor(new java.awt.Color(10, 10, 10));
            String[] tokens = clean.split("\\s+");
            if (tokens.length >= 2) {
                int half = (tokens.length + 1) / 2;
                StringBuilder sb1 = new StringBuilder();
                for (int i = 0; i < half; i++) {
                    if (!sb1.isEmpty()) sb1.append(" ");
                    sb1.append(tokens[i]);
                }
                StringBuilder sb2 = new StringBuilder();
                for (int i = half; i < tokens.length; i++) {
                    if (!sb2.isEmpty()) sb2.append(" ");
                    sb2.append(tokens[i]);
                }
                String l1 = sb1.toString();
                String l2 = sb2.toString();

                g2.setFont(thaiFont);
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int w1 = fm.stringWidth(l1);
                int w2 = fm.stringWidth(l2);
                int maxW = 200;
                if (w1 > maxW || w2 > maxW) {
                    float scale = (float) maxW / Math.max(w1, w2);
                    g2.setFont(thaiFont.deriveFont(Math.max(36 * scale, 18f)));
                    fm = g2.getFontMetrics();
                }
                g2.drawString(l1, 115 - fm.stringWidth(l1) / 2, 75);
                g2.drawString(l2, 115 - fm.stringWidth(l2) / 2, 125);
            } else {
                g2.setFont(thaiFont);
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int w = fm.stringWidth(clean);
                int maxW = 200;
                if (w > maxW) {
                    float scale = (float) maxW / w;
                    g2.setFont(thaiFont.deriveFont(Math.max(36 * scale, 18f)));
                    fm = g2.getFontMetrics();
                }
                g2.drawString(clean, 115 - fm.stringWidth(clean) / 2, 100);
            }

            // Right Column: Metadata (Adobe Acrobat format)
            drawRightMetadata(g2, clean, signerPosition, signerEmail, signedAt, 260, 265);

        } finally {
            g2.dispose();
        }

        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (java.io.IOException e) {
            log.error("Failed to render digital stamp PNG: {}", e.toString());
            return null;
        }
    }

    /**
     * Generates a fresh digital stamp for any signature kind (TYPE, DRAW, UPLOAD) with the exact signing timestamp.
     */
    public String generateAndStoreFreshDigitalStampForSignature(UserSignature signature, String signerName, String signerEmail, java.time.LocalDateTime signedAt) {
        if (signature == null) {
            return null;
        }
        String resolvedName = (signerName != null && !signerName.isBlank())
                ? signerName
                : (signature.getUser() != null ? signature.getUser().getName() : "Signer");
        String resolvedPosition = signature.getUser() != null ? signature.getUser().getAcademicPosition() : null;

        if (signature.getKind() == SignatureKind.TYPE && signature.getTypedText() != null && !signature.getTypedText().isBlank()) {
            return generateAndStoreDigitalStamp(signature.getTypedText(), resolvedPosition, signerEmail, signedAt);
        }
        if (signature.getImagePath() != null && !signature.getImagePath().isBlank()) {
            byte[] existing = storage.read(signature.getImagePath());
            if (existing != null) {
                return generateAndStoreDigitalStampFromImage(existing, resolvedName, resolvedPosition, signerEmail, signedAt);
            }
        }
        return null;
    }

    /**
     * Generates and stores a digital stamp embedding an ink/drawn/uploaded signature image.
     */
    public String generateAndStoreDigitalStampFromImage(byte[] rawOrStampPng, String signerName, String signerEmail, java.time.LocalDateTime signedAt) {
        return generateAndStoreDigitalStampFromImage(rawOrStampPng, signerName, null, signerEmail, signedAt);
    }

    public String generateAndStoreDigitalStampFromImage(byte[] rawOrStampPng, String signerName, String signerPosition, String signerEmail, java.time.LocalDateTime signedAt) {
        byte[] png = generateDigitalStampFromImage(rawOrStampPng, signerName, signerPosition, signerEmail, signedAt);
        if (png == null) {
            return null;
        }
        var stored = storage.store(png);
        return stored != null ? stored.filename() : null;
    }

    /**
     * Renders an Adobe Acrobat style digital signature stamp embedding an ink/drawn/uploaded signature image:
     * - Left: user's hand-drawn or uploaded signature ink
     * - Right: 6-line (or 7-line with position) metadata block with fresh signedAt timestamp
     * - Borderless clean background
     */
    public byte[] generateDigitalStampFromImage(byte[] rawOrStampPng, String signerName, String signerEmail, java.time.LocalDateTime signedAt) {
        return generateDigitalStampFromImage(rawOrStampPng, signerName, null, signerEmail, signedAt);
    }

    public byte[] generateDigitalStampFromImage(byte[] rawOrStampPng, String signerName, String signerPosition, String signerEmail, java.time.LocalDateTime signedAt) {
        if (rawOrStampPng == null || rawOrStampPng.length == 0) {
            return generateDigitalStampPng(signerName, signerPosition, signerEmail, signedAt);
        }

        java.awt.image.BufferedImage sourceImg;
        try {
            sourceImg = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(rawOrStampPng));
        } catch (java.io.IOException e) {
            log.warn("Could not read signature image bytes: {}", e.toString());
            return generateDigitalStampPng(signerName, signerPosition, signerEmail, signedAt);
        }

        if (sourceImg == null) {
            return generateDigitalStampPng(signerName, signerPosition, signerEmail, signedAt);
        }

        // If the source image is already a 540x185 composite stamp, extract the left signature region (x: 0..215)
        java.awt.image.BufferedImage inkImg = sourceImg;
        if (sourceImg.getWidth() == 540 && sourceImg.getHeight() == 185) {
            int cropW = 215;
            int cropH = 185;
            inkImg = sourceImg.getSubimage(0, 0, cropW, cropH);
        }

        int width = 540;
        int height = 185;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = img.createGraphics();
        try {
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // Background: clean white
            g2.setColor(java.awt.Color.WHITE);
            g2.fillRect(0, 0, width, height);

            // Left Column: Draw the signature ink image, centered at x=115, y=92 within 200x135
            int maxW = 200;
            int maxH = 135;
            float scale = Math.min((float) maxW / inkImg.getWidth(), (float) maxH / inkImg.getHeight());
            if (scale > 1.0f) {
                scale = 1.0f;
            }
            int drawW = Math.round(inkImg.getWidth() * scale);
            int drawH = Math.round(inkImg.getHeight() * scale);
            int drawX = 115 - (drawW / 2);
            int drawY = 92 - (drawH / 2);

            g2.drawImage(inkImg, drawX, drawY, drawW, drawH, null);

            // Right Column: Metadata
            drawRightMetadata(g2, signerName, signerPosition, signerEmail, signedAt, 260, 265);

        } finally {
            g2.dispose();
        }

        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (java.io.IOException e) {
            log.error("Failed to render digital stamp PNG from image: {}", e.toString());
            return null;
        }
    }

    public static String formatSignerNameWithPosition(String signerName, String signerPosition, String signerTitle) {
        String clean = (signerName != null && !signerName.isBlank()) ? signerName.trim() : "Signer";
        String ttl = (signerTitle != null && !signerTitle.isBlank()) ? signerTitle.trim() : null;
        String pos = (signerPosition != null && !signerPosition.isBlank()) ? signerPosition.trim() : null;

        // If title is null, but pos is already an academic rank abbreviation (e.g. ผศ., รศ., ศ., ดร., อาจารย์, อ.)
        if (ttl == null && pos != null && (pos.startsWith("ผศ") || pos.startsWith("รศ") || pos.startsWith("ศ")
                || pos.startsWith("ดร") || pos.startsWith("อาจารย์") || pos.startsWith("อ."))) {
            ttl = pos;
        }

        // กรณีที่ 2: มีคำนำหน้าทางวิชาการ (เช่น ผศ.ดร., รศ.ดร., ศ.ดร., ผศ., รศ., ศ., ดร., อาจารย์, อ.ดร.)
        if (ttl != null && (ttl.startsWith("ผศ") || ttl.startsWith("รศ") || ttl.startsWith("ศ")
                || ttl.startsWith("ดร") || ttl.startsWith("อาจารย์") || ttl.startsWith("อ."))) {
            if (!clean.startsWith(ttl)) {
                clean = clean.replaceFirst("^(นาย|นางสาว|นาง)\\s*", "");
                clean = ttl + (ttl.endsWith(".") ? "" : " ") + clean;
            }
            return clean;
        }

        // กรณีที่ 3: ไม่มีคำนำหน้าทางวิชาการ -> แสดงเฉพาะชื่อ-นามสกุล โดยไม่ใส่คำนำหน้าทั่วไป (นาย/นาง/นางสาว) และไม่ใส่ตำแหน่งเต็ม (เช่น ผู้ช่วยศาสตราจารย์)
        clean = clean.replaceFirst("^(นาย|นางสาว|นาง)\\s*", "");
        return clean;
    }

    public static String formatSignerNameWithPosition(String signerName, String signerPosition) {
        return formatSignerNameWithPosition(signerName, signerPosition, null);
    }

    private void drawRightMetadata(java.awt.Graphics2D g2, String signerName, String signerEmail,
                                   java.time.LocalDateTime signedAt, int rightX, int maxRightW) {
        drawRightMetadata(g2, signerName, null, signerEmail, signedAt, rightX, maxRightW);
    }

    private void drawRightMetadata(java.awt.Graphics2D g2, String signerName, String signerPosition, String signerEmail,
                                   java.time.LocalDateTime signedAt, int rightX, int maxRightW) {
        String formattedName = formatSignerNameWithPosition(signerName, signerPosition);
        g2.setColor(new java.awt.Color(20, 20, 20));

        String email = (signerEmail != null && !signerEmail.isBlank())
                ? signerEmail.trim()
                : "sutoch@kku.ac.th";

        // Lines: Date & Time in Thai numerals
        java.time.LocalDateTime dt = signedAt != null ? signedAt : java.time.LocalDateTime.now();
        String rawDate = String.format("%04d.%02d.%02d %02d:%02d:%02d",
                dt.getYear(),
                dt.getMonthValue(),
                dt.getDayOfMonth(),
                dt.getHour(),
                dt.getMinute(),
                dt.getSecond());
        String dateLine = "Date: " + toThaiDigits(rawDate);
        String tzLine = "+" + toThaiDigits("07") + "'" + toThaiDigits("00") + "'";

        // 6 lines layout (Option 3 - position prefixed directly in signer name)
        drawFittedString(g2, "Digitally signed by " + formattedName, rightX, 38, maxRightW, 16f, java.awt.Font.PLAIN);
        drawFittedString(g2, "DN: c=TH, o=Khon Kaen", rightX, 64, maxRightW, 16f, java.awt.Font.PLAIN);
        drawFittedString(g2, "University, cn=" + formattedName + ",", rightX, 88, maxRightW, 16f, java.awt.Font.PLAIN);
        drawFittedString(g2, "email=" + email, rightX, 112, maxRightW, 16f, java.awt.Font.PLAIN);
        drawFittedString(g2, dateLine, rightX, 142, maxRightW, 16f, java.awt.Font.PLAIN);
        drawFittedString(g2, tzLine, rightX, 166, maxRightW, 16f, java.awt.Font.PLAIN);
    }

    private void drawFittedString(java.awt.Graphics2D g2, String text, int x, int y, int maxW, float baseSize, int style) {
        java.awt.Font baseFont = getThaiFont(Math.round(baseSize), style);
        g2.setFont(baseFont);
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(text);
        if (w > maxW && w > 0) {
            float scale = (float) maxW / w;
            g2.setFont(baseFont.deriveFont(Math.max(baseSize * scale, 10f)));
        }
        g2.drawString(text, x, y);
    }

    private java.awt.Font getThaiFont(int size, int style) {
        String[] fontNames = {"TH Sarabun New", "Sarabun", "Tahoma", "Leelawadee", "Angsana New", "Cordia New", "SansSerif"};
        for (String name : fontNames) {
            java.awt.Font f = new java.awt.Font(name, style, size);
            if (!f.getFamily().equals("Dialog") || name.equals("SansSerif")) {
                return f;
            }
        }
        return new java.awt.Font("SansSerif", style, size);
    }
}
