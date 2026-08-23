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
}
