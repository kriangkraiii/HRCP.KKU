package com.ecom.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * ผู้รับการแจ้งเตือนงานของเจ้าหน้าที่ — คำร้องใหม่ ฉบับแก้ไขที่ผู้ยื่นส่งกลับมา คำขอทบทวนผล
 * อีเมลที่ส่งไม่สำเร็จ และเหตุขัดข้องของระบบ
 *
 * <p>บนระบบจริงคณบดีและผู้บริหารก็เป็น {@code ROLE_ADMIN} ด้วย การส่งถึงแอดมินทุกคนจึงพาอีเมล
 * "ขอให้ตรวจสอบและส่งเวียนลงนามต่อ" ไปถึงคณบดี ทั้งที่งานนั้นเป็นของเจ้าหน้าที่ ผู้บริหารรู้เรื่อง
 * ที่ต้องรู้จากซองลงนามที่ส่งถึงตัวอยู่แล้ว
 *
 * <p>ผู้บริหารคือแอดมินที่ผูกกับบุคลากรตำแหน่ง {@code DEAN} หรือ {@code HEAD} (รองคณบดี
 * ผู้ช่วยคณบดี หัวหน้าสาขา — ดู {@code StaffDirectorySync.resolveRoleFromPosition})
 * ถ้าตัดแล้วไม่เหลือใครเลย ส่งถึงแอดมินทุกคนเหมือนเดิม งานต้องไม่หายเงียบเพราะยังไม่ได้ผูกบัญชีเจ้าหน้าที่
 */
@Component
public class OfficerRecipients {

    private static final Set<String> EXECUTIVE_ROLES = Set.of("DEAN", "HEAD");

    private final UserRepository userRepository;
    private final StaffMemberRepository staffMemberRepository;

    public OfficerRecipients(UserRepository userRepository, StaffMemberRepository staffMemberRepository) {
        this.userRepository = userRepository;
        this.staffMemberRepository = staffMemberRepository;
    }

    @Transactional(readOnly = true)
    public List<UserDtls> officers() {
        List<UserDtls> admins = userRepository.findByRole("ROLE_ADMIN");
        Set<Integer> executives = staffMemberRepository.findAllActiveWithUser().stream()
                .filter(s -> s.getUser() != null && EXECUTIVE_ROLES.contains(s.getStaffRole()))
                .map(StaffMember::getUser)
                .map(UserDtls::getId)
                .collect(Collectors.toSet());
        List<UserDtls> officers = admins.stream()
                .filter(admin -> !executives.contains(admin.getId()))
                .toList();
        return officers.isEmpty() ? admins : officers;
    }
}
