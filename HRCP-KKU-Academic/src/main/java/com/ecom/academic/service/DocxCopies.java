package com.ecom.academic.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * ต่อสำเนาของเอกสารฉบับเดียวกันเป็นไฟล์เดียว ฉบับละหน้าใหม่
 *
 * <p>ใช้กับสำเนาที่สร้างจากเทมเพลตเดียวกันและรูปลายเซ็นชุดเดียวกันเท่านั้น — ไฟล์ประกอบ
 * (รูป relationship สไตล์) ของทุกสำเนาจึงตรงกันทุกไบต์ ใช้ของสำเนาแรกได้เลย ต่อแค่เนื้อความ
 */
public final class DocxCopies {

    private DocxCopies() {
    }

    private static final String DOCUMENT = "word/document.xml";
    /** เลข id ของรูปในแต่ละสำเนาห่างกันเท่านี้ — Word ไม่ยอมให้ id ของรูปซ้ำกันในไฟล์เดียว */
    private static final int ID_STEP = 1000;
    private static final Pattern DRAWING_ID = Pattern.compile("(<(?:wp:docPr|pic:cNvPr)\\b[^>]*?\\sid=\")(\\d+)\"");
    private static final Pattern BOOKMARK_ID = Pattern.compile("(<w:bookmark(?:Start|End)\\b[^>]*?\\sw:id=\")(\\d+)\"");
    private static final Pattern PARAGRAPH_IDS = Pattern.compile("\\sw14:(?:paraId|textId)=\"[^\"]*\"");

    static byte[] oneAfterAnother(List<byte[]> copies) throws IOException {
        if (copies.size() == 1) {
            return copies.get(0);
        }
        String first = documentXml(copies.get(0));
        int sectPr = first.lastIndexOf("<w:sectPr");
        if (sectPr < 0) {
            sectPr = first.lastIndexOf("</w:body>");
        }
        int open = first.indexOf('>', first.indexOf("<w:body")) + 1;
        StringBuilder merged = new StringBuilder(first.substring(0, open))
                .append(marker(1)).append(first, open, sectPr);
        for (int i = 1; i < copies.size(); i++) {
            merged.append(marker(i + 1))
                    .append(onNewPage(renumber(body(documentXml(copies.get(i))), i * ID_STEP)));
        }
        merged.append(first.substring(sectPr));
        return withDocument(copies.get(0), merged.toString());
    }

    /**
     * ฉบับที่ {@code copy} (นับจาก 1) จากไฟล์ที่ {@link #oneAfterAnother} ต่อไว้
     *
     * @return ไฟล์เดิมทั้งไฟล์ เมื่อไม่ใช่ไฟล์ที่ต่อสำเนาไว้
     */
    public static byte[] only(byte[] docx, int copy) throws IOException {
        String xml = documentXml(docx);
        int start = xml.indexOf(marker(copy));
        if (start < 0) {
            return docx;
        }
        int from = start + marker(copy).length();
        int next = xml.indexOf(marker(copy + 1), from);
        int sectPr = xml.lastIndexOf("<w:sectPr");
        String body = xml.substring(from, next >= 0 ? next : sectPr);
        if (copy > 1) {
            body = body.startsWith(PAGE_BREAK) ? body.substring(PAGE_BREAK.length())
                    : body.replaceFirst("<w:pageBreakBefore/>", "");
        }
        int open = xml.indexOf('>', xml.indexOf("<w:body")) + 1;
        return withDocument(docx, xml.substring(0, open) + body + xml.substring(sectPr));
    }

    /** ที่คั่นหัวแต่ละฉบับ — bookmark ว่าง ไม่พิมพ์อะไรออกมา */
    private static String marker(int copy) {
        return "<w:bookmarkStart w:id=\"" + (MARKER_ID + copy) + "\" w:name=\"_hrcp_copy_" + copy
                + "\"/><w:bookmarkEnd w:id=\"" + (MARKER_ID + copy) + "\"/>";
    }

    private static final int MARKER_ID = 990000;
    private static final String PAGE_BREAK = "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>";

    /** เนื้อความใน {@code <w:body>} ไม่รวมการตั้งค่าหน้ากระดาษท้ายเอกสาร */
    private static String body(String xml) {
        int open = xml.indexOf('>', xml.indexOf("<w:body")) + 1;
        int end = xml.lastIndexOf("<w:sectPr");
        return xml.substring(open, end >= open ? end : xml.lastIndexOf("</w:body>"));
    }

    private static String renumber(String xml, int offset) {
        xml = shift(DRAWING_ID, xml, offset);
        xml = shift(BOOKMARK_ID, xml, offset);
        return PARAGRAPH_IDS.matcher(xml).replaceAll("");
    }

    private static String shift(Pattern p, String xml, int offset) {
        Matcher m = p.matcher(xml);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(
                    m.group(1) + (Long.parseLong(m.group(2)) + offset) + "\""));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * ให้ย่อหน้าแรกขึ้นหน้าใหม่ — ไม่แทรกย่อหน้าว่างคั่น เพราะจะดันทั้งหน้าลงหนึ่งบรรทัด
     */
    private static String onNewPage(String body) {
        if (body.startsWith("<w:p>") || body.startsWith("<w:p ")) {
            int tagEnd = body.indexOf('>') + 1;
            if (body.startsWith("<w:pPr>", tagEnd)) {
                int at = tagEnd + "<w:pPr>".length();
                // pStyle ต้องมาก่อนเสมอตาม schema
                if (body.startsWith("<w:pStyle", at)) {
                    at = body.indexOf("/>", at) + 2;
                }
                return body.substring(0, at) + "<w:pageBreakBefore/>" + body.substring(at);
            }
            return body.substring(0, tagEnd) + "<w:pPr><w:pageBreakBefore/></w:pPr>" + body.substring(tagEnd);
        }
        return PAGE_BREAK + body;
    }

    private static String documentXml(byte[] docx) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (DOCUMENT.equals(e.getName())) {
                    return new String(zin.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("DOCX has no " + DOCUMENT);
    }

    private static byte[] withDocument(byte[] docx, String xml) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(docx));
                ZipOutputStream zout = new ZipOutputStream(out)) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                byte[] data = zin.readAllBytes();
                if (DOCUMENT.equals(e.getName())) {
                    data = xml.getBytes(StandardCharsets.UTF_8);
                }
                zout.putNextEntry(new ZipEntry(e.getName()));
                zout.write(data);
                zout.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
