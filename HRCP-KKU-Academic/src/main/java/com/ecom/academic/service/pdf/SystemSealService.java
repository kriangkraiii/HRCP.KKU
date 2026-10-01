package com.ecom.academic.service.pdf;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Service;

import com.ecom.model.UserDtls;

/**
 * ใบรับรองของหน่วยงาน (ระบบ) สำหรับประทับรับรองลายมือชื่อของผู้ลงนามภายนอกที่ไม่มี Digital ID
 *
 * <p>ทางสำรอง B ใน docs/PLAN-external-signer.md: ผู้ลงนามภายนอก ({@link UserDtls#ROLE_EXTERNAL}) ที่มี .p12
 * ของหน่วยงานตัวเองใช้ของตัวเอง (ทางหลัก A) ส่วนคนที่ไม่มี ยืนยันตัวตนด้วยรหัสทางอีเมลก่อน แล้วระบบลงนามลงไฟล์
 * ด้วยใบรับรองนี้ พร้อมเหตุผลที่ระบุชื่อและอีเมลของผู้ลงนาม — Foxit/Adobe จึงยังขึ้นใบรับรองครบทุกขั้น
 *
 * <p>หาไฟล์ที่ {@code secrets/system-seal.p12} ข้าง app.jar (deploy.yml สร้างให้ครั้งแรกที่ deploy ถ้ายังไม่มี)
 * รหัสอยู่ในไฟล์ {@code system-seal.p12.password} ข้างกัน — ไม่ต้องตั้งค่าอะไร และไม่พึ่ง environment ของ service
 * จะชี้ไปที่อื่นก็ตั้ง {@code app.esign.system-seal.p12-path} / {@code app.esign.system-seal.password} ได้
 * ไม่มีไฟล์ = ปิดทางสำรองนี้ ผู้ลงนามภายนอกต้องมี .p12 เอง
 */
@Service
public class SystemSealService {

    private static final Logger log = LoggerFactory.getLogger(SystemSealService.class);

    static final String DEFAULT_LOCATION = "secrets/system-seal.p12";
    static final String PASSWORD_FILE_SUFFIX = ".password";

    private final Path p12Path;
    private final String password;

    @Autowired
    public SystemSealService(@Value("${app.esign.system-seal.p12-path:}") String p12Path,
            @Value("${app.esign.system-seal.password:}") String password) {
        this(p12Path, password, new ApplicationHome(SystemSealService.class).getDir().toPath());
    }

    SystemSealService(String p12Path, String password, Path appHome) {
        boolean configured = p12Path != null && !p12Path.isBlank();
        this.p12Path = configured ? Path.of(p12Path.strip()) : appHome.resolve(DEFAULT_LOCATION);
        this.password = password == null ? "" : password;
        if (isConfigured()) {
            log.info("System seal certificate: {}", this.p12Path);
        } else if (configured) {
            log.warn("System seal certificate {} is not readable — external signers without a .p12 cannot sign",
                    this.p12Path);
        } else {
            log.info("No system seal certificate at {} — external signers without a .p12 cannot sign", this.p12Path);
        }
    }

    /** ตั้งค่าใบรับรองของระบบไว้และอ่านได้ */
    public boolean isConfigured() {
        return Files.isReadable(p12Path);
    }

    /** ผู้ลงนามคนนี้ใช้ทางสำรอง (ยืนยันทางอีเมล + ระบบประทับรับรอง) ได้ — เฉพาะผู้ลงนามภายนอก */
    public boolean availableFor(UserDtls signer) {
        return signer != null && signer.isExternal() && isConfigured();
    }

    /** กุญแจของระบบสำหรับลงนามหนึ่งครั้ง */
    public CmsSigner open() throws Exception {
        return CmsSigner.open(Files.readAllBytes(p12Path), password().toCharArray());
    }

    /** รหัสที่ตั้งไว้ หรือที่ deploy.yml เขียนไว้ข้างไฟล์ .p12 (อ่านตอนใช้ ไฟล์ที่สร้างทีหลังก็ใช้ได้เลย) */
    private String password() throws java.io.IOException {
        if (!password.isEmpty()) {
            return password;
        }
        Path file = p12Path.resolveSibling(p12Path.getFileName() + PASSWORD_FILE_SUFFIX);
        return Files.isReadable(file) ? Files.readString(file).strip() : "";
    }

    /** เหตุผลในลายมือชื่อดิจิทัล — ระบุว่าใครลงนามและยืนยันตัวตนอย่างไร */
    public static String reasonFor(UserDtls signer, String roleLabel) {
        return "ลงนามในตำแหน่ง \"" + roleLabel + "\" โดย " + signer.getName() + " (" + signer.getEmail()
                + ") ยืนยันตัวตนทางอีเมล — รับรองโดยระบบพัฒนาบุคลากร วิทยาลัยการคอมพิวเตอร์ มข.";
    }
}
