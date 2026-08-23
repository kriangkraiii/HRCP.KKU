package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.ecom.academic.controller.AcademicSettingsController;
import com.ecom.model.CustomExpiryAlert;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

class CustomExpiryAlertTest {

    private UserRepository userRepository;
    private TwoFactorService twoFactorService;
    private AdminLogService adminLogService;
    private HttpServletRequest httpRequest;
    private AcademicSettingsController controller;
    private ObjectMapper mapper;

    private UserDtls testUser;
    private Principal principal;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        twoFactorService = mock(TwoFactorService.class);
        adminLogService = mock(AdminLogService.class);
        httpRequest = mock(HttpServletRequest.class);
        mapper = new ObjectMapper();

        controller = new AcademicSettingsController(userRepository, twoFactorService, adminLogService, httpRequest);

        testUser = new UserDtls();
        testUser.setId(1);
        testUser.setEmail("test@kku.ac.th");
        testUser.setName("ดร. สมชาย ใจดี");

        principal = () -> "test@kku.ac.th";
        when(userRepository.findByEmail("test@kku.ac.th")).thenReturn(testUser);
        when(userRepository.save(any(UserDtls.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("สร้าง CustomExpiryAlert คำนวณวันและข้อความกำกับถูกต้อง")
    void testCreateAlertModel() {
        CustomExpiryAlert alertDays = CustomExpiryAlert.create(15, "DAYS");
        assertThat(alertDays.getDays()).isEqualTo(15);
        assertThat(alertDays.getLabel()).isEqualTo("15 วันก่อนหมด");
        assertThat(alertDays.isEnabled()).isTrue();

        CustomExpiryAlert alertWeeks = CustomExpiryAlert.create(2, "WEEKS");
        assertThat(alertWeeks.getDays()).isEqualTo(14);
        assertThat(alertWeeks.getLabel()).isEqualTo("2 สัปดาห์ก่อนหมด");

        CustomExpiryAlert alertMonths = CustomExpiryAlert.create(4, "MONTHS");
        assertThat(alertMonths.getDays()).isEqualTo(120);
        assertThat(alertMonths.getLabel()).isEqualTo("4 เดือนก่อนหมด");
    }

    @Test
    @DisplayName("เพิ่มการแจ้งเตือนกำหนดเองสำเร็จและจัดเก็บในรูปแบบ JSON")
    void testAddCustomAlertSuccess() throws Exception {
        ResponseEntity<?> resp = controller.addCustomAlert(15, "DAYS", principal);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<?, ?> body = (Map<?, ?>) resp.getBody();
        assertThat(body.get("success")).isEqualTo(true);
        assertThat(body.get("count")).isEqualTo(1);

        assertThat(testUser.getCustomExpiryAlerts()).isNotNull();
        List<CustomExpiryAlert> list = mapper.readValue(testUser.getCustomExpiryAlerts(), new TypeReference<List<CustomExpiryAlert>>() {});
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getDays()).isEqualTo(15);
    }

    @Test
    @DisplayName("จำกัดการเพิ่มการแจ้งเตือนกำหนดเองได้สูงสุด 5 รายการ")
    void testMaxFiveCustomAlertsLimit() throws Exception {
        // Add 5 alerts
        controller.addCustomAlert(10, "DAYS", principal);
        controller.addCustomAlert(20, "DAYS", principal);
        controller.addCustomAlert(40, "DAYS", principal);
        controller.addCustomAlert(50, "DAYS", principal);
        ResponseEntity<?> fifth = controller.addCustomAlert(60, "DAYS", principal);
        assertThat(fifth.getStatusCode().is2xxSuccessful()).isTrue();

        // Attempt to add 6th alert
        ResponseEntity<?> sixth = controller.addCustomAlert(70, "DAYS", principal);
        assertThat(sixth.getStatusCode().is4xxClientError()).isTrue();

        Map<?, ?> body = (Map<?, ?>) sixth.getBody();
        assertThat(body.get("success")).isEqualTo(false);
        assertThat((String) body.get("message")).contains("สูงสุด 5 รายการ");
    }

    @Test
    @DisplayName("ป้องกันการเพิ่มเวลาแจ้งเตือนที่ซ้ำกัน")
    void testPreventDuplicateAlerts() {
        controller.addCustomAlert(15, "DAYS", principal);

        ResponseEntity<?> duplicate = controller.addCustomAlert(15, "DAYS", principal);
        assertThat(duplicate.getStatusCode().is4xxClientError()).isTrue();

        Map<?, ?> body = (Map<?, ?>) duplicate.getBody();
        assertThat(body.get("success")).isEqualTo(false);
        assertThat((String) body.get("message")).contains("อยู่แล้ว");
    }

    @Test
    @DisplayName("เปิด/ปิด สถานะการแจ้งเตือน (Toggle) ได้อย่างถูกต้อง")
    void testToggleCustomAlert() throws Exception {
        controller.addCustomAlert(15, "DAYS", principal);
        List<CustomExpiryAlert> list = mapper.readValue(testUser.getCustomExpiryAlerts(), new TypeReference<List<CustomExpiryAlert>>() {});
        String alertId = list.get(0).getId();

        ResponseEntity<?> toggleOff = controller.toggleCustomAlert(alertId, false, principal);
        assertThat(toggleOff.getStatusCode().is2xxSuccessful()).isTrue();

        List<CustomExpiryAlert> updatedList = mapper.readValue(testUser.getCustomExpiryAlerts(), new TypeReference<List<CustomExpiryAlert>>() {});
        assertThat(updatedList.get(0).isEnabled()).isFalse();

        ResponseEntity<?> toggleOn = controller.toggleCustomAlert(alertId, true, principal);
        assertThat(toggleOn.getStatusCode().is2xxSuccessful()).isTrue();

        updatedList = mapper.readValue(testUser.getCustomExpiryAlerts(), new TypeReference<List<CustomExpiryAlert>>() {});
        assertThat(updatedList.get(0).isEnabled()).isTrue();
    }

    @Test
    @DisplayName("ลบรายการแจ้งเตือนกำหนดเองสำเร็จ")
    void testDeleteCustomAlert() throws Exception {
        controller.addCustomAlert(15, "DAYS", principal);
        controller.addCustomAlert(45, "DAYS", principal);

        List<CustomExpiryAlert> list = mapper.readValue(testUser.getCustomExpiryAlerts(), new TypeReference<List<CustomExpiryAlert>>() {});
        assertThat(list).hasSize(2);

        String alertIdToDelete = list.get(0).getId();
        ResponseEntity<?> delResp = controller.deleteCustomAlert(alertIdToDelete, principal);
        assertThat(delResp.getStatusCode().is2xxSuccessful()).isTrue();

        List<CustomExpiryAlert> remainingList = mapper.readValue(testUser.getCustomExpiryAlerts(), new TypeReference<List<CustomExpiryAlert>>() {});
        assertThat(remainingList).hasSize(1);
        assertThat(remainingList.get(0).getId()).isNotEqualTo(alertIdToDelete);
    }
}
