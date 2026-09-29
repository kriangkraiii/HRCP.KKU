package com.ecom.academic.service.pdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.UserDigitalCertificate;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.service.UserDigitalCertificateService;
import com.ecom.model.UserDtls;

/**
 * Issuing an incrementally signed document: the office saves the memo number and
 * date, and signs the PDF closed with their own .p12.
 *
 * <p>One transaction for all of it. Saving the values makes the document final in
 * the application; if the closing signature then failed, the application would say
 * "issued" over a PDF anyone could still append to. So the PIN is checked before
 * anything is written, and any failure after that rolls the save back too.
 */
@Service
public class OfficeIssueService {

    private static final Logger log = LoggerFactory.getLogger(OfficeIssueService.class);

    private final UserDigitalCertificateService certificates;
    private final IncrementalSigningService incremental;
    private final SignatureRequestRepository envelopes;

    public OfficeIssueService(UserDigitalCertificateService certificates, IncrementalSigningService incremental,
            SignatureRequestRepository envelopes) {
        this.certificates = certificates;
        this.incremental = incremental;
        this.envelopes = envelopes;
    }

    /**
     * @param saveValues writes the office values to the document rows (joins this transaction)
     * @return why it could not be issued, or null when it was
     */
    @Transactional
    public String issue(Long envelopeId, UserDtls officer, String pin, String ipAddress, Runnable saveValues) {
        UserDigitalCertificate cert = certificates.findActive(officer).orElse(null);
        if (cert == null) {
            return "การออกเลขที่หนังสือต้องลงนามปิดเอกสารด้วย Digital ID (.p12) ของท่าน "
                    + "กรุณาติดตั้งที่หน้า \"ลายเซ็นของฉัน\" ก่อน";
        }
        if (cert.isExpired()) {
            return "ใบรับรอง Digital ID (.p12) ของท่านหมดอายุแล้ว กรุณาติดตั้งไฟล์ใหม่ก่อนออกเลขที่หนังสือ";
        }
        var unlock = certificates.unlock(officer, cert, pin, ipAddress, true);
        if (!unlock.ok()) {
            return unlock.error();
        }
        try {
            CmsSigner signer = certificates.openSigner(cert, pin);
            saveValues.run();
            SignatureRequest envelope = envelopes.findById(envelopeId).orElseThrow();
            incremental.syncLateValues(envelope, officer);
            incremental.lock(envelope, signer, officer.getName(), officer);
            envelopes.save(envelope);
            return null;
        } catch (PdfIncrementService.DoesNotFitException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return "เลขที่หนังสือหรือวันที่ยาวเกินช่องในเอกสาร กรุณาย่อให้สั้นลง";
        } catch (Exception e) {
            log.error("Envelope {}: could not issue and close the signed PDF", envelopeId, e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return "ปิดเอกสารด้วยลายเซ็นดิจิทัลไม่สำเร็จ ยังไม่ได้บันทึกเลขที่หนังสือ กรุณาลองใหม่อีกครั้ง";
        }
    }
}
