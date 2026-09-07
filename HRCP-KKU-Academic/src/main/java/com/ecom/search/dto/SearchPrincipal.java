package com.ecom.search.dto;

import com.ecom.model.UserDtls;

/**
 * Who is searching — the only thing that decides what they may see.
 *
 * <p>Built solely from an authenticated {@link UserDtls}. That is the same
 * discipline {@code ScopusPublicationRepository} enforces by making every finder
 * take an owner id: there must be no method a controller can call that widens
 * the scope by passing different arguments. Here the scope is not an argument
 * the caller chooses at all.
 *
 * @param userId the searcher's own id, used to match {@code owner_user_id}
 * @param admin  whether they hold {@code ROLE_ADMIN}
 */
public record SearchPrincipal(Integer userId, boolean admin) {

    public static SearchPrincipal of(UserDtls user) {
        if (user == null) {
            throw new IllegalArgumentException("การค้นหาต้องมีผู้ใช้ที่ยืนยันตัวตนแล้วเสมอ");
        }
        return new SearchPrincipal(user.getId(), "ROLE_ADMIN".equals(user.getRole()));
    }

    /** Bound as an integer: H2 and PostgreSQL disagree about booleans in CASE. */
    public int adminFlag() {
        return admin ? 1 : 0;
    }
}
