# Database Migration Scripts

## ภาพรวม

โฟลเดอร์นี้ประกอบด้วย SQL migration scripts สำหรับระบบจัดการสถานะคำร้อง (Petition Status Management System)

## ไฟล์ Migration

### V1__create_petition_statuses_table.sql
สร้างตาราง `petition_statuses` สำหรับเก็บประวัติสถานะของคำร้อง

**คอลัมน์:**
- `id`: Primary key (BIGINT, AUTO_INCREMENT)
- `petition_id`: Foreign key ไปยังตาราง petitions (BIGINT, NOT NULL)
- `status_type`: ประเภทสถานะ (VARCHAR(50), NOT NULL)
- `note`: หมายเหตุเพิ่มเติม (TEXT, nullable)
- `created_at`: วันเวลาที่สร้างสถานะ (TIMESTAMP, NOT NULL)

**Indexes:**
- `idx_petition_status_petition_id`: Index บน petition_id สำหรับการค้นหาสถานะของคำร้อง
- `idx_petition_status_created_at`: Index บน created_at สำหรับการเรียงลำดับตามเวลา
- `idx_petition_status_composite`: Composite index บน (petition_id, created_at) สำหรับ query ที่ใช้ทั้งสองคอลัมน์

### V2__create_petitions_table.sql
สร้างตาราง `petitions` สำหรับเก็บข้อมูลคำร้อง (ถ้ายังไม่มี)

**คอลัมน์:**
- `id`: Primary key (BIGINT, AUTO_INCREMENT)
- `user_id`: Foreign key ไปยังตาราง user_dtls (BIGINT, NOT NULL)
- `title`: หัวข้อคำร้อง (VARCHAR(255), NOT NULL)
- `description`: รายละเอียดคำร้อง (TEXT, nullable)
- `created_at`: วันเวลาที่สร้างคำร้อง (TIMESTAMP, NOT NULL)

**Indexes:**
- `idx_petition_user_id`: Index บน user_id สำหรับการค้นหาคำร้องของผู้ใช้
- `idx_petition_created_at`: Index บน created_at สำหรับการเรียงลำดับตามเวลา

## วิธีการใช้งาน

### ตัวเลือกที่ 1: รัน Scripts แยกกัน

```bash
# เชื่อมต่อกับ MySQL
mysql -u root -p ecommerce_db

# รัน migration scripts ตามลำดับ
source src/main/resources/db/migration/V2__create_petitions_table.sql
source src/main/resources/db/migration/V1__create_petition_statuses_table.sql
```

### ตัวเลือกที่ 2: รัน Combined Script

```bash
mysql -u root -p ecommerce_db < src/main/resources/db/migration/combined_migration.sql
```

### ตัวเลือกที่ 3: ใช้ Hibernate Auto-DDL (แนะนำสำหรับ Development)

โปรเจกต์นี้ใช้ `spring.jpa.hibernate.ddl-auto=update` ใน application.properties ซึ่งจะสร้างตารางอัตโนมัติเมื่อรัน application

**หมายเหตุ:** สำหรับ production ควรใช้ migration scripts แทนการพึ่งพา Hibernate DDL

## การติดตั้ง Flyway (Optional)

หากต้องการใช้ Flyway สำหรับจัดการ migrations อัตโนมัติ:

1. เพิ่ม dependency ใน pom.xml:
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

2. เปลี่ยนชื่อไฟล์ให้ตรงกับ Flyway naming convention:
   - `V1__create_petition_statuses_table.sql` (ถูกต้องแล้ว)
   - `V2__create_petitions_table.sql` (ถูกต้องแล้ว)

3. เพิ่ม configuration ใน application.properties:
```properties
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.jpa.hibernate.ddl-auto=validate
```

## Performance Considerations

### Indexes ที่สร้างขึ้น

1. **idx_petition_status_petition_id**: เร่งความเร็วการค้นหาสถานะทั้งหมดของคำร้อง
2. **idx_petition_status_created_at**: เร่งความเร็วการเรียงลำดับตามเวลา
3. **idx_petition_status_composite**: เร่งความเร็ว query ที่ต้องการทั้ง petition_id และ created_at (เช่น การหาสถานะล่าสุด)
4. **idx_petition_user_id**: เร่งความเร็วการค้นหาคำร้องของผู้ใช้
5. **idx_petition_created_at**: เร่งความเร็วการเรียงลำดับคำร้องตามเวลา

### Query Optimization

Indexes เหล่านี้ถูกออกแบบมาเพื่อรองรับ queries ที่สำคัญ:
- หาคำร้องที่กำลังดำเนินการของผู้ใช้ (active petition check)
- หาสถานะล่าสุดของคำร้อง (current status)
- แสดงประวัติสถานะเรียงตามเวลา (status timeline)

## Rollback

หากต้องการ rollback การเปลี่ยนแปลง:

```sql
DROP TABLE IF EXISTS petition_statuses;
DROP TABLE IF EXISTS petitions;
```

**คำเตือน:** การ rollback จะลบข้อมูลทั้งหมดในตาราง ควรสำรองข้อมูลก่อน

## Requirements Mapping

- **Requirement 3.3**: การบันทึกสถานะพร้อม timestamp
  - ตาราง petition_statuses มีคอลัมน์ created_at พร้อม default CURRENT_TIMESTAMP
  - Indexes บน created_at เพื่อ performance ในการเรียงลำดับ
