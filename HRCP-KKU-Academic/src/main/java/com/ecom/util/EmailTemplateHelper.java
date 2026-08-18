package com.ecom.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Utility to generate modern, responsive, and official HTML email templates
 * for Khon Kaen University - College of Computing (วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น).
 * Integrated with official KKU emblem & College of Computing branding.
 */
public final class EmailTemplateHelper {

    private EmailTemplateHelper() {
    }

    public static final String SENDER_NAME = "วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น";
    public static final String SENDER_SYSTEM_NAME = "ระบบตำแหน่งทางวิชาการ วิทยาลัยการคอมพิวเตอร์ มข.";

    public static final String CID_KKU_LOGO = "cid:kku_logo";
    public static final String CID_CP_LOGO = "cid:cp_logo";

    /**
     * Attaches the bundled KKU Emblem and College of Computing logo as inline CID resources.
     */
    public static void attachLogos(MimeMessageHelper helper) {
        try {
            helper.addInline("kku_logo", new ClassPathResource("static/img/kku_logo.png"), "image/png");
            helper.addInline("cp_logo", new ClassPathResource("static/img/cp_logo.png"), "image/png");
        } catch (Exception e) {
            // Non-fatal if inline attachment fails
        }
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
        
        // --- Top White Branding Bar (Official Logos) ---
        sb.append("<tr><td style='background-color:#ffffff;padding:18px 24px;border-bottom:1px solid #e2e8f0;text-align:center;'>");
        sb.append("<table border='0' cellspacing='0' cellpadding='0' style='margin:0 auto;'><tr>");
        sb.append("<td align='center' style='padding:0 16px;vertical-align:middle;'>");
        sb.append("<img src='").append(CID_KKU_LOGO).append("' alt='มหาวิทยาลัยขอนแก่น' height='46' style='display:block;height:46px;width:auto;border:0;' />");
        sb.append("</td>");
        sb.append("<td style='width:1px;background-color:#cbd5e1;height:36px;vertical-align:middle;'></td>");
        sb.append("<td align='center' style='padding:0 16px;vertical-align:middle;'>");
        sb.append("<img src='").append(CID_CP_LOGO).append("' alt='วิทยาลัยการคอมพิวเตอร์ มข.' height='44' style='display:block;height:44px;width:auto;border:0;' />");
        sb.append("</td>");
        sb.append("</tr></table>");
        sb.append("</td></tr>");

        // --- Official Hero Header ---
        sb.append("<tr><td style='background:linear-gradient(135deg,#0b1f44 0%,#172554 50%,#1e3a8a 100%);padding:26px 24px;text-align:center;border-bottom:4px solid #d97706;'>");
        sb.append("<div style='color:#cbd5e1;font-size:12px;font-weight:600;letter-spacing:1px;text-transform:uppercase;margin-bottom:6px;'>ระบบบริหารจัดการตำแหน่งทางวิชาการ (HRCP.KKU)</div>");
        sb.append("<h1 style='color:#ffffff;margin:0 0 6px 0;font-size:20px;font-weight:700;line-height:1.4;'>").append(escapeHtml(headingTitle)).append("</h1>");
        if (badgeText != null && !badgeText.isBlank()) {
            sb.append("<div style='display:inline-block;background:rgba(217,119,6,0.22);border:1px solid #d97706;color:#fef3c7;font-size:12px;font-weight:500;padding:3px 12px;border-radius:20px;margin-top:4px;'>")
              .append(escapeHtml(badgeText)).append("</div>");
        }
        sb.append("<div style='color:#94a3b8;font-size:12px;margin-top:10px;'>วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น &bull; College of Computing, KKU</div>");
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

    /**
     * Builds OTP verification email for 2FA login or email verification.
     */
    public static String buildOtpEmail(String name, String otp, String purposeText, int expiryMinutes) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p style='font-size:16px;color:#1e293b;margin:0 0 12px 0;'>เรียน <strong>")
          .append(escapeHtml(name != null ? name : "ผู้ใช้งาน")).append("</strong>,</p>");
        sb.append("<p style='font-size:14px;color:#475569;margin:0 0 20px 0;'>ท่านได้ส่งคำขอรหัสยืนยันตัวตน (OTP) สำหรับ <strong>")
          .append(escapeHtml(purposeText)).append("</strong> ในระบบตำแหน่งทางวิชาการ</p>");

        sb.append("<div style='text-align:center;margin:28px 0;'>");
        sb.append("<div style='display:inline-block;background:#f0f7ff;border:2px dashed #2563eb;border-radius:12px;padding:16px 36px;'>");
        sb.append("<div style='font-size:12px;color:#64748b;text-transform:uppercase;letter-spacing:1px;margin-bottom:4px;'>รหัส OTP ของท่าน</div>");
        sb.append("<span style='font-size:34px;font-weight:700;color:#1e3a8a;letter-spacing:6px;font-family:monospace;'>")
          .append(escapeHtml(otp)).append("</span>");
        sb.append("</div>");
        sb.append("</div>");

        sb.append("<div style='background:#fffbeb;border:1px solid #fef3c7;border-left:4px solid #f59e0b;padding:12px 16px;border-radius:6px;margin:20px 0;font-size:13px;color:#92400e;'>");
        sb.append("⏱ รหัสนี้มีอายุการใช้งาน <strong>").append(expiryMinutes).append(" นาที</strong> เพื่อความปลอดภัย กรุณาอย่าเปิดเผยรหัสนี้แก่ผู้อื่น");
        sb.append("</div>");

        sb.append("<p style='font-size:12px;color:#94a3b8;margin:20px 0 0 0;'>หากท่านไม่ได้เป็นผู้ทำรายการดังกล่าว กรุณาเพิกเฉยต่ออีเมลฉบับนี้ หรือติดต่อผู้ดูแลระบบทันที</p>");

        return wrapLayout("รหัส OTP ยืนยันตัวตน", purposeText, sb.toString());
    }

