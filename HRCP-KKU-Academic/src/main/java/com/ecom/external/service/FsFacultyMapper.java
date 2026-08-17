package com.ecom.external.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecom.external.model.FsFaculty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Single definition of how an upstream faculty payload maps onto {@link FsFaculty}.
 *
 * <p>Every mapped field is registered once, in {@link #FIELDS}, and both the
 * writer and the change detector drive off that list. Keeping one list means a
 * newly mapped field cannot end up applied by the sync but invisible to the
 * approval diff — which is exactly the kind of drift that lets an unreviewed
 * edit slip through.
 */
public final class FsFacultyMapper {

    private static final Logger log = LoggerFactory.getLogger(FsFacultyMapper.class);

    /** One mapped attribute: how to read it upstream, and how to read/write it locally. */
    record Field(String label,
            String jsonKey,
            Function<JsonNode, String> read,
            Function<FsFaculty, String> get,
            BiConsumer<FsFaculty, String> set) {
    }

    private static Field plain(String label, String key,
            Function<FsFaculty, String> get, BiConsumer<FsFaculty, String> set) {
        return new Field(label, key, node -> FsApiClient.text(node, key), get, set);
    }

    /**
     * Comparable, admin-visible fields, in the order the review page shows them.
     *
     * <p>Bookkeeping columns ({@code syncedAt}, {@code rawJson}, {@code sourceUpdatedAt})
     * are deliberately absent: they change on every sync and would make every
     * record look edited.
     */
    static final java.util.List<Field> FIELDS = java.util.List.of(
            plain("คำนำหน้า", "prefix", FsFaculty::getPrefix, FsFaculty::setPrefix),
            plain("ชื่อ", "user_fname", FsFaculty::getFirstName, FsFaculty::setFirstName),
            plain("นามสกุล", "user_lname", FsFaculty::getLastName, FsFaculty::setLastName),
            plain("เพศ", "gender", FsFaculty::getGender, FsFaculty::setGender),
            new Field("อีเมล", "email",
                    node -> FsSyncService.normalizeEmail(FsApiClient.text(node, "email")),
                    FsFaculty::getEmail, FsFaculty::setEmail),
            plain("โทรศัพท์", "tel", FsFaculty::getTel, FsFaculty::setTel),
            plain("โทรศัพท์ (จัดรูปแบบ)", "tel_format", FsFaculty::getTelFormat, FsFaculty::setTelFormat),
            plain("ตำแหน่งทางวิชาการ", "position_title", FsFaculty::getPositionTitle, FsFaculty::setPositionTitle),
            plain("ตำแหน่ง (อังกฤษ)", "position_en", FsFaculty::getPositionEn, FsFaculty::setPositionEn),
            plain("คำนำหน้าตำแหน่ง (อังกฤษ)", "prefix_position_en",
                    FsFaculty::getPrefixPositionEn, FsFaculty::setPrefixPositionEn),
            plain("ตำแหน่งบริหาร", "manage_position", FsFaculty::getManagePosition, FsFaculty::setManagePosition),
            plain("ชื่อ-สกุล (อังกฤษ)", "name_en", FsFaculty::getNameEn, FsFaculty::setNameEn),
            plain("คำต่อท้าย (อังกฤษ)", "suffix_en", FsFaculty::getSuffixEn, FsFaculty::setSuffixEn),
            new Field("Scopus Author ID", "scopus_id",
                    node -> FsSyncService.normalizeScopusId(FsApiClient.text(node, "scopus_id")),
                    FsFaculty::getScopusId, FsFaculty::setScopusId),
            plain("Google Scholar ID", "scholar_author_id",
                    FsFaculty::getScholarAuthorId, FsFaculty::setScholarAuthorId),
            plain("ห้องปฏิบัติการ", "lab_name", FsFaculty::getLabName, FsFaculty::setLabName),
            plain("ห้องทำงาน", "room", FsFaculty::getRoom, FsFaculty::setRoom),
            plain("เว็บไซต์", "cp_web_id", FsFaculty::getCpWebId, FsFaculty::setCpWebId),
            plain("บทบาท", "role_name", FsFaculty::getRoleName, FsFaculty::setRoleName),
            plain("สถานะใช้งาน", "is_active", FsFaculty::getIsActive, FsFaculty::setIsActive));

    private FsFacultyMapper() {
    }

    /** Applies every mapped field, plus the bookkeeping columns. */
    static void apply(FsFaculty target, JsonNode row) {
        for (Field f : FIELDS) {
            f.set().accept(target, f.read().apply(row));
        }
        target.setRoleId(FsApiClient.integer(row, "role_id"));
        target.setSourceUpdatedAt(parseOffset(FsApiClient.text(row, "updated_at")));
        target.setSyncedAt(LocalDateTime.now());
        target.setRawJson(row.toString());
    }

    /**
     * Fields whose value differs between the stored record and the upstream row.
     *
     * @return label → {@code {"old":…, "new":…}}, empty when nothing changed
     */
    static Map<String, Map<String, String>> diff(FsFaculty current, JsonNode incoming) {
        Map<String, Map<String, String>> changes = new LinkedHashMap<>();
        for (Field f : FIELDS) {
            String oldValue = f.get().apply(current);
            String newValue = f.read().apply(incoming);
            if (!Objects.equals(oldValue, newValue)) {
                Map<String, String> pair = new LinkedHashMap<>();
                pair.put("old", oldValue);
                pair.put("new", newValue);
                changes.put(f.label(), pair);
            }
        }
        return changes;
    }

    static OffsetDateTime parseOffset(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception e) {
            log.debug("Unparseable timestamp from upstream: {}", value);
            return null;
        }
    }
}
