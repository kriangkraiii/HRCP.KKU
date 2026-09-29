package com.ecom.academic.service.pdf;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignedPdfRevision;
import com.ecom.academic.repository.SignedPdfRevisionRepository;

/**
 * The revision chain of an envelope's signed PDF.
 *
 * <p>Only what each revision appended is stored; the file at revision N is the
 * deltas 0..N in order. Appending checks that the new file starts with the
 * current one byte for byte, and the unique (envelope, revision) key makes two
 * concurrent appends fail rather than fork the chain.
 */
@Service
public class SignedPdfRevisionService {

    private final SignedPdfRevisionRepository repository;

    public SignedPdfRevisionService(SignedPdfRevisionRepository repository) {
        this.repository = repository;
    }

    /** The current file, or null for an envelope without a revision chain. */
    @Transactional(readOnly = true)
    public byte[] latest(Long envelopeId) {
        List<SignedPdfRevision> chain = repository.findBySignatureRequestIdOrderByRevisionNoAsc(envelopeId);
        if (chain.isEmpty()) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (SignedPdfRevision r : chain) {
            out.writeBytes(r.getDelta());
        }
        byte[] pdf = out.toByteArray();
        SignedPdfRevision last = chain.get(chain.size() - 1);
        if (pdf.length != last.getTotalLength() || !sha256(pdf).equals(last.getSha256())) {
            throw new IllegalStateException("Signed PDF of envelope " + envelopeId + " does not match its stored hash");
        }
        return pdf;
    }

    @Transactional(readOnly = true)
    public List<SignedPdfRevision> chain(Long envelopeId) {
        return repository.findBySignatureRequestIdOrderByRevisionNoAsc(envelopeId);
    }

    /**
     * Records {@code pdf} as the envelope's next revision.
     *
     * @param expected the file it was built on (null for revision 0)
     * @return the new revision number
     */
    @Transactional
    public int append(SignatureRequest envelope, byte[] expected, byte[] pdf, SignedPdfRevision.Kind kind,
            Long stepId, Integer actorUserId, String certFingerprint) {
        int next;
        byte[] delta;
        if (expected == null) {
            if (envelope.getCurrentRevisionNo() != null) {
                throw new IllegalStateException("Envelope " + envelope.getId() + " already has a base PDF");
            }
            next = 0;
            delta = pdf;
        } else {
            byte[] current = latest(envelope.getId());
            if (current == null || !Arrays.equals(current, expected)) {
                throw new IllegalStateException("The signed PDF changed while this revision was being made");
            }
            if (pdf.length <= current.length || !Arrays.equals(current, 0, current.length, pdf, 0, current.length)) {
                throw new IllegalStateException("A revision may only append to the signed PDF");
            }
            next = envelope.getCurrentRevisionNo() + 1;
            delta = Arrays.copyOfRange(pdf, current.length, pdf.length);
        }
        repository.saveAndFlush(new SignedPdfRevision(envelope.getId(), next, kind, stepId, actorUserId,
                delta, pdf.length, sha256(pdf), certFingerprint));
        envelope.setCurrentRevisionNo(next);
        return next;
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