    /**
     * Builds Password Reset email.
     */
    public static String buildPasswordResetEmail(String resetUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p style='font-size:16px;color:#1e293b;margin:0 0 12px 0;'>เรียน <strong>ผู้ใช้งานระบบ</strong>,</p>");
        sb.append("<p style='font-size:14px;color:#475569;margin:0 0 20px 0;'>ระบบได้รับคำขอตั้งค่ารหัสผ่านใหม่สำหรับบัญชีของท่าน กรุณาคลิกปุ่มด้านล่างเพื่อดำเนินการตั้งรหัสผ่านใหม่:</p>");

        sb.append("<div style='text-align:center;margin:30px 0;'>");
        sb.append("<a href='").append(escapeHtml(resetUrl)).append("' target='_blank' style='background:linear-gradient(135deg,#1e3a8a,#2563eb);color:#ffffff;text-decoration:none;padding:14px 32px;font-size:15px;font-weight:600;border-radius:8px;display:inline-block;box-shadow:0 4px 12px rgba(37,99,235,0.25);'>ตั้งรหัสผ่านใหม่</a>");
        sb.append("</div>");

        sb.append("<div style='background:#f8fafc;border:1px solid #e2e8f0;padding:12px 16px;border-radius:6px;font-size:12px;color:#64748b;word-break:break-all;'>");
        sb.append("หากปุ่มด้านบนไม่ทำงาน สามารถคัดลอกลิงก์นี้ไปเปิดในเบราว์เซอร์ได้:<br>");
        sb.append("<a href='").append(escapeHtml(resetUrl)).append("' style='color:#2563eb;'>").append(escapeHtml(resetUrl)).append("</a>");
        sb.append("</div>");

        sb.append("<p style='font-size:12px;color:#94a3b8;margin:20px 0 0 0;'>หากท่านไม่ได้ร้องขอการรีเซ็ตรหัสผ่าน บัญชีของท่านยังคงปลอดภัยและสามารถเพิกเฉยต่ออีเมลฉบับนี้ได้</p>");

        return wrapLayout("รีเซ็ตรหัสผ่าน", "ความปลอดภัยของบัญชี", sb.toString());
    }

