package com.ecom.util;

import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Utility to generate modern, responsive, and official HTML email templates
 * for Khon Kaen University - College of Computing (วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น).
 * Integrated with official KKU emblem & College of Computing branding via fast global CDN (Zero-attachment, 0ms instant loading).
 */
public final class EmailTemplateHelper {

    private EmailTemplateHelper() {
    }

    public static final String DEFAULT_SENDER_EMAIL = "noreply@kku.ac.th";
    public static final String SENDER_NAME = "College of Computing, KKU";
    public static final String SENDER_SYSTEM_NAME = "CP HRD - College of Computing Human Resource Development System";

    /**
     * Resolves the sender email address safely.
     * If the configured email is null, blank, or improperly set, returns the official default (noreply@kku.ac.th).
     */
    public static String resolveSenderEmail(String configuredEmail) {
        if (configuredEmail == null || configuredEmail.isBlank()) {
            return DEFAULT_SENDER_EMAIL;
        }
        return configuredEmail.trim();
    }

    public static final String DEFAULT_LOGO_BASE_URL =
            "https://raw.githubusercontent.com/kriangkraiii/HRCP.KKU/main/HRCP-KKU-Academic/src/main/resources/static/img";
    public static final String CID_KKU_LOGO = "cid:kku_logo";
    public static final String CID_CP_LOGO = "cid:cp_logo";
    public static final String LOGO_KKU_URL = DEFAULT_LOGO_BASE_URL + "/kku_logo.png";
    public static final String LOGO_CP_URL = DEFAULT_LOGO_BASE_URL + "/cphr_logo.png";

    /**
     * Attaches the bundled KKU Emblem and College of Computing logo as inline CID resources.
     * In modern zero-attachment mode, this is a safe no-op to eliminate the [inline] badge in Gmail.
     */
    public static void attachLogos(MimeMessageHelper helper) {
        // Safe no-op: Prevents Gmail [inline] attachment badge and eliminates binary payload bloat
    }

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(EmailTemplateHelper.class);

    /**
     * Whether to skip a real SMTP request for this address.
     *
     * <p>True for the configured rehearsal accounts (see
     * {@code app.notification.test-accounts}), and for a missing address, which
     * cannot be delivered to either. The two are distinct problems, so a
     * missing address is logged rather than quietly treated as a test account.
     *
     * <p>Matching is by exact address. It used to match whole domains —
     * {@code @test.com}, {@code @example.com}, and every address at
     * {@code @user.com} and {@code @admin.com} — which would have made a real
     * person silently unreachable if their address happened to look like one.
     */
    public static boolean isTestEmail(String email) {
        if (email == null || email.isBlank()) {
            log.warn("Skipping email with no recipient address — this is a data problem, not a test account");
            return true;
        }
        return com.ecom.config.TestAccountRegistry.isConfiguredTestAccount(email);
    }

