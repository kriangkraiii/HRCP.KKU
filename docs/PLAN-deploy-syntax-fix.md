# แผนงาน: แก้ไขปัญหา PowerShell Syntax & Character Encoding ใน CI/CD Deployment Workflow

**รหัสเอกสารแผนงาน:** `docs/PLAN-deploy-syntax-fix.md`  
**สถานะ:** รอผู้ใช้ตรวจสอบและอนุมัติ (Awaiting User Approval)  
**Agent:** `project-planner`  
**ผู้เชี่ยวชาญร่วม:** `devops-engineer`, `backend-specialist`

---

## 1. บริบทและปัญหาที่พบ (Problem Statement & Deep Discovery)

ระหว่างการรัน GitHub Actions Workflow ด้วย Self-Hosted Runner บนระบบปฏิบัติการ Windows Server ขั้นตอน `Back up uploads and current jar` ล้มเหลวด้วยข้อผิดพลาด:

```text
Run New-Item -ItemType Directory -Force -Path $env:BACKUP_ROOT | Out-Null
At C:\Users\Administrator\actions-runner\_work\_temp\264fc5a5-11d0-43a4-93c9-6939036a84b5.ps1:14 char:119
+ ... OT backed up. Set UPLOADS_PATH to the service's real uploads folder."
+                                                  ~~~~~~~~~~~~~~~~~~~~~~~~
The string is missing the terminator: '.
At C:\Users\Administrator\actions-runner\_work\_temp\264fc5a5-11d0-43a4-93c9-6939036a84b5.ps1:13 char:8
+ } else {
+        ~
Missing closing '}' in statement block or type definition.
    + CategoryInfo          : ParserError: (:) [], ParseException
    + FullyQualifiedErrorId : TerminatorExpectedAtEndOfString

Error: Process completed with exit code 1.
```

### การวิเคราะห์เชิงลึก (Root Cause Analysis)

1. **การบันทึกไฟล์สคริปต์ของ GitHub Actions Runner**:
   - เมื่อกำหนด `shell: powershell` ตัว Actions Runner บน Windows จะดึงบล็อก `run: |` ไปเขียนลงไฟล์ชั่วคราว `.ps1` ด้วยการเข้ารหัส **UTF-8 (แบบไม่มี BOM)**
2. **กลไกการอ่านไฟล์ของ Windows PowerShell 5.1**:
   - บน Windows Server ตัว `powershell.exe` (Windows PowerShell 5.1) เมื่ออ่านไฟล์ `.ps1` ที่ไม่มี UTF-8 BOM จะตีความข้อมูลไบต์ด้วย **ANSI Code Page ของเครื่อง** (โดยทั่วไปคือ Windows-1252 หรือ CP874)
