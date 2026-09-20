package com.ecom.academic.controller;

import org.springframework.ui.Model;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.service.DocumentCompleteness;

/**
 * ชิ้นส่วนที่หน้าฟอร์มเอกสารทุกใบใช้ร่วมกัน — URL ที่ต้องกลับไป และกติกาความครบถ้วน
 *
 * <p>เดิมทุก handler ต่อสตริงเองแล้วลงเอยด้วยการ redirect ออกไปหน้ารายการคำร้องหลัง
 * กดบันทึก ผู้ใช้จึงไม่เห็นแผงลงนามที่อยู่ใต้ฟอร์มและไม่รู้ว่ายังต้องเซ็นต่อ รวมไว้ที่เดียว
 * เพื่อให้ "บันทึกแล้วอยู่หน้าเดิม" เป็นค่าตั้งต้นที่ทุก handler ใช้ร่วมกัน
 *
 * <p>การส่งผู้ยื่นไปยัง URL ใต้ {@code /admin/**} จะถูกกฎการเข้าถึงเด้งกลับและทำให้
 * flash message หายไปด้วย จึงต้องเลือกเส้นทางตามบทบาทเสมอ
 */
public final class DocumentFormSupport {

    private DocumentFormSupport() {
    }

    /** หน้าฟอร์มของเอกสารฉบับนี้ ตามบทบาทของผู้ใช้ */
    public static String formPath(SignatureModule module, Long requestId, int documentType, boolean admin) {
        String base = module == SignatureModule.ACADEMIC
                ? (admin ? "/admin/academic/request/" : "/user/academic/request/")
                : (admin ? "/admin/position/request/" : "/user/position/request/");
        return base + requestId + "/document/" + documentType;
    }

    /** {@code redirect:} กลับมาที่ฟอร์มเดิม พร้อมธงที่หน้าฟอร์มใช้ขึ้นคำแนะนำขั้นตอนถัดไป */
    public static String redirectAfterSave(SignatureModule module, Long requestId, int documentType, boolean admin) {
        return "redirect:" + formPath(module, requestId, documentType, admin) + "?saved=1";
    }

    /** {@code redirect:} กลับมาที่ฟอร์มเดิมเฉย ๆ — ใช้คู่กับ flash {@code errorMsg} */
    public static String redirectToForm(SignatureModule module, Long requestId, int documentType, boolean admin) {
        return "redirect:" + formPath(module, requestId, documentType, admin);
    }

    /**
     * กติกา "ช่องไหนไม่บังคับ" ให้ฝั่งเบราว์เซอร์เตือนผู้ใช้ก่อนกดส่งลงนาม
     *
     * <p>ส่งเป็นสตริงคั่นจุลภาคผ่าน data attribute ของแผงลงนาม เพราะ
     * {@link DocumentCompleteness} เป็นแหล่งความจริงเดียว ฝั่งเบราว์เซอร์จึงไม่ต้อง
     * เก็บรายการเดียวกันซ้ำอีกชุด
     */
    public static void addCompletenessRules(Model model, SignatureModule module, int documentType) {
        model.addAttribute("docOptionalFields",
                String.join(",", DocumentCompleteness.optionalFields(module, documentType)));
        model.addAttribute("docRepeatablePrefixes",
                String.join(",", DocumentCompleteness.repeatablePrefixes(module, documentType)));
    }
}
