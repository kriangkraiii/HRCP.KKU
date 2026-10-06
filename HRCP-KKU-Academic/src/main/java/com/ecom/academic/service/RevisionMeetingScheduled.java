package com.ecom.academic.service;

import com.ecom.model.UserDtls;

/**
 * Flow ข้อ 14-15 — เจ้าหน้าที่นัดประชุมพิจารณาฉบับแก้ไขแล้ว (committed)
 *
 * <p>กรรมการสามท่านต้องรู้วันนัดรอบใหม่และได้เห็นฉบับแก้ — ดู
 * {@link CommitteeDocumentMailer#onRevisionMeetingScheduled}
 */
public record RevisionMeetingScheduled(Long requestId, UserDtls scheduledBy) {
}
