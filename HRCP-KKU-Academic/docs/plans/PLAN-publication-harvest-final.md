# PLAN: การเปิดใช้งานระบบดึงผลงานวิจัยแบบครอบคลุมรอบด้าน (Full Multi-Source Harvest)

##  เป้าหมาย (Goal)
ดึงผลงานวิจัยของอาจารย์วิทยาลัยการคอมพิวเตอร์ครบทั้ง 41 ท่านจากทุกฐานข้อมูลสากล (IEEE, ACM, Scopus, Crossref, DBLP, OpenAlex, ThaiJO, KKU IR) อย่างครบถ้วน แม่นยำ และไม่มีข้อมูลซ้ำซ้อน

---

## ️ รายละเอียดขั้นตอนดำเนินงาน (Task Breakdown)
1. **ตรวจสอบความพร้อมของ Adapters ทั้ง 5+1 แหล่ง:**
   - Scopus API
   - OpenAlex (Author Search)
   - Crossref (Author Query for IEEE / DOI papers)
   - DBLP (Computer Science Conference & Proceedings)
   - ThaiJO (TCI National Journals)
   - KKU IR (College of Computing Collections)
2. **ยืนยันระบบตัดข้อมูลซ้ำซ้อน 4 ชั้น (Deduplication Pipeline):**
   - DOI matching
   - SHA-256 Hash matching
   - PostgreSQL Trigram Fuzzy matching
   - In-batch staged cache
3. **การทดสอบความถูกต้อง (Verification):**
   - รัน Test Suite ทั้งหมด
   - ทดสอบ Trigger บนหน้าเว็บจริง

---

##  ไฟล์แผนงาน:
- `docs/PLAN-publication-harvest-final.md`
