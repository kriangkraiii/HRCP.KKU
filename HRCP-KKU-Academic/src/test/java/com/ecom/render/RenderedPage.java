package com.ecom.render;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * ตรวจหน้าที่ render ออกมาแล้ว ด้วย DOM จริงแทนการค้นสตริง
 *
 * <p>เทสเดิมในโปรเจกต์ตรวจหน้าเว็บด้วย {@code contentAsString().contains("...")}
 * ซึ่งเปราะสองทาง: ผ่านทั้งที่ไม่ควรผ่าน (ข้อความไปโผล่ในคอมเมนต์หรือใน
 * {@code <script>}) และ fail ทั้งที่ระบบไม่ได้ผิด (มีคนขัดเกลาคำบนปุ่ม)
 * ตัวอย่างล่าสุดคือ {@code RequiredAttachmentNoticeTest} ที่แดงเพราะประโยคถูกแก้คำ
 * ทั้งที่กติกายังถูกบังคับอยู่ครบ
 *
 * <p>สิ่งที่ตรวจที่นี่จึงเป็น <b>คุณสมบัติเชิงโครงสร้าง</b> ที่ต้องจริงเสมอไม่ว่า
 * ถ้อยคำจะเปลี่ยนไปอย่างไร — หน้าต้อง render จนจบ, ไม่มี expression ที่แก้ไม่ออก
 * หลงเหลือ, ไฟล์ที่หน้าอ้างถึงต้องมีอยู่จริง, ฟอร์มต้องมี CSRF token,
 * และ id ต้องไม่ซ้ำ
 *
 * <p><b>ทำไมต้องตรวจ id ซ้ำ:</b> {@code getElementById} คืนตัวแรกเสมอ หน้าที่มี id
 * ซ้ำจึงมีสคริปต์ที่ไปอัปเดตองค์ประกอบผิดตัวโดยไม่มีข้อผิดพลาดใด ๆ ปรากฏ
 * และต้องตรวจกับ <b>ผลลัพธ์ที่ render แล้ว</b> เท่านั้น — ในไฟล์เทมเพลตมี id ซ้ำ
 * ได้อย่างถูกต้องเมื่ออยู่คนละสาขาของ {@code th:if} ซึ่งจะเหลือรอดมาแค่สาขาเดียว
 */
final class RenderedPage {

    /** ที่เก็บไฟล์ static ที่ commit ไว้ ใช้ตรวจว่าหน้าอ้างถึงไฟล์ที่มีอยู่จริง */
    private static final Path STATIC_ROOT =
            Path.of("src", "main", "resources", "static");

    /** ตรวจเฉพาะพาธที่ชี้ไปไฟล์ที่อยู่ในรีโปจริง ไม่ใช่ของที่ระบบสร้างตอนรัน */
    private static final List<String> CHECKED_ASSET_PREFIXES =
            List.of("/js/", "/css/", "/vendor/");

    private final String url;
    private final Document doc;

    private RenderedPage(String url, String html) {
        this.url = url;
        this.doc = Jsoup.parse(html);
    }

    /**
     * ตรวจหน้าหนึ่งหน้าแล้วคืน <b>ข้อบกพร่องทั้งหมดที่พบ</b>
     *
     * <p>คืนเป็นรายการแทนที่จะ assert ทีละข้อ เพราะหน้าที่พังมักพังหลายอย่างพร้อมกัน
     * การเห็นครบในรอบเดียวเร็วกว่าการไล่แก้ทีละข้อแล้วรันใหม่
     */
    static List<String> complaintsAbout(String url, MockHttpServletResponse response)
            throws Exception {

        List<String> complaints = new ArrayList<>();

        if (response.getStatus() != 200) {
            complaints.add("ตอบ HTTP " + response.getStatus() + " แทนที่จะเป็น 200"
                    + (response.getRedirectedUrl() != null
                            ? " (เด้งไป " + response.getRedirectedUrl() + ")"
                            : ""));
            return complaints; // ไม่มี HTML ให้ตรวจต่อ
        }

        String contentType = response.getContentType();
        if (contentType == null || !contentType.contains("text/html")) {
            complaints.add("Content-Type เป็น " + contentType + " ไม่ใช่ text/html");
            return complaints;
        }

        RenderedPage page = new RenderedPage(url, response.getContentAsString());
        page.mustHaveRenderedABody(complaints);
        page.mustNotLeaveThymeleafAttributesBehind(complaints);
        page.mustNotLeaveUnresolvedExpressions(complaints);
        page.mustReferenceFilesThatExist(complaints);
        page.mustGiveEveryPostFormACsrfToken(complaints);
        page.mustNotRepeatAnElementId(complaints);
        return complaints;
    }

    // ------------------------------------------------------------------

    /** หน้าที่ context พังกลางคันมักคืน body ว่างพร้อม status 200 */
    private void mustHaveRenderedABody(List<String> complaints) {
        if (doc.body().children().isEmpty()) {
            complaints.add("body ว่างเปล่า — หน้าไม่ได้ render อะไรเลย");
        }
    }

