package com.ecom.external.harvest.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.external.model.FsFaculty;

class FacultyNameResolverTest {

    private FsFaculty createKanda() {
        FsFaculty f = new FsFaculty();
        f.setFsUserId(24L);
        f.setFirstName("คานดา");
        f.setLastName("รุณณาพงษ์ศา สายแก้ว");
        f.setNameEn("Kanda Runapongsa Saikaew");
        return f;
    }

    private FsFaculty createChakchai() {
        FsFaculty f = new FsFaculty();
        f.setFsUserId(21L);
        f.setFirstName("จักรชัย");
        f.setLastName("โสอินทร์");
        f.setNameEn("Chakchai So-In");
        return f;
    }

    private FsFaculty createNgamnij() {
        FsFaculty f = new FsFaculty();
        f.setFsUserId(84L);
        f.setFirstName("งามนิจ");
        f.setLastName("อาจอินทร์");
        f.setNameEn("Ngamnij Arch-int");
        return f;
    }

    @Test
    @DisplayName("Kanda Runapongsa Saikaew: generate realistic queries")
    void testKandaQueries() {
        FsFaculty f = createKanda();
        List<String> queries = FacultyNameResolver.generateSearchQueries(f);

        System.out.println("Kanda queries: " + queries);
        assertThat(queries).contains("Kanda Saikaew");
        assertThat(queries).contains("Kanda Runapongsa Saikaew");
        assertThat(queries).contains("คานดา สายแก้ว");
    }

    @Test
    @DisplayName("Kanda Runapongsa Saikaew: match all published author variations")
    void testKandaMatches() {
        FsFaculty f = createKanda();

        // Variations that MUST match
        assertThat(FacultyNameResolver.matchesAuthor("Kanda Saikaew", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("Saikaew, Kanda", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("Kanda Runapongsa Saikaew", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("Saikaew, Kanda Runapongsa", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("Kanda Runapongsa", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("Kanda R. Saikaew", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("K. Saikaew", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("Saikaew, K.", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("คานดา สายแก้ว", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("สายแก้ว, คานดา", f)).isTrue();

        // People that MUST NOT match (No false positives)
        assertThat(FacultyNameResolver.matchesAuthor("Somchai Saikaew", f)).isFalse();
        assertThat(FacultyNameResolver.matchesAuthor("Saikaew, Somchai", f)).isFalse();
        assertThat(FacultyNameResolver.matchesAuthor("John Doe", f)).isFalse();
        assertThat(FacultyNameResolver.matchesAuthor("Kanda Smith", f)).isFalse();
    }

    @Test
    @DisplayName("Chakchai So-In: match hyphenated, unhyphenated, and inverted variations")
    void testChakchaiMatches() {
        FsFaculty f = createChakchai();

        assertThat(FacultyNameResolver.matchesAuthor("Chakchai So-In", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("So-In, Chakchai", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("Chakchai Soin", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("Soin, Chakchai", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("C. So-In", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("จักรชัย โสอินทร์", f)).isTrue();

        // False positive prevention
        assertThat(FacultyNameResolver.matchesAuthor("Somchai So-In", f)).isFalse();
        assertThat(FacultyNameResolver.matchesAuthor("Chakchai Smith", f)).isFalse();
    }

    @Test
    @DisplayName("Ngamnij Arch-int: match conjoined surname and initials")
    void testNgamnijMatches() {
        FsFaculty f = createNgamnij();

        assertThat(FacultyNameResolver.matchesAuthor("Ngamnij Arch-int", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("Arch-int, Ngamnij", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("Ngamnij Archint", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("Archint, Ngamnij", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("N. Arch-int", f)).isTrue();
        assertThat(FacultyNameResolver.matchesAuthor("งามนิจ อาจอินทร์", f)).isTrue();

        assertThat(FacultyNameResolver.matchesAuthor("Somchai Arch-int", f)).isFalse();
    }
}