    /**
     * Wraps inner content in the official University & College email layout.
     *
     * @param headingTitle Main title in header
     * @param badgeText    Optional subtitle/tag in header
     * @param bodyContent  HTML content inside the card
     * @return Full HTML email string
     */
    public static String wrapLayout(String headingTitle, String badgeText, String bodyContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang='th'>");
        sb.append("<head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'></head>");
        sb.append("<body style='margin:0;padding:0;background-color:#f1f5f9;font-family:\"Sarabun\",\"Prompt\",-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,Helvetica,Arial,sans-serif;color:#1e293b;line-height:1.6;'>");
        
        sb.append("<table width='100%' border='0' cellspacing='0' cellpadding='0' style='background-color:#f1f5f9;padding:24px 12px;'>");
        sb.append("<tr><td align='center'>");
        
        sb.append("<table width='100%' border='0' cellspacing='0' cellpadding='0' style='max-width:600px;background-color:#ffffff;border-radius:14px;overflow:hidden;box-shadow:0 6px 20px rgba(0,0,0,0.07);border:1px solid #e2e8f0;'>");
        
        // --- Official Brand Header (CP HRD - Matching Sign-in Page & Zero-Attachment) ---
        sb.append("<tr><td style='background:linear-gradient(135deg,#0b1727 0%,#0f2744 50%,#173763 100%);padding:36px 28px 28px 28px;text-align:center;border-bottom:3px solid #3b82f6;'>");
        
        // Brand Title: CP | HRD
        sb.append("<div style='font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,\"Prompt\",Helvetica,Arial,sans-serif;font-size:32px;font-weight:800;letter-spacing:1px;line-height:1.1;margin-bottom:8px;'>");
        sb.append("<span style='color:#60a5fa;'>CP</span>");
        sb.append("<span style='color:#64748b;font-weight:300;margin:0 10px;'>|</span>");
        sb.append("<span style='color:#ffffff;'>HRD</span>");
        sb.append("</div>");
        
        // Brand Subtitle: COLLEGE OF COMPUTING HUMAN RESOURCE DEVELOPMENT SYSTEM
        sb.append("<div style='color:#94a3b8;font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,\"Prompt\",Helvetica,Arial,sans-serif;font-size:11px;font-weight:600;letter-spacing:1.8px;text-transform:uppercase;margin-bottom:18px;'>");
        sb.append("COLLEGE OF COMPUTING HUMAN RESOURCE DEVELOPMENT SYSTEM");
        sb.append("</div>");
        
        // Accent Divider
        sb.append("<div style='width:48px;height:2px;background:#3b82f6;margin:0 auto 18px auto;border-radius:2px;'></div>");
        
        // Main Heading
        sb.append("<h1 style='color:#ffffff;margin:0 0 6px 0;font-size:20px;font-weight:700;line-height:1.4;font-family:\"Sarabun\",\"Prompt\",sans-serif;'>")
          .append(escapeHtml(headingTitle)).append("</h1>");
        
        // Optional Badge
        if (badgeText != null && !badgeText.isBlank()) {
            sb.append("<div style='display:inline-block;background:rgba(59,130,246,0.18);border:1px solid rgba(96,165,250,0.4);color:#bfdbfe;font-size:12px;font-weight:500;padding:3px 14px;border-radius:20px;margin-top:6px;'>")
              .append(escapeHtml(badgeText)).append("</div>");
        }
        
        sb.append("<div style='color:#94a3b8;font-size:12px;margin-top:12px;'>วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น &bull; College of Computing, KKU</div>");
        sb.append("</td></tr>");
        
        // --- Body Content ---
        sb.append("<tr><td style='padding:32px 28px;'>");
        sb.append(bodyContent);
        sb.append("</td></tr>");
        
        // --- Official Footer ---
        sb.append("<tr><td style='background-color:#f8fafc;padding:22px 24px;border-top:1px solid #e2e8f0;text-align:center;color:#64748b;font-size:12px;'>");
        sb.append("<p style='margin:0 0 8px 0;color:#94a3b8;font-size:11px;'>อีเมลฉบับนี้เป็นการแจ้งเตือนอัตโนมัติจากระบบ กรุณาอย่าตอบกลับอีเมลนี้</p>");
        sb.append("<div style='font-weight:600;color:#334155;font-size:13px;'>วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น</div>");
        sb.append("<div style='color:#64748b;margin-top:2px;'>College of Computing, Khon Kaen University</div>");
        sb.append("<div style='margin-top:6px;color:#64748b;'>123 ถนนมิตรภาพ ตำบลในเมือง อำเภอเมือง จังหวัดขอนแก่น 40002</div>");
        sb.append("<div style='margin-top:4px;color:#64748b;'>โทรศัพท์: 043-009700 ต่อ 44456-59 | เว็บไซต์: <a href='https://computing.kku.ac.th' target='_blank' style='color:#2563eb;text-decoration:none;'>computing.kku.ac.th</a></div>");
        sb.append("</td></tr>");
        
        sb.append("</table>");
        sb.append("</td></tr></table>");
        sb.append("</body></html>");
        return sb.toString();
    }

    // =====================================================================
    // จดหมายแบบทางการ — ทุกอีเมลขึ้นต้น "เรื่อง / เรียน" บอกว่าเป็นเรื่องอะไร ต้องทำอะไร แล้วปิดท้ายแบบหนังสือราชการ
    // =====================================================================

    /** ผู้ส่งที่ลงท้ายจดหมาย — หน่วยงานที่ดูแลงานในระบบนี้ */
    public static final String SIGN_OFF_UNIT = "ภารกิจด้านทรัพยากรบุคคล";
    public static final String SIGN_OFF_ORG = "วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น";

    /**
     * ชื่อสำหรับขึ้นต้นจดหมาย: คำนำหน้า/ตำแหน่งทางวิชาการติดกับชื่อ-สกุล เช่น "รศ.ดร.วิภา ใจดี"
     * ไม่มีคำนำหน้าใช้ "คุณ" — ไม่ขึ้นต้นด้วยชื่อเปล่า ๆ
     */
    public static String formalName(com.ecom.model.UserDtls user) {
        if (user == null) {
            return "ท่าน";
        }
        String name = user.getName();
        if (name == null || name.isBlank()) {
            String first = user.getFirstName() == null ? "" : user.getFirstName().strip();
            String last = user.getLastName() == null ? "" : user.getLastName().strip();
            name = (first + " " + last).strip();
        }
        if (name.isBlank()) {
            return "ท่าน";
        }
        String title = user.getTitle() == null ? "" : user.getTitle().strip();
        return title.isEmpty() ? "คุณ" + name.strip() : title + name.strip();
    }

