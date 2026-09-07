package com.ecom.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.search.service.SearchQueryNormalizer;
import com.ecom.support.AbstractFlowTest;

/**
 * Locks the bug that made everyone the directory provisioned unfindable.
 *
 * <p>{@code searchUsers} used to match {@code u.name} — the legacy combined
 * column. {@code SsoUserProvisioner} and {@code UserDirectorySync} write
 * {@code firstName}/{@code lastName} and never touch it, so the search matched
 * nothing for any account that arrived through SSO, which is every account the
 * campus directory creates. {@link UserDtls#getName()} hides the difference in
 * Java by deriving the value, but JPQL reads the column, not the getter, so the
 * bug was invisible everywhere except the search box.
 *
 * <p>{@code TestDataFactory.user(...)} happens to build users in exactly that
 * shape — first and last name set, {@code name} left null — so these fixtures
 * are the real thing rather than an imitation of it.
 */
class UserSearchByNameTest extends AbstractFlowTest {

    @Autowired
    private UserRepository userRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private UserDtls somchai;

    @BeforeEach
    void seedPeople() {
        data.reset();
        somchai = data.applicant();      // สมชาย ใจดี
        data.otherApplicant();           // มาลี ตั้งใจ
    }

    private List<UserDtls> search(String typed) {
        return userRepository.searchUsers(SearchQueryNormalizer.likePattern(typed));
    }

    @Test
    @DisplayName("an SSO-provisioned user has no legacy name column")
    void fixtureIsShapedLikeAnSsoUser() {
        UserDtls raw = userRepository.findById(somchai.getId()).orElseThrow();
        assertThat(raw.getFirstName()).isEqualTo("สมชาย");
        assertThat(raw.getLastName()).isEqualTo("ใจดี");

        // Read the column, not the getter. UserDtls.getName() derives a value
        // from firstName/lastName when the column is null, so any assertion
        // going through the getter — including AssertJ's extracting("name"),
        // which prefers the property — reports a name that is not in the
        // database. That derived value is exactly what hid this bug: Java saw a
        // name everywhere, and only JPQL, which reads columns, saw the null.
        Object storedName = entityManager
                .createNativeQuery("SELECT name FROM user_dtls WHERE id = :id")
                .setParameter("id", somchai.getId())
                .getSingleResult();

        assertThat(storedName)
                .as("ถ้าคอลัมน์นี้ถูกเซ็ต แปลว่า fixture ไม่เหมือนผู้ใช้ที่มาจาก SSO อีกต่อไป "
                        + "และเทสต์ทั้งคลาสนี้จะผ่านด้วยเหตุผลที่ผิด")
                .isNull();
    }

    @Test
    @DisplayName("found by first name")
    void findsByFirstName() {
        assertThat(search("สมชาย")).extracting(UserDtls::getEmail)
                .containsExactly(somchai.getEmail());
    }

    @Test
    @DisplayName("found by last name")
    void findsByLastName() {
        assertThat(search("ใจดี")).extracting(UserDtls::getEmail)
                .containsExactly(somchai.getEmail());
    }

    @Test
    @DisplayName("found by full name — matches neither column on its own")
    void findsByFullName() {
        assertThat(search("สมชาย ใจดี")).extracting(UserDtls::getEmail)
                .containsExactly(somchai.getEmail());
    }

    @Test
    @DisplayName("found by email")
    void findsByEmail() {
        assertThat(search(somchai.getEmail())).extracting(UserDtls::getEmail)
                .containsExactly(somchai.getEmail());
    }

    @Test
    @DisplayName("does not return other people")
    void doesNotOverMatch() {
        assertThat(search("มาลี")).extracting(UserDtls::getEmail)
                .containsExactly(data.otherApplicant().getEmail());
    }

    @Test
    @DisplayName("a LIKE metacharacter is data, not a wildcard")
    void percentIsEscaped() {
        assertThat(search("%%"))
                .as("ถ้า % ไม่ถูก escape คำค้นนี้จะคืนผู้ใช้ทุกคน")
                .isEmpty();
    }
}
