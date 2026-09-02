# ภาคผนวก — ตารางแมปฟิลด์ → ช่องในแบบ ก.พ.ว.

> **เอกสารภายในทีมพัฒนา HRCP** — ไม่จำเป็นต้องส่งให้คณะ
> ใช้เป็นหลักฐานว่าทุกฟิลด์ที่ขอมีที่ใช้จริง และเป็นสเปกสำหรับเขียน importer ในภายหลัง

## ที่มาของข้อมูลในเอกสารนี้

ระบบ HRCP ยังไม่มี entity สำหรับผลงานวิจัย ข้อมูลผลงานทั้งหมดถูกเก็บเป็น `Map<String,String>`
ที่ serialize เป็น JSON ลงคอลัมน์ `position_document.json_data`
(`@Column(name = "json_data", columnDefinition = "TEXT")` ใน `PositionDocument.java`
— **ไม่ใส่ `@Lob` โดยเจตนา** เพราะบน PostgreSQL จะทำให้ Hibernate อ่านค่าเป็น LOB stream (OID)
ซึ่งต้องเปิด transaction ค้างไว้ตอนอ่าน และเกิดข้อผิดพลาด "Unable to access lob stream"
ดูคอมเมนต์กำกับไว้ที่ `PositionDocument.java:35-37`)
ดังนั้น **"schema" ที่แท้จริงคือชื่อ `name=` ของ input ในเทมเพลต Thymeleaf**

ไฟล์ต้นทาง: `HRCP-KKU-Academic/src/main/resources/templates/academic/position/applicant/doc_form_{1,4,6,7,9}.html`

## สัญลักษณ์ที่ใช้

| สัญลักษณ์ | ความหมาย |
|---|---|
| `{p}` | คำนำหน้าตามตำแหน่งที่ขอ — `asst` (ผศ.) / `assoc` (รศ.) / `prof` (ศ.) |
| `{n}`, `{idx}` | เลขลำดับแถว เริ่มที่ `1` เพิ่มขึ้นตามจำนวนรายการที่ผู้ใช้เพิ่ม |
| [NOTE] | ชื่อคีย์มีข้อผิดพลาดที่ต้องคงไว้ (ดู § ท้ายเอกสาร) |

---

## ๑. แบบ ก.พ.ว. มข. ๐๓ (ประวัติและผลงาน) — `doc_form_1.html`

### ๑.๑ รายชื่อผลงาน

| ฟิลด์ที่ขอ | คีย์ใน `json_data` | หมายเหตุ |
|---|---|---|
| `RESEARCH_OUTPUT.title_th` (ประเภทงานวิจัย) | `{p}_research_working_{n}` | กรองจาก `output_type_code` = งานวิจัย |
| ลำดับที่ (สร้างเอง) | `{p}_research_working_no_{n}` | ระบบใส่เลขลำดับให้อัตโนมัติ ไม่ต้องขอ |
| `RESEARCH_OUTPUT.title_th` (ตำรา/หนังสือ) | `{p}_book_working_{n}` | กรองจาก `BOOK_TEXTBOOK.book_kind` |
| ลำดับที่ | `{p}_book_working_no_{n}` | สร้างเอง |
| `RESEARCH_OUTPUT.title_th` (ผลงานลักษณะอื่น) | `{p}_other_working_{n}` | กลุ่มที่ ๒ ตาม `LK_OUTPUT_TYPE` |
| ลำดับที่ | `{p}_other_working_no_{n}` | สร้างเอง |

### ๑.๒ ประวัติการใช้ผลงานเสนอขอตำแหน่ง

| ฟิลด์ที่ขอ | คีย์ใน `json_data` | ค่าที่คาดหวัง |
|---|---|---|
| `POSITION_USAGE_HISTORY.was_used` | `{p}_used_research_{n}` · `{p}_used_book_{n}` · `{p}_used_other_{n}` | radio: `used` / `not_used` |
| `POSITION_USAGE_HISTORY.used_year_be` | `{p}_used_research_year_{n}` · `{p}_used_book_year_{n}` · `{p}_used_other_year_{n}` | ปี พ.ศ. |
| `POSITION_USAGE_HISTORY.quality_level_at_that_time` | `{p}_used_research_level_{n}` · `{p}_used_book_level_{n}` · `{p}_used_other_level_{n}` | ข้อความ |

