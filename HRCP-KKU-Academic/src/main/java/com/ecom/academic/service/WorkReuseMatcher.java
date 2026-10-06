package com.ecom.academic.service;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * ผลงานที่พิมพ์เองในแบบ ก.พ.ว. มข.๐๓ เป็นชิ้นเดียวกับผลงานที่เคยยื่นไปแล้วหรือไม่
 *
 * <p>ผลงานที่เลือกจากรายการผูกกับรหัสผลงาน จึงล็อกได้ตรงตัว ({@code position_request_publication})
 * แต่ผู้ยื่นพิมพ์เองหรือแก้ข้อความหลังเลือกได้ ระบบจึงต้องเทียบจากข้อความ: ตัดช่องว่าง เครื่องหมาย
 * และตัวพิมพ์ใหญ่เล็กออกก่อน แล้วถือว่าซ้ำเมื่อ
 * <ul>
 *   <li>ข้อความเหมือนกัน หรือข้อความหนึ่งอยู่ในอีกข้อความ (อ้างอิงเดิมที่ตัดหรือเติมท้าย)</li>
 *   <li>ต่างกันไม่เกิน 10% (พิมพ์ผิด เว้นวรรคต่าง)</li>
 *   <li>มีชื่อเรื่องของผลงานที่เคยเลือกจากรายการอยู่ในข้อความ</li>
 * </ul>
 * ข้อความสั้นมาก (ต่ำกว่า {@value #MIN_LENGTH} ตัวอักษรหลังตัด) ไม่นับ — สั้นขนาดนั้นซ้ำกันโดยบังเอิญได้
 */
final class WorkReuseMatcher {

    static final int MIN_LENGTH = 15;
    private static final double MAX_DISTANCE_RATIO = 0.10;
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final String THAI_DIGITS = "๐๑๒๓๔๕๖๗๘๙";

    private WorkReuseMatcher() {
    }

    /** ข้อความผลงาน {@code line} ซ้ำกับผลงานที่เคยยื่น ({@code earlierLines}) หรือชื่อผลงานที่ใช้ไปแล้ว ({@code spentTitles}) */
    static boolean reused(String line, Collection<String> earlierLines, Collection<String> spentTitles) {
        String mine = normalize(line);
        if (mine.length() < MIN_LENGTH) {
            return false;
        }
        for (String earlier : earlierLines) {
            if (sameWork(mine, normalize(earlier))) {
                return true;
            }
        }
        for (String title : spentTitles) {
            String t = normalize(title);
            if (t.length() >= MIN_LENGTH && mine.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameWork(String a, String b) {
        if (b.length() < MIN_LENGTH) {
            return false;
        }
        if (a.equals(b) || a.contains(b) || b.contains(a)) {
            return true;
        }
        int longer = Math.max(a.length(), b.length());
        if (Math.abs(a.length() - b.length()) > longer * MAX_DISTANCE_RATIO) {
            return false;
        }
        return levenshtein(a, b) <= longer * MAX_DISTANCE_RATIO;
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder digits = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            int thai = THAI_DIGITS.indexOf(c);
            digits.append(thai >= 0 ? (char) ('0' + thai) : c);
        }
        return NOT_LETTER_OR_DIGIT.matcher(digits.toString().toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = cur;
            cur = swap;
        }
        return prev[b.length()];
    }
}
