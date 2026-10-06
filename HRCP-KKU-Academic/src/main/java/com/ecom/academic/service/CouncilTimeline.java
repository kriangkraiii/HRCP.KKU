package com.ecom.academic.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.PositionStatusHistory;
import com.ecom.util.WorkingDays;

/**
 * วันที่ที่มีผลทางกฎหมายของคำร้องขอตำแหน่งหลังกรรมการประจำวิทยาลัยฯ มีมติ
 *
 * <ul>
 * <li><b>วันที่สภามหาวิทยาลัยรับเรื่อง</b> (ประกาศ มข. 1670/2569 ข้อ 6 (1)–(2)) — วันมติเห็นชอบ หรือถ้ามติให้แก้ไข
 * คือวันที่ได้รับเอกสารแก้ไขครบตามมติ โดยส่วนงานต้องส่งเรื่องให้มหาวิทยาลัยภายใน 3 วันทำการนับถัดจากวันนั้น
 * ส่งช้ากว่านั้น วันรับเรื่องจะเลื่อนไปเป็นวันที่กองทรัพยากรบุคคลได้รับเรื่องครบ — ซึ่งกำหนดวันแต่งตั้งที่เร็วที่สุด</li>
 * <li><b>ขอทบทวนผล</b> (ข้อบังคับ มข. พ.ศ. 2569 ข้อ 35) — ไม่เกิน 2 ครั้ง ภายใน 90 วันนับตั้งแต่วันที่รับทราบมติ</li>
 * </ul>
 */
public final class CouncilTimeline {

    public static final int SEND_WORKING_DAYS = 3;
    public static final int APPEAL_DAYS = 90;
    public static final int MAX_APPEALS = 2;

    private CouncilTimeline() {
    }

    /** วันที่สภามหาวิทยาลัยรับเรื่อง ถ้าส่งเรื่องทันกำหนด — null ถ้ายังไม่ได้บันทึกมติ */
    public static LocalDate receiptDate(PositionRequest request) {
        return request.getCorrectionsReceivedDate() != null ? request.getCorrectionsReceivedDate()
                : request.getCollegeResolutionDate();
    }

    /** วันสุดท้ายที่ต้องส่งเรื่องให้กองทรัพยากรบุคคล */
    public static LocalDate sendDeadline(PositionRequest request) {
        return WorkingDays.plus(receiptDate(request), SEND_WORKING_DAYS);
    }

    /** วันที่ส่งออกกองทรัพยากรบุคคล (ครั้งล่าสุด) — null ถ้ายังไม่ส่ง */
    public static LocalDate sentOn(List<PositionStatusHistory> history) {
        return history.stream()
                .filter(h -> h.getNewStatus() == PositionRequestStatus.SENT_TO_HR && h.getChangedAt() != null)
                .map(h -> h.getChangedAt().toLocalDate())
                .max(LocalDate::compareTo).orElse(null);
    }

    /** ส่งเรื่อง (หรือยังไม่ส่งทั้งที่) เกินกำหนด 3 วันทำการแล้ว */
    public static boolean sentLate(PositionRequest request, List<PositionStatusHistory> history, LocalDate today) {
        LocalDate deadline = sendDeadline(request);
        if (deadline == null) {
            return false;
        }
        LocalDate sent = sentOn(history);
        return (sent != null ? sent : today).isAfter(deadline);
    }

    /**
     * งานวิจัยหรือบทความทางวิชาการในเอกสารที่ 7 ที่มีหนังสือตอบรับแต่ยังไม่ได้ตีพิมพ์ — ประกาศ มข. 1670/2569 ข้อ 6 (6)
     * ให้วันที่สภามหาวิทยาลัยรับเรื่องเป็นวันที่ได้รับฉบับตีพิมพ์ เว้นแต่ ก.พ.ว. เห็นว่าผลงานที่ตีพิมพ์แล้วผ่านเกณฑ์เพียงพอ
     * ตำรา/หนังสือไม่อยู่ในข้อนี้
     */
    public static int unpublishedAcceptedWorks(Map<String, String> doc7) {
        if (doc7 == null) {
            return 0;
        }
        int count = 0;
        for (Map.Entry<String, String> e : doc7.entrySet()) {
            Matcher m = ACCEPTED_FIELD.matcher(e.getKey());
            if (!m.matches() || !TICK.equals(e.getValue())) {
                continue;
            }
            String publishedKey = (m.group(1) == null ? "is_published_" : "article_is_published_") + m.group(3);
            if (!TICK.equals(doc7.get(publishedKey))) {
                count++;
            }
        }
        return count;
    }

    private static final String TICK = "☑";

    /** งานวิจัย: pending_letter_N, บทความ: article_accept_letter_N / article_is_pending_N */
    private static final Pattern ACCEPTED_FIELD = Pattern
            .compile("^(?:(article_)(accept_letter|is_pending)|pending_letter)_(\\d+)$");

    public static int appealsUsed(List<PositionStatusHistory> history) {
        return (int) history.stream().filter(h -> h.getNewStatus() == PositionRequestStatus.APPEAL_SUBMITTED).count();
    }

    /** วันสุดท้ายที่ยื่นขอทบทวนได้ — null ถ้ายังไม่ได้บันทึกวันรับทราบมติ */
    public static LocalDate appealDeadline(PositionRequest request) {
        return request.getCouncilAcknowledgedDate() == null ? null
                : request.getCouncilAcknowledgedDate().plusDays(APPEAL_DAYS);
    }

    /** เหตุที่ยื่นขอทบทวนไม่ได้ หรือ {@code null} เมื่อยื่นได้ */
    public static String appealProblem(PositionRequest request, List<PositionStatusHistory> history, LocalDate today) {
        if (request.getCurrentStatus() != PositionRequestStatus.COUNCIL_REJECTED) {
            return "ขอทบทวนได้เฉพาะคำร้องที่สภามหาวิทยาลัยมีมติไม่กำหนดตำแหน่ง";
        }
        if (appealsUsed(history) >= MAX_APPEALS) {
            return "ขอทบทวนครบ " + MAX_APPEALS + " ครั้งแล้ว (ข้อบังคับ มข. พ.ศ. 2569 ข้อ 35)";
        }
        LocalDate deadline = appealDeadline(request);
        if (deadline == null) {
            return "ยังไม่ได้บันทึกวันที่ผู้ขอรับทราบมติ จึงนับกำหนด 90 วันไม่ได้";
        }
        if (today.isAfter(deadline)) {
            return "พ้นกำหนดขอทบทวนแล้ว (ภายใน " + APPEAL_DAYS + " วันนับตั้งแต่วันที่รับทราบมติ)";
        }
        return null;
    }
}
