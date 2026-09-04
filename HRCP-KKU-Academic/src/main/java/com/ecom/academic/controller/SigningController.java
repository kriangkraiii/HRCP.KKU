package com.ecom.academic.controller;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.service.SignatureVerificationService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.DocumentSnapshotProvider;
import com.ecom.academic.service.SignatureWorkflowService.Result;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.academic.service.SignedDocumentRenderer;
import com.ecom.academic.service.UserDigitalCertificateService;
import com.ecom.academic.service.UserSignatureService;
import com.ecom.config.ClientIpUtils;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The signing side of the feature: an inbox, a document to read, and consent.
 *
 * <p>Under {@code /esign/**} for the same reason as
 * {@link UserSignatureController}: a dean is usually {@code ROLE_ADMIN} and an
 * applicant {@code ROLE_USER}, and both sign here. The role-gated prefixes
 * would lock one of them out.
 */
@Controller
@RequestMapping("/esign")
public class SigningController {

    private static final Logger log = LoggerFactory.getLogger(SigningController.class);

    private final SignatureWorkflowService workflow;
    private final SignedDocumentRenderer renderer;
    private final UserSignatureService signatureService;
    private final UserRepository userRepository;
    private final SignatureVerificationService verificationService;
    private final DocumentSnapshotProvider documentLabelResolver;
    private final HttpServletRequest httpRequest;
    private final UserDigitalCertificateService digitalCertificateService;

    public SigningController(SignatureWorkflowService workflow,
            SignedDocumentRenderer renderer,
            UserSignatureService signatureService,
            UserRepository userRepository,
            SignatureVerificationService verificationService,
            DocumentSnapshotProvider documentLabelResolver,
            HttpServletRequest httpRequest,
            UserDigitalCertificateService digitalCertificateService) {
        this.workflow = workflow;
        this.renderer = renderer;
        this.signatureService = signatureService;
        this.userRepository = userRepository;
        this.verificationService = verificationService;
        this.documentLabelResolver = documentLabelResolver;
        this.httpRequest = httpRequest;
        this.digitalCertificateService = digitalCertificateService;
    }

    /** Everything waiting on the signed-in person + sent envelopes tracking. */
    @GetMapping("/inbox")
    public String inbox(Principal principal, Model model) {
        UserDtls me = currentUser(principal);
        boolean isAdmin = me != null && ("ROLE_ADMIN".equals(me.getRole()) || "ROLE_STAFF".equals(me.getRole()));

        List<SignatureStep> pendingSteps = workflow.findInbox(me);
        List<SignatureRequest> sentEnvelopes = workflow.findSentEnvelopes(me);
        List<SignatureRequest> allActiveEnvelopes = isAdmin ? workflow.findAllActiveEnvelopes() : List.of();

        model.addAttribute("pendingSteps", pendingSteps);
        model.addAttribute("sentEnvelopes", sentEnvelopes);
        model.addAttribute("allActiveEnvelopes", allActiveEnvelopes);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("currentUser", me);
        return "academic/esign/inbox";
    }

    /**
     * The signing page: read the document, pick a signature, consent, sign.
     *
     * <p>Opening it is itself recorded — "they saw it before they agreed" is part
     * of what makes the consent meaningful.
     */
    @GetMapping("/sign/{stepId}")
    public String signPage(@PathVariable Long stepId, Principal principal, Model model,
            RedirectAttributes redirectAttributes) {

        UserDtls me = currentUser(principal);
        SignatureStep step = workflow.findStep(stepId).orElse(null);

        boolean isAdmin = me != null && ("ROLE_ADMIN".equals(me.getRole()) || "ROLE_STAFF".equals(me.getRole()));
        boolean isSigner = step != null && step.getSigner() != null && me != null && step.getSigner().getId().equals(me.getId());

        if (step == null || step.getSigner() == null || (!isSigner && !isAdmin)) {
            // Not "forbidden": revealing that a step exists tells an outsider
            // something about documents they have nothing to do with.
            redirectAttributes.addFlashAttribute("errorMsg", "ไม่พบรายการลงนามนี้");
            return "redirect:/esign/inbox";
        }

        workflow.recordView(step, me, actorContext());

        List<SignatureStep> inbox = workflow.findInbox(me);
        int queueTotal = inbox.size();
        int queueIndex = 1;
        for (int i = 0; i < inbox.size(); i++) {
            if (inbox.get(i).getId().equals(stepId)) {
                queueIndex = i + 1;
                break;
            }
        }

        SignatureRequest envelope = step.getSignatureRequest();

        // The deadline is either a gate or a reminder, and the page has to show
        // the right one: offering a signing button the service will refuse sends
        // people through the form only to bounce them at the end.
        boolean deadlineAdvisory = workflow.deadlineIsAdvisory(step);

        var myCert = digitalCertificateService.findActive(me).orElse(null);
        boolean hasValidCert = myCert != null && !myCert.isExpired();

        boolean canSign = step.getStatus() == SignatureStepStatus.ACTIVE
                && envelope.getStatus().isOpen()
                && (deadlineAdvisory || !envelope.isOverdue());

        model.addAttribute("step", step);
        model.addAttribute("envelope", envelope);
        model.addAttribute("mySignatures", signatureService.findMine(me));
        model.addAttribute("defaultSignature", signatureService.findDefault(me).orElse(null));
        model.addAttribute("myCert", myCert);
        model.addAttribute("hasValidCert", hasValidCert);
        model.addAttribute("consentText", SignatureStep.CONSENT_TEXT);
        model.addAttribute("queueTotal", queueTotal);
        model.addAttribute("queueIndex", queueIndex);
        model.addAttribute("deadlineAdvisory", deadlineAdvisory);
        model.addAttribute("canSign", canSign);
        return "academic/esign/sign";
    }

    /** Streams the document being signed, for the on-page preview. */
    @GetMapping("/sign/{stepId}/preview")
    public ResponseEntity<byte[]> preview(
            @PathVariable Long stepId,
            @RequestParam(value = "userSignatureId", required = false) Long userSignatureId,
            Principal principal) {
        UserDtls me = currentUser(principal);
        SignatureStep step = workflow.findStep(stepId).orElse(null);

        boolean isAdmin = me != null && ("ROLE_ADMIN".equals(me.getRole()) || "ROLE_STAFF".equals(me.getRole()));
        boolean isSigner = step != null && step.getSigner() != null && me != null && step.getSigner().getId().equals(me.getId());

        if (step == null || step.getSigner() == null || (!isSigner && !isAdmin)) {
            return ResponseEntity.notFound().build();
        }

        UserSignature previewSig = null;
        if (userSignatureId != null) {
            previewSig = signatureService.findMine(userSignatureId, me).orElse(null);
            if (previewSig == null && isAdmin) {
                previewSig = signatureService.findById(userSignatureId).orElse(null);
            }
        }
        if (previewSig == null && step.getStatus() == SignatureStepStatus.ACTIVE) {
            UserDtls targetSigner = (isSigner || !isAdmin) ? me : step.getSigner();
            if (targetSigner != null) {
                previewSig = signatureService.findDefault(targetSigner).orElse(null);
                if (previewSig == null) {
                    List<UserSignature> mine = signatureService.findMine(targetSigner);
                    if (!mine.isEmpty()) {
                        previewSig = mine.get(0);
                    }
                }
            }
        }

        try {
            byte[] pdf = renderer.renderPdf(step.getSignatureRequest(), step, previewSig);
            if (pdf != null && pdf.length > 0) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header("Content-Disposition", "inline; filename=\"document.pdf\"")
                        .header("X-Preview-Format", "pdf")
                        .body(pdf);
            }
            // No LibreOffice on this host — hand over the DOCX so the signer can
            // still read what they are being asked to sign.
            byte[] docx = renderer.renderDocx(step.getSignatureRequest(), step, previewSig);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .header("Content-Disposition", "inline; filename=\"document.docx\"")
                    .header("X-Preview-Format", "docx-fallback")
                    .body(docx);
        } catch (Exception e) {
            log.error("Failed to render document for signing step {}: {}", stepId, e.toString(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/sign/{stepId}")
    public String sign(@PathVariable Long stepId,
            @RequestParam(value = "userSignatureId", required = false) Long userSignatureId,
            @RequestParam(value = "consent", required = false) Boolean consent,
            @RequestParam(value = "digitalCertPin", required = false) String digitalCertPin,
            Principal principal, RedirectAttributes redirectAttributes) {

        UserDtls me = currentUser(principal);
        var certOpt = digitalCertificateService.findActive(me);
        if (certOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg",
                    "คุณยังไม่ได้ติดตั้งใบรับรอง Digital ID (.p12) ของมหาวิทยาลัยขอนแก่น กรุณาติดตั้งที่หน้า \"ลายเซ็นของฉัน\" ก่อนจึงจะได้รับอนุญาตให้ลงนาม");
            return "redirect:/esign/sign/" + stepId;
        }
        if (certOpt.get().isExpired()) {
            redirectAttributes.addFlashAttribute("errorMsg",
                    "ใบรับรอง Digital ID (.p12) ของคุณหมดอายุแล้ว ไม่สามารถใช้ลงนามเอกสารได้ กรุณาดาวน์โหลดไฟล์ใหม่จาก https://i.kku.ac.th และติดตั้งที่หน้า \"ลายเซ็นของฉัน\"");
            return "redirect:/esign/sign/" + stepId;
        }

        Result result = workflow.sign(stepId, me, userSignatureId,
                Boolean.TRUE.equals(consent), actorContext(), digitalCertPin);

        if (!result.ok()) {
            redirectAttributes.addFlashAttribute("errorMsg", result.error());
            return "redirect:/esign/sign/" + stepId;
        }

        // Check if there is another document in the queue for continuous flow
        java.util.Optional<SignatureStep> nextStep = workflow.findNextPendingStep(me, stepId);
        if (nextStep.isPresent()) {
            redirectAttributes.addFlashAttribute("succMsg",
                    "ลงนามเอกสารฉบับนี้เรียบร้อยแล้ว — นำคุณไปยังเอกสารถัดไปในคิวทันที");
            return "redirect:/esign/sign/" + nextStep.get().getId();
        }

        redirectAttributes.addFlashAttribute("succMsg", "ลงนามครบทุกเอกสารในคิวเรียบร้อยแล้ว");

        // Back to the request they came from, so they can see where it now
        // stands and carry on with it — the inbox is empty by definition at
        // this point and says nothing useful.
        SignatureRequest envelope = result.request();
        if (envelope != null) {
            return "redirect:" + envelope.getModule().userLink(envelope.getRequestId());
        }
        return "redirect:/esign/inbox";
    }

    @PostMapping("/step/{stepId}/request-extension")
    public String requestExtension(@PathVariable Long stepId,
            @RequestParam(value = "reason", required = false) String reason,
            Principal principal, RedirectAttributes redirectAttributes) {

        Result result = workflow.requestExtension(stepId, reason, currentUser(principal), actorContext());
        if (!result.ok()) {
            redirectAttributes.addFlashAttribute("errorMsg", result.error());
        } else {
            redirectAttributes.addFlashAttribute("succMsg", "ส่งคำขอขยายเวลาลงนามไปยังผู้ส่งเอกสารเรียบร้อยแล้ว");
        }
        return "redirect:/esign/sign/" + stepId;
    }

    /**
     * Moves a round's deadline, reopening it if the clock already closed it.
     *
     * <p>Guarded like {@link #cancelEnvelope}: both change when other people are
     * expected to act, so both are limited to the people who own the round.
     */
    @PostMapping("/envelope/{envelopeId}/extend-due")
    public String extendDueDate(@PathVariable Long envelopeId,
            @RequestParam("dueAt") String dueAtStr,
            Principal principal, RedirectAttributes redirectAttributes) {

        UserDtls me = currentUser(principal);
        SignatureRequest envelope = workflow.findEnvelope(envelopeId).orElse(null);
        if (envelope == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "ไม่พบรายการเวียนลงนามนี้");
            return "redirect:/esign/inbox";
        }
        if (!mayManage(envelope, me)) {
            redirectAttributes.addFlashAttribute("errorMsg", "คุณไม่มีสิทธิ์แก้กำหนดเวลาลงนามนี้");
            return "redirect:/esign/inbox";
        }

        LocalDateTime newDueAt = null;
        if (dueAtStr != null && !dueAtStr.isBlank()) {
            try {
                newDueAt = LocalDateTime.parse(dueAtStr);
            } catch (DateTimeParseException e) {
                redirectAttributes.addFlashAttribute("errorMsg", "รูปแบบวันเวลาไม่ถูกต้อง");
                return "redirect:/esign/inbox";
            }
        }

        Result result = workflow.extendDueDate(envelopeId, newDueAt, me, actorContext());
        if (!result.ok()) {
            redirectAttributes.addFlashAttribute("errorMsg", result.error());
        } else {
            redirectAttributes.addFlashAttribute("succMsg", "ขยายกำหนดเวลาลงนามเรียบร้อยแล้ว");
        }
        return "redirect:/esign/inbox";
    }

    @PostMapping("/sign/{stepId}/decline")
    public String decline(@PathVariable Long stepId,
            @RequestParam(value = "reason", required = false) String reason,
            Principal principal, RedirectAttributes redirectAttributes) {

        Result result = workflow.decline(stepId, currentUser(principal), reason, actorContext());

        if (!result.ok()) {
            redirectAttributes.addFlashAttribute("errorMsg", result.error());
            return "redirect:/esign/sign/" + stepId;
        }
        redirectAttributes.addFlashAttribute("succMsg",
                "บันทึกการปฏิเสธการลงนามแล้ว ระบบได้แจ้งผู้ส่งเอกสารเรียบร้อย");
        return "redirect:/esign/inbox";
    }

    /**
     * Freezes a document and starts a signing round.
     *
     * <p>Restricted to administrators: circulating a document commits other
     * people to act, and today only staff prepare documents for signature.
     *
     * @param slotKeys parallel arrays from the form — {@code slotKeys[i]} is
     *                 filled by {@code signerUserIds[i]}; a blank id means that
     *                 position is not part of this round
     */
    @PostMapping("/envelope/create")
    public String createEnvelope(
            @RequestParam("module") SignatureModule module,
            @RequestParam("requestId") Long requestId,
            @RequestParam(value = "documentType", defaultValue = "0") int documentType,
            @RequestParam(value = "slotKeys", required = false) List<String> slotKeys,
            @RequestParam(value = "signerUserIds", required = false) List<String> signerUserIds,
            @RequestParam(value = "dueAt", required = false) String dueAt,
            Principal principal, RedirectAttributes redirectAttributes) {

        UserDtls me = currentUser(principal);

        // Administrators and staff circulate any document; an applicant may
        // circulate the documents of their own request, several of which only
        // they sign. Staff are included because the panel offers them the send
        // button — refusing here would bounce them off an access rule instead.
        boolean isAdmin = isAdminOrStaff(me);
        boolean isOwner = documentLabelResolver.isApplicantOf(module, requestId, me.getId());
        if (!isAdmin && !isOwner) {
            redirectAttributes.addFlashAttribute("errorMsg",
                    "คุณไม่มีสิทธิ์ส่งเอกสารฉบับนี้ไปลงนาม");
            return "redirect:" + module.userLink(requestId);
        }

        String documentLabel = documentLabelResolver.labelFor(module, documentType);
        String frozenJson = documentLabelResolver.currentJsonFor(module, requestId, documentType);

        List<SignerAssignment> assignments = parseAssignments(slotKeys, signerUserIds);
        if (assignments == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "ผู้ลงนามที่เลือกไม่ถูกต้อง");
            return "redirect:" + documentFormLink(module, requestId, documentType, me);
        }

        Result result = workflow.createEnvelope(module, requestId, documentType, documentLabel,
                frozenJson, assignments, parseDueAt(dueAt), me, actorContext());

        flashOutcome(result, redirectAttributes);

        // Their turn already? Take them straight to it. Making someone find the
        // document again in another list is the kind of detour that gets a
        // signature put off and forgotten.
        String signNow = signLinkFor(result, me);
        if (signNow != null) {
            return "redirect:" + signNow;
        }
        return "redirect:" + documentFormLink(module, requestId, documentType, me);
    }

    /**
     * Where this person should go to sign, if the round is waiting on them.
     *
     * @return the signing link, or null when it is somebody else's turn
     */
    private String signLinkFor(Result result, UserDtls me) {
        if (!result.ok() || result.request() == null || me == null) {
            return null;
        }
        return result.request().getSteps().stream()
                .filter(step -> step.getStatus() == SignatureStepStatus.ACTIVE)
                .filter(step -> step.getSigner() != null && me.getId().equals(step.getSigner().getId()))
                .findFirst()
                .map(step -> "/esign/sign/" + step.getId())
                .orElse(null);
    }

    /**
     * Adds the next signers to a round already under way.
     *
     * <p>The counterpart to the review gate: once the applicant has signed and
     * submitted, staff check the document and send it on to the head of
     * department and the dean from here.
     */
    @PostMapping("/envelope/{envelopeId}/forward")
    public String forwardEnvelope(@PathVariable Long envelopeId,
            @RequestParam(value = "slotKeys", required = false) List<String> slotKeys,
            @RequestParam(value = "signerUserIds", required = false) List<String> signerUserIds,
            @RequestParam(value = "dueAt", required = false) String dueAt,
            Principal principal, RedirectAttributes redirectAttributes) {

        UserDtls me = currentUser(principal);
        SignatureRequest envelope = workflow.findEnvelope(envelopeId).orElse(null);
        if (envelope == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "ไม่พบคำขอลงนาม");
            return "redirect:/esign/inbox";
        }
        // Forwarding commits other people to act, so it is staff-only — unlike
        // cancelling, which the applicant may also do to their own request.
        if (!isAdminOrStaff(me)) {
            redirectAttributes.addFlashAttribute("errorMsg", "คุณไม่มีสิทธิ์ส่งเวียนลงนามต่อ");
            return "redirect:/esign/inbox";
        }

        String back = documentFormLink(envelope.getModule(), envelope.getRequestId(),
                envelope.getDocumentType(), me);

        List<SignerAssignment> assignments = parseAssignments(slotKeys, signerUserIds);
        if (assignments == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "ผู้ลงนามที่เลือกไม่ถูกต้อง");
            return "redirect:" + back;
        }

        Result result = workflow.forwardToNextSigners(
                envelopeId, assignments, parseDueAt(dueAt), me, actorContext());
        flashOutcome(result, redirectAttributes);
        return "redirect:" + back;
    }

    /**
     * Staff have checked this document and are releasing it to the next signers.
     *
     * <p>Separate from adding signers: the usual case is that the signers were
     * chosen when the document was sent, and all staff need to do is say "I have
     * read this, let it go".
     */
    @PostMapping("/envelope/{envelopeId}/start")
    public String startCirculation(@PathVariable Long envelopeId,
            Principal principal, RedirectAttributes redirectAttributes) {

        UserDtls me = currentUser(principal);
        SignatureRequest envelope = workflow.findEnvelope(envelopeId).orElse(null);
        if (envelope == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "ไม่พบคำขอลงนาม");
            return "redirect:/esign/inbox";
        }
        if (!isAdminOrStaff(me)) {
            redirectAttributes.addFlashAttribute("errorMsg", "เฉพาะเจ้าหน้าที่เท่านั้นที่เริ่มเวียนลงนามได้");
            return "redirect:/esign/inbox";
        }

        Result result = workflow.startCirculation(envelopeId, me, actorContext());
        redirectAttributes.addFlashAttribute(result.ok() ? "succMsg" : "errorMsg",
                result.ok()
                        ? "เริ่มเวียนลงนามแล้ว ระบบได้แจ้งเตือนผู้ลงนามลำดับถัดไป"
                        : result.error());
        return "redirect:" + documentFormLink(envelope.getModule(), envelope.getRequestId(),
                envelope.getDocumentType(), me);
    }

    /** Whether this person prepares and circulates documents for other people. */
    private boolean isAdminOrStaff(UserDtls user) {
        return user != null
                && ("ROLE_ADMIN".equals(user.getRole()) || "ROLE_STAFF".equals(user.getRole()));
    }

    /**
     * Pairs up the form's parallel arrays into assignments.
     *
     * <p>The browser submits one {@code slotKeys} entry and one
     * {@code signerUserIds} entry per position, in slot order, so index i lines
     * up across both. A blank id means that position is not part of this round.
     *
     * @return the assignments, or null when an id was not a number at all
     */
    private List<SignerAssignment> parseAssignments(List<String> slotKeys, List<String> signerUserIds) {
        List<SignerAssignment> assignments = new ArrayList<>();
        if (slotKeys == null || signerUserIds == null) {
            return assignments;
        }
        for (int i = 0; i < slotKeys.size() && i < signerUserIds.size(); i++) {
            String raw = signerUserIds.get(i);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                assignments.add(new SignerAssignment(slotKeys.get(i), Integer.valueOf(raw.trim())));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return assignments;
    }

    /**
     * Reports what actually happened, rather than assuming it went out.
     *
     * <p>A round whose next step is being held back — because the request is
     * still a draft — is saved successfully but notifies nobody. Saying it was
     * sent would be the false reassurance that makes the button look broken.
     */
    private void flashOutcome(Result result, RedirectAttributes redirectAttributes) {
        if (!result.ok()) {
            redirectAttributes.addFlashAttribute("errorMsg", result.error());
            return;
        }
        SignatureRequest saved = result.request();
        if (saved != null && saved.activeStep().isEmpty()) {
            redirectAttributes.addFlashAttribute("warnMsg",
                    "บันทึกผู้ลงนามเรียบร้อยแล้ว แต่ยังไม่ได้ส่งให้ใครลงนาม "
                            + "ระบบจะเริ่มเวียนลงนามให้อัตโนมัติเมื่อผู้ยื่นกดส่งคำร้อง");
            return;
        }
        redirectAttributes.addFlashAttribute("succMsg",
                "ส่งเอกสารไปลงนามเรียบร้อยแล้ว ระบบได้แจ้งเตือนผู้ลงนามคนถัดไป");
    }

    /**
     * Withdraws a circulating document so its form can be corrected.
     *
     * <p>Cancelling voids signatures other people have already given, so it is
     * restricted to the person who started the round, an administrator, or the
     * applicant whose request it belongs to — not simply anyone holding a link.
     */
    @PostMapping("/envelope/{envelopeId}/cancel")
    public String cancelEnvelope(@PathVariable Long envelopeId,
            @RequestParam(value = "reason", required = false) String reason,
            Principal principal, RedirectAttributes redirectAttributes) {

        UserDtls me = currentUser(principal);
        SignatureRequest envelope = workflow.findEnvelope(envelopeId).orElse(null);
        if (envelope == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "ไม่พบคำขอลงนาม");
            return "redirect:/esign/inbox";
        }
        if (!mayManage(envelope, me)) {
            redirectAttributes.addFlashAttribute("errorMsg", "คุณไม่มีสิทธิ์ยกเลิกการเวียนลงนามนี้");
            return "redirect:/esign/inbox";
        }

        Result result = workflow.cancel(envelopeId, me, reason, actorContext());
        if (!result.ok()) {
            redirectAttributes.addFlashAttribute("errorMsg", result.error());
            return "redirect:/esign/inbox";
        }

        redirectAttributes.addFlashAttribute("succMsg", "ยกเลิกการเวียนลงนามแล้ว เอกสารกลับมาแก้ไขได้");
        return "redirect:" + documentFormLink(envelope.getModule(), envelope.getRequestId(),
                envelope.getDocumentType(), me);
    }

    /** Who may withdraw a round: its initiator, an administrator, or the applicant. */
    private boolean mayManage(SignatureRequest envelope, UserDtls user) {
        if ("ROLE_ADMIN".equals(user.getRole())) {
            return true;
        }
        if (envelope.getInitiatedBy() != null
                && user.getId().equals(envelope.getInitiatedBy().getId())) {
            return true;
        }
        return documentLabelResolver.isApplicantOf(
                envelope.getModule(), envelope.getRequestId(), user.getId());
    }

    /**
     * Back to the document form the round belongs to.
     *
     * <p>Routed by role: sending an applicant to an {@code /admin/**} URL would
     * bounce them off the access rules and lose the flash message with it.
     */
    private String documentFormLink(SignatureModule module, Long requestId, int documentType, UserDtls user) {
        boolean admin = "ROLE_ADMIN".equals(user.getRole());
        if (module == SignatureModule.ACADEMIC) {
            return (admin ? "/admin/academic/request/" : "/user/academic/request/")
                    + requestId + "/document/" + documentType;
        }
        return (admin ? "/admin/position/request/" : "/user/position/request/")
                + requestId + "/document/" + documentType;
    }

    /** Accepts the browser's {@code datetime-local} value; absent means no deadline. */
    private LocalDateTime parseDueAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            log.warn("Ignoring unparseable signing deadline '{}'", raw);
            return null;
        }
    }

    /**
     * Checks a document by its verification code.
     *
     * <p>Reached from the QR printed on a fully-signed document. Sits under
     * {@code /esign/**}, so it requires a login: the page names who signed and
     * when, which is personal data — an anonymous page would publish it to
     * anyone who photographed a document.
     */
    @GetMapping({"/verify", "/verify/", "/verify/{code}"})
    public String verify(@PathVariable(required = false) String code,
                         @RequestParam(value = "code", required = false) String queryCode,
                         Model model) {
        String targetCode = (code != null && !code.isBlank()) ? code.trim().toUpperCase()
                : (queryCode != null && !queryCode.isBlank() ? queryCode.trim().toUpperCase() : null);

        if (targetCode == null || targetCode.isBlank()) {
            return "academic/esign/verify";
        }

        SignatureRequest envelope = workflow.findByVerificationCodeWithSteps(targetCode).orElse(null);

        if (envelope == null) {
            model.addAttribute("notFoundCode", targetCode);
            return "academic/esign/verify";
        }

        model.addAttribute("report", verificationService.verify(envelope));
        model.addAttribute("auditTrail", workflow.auditTrail(envelope.getId()));
        return "academic/esign/verify";
    }

    /** Quick API to check if a verification code exists before redirecting */
    @GetMapping(value = "/verify/check/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> checkCodeExists(@PathVariable String code) {
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("exists", false, "message", "กรุณาระบุรหัส"));
        }
        String cleanCode = code.trim().toUpperCase();
        boolean exists = workflow.findByVerificationCode(cleanCode).isPresent();
        return ResponseEntity.ok(java.util.Map.of("exists", exists, "code", cleanCode));
    }

    /** IP and user agent, recorded with every signature as part of the evidence. */
    private ActorContext actorContext() {
        return new ActorContext(
                ClientIpUtils.resolveClientIp(httpRequest),
                httpRequest.getHeader("User-Agent"));
    }

    private UserDtls currentUser(Principal principal) {
        return userRepository.findByEmail(principal.getName());
    }
}
