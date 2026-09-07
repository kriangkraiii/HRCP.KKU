package com.ecom.search.index;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads the words out of an uploaded document.
 *
 * <p>Only genuinely opaque files are worth this. Documents the system generated
 * itself are rendered from {@code json_data}, which is already indexed
 * verbatim — extracting them again would burn CPU to obtain text the index
 * already has, and would put the same request in the results twice.
 *
 * <p>Everything here is defensive. These are files people uploaded: they can be
 * huge, encrypted, mislabelled, truncated, or no longer on disk at all. None of
 * those is exceptional and none should stop the queue, so each becomes a state
 * on the row rather than a thrown error.
 */
@Component
public class FileTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(FileTextExtractor.class);

    private final long maxFileBytes;
    private final int maxChars;

    public FileTextExtractor(
            @Value("${app.search.file-text.max-file-bytes:52428800}") long maxFileBytes,
            @Value("${app.search.file-text.max-chars:200000}") int maxChars) {
        this.maxFileBytes = maxFileBytes;
        this.maxChars = maxChars;
    }

    /**
     * What became of one file.
     *
     * @param text  the extracted text, or null
     * @param state where the row should end up
     * @param error a short reason, when there is one
     */
    public record Extraction(String text, com.ecom.search.model.ExtractionState state, String error) {

        static Extraction done(String text) {
            return new Extraction(text, com.ecom.search.model.ExtractionState.DONE, null);
        }

        static Extraction skipped(String why) {
            return new Extraction(null, com.ecom.search.model.ExtractionState.SKIPPED, why);
        }

        static Extraction failed(String why) {
            return new Extraction(null, com.ecom.search.model.ExtractionState.FAILED, why);
        }
    }

    public Extraction extract(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return Extraction.skipped("ไม่มีพาธไฟล์");
        }

        Path path;
        try {
            path = Path.of(storedPath);
        } catch (RuntimeException e) {
            return Extraction.skipped("พาธไฟล์ไม่ถูกต้อง");
        }

        if (!Files.isRegularFile(path)) {
            // Files are cleaned up on their own schedule; a row pointing at one
            // that has gone is ordinary, not a failure to investigate.
            return Extraction.skipped("ไม่พบไฟล์บนดิสก์");
        }

        try {
            long size = Files.size(path);
            if (size > maxFileBytes) {
                return Extraction.skipped("ไฟล์ใหญ่เกิน " + maxFileBytes + " ไบต์");
            }

            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".docx")) {
                return Extraction.done(cap(readDocx(path)));
            }
            if (name.endsWith(".pdf")) {
                return Extraction.done(cap(readPdf(path)));
            }
            // .doc needs POI's scratchpad module, which this project does not
            // ship; .zip is a container the search has no business opening.
            return Extraction.skipped("ไม่รองรับชนิดไฟล์นี้");

        } catch (Throwable t) {
            // Throwable, not Exception: a corrupt file can take POI or PDFBox
            // past an Error, and one bad upload must not stop the queue.
            log.debug("สกัดข้อความจาก {} ไม่สำเร็จ: {}", storedPath, t.toString());
            return Extraction.failed(shorten(t.getClass().getSimpleName() + ": " + t.getMessage()));
        }
    }

    private String readDocx(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path);
                XWPFDocument document = new XWPFDocument(in);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String readPdf(Path path) throws Exception {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            if (document.isEncrypted()) {
                // Loader opens some encrypted files with an empty password;
                // reading them anyway would be going further than the person who
                // locked the file intended.
                throw new IllegalStateException("ไฟล์ถูกเข้ารหัส");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(false);
            return stripper.getText(document);
        }
    }

    /** Collapses whitespace and caps the length, so one file cannot dominate the index. */
    private String cap(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String collapsed = raw.replaceAll("\\s+", " ").trim();
        return collapsed.length() > maxChars ? collapsed.substring(0, maxChars) : collapsed;
    }

    private static String shorten(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 480 ? message.substring(0, 480) : message;
    }
}
