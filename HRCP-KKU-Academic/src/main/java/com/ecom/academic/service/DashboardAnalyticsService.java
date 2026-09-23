package com.ecom.academic.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.ecom.academic.dto.EvaluationExpiryItem;
import com.ecom.academic.dto.EvaluationSummary;
import com.ecom.academic.dto.FacultyRankStat;
import com.ecom.academic.dto.PendingSignatureItem;
import com.ecom.academic.dto.StalledRequestItem;
import com.ecom.academic.dto.SubjectSubmissionItem;
import com.ecom.academic.model.AcademicRank;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.academic.repository.PositionRequestRepository;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.model.AdminLog;
import com.ecom.repository.AdminLogRepository;
import com.ecom.repository.UserRepository;

/**
 * Aggregates analytics data for the admin dashboard.
 *
 * <p>Each method issues one or two database queries and returns
 * simple structures (maps, lists) that the controller can pass
 * straight to Thymeleaf without further processing.
 */
@Service
public class DashboardAnalyticsService {

    private final AcademicRequestRepository acadRepo;
    private final PositionRequestRepository posRepo;
    private final UserRepository userRepo;
    private final StaffMemberRepository staffRepo;
    private final AdminLogRepository logRepo;
    private final AcademicRequestService acadService;
    private final SignatureStepRepository signatureStepRepo;

    public DashboardAnalyticsService(
            AcademicRequestRepository acadRepo,
            PositionRequestRepository posRepo,
            UserRepository userRepo,
            StaffMemberRepository staffRepo,
            AdminLogRepository logRepo,
            AcademicRequestService acadService,
            SignatureStepRepository signatureStepRepo) {
        this.acadRepo = acadRepo;
        this.posRepo = posRepo;
        this.userRepo = userRepo;
        this.staffRepo = staffRepo;
        this.logRepo = logRepo;
        this.acadService = acadService;
        this.signatureStepRepo = signatureStepRepo;
    }


    // ── KPI Summary ──────────────────────────────────────────────────────

    /** Key performance indicators displayed at the top of the dashboard. */
    public Map<String, Long> getKpiSummary() {
        long totalAcad = acadRepo.count();
        long totalPos = posRepo.count();

        List<RequestStatus> acadTerminal = List.of(RequestStatus.COMPLETED, RequestStatus.COMPLETED_FAIL);
        List<PositionRequestStatus> posTerminal = List.of(PositionRequestStatus.SENT_TO_HR);
        List<RequestStatus> acadDraft = List.of(RequestStatus.DRAFT);
        List<PositionRequestStatus> posDraft = List.of(PositionRequestStatus.DRAFT);

        long completedAcad = acadRepo.countByCurrentStatusIn(acadTerminal);
        long completedPos = posRepo.countByCurrentStatusIn(posTerminal);
        long draftAcad = acadRepo.countByCurrentStatus(RequestStatus.DRAFT);
        long draftPos = posRepo.countByCurrentStatus(PositionRequestStatus.DRAFT);

        long totalRequests = totalAcad + totalPos;
        long activeRequests = totalRequests - (completedAcad + completedPos) - (draftAcad + draftPos);
        long completedRequests = completedAcad + completedPos;
        long totalUsers = userRepo.count();

        long passedEval = acadRepo.countByCurrentStatusIn(List.of(
                RequestStatus.COMPLETED_PASS,
                RequestStatus.COLLEGE_ENDORSED,
                RequestStatus.COMPLETED));
        long sentToHr = posRepo.countByCurrentStatus(PositionRequestStatus.SENT_TO_HR);

        Map<String, Long> kpi = new LinkedHashMap<>();
        kpi.put("totalRequests", totalRequests);
        kpi.put("activeRequests", activeRequests);
        kpi.put("totalUsers", totalUsers);
        kpi.put("completedRequests", completedRequests);
        kpi.put("totalAcad", totalAcad);
        kpi.put("totalPos", totalPos);
        kpi.put("draftRequests", draftAcad + draftPos);
        kpi.put("passedEval", passedEval);
        kpi.put("sentToHr", sentToHr);
        return kpi;
    }

