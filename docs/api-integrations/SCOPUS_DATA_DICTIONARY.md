# Data Dictionary: Scopus

เอกสารนี้อธิบายเฉพาะตารางฐานข้อมูลที่ขึ้นต้นด้วย `scopus_` ในระบบ Fund Management โดยอ้างอิงจาก `db/fund_cpkku_structure.sql` และ migrations ที่เกี่ยวข้องกับข้อมูล conference

> ขอบเขต: ไม่รวมฟีเจอร์ **Scopus Benchmark**, ตารางของโมดูลอื่น และคอลัมน์ `users.scopus_id`

## ภาพรวมตาราง

| ตาราง | วัตถุประสงค์ |
|---|---|
| `scopus_documents` | เก็บข้อมูลผลงานตีพิมพ์ที่นำเข้าจาก Scopus |
| `scopus_authors` | เก็บข้อมูลผู้แต่งจาก Scopus |
| `scopus_affiliations` | เก็บข้อมูลหน่วยงานต้นสังกัดของผู้แต่ง |
| `scopus_document_authors` | ตารางเชื่อมผลงาน ผู้แต่ง และต้นสังกัด |
| `scopus_source_metrics` | เก็บตัวชี้วัดของแหล่งตีพิมพ์ เช่น CiteScore, SJR และ SNIP |
| `scopus_config` | เก็บค่าตั้งต้นสำหรับการเชื่อมต่อ/ทำงานกับ Scopus |
| `scopus_api_import_jobs` | เก็บสถานะงานนำเข้าข้อมูลผ่าน Scopus API |
| `scopus_api_requests` | เก็บรายละเอียดคำขอ API ภายใต้งานนำเข้า |
| `scopus_batch_import_runs` | เก็บสรุปผลการนำเข้าแบบหลายผู้ใช้ต่อหนึ่งรอบ |
| `scopus_conference_fetch_runs` | เก็บสรุปผลการดึงรายละเอียด conference ต่อหนึ่งรอบ |

## สัญลักษณ์ Key

- `PK` = Primary Key
- `FK` = Foreign Key
- `UK` = Unique Key
- `IDX` = Index
- `AI` = Auto Increment

## 1. `scopus_documents`

เก็บข้อมูลผลงานตีพิมพ์หลักที่ได้รับจาก Scopus Search API และรายละเอียด conference ที่เติมจาก Abstract Retrieval API

