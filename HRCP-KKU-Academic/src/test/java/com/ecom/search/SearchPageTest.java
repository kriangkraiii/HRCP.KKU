package com.ecom.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.search.dto.SearchResponse;
import com.ecom.support.AbstractFlowTest;

/**
 * The results page: what it shows, and what it must not.
 *
 * <p>{@code EveryPageRendersTest} already proves the template assembles. This
 * covers the decisions the page makes — that filters survive paging, that the
 * scoping the service enforces is what actually reaches the screen, and that a
 * hand-edited URL degrades instead of throwing.
 */
class SearchPageTest extends AbstractFlowTest {

    private UserDtls somchai;
    private UserDtls malee;
    private UserDtls admin;
    private AcademicRequest maleesRequest;

    @BeforeEach
    void seed() {
        data.reset();
        somchai = data.applicant();
        malee = data.otherApplicant();
        admin = data.admin();
        maleesRequest = data.evaluation(malee, RequestStatus.RECEIVED);
    }

    @Test
    @DisplayName("a search renders results for the person who owns them")
    void ownerSeesTheirResults() throws Exception {
        AcademicRequest mine = data.evaluation(somchai, RequestStatus.RECEIVED);

        var result = mvc.perform(get("/search")
                .param("q", mine.getRequestCode())
                .with(user(somchai.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andReturn();

        SearchResponse response = (SearchResponse) result.getModelAndView()
                .getModel().get("result");
        assertThat(response.total()).isPositive();
    }

    @Test
    @DisplayName("the page cannot show what the query would not return")
    void scopingReachesTheScreen() throws Exception {
        var asOtherApplicant = mvc.perform(get("/search")
                .param("q", maleesRequest.getRequestCode())
                .with(user(somchai.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(((SearchResponse) asOtherApplicant.getModelAndView().getModel().get("result")).total())
                .as("หน้าเว็บต้องไม่เปิดช่องที่ service ปิดไว้")
                .isZero();

        var asAdmin = mvc.perform(get("/search")
                .param("q", maleesRequest.getRequestCode())
                .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(((SearchResponse) asAdmin.getModelAndView().getModel().get("result")).total())
                .isPositive();
    }

    /**
     * The bug this guards is the one {@code PublicationAdminPageController}
     * documents: without the filters on the pager link, page two quietly becomes
     * a different, unfiltered search while still looking like page two of the
     * one that was asked for.
     */
    @Test
    @DisplayName("pager links carry every active filter")
    void pagerKeepsTheFilters() throws Exception {
        mvc.perform(get("/search")
                .param("q", "ประเมิน")
                .param("types", "ACADEMIC_REQUEST")
                .param("statuses", "RECEIVED")
                .param("from", "2020-01-01")
                .param("to", "2030-12-31")
                .param("sort", "NEWEST")
                .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterQuery",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("&types=ACADEMIC_REQUEST"),
                                org.hamcrest.Matchers.containsString("&statuses=RECEIVED"),
                                org.hamcrest.Matchers.containsString("&from=2020-01-01"),
                                org.hamcrest.Matchers.containsString("&to=2030-12-31"),
                                org.hamcrest.Matchers.containsString("&sort=NEWEST"),
                                // Thai must be percent-encoded or the link breaks.
                                org.hamcrest.Matchers.containsString("&q=%E0%B8%9B"))));
    }

    @Test
    @DisplayName("a query below the minimum length says so instead of returning everything")
    void tooShortIsExplained() throws Exception {
        mvc.perform(get("/search").param("q", "ศ")
                .with(user(somchai.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tooShort", true))
                .andExpect(model().attribute("minQueryLength", 2));
    }

    @Test
    @DisplayName("no query at all is a normal page, not an error")
    void emptyQueryIsFine() throws Exception {
        mvc.perform(get("/search").with(user(somchai.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tooShort", false));
    }

    @Test
    @DisplayName("a hand-edited sort or page falls back rather than failing")
    void malformedParametersDegrade() throws Exception {
        mvc.perform(get("/search")
                .param("q", "ประเมิน")
                .param("sort", "; DROP TABLE search_document")
                .param("page", "-5")
                .with(user(somchai.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sort", "RELEVANCE"));
    }
}