> ค่า `used`/`not_used` ถูกแปลงเป็นเครื่องหมาย `☑`/`☐` ในไฟล์ DOCX โดยเมท็อด
> `mapUsedCheckboxes` ใน `DocumentGenerationService.java` ซึ่งจะสร้างคีย์
> `{p}_is_used_research_{n}` / `{p}_is_not_used_research_{n}` เพิ่มขึ้นมาเอง — **ไม่ต้องขอจากคณะ**

### ๑.๓ ตัวชี้วัดผลงาน (เฉพาะ รศ. และ ศ.)

| ฟิลด์ที่ขอ | คีย์ใน `json_data` |
|---|---|
| `BIBLIOMETRIC_SNAPSHOT.document_count` | `assoc_scopus_stories_count` · `prof_scopus_stories_count` |
| `BIBLIOMETRIC_SNAPSHOT.citation_count` | `assoc_scopus_citation_count` · `prof_scopus_citation_count` |
| `BIBLIOMETRIC_SNAPSHOT.h_index` | `assoc_h_index` · `prof_h_index` |

### ๑.๔ ผลงานตามวิธีที่ ๓ (ควอไทล์และสถานะผู้ประพันธ์) — เฉพาะ รศ. และ ศ.

| ฟิลด์ที่ขอ | คีย์ใน `json_data` | ค่าที่คาดหวัง |
|---|---|---|
| `RESEARCH_OUTPUT.title_th` | `{assoc\|prof}_method3_research_{n}` | ข้อความ |
| ลำดับที่ | `{assoc\|prof}_method3_research_no_{n}` | สร้างเอง |
| `PUBLICATION_VENUE.quartile` = `Q1` | `{assoc\|prof}_m3_q1_{n}` | checkbox `☑` |
| `PUBLICATION_VENUE.quartile` = `Q2` | `{assoc\|prof}_m3_q2_{n}` | checkbox `☑` |
| `OUTPUT_AUTHOR.is_first_author` | `{assoc\|prof}_m3_first_{n}` | checkbox `☑` |
| `OUTPUT_AUTHOR.is_corresponding_author` | `{assoc\|prof}_m3_corresp_{n}` | checkbox `☑` |

> **ช่องว่างที่ทราบ:** แบบฟอร์มรองรับเฉพาะ Q1/Q2 ไม่มีช่อง Q3/Q4
> จึงขอ `quartile` เป็นค่า `Q1`–`Q4` เต็มช่วง เพื่อรองรับการแก้แบบฟอร์มในอนาคต
> ปัจจุบัน importer จะติ๊กเฉพาะเมื่อค่าเป็น `Q1` หรือ `Q2`

### ๑.๕ โครงการวิจัยที่เป็นหัวหน้าโครงการ — เฉพาะ รศ. และ ศ.

| ฟิลด์ที่ขอ | คีย์ใน `json_data` | หมายเหตุ |
|---|---|---|
| `GRANT_PROJECT.title_th` | `{assoc\|prof}_pi_project_{n}` | กรอง `role_in_project` = หัวหน้าโครงการ เท่านั้น |
| ลำดับที่ | `{assoc\|prof}_pi_project_no_{n}` | สร้างเอง |
| `GRANT_PROJECT.funder_name` | `{assoc\|prof}_pi_source_{n}` | ช่อง "แหล่งทุน" |

### ๑.๖ การเป็นวิทยากรในการประชุมวิชาการนานาชาติ (๕ ปีย้อนหลัง)

| ฟิลด์ที่ขอ | คีย์ใน `json_data` |
|---|---|
| `ACADEMIC_ENGAGEMENT.event_name` + `event_date` + `country` | `international_speaker_last_5_years_{n}` |
| ลำดับที่ | `international_speaker_last_5_years_no_{n}` |

> เป็นช่องข้อความบรรทัดเดียว — importer ต้องประกอบข้อความจากหลายฟิลด์
> โดยกรอง `is_international = Y` และ `event_date` ย้อนหลังไม่เกิน ๕ ปี

### ๑.๗ ข้อมูลประวัติส่วนบุคคลและหน่วยงาน