3. **ปัญหาอักขระ Em-dash (`—`)**:
   - ในบรรทัดที่ 58 ของ [.github/workflows/deploy.yml](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/.github/workflows/deploy.yml#L58):
     ```powershell
     Write-Host "::warning::$env:UPLOADS_PATH not found — uploads were NOT backed up. Set UPLOADS_PATH to the service's real uploads folder."
     ```
   - อักขระ `—` (U+2014) มีการเข้ารหัส UTF-8 คือ `0xE2 0x80 0x94`
   - ใน Windows-1252 ไบต์ `0x94` คืออักขระ **`”` (U+201D Right Double Quotation Mark)**
   - PowerShell รองรับ Typographic Quotes เป็นตัวเปิด/ปิด String จึงทำให้ String ถูกสั่งปิดเร็วกว่ากำหนดที่ตำแหน่ง `—`
4. **ผลกระทบต่อเนื่องที่ `service's`**:
   - ส่วนที่เหลือของบรรทัดจึงหลุดออกมาเป็นคำสั่งนอก String
   - เครื่องหมาย `'` ใน `service's` กลายเป็นตัวเปิด Single-Quoted String ใหม่ที่ไม่มีตัวปิด (`'`) ไปจนจบไฟล์
   - บล็อกปีกกาปิด `}` ของคำสั่ง `else` ถูกกลืนเข้าไปใน String ส่งผลให้เกิด Error ทั้ง `TerminatorExpectedAtEndOfString` และ `Missing closing '}'`
5. **ความเสี่ยงในคอมเมนต์ภาษาไทย (Line 53)**:
   - บรรทัดที่ 53 มีคอมเมนต์ภาษาไทย `# robocopy: 0-7 = สำเร็จ...` ซึ่งมีไบต์ `0x94` อยู่ในชุดตัวอักษรภาษาไทยเช่นกัน เสี่ยงต่อการตีความผิดพลาดของ Parser บน PowerShell เก่า

---

## 2. ขอบเขตงานและเป้าหมาย (Scope & Objectives)

1. **ปรับปรุง [.github/workflows/deploy.yml](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/.github/workflows/deploy.yml)**:
   - ปรับแต่งข้อความใน `Write-Host` (บรรทัด 58) และคอมเมนต์ (บรรทัด 53) ให้เป็น **Pure 7-bit ASCII** ปลอดภัย 100% ต่อทุก Code Page บน Windows
   - หลีกเลี่ยงอักขระพิเศษ (Em-dash) และ Apostrophe (`'`) ในจุดที่อาจเกิดความสับสน
2. **ตรวจสอบความถูกต้อง (Syntax Validation)**:
   - ตรวจสอบความถูกต้องของสคริปต์ PowerShell ทั้งหมดใน `deploy.yml` ว่าไม่มีอักขระ Non-ASCII หลงเหลือในบล็อก `run: |`
3. **เตรียมพร้อมสำหรับการ Deploy**:
   - Commit และ Push เข้า branch `deploy` เพื่อให้ Runner ทำการทดสอบ build และ backup ใหม่อีกครั้ง

---

## 3. รายละเอียดการแก้ไข (Technical Implementation Plan)

### [MODIFY] [.github/workflows/deploy.yml](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/.github/workflows/deploy.yml)

ตำแหน่งแก้ไข (บรรทัดที่ 52-60):

```diff
           if (Test-Path $env:UPLOADS_PATH) {
               robocopy $env:UPLOADS_PATH "$env:BACKUP_ROOT\uploads" /E /R:2 /W:2 /NP /NFL /NDL /XD chunks
-              # robocopy: 0-7 = สำเร็จ (มี/ไม่มีไฟล์ที่คัดลอก), 8 ขึ้นไป = ล้มเหลว
+              # robocopy: 0-7 = success (files copied or no change), 8+ = failure
               if ($LASTEXITCODE -ge 8) { throw "Backup of $env:UPLOADS_PATH failed (robocopy exit $LASTEXITCODE)" }
               $global:LASTEXITCODE = 0
               Write-Host "Backed up $env:UPLOADS_PATH -> $env:BACKUP_ROOT\uploads"
           } else {
-              Write-Host "::warning::$env:UPLOADS_PATH not found — uploads were NOT backed up. Set UPLOADS_PATH to the service's real uploads folder."
+              Write-Host "::warning::$env:UPLOADS_PATH not found - uploads were NOT backed up. Set UPLOADS_PATH to the service real uploads folder."
           }
```

---

## 4. รายการงานย่อย (Task Breakdown)

### Task 1: แก้ไขไฟล์ `.github/workflows/deploy.yml`
- **Agent:** `backend-specialist`
- **Skills:** `clean-code`, `bash-linux`
- **Input:** [.github/workflows/deploy.yml](file:///Users/kriangkrai/Developer/Projects/eclipse-workspace/Spring%20pj/pjweb/HRCP.KKU/.github/workflows/deploy.yml)
- **Output:** ไฟล์ `deploy.yml` ที่อัปเดตบรรทัด 53 และ 58 เป็น Pure ASCII
- **Verify:** รัน Python script ตรวจสอบว่าไม่มีไบต์ `> 127` ในบล็อก `run: |` ใดๆ ในไฟล์

### Task 2: ตรวจสอบและทดสอบโครงสร้าง YAML & Shell Blocks
- **Agent:** `backend-specialist`
- **Skills:** `clean-code`
- **Input:** โครงสร้าง YAML ของ `deploy.yml`
- **Output:** ผลการตรวจสอบ syntax ไม่มีความผิดพลาด
- **Verify:** ตรวจสอบความถูกต้องด้วย parser และ git diff

### Task 3: Commit และ Push เข้าสู่ Branch `deploy`
- **Agent:** `devops-engineer`
- **Skills:** `deployment-procedures`
- **Input:** Branch `deploy` ปัจจุบัน
- **Output:** Git commit ใหม่พร้อม push ไปยัง GitHub remote
- **Verify:** ตรวจสอบสถานะการรันของ Actions บน self-hosted runner

---

## 5. Phase X: Final Verification Checklist

- [x] **Non-ASCII Scan**: ทุกคำสั่งในบล็อก `run: |` ของ `deploy.yml` ต้องเป็น Pure ASCII (`ord(c) <= 127`) - ตรวจสอบแล้ว 0 ตัวอักษร
- [x] **Quote Balancing**: ตรวจสอบว่าไม่มี Unclosed Single/Double Quote ในบล็อก PowerShell - โครงสร้าง String สมบูรณ์
- [x] **Git Status Clean**: การเปลี่ยนแปลงเฉพาะบรรทัดที่เกี่ยวข้อง ไม่กระทบต่อการตั้งค่าตัวแปรสิ่งแวดล้อมหรือสเต็ปอื่น
- [ ] **CI Execution**: GitHub Actions รันผ่านขั้นตอน `Back up uploads and current jar` ได้สำเร็จ (รอผลหลัง Push)

---

## ✅ PHASE X STATUS
- Non-ASCII Scan: ✅ Pass
- Quote & Parser Validation: ✅ Pass
- Git Diff Validation: ✅ Clean
- Target File: `.github/workflows/deploy.yml`

