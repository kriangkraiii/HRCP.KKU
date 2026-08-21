# ทะเบียนทรัพย์สินสารสนเทศ (Information Asset Inventory)
**ระบบ HRCP.KKU — มาตรฐาน ISO/IEC 27001:2022 (Control 5.9 - 5.14)**

---

## 1. ทรัพยากรซอฟต์แวร์และแอปพลิเคชัน (Software & Application Assets)

| Asset ID | ชื่อทรัพย์สิน | ประเภท | ผู้รับผิดชอบ (Owner) | ระดับชั้นความลับ (Classification) |
|---|---|---|---|---|
| **SW-01** | HRCP-KKU Core Application | Java 21 / Spring Boot 4.x | Lead Developer | Confidential |
| **SW-02** | PostgreSQL Database (hr_db) | RDBMS Engine (Port 5432) | Database Admin | Strictly Confidential |
| **SW-03** | KKU SSONext Integration | Authentication Client | Security Admin | Confidential |
| **SW-04** | Flyway Migration Scripts | Database Versioning | Dev Team | Internal |

---

## 2. ทรัพยากรข้อมูล (Data Assets)

| Asset ID | ชื่อชุดข้อมูล | คำอธิบาย | การจัดเก็บ | ชั้นความลับ | Retention Period |
|---|---|---|---|---|---|
| **DT-01** | ข้อมูลผู้ใช้และสิทธิ์ (`user_dtls`) | ชื่อ, อีเมล, ตำแหน่งวิชาการ, สิทธิ์ | PostgreSQL | Strictly Confidential | ตลอดอายุการใช้งาน |
| **DT-02** | บันทึกการปฏิบัติงาน (`admin_logs`) | Who, When, What, IP, User-Agent | PostgreSQL | Confidential | 365 วัน (min 90 วัน) |
| **DT-03** | เอกสารคำขอตำแหน่งวิชาการ | ไฟล์แนบ PDF/DOCX คำขอตำแหน่ง | Local Storage / S3 | Strictly Confidential | ตามรอบประเมิน |
| **DT-04** | ข้อมูลผลงานวิจัย / Scopus | ดึงจากระบบภายนอกเพื่อประกอบการประเมิน | PostgreSQL / Cache | Internal | ซิงค์อัตโนมัติรายวัน |

---

## 3. ทรัพยากรโครงสร้างพื้นฐาน (Infrastructure Assets)

| Asset ID | ชื่อเครื่อง/โฮสต์ | วัตถุประสงค์ | ระบบปฏิบัติการ | IP Address / Domain |
|---|---|---|---|---|
| **INF-01** | Production Server | Application & DB Host | Windows / Linux Container | 10.198.200.84 |
| **INF-02** | Cloud Storage Bucket | จัดเก็บไฟล์เอกสารแนบ | AWS S3 (ap-southeast-1) | Secure Bucket URL |