| ฟิลด์ที่ขอ | คีย์ใน `json_data` |
|---|---|
| `RESEARCHER.title_th` | `title` |
| `RESEARCHER.first_name_th` + `last_name_th` | `applicant_name` |
| `RESEARCHER.birth_date` | `birth_date`, `age` (คำนวณ) |
| `RESEARCHER.employment_start_date` | `years`, `months` (คำนวณอายุงาน) |
| `RESEARCHER.academic_title_th` | `current_position` |
| `ORG_UNIT.org_name_th` (คณะ) | `faculty` |
| `ORG_UNIT.org_name_th` (ภาควิชา) | `department` |
| — (ค่าคงที่ "มหาวิทยาลัยขอนแก่น") | `university` |
| `RESEARCHER.major_code` | `major_code`, `major` |
| `RESEARCHER.sub_major_code` | `sub_major_code`, `sub_major` |
| `RESEARCHER.lecturer_appointed_date` | `lecturer_appointment_date` |
| `RESEARCHER.asst_prof_appointed_date` | `assistant_appointment_date` |
| `RESEARCHER.assoc_prof_appointed_date` | `associate_appointment_date` |

---

## ๔. บันทึกรับรองผลงานทางวิชาการ (วิทยานิพนธ์) — `doc_form_4.html`

| ฟิลด์ที่ขอ | คีย์ใน `json_data` | ค่าที่คาดหวัง |
|---|---|---|
| `THESIS_LINK.is_part_of_thesis` (บทความ) | `academic_paper_status` | radio: `is_part` / `not_part` |
| — (สร้างจาก radio) | `academic_paper_is_part_edu`, `academic_paper_not_part_edu` | hidden `✔` |
| จำนวนบทความ (นับจากข้อมูล) | `academic_paper_count` | จำนวนเต็ม |
| `RESEARCH_OUTPUT.title_th` (บทความ) | `paper_title_{n}` | ข้อความ |
| `THESIS_LINK.remark` | `academic_paper_additional_detail` | ข้อความ |
| `THESIS_LINK.is_part_of_thesis` (งานวิจัย) | `research_status` | radio: `is_part` / `not_part` |
| — (สร้างจาก radio) | `research_is_part_edu`, `research_not_part_edu` | hidden `✔` |
| จำนวนงานวิจัย | `research_count` | จำนวนเต็ม |
| `RESEARCH_OUTPUT.title_th` (งานวิจัย) | `research_title_{n}` | ข้อความ |
| `THESIS_LINK.remark` | `research_additional_detail` | ข้อความ |
| `THESIS_LINK.degree_level` = ปริญญาโท | `master_thesis` | checkbox |
| `THESIS_LINK.thesis_title` (ป.โท) | `master_thesis_title` | ข้อความ |
| `THESIS_LINK.degree_level` = ปริญญาเอก | `doctoral_thesis` | checkbox |
| `THESIS_LINK.thesis_title` (ป.เอก) | `doctoral_thesis_title` | ข้อความ |
| `RESEARCHER.academic_title_th` | `current_position` |
| `ORG_UNIT.org_name_th` | `affiliation` |

> **ข้อควรระวังฝั่ง DOCX:** เมท็อด `preprocessDoc4Placeholders` ใน `DocumentGenerationService.java`
> ยุบ `paper_title_{n}` ทั้งชุดเป็นคีย์เดียวชื่อ `research_title1`
> และยุบ `research_title_{n}` เป็น `research_title` (ข้อความก้อนเดียว)
> importer ต้องเขียนคีย์รายแถวลง `json_data` ตามปกติ แล้วปล่อยให้ชั้น DOCX ยุบเอง

---

## ๖. บันทึกข้อความจริยธรรมการวิจัย (Exemption) — `doc_form_6.html`

หนึ่งแถวต่อหนึ่งผลงาน (`{idx}` เริ่มที่ ๑)

| ฟิลด์ที่ขอ | คีย์ใน `json_data` | ค่าที่คาดหวัง |
|---|---|---|
| จำนวนผลงาน | `research_count` | จำนวนเต็ม |
| `RESEARCH_OUTPUT.title_th` | `des_research{idx}` | ข้อความ |
| `OUTPUT_AUTHOR.is_first_author` | `chk_firstauthor{idx}` | checkbox `☑` |
| `OUTPUT_AUTHOR.is_corresponding_author` | `chk_Corres{idx}` | checkbox `☑` |
| `OUTPUT_AUTHOR.is_essential_contributor` | `essen{idx}` | checkbox `☑` |
| `PUBLICATION_VENUE.impact_factor` | [NOTE] `impactfacttor{idx}` | ตัวเลข |
| `PUBLICATION_VENUE.indexed_database` | `data{idx}` | ข้อความ เช่น `Scopus` |
| `RESEARCHER.title_th` | `applicant_title` | ข้อความ |
| `RESEARCHER.academic_title_th` | `current_position` | ข้อความ |
| `ETHICS_APPROVAL.*` | — | ใช้เป็นเงื่อนไขว่าผลงานใดเข้าข่ายยกเว้น จึงจะแสดงในแบบฟอร์มนี้ |

