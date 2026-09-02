# PLAN: Smart Photo Caching & Duplicate Download Prevention for College Sync

## 📌 Executive Summary
ปรับปรุงระบบการ Sync รูปภาพอาจารย์จากเว็บไซต์วิทยาลัยการคอมพิวเตอร์ (`computing.kku.ac.th`) ใน `CpDirectorySyncService.java` ให้มีความฉลาดในการตรวจสอบไฟล์ภาพบนดิสก์ (Disk Cache) เพื่อป้องกันการส่งคำขอ HTTP ไปดาวน์โหลดรูปภาพเดิมซ้ำๆ ทุกครั้งที่มีการกดปุ่ม Sync หรือเมื่อระบบเริ่มทำงาน

---

## 🔍 Problem Analysis (ปัญหาปัจจุบัน)
1. **การดาวน์โหลดซ้ำซ้อน:** แม้ว่าไฟล์รูปภาพ `cp-xxx.jpg` จะถูกดาวน์โหลดและเก็บไว้ในโฟลเดอร์ `uploads/profile_img/` เรียบร้อยแล้ว แต่ถ้าบัญชีผู้ใช้ในฐานข้อมูลยังไม่มีชื่อรูป (เช่น รีเซ็ต DB หรือสร้าง User ใหม่) ระบบจะยิง HTTP ออกไปโหลดรูปใหม่ทั้งหมด 75+ รูปจากเว็บคณะ
2. **ความช้าและสิ้นเปลืองแบนด์วิดท์:** การยิง HTTP Request ซ้ำๆ ทำให้อาจารย์/แอดมินต้องรอนาน และเพิ่มภาระให้กับเซิร์ฟเวอร์ของวิทยาลัย
3. **ขาดการบีบอัดรูปภาพ:** รูปที่ดึงมาจากเว็บคณะถูกบันทึกดิบๆ โดยไม่ได้ผ่านการ Optimize/Resize

---

## 🛠️ Implementation Tasks

### 1. Smart Photo Cache Check in `CpDirectorySyncService.java`
- ก่อนที่จะเรียก `client.fetchImage(url)`:
  - คำนวณชื่อไฟล์ที่คาดหวัง: `String targetFileName = fileNameFor(person);`
  - ตรวจสอบว่า `imageStorage.exists(targetFileName)` หรือไม่
  - **ถ้าไฟล์มีอยู่แล้วในดิสก์:**
    - กำหนด `user.setProfileImage(targetFileName);` ได้ทันที **โดยไม่ต้องยิง HTTP ออกไปโหลด**
    - บันทึก log: `Reusing existing photo from disk for {email}: {targetFileName}`
  - **ถ้าไฟล์ยังไม่มีในดิสก์:**
    - ยิง `client.fetchImage(url)` ดาวน์โหลดรูปภาพ
    - บันทึกลงดิสก์ผ่าน `imageStorage.storeFromBytes(...)`
    - กำหนดชื่อรูปให้ `user`

### 2. Auto Image Optimization in `ProfileImageStorage.java`
- ปรับปรุง `storeFromBytes(byte[] content, String baseName)` ให้เรียกใช้ `optimizeImage(content, extension)`
- รูปภาพที่ดาวน์โหลดมาจากเว็บคณะจะถูก Resize เป็นขนาดกะทัดรัด (Max 512x512) คุณภาพสูง ช่วยประหยัดพื้นที่ดิสก์และทำให้หน้าเว็บโหลดรูปเร็วขึ้นทันที

### 3. Verification & Testing
- เขียน Unit Test ใน `CpDirectorySyncServiceTest.java` เพื่อทดสอบ:
  - กรณีที่มีไฟล์รูปอยู่บนดิสก์แล้ว ➔ ต้องไม่เรียก `client.fetchImage(...)`
  - กรณีที่ยังไม่มีไฟล์รูปบนดิสก์ ➔ ต้องเรียก `client.fetchImage(...)` และบันทึกไฟล์สำเร็จ

---

## 📋 Task Breakdown
- [ ] Update `applyPhoto` logic in `CpDirectorySyncService.java`
- [ ] Optimize `storeFromBytes` in `ProfileImageStorage.java`
- [ ] Update/Add Unit Tests in `CpDirectorySyncServiceTest.java`
- [ ] Run `./mvnw test` to verify zero regression
