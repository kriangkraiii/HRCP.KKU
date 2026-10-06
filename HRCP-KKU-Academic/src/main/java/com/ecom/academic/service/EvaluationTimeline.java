package com.ecom.academic.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.RequestStatusHistory;
import com.ecom.util.WorkingDays;

/**
 * กำหนดเวลาของการประเมินผลการสอนตามประกาศ มข. ฉบับที่ 1669/2569 ข้อ 10.3 คำนวณจากประวัติสถานะ
 *
 * <ul>
 * <li>คณะกรรมการประเมินและรายงานผลภายใน 45 วันทำการนับจากวันที่แต่งตั้ง</li>
 * <li>ส่วนงานแจ้งผลให้ผู้ขอทราบภายใน 7 วันทำการหลังรับรองผล</li>
 * <li>ผู้ขอที่ไม่ผ่านขอทบทวนต่อหัวหน้าส่วนงานได้ภายใน 30 วันทำการนับจากวันที่ทราบผล</li>
 * </ul>
 *
 * วันทำการนับตาม {@link WorkingDays} (จันทร์–ศุกร์ ไม่หักวันหยุดราชการ)
 */
public final class EvaluationTimeline {

    public static final int COMMITTEE_WORKING_DAYS = 45;
    public static final int NOTIFY_WORKING_DAYS = 7;
    public static final int APPEAL_WORKING_DAYS = 30;

    private static final Set<RequestStatus> RESULTS = Set.of(RequestStatus.COMPLETED_PASS,
            RequestStatus.COMPLETED_REVISE, RequestStatus.SUBCOMMITTEE_FAIL, RequestStatus.COMPLETED_FAIL);

    /**
     * @param label  ชื่อกำหนดเวลา
     * @param from   วันเริ่มนับ (นับวันทำการแรกเป็นวันถัดไป)
     * @param due    วันครบกำหนด
     * @param metOn  วันที่ทำสำเร็จ หรือ {@code null} ถ้ายังไม่ได้ทำ
     */
    public record Deadline(String label, LocalDate from, LocalDate due, LocalDate metOn) {

        public boolean overdue(LocalDate today) {
            return metOn == null ? today.isAfter(due) : metOn.isAfter(due);
        }

        public boolean met() {
            return metOn != null;
        }
    }

    private EvaluationTimeline() {
    }

    /** กำหนดเวลาทั้งหมดที่เริ่มนับแล้ว เรียงตามลำดับในกระบวนการ */
    public static List<Deadline> of(List<RequestStatusHistory> history) {
        List<RequestStatusHistory> ordered = ascending(history);
        List<Deadline> deadlines = new ArrayList<>();
        add(deadlines, ordered, "คณะกรรมการประเมินและรายงานผล (" + COMMITTEE_WORKING_DAYS + " วันทำการนับจากวันแต่งตั้ง)",
                Set.of(RequestStatus.SUB_COMMITTEE_APPOINTED), RESULTS, COMMITTEE_WORKING_DAYS);
        add(deadlines, ordered, "แจ้งผลให้ผู้ขอทราบ (" + NOTIFY_WORKING_DAYS + " วันทำการหลังรับรองผล)",
                Set.of(RequestStatus.COLLEGE_ENDORSED, RequestStatus.COLLEGE_ENDORSED_FAIL),
                Set.of(RequestStatus.COMPLETED, RequestStatus.COMPLETED_FAIL), NOTIFY_WORKING_DAYS);
        add(deadlines, ordered, "ผู้ขอยื่นขอทบทวนผล (" + APPEAL_WORKING_DAYS + " วันทำการนับจากวันที่ทราบผล)",
                Set.of(RequestStatus.COMPLETED_FAIL), Set.of(RequestStatus.APPEAL_SUBMITTED), APPEAL_WORKING_DAYS);
        return deadlines;
    }

    /** วันสุดท้ายที่ขอทบทวนผลที่ไม่ผ่านได้ — {@code null} ถ้ายังไม่เคยแจ้งผลไม่ผ่าน */
    public static LocalDate appealDeadline(List<RequestStatusHistory> history) {
        LocalDate failed = lastEntered(ascending(history), Set.of(RequestStatus.COMPLETED_FAIL));
        return WorkingDays.plus(failed, APPEAL_WORKING_DAYS);
    }

    /** เคยขอทบทวนแล้วหรือยัง — ขอได้ครั้งเดียวต่อคำร้อง */
    public static boolean alreadyAppealed(List<RequestStatusHistory> history) {
        return history.stream().anyMatch(h -> h.getNewStatus() == RequestStatus.APPEAL_SUBMITTED);
    }

    private static void add(List<Deadline> out, List<RequestStatusHistory> ordered, String label,
            Set<RequestStatus> starts, Set<RequestStatus> ends, int workingDays) {
        LocalDate from = lastEntered(ordered, starts);
        if (from == null) {
            return;
        }
        LocalDate metOn = ordered.stream()
                .filter(h -> ends.contains(h.getNewStatus()) && !h.getChangedAt().toLocalDate().isBefore(from))
                .map(h -> h.getChangedAt().toLocalDate())
                .findFirst().orElse(null);
        out.add(new Deadline(label, from, WorkingDays.plus(from, workingDays), metOn));
    }

    private static LocalDate lastEntered(List<RequestStatusHistory> ordered, Set<RequestStatus> statuses) {
        LocalDate last = null;
        for (RequestStatusHistory h : ordered) {
            if (statuses.contains(h.getNewStatus()) && h.getNewStatus() != h.getOldStatus()) {
                last = h.getChangedAt().toLocalDate();
            }
        }
        return last;
    }

    private static List<RequestStatusHistory> ascending(List<RequestStatusHistory> history) {
        return history.stream()
                .filter(h -> h.getChangedAt() != null && h.getNewStatus() != null)
                .sorted(Comparator.comparing(RequestStatusHistory::getChangedAt))
                .toList();
    }
}
