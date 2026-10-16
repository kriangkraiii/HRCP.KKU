package com.ecom.util;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * โทเค็นลิงก์ตั้งรหัสผ่านใหม่ — สุ่ม + เวลาออก ในสตริงเดียว ({@code <uuid>.<epochSeconds>})
 *
 * <p>เดิมเป็น UUID เปล่า ใช้ได้ตลอดไปจนกว่าจะถูกใช้ ลิงก์ในอีเมลเก่าที่หลุดไปจึงตั้งรหัสผ่านของคนอื่นได้
 * ทุกเมื่อ ใส่เวลาไว้ในตัวโทเค็นแทนการเพิ่มคอลัมน์ — คอลัมน์ reset_token เดิมเก็บได้ทั้งก้อน
 * โทเค็นรุ่นเก่าที่ไม่มีเวลาถือว่าหมดอายุ ผู้ใช้ขอลิงก์ใหม่ได้
 */
public final class PasswordResetTokens {

    /** ลิงก์ใช้ได้นานเท่านี้หลังส่ง */
    public static final Duration LIFETIME = Duration.ofHours(1);

    private PasswordResetTokens() {
    }

    public static String issue(Instant now) {
        return UUID.randomUUID() + "." + now.getEpochSecond();
    }

    /** โทเค็นนี้ยังอยู่ในอายุหรือไม่ — รูปแบบผิด ไม่มีเวลา หรือเวลาในอนาคตถือว่าใช้ไม่ได้ */
    public static boolean isFresh(String token, Instant now) {
        if (token == null) {
            return false;
        }
        int dot = token.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        try {
            Instant issued = Instant.ofEpochSecond(Long.parseLong(token.substring(dot + 1)));
            return !issued.isAfter(now.plusSeconds(60)) && !now.isAfter(issued.plus(LIFETIME));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
