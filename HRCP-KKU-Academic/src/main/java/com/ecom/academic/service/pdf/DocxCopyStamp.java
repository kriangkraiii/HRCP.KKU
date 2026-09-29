package com.ecom.academic.service.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Marks the Word copy of an incrementally signed document as a copy.
 *
 * <p>A .docx cannot carry the PDF's digital signatures. Handed out plain, it reads
 * like the signed document and can be edited and passed on as one. The banner at
 * the top says what it is and where the signed original is.
 */
public final class DocxCopyStamp {

    private DocxCopyStamp() {
    }

    static String text(String verificationCode) {
        return "สำเนา — ไม่มีผลทางลายมือชื่อ ฉบับที่ลงนามคือไฟล์ PDF ที่มีลายเซ็นดิจิทัล"
                + (verificationCode != null ? " (รหัสตรวจสอบ " + verificationCode + ")" : "");
    }

    public static byte[] stamp(byte[] docx, String verificationCode) throws IOException {
        String banner = "<w:p><w:pPr><w:jc w:val=\"center\"/><w:pBdr>"
                + "<w:top w:val=\"single\" w:sz=\"8\" w:space=\"1\" w:color=\"C00000\"/>"
                + "<w:bottom w:val=\"single\" w:sz=\"8\" w:space=\"1\" w:color=\"C00000\"/></w:pBdr></w:pPr>"
                + "<w:r><w:rPr><w:rFonts w:ascii=\"TH Sarabun New\" w:hAnsi=\"TH Sarabun New\" w:cs=\"TH Sarabun New\"/>"
                + "<w:b/><w:bCs/><w:color w:val=\"C00000\"/><w:sz w:val=\"28\"/><w:szCs w:val=\"28\"/></w:rPr>"
                + "<w:t xml:space=\"preserve\">" + escape(text(verificationCode)) + "</w:t></w:r></w:p>";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean stamped = false;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(docx));
                ZipOutputStream zout = new ZipOutputStream(out)) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                byte[] data = zin.readAllBytes();
                if (e.getName().equals("word/document.xml")) {
                    String xml = new String(data, StandardCharsets.UTF_8);
                    int body = xml.indexOf("<w:body");
                    if (body >= 0) {
                        int open = xml.indexOf('>', body) + 1;
                        xml = xml.substring(0, open) + banner + xml.substring(open);
                        stamped = true;
                    }
                    data = xml.getBytes(StandardCharsets.UTF_8);
                }
                zout.putNextEntry(new ZipEntry(e.getName()));
                zout.write(data);
                zout.closeEntry();
            }
        }
        if (!stamped) {
            throw new IOException("Not a Word document: no body to stamp");
        }
        return out.toByteArray();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
