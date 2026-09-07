package com.ecom.search.model;

/**
 * Who may see an indexed document.
 *
 * <p>Four values so that one WHERE clause over {@code visibility} and
 * {@code owner_user_id} is the entire authorisation check:
 *
 * <pre>
 *   visibility = 'PUBLIC'
 *   OR (owner_user_id = :userId AND visibility IN ('OWNER', 'OWNER_OR_ADMIN'))
 *   OR (:isAdmin = 1            AND visibility IN ('ADMIN', 'OWNER_OR_ADMIN'))
 * </pre>
 *
 * <p>Keeping the check in the same statement as the matching is the point. The
 * old search filtered per repository, so every new source was a fresh chance to
 * forget the scoping; here a document that names no owner and no audience
 * simply cannot be returned to anyone.
 */
public enum SearchVisibility {

    /** Anyone signed in: regulations, help pages, applicant-facing menus. */
    PUBLIC,

    /** Only {@code owner_user_id}. Notifications — an admin has their own. */
    OWNER,

    /** The owner, and any admin. Requests, their documents and attachments. */
    OWNER_OR_ADMIN,

    /** Admins only: staff records, system users, admin files, admin menus. */
    ADMIN
}
