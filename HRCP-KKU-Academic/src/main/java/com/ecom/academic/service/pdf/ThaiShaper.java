package com.ecom.academic.service.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.fontbox.ttf.TrueTypeFont;

/**
 * Thai shaping for TH Sarabun New without a shaping engine.
 *
 * <p>The JDK's layout skips this font's Thai GSUB lookups (tone marks collide
 * with the consonant), and PDFBox has no Thai shaping at all. HarfBuzz output
 * for this font has no GPOS offsets — every adjustment is a glyph substitution
 * to one of the font's named alternates — so the rules below reproduce it. They
 * were derived from, and are checked against, {@code hb-shape} on a corpus.
 */
public final class ThaiShaper {

    /** A shaped glyph: id and advance, both in font units. */
    public record Glyph(int gid, String name, int advance) {
    }

    private static final String ASCENDER = "ปฝฟ"; // ป ฝ ฟ
    private static final String UPPER_VOWEL = "ัิีึื็ํ"; // ั ิ ี ึ ื ็ ํ
    private static final String TONE = "่้๊๋์๎"; // ่ ้ ๊ ๋ ์ ๎
    private static final String LOWER_VOWEL = "ฺุู"; // ุ ู ฺ
    private static final char SARA_AM = 'ำ', NIKHAHIT = 'ํ', SARA_AA = 'า';
    private static final char LAKKHANGYAO = 'ๅ';

    private final TrueTypeFont font;
    private final Map<String, Integer> byName = new HashMap<>();

    public ThaiShaper(TrueTypeFont font) throws IOException {
        this.font = font;
        for (int g = 0; g < font.getNumberOfGlyphs(); g++) {
            String n = font.getPostScript().getName(g);
            if (n != null) {
                byName.putIfAbsent(n, g);
            }
        }
    }

    private static boolean in(String set, char c) {
        return set.indexOf(c) >= 0;
    }

    /** Glyph name for a code point, as the font's post table spells it. */
    private static String uni(char c) {
        return String.format("uni%04X", (int) c);
    }

    public List<Glyph> shape(String text) throws IOException {
        // 1. SARA AM: decompose to NIKHAHIT + SARA AA, with any tone marks just
        //    before it moved after the NIKHAHIT (น้ำ = น ํ ้ า).
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == SARA_AM) {
                int j = s.length();
                while (j > 0 && in(TONE, s.charAt(j - 1))) {
                    j--;
                }
                String tones = s.substring(j);
                s.setLength(j);
                s.append(NIKHAHIT).append(tones).append(SARA_AA);
            } else {
                s.append(c);
            }
        }

        List<String> names = new ArrayList<>();
        char base = 0;
        int baseIndex = -1;
        boolean upperSeen = false;
        boolean lowerSeen = false;
        // An above-mark typed after a below-vowel is out of order: HarfBuzz's
        // lookups stop matching, so from there on every mark keeps its plain glyph.
        boolean outOfOrder = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String name = uni(c);
            if (in(UPPER_VOWEL, c)) {
                upperSeen = true;
                if (lowerSeen) {
                    outOfOrder = true;
                } else if (in(ASCENDER, base)) {
                    name += ".alt1";
                } else if (base == 'ฬ' && (c >= '\u0E34' && c <= '\u0E37' || c == '\u0E47')) {
                    // ฬ makes room itself for ิ ี ึ ื; ็ also moves left
                    names.set(baseIndex, uni(base) + ".alt1");
                    if (c == '\u0E47') {
                        name += ".alt1";
                    }
                }
            } else if (in(TONE, c)) {
                if (outOfOrder) {
                    // leave it alone
                } else if (base == 0) {
                    if (!upperSeen) {
                        name += ".alt2"; // a bare tone mark sits low
                    }
                } else if (upperSeen) {
                    if (in(ASCENDER, base)) {
                        name += ".alt3";
                    }
                } else {
                    name += in(ASCENDER, base) ? ".alt1" : ".alt2";
                }
            } else if (in(LOWER_VOWEL, c)) {
                lowerSeen = true;
                switch (base) {
                    case 'ญ', 'ฐ' -> names.set(baseIndex, uni(base) + ".alt1"); // ญ ฐ lose the tail
                    case 'ฎ', 'ฏ' -> { // ฎ ฏ: shorter tail, vowel drops below it
                        names.set(baseIndex, uni(base) + ".alt1");
                        name += ".alt1";
                    }
                    case 'ฤ', 'ฦ' -> name += ".alt1"; // ฤ ฦ
                    default -> {
                    }
                }
            } else if ((c == LAKKHANGYAO || c == SARA_AA) && (base == 'ฤ' || base == 'ฦ') && baseIndex == names.size() - 1) {
                names.set(baseIndex, uni(base) + ".liga"); // ฤๅ ฦๅ ฤา ฦา
                continue;
            } else {
                // Anything else starts a new cluster.
                base = (c >= 'ก' && c <= 'ฮ') ? c : 0;
                baseIndex = names.size();
                upperSeen = false;
                lowerSeen = false;
                outOfOrder = false;
            }
            names.add(name);
        }

        List<Glyph> out = new ArrayList<>(names.size());
        int[] gids = new int[names.size()];
        String[] resolved = new String[names.size()];
        for (int k = 0; k < names.size(); k++) {
            String n = names.get(k);
            Integer gid = byName.get(n);
            if (gid == null) {
                // No such alternate in this font: fall back to the plain glyph.
                char c = (char) Integer.parseInt(n.substring(3, 7), 16);
                gid = font.getUnicodeCmapLookup().getGlyphId(c);
                n = uni(c);
            }
            gids[k] = gid;
            resolved[k] = n;
        }
        // Pair kerning from the 'kern' table, between spacing glyphs (marks skipped),
        // added to the left glyph's advance as HarfBuzz does.
        var kern = font.getKerning() != null ? font.getKerning().getHorizontalKerningSubtable() : null;
        for (int k = 0; k < gids.length; k++) {
            int advance = font.getAdvanceWidth(gids[k]);
            if (kern != null && advance > 0) {
                int next = k + 1;
                while (next < gids.length && font.getAdvanceWidth(gids[next]) == 0) {
                    next++;
                }
                if (next < gids.length) {
                    advance += kern.getKerning(gids[k], gids[next]);
                }
            }
            out.add(new Glyph(gids[k], resolved[k], advance));
        }
        return out;
    }
}
