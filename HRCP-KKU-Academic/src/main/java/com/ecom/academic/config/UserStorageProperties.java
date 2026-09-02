// [ปิดการใช้งาน] คลังไฟล์ส่วนตัวของผู้ยื่น (user storage)
//
// ระบบเคยมีคลังไฟล์สองชุดที่เป็นฝาแฝดกัน ฝั่งแอดมิน (/admin/file-manager)
// กับฝั่งผู้ยื่น (/user/academic/storage) ตอนนี้เหลือใช้เฉพาะฝั่งแอดมิน
//
// ตาราง user_file และ user_folder ถูก drop ไปแล้วด้วย V16__drop_user_storage.sql
// และไฟล์ใน uploads/user-storage/ ถูกลบทิ้ง โค้ดข้างล่างจึงไม่มีอะไรรองรับ
// เก็บไว้เป็นคอมเมนต์ตามที่ตกลงกันไว้ ไม่ได้ลบทิ้ง
//
// ถ้าจะเปิดกลับ ต้องสร้างตารางทั้งสองขึ้นใหม่ก่อน ไม่ใช่แค่ปลดคอมเมนต์

// package com.ecom.academic.config;
//
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.stereotype.Component;
//
// /**
//  * สวิตช์ควบคุมคลังไฟล์ส่วนตัวของผู้ยื่น (/user/academic/storage)
//  */
// @Component
// public class UserStorageProperties {
//
//     private final boolean enabled;
//
//     public UserStorageProperties(
//             @Value("${app.storage.user.enabled:true}") boolean enabled) {
//         this.enabled = enabled;
//     }
//
//     public boolean isEnabled() {
//         return enabled;
//     }
// }
