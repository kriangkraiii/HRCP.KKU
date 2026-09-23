# Plan: Admin Dashboard Analytics

> **Plan Document:** `docs/PLAN-admin-dashboard.md`
> **Date:** 2026-09-23
> **Agent:** `@[frontend-specialist]` + `@[backend-specialist]`

---

## Summary

สร้างหน้า Dashboard Analytics ฝั่งแอดมิน ระบบ CP HRD แสดงข้อมูลวิเคราะห์สำคัญ:
- 4 KPI Cards พร้อม mini sparklines
- Stacked Bar Chart: คำร้องรายเดือน 12 เดือน (ประเมินฯ vs ขอตำแหน่ง)
- Doughnut Chart: สัดส่วนสถานะคำร้อง
- Line Chart: แนวโน้มผู้ใช้ใหม่ 12 เดือน
- Horizontal Bar: สัดส่วนตำแหน่งวิชาการ
- Activity Table: AdminLog ล่าสุด

## Tech Stack

- **Backend**: Spring Boot + JPA repository aggregate queries
- **Frontend**: Thymeleaf + Chart.js 4.x CDN
- **Styling**: Existing design system + new dashboard CSS

## Files

| Action | File |
|--------|------|
| NEW | `DashboardAnalyticsService.java` |
| NEW | `dashboard.html` |
| MODIFY | `AcademicRequestRepository.java` |
| MODIFY | `PositionRequestRepository.java` |
| MODIFY | `UserRepository.java` |
| MODIFY | `StaffMemberRepository.java` |
| MODIFY | `AcademicAdminController.java` |
| MODIFY | `style.css` |
| MODIFY | `dark-theme.css` |
| MODIFY | `base_academic.html` |
