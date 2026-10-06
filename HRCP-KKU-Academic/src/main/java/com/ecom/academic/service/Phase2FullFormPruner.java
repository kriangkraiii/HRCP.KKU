package com.ecom.academic.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ตัดหัวข้อที่ไม่ได้กรอกหรือไม่เกี่ยวกับตำแหน่งที่ขอ ออกจากแบบ ก.พ.ว. มข.๐๓ (เอกสารที่ 1 ของเฟส 2)
 *
 * <p>แบบฟอร์มเปล่ามีทุกหัวข้อของทุกตำแหน่ง ถ้าพิมพ์ทั้งหมดผู้ขอ รศ. จะได้หัวข้อ ๔.๑ ของ ผศ. และ ๔.๓
 * ของ ศ. เป็นจุดไข่ปลาติดมาด้วย กติกา:
 * <ul>
 *   <li>๒.๓ (ผศ.) พิมพ์เฉพาะผู้ขอ รศ./ศ. ๒.๔ (รศ.) เฉพาะผู้ขอ ศ. — และต้องกรอกแล้ว</li>
 *   <li>๔.๑ / ๔.๒ / ๔.๓ เหลือเฉพาะหัวข้อของตำแหน่งที่ขอ ในนั้นตัดงานวิจัย/ผลงานลักษณะอื่น/ตำรา
 *       ที่ไม่ได้เสนอ</li>
 *   <li>อนุสาขาวิชา ๒.๕ ๒.๖ และ ๓.๕ เป็นข้อ "ถ้ามี" ไม่ได้กรอกก็ไม่พิมพ์</li>
 * </ul>
 * ข้อที่เหลือเลื่อนเลขขึ้นมาแทนข้อที่ตัด ({@link #renumber})
 *
 * <p>ทำงานกับ {@code document.xml} ที่รวม placeholder กลับเป็นชิ้นเดียวแล้ว (หลัง defragment) และ
 * ก่อนแทนค่า หาหัวข้อจากข้อความในย่อหน้า จึงไม่ต้องแก้ไฟล์ .docx ถ้าเทมเพลตไม่มีเครื่องหมายของ
 * เอกสารนี้ (เช่นเอกสารที่ 1 ของเฟส 1) คืนค่าเดิม
 */
final class Phase2FullFormPruner {

    private Phase2FullFormPruner() {
    }

    /** ย่อหน้า ตาราง หรือ content control ระดับบนสุดของ body */
    private record Block(int start, int end, String text) {
    }

    private static final Pattern BLOCK_TAG = Pattern.compile("<(/?)(w:p|w:tbl|w:sdt)(?=[\\s>/])[^>]*?(/?)>");
    private static final Pattern TEXT = Pattern.compile("<w:t(?:\\s[^>]*)?>([^<]*)</w:t>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final String MARKER = "{{assistant_appointment_date}}";

    static String prune(String xml, Map<String, String> data) {
        if (xml == null || !xml.contains(MARKER)) {
            return xml;
        }
        List<Block> blocks = blocks(xml);
        if (blocks.isEmpty()) {
            return xml;
        }
        boolean[] drop = new boolean[blocks.size()];
        String rank = rankKey(data.get("target_position"));

        if (!filled(data, "sub_major") && !filled(data, "sub_major_code")) {
            markEach(blocks, drop, b -> b.text.contains("{{sub_major}}"));
        }

        boolean assistantApplies = rank == null || rank.equals("assoc") || rank.equals("prof");
        if (!assistantApplies || !anyFilled(data, "assistant_(method|department|appointment_date)")) {
            markSpan(blocks, drop, b -> b.text.contains("{{assistant_method}}"),
                    b -> b.text.contains("{{assistant_appointment_date}}"));
        }
        boolean associateApplies = rank == null || rank.equals("prof");
        if (!associateApplies || !anyFilled(data, "associate_(method|department|appointment_date)")) {
            markSpan(blocks, drop, b -> b.text.contains("{{associate_method}}"),
                    b -> b.text.contains("{{associate_appointment_date}}"));
        }
        if (!anyFilled(data, "other_position_\\d+")) {
            markSpan(blocks, drop, startsWith("๒.๕"), b -> b.text.contains("{{other_position_1}}"));
        }
        if (!anyFilled(data, "international_speaker_last_5_years_\\d+")) {
            markSpan(blocks, drop, startsWith("๒.๖"),
                    b -> b.text.contains("{{international_speaker_last_5_years_1}}"));
        }
        if (!filled(data, "other")) {
            markSpan(blocks, drop, startsWith("๓.๕"), b -> b.text.contains("{{other}}"));
        }

        pruneWorks(blocks, drop, rank);
        pruneWorkKinds(blocks, drop, data, "๑", "asst");
        pruneWorkKinds(blocks, drop, data, "๒", "assoc");
        pruneWorkKinds(blocks, drop, data, "๓", "prof");

        Map<Integer, String[]> renumbered = renumber(blocks, drop);

        StringBuilder out = new StringBuilder(xml.length());
        int cursor = 0;
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            out.append(xml, cursor, b.start);
            cursor = b.end;
            if (drop[i]) {
                continue;
            }
            String blockXml = xml.substring(b.start, b.end);
            String[] change = renumbered.get(i);
            out.append(change == null ? blockXml : replaceLeadingNumber(blockXml, change[0], change[1]));
        }
        out.append(xml, cursor, xml.length());
        return out.toString();
    }

    // ---------------------------------------------------------------------
    // เลขข้อ — ข้อที่เหลือเลื่อนขึ้นมาแทนข้อที่ตัด
    // ---------------------------------------------------------------------

    private static final String THAI_DIGITS = "๐๑๒๓๔๕๖๗๘๙";

    /** "๒.๕ ตำแหน่งอื่น ๆ" / "๔.๒.๑ งานวิจัย" — หัวข้อ ไม่ใช่บรรทัดรายการ (ซึ่งมี "." ต่อท้ายเลข) */
    private static final Pattern HEADING = Pattern.compile("^\\s*([๒-๔])\\.([๑-๙])(?:\\.([๑-๙]))?(?![.๐-๙])");
    /** "๒.๕.{{other_position_no_1}}" / "๔.๒.๑.{{assoc_research_working_no_1}}" — บรรทัดรายการใต้หัวข้อ */
    private static final Pattern ITEM_ROW = Pattern.compile("^\\s*([๒-๔])\\.([๑-๙])(?:\\.([๑-๙]))?\\.\\{\\{");

    /**
     * เลขใหม่ของย่อหน้าที่เหลือในส่วนที่ ๑ — ไล่หัวข้อตามลำดับ ข้อย่อยนับใหม่ในแต่ละข้อหลัก
     * เช่นผู้ขอ รศ. ที่ไม่มี ๒.๔: ๒.๕ → ๒.๔, ๒.๖ → ๒.๕ และหัวข้อ ๔.๒ ของ รศ. → ๔.๑
     *
     * <p>หยุดที่ "ขอรับรองว่า" ส่วนที่ ๔ ของแบบฟอร์มมีเลข "๒.๑)" ของตัวเองที่ห้ามแตะ
     *
     * @return ตำแหน่งย่อหน้า → {เลขเดิม, เลขใหม่} เฉพาะที่เลขเปลี่ยน
     */
    private static Map<Integer, String[]> renumber(List<Block> blocks, boolean[] drop) {
        Map<Integer, String[]> changes = new java.util.HashMap<>();
        int end = find(blocks, 0, blocks.size(), startsWith("ขอรับรองว่า"));
        Map<String, Integer> counters = new java.util.HashMap<>();
        Map<String, String> newNumbers = new java.util.HashMap<>();
        for (int i = 0; i < (end < 0 ? blocks.size() : end); i++) {
            if (drop[i]) {
                continue;
            }
            String text = blocks.get(i).text;
            Matcher heading = HEADING.matcher(text);
            Matcher row = ITEM_ROW.matcher(text);
            String old;
            String renamed;
            if (heading.find()) {
                old = number(heading);
                String parent = heading.group(3) == null ? heading.group(1)
                        : newNumbers.getOrDefault(heading.group(1) + "." + heading.group(2),
                                heading.group(1) + "." + heading.group(2));
                int n = counters.merge(parent, 1, Integer::sum);
                renamed = parent + "." + THAI_DIGITS.charAt(n);
                newNumbers.put(old, renamed);
            } else if (row.find()) {
                old = number(row);
                renamed = newNumbers.getOrDefault(old, old);
                old = old + ".";
                renamed = renamed + ".";
            } else {
                continue;
            }
            if (!old.equals(renamed)) {
                changes.put(i, new String[] { old, renamed });
            }
        }
        return changes;
    }

    private static String number(Matcher m) {
        return m.group(1) + "." + m.group(2) + (m.group(3) == null ? "" : "." + m.group(3));
    }

    /** เลขข้ออยู่ต้นข้อความชิ้นแรกของย่อหน้าเสมอ (ตรวจกับเทมเพลตแล้ว) — แทนเฉพาะตรงนั้น */
    private static String replaceLeadingNumber(String blockXml, String oldNumber, String newNumber) {
        Matcher m = Pattern.compile("(<w:t(?:\\s[^>]*)?>\\s*)" + Pattern.quote(oldNumber)).matcher(blockXml);
        return m.find()
                ? blockXml.substring(0, m.start()) + m.group(1) + newNumber + blockXml.substring(m.end())
                : blockXml;
    }

    /** ๔.๑ / ๔.๒ / ๔.๓ — ตัดหัวข้อของตำแหน่งที่ไม่ได้ขอทั้งหัวข้อ */
    private static void pruneWorks(List<Block> blocks, boolean[] drop, String rank) {
        if (rank == null) {
            return;
        }
        String[][] sections = { { "๑", "asst" }, { "๒", "assoc" }, { "๓", "prof" } };
        for (String[] section : sections) {
            if (section[1].equals(rank)) {
                continue;
            }
            int[] range = sectionRange(blocks, section[0]);
            if (range != null) {
                for (int i = range[0]; i < range[1]; i++) {
                    drop[i] = true;
                }
            }
        }
    }

    /** ในหัวข้อ ๔.N ตัดงานวิจัย (.๑) ผลงานลักษณะอื่น (.๒) ตำรา (.๓) ที่ไม่ได้เสนอ */
    private static void pruneWorkKinds(List<Block> blocks, boolean[] drop, Map<String, String> data,
            String n, String prefix) {
        int[] section = sectionRange(blocks, n);
        if (section == null) {
            return;
        }
        String[] kinds = { "research", "other", "book" };
        String[] digits = { "๑", "๒", "๓" };
        for (int k = 0; k < kinds.length; k++) {
            if (anyFilled(data, prefix + "_" + kinds[k] + "_working_\\d+")) {
                continue;
            }
            int from = find(blocks, section[0], section[1], startsWith("๔." + n + "." + digits[k]));
            if (from < 0) {
                continue;
            }
            Predicate<Block> next = k + 1 < kinds.length
                    ? startsWith("๔." + n + "." + digits[k + 1])
                    : startsWith("ผลงานทางวิชาการทุกประเภท");
            int to = find(blocks, from + 1, section[1], next);
            for (int i = from; i < (to < 0 ? section[1] : to); i++) {
                drop[i] = true;
            }
        }
    }

    /** ช่วงย่อหน้าของหัวข้อ ๔.N — จากหัวข้อถึงก่อนหัวข้อถัดไป (๔.๓ จบก่อน "ขอรับรองว่า") */
    private static int[] sectionRange(List<Block> blocks, String n) {
        int start = find(blocks, 0, blocks.size(), startsWith("๔." + n + "ผลงานทางวิชาการที่เสนอ"));
        if (start < 0) {
            return null;
        }
        Predicate<Block> next = switch (n) {
            case "๑" -> startsWith("๔.๒ผลงานทางวิชาการที่เสนอ");
            case "๒" -> startsWith("๔.๓ผลงานทางวิชาการที่เสนอ");
            default -> startsWith("ขอรับรองว่า");
        };
        int end = find(blocks, start + 1, blocks.size(), next);
        return new int[] { start, end < 0 ? blocks.size() : end };
    }

    private static Predicate<Block> startsWith(String prefix) {
        String wanted = squash(prefix);
        return b -> squash(b.text).startsWith(wanted);
    }

    private static int find(List<Block> blocks, int from, int to, Predicate<Block> p) {
        for (int i = Math.max(0, from); i < Math.min(to, blocks.size()); i++) {
            if (p.test(blocks.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static void markEach(List<Block> blocks, boolean[] drop, Predicate<Block> p) {
        for (int i = 0; i < blocks.size(); i++) {
            if (p.test(blocks.get(i))) {
                drop[i] = true;
            }
        }
    }

    /** ตัดตั้งแต่ย่อหน้าแรกที่ตรง {@code first} ถึงย่อหน้าแรกที่ตรง {@code last} รวมทั้งสองย่อหน้า */
    private static void markSpan(List<Block> blocks, boolean[] drop, Predicate<Block> first,
            Predicate<Block> last) {
        int start = find(blocks, 0, blocks.size(), first);
        if (start < 0) {
            return;
        }
        int end = find(blocks, start, blocks.size(), last);
        if (end < 0) {
            return;
        }
        for (int i = start; i <= end; i++) {
            drop[i] = true;
        }
    }


    private static List<Block> blocks(String xml) {
        List<Block> result = new ArrayList<>();
        int bodyStart = xml.indexOf("<w:body>");
        int bodyEnd = xml.lastIndexOf("</w:body>");
        if (bodyStart < 0 || bodyEnd < 0) {
            return result;
        }
        Matcher m = BLOCK_TAG.matcher(xml);
        m.region(bodyStart + "<w:body>".length(), bodyEnd);
        int depth = 0;
        int start = -1;
        while (m.find()) {
            boolean closing = !m.group(1).isEmpty();
            boolean selfClosing = !m.group(3).isEmpty();
            if (selfClosing) {
                if (depth == 0) {
                    result.add(block(xml, m.start(), m.end()));
                }
                continue;
            }
            if (!closing) {
                if (depth == 0) {
                    start = m.start();
                }
                depth++;
            } else {
                depth--;
                if (depth == 0 && start >= 0) {
                    result.add(block(xml, start, m.end()));
                    start = -1;
                }
            }
        }
        // ย่อหน้าที่ถือตัวแบ่งส่วน (sectPr) ห้ามตัด มิฉะนั้นหน้ากระดาษและหัวท้ายกระดาษเพี้ยน — ไม่นับเป็นตัวเลือกเลย
        result.removeIf(b -> xml.substring(b.start, b.end).contains("<w:sectPr"));
        return result;
    }

    private static Block block(String xml, int start, int end) {
        StringBuilder text = new StringBuilder();
        Matcher t = TEXT.matcher(xml.substring(start, end));
        while (t.find()) {
            text.append(t.group(1));
        }
        return new Block(start, end, text.toString());
    }

    private static String squash(String s) {
        return WHITESPACE.matcher(s).replaceAll("");
    }

    private static boolean filled(Map<String, String> data, String key) {
        String v = data.get(key);
        return v != null && !v.isBlank();
    }

    private static boolean anyFilled(Map<String, String> data, String keyRegex) {
        Pattern p = Pattern.compile(keyRegex);
        return data.entrySet().stream()
                .anyMatch(e -> p.matcher(e.getKey()).matches() && e.getValue() != null && !e.getValue().isBlank());
    }

    /** "asst" / "assoc" / "prof" ตามตำแหน่งที่ขอ — null เมื่อไม่รู้ (พิมพ์ทุกหัวข้อ) */
    static String rankKey(String targetPosition) {
        if (targetPosition == null || targetPosition.isBlank()) {
            return null;
        }
        String t = squash(targetPosition);
        if (t.contains("รองศาสตราจารย์")) {
            return "assoc";
        }
        if (t.contains("ผู้ช่วยศาสตราจารย์")) {
            return "asst";
        }
        return t.contains("ศาสตราจารย์") ? "prof" : null;
    }
}
