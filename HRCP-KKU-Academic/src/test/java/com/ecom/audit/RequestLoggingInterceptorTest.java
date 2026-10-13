package com.ecom.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.ecom.config.RequestLoggingInterceptor;
import com.ecom.service.AdminLogService;

/**
 * ประวัติกิจกรรมต้องบอกผลลัพธ์ ไม่ใช่แค่ว่ามี request เข้ามา
 */
class RequestLoggingInterceptorTest {

    private final AdminLogService logs = mock(AdminLogService.class);
    private final RequestLoggingInterceptor interceptor = new RequestLoggingInterceptor(logs);

    private String loggedDetails(String method, String uri, String query, int status, Exception ex,
            String expectedAction) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setQueryString(query);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);

        interceptor.afterCompletion(request, response, new Object(), ex);

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(logs).logWithDetails(anyString(), anyString(), eq(expectedAction), details.capture(), any(),
                eq(uri), any());
        return details.getValue();
    }

    @Test
    @DisplayName("POST ที่ถูกปฏิเสธบันทึก 403 ไว้ ไม่ดูเหมือนส่งสำเร็จ")
    void refusedPostRecordsItsStatus() {
        assertThat(loggedDetails("POST", "/admin/users/delete", null, 403, null, "FORM_SUBMIT"))
                .isEqualTo("POST /admin/users/delete → HTTP 403");
    }

    @Test
    @DisplayName("ข้อยกเว้นที่ไม่มีใครจัดการบันทึกเป็น 500 แม้ response ยังเป็น 200")
    void unhandledExceptionIsA500() {
        assertThat(loggedDetails("POST", "/admin/x", null, 200, new IllegalStateException(), "FORM_SUBMIT"))
                .endsWith("→ HTTP 500");
    }

    @Test
    @DisplayName("DELETE ไม่ถูกบันทึกเป็นการเข้าชมหน้า")
    void deleteIsADataChange() {
        assertThat(loggedDetails("DELETE", "/admin/external-sync/author-mappings/3", null, 200, null, "DATA_DELETE"))
                .startsWith("DELETE ");
    }

    @Test
    @DisplayName("query string ยาวเกินคอลัมน์ถูกตัด แต่ผลลัพธ์ยังอยู่ท้ายสุด")
    void longQueryIsCutButKeepsTheOutcome() {
        String details = loggedDetails("GET", "/admin/activity-logs", "q=" + "x".repeat(2000), 200, null, "PAGE_VIEW");
        assertThat(details).hasSize(1000).endsWith("→ HTTP 200");
    }
}