> `expandDynamicRows` ใน `DocumentGenerationService.java` โคลนแถวจาก `des_research5`
> ไปเป็น `des_research6` ขึ้นไปพร้อมคีย์ `chk_firstauthor{n}` / `chk_Corres{n}` / `essen{n}`
> ดังนั้น importer ต้องเขียนคีย์ให้ต่อเนื่องไม่ข้ามเลข

---

## ๗. แบบฟอร์มตรวจสอบคุณสมบัติ (Checklist) — `doc_form_7.html`

ฟอร์มนี้มีคีย์ทั้งหมด ๒๖๐ คีย์ ส่วนใหญ่เป็น checkbox ยืนยันว่ามีหลักฐานประกอบครบหรือไม่
importer จะเติมเฉพาะ **ชื่อผลงาน · ประเภท · สถานะการเผยแพร่ · สถานะจริยธรรม**
ส่วนช่องยืนยันหลักฐาน (`*_letter_*`, `*_cert_*`, `*_evidence_*`) ยังต้องให้ผู้ยื่นติ๊กเอง
เพราะเป็นการยืนยันว่า *ได้แนบเอกสารจริง* ไม่ใช่ข้อเท็จจริงที่ดึงจากฐานข้อมูลได้

### ๗.๑ ชื่อผลงานตามประเภท

| ฟิลด์ที่ขอ | คีย์ใน `json_data` |
|---|---|
| `RESEARCH_OUTPUT.title_th` (งานวิจัย) | `research_working_title_{n}` |
| `RESEARCH_OUTPUT.title_th` (บทความ) | `article_title_{n}` |
| `RESEARCH_OUTPUT.title_th` (ตำรา/หนังสือ) | `book_working_title_{n}` |
| `RESEARCH_OUTPUT.title_th` (ลักษณะอื่น) | `other_working_title_{n}` |
| `RESEARCH_OUTPUT.title_th` (รับใช้สังคม) | `social_working_title_{n}` |
| จำนวนผลงานรวม | `total_stories` |

### ๗.๒ ประเภทผลงาน (จาก `output_type_code` → checkbox)

`is_published_{n}` · `is_textbook_{n}` · `is_book_{n}` · `is_industry_{n}` · `is_case_study_{n}` ·
`is_learning_dev_{n}` · `is_translation_{n}` · `is_public_policy_{n}` · `is_patent_{n}` ·
`is_creative_sci_{n}` · `is_software_{n}` · `is_dictionary_{n}` · `is_creative_art_{n}`

→ แมปหนึ่งต่อหนึ่งกับรายการใน `LK_OUTPUT_TYPE` (ดูเอกสารแนบ ๑ § D1)

### ๗.๓ สถานะการเผยแพร่และช่องทาง

| ฟิลด์ที่ขอ | คีย์ใน `json_data` |
|---|---|
| `RESEARCH_OUTPUT.publication_status` = เผยแพร่แล้ว | `article_is_published_{n}` · `book_is_published_{n}` |
| `RESEARCH_OUTPUT.publication_status` = รอพิจารณา | `article_is_pending_{n}` · `book_is_pending_{n}` |
| `PUBLICATION_VENUE.venue_type` = วารสาร | `article_pub_journal_{n}` · `diss_journal_{n}` |
| `PUBLICATION_VENUE.venue_type` = หนังสือรวมบทความ | `article_pub_book_{n}` · `diss_book_{n}` |
| `PUBLICATION_VENUE.venue_type` = proceedings | `diss_proceeding_{n}` |
| `PUBLICATION_VENUE.venue_type` = monograph | `diss_monograph_{n}` |
| `PUBLICATION_VENUE.venue_type` = รายงานวิจัยฉบับสมบูรณ์ | `diss_report_{n}` |
| `PUBLICATION_VENUE.scope_level` = ระดับชาติ | `learn_journal_kpo_{n}` |
| `PUBLICATION_VENUE.scope_level` = ระดับนานาชาติ | `learn_journal_inter_{n}` |
| `BOOK_TEXTBOOK.media_type` = สิ่งพิมพ์ | `book_pub_print_{n}` · `trans_print_{n}` · `dict_print_{n}` |
| `BOOK_TEXTBOOK.media_type` = อิเล็กทรอนิกส์ | `book_pub_electronic_{n}` · `trans_elec_{n}` · `dict_elec_{n}` |
| `BOOK_TEXTBOOK.media_type` = e-book | `book_pub_ebook_{n}` |
| `BOOK_TEXTBOOK.publish_date` | `book_publish_date_{n}` |
| `BOOK_TEXTBOOK.isbn` / เลขมาตรฐาน | `book_publish_index_{n}` |
| `BOOK_TEXTBOOK.distribution_scope` | `book_wide_dist_letter_{n}` · `learn_wide_dist_letter_{n}` · `report_wide_dist_letter_{n}` |
| `RESEARCH_OUTPUT.accept_letter_date` | `article_accept_letter_{n}` · `book_accept_letter_{n}` |