    /**
     * Builds Status Update email for Teaching Evaluation or Academic Position requests.
     */
    public static String buildStatusChangeEmail(String applicantName, String requestType, String requestCode,
            String oldStatusLabel, String newStatusLabel, String statusColor, String extraDetailsHtml) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p style='font-size:16px;color:#1e293b;margin:0 0 12px 0;'>เรียน <strong>")
          .append(escapeHtml(applicantName)).append("</strong>,</p>");
        sb.append("<p style='font-size:14px;color:#475569;margin:0 0 20px 0;'>คำร้องของท่านในระบบบริหารตำแหน่งทางวิชาการ ได้รับการอัปเดตสถานะเรียบร้อยแล้ว ดังมีรายละเอียดต่อไปนี้:</p>");

        sb.append("<div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:18px 20px;margin-bottom:20px;'>");
        sb.append("<table width='100%' border='0' cellspacing='0' cellpadding='6' style='font-size:14px;'>");
        sb.append("<tr><td width='35%' style='color:#64748b;font-weight:600;'>ประเภทคำร้อง:</td><td style='color:#1e293b;font-weight:600;'>").append(escapeHtml(requestType)).append("</td></tr>");
        sb.append("<tr><td style='color:#64748b;font-weight:600;'>รหัสคำร้อง:</td><td style='color:#1e293b;font-weight:600;'>#").append(escapeHtml(requestCode)).append("</td></tr>");
        if (oldStatusLabel != null && !oldStatusLabel.isBlank() && !"-".equals(oldStatusLabel)) {
            sb.append("<tr><td style='color:#64748b;font-weight:600;'>สถานะเดิม:</td><td style='color:#64748b;'>").append(escapeHtml(oldStatusLabel)).append("</td></tr>");
        }
        sb.append("<tr><td style='color:#64748b;font-weight:600;'>สถานะปัจจุบัน:</td><td><span style='background-color:").append(statusColor != null ? statusColor : "#1e3a8a").append(";color:#ffffff;font-size:13px;font-weight:600;padding:3px 12px;border-radius:20px;display:inline-block;'>").append(escapeHtml(newStatusLabel)).append("</span></td></tr>");
        sb.append("</table>");
        sb.append("</div>");

        if (extraDetailsHtml != null && !extraDetailsHtml.isBlank()) {
            sb.append(extraDetailsHtml);
        }

        sb.append("<p style='font-size:14px;color:#475569;margin:20px 0 0 0;'>ท่านสามารถเข้าสู่ระบบเพื่อตรวจสอบรายละเอียดเอกสารหรือดำเนินการในขั้นตอนต่อไป</p>");