    /** ประกอบเนื้อจดหมายทีละส่วน ข้อความทุกส่วน escape ให้แล้ว ยกเว้นที่ชื่อเมธอดลงท้ายด้วย Html */
    public static final class Letter {
        private final StringBuilder sb = new StringBuilder();

        private Letter() {
        }

        /** เริ่มจดหมาย: เรื่อง แล้ว เรียน */
        public static Letter of(String subject, String salutationName) {
            Letter l = new Letter();
            if (subject != null && !subject.isBlank()) {
                l.sb.append("<p style='font-size:14px;color:#1e293b;margin:0 0 6px 0;'><strong>เรื่อง</strong>&nbsp;&nbsp;")
                        .append(escapeHtml(subject)).append("</p>");
            }
            l.sb.append("<p style='font-size:14px;color:#1e293b;margin:0 0 18px 0;'><strong>เรียน</strong>&nbsp;&nbsp;")
                    .append(escapeHtml(salutationName == null || salutationName.isBlank() ? "ท่าน" : salutationName))
                    .append("</p>");
            return l;
        }

        public Letter para(String text) {
            return text == null || text.isBlank() ? this : paraHtml(escapeHtml(text));
        }

        public Letter paraHtml(String html) {
            sb.append("<p style='font-size:14px;color:#334155;margin:0 0 14px 0;text-indent:2.5em;'>").append(html).append("</p>");
            return this;
        }

        /** ตารางรายละเอียด แถวที่ค่าว่างไม่แสดง */
        public Letter details(java.util.Map<String, String> rows) {
            sb.append("<div style='background:#f8fafc;border:1px solid #e2e8f0;border-left:4px solid #1e3a8a;border-radius:8px;padding:14px 18px;margin:4px 0 18px 0;'>");
            sb.append("<table width='100%' border='0' cellspacing='0' cellpadding='4' style='font-size:14px;'>");
            rows.forEach((label, value) -> {
                if (value != null && !value.isBlank()) {
                    sb.append("<tr><td width='34%' valign='top' style='color:#64748b;font-weight:600;'>").append(escapeHtml(label))
                            .append("</td><td style='color:#1e293b;'>").append(escapeHtml(value)).append("</td></tr>");
                }
            });
            sb.append("</table></div>");
            return this;
        }

        /** สิ่งที่ผู้รับต้องทำ เรียงตามลำดับ */
        public Letter steps(String heading, java.util.List<String> items) {
            if (items == null || items.isEmpty()) {
                return this;
            }
            sb.append("<p style='font-size:14px;color:#1e293b;font-weight:600;margin:0 0 6px 0;'>").append(escapeHtml(heading)).append("</p>");
            sb.append("<ol style='font-size:14px;color:#334155;margin:0 0 16px 0;padding-left:22px;'>");
            for (String item : items) {
                sb.append("<li style='margin-bottom:6px;'>").append(escapeHtml(item)).append("</li>");
            }
            sb.append("</ol>");
            return this;
        }

        /** กล่องเน้นข้อความ — tone: info, warn, danger, success */
        public Letter note(String tone, String heading, String text) {
            String[] c = switch (tone == null ? "info" : tone) {
                case "warn" -> new String[] { "#fffbeb", "#fde68a", "#d97706", "#92400e" };
                case "danger" -> new String[] { "#fef2f2", "#fecaca", "#dc2626", "#991b1b" };
                case "success" -> new String[] { "#f0fdf4", "#bbf7d0", "#16a34a", "#166534" };
                default -> new String[] { "#eff6ff", "#bfdbfe", "#2563eb", "#1e3a8a" };
            };
            sb.append("<div style='background:").append(c[0]).append(";border:1px solid ").append(c[1])
                    .append(";border-left:4px solid ").append(c[2]).append(";border-radius:8px;padding:12px 16px;margin:0 0 16px 0;font-size:14px;'>");
            if (heading != null && !heading.isBlank()) {
                sb.append("<div style='font-weight:700;color:").append(c[3]).append(";margin-bottom:4px;'>").append(escapeHtml(heading)).append("</div>");
            }
            sb.append("<div style='color:#1e293b;white-space:pre-wrap;'>").append(escapeHtml(text)).append("</div></div>");
            return this;
        }

        /** ส่วนที่ผู้เรียกประกอบ HTML เอง (เช่น รหัส OTP) */
        public Letter html(String html) {
            sb.append(html);
            return this;
        }

