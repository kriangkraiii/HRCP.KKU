package com.ecom.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.model.LoginAnnouncement;
import com.ecom.repository.LoginAnnouncementRepository;

/** ประกาศบนหน้าเข้าสู่ระบบ — อ่านทุกครั้งที่เปิดหน้า แอดมินแก้ได้จากหน้าตั้งค่า */
@Service
public class LoginAnnouncementService {

    private final LoginAnnouncementRepository repository;

    public LoginAnnouncementService(LoginAnnouncementRepository repository) {
        this.repository = repository;
    }

    /** ประกาศที่บันทึกไว้ — ยังไม่เคยบันทึกได้แถวว่างที่ปิดอยู่ */
    @Transactional(readOnly = true)
    public LoginAnnouncement current() {
        return repository.findById(LoginAnnouncement.SINGLETON_ID).orElseGet(LoginAnnouncement::new);
    }

    /** ข้อความที่ต้องแสดงบนหน้าเข้าสู่ระบบ — ว่างเมื่อปิดอยู่หรือไม่มีข้อความ */
    @Transactional(readOnly = true)
    public Optional<String> showing() {
        LoginAnnouncement a = current();
        return a.isShowing() ? Optional.of(a.getMessage().strip()) : Optional.empty();
    }

    /**
     * บันทึกข้อความและสถานะเปิด/ปิด
     *
     * @throws IllegalArgumentException เมื่อข้อความยาวเกิน หรือเปิดแสดงแต่ไม่มีข้อความ
     */
    @Transactional
    public LoginAnnouncement save(boolean enabled, String message, String updatedBy) {
        String text = message == null ? "" : message.strip();
        if (text.length() > LoginAnnouncement.MAX_LENGTH) {
            throw new IllegalArgumentException("ข้อความประกาศยาวเกิน " + LoginAnnouncement.MAX_LENGTH + " ตัวอักษร");
        }
        if (enabled && text.isEmpty()) {
            throw new IllegalArgumentException("เปิดแสดงประกาศต้องมีข้อความ");
        }
        LoginAnnouncement a = current();
        a.setEnabled(enabled);
        a.setMessage(text.isEmpty() ? null : text);
        a.setUpdatedAt(LocalDateTime.now(ZoneId.of("Asia/Bangkok")));
        a.setUpdatedBy(updatedBy);
        return repository.save(a);
    }
}
