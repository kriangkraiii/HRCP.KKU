package com.ecom.search;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * Pins the JSON contract the omnibox reads.
 *
 * <p>{@code static/js/global-search.js} renders seven field names off each
 * result. They are asserted here by name because the renderer fails silently
 * when one goes missing — a dropped {@code badgeClass} paints an unstyled badge
 * and a dropped {@code url} produces a link to nowhere, neither of which throws.
 * Phase 3 replaces the service behind this endpoint, and these names are what it
 * has to keep.
 */
class GlobalSearchApiTest extends AbstractFlowTest {

    private UserDtls applicant;

    @BeforeEach
    void seed() {
        data.reset();
        applicant = data.applicant();
    }

    /**
     * The endpoint is not reachable without signing in.
     *
     * <p>The check is a redirect, not the 401 the controller writes for a null
     * principal. {@code /api/**} matches no prefix rule in {@code SecurityConfig}
     * and falls through to {@code .anyRequest().authenticated()}, so the
     * authentication entry point turns an anonymous call into a bounce to
     * {@code /signin} before the controller is ever entered. The controller's own
     * 401 branch is reached only when a session is authenticated but the user row
     * behind it has gone.
     */
    @Test
    @DisplayName("anonymous callers never reach the search")
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/global-search").param("q", "ตำแหน่ง"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/signin?expired=true"));
    }

    @Test
    @DisplayName("the envelope keeps query, results and total")
    void envelopeShape() throws Exception {
        mvc.perform(get("/api/global-search").param("q", "ตำแหน่ง")
                .with(user(applicant.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("ตำแหน่ง"))
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.total").isNumber());
    }

    @Test
    @DisplayName("every field the renderer reads is present")
    void resultItemKeepsAllSevenFields() throws Exception {
        mvc.perform(get("/api/global-search").param("q", "ตำแหน่ง")
                .with(user(applicant.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].category").exists())
                .andExpect(jsonPath("$.results[0].title").exists())
                .andExpect(jsonPath("$.results[0].subtitle").exists())
                .andExpect(jsonPath("$.results[0].url").exists())
                .andExpect(jsonPath("$.results[0].icon").exists())
                .andExpect(jsonPath("$.results[0].badge").exists())
                .andExpect(jsonPath("$.results[0].badgeClass").exists());
    }

    @Test
    @DisplayName("a query below the minimum length returns nothing, not everything")
    void tooShortReturnsEmpty() throws Exception {
        mvc.perform(get("/api/global-search").param("q", "ศ")
                .with(user(applicant.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    @DisplayName("a missing q is handled")
    void missingQueryIsSafe() throws Exception {
        mvc.perform(get("/api/global-search").with(user(applicant.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isEmpty());
    }
}