        public Letter button(String label, String url) {
            if (url == null || url.isBlank()) {
                return this;
            }
            sb.append("<div style='text-align:center;margin:22px 0 10px 0;'><a href='").append(escapeHtml(url))
                    .append("' target='_blank' style='display:inline-block;background-color:#0d47a1;color:#ffffff;text-decoration:none;padding:12px 28px;border-radius:6px;font-weight:600;font-size:15px;'>")
                    .append(escapeHtml(label)).append("</a></div>");
            sb.append("<p style='font-size:12px;color:#94a3b8;text-align:center;margin:0 0 18px 0;word-break:break-all;'>หากปุ่มไม่ทำงาน กรุณาคัดลอกลิงก์นี้ไปเปิดในเบราว์เซอร์: ")
                    .append(escapeHtml(url)).append("</p>");
            return this;
        }

        /** ปิดจดหมาย เช่น "จึงเรียนมาเพื่อโปรดทราบ" แล้วลงท้ายด้วยหน่วยงาน */
        public String close(String closingLine) {
            sb.append("<p style='font-size:14px;color:#334155;margin:18px 0 22px 0;text-indent:2.5em;'>").append(escapeHtml(closingLine)).append("</p>");
            sb.append("<p style='font-size:14px;color:#1e293b;margin:0;text-align:center;line-height:1.7;'>")
                    .append(SIGN_OFF_UNIT).append("<br>").append(SIGN_OFF_ORG).append("</p>");
            return sb.toString();
        }
    }

    /** กล่องเน้นข้อความแบบเดียวกับใน {@link Letter} สำหรับส่วนที่ผู้เรียกแนบเพิ่ม */
    public static String noteHtml(String tone, String heading, String text) {
        Letter l = new Letter();
        l.note(tone, heading, text);
        return l.sb.toString();
    }

