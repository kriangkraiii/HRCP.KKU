package com.ecom.config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ecom.model.UserDtls;

/**
 * แยกผู้ลงนาม {@link UserDtls#ROLE_EXTERNAL} เป็น "นอกวิทยาลัยแต่ใน มข." กับ "นอก มข." จากโดเมนอีเมล
 *
 * <p>{@code ROLE_EXTERNAL} แปลว่าไม่อยู่ในทะเบียนบุคลากรของวิทยาลัยเท่านั้น บุคลากรคณะอื่นของ มข. ก็เป็นบทบาทนี้
 * แต่มีบัญชี KKU SSO และขอ .p12 ของ มข. ได้ จึงเข้าระบบทาง SSO และใช้ตราระบบไม่ได้ ส่วนคนนอก มข.
 * เข้าด้วยลิงก์ทางอีเมล และลงนามด้วยตราระบบได้ — ดู docs/PLAN-external-magic-link.md ข้อ 2.1
 *
 * <p>ดูจากอีเมลทุกครั้งที่ถาม ไม่เก็บเป็นคอลัมน์ แก้อีเมลของบัญชีแล้วกลุ่มเปลี่ยนตามทันที
 */
@Component
public class KkuEmailDomains {

    private final List<String> domains;

    public KkuEmailDomains(@Value("${app.kku.email-domains:kku.ac.th,kkumail.com}") String domains) {
        this.domains = Arrays.stream(domains.split(","))
                .map(d -> d.strip().toLowerCase(Locale.ROOT))
                .map(d -> d.startsWith("@") ? d.substring(1) : d)
                .filter(d -> !d.isEmpty())
                .toList();
    }

    /** อีเมลที่ KKU SSO ออกให้ — ตรงทั้งโดเมนเต็มและ subdomain ({@code en.kku.ac.th}) แต่ไม่ตรง {@code notkku.ac.th} */
    public boolean isKku(String email) {
        if (email == null) {
            return false;
        }
        int at = email.lastIndexOf('@');
        if (at < 0) {
            return false;
        }
        String host = email.substring(at + 1).strip().toLowerCase(Locale.ROOT);
        return domains.stream().anyMatch(d -> host.equals(d) || host.endsWith("." + d));
    }

    /** ผู้ลงนามภายนอกที่ไม่มีบัญชี KKU SSO — เข้าด้วยลิงก์ทางอีเมล และลงนามด้วยตราระบบได้ */
    public boolean isOutsideKku(UserDtls user) {
        return user != null && user.isExternal() && !isKku(user.getEmail());
    }
}