| คอลัมน์ | ชนิดข้อมูล | Key | Null | ค่าเริ่มต้น | คำอธิบาย |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AI | ไม่ได้ | - | รหัสภายในของผลงาน |
| `eid` | `VARCHAR(64)` | UK | ไม่ได้ | - | Electronic Identifier ของผลงานใน Scopus |
| `scopus_id` | `VARCHAR(64)` | - | ได้ | `NULL` | รหัสผลงาน Scopus เช่นค่าจาก `dc:identifier` |
| `scopus_link` | `TEXT` | - | ได้ | `NULL` | URL สำหรับเปิดระเบียนผลงานใน Scopus |
| `title` | `TEXT` | - | ได้ | `NULL` | ชื่อผลงาน |
| `abstract` | `LONGTEXT` | - | ได้ | `NULL` | บทคัดย่อ |
| `aggregation_type` | `VARCHAR(32)` | - | ได้ | `NULL` | ประเภทแหล่งรวม เช่น Journal หรือ Conference Proceeding |
| `subtype` | `VARCHAR(32)` | - | ได้ | `NULL` | รหัสประเภทย่อยของผลงาน เช่น `ar`, `cp` |
| `subtype_description` | `TEXT` | - | ได้ | `NULL` | คำอธิบายประเภทย่อย เช่น Article หรือ Conference Paper |
| `source_id` | `VARCHAR(32)` | IDX | ได้ | `NULL` | รหัสแหล่งตีพิมพ์ใน Scopus |
| `publication_name` | `TEXT` | - | ได้ | `NULL` | ชื่อวารสาร หนังสือ หรือ proceedings |
| `issn` | `VARCHAR(32)` | - | ได้ | `NULL` | ISSN ของสิ่งพิมพ์ |
| `eissn` | `VARCHAR(32)` | - | ได้ | `NULL` | Electronic ISSN |
| `isbn` | `VARCHAR(64)` | - | ได้ | `NULL` | ISBN ของหนังสือหรือ proceedings |
| `volume` | `VARCHAR(32)` | - | ได้ | `NULL` | เล่มที่ |
| `issue` | `VARCHAR(32)` | - | ได้ | `NULL` | ฉบับที่ |
| `page_range` | `VARCHAR(64)` | - | ได้ | `NULL` | ช่วงหน้า |
| `article_number` | `VARCHAR(64)` | - | ได้ | `NULL` | เลขบทความ |
| `cover_date` | `DATE` | IDX | ได้ | `NULL` | วันที่เผยแพร่ตาม Scopus |
| `cover_display_date` | `TEXT` | - | ได้ | `NULL` | วันที่เผยแพร่ในรูปแบบข้อความจาก Scopus |
| `doi` | `VARCHAR(255)` | IDX | ได้ | `NULL` | Digital Object Identifier |
| `pii` | `VARCHAR(64)` | - | ได้ | `NULL` | Publisher Item Identifier |
| `citedby_count` | `INT` | - | ได้ | `NULL` | จำนวนครั้งที่ถูกอ้างอิง |
| `openaccess` | `TINYINT` | - | ได้ | `NULL` | ค่าสถานะ Open Access จาก Scopus |
| `openaccess_flag` | `TINYINT(1)` | - | ได้ | `NULL` | ธงระบุว่าเป็น Open Access หรือไม่ |
| `authkeywords` | `LONGTEXT` (JSON) | - | ได้ | `NULL` | คำสำคัญของผู้แต่งในรูป JSON array |
| `fund_acr` | `TEXT` | - | ได้ | `NULL` | ตัวย่อหน่วยงานผู้ให้ทุน |
| `fund_sponsor` | `TEXT` | - | ได้ | `NULL` | ชื่อผู้สนับสนุนทุน |
| `conference_name` | `TEXT` | - | ได้ | `NULL` | ชื่องานประชุมวิชาการ |
| `conference_venue` | `TEXT` | - | ได้ | `NULL` | สถานที่จัดงานประชุม |
| `conference_city` | `VARCHAR(255)` | IDX | ได้ | `NULL` | เมืองที่จัดงานประชุม |
| `conference_country` | `VARCHAR(64)` | IDX | ได้ | `NULL` | ประเทศที่จัดงานประชุม |
| `conference_location` | `TEXT` | - | ได้ | `NULL` | ข้อมูลสถานที่จัดงานในรูปข้อความรวม |
| `conference_info_json` | `LONGTEXT` (JSON) | - | ได้ | `NULL` | รายละเอียด conference ฉบับเต็มในรูป JSON |
| `conference_info_fetched_at` | `DATETIME` | - | ได้ | `NULL` | วันเวลาที่ดึงรายละเอียด conference ล่าสุด |
| `raw_json` | `LONGTEXT` (JSON) | - | ได้ | `NULL` | ข้อมูลดิบของรายการจาก Scopus API |
| `created_at` | `DATETIME` | - | ไม่ได้ | `CURRENT_TIMESTAMP` | วันเวลาที่สร้างข้อมูล |
| `updated_at` | `DATETIME` | - | ไม่ได้ | `CURRENT_TIMESTAMP` | วันเวลาที่แก้ไขล่าสุด ปรับอัตโนมัติเมื่อ update |

ข้อกำหนดสำคัญ: `eid` ต้องไม่ซ้ำ และคอลัมน์ JSON ต้องผ่าน `JSON_VALID()`

## 2. `scopus_authors`

เก็บข้อมูลผู้แต่งที่พบในผลงาน Scopus โดยใช้ Scopus Author ID เป็นรหัสอ้างอิงหลักทางธุรกิจ