    // ── Monthly Request Trend ────────────────────────────────────────────

    /**
     * Returns month labels and two series (evaluation / position) for the
     * stacked bar chart, covering the last {@code months} calendar months.
     */
    public Map<String, Object> getMonthlyRequestTrend(int months) {
        LocalDateTime since = LocalDateTime.now().minusMonths(months).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        List<Object[]> acadRows = acadRepo.countByMonthSince(since);
        List<Object[]> posRows = posRepo.countByMonthSince(since);

        Map<String, Long> acadMap = rowsToMonthMap(acadRows);
        Map<String, Long> posMap = rowsToMonthMap(posRows);

        List<String> labels = new ArrayList<>();
        List<Long> evalSeries = new ArrayList<>();
        List<Long> posSeries = new ArrayList<>();

        YearMonth current = YearMonth.now();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            String key = ym.getYear() + "-" + String.format("%02d", ym.getMonthValue());
            String label = thaiShortMonth(ym.getMonthValue()) + " " + (ym.getYear() + 543);
            labels.add(label);
            evalSeries.add(acadMap.getOrDefault(key, 0L));
            posSeries.add(posMap.getOrDefault(key, 0L));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("labels", labels);
        result.put("evalSeries", evalSeries);
        result.put("posSeries", posSeries);
        return result;
    }

    // ── Status Distribution ──────────────────────────────────────────────

    /** Groups all requests by high-level status for the doughnut chart. */
    public Map<String, Long> getStatusDistribution() {
        long draft = acadRepo.countByCurrentStatus(RequestStatus.DRAFT)
                   + posRepo.countByCurrentStatus(PositionRequestStatus.DRAFT);

        long active = 0;
        for (RequestStatus s : RequestStatus.values()) {
            if (!s.isTerminal() && !s.isDraft()) {
                active += acadRepo.countByCurrentStatus(s);
            }
        }
        for (PositionRequestStatus s : PositionRequestStatus.values()) {
            if (!s.isTerminal() && !s.isDraft()) {
                active += posRepo.countByCurrentStatus(s);
            }
        }

        long completed = acadRepo.countByCurrentStatus(RequestStatus.COMPLETED)
                       + posRepo.countByCurrentStatus(PositionRequestStatus.SENT_TO_HR);

        long passedOrEndorsed = acadRepo.countByCurrentStatus(RequestStatus.COMPLETED_PASS)
                              + acadRepo.countByCurrentStatus(RequestStatus.COLLEGE_ENDORSED);

        long failed = acadRepo.countByCurrentStatus(RequestStatus.COMPLETED_FAIL);

        long revising = acadRepo.countByCurrentStatus(RequestStatus.COMPLETED_REVISE)
                      + acadRepo.countByCurrentStatus(RequestStatus.REVISION_SUBMITTED)
                      + posRepo.countByCurrentStatus(PositionRequestStatus.REVISION_REQUESTED);

        Map<String, Long> dist = new LinkedHashMap<>();
        dist.put("แบบร่าง", draft);
        dist.put("กำลังดำเนินการ", active - passedOrEndorsed - revising);
        dist.put("รอแก้ไข", revising);
        dist.put("ผ่านการประเมิน", passedOrEndorsed);
        dist.put("เสร็จสิ้น", completed);
        dist.put("ไม่ผ่าน", failed);
        return dist;
    }

    // ── Monthly New Users ────────────────────────────────────────────────