### ๗.๔ จริยธรรมการวิจัย

| ฟิลด์ที่ขอ | คีย์ใน `json_data` |
|---|---|
| `ETHICS_APPROVAL.status` | `ethics_status_{n}` |
| `ETHICS_APPROVAL.exemption_reason` | `reason_e_{n}` |

### ๗.๕ สิทธิบัตร ซอฟต์แวร์ และการรับใช้สังคม

| ฟิลด์ที่ขอ | คีย์ใน `json_data` |
|---|---|
| `PATENT_IP.registration_no` / `application_no` | `patent_evidence_{n}` |
| `RESEARCH_OUTPUT.full_text_url` (ซอฟต์แวร์) | `software_evidence_{n}` |
| `TECH_TRANSFER.usage_description` | `social_desc_doc_{n}` · `soc_visit_record_{n}` |
| `TECH_TRANSFER.evidence_url` | `social_other_evidence_{n}` · `soc_evid_cert_{n}` |

### ๗.๖ วิทยานิพนธ์

| ฟิลด์ที่ขอ | คีย์ใน `json_data` | หมายเหตุ |
|---|---|---|
| `THESIS_LINK.thesis_title` (ป.โท) | `master_thesis_titles` | ช่องเดียวรวมทุกเรื่อง (ชื่อคีย์เป็นพหูพจน์ ต่างจากฉบับที่ ๔) |
| `THESIS_LINK.thesis_title` (ป.เอก) | `doctoral_thesis_titles` | ช่องเดียวรวมทุกเรื่อง |

### ๗.๗ ข้อมูลผู้ยื่นและหน่วยงาน

`applicant_firstname` · `applicant_lastname` · `email` · `faculty` · `department` ·
`major` · `major_code` — จากตาราง `RESEARCHER` และ `ORG_UNIT`

---

## ๙. ลักษณะการมีส่วนร่วมในผลงาน — `doc_form_9.html`

### ๙.๑ ประเภทผลงาน (checkbox ตามกลุ่ม)

| ฟิลด์ที่ขอ | คีย์ใน `json_data` |
|---|---|
| `output_type_code` = งานวิจัย | `group1_research` |
| `output_type_code` = กลุ่มที่ ๒ รายการที่ ๑–๑๒ | `chkgroup2_1` … `chkgroup2_12` |
| `output_type_code` = ตำรา | [NOTE] `chkgroup3_ treatise` |
| `output_type_code` = หนังสือ | `chkgroup3_book` |
| `output_type_code` = บทความทางวิชาการ | `chkgroup3_Academicarticles` |

ลำดับของ `chkgroup2_1..12` ตรงกับลำดับในเอกสารแนบ ๑ § D1 รายการที่ ๒–๑๓

### ๙.๒ บทบาทการมีส่วนร่วม ๗ ด้าน

| ฟิลด์ที่ขอ | คีย์ใน `json_data` |
|---|---|
| `OUTPUT_AUTHOR.role_idea` | `role_des1` |
| `OUTPUT_AUTHOR.role_design` | `role_des2` |
| `OUTPUT_AUTHOR.role_data_analysis` | `role_des3` |
| `OUTPUT_AUTHOR.role_discussion` | `role_des4` |
| `OUTPUT_AUTHOR.role_manuscript` | `role_des5` |
| `OUTPUT_AUTHOR.role_support` | `role_des6` |
| `OUTPUT_AUTHOR.role_other` | `role_des7` |

