package com.ecom.util;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * นับวันทำการตามกำหนดเวลาในประกาศ มข. ฉบับที่ 1669/2569 และ 1670/2569 (3, 7, 30, 45 วันทำการ)
 *
 * <p>นับเฉพาะวันจันทร์–ศุกร์ ระบบไม่มีปฏิทินวันหยุดราชการ จึงไม่ได้หักวันหยุดนักขัตฤกษ์และวันหยุดของมหาวิทยาลัย
 * วันครบกำหนดที่แสดงจึงอาจเร็วกว่าจริงเมื่อมีวันหยุดอยู่ในช่วง — ทุกหน้าที่แสดงวันครบกำหนดต้องบอก {@link #NOTE}
 */
public final class WorkingDays {

    /** หมายเหตุที่แสดงคู่กับวันครบกำหนดทุกครั้ง */
    public static final String NOTE = "นับวันทำการตามวันเวลาราชการ (จันทร์–ศุกร์) ไม่ได้หักวันหยุดราชการและวันหยุดของมหาวิทยาลัย";

    private WorkingDays() {
    }

    public static boolean isWorkingDay(LocalDate day) {
        DayOfWeek dow = day.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }

    /**
     * วันทำการที่ {@code days} โดยเริ่มนับวันถัดจาก {@code from} — "ภายใน 3 วันทำการนับถัดจากวันรับเรื่อง"
     * รับเรื่องวันศุกร์ วันทำการที่ 3 คือวันพุธ
     */
    public static LocalDate plus(LocalDate from, int days) {
        if (from == null) {
            return null;
        }
        LocalDate day = from;
        int counted = 0;
        while (counted < days) {
            day = day.plusDays(1);
            if (isWorkingDay(day)) {
                counted++;
            }
        }
        return day;
    }
}