    private static java.util.Map<String, String> rows(String... labelValue) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < labelValue.length; i += 2) {
            m.put(labelValue[i], labelValue[i + 1]);
        }
        return m;
    }

    /**
     * รหัส OTP สำหรับยืนยันตัวตน
     *
     * @param name ชื่อสำหรับขึ้นต้นจดหมาย ({@link #formalName})
     */
    public static String buildOtpEmail(String name, String otp, String purposeText, int expiryMinutes) {
        String code = "<div style='text-align:center;margin:8px 0 20px 0;'>"
                + "<div style='display:inline-block;background:#f0f7ff;border:2px dashed #2563eb;border-radius:12px;padding:14px 36px;'>"
                + "<div style='font-size:12px;color:#64748b;margin-bottom:4px;'>รหัสยืนยันตัวตน (OTP)</div>"
                + "<span style='font-size:34px;font-weight:700;color:#1e3a8a;letter-spacing:6px;font-family:monospace;'>"
                + escapeHtml(otp) + "</span></div></div>";
        String body = Letter.of("รหัสยืนยันตัวตนสำหรับ" + purposeText, name)
                .para("ตามที่ท่านได้ขอรหัสยืนยันตัวตน (OTP) สำหรับ" + purposeText
                        + " ในระบบพัฒนาบุคลากร วิทยาลัยการคอมพิวเตอร์ ขอแจ้งรหัสของท่าน ดังนี้")
                .html(code)
                .note("warn", null, "รหัสนี้ใช้ได้ภายใน " + expiryMinutes + " นาที และใช้ได้เพียงครั้งเดียว"
                        + " เพื่อความปลอดภัย กรุณาอย่าเปิดเผยรหัสนี้แก่ผู้อื่น รวมถึงเจ้าหน้าที่")
                .para("หากท่านไม่ได้เป็นผู้ขอรหัสนี้ ไม่ต้องดำเนินการใด ๆ และโปรดแจ้งผู้ดูแลระบบ")
                .close("จึงเรียนมาเพื่อโปรดทราบ");
        return wrapLayout("รหัสยืนยันตัวตน (OTP)", purposeText, body);
    }

    /** ลิงก์ตั้งรหัสผ่านใหม่ */
    public static String buildPasswordResetEmail(String resetUrl) {
        return buildPasswordResetEmail(null, resetUrl);
    }

    public static String buildPasswordResetEmail(String name, String resetUrl) {
        String body = Letter.of("การตั้งรหัสผ่านใหม่", name == null ? "ผู้ใช้งานระบบ" : name)
                .para("ระบบพัฒนาบุคลากร วิทยาลัยการคอมพิวเตอร์ ได้รับคำขอตั้งรหัสผ่านใหม่สำหรับบัญชีของท่าน"
                        + " ท่านสามารถตั้งรหัสผ่านใหม่ได้โดยกดปุ่มด้านล่าง")
                .button("ตั้งรหัสผ่านใหม่", resetUrl)
                .para("หากท่านไม่ได้ขอตั้งรหัสผ่านใหม่ บัญชีของท่านยังปลอดภัย ไม่ต้องดำเนินการใด ๆ")
                .close("จึงเรียนมาเพื่อโปรดทราบ");
        return wrapLayout("การตั้งรหัสผ่านใหม่", "ความปลอดภัยของบัญชี", body);
    }

    /**
     * แจ้งผู้ยื่นว่าคำร้องเปลี่ยนสถานะ
     *
     * @param applicantName  ชื่อสำหรับขึ้นต้นจดหมาย ({@link #formalName})
     * @param meaning        สถานะนี้หมายถึงอะไร และขั้นต่อไปคืออะไร
     * @param extraDetailsHtml ส่วนเพิ่มเติม เช่น เหตุผลที่ส่งคืน (ประกอบด้วย {@link Letter} ไม่ได้ เพราะมาจากผู้เรียก)
     */
    public static String buildStatusChangeEmail(String applicantName, String requestType, String requestCode,
            String oldStatusLabel, String newStatusLabel, String statusColor, String extraDetailsHtml) {
        return buildStatusChangeEmail(applicantName, requestType, requestCode, oldStatusLabel, newStatusLabel,
                statusColor, null, extraDetailsHtml);
    }

    public static String buildStatusChangeEmail(String applicantName, String requestType, String requestCode,
            String oldStatusLabel, String newStatusLabel, String statusColor, String meaning, String extraDetailsHtml) {
        String body = Letter.of("แจ้งความคืบหน้า" + requestType + " (รหัส " + requestCode + ")", applicantName)
                .para("ตามที่ท่านได้ยื่น" + requestType + " ไว้ในระบบพัฒนาบุคลากร วิทยาลัยการคอมพิวเตอร์ ขอแจ้งความคืบหน้า ดังนี้")
                .details(rows("ประเภทคำร้อง", requestType,
                        "รหัสคำร้อง", requestCode,
                        "สถานะเดิม", "-".equals(oldStatusLabel) ? null : oldStatusLabel,
                        "สถานะปัจจุบัน", newStatusLabel))
                .para(meaning)
                .html(extraDetailsHtml == null ? "" : extraDetailsHtml)
                .para("ท่านสามารถตรวจสอบรายละเอียดเพิ่มเติมได้โดยเข้าสู่ระบบ")
                .close("จึงเรียนมาเพื่อโปรดทราบ");
        return wrapLayout("แจ้งความคืบหน้าคำร้อง", requestType, body);
    }

    /** แจ้งเจ้าหน้าที่ว่ามีคำร้องใหม่ */
    public static String buildAdminNewRequestEmail(String adminName, String applicantName, String applicantEmail,
            String requestType, String requestCode, String targetPosition) {
        String body = Letter.of("มี" + requestType + "ยื่นเข้ามาใหม่ (รหัส " + requestCode + ")", adminName)
                .para(applicantName + " ได้ยื่น" + requestType + " เข้ามาในระบบพัฒนาบุคลากร วิทยาลัยการคอมพิวเตอร์"
                        + " รายละเอียดดังนี้")
                .details(rows("ผู้ยื่นคำร้อง", applicantName,
                        "อีเมล", applicantEmail,
                        "ประเภทคำร้อง", requestType,
                        "รหัสคำร้อง", requestCode,
                        "ตำแหน่งที่ขอ", targetPosition))
                .para("ขอให้ตรวจสอบความครบถ้วนของเอกสาร และดำเนินการตามขั้นตอนต่อไป")
                .close("จึงเรียนมาเพื่อโปรดดำเนินการ");
        return wrapLayout("คำร้องใหม่ในระบบ", "แจ้งเจ้าหน้าที่", body);
    }

    /** ข้อเสนอแนะของคณะอนุกรรมการที่ผู้ยื่นต้องแก้ไข */
    public static String buildSuggestionEmail(String applicantName, String requestCode, String suggestionsText) {
        String body = Letter.of("ข้อเสนอแนะจากคณะอนุกรรมการประเมินผลการสอน และการแก้ไขเอกสาร (รหัส " + requestCode + ")",
                        applicantName)
                .para("ตามที่คณะอนุกรรมการประเมินผลการสอนได้ประเมินผลการสอนของท่าน คณะอนุกรรมการมีข้อเสนอแนะ"
                        + " และขอให้ท่านปรับปรุงเอกสาร ดังนี้")
                .note("warn", "ข้อเสนอแนะจากคณะอนุกรรมการ", suggestionsText == null ? "" : suggestionsText)
                .steps("สิ่งที่ท่านต้องดำเนินการ", java.util.List.of(
                        "เข้าสู่ระบบ แล้วเปิดคำร้องรหัส " + requestCode,
                        "แก้ไขเอกสารตามข้อเสนอแนะข้างต้น",
                        "ส่งเอกสารฉบับแก้ไขผ่านระบบ วิทยาลัยฯ จะเสนอคณะอนุกรรมการพิจารณาอีกครั้ง"))
                .close("จึงเรียนมาเพื่อโปรดดำเนินการ");
        return wrapLayout("ข้อเสนอแนะจากคณะอนุกรรมการ", "ขอให้แก้ไขเอกสาร", body);
    }

    /** ข้อเสนอแนะแจ้งเพื่อทราบ — ไม่ต้องแก้ไขเอกสาร กระบวนการดำเนินต่อตามปกติ */
    public static String buildSuggestionNoticeEmail(String applicantName, String requestCode, String suggestionsText) {
        String body = Letter.of("ข้อเสนอแนะจากคณะอนุกรรมการประเมินผลการสอน (รหัส " + requestCode + ")", applicantName)
                .para("ตามที่คณะอนุกรรมการประเมินผลการสอนได้ประเมินผลการสอนของท่านแล้ว คณะอนุกรรมการมีข้อเสนอแนะ"
                        + " เพื่อเป็นประโยชน์ในการพัฒนาการสอน ดังนี้")
                .note("success", "ข้อเสนอแนะจากคณะอนุกรรมการ", suggestionsText == null ? "" : suggestionsText)
                .para("ข้อเสนอแนะนี้แจ้งเพื่อทราบ ท่านไม่ต้องแก้ไขเอกสาร วิทยาลัยฯ จะดำเนินการในขั้นตอนต่อไปให้")
                .close("จึงเรียนมาเพื่อโปรดทราบ");
        return wrapLayout("ข้อเสนอแนะจากคณะอนุกรรมการ", "แจ้งเพื่อทราบ", body);
    }

    /**
     * หนังสือแจ้งผลการประเมินผลการสอน (เอกสารที่ 9) ออกแล้ว — พาผู้ยื่นไปเปิดดูในระบบ
     */
    public static String buildResultLetterEmail(String applicantName, String requestCode, String memoNo,
            String issuedDate, String resultLevel, String expiryDate, String viewUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p style='font-size:16px;color:#1e293b;margin:0 0 12px 0;'>เรียน <strong>")
          .append(escapeHtml(applicantName)).append("</strong>,</p>");
        sb.append("<p style='font-size:14px;color:#475569;margin:0 0 16px 0;'>หนังสือแจ้งผลการประเมินผลการสอนตามคำร้องหมายเลข <strong>#")
          .append(escapeHtml(requestCode)).append("</strong> ของท่านออกแล้ว ท่านเปิดดูและดาวน์โหลดหนังสือได้ในระบบ</p>");

        sb.append("<div style='background:#f8fafc;border:1px solid #e2e8f0;border-left:4px solid #2563eb;padding:16px 20px;border-radius:8px;margin:20px 0;font-size:14px;color:#1e293b;line-height:1.8;'>");
        appendRow(sb, "เลขที่หนังสือ", memoNo);
        appendRow(sb, "ลงวันที่", issuedDate);
        appendRow(sb, "ผลการประเมิน", resultLevel);
        appendRow(sb, "ใช้ประกอบการขอกำหนดตำแหน่งได้ภายในวันที่", expiryDate);
        sb.append("</div>");

        sb.append("<div style='text-align:center;margin:30px 0;'>");
        sb.append("<a href='").append(escapeHtml(viewUrl)).append("' target='_blank' style='background:linear-gradient(135deg,#1e3a8a,#2563eb);color:#ffffff;text-decoration:none;padding:14px 32px;font-size:15px;font-weight:600;border-radius:8px;display:inline-block;box-shadow:0 4px 12px rgba(37,99,235,0.25);'>เปิดดูหนังสือแจ้งผล</a>");
        sb.append("</div>");

        return wrapLayout("หนังสือแจ้งผลการประเมินผลการสอน", "ออกหนังสือแล้ว", sb.toString());
    }

    private static void appendRow(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sb.append("<div><span style='color:#64748b;'>").append(escapeHtml(label)).append(":</span> <strong>")
          .append(escapeHtml(value)).append("</strong></div>");
    }

    /**
     * หนังสือเชิญเป็นกรรมการ (เอกสารที่ 5) ถึงกรรมการท่านหนึ่ง พร้อมทางเปิดดูเอกสารของผู้ยื่น
     *
     * @param committeeName ชื่อกรรมการตามที่พิมพ์ในหนังสือเชิญ (มีคำนำหน้าแล้ว)
     * @param applicantName ชื่อผู้ขอรับการประเมินพร้อมคำนำหน้า
     * @param files ชื่อไฟล์ที่ผู้ยื่นแนบ — เปิดได้จากหน้า {@code filesUrl} หลังเข้าสู่ระบบ
     * @param links ลิงก์ที่ผู้ยื่นแนบ (ชื่อ, URL) — เปิดได้จากอีเมลโดยตรง
     */
    public static String buildCommitteeInvitationEmail(String committeeName, String requestCode,
            String applicantName, String filesUrl, java.util.List<String> files,
            java.util.List<java.util.Map.Entry<String, String>> links) {
        StringBuilder list = new StringBuilder();
        if (files.isEmpty() && links.isEmpty()) {
            list.append(noteHtml("warn", null, "ผู้ขอรับการประเมินไม่ได้แนบเอกสารประกอบไว้ในระบบ"
                    + " กรุณาติดต่อภารกิจด้านทรัพยากรบุคคลเพื่อขอรับเอกสาร"));
        } else {
            list.append("<p style='font-size:14px;color:#1e293b;font-weight:600;margin:0 0 6px 0;'>เอกสารประกอบการประเมินของผู้ขอรับการประเมิน</p>");
            list.append("<ul style='margin:0 0 16px 0;padding-left:22px;color:#334155;font-size:14px;line-height:1.7;'>");
            for (String file : files) {
                list.append("<li>").append(escapeHtml(file)).append("</li>");
            }
            for (java.util.Map.Entry<String, String> link : links) {
                list.append("<li><a href='").append(escapeHtml(link.getValue()))
                        .append("' target='_blank' style='color:#2563eb;'>").append(escapeHtml(link.getKey())).append("</a></li>");
            }
            list.append("</ul>");
        }

        Letter letter = Letter.of("ขอเรียนเชิญเป็นกรรมการผู้ทรงคุณวุฒิประเมินผลการสอน", committeeName)
                .para("ด้วยวิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น ได้แต่งตั้งคณะอนุกรรมการประเมินผลการสอนของ "
                        + applicantName + " (รหัสคำร้อง " + requestCode + ") โดยมีท่านเป็นกรรมการ"
                        + " วิทยาลัยฯ จึงขอเรียนเชิญท่านเป็นกรรมการผู้ทรงคุณวุฒิ ตามหนังสือเชิญที่แนบมากับอีเมลฉบับนี้"
                        + " ซึ่งระบุวัน เวลา และสถานที่ประเมินผลการสอน")
                .details(rows("ผู้ขอรับการประเมิน", applicantName,
                        "รหัสคำร้อง", requestCode,
                        "สิ่งที่แนบมาด้วย", "หนังสือเชิญเป็นกรรมการผู้ทรงคุณวุฒิ (ไฟล์แนบ)"))
                .html(list.toString());
        if (!files.isEmpty()) {
            letter.button("เปิดดูเอกสารของผู้ขอรับการประเมิน", filesUrl)
                    .para("การเปิดดูเอกสาร ท่านต้องเข้าสู่ระบบด้วยอีเมลที่ได้รับหนังสือฉบับนี้");
        }
        String body = letter
                .steps("สิ่งที่วิทยาลัยฯ ขอความอนุเคราะห์จากท่าน", java.util.List.of(
                        "ศึกษาเอกสารประกอบการประเมินของผู้ขอรับการประเมิน",
                        "ร่วมประเมินผลการสอนตามวัน เวลา และสถานที่ที่ระบุในหนังสือเชิญ",
                        "ภายหลังการประเมิน ลงนามในแบบประเมินผลการสอนผ่านระบบ โดยระบบจะส่งอีเมลแจ้งท่านเมื่อถึงลำดับการลงนาม"))
                .close("จึงเรียนมาเพื่อโปรดพิจารณา และขอขอบคุณมา ณ โอกาสนี้");
        return wrapLayout("ขอเรียนเชิญเป็นกรรมการผู้ทรงคุณวุฒิ", "ประเมินผลการสอน", body);
    }

    /** ผลการประเมินผลการสอนใกล้หมดอายุ */
    public static String buildEvaluationExpiryEmail(String recipientName, String requestCode, String alertLabel,
            long daysLeft, String formattedExpiryDate) {
        String body = Letter.of("ผลการประเมินผลการสอนใกล้หมดอายุ (รหัส " + requestCode + ")", recipientName)
                .para("ผลการประเมินผลการสอนของท่าน ซึ่งใช้ประกอบการขอกำหนดตำแหน่งทางวิชาการ จะหมดอายุในอีก "
                        + daysLeft + " วัน รายละเอียดดังนี้")
                .details(rows("รหัสคำร้อง", requestCode,
                        "วันหมดอายุ", formattedExpiryDate,
                        "ระยะเวลาคงเหลือ", daysLeft + " วัน (แจ้งเตือนล่วงหน้า " + alertLabel + ")"))
                .para("หากท่านประสงค์จะขอกำหนดตำแหน่งทางวิชาการโดยใช้ผลการประเมินนี้"
                        + " กรุณายื่นคำขอผ่านระบบก่อนวันหมดอายุ หากเลยกำหนดจะต้องขอรับการประเมินผลการสอนใหม่")
                .close("จึงเรียนมาเพื่อโปรดทราบ");
        return wrapLayout("ผลการประเมินผลการสอนใกล้หมดอายุ", "คงเหลือ " + daysLeft + " วัน", body);
    }

    /** เหตุขัดข้องของระบบ ถึงผู้ดูแลระบบ */
    public static String buildSystemAlertEmail(String adminName, String heading, String levelColor,
            String levelIcon, String source, String detail, String timestamp) {
        String body = Letter.of("แจ้งเหตุขัดข้องของระบบ: " + heading, adminName)
                .para("ระบบพัฒนาบุคลากร วิทยาลัยการคอมพิวเตอร์ ตรวจพบเหตุการณ์ที่ควรได้รับการตรวจสอบ รายละเอียดดังนี้")
                .details(rows("เหตุการณ์", (levelIcon != null && !levelIcon.isBlank() ? levelIcon + " " : "") + heading,
                        "ส่วนของระบบ", source,
                        "เวลา", timestamp))
                .html("<div style='background:#ffffff;border:1px solid #cbd5e1;padding:12px;border-radius:6px;font-size:13px;color:#334155;white-space:pre-wrap;font-family:monospace;margin:0 0 16px 0;'>"
                        + escapeHtml(detail) + "</div>")
                .para("ขอให้ตรวจสอบสาเหตุและดำเนินการแก้ไขตามความเหมาะสม")
                .close("จึงเรียนมาเพื่อโปรดดำเนินการ");
        return wrapLayout("แจ้งเหตุขัดข้องของระบบ", source, body);
    }

    /**
     * รายงานปัญหาหรือข้อเสนอแนะจากผู้ใช้งาน
     *
     * @param imageCount จำนวนภาพที่แนบมากับอีเมล
     */
    public static String buildFeedbackEmail(String senderName, String senderEmail,
            String category, String subject, String detail, int imageCount) {
        String body = Letter.of(category + ": " + subject, "ผู้ดูแลระบบ")
                .para(senderName + " ได้แจ้ง" + category + "เกี่ยวกับระบบพัฒนาบุคลากร วิทยาลัยการคอมพิวเตอร์ รายละเอียดดังนี้")
                .details(rows("ผู้แจ้ง", senderName,
                        "อีเมลผู้แจ้ง", senderEmail,
                        "ประเภท", category,
                        "หัวข้อ", subject,
                        "ภาพประกอบ", imageCount > 0 ? imageCount + " ไฟล์ (แนบท้ายอีเมลนี้)" : null))
                .note("info", "รายละเอียด", detail == null ? "" : detail)
                .para("ขอให้พิจารณาดำเนินการ ท่านสามารถตอบกลับอีเมลฉบับนี้เพื่อติดต่อผู้แจ้งได้โดยตรง")
                .close("จึงเรียนมาเพื่อโปรดพิจารณา");
        return wrapLayout("รายงานปัญหา/ข้อเสนอแนะ", category, body);
    }

    /** ทดสอบการส่งอีเมลของระบบ */
    public static String buildDiagnosticTestEmail(String adminName, String targetEmail, String serverIp, String smtpHost, String timestamp) {
        String body = Letter.of("ทดสอบการส่งอีเมลของระบบ", adminName == null ? "ผู้ดูแลระบบ" : adminName)
                .para("อีเมลฉบับนี้ส่งเพื่อทดสอบการส่งอีเมลของระบบพัฒนาบุคลากร วิทยาลัยการคอมพิวเตอร์"
                        + " ผ่าน KKU SMTP Relay การที่ท่านได้รับอีเมลนี้แสดงว่าการตั้งค่าการส่งอีเมลทำงานได้ตามปกติ")
                .details(rows("เซิร์ฟเวอร์ SMTP", smtpHost,
                        "เซิร์ฟเวอร์ต้นทาง", serverIp,
                        "ผู้ส่ง", DEFAULT_SENDER_EMAIL,
                        "ผู้รับ", targetEmail,
                        "เวลาที่ส่ง", timestamp + " (เวลาประเทศไทย)"))
                .close("จึงเรียนมาเพื่อโปรดทราบ");
        return wrapLayout("ทดสอบการส่งอีเมล", "ตรวจสอบระบบ", body);
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
