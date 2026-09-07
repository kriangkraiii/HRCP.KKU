package com.ecom.search.model;

/**
 * What kind of thing a search document stands for, and how a hit on it is
 * presented and linked.
 *
 * <p>Every routing decision the search makes lives here. The old service spread
 * the same information across a dozen inline {@code new SearchResultItem(...)}
 * calls, so a moved page meant hunting for every literal that pointed at it.
 *
 * <p><b>Changing a URL template here does not fix rows already written.</b> The
 * resolved link is stored on each row at index time, so yesterday's rows keep
 * pointing at the old path until they are rewritten. After changing a template,
 * run {@code POST /admin/search/reindex} — or accept that the nightly reconcile
 * fixes it by morning.
 *
 * <p>{@code weight} multiplies the relevance score, so the relative importance
 * of a whole category is tuned here rather than in SQL. Notifications sit below
 * 1.0 because that table is by far the largest and noisiest; without it a search
 * for a person's name returns twenty notifications mentioning them before the
 * request they are actually looking for.
 */
public enum SearchEntityType {

    ACADEMIC_REQUEST("คำร้องขอประเมินผลการสอน", "fas fa-clipboard-check text-primary", 1.4f,
            "/user/academic/dashboard", "/admin/academic/requests?type=evaluation"),

    POSITION_REQUEST("คำร้องขอตำแหน่งทางวิชาการ", "fas fa-university text-info", 1.4f,
            "/user/position/request/{id}", "/admin/academic/requests?type=position"),

    ACADEMIC_DOCUMENT("เอกสารประกอบคำร้องประเมิน", "fas fa-file-lines text-primary", 1.0f,
            "/user/academic/dashboard", "/admin/academic/requests/{parentId}"),

    POSITION_DOCUMENT("เอกสารประกอบคำร้องตำแหน่ง", "fas fa-file-lines text-info", 1.0f,
            "/user/position/request/{parentId}", "/admin/academic/requests?type=position"),

    ACADEMIC_ATTACHMENT("ไฟล์แนบคำร้องประเมิน", "fas fa-paperclip text-secondary", 0.9f,
            "/user/academic/dashboard", "/admin/academic/requests/{parentId}"),

    POSITION_ATTACHMENT("ไฟล์แนบคำร้องตำแหน่ง", "fas fa-paperclip text-secondary", 0.9f,
            "/user/position/request/{parentId}", "/admin/academic/requests?type=position"),

    STATUS_HISTORY("ประวัติสถานะคำร้อง", "fas fa-clock-rotate-left text-secondary", 0.7f,
            "/user/academic/history", "/admin/academic/requests"),

    COMMITTEE_MEMBER("คณะกรรมการ / ผู้ทรงคุณวุฒิ", "fas fa-user-graduate text-success", 1.2f,
            null, "/admin/academic/committee"),

    STAFF_MEMBER("จัดการบุคลากร", "fas fa-user-tie text-success", 1.2f,
            null, "/admin/academic/staff"),

    SYSTEM_USER("ผู้ใช้งานระบบ", "fas fa-users-cog text-secondary", 1.1f,
            null, "/admin/users?type=1"),

    NOTIFICATION("การแจ้งเตือน", "fas fa-bell text-warning", 0.6f,
            "/notifications/open/{id}", "/notifications/open/{id}"),

    ADMIN_FILE("ไฟล์ระบบ", "fas fa-folder-open text-warning", 0.8f,
            null, "/admin/file-manager"),

    PUBLICATION("ผลงานตีพิมพ์", "fas fa-book text-primary", 1.0f,
            "/user/position/dashboard", "/admin/publications"),

    REGULATION_DOC("คลังเอกสาร / ข้อบังคับ", "fas fa-book-open text-primary", 1.0f,
            "/user/academic/documents", "/user/academic/documents"),

    SIGNATURE_REQUEST("เอกสารลงนาม", "fas fa-file-signature text-info", 1.0f,
            "/user/esign/inbox", "/admin/esign/inbox"),

    NAVIGATION("เมนูระบบ", "fas fa-compass text-secondary", 1.3f,
            null, null);

    private final String category;
    private final String icon;
    private final float defaultWeight;
    private final String userUrlTemplate;
    private final String adminUrlTemplate;

    SearchEntityType(String category, String icon, float defaultWeight,
            String userUrlTemplate, String adminUrlTemplate) {
        this.category = category;
        this.icon = icon;
        this.defaultWeight = defaultWeight;
        this.userUrlTemplate = userUrlTemplate;
        this.adminUrlTemplate = adminUrlTemplate;
    }

    /** Thai label shown as the group heading in the result list. */
    public String getCategory() {
        return category;
    }

    public String getIcon() {
        return icon;
    }

    public float getDefaultWeight() {
        return defaultWeight;
    }

    /**
     * Where an applicant should land, or null when this type is admin-only.
     *
     * @param id       the entity's own id
     * @param parentId the owning request's id, for documents and attachments
     */
    public String userUrl(Long id, Long parentId) {
        return fill(userUrlTemplate, id, parentId);
    }

    /** Where an admin should land, or null when there is no admin view. */
    public String adminUrl(Long id, Long parentId) {
        return fill(adminUrlTemplate, id, parentId);
    }

    private static String fill(String template, Long id, Long parentId) {
        if (template == null) {
            return null;
        }
        String filled = template;
        if (filled.contains("{id}")) {
            // A template that needs an id it was not given would render
            // "/user/position/request/null" — a link straight to an error page.
            if (id == null) {
                return null;
            }
            filled = filled.replace("{id}", id.toString());
        }
        if (filled.contains("{parentId}")) {
            if (parentId == null) {
                return null;
            }
            filled = filled.replace("{parentId}", parentId.toString());
        }
        return filled;
    }
}
