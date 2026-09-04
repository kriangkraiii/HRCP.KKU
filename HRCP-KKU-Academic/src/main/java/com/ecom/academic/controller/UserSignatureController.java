package com.ecom.academic.controller;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ecom.academic.model.SignatureKind;
import com.ecom.academic.service.UserDigitalCertificateService;
import com.ecom.academic.service.UserSignatureService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * The "ลายเซ็นของฉัน" page: everyone's personal signature library.
 *
 * <p>Mapped under {@code /esign/**} rather than {@code /user/**} or
 * {@code /admin/**} on purpose. Those two prefixes are role-gated in
 * {@code SecurityConfig} — {@code hasRole("USER")} and {@code hasRole("ADMIN")}
 * respectively — but a dean who must sign documents is typically an admin while
 * an applicant is a user, and both need this exact page. {@code /esign/**} falls
 * through to {@code anyRequest().authenticated()}, so it is protected without
 * being restricted to one role.
 */
@Controller
@RequestMapping("/esign")
public class UserSignatureController {

    private final UserSignatureService signatureService;
    private final UserRepository userRepository;
    private final UserDigitalCertificateService digitalCertificateService;

    public UserSignatureController(
            UserSignatureService signatureService,
            UserRepository userRepository,
            UserDigitalCertificateService digitalCertificateService) {
        this.signatureService = signatureService;
        this.userRepository = userRepository;
        this.digitalCertificateService = digitalCertificateService;
    }

    @GetMapping("/my-signatures")
    public String mySignatures(Principal principal, Model model,
            @RequestParam(value = "edit", required = false) Long editId) {
        UserDtls me = currentUser(principal);
        model.addAttribute("signatures", signatureService.findMine(me));
        model.addAttribute("maxSignatures", UserSignatureService.MAX_PER_USER);
        model.addAttribute("signatureKinds", SignatureKind.values());
        model.addAttribute("activeCertificate", digitalCertificateService.findActive(me).orElse(null));
        model.addAttribute("myCertificates", digitalCertificateService.findMine(me));

        if (editId != null) {
            signatureService.findMine(editId, me)
                    .ifPresent(sig -> model.addAttribute("editing", sig));
        }
        return "academic/esign/my_signatures";
    }

    @PostMapping("/my-signatures")
    public String save(Principal principal,
            @RequestParam(value = "id", required = false) Long id,
            @RequestParam(value = "imageData", required = false) String imageData,
            @RequestParam(value = "kind", required = false) String kind,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "typedText", required = false) String typedText,
            @RequestParam(value = "typedFont", required = false) String typedFont,
            @RequestParam(value = "makeDefault", required = false) Boolean makeDefault,
            RedirectAttributes redirectAttributes) {

        UserDtls me = currentUser(principal);
        SignatureKind parsedKind = SignatureKind.fromValue(kind);
        boolean asDefault = Boolean.TRUE.equals(makeDefault);

        UserSignatureService.SaveResult result = (id == null)
                ? signatureService.create(me, imageData, parsedKind, name, typedText, typedFont, asDefault)
                : signatureService.update(id, me, imageData, parsedKind, name, typedText, typedFont, asDefault);

        if (!result.ok()) {
            redirectAttributes.addFlashAttribute("errorMsg", result.error());
            return "redirect:/esign/my-signatures" + (id == null ? "" : "?edit=" + id);
        }

        redirectAttributes.addFlashAttribute("succMsg",
                id == null ? "บันทึกลายเซ็นเรียบร้อยแล้ว" : "แก้ไขลายเซ็นเรียบร้อยแล้ว");
        return "redirect:/esign/my-signatures";
    }

    @PostMapping("/my-signatures/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        boolean deleted = signatureService.delete(id, currentUser(principal));
        redirectAttributes.addFlashAttribute(deleted ? "succMsg" : "errorMsg",
                deleted ? "ลบลายเซ็นเรียบร้อยแล้ว" : "ไม่พบลายเซ็นที่ต้องการลบ");
        return "redirect:/esign/my-signatures";
    }

    @PostMapping("/my-signatures/{id}/default")
    public String makeDefault(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        boolean updated = signatureService.makeDefault(id, currentUser(principal));
        redirectAttributes.addFlashAttribute(updated ? "succMsg" : "errorMsg",
                updated ? "ตั้งเป็นลายเซ็นหลักเรียบร้อยแล้ว" : "ไม่พบลายเซ็นที่เลือก");
        return "redirect:/esign/my-signatures";
    }

    /**
     * Serves a signature image to its owner only.
     *
     * <p>Deliberately not a static resource. {@code WebConfig} maps
     * {@code /uploads/**} to a handler that serves any file under the upload root
     * to any authenticated user; a signature is personal data, so it is read back
     * through the repository with the owner in the query and a 404 — not a 403 —
     * for anything else, which avoids confirming that an id exists.
     */
    @GetMapping("/signature/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id, Principal principal) {
        byte[] png = signatureService.readImage(id, currentUser(principal));
        if (png == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                // Private: this must never be held in a shared or proxy cache.
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(png);
    }

    @PostMapping("/my-certificates/verify")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyCertificate(
            Principal principal,
            @RequestParam(value = "certFile", required = false) MultipartFile certFile,
            @RequestParam(value = "password", required = false) String password) {
        if (certFile == null || certFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "กรุณาเลือกไฟล์ .p12 ของท่าน"));
        }
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "กรุณาระบุ Digital ID Password"));
        }
        try {
            UserDigitalCertificateService.VerifyResult res = digitalCertificateService.verifyP12(certFile.getBytes(), password);
            if (res.ok()) {
                return ResponseEntity.ok(Map.of(
                        "ok", true,
                        "commonName", res.commonName() != null ? res.commonName() : "-",
                        "issuer", res.issuer() != null ? res.issuer() : "-",
                        "validTo", res.validTo() != null ? res.validTo() : "-"
                ));
            } else {
                return ResponseEntity.ok(Map.of("ok", false, "error", res.error()));
            }
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("ok", false, "error", "ไม่สามารถอ่านไฟล์ .p12: " + e.getMessage()));
        }
    }

    @PostMapping("/my-certificates/verify-current")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyCurrentCertificatePassword(
            Principal principal,
            @RequestParam(value = "password", required = false) String password) {
        UserDtls me = currentUser(principal);
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "กรุณาระบุ Digital ID Password"));
        }
        try {
            UserDigitalCertificateService.VerifyResult res = digitalCertificateService.verifyActiveCertificatePassword(me, password);
            if (res.ok()) {
                return ResponseEntity.ok(Map.of(
                        "ok", true,
                        "commonName", res.commonName() != null ? res.commonName() : "-",
                        "issuer", res.issuer() != null ? res.issuer() : "-",
                        "validTo", res.validTo() != null ? res.validTo() : "-"
                ));
            } else {
                return ResponseEntity.ok(Map.of("ok", false, "error", res.error()));
            }
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("ok", false, "error", "เกิดข้อผิดพลาดในการตรวจสอบ: " + e.getMessage()));
        }
    }

    @PostMapping("/my-certificates/update-password")
    public String updateCertificatePassword(
            Principal principal,
            @RequestParam("newPassword") String newPassword,
            RedirectAttributes redirectAttributes) {
        UserDtls me = currentUser(principal);
        UserDigitalCertificateService.SaveResult res = digitalCertificateService.updatePassword(me, newPassword);
        if (res.ok()) {
            redirectAttributes.addFlashAttribute("succMsg", "อัปเดต Digital ID Password สำเร็จแล้ว");
        } else {
            redirectAttributes.addFlashAttribute("errorMsg", res.error());
        }
        return "redirect:/esign/my-signatures";
    }

    @PostMapping("/my-certificates/upload")
    public String uploadCertificate(
            Principal principal,
            @RequestParam("certFile") MultipartFile certFile,
            @RequestParam("pin") String pin,
            RedirectAttributes redirectAttributes) {
        UserDtls me = currentUser(principal);
        if (certFile == null || certFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "กรุณาเลือกไฟล์ .p12 ของท่าน");
            return "redirect:/esign/my-signatures";
        }
        try {
            UserDigitalCertificateService.SaveResult result = digitalCertificateService.registerCertificate(
                    me, certFile.getBytes(), certFile.getOriginalFilename(), pin, true);
            if (result.ok()) {
                redirectAttributes.addFlashAttribute("succMsg", "ติดตั้งและยืนยัน Digital ID (.p12) เรียบร้อยแล้ว (เปิดใช้ One-Click Sign อัตโนมัติ)");
            } else {
                redirectAttributes.addFlashAttribute("errorMsg", result.error());
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "เกิดข้อผิดพลาดในการอัปโหลด: " + e.getMessage());
        }
        return "redirect:/esign/my-signatures";
    }

    @PostMapping("/my-certificates/{id}/deactivate")
    public String deactivateCertificate(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        UserDtls me = currentUser(principal);
        digitalCertificateService.deactivate(id, me);
        redirectAttributes.addFlashAttribute("succMsg", "ยกเลิกการใช้งานใบรับรองดิจิทัลแล้ว");
        return "redirect:/esign/my-signatures";
    }

    /** The signed-in account. Never null: every route here requires authentication. */
    private UserDtls currentUser(Principal principal) {
        return userRepository.findByEmail(principal.getName());
    }
}
