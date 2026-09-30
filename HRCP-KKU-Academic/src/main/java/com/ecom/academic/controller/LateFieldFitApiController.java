package com.ecom.academic.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.service.DocumentFieldOwnership;
import com.ecom.academic.service.pdf.IncrementalSigningService;
import com.ecom.academic.service.pdf.LateFieldFit;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * ตรวจระหว่างพิมพ์ว่าค่าที่เจ้าหน้าที่กรอก (หมายเหตุ เลขที่หนังสือ ฯลฯ) ใส่ลงช่องในไฟล์ลงนามได้ไหม
 *
 * <p>ใช้ขนาดช่องจากไฟล์ของซองที่ถือเอกสารฉบับนี้อยู่ — ยังไม่มีซองก็ยังไม่มีช่องให้เทียบ
 * (ตอนส่งเวียน {@code SignatureWorkflowService} ตรวจซ้ำอีกชั้นเสมอ) ดู late_field_fit.js
 */
@RestController
@RequestMapping("/api")
public class LateFieldFitApiController {

    private static final Set<String> STAFF = Set.of("ROLE_ADMIN", "ROLE_STAFF");

    private final IncrementalSigningService incrementalSigning;
    private final UserRepository userRepository;

    public LateFieldFitApiController(IncrementalSigningService incrementalSigning, UserRepository userRepository) {
        this.incrementalSigning = incrementalSigning;
        this.userRepository = userRepository;
    }

    /** @return {@code {"problems":[{field,label,value}]}} — ว่างเมื่อใส่ได้ทุกช่อง หรือยังไม่มีซอง */
    @PostMapping("/late-field-fit/{module}/{requestId}/{docType}")
    public ResponseEntity<?> check(@PathVariable String module, @PathVariable Long requestId,
            @PathVariable int docType, @RequestBody Map<String, String> values, Principal principal) {
        UserDtls user = principal == null ? null : userRepository.findByEmail(principal.getName());
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        if (!STAFF.contains(user.getRole())) {
            return ResponseEntity.status(403).build();
        }
        SignatureModule m;
        try {
            m = SignatureModule.valueOf(module.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        Set<String> late = DocumentFieldOwnership.lateFields(m, docType);
        Map<String, String> staffValues = new java.util.LinkedHashMap<>(values);
        staffValues.keySet().retainAll(late);
        List<LateFieldFit.Problem> problems = incrementalSigning.envelopeFor(m, requestId, docType)
                .map(envelope -> LateFieldFit.check(envelope.getFieldLayoutJson(), staffValues))
                .orElse(List.of());
        return ResponseEntity.ok(Map.of("problems", problems));
    }
}