| คอลัมน์ | ชนิดข้อมูล | Key | Null | ค่าเริ่มต้น | คำอธิบาย |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AI | ไม่ได้ | - | รหัสภายในของผู้แต่ง |
| `scopus_author_id` | `VARCHAR(100)` | UK | ไม่ได้ | - | รหัสผู้แต่งใน Scopus |
| `full_name` | `TEXT` | - | ได้ | `NULL` | ชื่อผู้แต่งแบบเต็มตาม Scopus |
| `given_name` | `TEXT` | - | ได้ | `NULL` | ชื่อ |
| `surname` | `TEXT` | - | ได้ | `NULL` | นามสกุล |
| `initials` | `TEXT` | - | ได้ | `NULL` | อักษรย่อชื่อ |
| `orcid` | `VARCHAR(64)` | - | ได้ | `NULL` | ORCID ของผู้แต่ง |
| `author_url` | `TEXT` | - | ได้ | `NULL` | URL โปรไฟล์ผู้แต่งจาก Scopus |
| `created_at` | `DATETIME` | - | ไม่ได้ | `CURRENT_TIMESTAMP` | วันเวลาที่สร้างข้อมูล |
| `updated_at` | `DATETIME` | - | ไม่ได้ | `CURRENT_TIMESTAMP` | วันเวลาที่แก้ไขล่าสุด |

## 3. `scopus_affiliations`

เก็บข้อมูลหน่วยงานต้นสังกัดที่ปรากฏในผลงาน Scopus

| คอลัมน์ | ชนิดข้อมูล | Key | Null | ค่าเริ่มต้น | คำอธิบาย |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AI | ไม่ได้ | - | รหัสภายในของหน่วยงาน |
| `afid` | `VARCHAR(32)` | UK | ไม่ได้ | - | Scopus Affiliation ID |
| `name` | `TEXT` | - | ได้ | `NULL` | ชื่อหน่วยงาน |
| `city` | `TEXT` | - | ได้ | `NULL` | เมืองที่ตั้ง |
| `country` | `TEXT` | - | ได้ | `NULL` | ประเทศที่ตั้ง |
| `affiliation_url` | `TEXT` | - | ได้ | `NULL` | URL หน่วยงานจาก Scopus |
| `created_at` | `DATETIME` | - | ไม่ได้ | `CURRENT_TIMESTAMP` | วันเวลาที่สร้างข้อมูล |
| `updated_at` | `DATETIME` | - | ไม่ได้ | `CURRENT_TIMESTAMP` | วันเวลาที่แก้ไขล่าสุด |

## 4. `scopus_document_authors`

เชื่อมความสัมพันธ์แบบหลายต่อหลายระหว่างผลงานและผู้แต่ง พร้อมลำดับผู้แต่งและต้นสังกัดหลักของผู้แต่งในผลงานนั้น

| คอลัมน์ | ชนิดข้อมูล | Key | Null | ค่าเริ่มต้น | คำอธิบาย |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AI | ไม่ได้ | - | รหัสรายการเชื่อม |
| `document_id` | `BIGINT UNSIGNED` | FK, UK, IDX | ไม่ได้ | - | อ้างถึง `scopus_documents.id`; ลบตามผลงานแบบ CASCADE |
| `author_id` | `BIGINT UNSIGNED` | FK, UK, IDX | ไม่ได้ | - | อ้างถึง `scopus_authors.id`; ลบตามผู้แต่งแบบ CASCADE |
| `author_seq` | `INT` | IDX | ได้ | `NULL` | ลำดับผู้แต่งในผลงาน เริ่มจาก 1 |
| `affiliation_id` | `BIGINT UNSIGNED` | FK, IDX | ได้ | `NULL` | อ้างถึง `scopus_affiliations.id`; ตั้งเป็น `NULL` เมื่อหน่วยงานถูกลบ |
| `created_at` | `DATETIME` | - | ไม่ได้ | `CURRENT_TIMESTAMP` | วันเวลาที่สร้างข้อมูล |
| `updated_at` | `DATETIME` | - | ไม่ได้ | `CURRENT_TIMESTAMP` | วันเวลาที่แก้ไขล่าสุด |

ข้อกำหนดสำคัญ: คู่ (`document_id`, `author_id`) ต้องไม่ซ้ำ

