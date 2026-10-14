package com.ecom.sso;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.ecom.config.ClientIpUtils;
import com.ecom.service.SignInService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ทางเข้าของผู้ลงนามภายนอก มข. — ปุ่มในอีเมลพามาที่นี่ ไม่ผ่านหน้า /signin และไม่ผ่าน KKU SSO
 *
 * <p>GET แสดงหน้ายืนยันเท่านั้น ไม่ใช้ token: ระบบอีเมลของหน่วยงาน (Safe Links, Proofpoint ฯลฯ) เปิดลิงก์
 * ในอีเมลล่วงหน้าเพื่อสแกน ถ้า GET ใช้ token ลิงก์ใช้ครั้งเดียวจะหมดก่อนผู้รับได้กด token ถูกใช้ตอน POST
 * ที่ผู้ใช้กดปุ่มเองเท่านั้น
 */
@Controller
public class ExternalLinkController {

    private final ExternalLinkService links;
    private final SignInService signInService;

    public ExternalLinkController(ExternalLinkService links, SignInService signInService) {
        this.links = links;
        this.signInService = signInService;
    }

    @GetMapping({ ExternalLinkService.PATH, ExternalLinkService.PATH + "/" })
    public String requestForm() {
        requireEnabled();
        return "guest/link_request";
    }

    @PostMapping({ ExternalLinkService.PATH, ExternalLinkService.PATH + "/" })
    public String requestLink(@RequestParam(required = false) String email, Model model) {
        requireEnabled();
        String address = email == null ? "" : email.strip();
        var outcome = links.resend(address);
        model.addAttribute("email", address);
        model.addAttribute("outcome", outcome.name());
        model.addAttribute("outcomeOk", outcome == ExternalLinkService.ResendOutcome.SENT
                || outcome == ExternalLinkService.ResendOutcome.RECENTLY_SENT);
        model.addAttribute("outcomeMessage", switch (outcome) {
            case SENT -> "ส่งลิงก์เข้าสู่ระบบไปที่ " + address + " แล้ว กรุณาตรวจสอบกล่องจดหมาย"
                    + " (รวมถึงโฟลเดอร์จดหมายขยะ) ลิงก์ใช้ได้ครั้งเดียว";
            case RECENTLY_SENT -> "ระบบเพิ่งส่งลิงก์ไปที่ " + address + " เมื่อครู่ กรุณาตรวจสอบกล่องจดหมาย"
                    + " หรือรอ 1 นาทีแล้วขอใหม่";
            case KKU_ACCOUNT -> "อีเมลนี้เป็นอีเมลของ มข. กรุณาเข้าสู่ระบบด้วย KKU SSO ที่หน้าเข้าสู่ระบบ";
            case NOT_EXTERNAL -> "อีเมลนี้ไม่ใช่บัญชีผู้ลงนามภายนอก กรุณาเข้าสู่ระบบที่หน้าเข้าสู่ระบบ";
            case DISABLED -> "บัญชีของอีเมลนี้ถูกปิดการใช้งาน กรุณาติดต่อเจ้าหน้าที่วิทยาลัยฯ";
            case NOT_FOUND -> "ไม่พบอีเมลนี้ในรายชื่อผู้ลงนามภายนอก กรุณาใช้อีเมลที่ได้รับหนังสือเชิญ"
                    + " หรือติดต่อเจ้าหน้าที่วิทยาลัยฯ";
        });
        return "guest/link_request";
    }

    @GetMapping(ExternalLinkService.PATH + "/{token}")
    public String confirm(@PathVariable String token, Model model) {
        requireEnabled();
        model.addAttribute("token", token);
        model.addAttribute("live", links.isLive(token));
        return "guest/link_confirm";
    }

    @PostMapping(ExternalLinkService.PATH + "/{token}")
    public String signIn(@PathVariable String token, HttpServletRequest request, HttpServletResponse response,
            Model model) {
        requireEnabled();
        var redemption = links.redeem(token, ClientIpUtils.resolveClientIp(request));
        if (redemption.isEmpty()) {
            model.addAttribute("token", token);
            model.addAttribute("live", false);
            return "guest/link_confirm";
        }
        String landing = signInService.completeSignIn(request, response, redemption.get().user(),
                SignInService.Method.EMAIL_LINK, null);
        // คนนอกไม่มีอะไรทำในระบบนอกจากลงนาม — session สั้นกว่าปกติ เผื่อเปิดจากเครื่องที่ใช้ร่วมกัน
        request.getSession().setMaxInactiveInterval((int) links.sessionTimeout().toSeconds());
        Long stepId = redemption.get().stepId();
        return "redirect:" + (stepId != null ? "/esign/sign/" + stepId : landing);
    }

    private void requireEnabled() {
        if (!links.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