### ๙.๓ การเผยแพร่และถ่ายทอดเทคโนโลยี (ช่องข้อความรวม — importer ต้องประกอบข้อความ)

| คีย์ใน `json_data` | ฟิลด์ที่ขอมาประกอบเป็นข้อความ |
|---|---|
| `des_journal` | `PUBLICATION_VENUE`: `journal_name` + `impact_factor` + `citation_count` + `indexed_database` |
| [NOTE] `des_ patent` | `PATENT_IP`: `ip_type` + `grant_date` + `registration_no` + `countries_covered` |
| `des_techreport` | `TECH_TRANSFER`: `user_organization` + `usage_description` |
| `des_poster` | `PUBLICATION_VENUE`: `presentation_type` + `session_name` + `conference_name` + `conference_country` |
| `des_scholarship` | `GRANT_PROJECT`: `title_th` + `funder_name` + `fiscal_year_be` + `budget_amount` |
| `des_researchdd` | `PATENT_IP`: `licensing_fee_total` + `licensee_name` |

### ๙.๔ สถานะผู้ประพันธ์และผู้ร่วมงาน

| ฟิลด์ที่ขอ | คีย์ใน `json_data` |
|---|---|
| `OUTPUT_AUTHOR.is_first_author` (ของผู้ยื่น) | [NOTE] `chk_ firstauthor` |
| `OUTPUT_AUTHOR.is_corresponding_author` (ของผู้ยื่น) | `chk_corresp` |
| `OUTPUT_AUTHOR.is_essential_contributor` (ของผู้ยื่น) | `chk_essen` |
| `OUTPUT_AUTHOR.author_name_full` (ผู้ประพันธ์อันดับแรก) | `firstauthor_name` |
| `OUTPUT_AUTHOR.author_name_full` (ผู้ประพันธ์บรรณกิจ) | `corres_name` |
| จำนวนผู้ร่วมงาน (นับจากข้อมูล) | `coauthor_count` |
| `OUTPUT_AUTHOR.author_name_full` (ผู้ร่วมงานรายอื่น) | `coauthor_name_{n}` |
| `RESEARCHER.title_th` + `academic_title_th` | `title_name` |
| `RESEARCHER.first_name_th` + `last_name_th` | `applicant_name` |

---

## ข้อบกพร่องที่ทราบในโค้ดปัจจุบัน (ต้องรับมือใน importer)

| ประเด็น | ที่มา | วิธีรับมือ |
|---|---|---|
| `reseach` (ตกตัว `r`) | มี input ชื่อนี้ใน `doc_form_1.html` และเทมเพลต DOCX ส่วน ผศ. ใช้ชื่อผิดนี้ — `mapUsedCheckboxes` จึงเขียนคีย์ซ้ำทั้งสองสะกด | คงชื่อผิดไว้ ห้ามแก้ ไม่งั้น DOCX ไม่แทนค่า |
| `impactfacttor{idx}` (ตัว `t` เกิน) | `doc_form_6.html` บรรทัด ๑๔๒ | คงไว้ |
| `des_ patent` มีช่องว่างในชื่อคีย์ | `doc_form_9.html` บรรทัด ๑๓๒ | เขียนคีย์ให้มีช่องว่างตรงตามนี้ |
| `chkgroup3_ treatise` มีช่องว่าง | `doc_form_9.html` | คงไว้ |
| `chk_ firstauthor` มีช่องว่าง | `doc_form_9.html` | คงไว้ (ต่างจาก `chk_firstauthor{idx}` ในฉบับ ๖ ที่ไม่มีช่องว่าง) |
| `coauthor_role_{n}` เป็นคีย์ที่อ่านแต่ไม่มีที่เขียน | `doc_form_9.html` บรรทัด ๒๕๖ เรียก `addCoauthorRow(name, role)` แต่ฟังก์ชันบรรทัด ๑๙๙–๒๑๓ ไม่ได้สร้าง input สำหรับ role | ฟอร์มเจตนาจะเก็บบทบาทผู้ร่วมงานแต่ยังไม่ได้ทำ — จึงขอ `OUTPUT_AUTHOR.contribution_percent` และ `role_*` ไว้ล่วงหน้า เมื่อเพิ่ม input ในฟอร์มแล้วจะใช้ได้ทันที |
| ไม่มีช่อง Q3/Q4 | `doc_form_1.html` มีเพียง `*_m3_q1_N`, `*_m3_q2_N` | ขอ `quartile` เต็มช่วง `Q1`–`Q4` เก็บไว้ในฐานข้อมูล ติ๊กเฉพาะ Q1/Q2 |
| ไม่มีฟิลด์ DOI/ISSN/ISBN ที่ใดในระบบ | ตรวจแล้วทั้งโค้ดเบส | ขอมาเก็บไว้เพื่อใช้ตรวจผลงานซ้ำ (deduplication) ไม่ได้ใช้เติมฟอร์มโดยตรง |
| ไม่มีตาราง lookup ในระบบ | มีเพียง ๕ enum ในโค้ด ทั้งหมดเป็นสถานะคำขอ ไม่ใช่ประเภทผลงาน | ต้องสร้างตาราง lookup ใหม่ตาม § D ของเอกสารแนบ ๑ |