## 5. `scopus_source_metrics`

เก็บตัวชี้วัดรายปีของแหล่งตีพิมพ์ แยกตาม `source_id` และชนิดเอกสาร

| คอลัมน์ | ชนิดข้อมูล | Key | Null | ค่าเริ่มต้น | คำอธิบาย |
|---|---|---|---|---|---|
| `source_metric_id` | `INT` | PK, AI | ไม่ได้ | - | รหัสรายการตัวชี้วัด |
| `source_id` | `VARCHAR(32)` | UK | ไม่ได้ | - | รหัสแหล่งตีพิมพ์ใน Scopus |
| `issn` | `VARCHAR(32)` | IDX | ได้ | `NULL` | ISSN |
| `eissn` | `VARCHAR(32)` | IDX | ได้ | `NULL` | Electronic ISSN |
| `metric_year` | `INT(4)` | UK, IDX | ไม่ได้ | - | ปีของตัวชี้วัด |
| `doc_type` | `VARCHAR(32)` | UK | ไม่ได้ | `'all'` | ชนิดเอกสารที่ใช้คำนวณ เช่น all, article หรือ review |
| `cite_score` | `DECIMAL(8,3)` | - | ได้ | `NULL` | ค่า CiteScore |
| `cite_score_status` | `ENUM('Complete','In-Progress')` | - | ได้ | `NULL` | สถานะความสมบูรณ์ของ CiteScore |
| `cite_score_scholarly_output` | `INT` | - | ได้ | `NULL` | จำนวน scholarly outputs ที่ใช้คำนวณ |
| `cite_score_citation_count` | `INT` | - | ได้ | `NULL` | จำนวน citation ที่ใช้คำนวณ CiteScore |
| `cite_score_percent_cited` | `DECIMAL(5,2)` | - | ได้ | `NULL` | ร้อยละของผลงานที่ได้รับการอ้างอิง |
| `cite_score_rank` | `INT` | - | ได้ | `NULL` | อันดับ CiteScore |
| `cite_score_percentile` | `DECIMAL(5,2)` | - | ได้ | `NULL` | Percentile ของ CiteScore |
| `cite_score_quartile` | `VARCHAR(4)` | - | ได้ | `NULL` | Quartile เช่น Q1, Q2, Q3 หรือ Q4 |
| `cite_score_current_metric` | `DECIMAL(8,3)` | - | ได้ | `NULL` | CiteScore metric ปัจจุบัน |
| `cite_score_current_metric_year` | `INT(4)` | - | ได้ | `NULL` | ปีของ CiteScore metric ปัจจุบัน |
| `cite_score_tracker` | `DECIMAL(8,3)` | - | ได้ | `NULL` | ค่า CiteScore Tracker |
| `cite_score_tracker_year` | `INT(4)` | - | ได้ | `NULL` | ปีของ CiteScore Tracker |
| `sjr` | `DECIMAL(8,3)` | - | ได้ | `NULL` | ค่า SCImago Journal Rank |
| `snip` | `DECIMAL(8,3)` | - | ได้ | `NULL` | ค่า Source Normalized Impact per Paper |
| `publication_count` | `INT` | - | ได้ | `NULL` | จำนวนผลงานตีพิมพ์ |
| `cite_count_sce` | `INT` | - | ได้ | `NULL` | จำนวน citation ในชุดข้อมูล SCE |
| `zero_cites_sce` | `DECIMAL(5,2)` | - | ได้ | `NULL` | ร้อยละ/ค่าผลงานที่ไม่มี citation ในชุดข้อมูล SCE |
| `rev_percent` | `DECIMAL(5,2)` | - | ได้ | `NULL` | ร้อยละของ review documents |
| `created_at` | `DATETIME` | - | ได้ | `CURRENT_TIMESTAMP` | วันเวลาที่สร้างข้อมูล |
| `updated_at` | `DATETIME` | - | ได้ | `CURRENT_TIMESTAMP` | วันเวลาที่แก้ไขล่าสุด |
| `last_fetched_at` | `DATETIME` | - | ได้ | `NULL` | วันเวลาที่ดึง metrics จาก Scopus API ล่าสุด |