    /**
     * แอตทริบิวต์ {@code th:*} ที่ยังเหลืออยู่ในผลลัพธ์แปลว่า Thymeleaf ไม่ได้ประมวลผล
     * ส่วนนั้น — มักเกิดเมื่อ fragment ถูกอ้างผิดชื่อ หรือมาร์กอัปหลุดออกนอกขอบเขต
     * ที่ template engine มองเห็น หน้าจะดูปกติดีจนกว่าจะมีคนสังเกตว่าปุ่มไม่ทำงาน
     */
    private void mustNotLeaveThymeleafAttributesBehind(List<String> complaints) {
        Set<String> leftovers = new LinkedHashSet<>();
        for (Element el : doc.getAllElements()) {
            for (Attribute attr : el.attributes()) {
                if (attr.getKey().startsWith("th:")) {
                    leftovers.add(attr.getKey() + " บน <" + el.tagName() + ">");
                }
            }
        }
        if (!leftovers.isEmpty()) {
            complaints.add("มีแอตทริบิวต์ Thymeleaf หลงเหลือในผลลัพธ์: " + leftovers);
        }
    }

    /**
     * {@code ${...}} ที่โผล่ในข้อความที่ผู้ใช้เห็น แปลว่า expression แก้ไม่ออก
     *
     * <p>ใช้ {@code doc.text()} ไม่ใช่ HTML ดิบ เพราะเนื้อใน {@code <script>} เป็น
     * DataNode ที่ {@code text()} ไม่เก็บ — ไม่อย่างนั้น template literal ของ
     * JavaScript ({@code `${x}`}) จะกลายเป็น false positive ทุกหน้า
     */
    private void mustNotLeaveUnresolvedExpressions(List<String> complaints) {
        String visible = doc.text();
        for (String marker : List.of("${", "*{", "#{")) {
            int at = visible.indexOf(marker);
            if (at >= 0) {
                complaints.add("มี expression ที่แก้ไม่ออกโผล่ให้ผู้ใช้เห็น: \""
                        + visible.substring(at, Math.min(at + 60, visible.length())) + "\"");
            }
        }
    }

    /**
     * ไฟล์ที่หน้าอ้างถึงต้องมีอยู่จริง
     *
     * <p>ทุก {@code <script src>} ในโปรเจกต์นี้ต่อท้ายด้วยเลขเวอร์ชันเอง
     * ({@code doc_preview.js?v=3.0}) การเปลี่ยนชื่อไฟล์หรือแก้พาธจึงพังแบบเงียบสนิท:
     * เบราว์เซอร์ได้ 404 แล้วข้ามไป ไม่มีอะไรบนหน้าจอบอกว่าสคริปต์หายไป
     */
    private void mustReferenceFilesThatExist(List<String> complaints) {
        Set<String> missing = new LinkedHashSet<>();
        for (Element el : doc.select("script[src], link[href]")) {
            String ref = el.hasAttr("src") ? el.attr("src") : el.attr("href");
            String path = ref.split("\\?")[0];
            if (CHECKED_ASSET_PREFIXES.stream().noneMatch(path::startsWith)) {
                continue;
            }
            if (!Files.exists(STATIC_ROOT.resolve(path.substring(1)))) {
                missing.add(ref);
            }
        }
        if (!missing.isEmpty()) {
            complaints.add("อ้างถึงไฟล์ที่ไม่มีอยู่ใน static/: " + missing);
        }
    }

    /**
     * ฟอร์ม POST ที่ไม่มี CSRF token จะถูก {@code CsrfFilter} ปฏิเสธแล้วเด้งไป
     * {@code /signin?expired=true} — ผู้ใช้กรอกครบทั้งฟอร์มแล้วเจอหน้าเข้าสู่ระบบ
     * โดยไม่มีคำอธิบาย ชื่อพารามิเตอร์คือค่าเริ่มต้นของ Spring ({@code _csrf});
     * ที่ถูกตั้งชื่อใหม่ใน SecurityConfig คือ cookie กับ header เท่านั้น
     */
    private void mustGiveEveryPostFormACsrfToken(List<String> complaints) {
        Set<String> unprotected = new LinkedHashSet<>();
        for (Element form : doc.select("form")) {
            String method = form.attr("method");
            if (!"post".equalsIgnoreCase(method)) {
                continue;
            }
            if (form.select("input[name=_csrf]").isEmpty()) {
                unprotected.add(form.attr("action").isEmpty() ? "(ไม่มี action)" : form.attr("action"));
            }
        }
        if (!unprotected.isEmpty()) {
            complaints.add("ฟอร์ม POST ที่ไม่มี CSRF token: " + unprotected);
        }
    }

    /** id ซ้ำในหน้าเดียว — getElementById จะคืนตัวแรกเสมอ อีกตัวกลายเป็นของตาย */
    private void mustNotRepeatAnElementId(List<String> complaints) {
        Map<String, Integer> seen = new TreeMap<>();
        for (Element el : doc.select("[id]")) {
            String id = el.id();
            if (!id.isBlank()) {
                seen.merge(id, 1, Integer::sum);
            }
        }
        Map<String, Integer> repeated = new TreeMap<>();
        seen.forEach((id, count) -> {
            if (count > 1) {
                repeated.put(id, count);
            }
        });
        if (!repeated.isEmpty()) {
            complaints.add("id ซ้ำในหน้าเดียวกัน: " + repeated);
        }
    }

    @Override
    public String toString() {
        return url;
    }
}
