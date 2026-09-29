package com.ecom.spike;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;

import com.ecom.academic.service.pdf.ThaiShaper;

/**
 * SPIKE S3: Thai text as positioned glyph ids.
 *
 * <p>PDFBox writes text through the font's cmap, one glyph per character, with no
 * Thai shaping, and the JDK's layout engine skips this font's Thai lookups: either
 * way tone marks collide (ข้อ, หน้า). {@link ThaiShaper} picks the glyphs HarfBuzz
 * would; this writes each glyph id at its position. The font is embedded whole
 * (CID = GID) and the run carries /ActualText so copy-paste yields the text.
 */
final class ThaiGlyphRun {

    private final TrueTypeFont font;
    private final ThaiShaper shaper;
    private final float unitsPerEm;

    ThaiGlyphRun(InputStream ttf) throws Exception {
        this.font = new TTFParser().parse(new RandomAccessReadBuffer(ttf));
        this.shaper = new ThaiShaper(font);
        this.unitsPerEm = font.getUnitsPerEm();
    }

    float width(String text, float size) throws Exception {
        return shaper.shape(text).stream().mapToInt(ThaiShaper.Glyph::advance).sum() * size / unitsPerEm;
    }

    /** Content-stream operators drawing {@code text} with its baseline starting at (x, y). */
    String operators(String fontResource, float size, String text, float x, float y) throws Exception {
        List<ThaiShaper.Glyph> glyphs = shaper.shape(text);
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