ข้อกำหนดสำคัญ: ชุด (`source_id`, `metric_year`, `doc_type`) ต้องไม่ซ้ำ

## 6. `scopus_config`

เก็บค่า configuration แบบ key-value สำหรับการเชื่อมต่อหรือการทำงานของโมดูล Scopus

| คอลัมน์ | ชนิดข้อมูล | Key | Null | ค่าเริ่มต้น | คำอธิบาย |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AI | ไม่ได้ | - | รหัสรายการ configuration |
| `key` | `VARCHAR(128)` | UK | ไม่ได้ | - | ชื่อ configuration เช่น `api_key` |
| `value` | `TEXT` | - | ไม่ได้ | - | ค่าของ configuration |
| `updated_at` | `DATETIME` | - | ไม่ได้ | `CURRENT_TIMESTAMP` | วันเวลาที่แก้ไขล่าสุด |

## 7. `scopus_api_import_jobs`

เก็บสถานะระดับงานของการนำเข้าข้อมูลจาก Scopus API

| คอลัมน์ | ชนิดข้อมูล | Key | Null | ค่าเริ่มต้น | คำอธิบาย |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AI | ไม่ได้ | - | รหัสงานนำเข้า |
| `service` | `VARCHAR(64)` | - | ไม่ได้ | `'scopus'` | ชื่อบริการต้นทาง |
| `job_type` | `VARCHAR(64)` | - | ไม่ได้ | `'author_documents'` | ประเภทงานนำเข้า |
| `scopus_author_id` | `VARCHAR(100)` | IDX | ได้ | `NULL` | Scopus Author ID ที่ใช้เป็นเงื่อนไข |
| `query_string` | `TEXT` | - | ไม่ได้ | - | คำค้นที่ส่งไปยัง Scopus API |
| `total_results` | `INT` | - | ได้ | `NULL` | จำนวนรายการทั้งหมดที่ API แจ้ง |
| `status` | `VARCHAR(32)` | IDX | ไม่ได้ | `'running'` | สถานะงาน เช่น running, success หรือ failed |
| `error_message` | `TEXT` | - | ได้ | `NULL` | รายละเอียดข้อผิดพลาด |
| `started_at` | `DATETIME` | IDX | ไม่ได้ | `CURRENT_TIMESTAMP` | วันเวลาเริ่มงาน |
| `finished_at` | `DATETIME` | - | ได้ | `NULL` | วันเวลาสิ้นสุดงาน |
| `created_at` | `DATETIME` | - | ไม่ได้ | `CURRENT_TIMESTAMP` | วันเวลาที่สร้างข้อมูล |
| `updated_at` | `DATETIME` | - | ไม่ได้ | `CURRENT_TIMESTAMP` | วันเวลาที่แก้ไขล่าสุด |

## 8. `scopus_api_requests`

เก็บรายละเอียดการเรียก Scopus API แต่ละครั้งภายใต้งานนำเข้า ใช้ตรวจสอบ pagination, response และประสิทธิภาพ

| คอลัมน์ | ชนิดข้อมูล | Key | Null | ค่าเริ่มต้น | คำอธิบาย |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AI | ไม่ได้ | - | รหัสคำขอ API |
| `job_id` | `BIGINT UNSIGNED` | FK, IDX | ไม่ได้ | - | อ้างถึง `scopus_api_import_jobs.id`; ลบตามงานแบบ CASCADE |
| `http_method` | `VARCHAR(8)` | - | ไม่ได้ | `'GET'` | HTTP method |
| `endpoint` | `TEXT` | - | ไม่ได้ | - | Endpoint ที่เรียก |
| `query_params` | `LONGTEXT` (JSON) | - | ได้ | `NULL` | Query parameters ในรูป JSON |
| `request_headers` | `LONGTEXT` (JSON) | - | ได้ | `NULL` | Request headers ในรูป JSON; ไม่ควรเก็บ secret แบบเปิดเผย |
| `response_status` | `INT` | IDX | ได้ | `NULL` | HTTP response status code |
| `response_time_ms` | `INT` | - | ได้ | `NULL` | ระยะเวลาตอบกลับ หน่วยมิลลิวินาที |
| `page_start` | `INT` | IDX | ได้ | `NULL` | ตำแหน่งเริ่มต้นของหน้าข้อมูล |
| `page_count` | `INT` | IDX | ได้ | `NULL` | จำนวนรายการที่ร้องขอต่อหน้า |
| `items_returned` | `INT` | - | ได้ | `NULL` | จำนวนรายการที่ API ส่งกลับจริง |
| `created_at` | `DATETIME` | - | ไม่ได้ | `CURRENT_TIMESTAMP` | วันเวลาที่เรียก API |