        return wrapLayout("แจ้งเตือนสถานะคำร้อง", requestType, sb.toString());
    }

    /**
     * Builds Admin Alert for a newly submitted request.
     */
    public static String buildAdminNewRequestEmail(String adminName, String applicantName, String applicantEmail,
            String requestType, String requestCode, String targetPosition) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p style='font-size:16px;color:#1e293b;margin:0 0 12px 0;'>เรียน <strong>")
          .append(escapeHtml(adminName)).append("</strong> (ผู้ดูแลระบบ),</p>");
        sb.append("<p style='font-size:14px;color:#475569;margin:0 0 20px 0;'>มีคำร้องใหม่ยื่นเข้ามาในระบบ กรุณาเข้าตรวจสอบและดำเนินการ:</p>");

        sb.append("<div style='background:#f8fafc;border:1px solid #e2e8f0;border-left:4px solid #1e3a8a;border-radius:8px;padding:16px 20px;margin-bottom:20px;'>");
        sb.append("<table width='100%' border='0' cellspacing='0' cellpadding='5' style='font-size:14px;'>");
        sb.append("<tr><td width='35%' style='color:#64748b;font-weight:600;'>ผู้ยื่นคำร้อง:</td><td style='color:#1e293b;font-weight:600;'>").append(escapeHtml(applicantName)).append("</td></tr>");
        sb.append("<tr><td style='color:#64748b;font-weight:600;'>อีเมล:</td><td style='color:#2563eb;'>").append(escapeHtml(applicantEmail)).append("</td></tr>");
        sb.append("<tr><td style='color:#64748b;font-weight:600;'>ประเภทคำร้อง:</td><td style='color:#1e293b;'>").append(escapeHtml(requestType)).append("</td></tr>");
        sb.append("<tr><td style='color:#64748b;font-weight:600;'>รหัสคำร้อง:</td><td style='color:#1e293b;font-weight:600;'>#").append(escapeHtml(requestCode)).append("</td></tr>");
        if (targetPosition != null && !targetPosition.isBlank()) {
            sb.append("<tr><td style='color:#64748b;font-weight:600;'>ตำแหน่งที่ขอ:</td><td style='color:#d97706;font-weight:600;'>").append(escapeHtml(targetPosition)).append("</td></tr>");
        }
        sb.append("</table>");
        sb.append("</div>");

        sb.append("<p style='font-size:13px;color:#64748b;'>กรุณาเข้าสู่ระบบผู้ดูแลเพื่อตรวจสอบเอกสารและพิจารณาดำเนินการ</p>");

        return wrapLayout("มีคำร้องใหม่ในระบบ", "แจ้งเตือนเจ้าหน้าที่", sb.toString());
    }

    /**
     * Builds Subcommittee Suggestion / Revision Request email.
     */
    public static String buildSuggestionEmail(String applicantName, String requestCode, String suggestionsText) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p style='font-size:16px;color:#1e293b;margin:0 0 12px 0;'>เรียน <strong>")
          .append(escapeHtml(applicantName)).append("</strong>,</p>");
        sb.append("<p style='font-size:14px;color:#475569;margin:0 0 16px 0;'>คณะอนุกรรมการประเมินผลการสอนได้ให้ข้อเสนอแนะเกี่ยวกับคำร้องหมายเลข <strong>#")
          .append(escapeHtml(requestCode)).append("</strong> ของท่าน โดยมีรายละเอียดดังต่อไปนี้:</p>");

        sb.append("<div style='background:#fffbeb;border:1px solid #fef3c7;border-left:4px solid #d97706;padding:16px 20px;border-radius:8px;margin:20px 0;'>");
        sb.append("<div style='font-weight:700;color:#92400e;margin-bottom:8px;font-size:14px;'>📝 ข้อเสนอแนะจากคณะอนุกรรมการ:</div>");
        sb.append("<div style='color:#1e293b;font-size:14px;white-space:pre-wrap;line-height:1.6;'>")
          .append(escapeHtml(suggestionsText != null ? suggestionsText : "")).append("</div>");
        sb.append("</div>");

        sb.append("<div style='background:#fef2f2;border:1px solid #fee2e2;border-radius:8px;padding:12px 16px;margin-bottom:20px;color:#991b1b;font-size:13px;font-weight:500;'>");
        sb.append("⚠️ กรุณาเข้าสู่ระบบเพื่อแก้ไขเอกสารตามข้อเสนอแนะและยื่นเอกสารฉบับปรับปรุง");
        sb.append("</div>");

        return wrapLayout("ข้อเสนอแนะจากคณะอนุกรรมการ", "กรุณาแก้ไขเอกสาร", sb.toString());
    }

    /**
     * Builds Teaching Evaluation Expiry Reminder email.
     */
    public static String buildEvaluationExpiryEmail(String recipientName, String requestCode, String alertLabel,
            long daysLeft, String formattedExpiryDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p style='font-size:16px;color:#1e293b;margin:0 0 12px 0;'>เรียน <strong>")
          .append(escapeHtml(recipientName)).append("</strong>,</p>");
        sb.append("<p style='font-size:14px;color:#475569;margin:0 0 20px 0;'>ระบบขอแจ้งเตือนวันหมดอายุของผลการประเมินผลการสอนของท่าน เพื่อให้ท่านสามารถดำเนินการยื่นคำขอตำแหน่งทางวิชาการได้ทันตามกำหนดเวลา:</p>");

        sb.append("<div style='background:#fffbeb;border:1px solid #fef3c7;border-left:4px solid #f59e0b;padding:18px 20px;border-radius:8px;margin:20px 0;'>");
        sb.append("<table width='100%' border='0' cellspacing='0' cellpadding='5' style='font-size:14px;'>");
        sb.append("<tr><td width='35%' style='color:#92400e;font-weight:600;'>รหัสคำร้อง:</td><td style='color:#1e293b;font-weight:600;'>#").append(escapeHtml(requestCode)).append("</td></tr>");
        sb.append("<tr><td style='color:#92400e;font-weight:600;'>ระยะเวลาคงเหลือ:</td><td style='color:#dc2626;font-weight:700;font-size:16px;'>").append(daysLeft).append(" วัน (ภายใน ").append(escapeHtml(alertLabel)).append(")</td></tr>");
        sb.append("<tr><td style='color:#92400e;font-weight:600;'>วันหมดอายุ:</td><td style='color:#1e293b;font-weight:600;'>").append(escapeHtml(formattedExpiryDate)).append("</td></tr>");
        sb.append("</table>");
        sb.append("</div>");

        sb.append("<p style='font-size:14px;color:#475569;'>หากท่านประสงค์จะยื่นคำขอตำแหน่งทางวิชาการ กรุณาดำเนินการก่อนที่ผลการประเมินการสอนจะหมดอายุ</p>");

        return wrapLayout("แจ้งเตือนวันหมดอายุผลประเมินการสอน", "ระยะเวลาคงเหลือ " + daysLeft + " วัน", sb.toString());
    }

    /**
     * Builds System Alert Email for Admins.
     */
    public static String buildSystemAlertEmail(String adminName, String heading, String levelColor,
            String levelIcon, String source, String detail, String timestamp) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p style='font-size:15px;color:#1e293b;margin:0 0 12px 0;'>เรียน <strong>")
          .append(escapeHtml(adminName)).append("</strong> (ผู้ดูแลระบบ),</p>");
        sb.append("<p style='font-size:14px;color:#475569;margin:0 0 16px 0;'>ระบบตรวจพบเหตุการณ์แจ้งเตือนสถานะการทำงาน ดังนี้:</p>");

        sb.append("<div style='background:#f8fafc;border:1px solid #e2e8f0;border-left:4px solid ").append(levelColor).append(";padding:16px 20px;border-radius:8px;margin-bottom:20px;'>");
        sb.append("<div style='font-weight:700;color:#1e293b;font-size:15px;margin-bottom:6px;'>").append(levelIcon).append(" ").append(escapeHtml(heading)).append("</div>");
        sb.append("<div style='font-size:13px;color:#64748b;margin-bottom:8px;'>แหล่งที่มา: <strong>").append(escapeHtml(source)).append("</strong> | เวลา: ").append(escapeHtml(timestamp)).append("</div>");
        sb.append("<div style='background:#ffffff;border:1px solid #cbd5e1;padding:12px;border-radius:6px;font-size:13px;color:#334155;white-space:pre-wrap;font-family:monospace;'>")
          .append(escapeHtml(detail)).append("</div>");
        sb.append("</div>");

        return wrapLayout("แจ้งเตือนระบบ: " + heading, source, sb.toString());
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
