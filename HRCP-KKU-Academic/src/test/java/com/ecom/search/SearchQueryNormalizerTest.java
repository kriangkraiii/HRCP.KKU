package com.ecom.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.search.service.SearchQueryNormalizer;

/** Pure unit cover for the two rules every search path depends on. */
class SearchQueryNormalizerTest {

    @Test
    @DisplayName("trims, lower-cases and collapses whitespace")
    void normalizesShape() {
        assertThat(SearchQueryNormalizer.normalize("  Machine   Learning ")).isEqualTo("machine learning");
        assertThat(SearchQueryNormalizer.normalize("ประเมิน\tการสอน")).isEqualTo("ประเมิน การสอน");
    }

    @Test
    @DisplayName("anything shorter than the minimum is not a query")
    void rejectsTooShort() {
        assertThat(SearchQueryNormalizer.normalize(null)).isNull();
        assertThat(SearchQueryNormalizer.normalize("")).isNull();
        assertThat(SearchQueryNormalizer.normalize("   ")).isNull();
        assertThat(SearchQueryNormalizer.normalize("ศ")).isNull();
        assertThat(SearchQueryNormalizer.likePattern("ศ")).isNull();
        assertThat(SearchQueryNormalizer.prefixPattern("ศ")).isNull();
    }

    @Test
    @DisplayName("two Thai characters are a real word and are allowed")
    void acceptsTwoCharacters() {
        assertThat(SearchQueryNormalizer.normalize("ผศ")).isEqualTo("ผศ");
        assertThat(SearchQueryNormalizer.likePattern("มข")).isEqualTo("%มข%");
    }

    @Test
    @DisplayName("LIKE metacharacters are escaped, so a search box is not a select-all")
    void escapesLikeMetacharacters() {
        assertThat(SearchQueryNormalizer.likePattern("100%")).isEqualTo("%100\\%%");
        assertThat(SearchQueryNormalizer.likePattern("a_b")).isEqualTo("%a\\_b%");
        assertThat(SearchQueryNormalizer.likePattern("c\\d")).isEqualTo("%c\\\\d%");
    }

    @Test
    @DisplayName("prefix patterns anchor at the start")
    void buildsPrefixPattern() {
        assertThat(SearchQueryNormalizer.prefixPattern("KKU-ACAD")).isEqualTo("kku-acad%");
    }
}