ข้อกำหนดสำคัญ: `query_params` และ `request_headers` ต้องผ่าน `JSON_VALID()`

## 9. `scopus_batch_import_runs`

เก็บผลรวมและสถิติของการนำเข้าข้อมูล Scopus สำหรับผู้ใช้หลายรายในหนึ่งรอบ

| คอลัมน์ | ชนิดข้อมูล | Key | Null | ค่าเริ่มต้น | คำอธิบาย |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AI | ไม่ได้ | - | รหัสรอบนำเข้า |
| `status` | `VARCHAR(32)` | IDX | ไม่ได้ | `'running'` | สถานะรอบนำเข้า |
| `error_message` | `TEXT` | - | ได้ | `NULL` | รายละเอียดข้อผิดพลาด |
| `requested_user_ids` | `TEXT` | - | ได้ | `NULL` | รายการรหัสผู้ใช้ที่ร้องขอให้นำเข้า |
| `limit` | `INT` | - | ได้ | `NULL` | จำนวนผู้ใช้สูงสุดที่กำหนดสำหรับรอบนี้ |
| `users_processed` | `INT` | - | ไม่ได้ | `0` | จำนวนผู้ใช้ที่ประมวลผลแล้ว |
| `users_with_errors` | `INT` | - | ไม่ได้ | `0` | จำนวนผู้ใช้ที่เกิดข้อผิดพลาด |
| `documents_fetched` | `INT` | - | ไม่ได้ | `0` | จำนวนผลงานที่ดึงได้ |
| `documents_created` | `INT` | - | ไม่ได้ | `0` | จำนวนผลงานที่สร้างใหม่ |
| `documents_updated` | `INT` | - | ไม่ได้ | `0` | จำนวนผลงานที่อัปเดต |
| `documents_failed` | `INT` | - | ไม่ได้ | `0` | จำนวนผลงานที่บันทึกไม่สำเร็จ |
| `authors_created` | `INT` | - | ไม่ได้ | `0` | จำนวนผู้แต่งที่สร้างใหม่ |
| `authors_updated` | `INT` | - | ไม่ได้ | `0` | จำนวนผู้แต่งที่อัปเดต |
| `affiliations_created` | `INT` | - | ไม่ได้ | `0` | จำนวนหน่วยงานที่สร้างใหม่ |
| `affiliations_updated` | `INT` | - | ไม่ได้ | `0` | จำนวนหน่วยงานที่อัปเดต |
| `links_inserted` | `INT` | - | ไม่ได้ | `0` | จำนวนความสัมพันธ์ผลงาน-ผู้แต่งที่สร้างใหม่ |
| `links_updated` | `INT` | - | ไม่ได้ | `0` | จำนวนความสัมพันธ์ผลงาน-ผู้แต่งที่อัปเดต |
| `started_at` | `DATETIME` | IDX | ไม่ได้ | `CURRENT_TIMESTAMP` | วันเวลาเริ่มรอบ |
| `finished_at` | `DATETIME` | - | ได้ | `NULL` | วันเวลาสิ้นสุดรอบ |
| `duration_seconds` | `DOUBLE` | - | ได้ | `NULL` | ระยะเวลาประมวลผล หน่วยวินาที |
| `created_at` | `DATETIME` | - | ไม่ได้ | `CURRENT_TIMESTAMP` | วันเวลาที่สร้างข้อมูล |
| `updated_at` | `DATETIME` | - | ไม่ได้ | `CURRENT_TIMESTAMP` | วันเวลาที่แก้ไขล่าสุด |

