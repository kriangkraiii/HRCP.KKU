# แผนการอัปเดต Dependency ใน pom.xml เป็นเวอร์ชันเสถียรล่าสุดสำหรับ Production

**รหัสแผน:** `PLAN-update-dependencies-stable`  
**สถานะ:** 🟢 ดำเนินการเสร็จสมบูรณ์ (Completed & Verified)  
**เป้าหมาย:** อัปเดต Dependency และ Plugins ใน `pom.xml` ที่ระบุเวอร์ชันไว้เฉพาะที่เป็น **เวอร์ชันเต็มและเสถียร (Full Stable / GA Releases)** สำหรับใช้งานใน Production ปราศจากเวอร์ชันทดลอง (No Beta, No RC, No Milestone, No Dev)

---

## 1.  สรุปการวิเคราะห์ Dependency ในโปรเจคปัจจุบัน

### 1.1 กลุ่ม Spring Boot Starter (Managed by Parent)
- `<parent>`: `spring-boot-starter-parent` ปัจจุบันใช้ **`4.1.1`** ซึ่งเป็นเวอร์ชันเต็ม (GA) ล่าสุด (เวอร์ชัน `4.2.0-M1` ยังเป็น Milestone ทดลอง **ไม่ควรอัปเดต**)
- Dependencies ภายใต้ Spring Boot (เช่น `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-thymeleaf`, `spring-boot-starter-validation`, `spring-boot-starter-mail`, `spring-boot-starter-actuator`) **ถูกต้องแล้วที่ไม่มี `<version>` กำกับ** เพื่อให้ Spring จัดการความเข้ากันได้แบบ 100%

### 1.2 รายการ Third-Party Dependencies ที่ระบุ `<version>` และวิเคราะห์ผลการอัปเดต

| ลำดับ | Group & Artifact ID | เวอร์ชันเดิม | เวอร์ชัน Stable ที่อัปเดต | สถานะ |
| :---: | :--- | :---: | :---: | :--- |
| 1 | `com.amazonaws:aws-java-sdk-s3` | `1.12.700` | **`1.12.797`** | [YES] Stable Full Release (อัปเดตแล้ว) |
| 2 | `org.jodconverter:jodconverter-local` | `4.4.7` | **`4.4.11`** | [YES] Stable Full Release (อัปเดตแล้ว) |
| 3 | `fr.opensagres.xdocreport:fr.opensagres.poi.xwpf.converter.pdf` | `2.1.0` | **`2.2.0`** | [YES] Stable Full Release (อัปเดตแล้ว) |
| 4 | `net.jqwik:jqwik` *(test scope)* | `1.8.2` | **`1.10.1`** | [YES] Stable Full Release (อัปเดตแล้ว) |
| 5 | `org.owasp:dependency-check-maven` *(plugin)* | `12.1.0` | **`13.0.0`** | [YES] Stable Full Release (อัปเดตแล้ว) |
| 6 | `com.deepoove:poi-tl` | `1.12.2` | **`1.12.2` (คงเดิม)** |  ตรวจพบ `1.12.3-beta1` เป็นรุ่น Beta จึงคง `1.12.2` ที่เสถียรไว้ |
| 7 | `org.passay:passay` | `2.0.0` | **`2.0.0` (คงเดิม)** | [YES] ล่าสุดและเสถียรแล้ว |
| 8 | `com.google.zxing:core` & `javase` | `3.5.4` | **`3.5.4` (คงเดิม)** | [YES] ล่าสุดและเสถียรแล้ว |
| 9 | `net.lingala.zip4j:zip4j` | `2.11.6` | **`2.11.6` (คงเดิม)** | [YES] ล่าสุดและเสถียรแล้ว |
| 10 | `org.apache.poi:poi-ooxml` & `full` | `5.5.1` | **`5.5.1` (คงเดิม)** | [YES] ล่าสุดและเสถียรแล้ว |

---

## 2.  บันทึกผลการดำเนินงาน (Task Execution)

- [x] **Task 1: อัปเดต `pom.xml`**
  - ปรับปรุงเลขเวอร์ชันตามตาราง Stable Releases เรียบร้อย
- [x] **Task 2: ตรวจสอบและคอมไพล์ (`mvn compile` & `mvn test-compile`)**
  - ดาวน์โหลดและคอมไพล์ผ่าน 100% BUILD SUCCESS
- [x] **Task 3: รันทดสอบระบบทั้งหมด (`./mvnw test`)**
  - ชุดทดสอบทั้งหมด 835 tests ผ่าน 100% BUILD SUCCESS (Failures: 0, Errors: 0)

