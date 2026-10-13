package com.ecom.academic.live;

import jakarta.servlet.http.HttpServletRequest;

/**
 * คำขอที่เปิดการเชื่อมต่ออัปเดตสด (Server-Sent Events) ของหน้าคำร้อง
 *
 * <p>คำขอแบบนี้ค้างอยู่ได้นานถึง {@link RequestLiveUpdates#TIMEOUT_MS} ทุกอย่างที่ห่อ response ไว้ก่อนส่ง
 * หรือหยิบ DB connection ระหว่างคำขอ จึงต้องข้ามมัน — ห่อไว้ทำให้เหตุการณ์ไปไม่ถึงเบราว์เซอร์
 * ส่วน connection ที่หยิบไป open-in-view จะถือไว้จนคำขอจบ หน้าที่เปิดค้างไม่กี่หน้าก็ทำให้ pool หมด
 */
public final class EventStreamRequests {

    private EventStreamRequests() {
    }

    public static boolean isEventStream(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/event-stream")) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.endsWith("/live");
    }
}
