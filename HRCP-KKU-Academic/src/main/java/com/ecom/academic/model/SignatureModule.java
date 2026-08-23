package com.ecom.academic.model;

/**
 * Which request family a signing envelope belongs to.
 *
 * <p>The two flows have separate tables ({@code academic_request} and
 * {@code position_request}) and separate document sets, so an envelope has to
 * record which one its {@code request_id} points into. A plain discriminator
 * rather than two FK columns: the signing machinery is identical for both, and
 * splitting it would double every query for no benefit.
 */
public enum SignatureModule {

    ACADEMIC("ประเมินผลการสอน", "/admin/academic/request/", "/user/academic/request/"),
    POSITION("ขอตำแหน่งทางวิชาการ", "/admin/position/request/", "/user/position/request/");

    private final String thaiLabel;
    private final String adminPathPrefix;
    private final String userPathPrefix;

    SignatureModule(String thaiLabel, String adminPathPrefix, String userPathPrefix) {
        this.thaiLabel = thaiLabel;
        this.adminPathPrefix = adminPathPrefix;
        this.userPathPrefix = userPathPrefix;
    }

    public String getThaiLabel() {
        return thaiLabel;
    }

    /** Where a notification should send an administrator for this request. */
    public String adminLink(Long requestId) {
        return adminPathPrefix + requestId;
    }

    /** Where a notification should send the applicant for this request. */
    public String userLink(Long requestId) {
        return userPathPrefix + requestId;
    }
}
