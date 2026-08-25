# 📋 แผนการดำเนินการลบตารางฐานข้อมูลและโมเดลที่ไม่ได้ใช้งาน (PLAN-drop-unused-tables.md)

> **เป้าหมาย:** ลบตาราง `petitions` และ `petition_statuses` ออกจากฐานข้อมูล และลบไฟล์ Enum/Model เก่าที่หลงเหลือ (`StatusType.java`) อย่างปลอดภัย โดยไม่กระทบต่อประวัติ Flyway migration และปรับปรุง Unit Test / Documentation ให้สอดคล้องกัน

---

## 🎯 1. รายละเอียดขอบเขตงาน (Scope of Work)

| รายการ | สิ่งที่ต้องทำ | วัตถุประสงค์ / ผลลัพธ์ |
|:---|:---|:---|
| **1. Flyway Migration (V11)** | สร้างไฟล์ `V11__drop_unused_petition_tables.sql` | สั่ง `DROP TABLE IF EXISTS` เพื่อลบตารางออกจากฐานข้อมูลจริงเมื่อ Deploy/Run |
| **2. ลบ Model/Enum ค้างเก่า** | ลบไฟล์ `StatusType.java` | ลบ Enum เก่าที่เคยใช้คู่กับ `petition_statuses` ออกจากโค้ด Java |
| **3. Unit/Integration Test** | อัปเดต `FlywayMigrationRunTest.java` | ตรวจสอบว่า `V11` ทำงานได้จริง และตารางทั้งสองถูกลบออกจริง |
| **4. Clean Up Legacy SQL** | ลบไฟล์ SQL เก่าที่ค้างอยู่ (`combined_migration.sql`, `verify_indexes.sql`, `rollback.sql`) | ป้องกันความสับสนในการดูแลระบบระยะยาว |
| **5. Documentation** | อัปเดต `src/main/resources/db/migration/README.md` | บันทึกประวัติ Migration `V0` ถึง `V11` ให้เป็นปัจจุบัน |

---

## 🏗️ 2. แผนการดำเนินงานตามขั้นตอน (Step-by-Step Task Breakdown)

### Phase 1: สร้าง Migration Script `V11`
* **ไฟล์เป้าหมาย:** `src/main/resources/db/migration/V11__drop_unused_petition_tables.sql`
* **เนื้อหา DDL:**
  ```sql
  -- ============================================================
  -- V11: ลบตาราง petitions และ petition_statuses ที่ไม่ได้ใช้งาน
  -- ============================================================
  DROP TABLE IF EXISTS petition_statuses CASCADE;
  DROP TABLE IF EXISTS petitions CASCADE;
  ```

### Phase 2: ลบไฟล์ Model/Enum ที่หลงเหลือในโค้ด Java
* **ไฟล์เป้าหมายที่จะลบ:**
  - `src/main/java/com/ecom/academic/model/StatusType.java` (Enum เก่าของ petition_statuses ที่ไม่ได้ถูกเรียกใช้จากที่ใดเลย)
* **หมายเหตุเกี่ยวกับคลาส Model อื่นๆ:**
  - คลาส `Petition.java` และ `PetitionStatus.java` **เคยถูกลบออกไปแล้วในอดีต** จึงเหลือเฉพาะ Enum ตัวนี้ที่ตกค้าง

### Phase 3: ปรับปรุง Unit Test
* **ไฟล์เป้าหมาย:** `src/test/java/com/ecom/FlywayMigrationRunTest.java`
* **การแก้ไข:**
  - อัปเดต Test case ตรวจสอบว่าหลังรัน `V11` ตาราง `petitions` และ `petition_statuses` จะต้อง **ไม่อยู่ในฐานข้อมูล** (`assertThat(tables).doesNotContain("petitions", "petition_statuses")`)
  - ตรวจสอบว่าตารางที่ใช้งานจริงอื่นๆ ยังอยู่ครบถ้วน

### Phase 4: ทำความสะอาดไฟล์ Migration เสริมที่ล้าสมัย
* **ไฟล์เป้าหมายที่จะลบ:**
  - `src/main/resources/db/migration/combined_migration.sql` (ไฟล์ MySQL เก่าของ petitions)
  - `src/main/resources/db/migration/verify_indexes.sql` (สคริปต์ตรวจ index ของ petitions เก่า)
  - `src/main/resources/db/migration/rollback.sql`

### Phase 5: อัปเดตเอกสาร Migration
* **ไฟล์เป้าหมาย:** `src/main/resources/db/migration/README.md`
* **การแก้ไข:** ปรับปรุงตารางสารบัญ Migration ให้ระบุถึง `V11` และอธิบายการเปลี่ยนแปลง

---

## 🛡️ 3. ข้อควรระวังและการป้องกันความเสี่ยง (Risk Mitigation)

1. **ห้ามลบไฟล์ `V1` และ `V2` ออกจากโฟลเดอร์:**
   - *เหตุผล:* Flyway บันทึก Checksum ของไฟล์ `V1` และ `V2` ไว้ใน `flyway_schema_history` ของฐานข้อมูล Production แล้ว หากลบไฟล์เดิมทิ้ง Flyway จะฟ้อง Validation Error และแอปจะสตาร์ตไม่ขึ้น
   - *วิธีแก้ที่ถูกต้อง:* คงไฟล์ `V1`/`V2` ไว้ และใช้ `V11` ในการสั่ง Drop ตาราง
2. **PostgreSQL CASCADE Drop:**
   - ใช้ `CASCADE` เพื่อให้ระบบลบ Foreign Key และ Index ที่เกี่ยวข้องของตาราง `petitions` ออกทั้งหมดอย่างหมดจด

---

## 📋 4. Verification Checklist (การตรวจสอบหลังดำเนินการ)

- [ ] ไฟล์ `V11__drop_unused_petition_tables.sql` ถูกสร้างอย่างถูกต้อง
- [ ] ไฟล์ `StatusType.java` ถูกลบเรียบร้อย และโปรเจกต์ Build ผ่าน
- [ ] รันการทดสอบ `mvn test -Dtest=FlywayMigrationRunTest` ผ่าน 100%
- [ ] รันคำสั่งตรวจสอบ Schema พบว่าตาราง `petitions` และ `petition_statuses` หายไปจากฐานข้อมูล
- [ ] แอปพลิเคชัน Spring Boot สตาร์ตได้ปกติ (`ddl-auto=validate` หรือ `update` ไม่พบ Error)
