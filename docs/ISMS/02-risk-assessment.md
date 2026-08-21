# ทะเบียนความเสี่ยงและการประเมินความเสี่ยง (Risk Register & Assessment)
**ระบบ HRCP.KKU — มาตรฐาน ISO/IEC 27001:2022 (Control 5.2, 5.3)**

---

## 1. เกณฑ์การประเมินความเสี่ยง (Risk Criteria)
- **โอกาสเกิด (Likelihood):** 1 (ต่ำมาก) ถึง 5 (สูงมาก)
- **ผลกระทบ (Impact):** 1 (น้อยมาก) ถึง 5 (วิกฤต)
- **ระดับความเสี่ยง (Risk Level) = Likelihood x Impact:**
  - 1-6: ต่ำ (Low)
  - 8-12: ปานกลาง (Medium)
  - 15-25: สูง/วิกฤต (High/Critical)

---

## 2. ตารางทะเบียนความเสี่ยง (Risk Register)

| ID | ทรัพย์สิน (Asset) | ภัยคุกคาม / ช่องโหว่ (Threat/Vulnerability) | L | I | Score | มาตรการควบคุมปัจจุบัน (Current Controls) | แผนจัดการความเสี่ยง (Treatment Plan) |
|---|---|---|---|---|---|---|---|
| **R01** | ข้อมูลบัญชีผู้ใช้ | Brute-force Password Attack | 4 | 4 | 16 (H) | RateLimitFilter + Progressive Lockout (15->120 นาที) + Dual-key blocking | บังคับ 2FA สำหรับ Admin, รองรับ SSO |
| **R02** | OTP Verification | รหัส OTP ค้างในฐานข้อมูลถูกเปิดเผย | 2 | 4 | 8 (M) | เก็บเป็น SHA-256 Hash + In-memory Cache TTL 5 นาที | ล้าง OTP ทันทีหลังใช้หรือหมดอายุ |
| **R03** | Web Application | Clickjacking / XSS Injection | 3 | 4 | 12 (M) | SecurityHeadersFilter (CSP Nonce, X-Frame-Options: SAMEORIGIN, nosniff, HSTS) | รีวิว Content-Security-Policy สม่ำเสมอ |
| **R04** | ฐานข้อมูล | Schema เสียหายจาก Hibernate Auto-update | 3 | 5 | 15 (H) | ปรับเป็น `ddl-auto=validate` + ควบคุมด้วย Flyway Migrations | กำหนดให้ทุกการแก้ DB ทำผ่าน SQL script เท่านั้น |
| **R05** | Audit Logs | Log สูญหายหรือไม่ครอบคลุมตามกฎหมาย | 2 | 5 | 10 (M) | AdminLogService บันทึก async ลง DB + DataRetentionService คุมขั้นต่ำ 90 วัน | สำรอง Log ไฟล์รายวัน |
| **R06** | Third-party Libraries | ช่องโหว่ CVE ใน Dependencies | 3 | 4 | 12 (M) | OWASP Dependency-Check Maven Plugin ใน CI/CD pipeline | สแกนก่อน Release และอัปเดตเวอร์ชันปลอดภัย |
| **R07** | ข้อมูลส่วนบุคคล (PDPA) | ข้อมูลบุคลากรถูกนำไปใช้โดยมิชอบ | 2 | 5 | 10 (M) | เข้ารหัส Password ด้วย BCrypt, ควบคุมสิทธิ์ด้วย RBAC | จำกัดการเข้าถึงหน้าจัดการข้อมูลเฉพาะ Admin |

---

## 3. การทบทวนความเสี่ยง (Review Schedule)
- ตรวจสอบทะเบียนความเสี่ยงทุกไตรมาส
- ทบทวนทันทีเมื่อพบช่องโหว่ใหม่ระดับ High ขึ้นไป
