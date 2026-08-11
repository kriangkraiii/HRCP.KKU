# ชุดเอกสารขอข้อมูลงานวิจัยจากคณะ

เอกสารชุดนี้ใช้สำหรับ **ขอข้อมูลผลงานวิจัย/ผลงานวิชาการจากคณะ** เพื่อนำเข้าระบบ HRCP-KKU-Academic
และเติมแบบ ก.พ.ว. มข. ให้ผู้ยื่นขอตำแหน่งทางวิชาการโดยอัตโนมัติ (auto-fill)

## สารบัญ

| ไฟล์ | สำหรับใคร | เนื้อหา |
|---|---|---|
| [01-request-letter.md](01-request-letter.md) | ผู้บริหาร / คณบดี | บันทึกข้อความขอข้อมูล — วัตถุประสงค์ ขอบเขต สิ่งที่ขอ กำหนดเวลา |
| [02-data-dictionary.md](02-data-dictionary.md) | เจ้าหน้าที่ IT / ผู้ดูแลระบบต้นทาง | **พจนานุกรมข้อมูล 14 ตาราง** — รายการฟิลด์ที่ขอทั้งหมด |
| [03-delivery-requirements.md](03-delivery-requirements.md) | เจ้าหน้าที่ IT / ผู้ดูแลข้อมูล | ช่องทางส่งมอบ รูปแบบไฟล์ ความปลอดภัย PDPA |
| [04-appendix-field-mapping.md](04-appendix-field-mapping.md) | ทีมพัฒนา HRCP (ภาคผนวก) | แมปฟิลด์ที่ขอ → ช่องจริงในแบบ ก.พ.ว. แต่ละฉบับ |
| [templates/รายการข้อมูลที่ขอ-รวมทุกตาราง.csv](templates/) | คณะ (ตอบกลับ) | **CSV แผ่นเดียว 184 แถว** รวม A1–C7 มีคอลัมน์ว่างให้คณะกรอกว่ามีข้อมูลนั้นหรือไม่ |
| [templates/แบบฟอร์มกรอกข้อมูลงานวิจัย.xlsx](templates/) | คณะ (กรอกข้อมูลจริง) | Excel 15 แผ่น (คำแนะนำ + 14 ตารางแยกแผ่น) |
| [templates/*.csv](templates/) (14 ไฟล์) | สคริปต์/งานอัตโนมัติ | CSV แยกตาราง เนื้อหาเดียวกับ xlsx (สำหรับ SFTP / importer) |

**ลำดับการอ่านที่แนะนำสำหรับคณะ:** `01` → `03` → `02` (ภาคผนวก `04` เป็นเอกสารภายในของทีมพัฒนา ไม่จำเป็นต้องส่งให้คณะ)

## การสร้างไฟล์ที่ส่งออก

ทั้งสองไฟล์ที่ส่งคณะถูก generate จากไฟล์ Markdown และ `templates/table_defs.py`
ดังนั้นเมื่อแก้ต้นฉบับแล้วต้อง generate ใหม่ทุกครั้ง

```bash
pip install python-docx openpyxl

# หนังสือขอข้อมูลงานวิจัย.docx — รวม 01-03, TH Sarabun New 16pt, A4
python docs/data-request-research/build_docx.py

# templates/แบบฟอร์มกรอกข้อมูลงานวิจัย.xlsx — 15 แผ่น
python docs/data-request-research/templates/make_workbook.py

# templates/รายการข้อมูลที่ขอ-รวมทุกตาราง.csv — แผ่นเดียว 184 แถว
python docs/data-request-research/templates/make_combined_csv.py

# templates/*.csv — 14 ไฟล์แยกตาราง (ไม่จำเป็นถ้าคณะเลือกส่งเป็น Excel)
python docs/data-request-research/templates/make_templates.py
```

`table_defs.py` เป็นนิยามกลางของ 14 ตาราง — ทั้ง `make_workbook.py` และ
`make_templates.py` อ่านจากไฟล์นี้ แก้ที่เดียวแล้ว generate ใหม่ ผลลัพธ์จะตรงกันเสมอ

## หมายเหตุสำหรับทีมพัฒนา

- ชื่อไฟล์ใช้ ASCII เพื่อให้ git / สคริปต์ / CI บน Windows ทำงานได้ไม่มีปัญหา encoding
  ส่วนชื่อเรื่องภาษาไทยอยู่ในหัวเอกสารและในไฟล์ DOCX ที่ส่งออก
- เอกสารชุดนี้ **ไม่มีการแก้โค้ด** — เป็นเอกสารขอข้อมูลอย่างเดียว
  ระบบจะออกแบบ entity / importer หลังจากคณะยืนยันว่าให้ข้อมูลอะไรได้จริง
- ที่มาของรายการฟิลด์: derive จากชื่อ `name=` ของ input ในไฟล์
  `HRCP-KKU-Academic/src/main/resources/templates/academic/position/applicant/doc_form_{1,4,6,7,9}.html`
  เพราะปัจจุบันระบบเก็บข้อมูลผลงานเป็น key/value ในคอลัมน์ `position_document.json_data` (TEXT)
  จึงยังไม่มี schema ฝั่งฐานข้อมูลให้อ้างอิง
