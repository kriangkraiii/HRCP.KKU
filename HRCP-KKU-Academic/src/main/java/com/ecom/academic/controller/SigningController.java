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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.service.SignatureVerificationService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.DocumentSnapshotProvider;
import com.ecom.academic.service.SignatureWorkflowService.Result;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.academic.service.SignedDocumentRenderer;
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

    public SigningController(SignatureWorkflowService workflow,
            SignedDocumentRenderer renderer,
            UserSignatureService signatureService,
            UserRepository userRepository,
            SignatureVerificationService verificationService,
            DocumentSnapshotProvider documentLabelResolver,
            HttpServletRequest httpRequest) {
        this.workflow = workflow;
        this.renderer = renderer;
        this.signatureService = signatureService;
        this.userRepository = userRepository;
        this.verificationService = verificationService;
        this.documentLabelResolver = documentLabelResolver;
        this.httpRequest = httpRequest;
    }

    /** Everything waiting on the signed-in person. */
    @GetMapping("/inbox")
    public String inbox(Principal principal, Model model) {
        UserDtls me = currentUser(principal);
        model.addAttribute("pendingSteps", workflow.findInbox(me));
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

        if (step == null || step.getSigner() == null || !step.getSigner().getId().equals(me.getId())) {
            // Not "forbidden": revealing that a step exists tells an outsider
            // something about documents they have nothing to do with.
            redirectAttributes.addFlashAttribute("errorMsg", "ไม่พบรายการลงนามนี้");
            return "redirect:/esign/inbox";
        }

        workflow.recordView(step, me, actorContext());

        model.addAttribute("step", step);
        model.addAttribute("envelope", step.getSignatureRequest());
        model.addAttribute("mySignatures", signatureService.findMine(me));
        model.addAttribute("defaultSignature", signatureService.findDefault(me).orElse(null));
        model.addAttribute("consentText", SignatureStep.CONSENT_TEXT);
        model.addAttribute("canSign",
                step.getStatus() == SignatureStepStatus.ACTIVE
                        && step.getSignatureRequest().getStatus().isOpen());
        return "academic/esign/sign";
    }

    /** Streams the document being signed, for the on-page preview. */
    @GetMapping("/sign/{stepId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long stepId, Principal principal) {
        UserDtls me = currentUser(principal);
        SignatureStep step = workflow.findStep(stepId).orElse(null);

        if (step == null || step.getSigner() == null || !step.getSigner().getId().equals(me.getId())) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] pdf = renderer.renderPdf(step.getSignatureRequest());
            if (pdf != null && pdf.length > 0) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header("Content-Disposition", "inline; filename=\"document.pdf\"")
                        .header("X-Preview-Format", "pdf")
                        .body(pdf);
            }
            // No LibreOffice on this host — hand over the DOCX so the signer can
            // still read what they are being asked to sign.
            byte[] docx = renderer.renderDocx(step.getSignatureRequest());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .header("Content-Disposition", "inline; filename=\"document.docx\"")
                    .header("X-Preview-Format", "docx-fallback")
                    .body(docx);
        } catch (IOException e) {
            log.error("Failed to render document for signing step {}: {}", stepId, e.toString());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/sign/{stepId}")
    public String sign(@PathVariable Long stepId,
            @RequestParam(value = "userSignatureId", required = false) Long userSignatureId,
            @RequestParam(value = "consent", required = false) Boolean consent,
            Principal principal, RedirectAttributes redirectAttributes) {

        Result result = workflow.sign(stepId, currentUser(principal), userSignatureId,
                Boolean.TRUE.equals(consent), actorContext());

        if (!result.ok()) {
            redirectAttributes.addFlashAttribute("errorMsg", result.error());
            return "redirect:/esign/sign/" + stepId;
        }
        redirectAttributes.addFlashAttribute("succMsg", "ลงนามเรียบร้อยแล้ว");
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
            @RequestParam("documentType") int documentType,
            @RequestParam(value = "slotKeys", required = false) List<String> slotKeys,
            @RequestParam(value = "signerUserIds", required = false) List<String> signerUserIds,
            @RequestParam(value = "delegateReasons", required = false) List<String> delegateReasons,
            @RequestParam(value = "dueAt", required = false) String dueAt,
            Principal principal, RedirectAttributes redirectAttributes) {

        UserDtls me = currentUser(principal);

        // Administrators circulate any document; an applicant may circulate the
        // documents of their own request, several of which only they sign.
        boolean isAdmin = "ROLE_ADMIN".equals(me.getRole());
        boolean isOwner = documentLabelResolver.isApplicantOf(module, requestId, me.getId());
        if (!isAdmin && !isOwner) {
            redirectAttributes.addFlashAttribute("errorMsg",
                    "คุณไม่มีสิทธิ์ส่งเอกสารฉบับนี้ไปลงนาม");
            return "redirect:" + module.userLink(requestId);
        }

        String documentLabel = documentLabelResolver.labelFor(module, documentType);
        String frozenJson = documentLabelResolver.currentJsonFor(module, requestId, documentType);

        List<SignerAssignment> assignments = new ArrayList<>();
        if (slotKeys != null && signerUserIds != null) {
            for (int i = 0; i < slotKeys.size() && i < signerUserIds.size(); i++) {
                String raw = signerUserIds.get(i);
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                // Parallel arrays: the browser submits one entry per slot, in
                // slot order, so index i lines up across all three.
                String reason = (delegateReasons != null && i < delegateReasons.size())
                        ? delegateReasons.get(i)
                        : null;
                try {
                    assignments.add(new SignerAssignment(
                            slotKeys.get(i), Integer.valueOf(raw.trim()), reason));
                } catch (NumberFormatException e) {
                    redirectAttributes.addFlashAttribute("errorMsg", "ผู้ลงนามที่เลือกไม่ถูกต้อง");
                    return "redirect:" + documentFormLink(module, requestId, documentType, me);
                }
            }
        }

        Result result = workflow.createEnvelope(module, requestId, documentType, documentLabel,
                frozenJson, assignments, parseDueAt(dueAt), me, actorContext());

        redirectAttributes.addFlashAttribute(result.ok() ? "succMsg" : "errorMsg",
                result.ok()
                        ? "ส่งเอกสารไปลงนามเรียบร้อยแล้ว ระบบได้แจ้งเตือนผู้ลงนามคนแรก"
                        : result.error());
        return "redirect:" + documentFormLink(module, requestId, documentType, me);
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
    @GetMapping("/verify/{code}")
    public String verify(@PathVariable String code, Model model) {
        SignatureRequest envelope = workflow.findByVerificationCodeWithSteps(code).orElse(null);

        if (envelope == null) {
            model.addAttribute("notFoundCode", code);
            return "academic/esign/verify";
        }

        model.addAttribute("report", verificationService.verify(envelope));
        model.addAttribute("auditTrail", workflow.auditTrail(envelope.getId()));
        return "academic/esign/verify";
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