---

## ช่องที่ **ไม่** ขอจากคณะ (ยืนยันขอบเขต)

คีย์ต่อไปนี้อยู่ในฟอร์มฉบับที่ ๑/๔/๖/๗/๙ แต่ไม่ต้องขอข้อมูลจากคณะ

**ธุรการเอกสาร** — `memo_no`, `date`, `sign_date`, `dean_name`, `dean_position`,
`hr_officer_name`, `hr_officer_position`, `status`, `action`, `m_days`/`m_months`/`m_years`,
`d_days`/`d_months`/`d_years`

**จำนวนชุดเอกสารที่แนบ** — `boss_eval_10_sets`, `ethics_report_10_sets`,
`expert_list_1_set`, `teaching_eval_1_set`

**ผู้ยื่นต้องยืนยันเอง** — `target_position`, `request_position`, `method`,
`eval_expert`/`eval_highly_skilled`/`eval_skilled`,
`is_req_asst`/`is_req_assoc`/`is_req_prof`/`is_req_prof_2`/`is_req_03`/`is_req_04`/`is_req_05`,
`is_teach_asst`/`is_teach_assoc`/`is_teach_prof`,
`research_and_articles(_chk)`, `textbooks_and_books(_chk)`, `academic_articles_social_only(_chk)`,
`social_service_works(_chk)`, `other_academic_works(_chk)`, `other_academic_works_type`

**ยืนยันว่าได้แนบหลักฐาน (ไม่ใช่ข้อเท็จจริงที่ดึงได้)** — ทุกคีย์ที่ลงท้ายด้วย
`_eval_letter_{n}`, `_cert_content_{n}`, `_accept_letter_{n}` (ส่วนที่เป็นการติ๊กยืนยัน),
`pending_letter_{n}`, `content_certified_{n}`, `ind_pub_*`, `pub_*`, `learn_*`, `case_*`,
`trans_*`, `dict_*`, `sci_*`, `art_*`, `policy_*`, `soc_evid_*`, `elect_descr_{n}`

**ข้อมูลจากระบบอื่น** — `teaching_subject_{n}`, `teaching_level_{n}`,
`teaching_semester_{n}`, `teaching_hours_per_week_{n}` (ฐานข้อมูลทะเบียนเรียน),
`education_degree_{n}`/`education_major_{n}`/`education_institution_{n}`/
`education_country_{n}`/`education_year_{n}` (ฐานข้อมูลบุคลากร),
`current_salary`, `other_position_{n}`, `academic_service`, `administration`, `other`

**รายละเอียดการแต่งตั้งครั้งก่อน** — `assistant_department`, `assistant_method`,
`associate_department`, `associate_method` (หน่วยงานและวิธีการพิจารณาที่ใช้ตอนได้รับแต่งตั้ง
ผศ./รศ.) เป็นข้อมูลกองทรัพยากรบุคคล และผู้ยื่นทราบข้อมูลของตนเองอยู่แล้ว
จึงให้กรอกเอง — ขอจากคณะเฉพาะ *วันที่* แต่งตั้ง (`RESEARCHER.asst_prof_appointed_date`,
`assoc_prof_appointed_date`) ซึ่งผู้ยื่นมักจำไม่ได้แน่นอน

**ข้อมูลติดต่อส่วนบุคคล** — `phone_home`, `phone_mobile` ไม่ขอจากคณะเพื่อลดขอบเขต
ข้อมูลส่วนบุคคลที่ต้องรับผิดชอบ ระบบมีเบอร์โทรของผู้ใช้อยู่แล้วจากการลงทะเบียน
