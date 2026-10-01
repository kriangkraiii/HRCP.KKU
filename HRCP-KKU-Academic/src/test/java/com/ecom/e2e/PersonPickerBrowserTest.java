package com.ecom.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.academic.service.SignerNameResolver;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.support.TestDataFactory;
import com.microsoft.playwright.Locator;

/**
 * ช่องชื่อผู้ลงนามเลือกได้อย่างเดียว — พิมพ์เพื่อค้นหา เลือกจากรายชื่อในระบบ (person_picker.js)
 * ดู docs/PLAN-signer-picker.md
 */
@DisplayName("E2E: ตัวค้นหาชื่อผู้ลงนาม — ค้นหา เลือก เติมตำแหน่ง พิมพ์อิสระไม่ได้")
class PersonPickerBrowserTest extends PlaywrightTestBase {

    @Autowired
    private StaffMemberRepository staffMembers;

    @Autowired
    private UserRepository users;

    @AfterEach
    void removeStaff() {
        staffMembers.deleteAll();
    }

    @Test
    @DisplayName("เอกสารที่ 4: ค้นหาคณบดี เลือกแล้วได้ชื่อ รหัสบัญชี และตำแหน่ง — พิมพ์ทับแล้วออกจากช่อง ชื่อกลับเป็นคนที่เลือก")
    void searchPickAndNoFreeText() {
        UserDtls officer = data.admin();
        UserDtls dean = data.user("dean@" + TestDataFactory.DOMAIN, "ประสิทธิ์", "คณบดี", "ROLE_USER");
        dean.setPositionTitle("คณบดีวิทยาลัยการคอมพิวเตอร์");
        users.save(dean);
        StaffMember s = new StaffMember();
        s.setFirstName(dean.getFirstName());
        s.setLastName(dean.getLastName());
        s.setStaffRole("DEAN");
        s.setIsActive(true);
        s.setUser(dean);
        staffMembers.save(s);
        AcademicRequest request = data.evaluation(data.applicant(), RequestStatus.RECEIVED);
        String printed = SignerNameResolver.printedName(dean);

        signIn(officer.getEmail(), TestDataFactory.PASSWORD);
        page.navigate("/admin/academic/request/" + request.getId() + "/document/4");

        Locator name = page.locator("input[name=dean_name]");
        assertThat(name.getAttribute("list")).as("ไม่มี datalist ให้พิมพ์อิสระแล้ว").isNull();
        name.fill("ประสิทธิ์");
        Locator option = page.locator(".person-picker-menu.show .dropdown-item", new com.microsoft.playwright.Page
                .LocatorOptions().setHasText(printed)).first();
        option.waitFor();
        option.dispatchEvent("mousedown");

        assertThat(name.inputValue()).isEqualTo(printed);
        assertThat(page.locator("input[name=dean_name__signer]").inputValue()).isEqualTo(String.valueOf(dean.getId()));
        assertThat(page.locator("[name=deandropdown_position]").inputValue()).isEqualTo("คณบดีวิทยาลัยการคอมพิวเตอร์");

        name.fill("ชื่อที่พิมพ์เอง");
        name.evaluate("e => e.blur()"); // ออกจากช่องโดยไม่ได้เลือกจากรายการ
        page.waitForFunction("v => document.querySelector('input[name=dean_name]').value === v", printed);
        assertThat(page.locator("input[name=dean_name__signer]").inputValue()).isEqualTo(String.valueOf(dean.getId()));
    }
}
