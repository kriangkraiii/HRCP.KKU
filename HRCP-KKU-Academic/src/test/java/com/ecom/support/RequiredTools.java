package com.ecom.support;

import org.junit.jupiter.api.Assumptions;

/**
 * ตัดสินว่าเครื่องมือภายนอกที่ขาดไป ควรทำให้เทส "ข้าม" หรือ "พัง"
 *
 * <p>เทสบางชั้นต้องพึ่งของที่ไม่ได้อยู่ในรีโป — เบราว์เซอร์ของ Playwright,
 * LibreOffice สำหรับแปลง DOCX เป็น PDF, Docker สำหรับ PostgreSQL จริง
 * บนเครื่องนักพัฒนาที่ยังไม่ได้ติดตั้ง การข้ามไปพร้อมเหตุผลเป็นพฤติกรรมที่ถูก
 *
 * <p><b>แต่บน CI มันคือหายนะเงียบ ๆ</b> — ชั้นที่ควรถูกตรวจถูกข้ามไปทั้งชั้น
 * แล้ว build ขึ้นเขียว เรื่องนี้เกิดขึ้นจริงมาแล้วในโปรเจกต์นี้:
 * {@code MigrationOnPostgresTest} ขึ้นเป็น {@code skipped="9"} มาตลอด และซ่อน
 * ไว้ว่า {@code V16} รันไม่ผ่านบนฐานข้อมูลที่สร้างใหม่ ไม่มีใครเห็นจนกระทั่ง
 * มีคนบังเอิญเปิด Docker ไว้ตอนรันเทส
 *
 * <p>เมื่อรันด้วย {@code -Dhrcp.tools.required=true} เมธอดในคลาสนี้จะ
 * <b>fail แทน skip</b> CI จึงตั้งค่านี้เสมอ ส่วนการรันบนเครื่องยังข้ามให้ตามเดิม
 */
public final class RequiredTools {

    /** ตั้งค่านี้บน CI เพื่อให้เครื่องมือที่ขาดไปกลายเป็นความล้มเหลว ไม่ใช่การข้าม */
    public static final String REQUIRE_ALL = "hrcp.tools.required";

    private RequiredTools() {
    }

    private static boolean everythingIsMandatory() {
        return Boolean.getBoolean(REQUIRE_ALL);
    }

    /**
     * ประกาศว่าเทสนี้ต้องใช้เครื่องมือชิ้นหนึ่ง
     *
     * @param available ผลการตรวจว่ามีเครื่องมือนั้นหรือไม่
     * @param tool      ชื่อเครื่องมือ ใช้ในข้อความที่รายงานออกไป
     */
    public static void require(boolean available, String tool) {
        if (available) {
            return;
        }
        if (everythingIsMandatory()) {
            throw new AssertionError("ไม่พบ " + tool
                    + " และรันด้วย -D" + REQUIRE_ALL + "=true จึงถือว่าล้มเหลว"
                    + " — ชั้นเทสนี้ต้องรันได้จริง ไม่ใช่ถูกข้าม");
        }
        Assumptions.abort("ไม่พบ " + tool + " จึงข้ามเทสนี้"
                + " (รันด้วย -D" + REQUIRE_ALL + "=true เพื่อให้กรณีนี้ fail แทน)");
    }

    /** Docker daemon ที่ Testcontainers ใช้ */
    public static boolean dockerIsAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }
}
