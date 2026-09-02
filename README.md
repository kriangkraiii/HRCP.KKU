# HRCP KKU - ระบบสารสนเทศการขอกำหนดตำแหน่งทางวิชาการ
### Academic Position Management & Processing System
**วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น (College of Computing, Khon Kaen University)**

---

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x%20%2F%204.x-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Thymeleaf](https://img.shields.io/badge/Thymeleaf-3.x-005F0F?style=for-the-badge&logo=thymeleaf&logoColor=white)](https://www.thymeleaf.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![Security](https://img.shields.io/badge/Compliance-ISMS%20%2F%20ISO%2027001-blueviolet?style=for-the-badge)](docs/isms/)

---

## สารบัญ (Table of Contents)

1. [ภาพรวมของระบบ (Overview)](#ภาพรวมของระบบ-overview)
2. [ฟังก์ชันการทำงานหลัก (Key Features)](#ฟังก์ชันการทำงานหลัก-key-features)
3. [เทคโนโลยีและสถาปัตยกรรม (Tech Stack & Architecture)](#เทคโนโลยีและสถาปัตยกรรม-tech-stack--architecture)
4. [โครงสร้างไดเรกทอรี (Project Structure)](#โครงสร้างไดเรกทอรี-project-structure)
5. [การติดตั้งและรันในเครื่องพัฒนา (Local Development)](#การติดตั้งและรันในเครื่องพัฒนา-local-development)
6. [การติดตั้งและ Deploy บน Production (Docker Deployment)](#การติดตั้งและ-deploy-บน-production-docker-deployment)
7. [ระบบสำรองและกู้คืนข้อมูล (Backup & Disaster Recovery)](#ระบบสำรองและกู้คืนข้อมูล-backup--disaster-recovery)
8. [ความมั่นคงปลอดภัยและการปฏิบัติตามมาตรฐาน (Security & Compliance)](#ความมั่นคงปลอดภัยและการปฏิบัติตามมาตรฐาน-security--compliance)
9. [ดัชนีเอกสารและคู่มือ (Documentation Index)](#ดัชนีเอกสารและคู่มือ-documentation-index)

---

## ภาพรวมของระบบ (Overview)

**HRCP KKU** เป็นระบบสารสนเทศสำหรับการบริหารจัดการและติดตามคำขอกำหนดตำแหน่งทางวิชาการ (ผู้ช่วยศาสตราจารย์, รองศาสตราจารย์, ศาสตราจารย์) ของคณาจารย์และบุคลากรสายวิชาการ วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น โดยพัฒนากระบวนการแบบครบวงจร (End-to-End Paperless Workflow) ตั้งแต่การยื่นคำขอ, การตรวจสอบคุณสมบัติ, การลงนามอิเล็กทรอนิกส์, การออกเอกสารแบบประเมินและเอกสารทางการ (DOCX/PDF) ไปจนถึงการส่งต่อข้อมูลเข้าสู่ระบบบริหารงานบุคคล

---

## ฟังก์ชันการทำงานหลัก (Key Features)

### 1. Academic Request & Workflow Management
- **ระบบยื่นและติดตามคำขอ:** คณาจารย์สามารถยื่นคำขอกำหนดตำแหน่งทางวิชาการ พร้อมแนบเอกสารหลักฐาน ตรวจสอบสถานะแบบเรียลไทม์ (Status Tracker)
- **ระบบกำหนดรหัสคำขออัตโนมัติ:** รูปแบบมาตรฐาน `KKU-ACAD-YYYYMM-XXXX` ป้องกันความซ้ำซ้อนและรองรับการค้นหา
- **Multi-Role Flow:** รองรับสิทธิ์การใช้งานแบบแยกหน้าที่อย่างรัดกุม (Applicant, Staff, Department Head, Dean/Executive, System Admin)

### 2. Digital Signature & Approval Matrix (e-Signature)
- **ระบบลงนามอิเล็กทรอนิกส์ตามลำดับขั้น:** รองรับการเซ็นกำกับและการลงนามอนุมัติตาม Matrix สายงานบริหาร
- **ลายน้ำและตราประทับเวลา (Timestamping):** บันทึกประวัติการลงนามเพื่อป้องกันการปฏิเสธความรับผิดชอบ (Non-repudiation)

### 3. Automated Document Generation (Pure ZIP/XML & PDF Conversion)
- **สร้างเอกสาร DOCX อัตโนมัติ:** เทคโนโลยี Pure ZIP/XML Stream สำหรับแทนที่ตัวแปรใน Template โดยรักษาฟอร์แมตและความสวยงาม 100%
- **แปลงไฟล์เป็น PDF ความละเอียดสูง:** เชื่อมต่อ LibreOffice Engine ร่วมกับฟอนต์มาตรฐาน **TH Sarabun PSK** และระบบแปลงตัวเลขอารบิกเป็นเลขไทย
- **ระบบพรีวิวเอกสารความเร็วสูง:** แคชเอกสารและเรนเดอร์พรีวิวอย่างรวดเร็ว

### 4. External Integration & Data Harvesting
- **Scopus API Harvesting:** ดึงข้อมูลบทความวิจัย, ผู้แต่ง, Quartile (Q1–Q4), CiteScore, และค่าดัชนีชี้วัดอัตโนมัติ
- **SSO Authentication:** รองรับ Single Sign-On ร่วมกับระบบของมหาวิทยาลัยขอนแก่น
- **External User Synchronization:** ระบบจับคู่และซิงค์ข้อมูลบุคลากรภายนอก

### 5. Security, Audit Logging & ISMS Compliance
- **Audit Logging:** บันทึกประวัติการจราจรและกิจกรรมสำคัญตามพระราชบัญญัติว่าด้วยการกระทำความผิดเกี่ยวกับคอมพิวเตอร์
- **Password Complexity:** ตรวจสอบความปลอดภัยของรหัสผ่านตามมาตรฐาน ISO 27001 (Passay 2.0.0)
- **Security Headers & Rate Limiting:** ป้องกัน Brute-force Attacks, CSRF และ XSS

---

## เทคโนโลยีและสถาปัตยกรรม (Tech Stack & Architecture)

| Layer | Technologies & Libraries | รายละเอียด |
|---|---|---|
| **Backend** | Java 21, Spring Boot 3.x / 4.x | Spring MVC, Spring Data JPA, Spring Security, Spring Mail |
| **Frontend / Template** | Thymeleaf, HTML5, Vanilla CSS, JS | เรนเดอร์ฝั่งเซิร์ฟเวอร์ สะอาด น้ำหนักเบา ปลอดภัย |
| **Caching** | Caffeine Cache | แคชการประมวลผลเอกสารและข้อมูลผลงานวิจัย |
| **Database** | PostgreSQL 16 | ฐานข้อมูลเชิงสัมพันธ์ พร้อม Indexing Strategy |
| **Document Processing** | LibreOffice, Pure XML/ZIP Engine | สร้าง DOCX และแปลงเป็น PDF พร้อมฟอนต์ TH Sarabun |
| **Container & Deploy** | Docker, Docker Compose, Bash | 3 Containers (`app`, `db`, `backup`) แยกหน้าที่ชัดเจน |

---

## โครงสร้างไดเรกทอรี (Project Structure)

```text
HRCP.KKU (Project Root)
├── deploy.sh                           # สคริปต์หลักสำหรับ Deploy บน Production
├── HRCP-KKU-Academic/                  # Source Code โปรเจกต์ Spring Boot
│   ├── src/main/java/com/ecom/         # Java Packages (Controllers, Services, Models, Repositories)
│   ├── src/main/resources/             # Templates (Thymeleaf), Static assets, Application configs
│   ├── src/test/java/                  # Unit & Integration Test Suites
│   ├── pom.xml                         # Maven Dependencies & Build Configuration
│   └── Dockerfile                      # Multi-stage Docker build สำหรับแอปพลิเคชัน
└── docs/                               # คลังเอกสารและทรัพยากรระบบ
    ├── api-integrations/               # เอกสารเชื่อมต่อ Scopus, SSO, External Users
    │   ├── EXTERNAL_API_SCOPUS.md
    │   ├── EXTERNAL_DATA_MAPPING_ANALYSIS.md
    │   ├── EXTERNAL_USERS_API.md
    │   ├── SCOPUS_DATA_DICTIONARY.md
    │   └── SSO_SETUP.md
    ├── database/                       # สคริปต์ SQL (Migrations & Seed Data)
    │   ├── migrations/
    │   │   └── add_database_indexes.sql
    │   └── seeds/
    │       └── roles_positions_users_combined.sql
    ├── isms/                           # เอกสารนโยบายความมั่นคงปลอดภัยสารสนเทศ (ISO 27001)
    │   ├── 01-information-security-policy.md
    │   ├── 02-risk-assessment.md
    │   ├── 03-incident-response-plan.md
    │   ├── 04-asset-inventory.md
    │   └── 05-access-control-policy.md
    ├── plans/                          # แผนงานและการพัฒนาฟีเจอร์ (PLAN-*.md)
    ├── reports/                        # ผลการ Audit, รายงานข้อผิดพลาด และ Security JSON
    ├── references/                     # เอกสารอ้างอิงและขั้นตอนการขอกำหนดตำแหน่ง (PDF)
    ├── data-request-research/          # สเปกและโค้ดเครื่องมือขอข้อมูลงานวิจัย
    └── deployment.md                   # คู่มือและรายละเอียดการ Deploy เชิงลึก
```

---

## การติดตั้งและรันในเครื่องพัฒนา (Local Development)

### ข้อกำหนดของระบบ (Prerequisites)
- **JDK 21** (แนะนำ Eclipse Temurin หรือ Oracle OpenJDK 21)
- **Maven 3.9+** (หรือใช้ Maven Wrapper ในโปรเจกต์)
- **PostgreSQL 16** (ติดตั้งในเครื่อง หรือรันผ่าน Docker)
- **LibreOffice** (จำเป็นสำหรับการทดสอบฟังก์ชันแปลง DOCX เป็น PDF)

### ขั้นตอนการรันแอปพลิเคชัน:

1. **Clone Repository:**
   ```bash
   git clone <repository-url>
   cd HRCP.KKU/HRCP-KKU-Academic
   ```

2. **ตั้งค่าคอนฟิกฐานข้อมูล (`application-dev.properties` หรือตัวแปรสภาพแวดล้อม):**
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/hrcp_db
   spring.datasource.username=postgres
   spring.datasource.password=your_password
   ```

3. **Import Seed Data (ตัวอย่างข้อมูลบุคลากรและบทบาท):**
   ```bash
   psql -U postgres -d hrcp_db -f ../docs/database/seeds/roles_positions_users_combined.sql
   ```

4. **Build และรัน Spring Boot:**
   ```bash
   ./mvnw clean spring-boot:run
   ```
   เข้าใช้งานผ่านเบราว์เซอร์ที่: `http://localhost:8080`

---

## การติดตั้งและ Deploy บน Production (Docker Deployment)

ระบบถูกออกแบบให้ทำงานแบบแยก 3 Container ภายใต้ Network เดียวกันเพื่อความปลอดภัยสูงสุด โดย Database จะไม่ Publish Port ออกภายนอก Server

| Container | Image | หน้าที่ |
|---|---|---|
| `app` | `hrcp-academic:latest` | Spring Boot + LibreOffice + ฟอนต์ TH Sarabun PSK |
| `db` | `postgres:16` | ฐานข้อมูลหลัก (เก็บข้อมูลใน Named Volume `pgdata`) |
| `backup` | `postgres:16` | รัน Cron สำรองฐานข้อมูลอัตโนมัติ |

### การ Deploy ด้วย `deploy.sh`:

1. จากเครื่องพัฒนา รันคำสั่ง:
   ```bash
   ./deploy.sh
   ```
2. สคริปต์จะทำการ:
   - Build Image `hrcp-academic:latest`
   - ส่ง Image และโค้ดไปยัง Server ปลายทาง
   - สั่ง Restart Container `app` โดยไม่กระทบต่อ Container `db` ทำให้ข้อมูลและบริการทำงานได้อย่างต่อเนื่อง

> อ่านขั้นตอนการติดตั้ง Server ใหม่และการตั้งค่าโดยละเอียดได้ที่ [docs/deployment.md](docs/deployment.md)

---

## ระบบสำรองและกู้คืนข้อมูล (Backup & Disaster Recovery)

Container `backup` จะทำการรัน `pg_dump` อัตโนมัติทุกวันตามเวลาที่กำหนดใน `.env` (ค่าเริ่มต้น 02:00 น. เวลาไทย) และเก็บย้อนหลังตามจำนวนวันที่ตั้งไว้ (ค่าเริ่มต้น 14 วัน)

### คำสั่งที่ใช้บ่อย:
```bash
# ตรวจสอบประวัติและรอบการสำรองข้อมูล
docker compose logs -f backup

# แสดงรายการไฟล์ Backup ที่มีอยู่ใน Volume
docker compose run --rm --entrypoint ls backup -lh /backups

# กู้คืนข้อมูลจากไฟล์ Backup (สคริปต์จะหยุดแอปชั่วคราวอัตโนมัติเพื่อความปลอดภัยของข้อมูล)
./docker/backup/restore.sh hrcp-YYYYMMDD-HHMMSS.sql.gz
```

---

## ความมั่นคงปลอดภัยและการปฏิบัติตามมาตรฐาน (Security & Compliance)

- **ISMS (ISO/IEC 27001):** มีชุดเอกสารนโยบายความมั่นคงปลอดภัยสารสนเทศ การประเมินความเสี่ยง แผนรับมือภัยคุกคาม และการควบคุมการเข้าถึงใน [docs/isms/](docs/isms/)
- **Activity & Traffic Audit Log:** ระบบ Middleware บันทึก Access Log และ Audit Trail ตามข้อกำหนดของ พ.ร.บ. ว่าด้วยการกระทำความผิดเกี่ยวกับคอมพิวเตอร์ (เก็บรักษาข้อมูลไม่น้อยกว่า 90 วัน)
- **Role-Based Access Control (RBAC):** กำหนดสิทธิ์การเข้าถึงข้อมูลตามหน้าที่อย่างเคร่งครัด
- **Data Protection:** ข้อมูลรหัสผ่านถูกเข้ารหัสด้วย BCrypt และควบคุมความซับซ้อนตามมาตรฐานสากล

---

## ดัชนีเอกสารและคู่มือ (Documentation Index)

| หมวดหมู่ | เอกสาร | รายละเอียด |
|---|---|---|
| **การติดตั้ง & Deploy** | [docs/deployment.md](docs/deployment.md) | คู่มือการตั้งค่า Server, Docker Compose และการ Deploy |
| **API & เชื่อมต่อภายนอก** | [docs/api-integrations/EXTERNAL_API_SCOPUS.md](docs/api-integrations/EXTERNAL_API_SCOPUS.md) | สเปกและการเรียกใช้งาน Scopus API |
| | [docs/api-integrations/SCOPUS_DATA_DICTIONARY.md](docs/api-integrations/SCOPUS_DATA_DICTIONARY.md) | พจนานุกรมข้อมูล Scopus Data Dictionary |
| | [docs/api-integrations/SSO_SETUP.md](docs/api-integrations/SSO_SETUP.md) | คู่มือการติดตั้งและเชื่อมต่อระบบ Single Sign-On |
| | [docs/api-integrations/EXTERNAL_DATA_MAPPING_ANALYSIS.md](docs/api-integrations/EXTERNAL_DATA_MAPPING_ANALYSIS.md) | การวิเคราะห์การจับคู่ข้อมูลภายนอก |
| **ฐานข้อมูล** | [docs/database/migrations/add_database_indexes.sql](docs/database/migrations/add_database_indexes.sql) | สคริปต์ Indexing เพื่อเพิ่มประสิทธิภาพ DB |
| | [docs/database/seeds/roles_positions_users_combined.sql](docs/database/seeds/roles_positions_users_combined.sql) | ข้อมูลเริ่มต้น Seed บุคลากรและตำแหน่ง |
| **นโยบาย ISMS** | [docs/isms/](docs/isms/) | รวมนโยบายความมั่นคงปลอดภัยสารสนเทศ (01–05) |
| **แผนงานโครงการ** | [docs/plans/](docs/plans/) | รวมเอกสารแผนงาน Task Plans ทั้งหมด |
| **รายงานและผล Audit** | [docs/reports/](docs/reports/) | ผลการทดสอบ Audit, Bug Reports, Security JSON |

---

## หน่วยงานผู้ดูแลระบบ (Maintainers)

**วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น (College of Computing, Khon Kaen University)**  
- 123 หมู่ 16 ถ.มิตรภาพ ต.ในเมือง อ.เมือง จ.ขอนแก่น 40002  
- Website: [https://computing.kku.ac.th](https://computing.kku.ac.th)  
- Contact / Support: ทีมพัฒนาระบบสารสนเทศ วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น
