package com.ecom.util;

import java.util.List;

/**
 * ตัวเลือกคำนำหน้าชื่อที่ฟอร์มฝั่งแอดมินเสนอให้เลือก
 *
 * <p>ก่อนหน้านี้รายการนี้ถูก hardcode ไว้ 4 ที่และ <b>ไม่ตรงกัน</b>: หน้าแก้ผู้ใช้กับแก้แอดมิน
 * มี 13 ตัวเลือก หน้าโปรไฟล์แอดมินมี 8 (ขาด อาจารย์/ผศ./รศ./ศ. แบบไม่มี ดร.) และหน้าเพิ่มแอดมิน
 * มี 5 ผลคือแอดมินที่คำนำหน้าเป็น "รศ." เปิดหน้าโปรไฟล์ตัวเองแล้วกดบันทึก คำนำหน้าหายไปเงียบ ๆ
 * เพราะไม่มี option ไหนตรงให้ browser เลือก
 *
 * <p>{@link #aliases()} คือรูปเขียนอื่นของค่าเดียวกันที่มีอยู่จริงในฐานข้อมูล — ค่ามาจากหลายทาง
 * ({@code fs_faculty.prefix}, เว็บวิทยาลัย, {@link AcademicTitleResolver}, ของเก่าที่พิมพ์เอง)
 * ใช้เทียบตอนตัดสินว่า option ไหนควรถูกเลือกไว้ ไม่ใช่ค่าที่จะถูกบันทึกลงไป
 */
public record NameTitleOption(String value, String label, List<String> aliases) {

    private static final List<NameTitleOption> ALL = List.of(
            new NameTitleOption("นาย", "นาย", List.of()),
            new NameTitleOption("นาง", "นาง", List.of()),
            new NameTitleOption("นางสาว", "นางสาว", List.of("น.ส.")),
            new NameTitleOption("ดร.", "ดร.", List.of()),
            new NameTitleOption("อาจารย์", "อาจารย์", List.of("อ.")),
            new NameTitleOption("อ.ดร.", "อาจารย์ ดร.", List.of("อาจารย์ ดร.")),
            new NameTitleOption("ผศ.", "ผศ.", List.of("ผู้ช่วยศาสตราจารย์")),
            new NameTitleOption("ผศ.ดร.", "ผศ.ดร.",
                    List.of("ผู้ช่วยศาสตราจารย์ ดร.", "ผู้ช่วยศาสตราจารย์ดร.")),
            new NameTitleOption("รศ.", "รศ.", List.of("รองศาสตราจารย์")),
            new NameTitleOption("รศ.ดร.", "รศ.ดร.",
                    List.of("รองศาสตราจารย์ ดร.", "รองศาสตราจารย์ดร.")),
            new NameTitleOption("ศ.", "ศ.", List.of("ศาสตราจารย์")),
            new NameTitleOption("ศ.ดร.", "ศ.ดร.",
                    List.of("ศาสตราจารย์ ดร.", "ศาสตราจารย์ดร.")));

    /** ทุกค่าที่ {@link AcademicTitleResolver#resolveShortTitle} ผลิตได้ ต้องมีอยู่ในนี้ครบ */
    public static List<NameTitleOption> all() {
        return ALL;
    }

    /**
     * ทุกรูปเขียนที่ list นี้รู้จัก — ใช้ถามว่าค่าที่เก็บอยู่ตรงกับ option ไหนหรือเปล่า
     * ถ้าไม่ตรงสักอัน ฟอร์มต้องเสนอมันเป็น option เพิ่มเอง ไม่งั้นการกดบันทึกจะลบค่านั้นทิ้ง
     */
    public static List<String> knownValues() {
        return ALL.stream()
                .flatMap(o -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(o.value()), o.aliases().stream()))
                .toList();
    }
}
