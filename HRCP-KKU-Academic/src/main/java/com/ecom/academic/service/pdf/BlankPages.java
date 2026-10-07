package com.ecom.academic.service.pdf;

import java.awt.geom.Point2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Drops the empty pages LibreOffice lays out when a document grows by a line.
 *
 * <p>A signature picture or a reserved value can make the last paragraph of a
 * page just too tall, and the empty paragraph after it — or the one before a page
 * break — spills onto a page of its own. Nobody wants that page, and every
 * template here is meant to print without one.
 *
 * <p><b>What counts as empty.</b> No picture, no drawn line or fill, and no text
 * but the page's furniture: a header repeated from the page before it, or a page
 * number. Header text is told apart by standing at the same height on the previous
 * kept page; digits and punctuation are ignored so "- 2 -" counts as a page number.
 * Non-breaking spaces are content: they are the reserved places of late values.
 * The first page always stays.
 */
public final class BlankPages {

    /** How far apart, in points, a header line may sit on two pages and still be the same line. */
    private static final float SAME_LINE = 1.5f;

    private BlankPages() {
    }

    /**
     * @return {@code pdf} itself when no page is empty, otherwise a copy without the empty pages
     */
    public static byte[] drop(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            int total = doc.getNumberOfPages();
            if (total < 2) {
                return pdf;
            }
            List<Integer> empty = new ArrayList<>();
            Map<Float, String> kept = lines(doc, 0);
            for (int p = 1; p < total; p++) {
                Map<Float, String> here = lines(doc, p);
                if (!drawsAnything(doc.getPage(p)) && onlyFurniture(here, kept)) {
                    empty.add(p);
                } else {
                    kept = here;
                }
            }
            if (empty.isEmpty()) {
                return pdf;
            }
            for (int i = empty.size() - 1; i >= 0; i--) {
                doc.removePage(empty.get(i));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Every line of {@code page} that has content once digits, punctuation and spaces are gone, by baseline height. */
    private static Map<Float, String> lines(PDDocument doc, int page) throws IOException {
        Map<Float, StringBuilder> byHeight = new LinkedHashMap<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void processTextPosition(TextPosition text) {
                byHeight.computeIfAbsent(text.getYDirAdj(), y -> new StringBuilder()).append(text.getUnicode());
            }
        };
        stripper.setStartPage(page + 1);
        stripper.setEndPage(page + 1);
        stripper.getText(doc);

        Map<Float, String> out = new LinkedHashMap<>();
        byHeight.forEach((y, s) -> {
            String meaningful = meaningful(s);
            if (!meaningful.isEmpty()) {
                out.merge(y, meaningful, String::concat);
            }
        });
        return out;
    }

    private static String meaningful(CharSequence s) {
        StringBuilder out = new StringBuilder();
        s.codePoints().filter(c -> !Character.isWhitespace(c) && !Character.isDigit(c) && !isPunctuation(c))
                .forEach(out::appendCodePoint);
        return out.toString();
    }

    private static boolean isPunctuation(int c) {
        return switch (Character.getType(c)) {
            case Character.DASH_PUNCTUATION, Character.START_PUNCTUATION, Character.END_PUNCTUATION,
                    Character.OTHER_PUNCTUATION -> true;
            default -> false;
        };
    }

    /** Whether every line of {@code page} is also on {@code previous}, at the same height. */
    private static boolean onlyFurniture(Map<Float, String> page, Map<Float, String> previous) {
        return page.entrySet().stream().allMatch(line -> previous.entrySet().stream()
                .anyMatch(p -> Math.abs(p.getKey() - line.getKey()) <= SAME_LINE && p.getValue().equals(line.getValue())));
    }

    /** Whether the page paints a picture, a line or a fill anywhere, form XObjects included. */
    private static boolean drawsAnything(PDPage page) throws IOException {
        Painter painter = new Painter(page);
        painter.processPage(page);
        return painter.painted;
    }

    private static final class Painter extends PDFGraphicsStreamEngine {
        private boolean painted;

        Painter(PDPage page) {
            super(page);
        }

        @Override
        public void drawImage(PDImage pdImage) {
            painted = true;
        }

        @Override
        public void strokePath() {
            painted = true;
        }

        @Override
        public void fillPath(int windingRule) {
            painted = true;
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
            painted = true;
        }

        @Override
        public void shadingFill(COSName shadingName) {
            painted = true;
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
        }

        @Override
        public void clip(int windingRule) {
        }

        @Override
        public void moveTo(float x, float y) {
        }

        @Override
        public void lineTo(float x, float y) {
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        }

        @Override
        public Point2D getCurrentPoint() {
            return new Point2D.Float();
        }

        @Override
        public void closePath() {
        }

        @Override
        public void endPath() {
        }
    }
}