## 10. `scopus_conference_fetch_runs`

เก็บผลรวมของการดึงรายละเอียดงานประชุมจาก Scopus Abstract Retrieval API เพื่อเติมคอลัมน์ `conference_*` ใน `scopus_documents`

| คอลัมน์ | ชนิดข้อมูล | Key | Null | ค่าเริ่มต้น | คำอธิบาย |
|---|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AI | ไม่ได้ | - | รหัสรอบการดึงข้อมูล conference |
| `run_type` | `VARCHAR(32)` | - | ไม่ได้ | - | ประเภทรอบ เช่น `backfill` หรือ `refresh` |
| `status` | `VARCHAR(32)` | IDX | ไม่ได้ | `'running'` | สถานะ เช่น running, success หรือ failed |
| `error_message` | `TEXT` | - | ได้ | `NULL` | รายละเอียดข้อผิดพลาด |
| `documents_scanned` | `INT` | - | ไม่ได้ | `0` | จำนวนผลงานที่ตรวจสอบ |
| `documents_fetched` | `INT` | - | ไม่ได้ | `0` | จำนวนผลงานที่ดึงรายละเอียดสำเร็จ |
| `skipped_existing` | `INT` | - | ไม่ได้ | `0` | จำนวนผลงานที่ข้ามเพราะมีข้อมูลแล้ว |
| `documents_failed` | `INT` | - | ไม่ได้ | `0` | จำนวนผลงานที่ดึงข้อมูลไม่สำเร็จ |
| `started_at` | `DATETIME` | IDX | ได้ | `CURRENT_TIMESTAMP` | วันเวลาเริ่มรอบ |
| `finished_at` | `DATETIME` | - | ได้ | `NULL` | วันเวลาสิ้นสุดรอบ |
| `duration_seconds` | `DOUBLE` | - | ได้ | `NULL` | ระยะเวลาประมวลผล หน่วยวินาที |
| `created_at` | `DATETIME` | - | ได้ | `CURRENT_TIMESTAMP` | วันเวลาที่สร้างข้อมูล |
| `updated_at` | `DATETIME` | - | ได้ | `CURRENT_TIMESTAMP` | วันเวลาที่แก้ไขล่าสุด |

## ความสัมพันธ์ระหว่างตาราง

| ตารางต้นทาง | คอลัมน์ | ตารางปลายทาง | คอลัมน์ | เมื่อลบข้อมูลปลายทาง |
|---|---|---|---|---|
| `scopus_document_authors` | `document_id` | `scopus_documents` | `id` | `CASCADE` |
| `scopus_document_authors` | `author_id` | `scopus_authors` | `id` | `CASCADE` |
| `scopus_document_authors` | `affiliation_id` | `scopus_affiliations` | `id` | `SET NULL` |
| `scopus_api_requests` | `job_id` | `scopus_api_import_jobs` | `id` | `CASCADE` |

ความสัมพันธ์เชิงข้อมูลที่ไม่มี Foreign Key:

- `scopus_documents.source_id` เชื่อมกับ `scopus_source_metrics.source_id` ร่วมกับปีที่ต้องการใช้วัดผล
- `scopus_conference_fetch_runs` เป็นตารางสรุปรอบการทำงานและไม่มี FK ไปยังผลงานรายรายการ

## หมายเหตุด้านข้อมูล

- ฐานข้อมูลใช้ `InnoDB`, character set `utf8mb4` และ collation `utf8mb4_unicode_ci`
- ฟิลด์ที่มาจาก Scopus API ส่วนใหญ่ยอมให้เป็น `NULL` เพราะ API อาจไม่ส่งค่ามาในทุกประเภทผลงาน
- `scopus_config.value` อาจมีข้อมูลลับ เช่น API key จึงควรจำกัดสิทธิ์และไม่บันทึกค่าดังกล่าวใน log
- เอกสารนี้ไม่ครอบคลุมหน้าจอ, route หรือข้อมูลของฟีเจอร์ Scopus Benchmark