    /** New user registrations per month for the line chart. */
    public Map<String, Object> getMonthlyNewUsers(int months) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -months);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        Date since = cal.getTime();

        List<Object[]> rows = userRepo.countNewUsersByMonth(since);
        Map<String, Long> monthMap = rowsToMonthMap(rows);

        List<String> labels = new ArrayList<>();
        List<Long> series = new ArrayList<>();

        YearMonth current = YearMonth.now();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            String key = ym.getYear() + "-" + String.format("%02d", ym.getMonthValue());
            labels.add(thaiShortMonth(ym.getMonthValue()) + " " + (ym.getYear() + 543));
            series.add(monthMap.getOrDefault(key, 0L));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("labels", labels);
        result.put("series", series);
        return result;
    }

    // ── Academic Rank Distribution ───────────────────────────────────────

    /** Active staff grouped by academic title for the horizontal bar chart. */
    public Map<String, Long> getAcademicRankDistribution() {
        List<Object[]> rows = staffRepo.countByAcademicTitle();
        Map<String, Long> raw = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String title = (String) row[0];
            Long count = (Long) row[1];
            String normalised = normaliseAcademicTitle(title);
            raw.merge(normalised, count, Long::sum);
        }

        // Fixed order: อ. → ผศ. → รศ. → ศ.
        Map<String, Long> ordered = new LinkedHashMap<>();
        ordered.put("อาจารย์", raw.getOrDefault("อาจารย์", 0L));
        ordered.put("ผู้ช่วยศาสตราจารย์", raw.getOrDefault("ผู้ช่วยศาสตราจารย์", 0L));
        ordered.put("รองศาสตราจารย์", raw.getOrDefault("รองศาสตราจารย์", 0L));
        ordered.put("ศาสตราจารย์", raw.getOrDefault("ศาสตราจารย์", 0L));
        return ordered;
    }

    // ── Recent Admin Activity ────────────────────────────────────────────

    /** The most recent admin log entries for the activity table. */
    public List<AdminLog> getRecentAdminActivity(int limit) {
        return logRepo.findAllByOrderByTimestampDesc(PageRequest.of(0, limit)).getContent();
    }

    // ── Evaluation Expiry Analytics ──────────────────────────────────────

    /**
     * Aggregates evaluation expiration data, countdowns, and position request usage.
     */
    public Map<String, Object> getEvaluationExpiryAnalytics(String filter) {
        List<AcademicRequest> requests = acadRepo.findAllNonDraftWithApplicant();
        Set<Long> linkedEvalIds = new HashSet<>(posRepo.findLinkedEvaluationIds());

        List<EvaluationExpiryItem> allItems = new ArrayList<>();
        long totalPassed = 0;
        long activeCount = 0;
        long expiringSoon30Count = 0;
        long expiringSoon90Count = 0;
        long expiringSoon180Count = 0;
        long expiredCount = 0;
        long unusedCount = 0;

        for (AcademicRequest req : requests) {
            if (req.getCurrentStatus() != null && req.getCurrentStatus().carriesAPassedResult()) {
                totalPassed++;
                EvaluationSummary summary = acadService.summarize(req);
                Long daysLeft = summary != null ? summary.daysLeft() : null;
                boolean hasPos = linkedEvalIds.contains(req.getId());
                if (!hasPos) {
                    unusedCount++;
                }

                String category;
                if (daysLeft != null && daysLeft < 0) {
                    category = "EXPIRED";
                    expiredCount++;
                } else if (daysLeft != null && daysLeft <= 30) {
                    category = "EXPIRING_SOON_30";
                    expiringSoon30Count++;
                    expiringSoon90Count++;
                    expiringSoon180Count++;
                } else if (daysLeft != null && daysLeft <= 90) {
                    category = "EXPIRING_SOON_90";
                    expiringSoon90Count++;
                    expiringSoon180Count++;
                } else if (daysLeft != null && daysLeft <= 180) {
                    category = "EXPIRING_SOON_180";
                    expiringSoon180Count++;
                    activeCount++;
                } else {
                    category = "ACTIVE";
                    activeCount++;
                }

                String applicantName = req.getApplicant() != null ? req.getApplicant().getName() : "-";
                String targetRank = (summary != null && summary.targetRank() != null)
                        ? summary.targetRank().thaiLabel()
                        : "-";

                allItems.add(new EvaluationExpiryItem(
                        req.getId(),
                        req.getRequestCode(),
                        applicantName,
                        summary != null ? summary.courseCode() : null,
                        summary != null ? summary.courseName() : null,
                        summary != null ? summary.academicYear() : null,
                        summary != null ? summary.semester() : null,
                        summary != null ? summary.evaluationDate() : null,
                        summary != null ? summary.expiryDate() : null,
                        summary != null ? summary.expiryAt() : null,
                        daysLeft,
                        category,
                        hasPos,
                        targetRank,
                        summary != null ? summary.resultLevel() : null));
            }
        }

        // Sort by daysLeft ascending (soonest to expire first, expired on top, nulls last)
        allItems.sort(Comparator.comparing(
                EvaluationExpiryItem::daysLeft,
                Comparator.nullsLast(Long::compareTo)));

        // Filter if requested
        List<EvaluationExpiryItem> filteredItems = allItems;
        if ("urgent30".equalsIgnoreCase(filter)) {
            filteredItems = allItems.stream()
                    .filter(item -> item.daysLeft() != null && item.daysLeft() >= 0 && item.daysLeft() <= 30)
                    .toList();
        } else if ("warning90".equalsIgnoreCase(filter)) {
            filteredItems = allItems.stream()
                    .filter(item -> item.daysLeft() != null && item.daysLeft() >= 0 && item.daysLeft() <= 90)
                    .toList();
        } else if ("warning180".equalsIgnoreCase(filter)) {
            filteredItems = allItems.stream()
                    .filter(item -> item.daysLeft() != null && item.daysLeft() >= 0 && item.daysLeft() <= 180)
                    .toList();
        } else if ("expired".equalsIgnoreCase(filter)) {
            filteredItems = allItems.stream()
                    .filter(item -> item.daysLeft() != null && item.daysLeft() < 0)
                    .toList();
        } else if ("unused".equalsIgnoreCase(filter)) {
            filteredItems = allItems.stream()
                    .filter(item -> !item.hasPositionRequest())
                    .toList();
        }

        Map<String, Long> summaryMap = new LinkedHashMap<>();
        summaryMap.put("totalPassed", totalPassed);
        summaryMap.put("activeCount", activeCount);
        summaryMap.put("expiringSoon30Count", expiringSoon30Count);
        summaryMap.put("expiringSoon90Count", expiringSoon90Count);
        summaryMap.put("expiringSoon180Count", expiringSoon180Count);
        summaryMap.put("expiredCount", expiredCount);
        summaryMap.put("unusedCount", unusedCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summaryMap);
        result.put("items", filteredItems);
        result.put("allItems", allItems);
        result.put("selectedFilter", filter != null ? filter : "all");
        return result;
    }

    // ── Yearly Subject Submissions Analytics ─────────────────────────────

    public Map<String, Object> getYearlySubjectAnalytics(String selectedYear) {
        List<AcademicRequest> requests = acadRepo.findAllNonDraftWithApplicant();

        List<SubjectSubmissionItem> items = new ArrayList<>();
        Map<String, Long> subjectCounts = new LinkedHashMap<>();
        Set<String> distinctYears = new TreeSet<>(Comparator.reverseOrder());

        for (AcademicRequest req : requests) {
            EvaluationSummary summary = acadService.summarize(req);
            String year = summary != null ? summary.academicYear() : null;
            if (year == null || year.isBlank()) {
                year = req.getCreatedAt() != null ? String.valueOf(req.getCreatedAt().getYear() + 543) : "";
            }
            if (!year.isBlank()) {
                distinctYears.add(year);
            }

            boolean matchYear = (selectedYear == null || selectedYear.isBlank() || "ALL".equalsIgnoreCase(selectedYear))
                    || year.equals(selectedYear);

            if (matchYear) {
                String applicantName = req.getApplicant() != null ? req.getApplicant().getName() : "-";
                String courseCode = summary != null ? summary.courseCode() : null;
                String courseName = summary != null ? summary.courseName() : null;
                String courseLabel = summary != null ? summary.courseLabel() : null;

                if (courseLabel != null && !courseLabel.isBlank() && !"ไม่ระบุรายวิชา".equals(courseLabel)) {
                    subjectCounts.merge(courseLabel, 1L, Long::sum);
                }

                items.add(new SubjectSubmissionItem(
                        req.getId(),
                        req.getRequestCode(),
                        courseCode,
                        courseName,
                        year,
                        summary != null ? summary.semester() : null,
                        applicantName,
                        req.getCurrentStatus().getThaiLabel(),
                        req.getCurrentStatus().getColor(),
                        summary != null ? summary.evaluationDate() : null,
                        summary != null ? summary.expiryDate() : null,
                        summary != null ? summary.resultLevel() : null));
            }
        }

        Map<String, Long> topSubjects = subjectCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("totalSubmissions", (long) items.size());
        result.put("topSubjects", topSubjects);
        result.put("availableYears", new ArrayList<>(distinctYears));
        result.put("selectedYear", (selectedYear == null || selectedYear.isBlank()) ? "ALL" : selectedYear);
        return result;
    }

    // ── Position Request Pipeline & Discipline Distribution ──────────────

    public Map<String, Object> getPositionPipelineAnalytics() {
        List<Object[]> rankRows = posRepo.countByTargetPosition();
        Map<String, Long> byRank = new LinkedHashMap<>();
        for (Object[] row : rankRows) {
            String rank = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            byRank.put(rank, count);
        }

        List<Object[]> majorRows = posRepo.countByMajor();
        Map<String, Long> byMajor = new LinkedHashMap<>();
        for (Object[] row : majorRows) {
            String major = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            byMajor.put(major, count);
        }

        Map<String, Object> pipeline = new LinkedHashMap<>();
        pipeline.put("byRank", byRank);
        pipeline.put("byMajor", byMajor);
        return pipeline;
    }

    // ── Evaluation Quality & Pass Rate ───────────────────────────────────

    public Map<String, Object> getEvaluationQualityMetrics() {
        long passedCount = acadRepo.countByCurrentStatusIn(List.of(
                RequestStatus.COMPLETED_PASS,
                RequestStatus.COLLEGE_ENDORSED,
                RequestStatus.COMPLETED));

        long failedCount = acadRepo.countByCurrentStatus(RequestStatus.COMPLETED_FAIL);
        long revisingCount = acadRepo.countByCurrentStatusIn(List.of(
                RequestStatus.COMPLETED_REVISE,
                RequestStatus.REVISION_SUBMITTED));

        long totalEvaluated = passedCount + failedCount;
        int passRate = totalEvaluated > 0 ? (int) Math.round((passedCount * 100.0) / totalEvaluated) : 100;

        List<AcademicRequest> requests = acadRepo.findAllNonDraftWithApplicant();
        Map<String, Long> levelCounts = new LinkedHashMap<>();
        for (AcademicRequest req : requests) {
            if (req.getCurrentStatus() != null && req.getCurrentStatus().carriesAPassedResult()) {
                EvaluationSummary summary = acadService.summarize(req);
                String level = summary != null ? summary.resultLevel() : null;
                if (level == null || level.isBlank()) {
                    level = "ผ่านเกณฑ์";
                }
                levelCounts.merge(level, 1L, Long::sum);
            }
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("passRate", passRate);
        metrics.put("passedCount", passedCount);
        metrics.put("failedCount", failedCount);
        metrics.put("revisingCount", revisingCount);
        metrics.put("levelCounts", levelCounts);
        return metrics;
    }

    // ── Stalled & SLA Bottleneck Analytics ──────────────────────────────

    public Map<String, Object> getStalledRequestAnalytics() {
        LocalDate today = LocalDate.now();
        List<StalledRequestItem> stalledItems = new ArrayList<>();
        long totalStalled14 = 0;
        long totalCritical30 = 0;

        Set<RequestStatus> acadTerminal = Set.of(
                RequestStatus.COMPLETED,
                RequestStatus.COMPLETED_PASS,
                RequestStatus.COMPLETED_FAIL,
                RequestStatus.DRAFT
        );

        List<AcademicRequest> acadRequests = acadRepo.findAllNonDraftWithApplicant();
        long completedAcadCount = 0;
        long completedAcadDaysSum = 0;

        for (AcademicRequest r : acadRequests) {
            if (r.getCurrentStatus() == RequestStatus.COMPLETED
                    || r.getCurrentStatus() == RequestStatus.COMPLETED_PASS
                    || r.getCurrentStatus() == RequestStatus.COMPLETED_FAIL) {
                if (r.getCreatedAt() != null && r.getUpdatedAt() != null) {
                    long duration = ChronoUnit.DAYS.between(r.getCreatedAt().toLocalDate(), r.getUpdatedAt().toLocalDate());
                    if (duration >= 0) {
                        completedAcadDaysSum += duration;
                        completedAcadCount++;
                    }
                }
            } else if (!acadTerminal.contains(r.getCurrentStatus())) {
                LocalDateTime lastUpdated = r.getUpdatedAt() != null ? r.getUpdatedAt()
                        : (r.getSubmissionDate() != null ? r.getSubmissionDate() : r.getCreatedAt());
                if (lastUpdated != null) {
                    long days = ChronoUnit.DAYS.between(lastUpdated.toLocalDate(), today);
                    if (days >= 14) {
                        totalStalled14++;
                        boolean critical = days >= 30;
                        if (critical) totalCritical30++;
                        String applicantName = r.getApplicant() != null ? r.getApplicant().getName() : "-";
                        stalledItems.add(new StalledRequestItem(
                                r.getRequestCode(),
                                SignatureModule.ACADEMIC.adminLink(r.getId()),
                                applicantName,
                                "ประเมินการสอน",
                                r.getCurrentStatus().getThaiLabel(),
                                r.getCurrentStatus().getColor(),
                                AcademicRequestService.formatThaiDate(lastUpdated),
                                days,
                                critical
                        ));
                    }
                }
            }
        }

        Set<PositionRequestStatus> posTerminal = Set.of(
                PositionRequestStatus.SENT_TO_HR,
                PositionRequestStatus.DRAFT
        );

        List<PositionRequest> posRequests = posRepo.findAllNonDraftWithApplicant();
        for (PositionRequest pr : posRequests) {
            if (!posTerminal.contains(pr.getCurrentStatus())) {
                LocalDateTime lastUpdated = pr.getUpdatedAt() != null ? pr.getUpdatedAt()
                        : (pr.getSubmissionDate() != null ? pr.getSubmissionDate() : pr.getCreatedAt());
                if (lastUpdated != null) {
                    long days = ChronoUnit.DAYS.between(lastUpdated.toLocalDate(), today);
                    if (days >= 14) {
                        totalStalled14++;
                        boolean critical = days >= 30;
                        if (critical) totalCritical30++;
                        String applicantName = pr.getApplicant() != null ? pr.getApplicant().getName() : "-";
                        String code = pr.getRequestCode() != null ? pr.getRequestCode() : ("KKU-POS-" + pr.getId());
                        stalledItems.add(new StalledRequestItem(
                                code,
                                SignatureModule.POSITION.adminLink(pr.getId()),
                                applicantName,
                                "ขอตำแหน่งทางวิชาการ",
                                pr.getCurrentStatus().getThaiLabel(),
                                pr.getCurrentStatus().getColor(),
                                AcademicRequestService.formatThaiDate(lastUpdated),
                                days,
                                critical
                        ));
                    }
                }
            }
        }

        stalledItems.sort((a, b) -> Long.compare(b.stalledDays(), a.stalledDays()));
        long avgTurnaroundDays = completedAcadCount > 0 ? (completedAcadDaysSum / completedAcadCount) : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", stalledItems);
        result.put("totalStalled14", totalStalled14);
        result.put("totalCritical30", totalCritical30);
        result.put("avgTurnaroundDays", avgTurnaroundDays);
        return result;
    }

    // ── Pending E-Signature Pipeline ─────────────────────────────────────

    public Map<String, Object> getPendingSignatureAnalytics() {
        LocalDate today = LocalDate.now();
        List<SignatureStep> activeSteps = signatureStepRepo.findAllActivePendingSteps();
        List<PendingSignatureItem> items = new ArrayList<>();
        Map<String, Long> byRole = new LinkedHashMap<>();
        long overdueCount = 0;

        // An envelope stores only the request's id; the code the admin knows the
        // request by lives on the request row. Fetch those in one query per module.
        Set<Long> acadIds = new HashSet<>();
        Set<Long> posIds = new HashSet<>();
        for (SignatureStep s : activeSteps) {
            if (s.getSignatureRequest() == null || s.getSignatureRequest().getRequestId() == null) continue;
            (moduleOf(s) == SignatureModule.POSITION ? posIds : acadIds).add(s.getSignatureRequest().getRequestId());
        }
        Map<Long, String> acadCodes = new LinkedHashMap<>();
        if (!acadIds.isEmpty()) {
            acadRepo.findAllById(acadIds).forEach(r -> acadCodes.put(r.getId(), r.getRequestCode()));
        }
        Map<Long, String> posCodes = new LinkedHashMap<>();
        if (!posIds.isEmpty()) {
            posRepo.findAllById(posIds).forEach(r -> posCodes.put(r.getId(), r.getRequestCode()));
        }

        for (SignatureStep s : activeSteps) {
            Long stepId = s.getId();
            Long reqId = s.getSignatureRequest() != null ? s.getSignatureRequest().getId() : 0L;
            String reqCode = "-";
            String detailUrl = null;
            if (s.getSignatureRequest() != null && s.getSignatureRequest().getRequestId() != null) {
                SignatureModule module = moduleOf(s);
                Long requestId = s.getSignatureRequest().getRequestId();
                String code = (module == SignatureModule.POSITION ? posCodes : acadCodes).get(requestId);
                reqCode = code != null ? code : ("#" + requestId);
                detailUrl = module.adminLink(requestId);
            }

            String docTitle = (s.getSignatureRequest() != null && s.getSignatureRequest().getDocumentLabel() != null)
                    ? s.getSignatureRequest().getDocumentLabel()
                    : ("เอกสารแบบฟอร์ม " + (s.getSignatureRequest() != null ? s.getSignatureRequest().getDocumentType() : "-"));

            String signerName = s.getSigner() != null ? s.getSigner().getName()
                    : (s.getSignerNameSnapshot() != null ? s.getSignerNameSnapshot() : "ไม่ระบุ");

            String role = s.getRoleLabel() != null ? s.getRoleLabel() : "ผู้ลงนาม";

            LocalDateTime reqDate = s.getNotifiedAt() != null ? s.getNotifiedAt()
                    : (s.getSignatureRequest() != null ? s.getSignatureRequest().getCreatedAt() : null);

            long waitingDays = reqDate != null ? ChronoUnit.DAYS.between(reqDate.toLocalDate(), today) : 0;
            boolean isOverdue = s.getSignatureRequest() != null
                    && s.getSignatureRequest().getDueAt() != null
                    && s.getSignatureRequest().getDueAt().isBefore(LocalDateTime.now());

            if (isOverdue) overdueCount++;

            byRole.put(role, byRole.getOrDefault(role, 0L) + 1);

            items.add(new PendingSignatureItem(
                    stepId,
                    reqId,
                    reqCode,
                    detailUrl,
                    docTitle,
                    signerName,
                    role,
                    reqDate != null ? AcademicRequestService.formatThaiDate(reqDate) : "-",
                    waitingDays,
                    isOverdue
            ));
        }

        items.sort((a, b) -> Long.compare(b.waitingDays(), a.waitingDays()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("totalPending", (long) items.size());
        result.put("overdueCount", overdueCount);
        result.put("byRole", byRole);
        return result;
    }

    // ── Faculty Academic Rank Distribution ───────────────────────────────

    public Map<String, Object> getFacultyRankAnalytics() {
        List<StaffMember> staffList = staffRepo.findByIsActiveTrueOrderByFirstNameAscLastNameAsc();
        long profCount = 0;
        long assocCount = 0;
        long assistCount = 0;
        long lectCount = 0;

        for (StaffMember staff : staffList) {
            AcademicRank rank = AcademicRank.of(staff.getAcademicTitle());
            if (rank == AcademicRank.PROFESSOR) {
                profCount++;
            } else if (rank == AcademicRank.ASSOCIATE_PROFESSOR) {
                assocCount++;
            } else if (rank == AcademicRank.ASSISTANT_PROFESSOR) {
                assistCount++;
            } else {
                lectCount++;
            }
        }

        long total = staffList.size();
        long promoted = profCount + assocCount + assistCount;
        double promotedPct = total > 0 ? Math.round((promoted * 100.0 / total) * 10.0) / 10.0 : 0.0;

        FacultyRankStat stat = new FacultyRankStat(
                total,
                profCount,
                assocCount,
                assistCount,
                lectCount,
                promotedPct
        );

        Map<String, Long> rankCounts = new LinkedHashMap<>();
        rankCounts.put("ศาสตราจารย์ (ศ.)", profCount);
        rankCounts.put("รองศาสตราจารย์ (รศ.)", assocCount);
        rankCounts.put("ผู้ช่วยศาสตราจารย์ (ผศ.)", assistCount);
        rankCounts.put("อาจารย์ทั่วไป (อ.)", lectCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stat", stat);
        result.put("rankCounts", rankCounts);
        result.put("labels", new ArrayList<>(rankCounts.keySet()));
        result.put("values", new ArrayList<>(rankCounts.values()));
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** The envelope's module; the column is NOT NULL, but default to ACADEMIC defensively. */
    private static SignatureModule moduleOf(SignatureStep s) {
        SignatureModule m = s.getSignatureRequest().getModule();
        return m != null ? m : SignatureModule.ACADEMIC;
    }

    private Map<String, Long> rowsToMonthMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            long count = ((Number) row[2]).longValue();
            map.put(year + "-" + String.format("%02d", month), count);
        }
        return map;
    }

    private String normaliseAcademicTitle(String title) {
        if (title == null) return "อาจารย์";
        String t = title.trim();
        if (t.contains("ศาสตราจารย์") && !t.contains("ผู้ช่วย") && !t.contains("รอง")) return "ศาสตราจารย์";
        if (t.contains("รองศาสตราจารย์") || t.startsWith("รศ.")) return "รองศาสตราจารย์";
        if (t.contains("ผู้ช่วยศาสตราจารย์") || t.startsWith("ผศ.")) return "ผู้ช่วยศาสตราจารย์";
        if (t.contains("อาจารย์") || t.startsWith("อ.")) return "อาจารย์";
        // Fallback for titles containing ดร. but no academic rank
        if (t.startsWith("ดร.") || t.equals("ดร.")) return "อาจารย์";
        return t;
    }

    private static final String[] THAI_SHORT_MONTHS = {
            "", "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.",
            "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."
    };

    private String thaiShortMonth(int month) {
        return (month >= 1 && month <= 12) ? THAI_SHORT_MONTHS[month] : String.valueOf(month);
    }
}
