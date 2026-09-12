package com.ecom.academic.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for StatusType enum configuration
 * 
 * **Validates: Requirements 2.2, 2.3, 4.1-4.7**
 * 
 * Tests that each StatusType has the correct displayName, color, and iconClass
 * as specified in the requirements.
 */
public class StatusTypeTest {

    @Test
    void receivedStatus_shouldHaveCorrectConfiguration() {
        StatusType status = StatusType.RECEIVED;
        
        assertThat(status.getDisplayName()).isEqualTo("รับคำร้อง");
        assertThat(status.getColor()).isEqualTo("blue");
        assertThat(status.getIconClass()).isEqualTo("fa-inbox");
    }

    @Test
    void committeeAssignedStatus_shouldHaveCorrectConfiguration() {
        StatusType status = StatusType.COMMITTEE_ASSIGNED;
        
        assertThat(status.getDisplayName()).isEqualTo("แต่งตั้งอนุกรรมการ");
        assertThat(status.getColor()).isEqualTo("orange");
        assertThat(status.getIconClass()).isEqualTo("fa-users");
    }

    @Test
    void meetingScheduledStatus_shouldHaveCorrectConfiguration() {
        StatusType status = StatusType.MEETING_SCHEDULED;
        
        assertThat(status.getDisplayName()).isEqualTo("นัดหมายวันประชุม");
        assertThat(status.getColor()).isEqualTo("purple");
        assertThat(status.getIconClass()).isEqualTo("fa-calendar");
    }

    @Test
    void resultApprovedStatus_shouldHaveCorrectConfiguration() {
        StatusType status = StatusType.RESULT_APPROVED;
        
        assertThat(status.getDisplayName()).isEqualTo("แจ้งผล - ผ่าน");
        assertThat(status.getColor()).isEqualTo("green");
        assertThat(status.getIconClass()).isEqualTo("fa-check-circle");
    }

    @Test
    void resultRevisionStatus_shouldHaveCorrectConfiguration() {
        StatusType status = StatusType.RESULT_REVISION;
        
        assertThat(status.getDisplayName()).isEqualTo("แจ้งผล - แก้ไข");
        assertThat(status.getColor()).isEqualTo("yellow");
        assertThat(status.getIconClass()).isEqualTo("fa-edit");
    }

    @Test
    void completedStatus_shouldHaveCorrectConfiguration() {
        StatusType status = StatusType.COMPLETED;
        
        assertThat(status.getDisplayName()).isEqualTo("เสร็จสิ้น");
        assertThat(status.getColor()).isEqualTo("dark-green");
        assertThat(status.getIconClass()).isEqualTo("fa-check");
    }

    @Test
    void completedStatus_shouldHaveDarkGreenColorAndCheckIcon() {
        // Requirement 2.2: WHEN แสดงสถานะ "เสร็จสิ้น" THEN ระบบ SHALL แสดงไอคอนติ๊กถูก (✓)
        // Requirement 2.3: WHEN แสดงสถานะ "เสร็จสิ้น" THEN ระบบ SHALL ใช้สีเขียวเข้ม (dark green)
        StatusType status = StatusType.COMPLETED;
        
        assertThat(status.getColor()).isEqualTo("dark-green");
        assertThat(status.getIconClass()).isEqualTo("fa-check");
    }

    @Test
    void allStatusTypes_shouldHaveNonNullValues() {
        for (StatusType status : StatusType.values()) {
            assertThat(status.getDisplayName()).isNotNull().isNotEmpty();
            assertThat(status.getColor()).isNotNull().isNotEmpty();
            assertThat(status.getIconClass()).isNotNull().isNotEmpty();
        }
    }

    @Test
    void allStatusTypes_shouldHaveUniqueDisplayNames() {
        StatusType[] statuses = StatusType.values();
        
        for (int i = 0; i < statuses.length; i++) {
            for (int j = i + 1; j < statuses.length; j++) {
                assertThat(statuses[i].getDisplayName())
                    .isNotEqualTo(statuses[j].getDisplayName());
            }
        }
    }
}
