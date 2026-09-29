package com.ecom.spike;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.ecom.academic.service.pdf.ThaiShaper;

import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * SPIKE S3: {@link ThaiShaper} against HarfBuzz. Needs {@code -Dhb.truth=<file>}
 * with lines "text TAB hb-shape --no-clusters --font-size=1000 output".
 */
@EnabledIfSystemProperty(named = "hb.truth", matches = ".+")
class ThaiShaperVsHarfBuzzSpikeTest {

    @Test
    void matchesHarfBuzz() throws Exception {
        TrueTypeFont font;
        try (var in = getClass().getClassLoader().getResourceAsStream("fonts/th-sarabun/THSarabunNew.ttf")) {
            font = new TTFParser().parse(new RandomAccessReadBuffer(in));
        }
        ThaiShaper shaper = new ThaiShaper(font);
        String[] post = new String[font.getNumberOfGlyphs()];
        for (int g = 0; g < post.length; g++) {
            post[g] = font.getPostScript().getName(g);
        }
        List<String> mismatches = new ArrayList<>();
        int total = 0;
        for (String line : Files.readAllLines(Path.of(System.getProperty("hb.truth")), StandardCharsets.UTF_8)) {
            int tab = line.indexOf('\t');
            if (tab < 0) {
                continue;
            }
            String text = line.substring(0, tab);
            String expected = line.substring(tab + 1).trim();
            String actual = shaper.shape(text).stream()
                    .map(g -> post[g.gid()] + "+" + g.advance())
                    .collect(Collectors.joining("|", "[", "]"));
            total++;
            if (!actual.equals(expected)) {
                mismatches.add(text + "\n   hb: " + expected + "\n   us: " + actual);
            }
        }
        System.out.println("ThaiShaper vs HarfBuzz: " + (total - mismatches.size()) + "/" + total + " identical");
        Files.createDirectories(Path.of("target", "spike"));
        Files.write(Path.of("target", "spike", "shaper-mismatch.txt"), mismatches, StandardCharsets.UTF_8);
        assertThat(mismatches).isEmpty();
    }
}
