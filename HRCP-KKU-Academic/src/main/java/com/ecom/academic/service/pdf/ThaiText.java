package com.ecom.academic.service.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;

/**
 * Thai text as PDF content-stream operators, glyph by glyph.
 *
 * <p>PDFBox writes text through the font's cmap with no Thai shaping, and the
 * JDK's layout skips TH Sarabun New's Thai lookups: either way tone marks
 * collide (ข้อ, หน้า). {@link ThaiShaper} picks the glyphs HarfBuzz would; this
 * writes each glyph id at its position. The font must be embedded whole
 * (CID = GID). Every run carries /ActualText so copy-paste and search return
 * the text, not glyph ids.
 */
public final class ThaiText {

    public static final String FONT_RESOURCE = "fonts/th-sarabun/THSarabunNew.ttf";

    private final ThaiShaper shaper;
    private final float unitsPerEm;

    private ThaiText(TrueTypeFont font) throws IOException {
        this.shaper = new ThaiShaper(font);
        this.unitsPerEm = font.getUnitsPerEm();
    }

    /** TH Sarabun New from the classpath. */
    public static ThaiText sarabun() {
        try (InputStream in = ThaiText.class.getClassLoader().getResourceAsStream(FONT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Font not on classpath: " + FONT_RESOURCE);
            }
            return new ThaiText(new TTFParser().parse(new RandomAccessReadBuffer(in)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public float width(String text, float size) {
        try {
            return shaper.shape(text).stream().mapToInt(ThaiShaper.Glyph::advance).sum() * size / unitsPerEm;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The largest size, from {@code preferred} down to {@code minimum} in half
     * points, at which {@code text} fits {@code maxWidth}; or -1 if none does.
     */
    public float fittingSize(String text, float preferred, float minimum, float maxWidth) {
        for (float size = preferred; size >= minimum; size -= 0.5f) {
            if (width(text, size) <= maxWidth) {
                return size;
            }
        }
        return -1;
    }

    /** Operators drawing {@code text} with its baseline starting at (x, y). */
    public String operators(String fontResource, float size, String text, float x, float y) {
        List<ThaiShaper.Glyph> glyphs;
        try {
            glyphs = shaper.shape(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("/Span <</ActualText <FEFF")
                .append(HexFormat.of().withUpperCase().formatHex(text.getBytes(StandardCharsets.UTF_16BE)))
                .append(">>> BDC\nBT\n/").append(fontResource).append(' ').append(fmt(size)).append(" Tf\n");
        float pen = x;
        for (ThaiShaper.Glyph g : glyphs) {
            sb.append("1 0 0 1 ").append(fmt(pen)).append(' ').append(fmt(y)).append(" Tm <")
                    .append(String.format("%04X", g.gid())).append("> Tj\n");
            pen += g.advance() * size / unitsPerEm;
        }
        sb.append("ET\nEMC\n");
        return sb.toString();
    }

    private static String fmt(float f) {
        return String.format(Locale.ROOT, "%.3f", f);
    }
}
