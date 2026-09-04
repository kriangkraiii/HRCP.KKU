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
     * Generates a digital stamp PNG with exact timestamp and stores it, returning the stored filename.
     */
    public String generateAndStoreDigitalStamp(String signerName, java.time.LocalDateTime signedAt) {
        byte[] png = generateDigitalStampPng(signerName, signedAt);
        if (png == null) {
            return null;
        }
        var stored = storage.store(png);
        return stored != null ? stored.filename() : null;
    }

    /**
     * Renders a digital signature stamp:
     * - Left: signer's name in Thai font
     * - Right: "Digitally signed by", Name, "Date: YYYY.MM.DD", "HH:mm:ss +07'00'"
     * - Crisp light-gray border
     */
    public byte[] generateDigitalStampPng(String signerName, java.time.LocalDateTime signedAt) {
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

            // Background: white
            g2.setColor(java.awt.Color.WHITE);
            g2.fillRect(0, 0, width, height);

            // Border: light-gray
            g2.setColor(new java.awt.Color(200, 200, 200));
            g2.setStroke(new java.awt.BasicStroke(1.5f));
            g2.drawRect(1, 1, width - 2, height - 2);

            // Time formatting
            java.time.LocalDateTime dt = signedAt != null ? signedAt : java.time.LocalDateTime.now();
            java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd");
            java.time.format.DateTimeFormatter timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
            String dateLine = "Date: " + dt.format(dateFormatter);
            String timeLine = dt.format(timeFormatter) + " +07'00'";

            // Font setup
            java.awt.Font thaiFont = getThaiFont(36, java.awt.Font.BOLD);
            java.awt.Font metaFontBold = getThaiFont(22, java.awt.Font.BOLD);
            java.awt.Font metaFontPlain = getThaiFont(20, java.awt.Font.PLAIN);

            // Left Column: Large Name
            g2.setColor(new java.awt.Color(10, 10, 10));
            String clean = signerName.trim();
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
                int maxW = 210;
                if (w1 > maxW || w2 > maxW) {
                    float scale = (float) maxW / Math.max(w1, w2);
                    g2.setFont(thaiFont.deriveFont(Math.max(36 * scale, 20f)));
                    fm = g2.getFontMetrics();
                }
                g2.drawString(l1, 120 - fm.stringWidth(l1) / 2, 75);
                g2.drawString(l2, 120 - fm.stringWidth(l2) / 2, 125);
            } else {
                g2.setFont(thaiFont);
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int w = fm.stringWidth(clean);
                if (w > 215) {
                    float scale = 215f / w;
                    g2.setFont(thaiFont.deriveFont(Math.max(36 * scale, 20f)));
                    fm = g2.getFontMetrics();
                }
                g2.drawString(clean, 120 - fm.stringWidth(clean) / 2, 100);
            }

            // Right Column: Metadata
            int rightX = 265;
            g2.setColor(new java.awt.Color(10, 10, 10));
            g2.setFont(metaFontBold);
            g2.drawString("Digitally signed by", rightX, 48);

            g2.setColor(new java.awt.Color(35, 35, 35));
            g2.setFont(metaFontPlain);
            g2.drawString(clean, rightX, 82);

            g2.setColor(new java.awt.Color(10, 10, 10));
            g2.setFont(metaFontBold);
            g2.drawString(dateLine, rightX, 122);
            g2.drawString(timeLine, rightX, 156);

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
