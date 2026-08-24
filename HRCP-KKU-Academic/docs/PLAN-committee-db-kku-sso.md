# แผนงาน: ระบบจัดเก็บข้อมูลกรรมการลงฐานข้อมูล & ระบบลงนามผ่าน KKU SSO พร้อมอีเมลทางการ (PLAN-committee-db-kku-sso.md)

## 📌 สรุปโจทย์และความต้องการ
1. จัดเก็บข้อมูลกรรมการผู้ทรงคุณวุฒิลงใน Database: ชื่อ-สกุล, ตำแหน่งวิชาการ, สถาบัน/มหาวิทยาลัยต้นสังกัด, อีเมล, เบอร์โทรศัพท์, ประเภทกรรมการ และสาขาความเชี่ยวชาญ
2. การลงนามของกรรมการต้องยืนยันตัวตนผ่าน **KKU SSO** (ไม่มีการใช้ Password ทั่วไปใน Production)
3. อีเมลแจ้งเตือนที่ส่งหากรรมการต้องเป็น **รูปแบบหนังสือราชการทางการ น่าเชื่อถือ และระบุวันหมดอายุ (Due Date) ไว้อย่างชัดเจน**

---

## 🗄️ โครงสร้างฐานข้อมูล (Entity Model)

```sql
CREATE TABLE academic_committee_member (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(50) NOT NULL,              -- เช่น ศ., รศ.ดร., ผศ., อ.
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    academic_position VARCHAR(100),          -- ศาสตราจารย์, รองศาสตราจารย์, ผู้ช่วยศาสตราจารย์
    affiliation VARCHAR(255) NOT NULL,       -- เช่น คณะวิทยาศาสตร์ มหาวิทยาลัยขอนแก่น
    email VARCHAR(255) NOT NULL,             -- อีเมลสำหรับรับแจ้งเตือนและเข้าสู่ระบบผ่าน KKU SSO
    phone_number VARCHAR(50),                -- เบอร์โทรศัพท์ติดต่อ
    committee_type VARCHAR(50) NOT NULL,     -- TEACHING_EVALUATION, POSITION_SCREENING, EXTERNAL_READER
    expertise_field VARCHAR(255),            -- สาขาวิชา/ความเชี่ยวชาญ
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_comm_email ON academic_committee_member(email);
CREATE INDEX idx_comm_affiliation ON academic_committee_member(affiliation);
CREATE INDEX idx_comm_type ON academic_committee_member(committee_type);
```

---

## 🔐 กลไกความปลอดภัยและ KKU SSO Workflow

1. **ส่งหนังสือขอความอนุเคราะห์ (Email Notification):**
   - เมื่อเอกสารเข้าสู่คิวลงนามของกรรมการ ระบบส่งอีเมลราชการระบุรายละเอียดและวันหมดอายุ
   - ลิงก์ในอีเมล: `https://.../esign/sign/{stepId}`
2. **การเข้าสู่ระบบผ่าน KKU SSO:**
   - เมื่อกรรมการคลิกลิงก์ หากยังไม่มี Session ระบบจะจำ URL ปลายทางและส่งต่อไปยัง KKU SSO
   - กรรมการเข้าสู่ระบบด้วยบัญชี KKU (SSO)
   - `SsoAccessPolicy` ตรวจสอบว่า Email ตรงกับกรรมการที่มีคิวลงนาม ➔ อนุมัติการเข้าใช้งานและพากลับมาที่หน้าลงนามเอกสารนั้นทันที
3. **การลงนาม:**
   - กรรมการตรวจดูเนื้อหาเอกสารฉบับจริง (Freeze Snapshot PDF)
   - วาดลายเซ็นบน iPad / คอมพิวเตอร์ และกดยืนยันการลงนาม
   - ระบบประทับลายเซ็นพร้อม QR Code และบันทึก Audit Trail

---

## 📋 แผนการพัฒนาทีละขั้นตอน (Step-by-Step Task Breakdown)

1. **Phase 1: Model & Repository**
   - สร้าง `AcademicCommitteeMember.java`, `CommitteeType.java`
   - สร้าง `AcademicCommitteeMemberRepository.java`
2. **Phase 2: Service & SSO Policy Integration**
   - สร้าง `AcademicCommitteeService.java`
   - ปรับปรุง `SsoAccessPolicy.java` ให้รองรับอีเมลของกรรมการ
3. **Phase 3: Formal Official Email Design**
   - พัฒนาเทมเพลตอีเมลทางการของวิทยาลัยการคอมพิวเตอร์ใน `SignatureNotifier.java`
   - แสดงวันหมดอายุ (Due Date) และการนับถอยหลังอย่างชัดเจน
4. **Phase 4: Admin UI for Committee Management**
   - สร้าง `AcademicCommitteeController.java`
   - สร้าง `templates/academic/admin/committee.html`
   - เพิ่มเมนูใน `base_academic.html`
5. **Phase 5: Automated Testing & Verification**
   - เขียน Unit Tests ครอบคลุม CRUD, SSO Policy, และการส่งอีเมล
   - รัน `./mvnw test` ตรวจสอบความถูกต้อง 100%
