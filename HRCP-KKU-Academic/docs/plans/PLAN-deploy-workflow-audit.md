# แผนการตรวจสอบและปรับปรุง Deployment Workflow (`deploy.yml`)

## 📌 บทสรุปการวิเคราะห์ (Executive Summary)

จากการตรวจสอบการเปลี่ยนแปลงล่าสุดใน `.github/workflows/deploy.yml` พบว่า **มีจุดเสี่ยงวิกฤต (Critical Risks) 3-4 จุด** ที่จะทำให้กระบวนการ Deploy ล้มเหลวทันที หรือทำให้ระบบบนเซิร์ฟเวอร์สตาร์ทไม่ติด ดังนี้:

| จุดที่พบ | สิ่งที่เปลี่ยนล่าสุด | ความเสี่ยงและผลกระทบ (Impact) | ระดับความเสี่ยง |
|---|---|---|:---:|
| **1. ปัญหา Windows File Lock** | ลบ Wait Loop ใน Step 3 ออก เหลือแค่ `Stop-Service` แล้วข้ามไปก๊อปปี้ไฟล์ทันที | **วิกฤต (High):** Windows ทำงานแบบ Asynchronous ในการหยุด Service เมื่อ JVM ยังปิด connection หรือ unmap ไฟล์ `app.jar` ไม่เสร็จ คำสั่ง `Copy-Item` จะติด Lock: *`The process cannot access the file ... because it is being used by another process`* | 🔴 Critical |
| **2. การเลือกไฟล์ JAR ด้วย `Select-Object -First 1`** | ลบตัวกรอง `Where-Object { ... -notlike "*original*" }` ออก | **วิกฤต (High):** Maven จะสร้างไฟล์ `original-*.jar` คู่กับ Fat JAR เสมอ การหยิบไฟล์แรกโดยไม่คัดกรอง อาจได้ไฟล์ขนาดเล็กที่ไม่มี dependencies ทำให้ Service สตาร์ทไม่ขึ้น (`ClassNotFoundException`) | 🔴 Critical |
| **3. สิทธิ์การตั้งค่า `JAVA_TOOL_OPTIONS` ระดับ Machine** | ลบ `try-catch` และ `continue-on-error: true` ออก | **ปานกลาง (Medium):** หาก Runner บน Windows Server รันด้วยสิทธิ์ User ทั่วไป (ไม่ใช่ Admin) จะ Error สิทธิ์เข้าถึง Registry และทำให้ Workflow พังทันที | 🟡 Medium |
| **4. ขาดการตรวจสอบสถานะหลัง Start Service** | ลบการตรวจ `Get-Service` หลัง `Start-Service` ออก | **ปานกลาง (Medium):** หากแอปสตาร์ทไม่ติด (เช่น Port ชน หรือ Spring Crash) GitHub Actions จะขึ้นเครื่องหมายถูกสีเขียว (Passed) หลอกตา ทั้งที่ระบบจริงดับอยู่ | 🟡 Medium |

---

## 🛠️ แผนการปรับปรุงแก้ไข (Action Plan)

### ขั้นตอนที่ 1: คืนค่า Wait Loop ใน Step 3 (ป้องกัน File Lock)
```powershell
Write-Host "Stopping service $env:SERVICE_NAME ..."
Stop-Service -Name $env:SERVICE_NAME -Force -ErrorAction SilentlyContinue
$waited = 0
while ((Get-Service -Name $env:SERVICE_NAME -ErrorAction SilentlyContinue).Status -eq 'Running' -and $waited -lt 20) {
    Start-Sleep -Seconds 1
    $waited++
}
Start-Sleep -Seconds 2
Write-Host "Service stopped and file lock released."
```

### ขั้นตอนที่ 2: เลือกเฉพาะ Spring Boot Executable Fat JAR ใน Step 4
```powershell
$jarFile = Get-ChildItem -Path "${{ env.PROJECT_DIR }}\target\*.jar" |
    Where-Object { $_.Name -notlike "*.original*" -and $_.Name -notlike "original-*" } |
    Sort-Object Length -Descending |
    Select-Object -First 1

if (-not $jarFile) {
    throw "No valid executable fat jar found in ${{ env.PROJECT_DIR }}\target!"
}
Write-Host "Copying $($jarFile.FullName) ($([math]::Round($jarFile.Length/1MB, 2)) MB) -> $env:DEPLOY_PATH"
Copy-Item -Path $jarFile.FullName -Destination $env:DEPLOY_PATH -Force
```

### ขั้นตอนที่ 3: เสริมความปลอดภัยให้ Step 4.1 (`JAVA_TOOL_OPTIONS`)
- ใส่ `try-catch` และ `continue-on-error: true` เพื่อไม่ให้บล็อกการ Deploy หากติดสิทธิ์ User ทั่วไป
- เพิ่มการแจ้งเตือนว่าตัวแปรนี้จะมีผลกับ JVM รอบถัดไป

### ขั้นตอนที่ 4: ใส่ Health Check สั้นๆ หลัง Start Service ใน Step 5
```powershell
Write-Host "Starting service $env:SERVICE_NAME ..."
Start-Service -Name $env:SERVICE_NAME
Start-Sleep -Seconds 3
$svc = Get-Service -Name $env:SERVICE_NAME
Write-Host "Service status: $($svc.Status)"
if ($svc.Status -ne 'Running') {
    throw "Service $env:SERVICE_NAME failed to start (current status: $($svc.Status))"
}
Write-Host "Service started successfully."
```

---

## 📋 แผนการตรวจสอบและทดสอบ (Verification Plan)
1. **Static Validation:** ตรวจสอบโครงสร้าง YAML Syntax ผ่านเครื่องมือ linter
2. **Review with User:** อธิบายความเสี่ยงและขออนุมัติแนวทางปรับแก้
