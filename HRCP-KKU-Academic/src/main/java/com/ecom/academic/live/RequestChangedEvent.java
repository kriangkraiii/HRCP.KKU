package com.ecom.academic.live;

import com.ecom.academic.model.SignatureModule;

/**
 * คำร้องนี้เปลี่ยน — สถานะ เอกสาร หรือซองลงนาม หน้าคำร้องที่เปิดค้างอยู่ควรดึงข้อมูลใหม่
 *
 * <p>ไม่บอกว่าเปลี่ยนอะไร: หน้าดึงสถานะทั้งหมดจากที่เดียวกับตอนโหลดหน้า จึงไม่มีทางเห็นไม่ตรงกัน
 */
public record RequestChangedEvent(SignatureModule module, Long requestId) {
}
