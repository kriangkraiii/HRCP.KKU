package com.ecom.academic.live;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ecom.academic.model.SignatureModule;

/**
 * อัปเดตสดของหน้าคำร้องเจ้าหน้าที่ (Server-Sent Events) — คำร้องเปลี่ยนเมื่อไร หน้าที่เปิดคำร้องนั้นอยู่ได้รับแจ้งทันที
 *
 * <p>ส่งแค่ "เปลี่ยนแล้ว" หน้าไปดึงส่วนที่ต้องแสดงใหม่เอง ({@code request_live.js})
 * แจ้งหลัง commit เท่านั้น ({@link TransactionalEventListener}) — แจ้งก่อนแล้วหน้าดึงทันที จะได้ข้อมูลเก่า
 *
 * <p>เก็บการเชื่อมต่อในหน่วยความจำของเครื่องนี้ — รันหลายเครื่องหลัง load balancer ต้องกระจายเหตุการณ์ข้ามเครื่องเพิ่ม
 * proxy หน้าเซิร์ฟเวอร์ต้องไม่กักข้อมูล (nginx: ปิด proxy_buffering หรือใช้ header {@code X-Accel-Buffering: no} ที่ส่งไปแล้ว)
 */
@Component
public class RequestLiveUpdates {

    private static final Logger log = LoggerFactory.getLogger(RequestLiveUpdates.class);

    /** หน้าที่เปิดค้างนานกว่านี้ เบราว์เซอร์ต่อใหม่เอง (EventSource) */
    static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private record Key(SignatureModule module, Long requestId) {
    }

    private final Map<Key, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    /** เปิดการเชื่อมต่อของหน้าคำร้องหนึ่งหน้า */
    public SseEmitter subscribe(SignatureModule module, Long requestId) {
        Key key = new Key(module, requestId);
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = subscribers.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> remove(key, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            // บอกเบราว์เซอร์ว่าต่อติดแล้ว — proxy บางตัวไม่ส่งอะไรออกไปจนกว่าจะมีข้อมูลก้อนแรก
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onChanged(RequestChangedEvent event) {
        List<SseEmitter> list = subscribers.get(new Key(event.module(), event.requestId()));
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("changed").data(String.valueOf(System.currentTimeMillis())));
            } catch (IOException | IllegalStateException e) {
                remove(new Key(event.module(), event.requestId()), emitter);
            }
        }
    }

    /** ส่ง comment เป็นระยะ — กัน proxy ตัดการเชื่อมต่อที่เงียบ และเก็บกวาดหน้าที่ปิดไปแล้ว */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        subscribers.forEach((key, list) -> list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                remove(key, emitter);
            }
        }));
    }

    /** จำนวนหน้าที่เปิดคำร้องนี้อยู่ — สำหรับเทสต์ */
    int subscriberCount(SignatureModule module, Long requestId) {
        List<SseEmitter> list = subscribers.get(new Key(module, requestId));
        return list == null ? 0 : list.size();
    }

    private void remove(Key key, SseEmitter emitter) {
        subscribers.computeIfPresent(key, (k, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
        log.trace("Live update subscriber for {} removed", key);
    }
}
