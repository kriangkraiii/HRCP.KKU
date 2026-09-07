package com.ecom.search.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ecom.search.model.SearchDocument;
import com.ecom.search.model.SearchEntityType;
import com.ecom.search.model.SearchVisibility;

/**
 * The application's own menus, as searchable documents.
 *
 * <p>Replaces two hand-written matcher methods in {@code GlobalSearchService}
 * that tested {@code kw.contains(term)} against inline keyword lists. That shape
 * is what produced the {@code "ศ"} bug: a single Thai character in one of the
 * lists, and because Thai is written without spaces it was a substring of
 * ประกาศ, การศึกษา and ศาสตราจารย์ — so the position menu answered nearly every
 * query.
 *
 * <p>As index rows the same mistake cannot be made. Keywords are matched by the
 * ranked query like any other document, which enforces the minimum query length
 * and scores a menu against real records instead of always placing it first.
 * A bare character in this table would simply never be reached.
 *
 * <p>Ids are fixed and assigned by position, so re-seeding updates rows rather
 * than accumulating duplicates.
 */
@Component
public class NavigationCatalog {

    /**
     * @param title      what the result shows
     * @param subtitle   the one-line description under it
     * @param keywords   the words that should find it — abbreviations written
     *                   with their full stops, and the spelled-out forms too
     * @param url        where it goes
     * @param icon       Font Awesome classes
     * @param adminOnly  admin menus are indexed as {@code ADMIN}, the rest as
     *                   {@code PUBLIC}: every signed-in user has these pages
     */
    private record Entry(String title, String subtitle, String keywords,
            String url, String icon, boolean adminOnly) {
    }

    private static final List<Entry> ENTRIES = List.of(
            // ---------- applicant ----------
            new Entry("แดชบอร์ดงานวิชาการ", "ดูภาพรวมและสถานะคำร้องของฉัน",
                    "แดชบอร์ด หน้าหลัก dashboard home ภาพรวม",
                    "/user/academic/dashboard", "fas fa-tachometer-alt text-primary", false),
            new Entry("ประวัติการยื่นคำร้อง", "ตรวจสอบรายการคำร้องทั้งหมดที่เคยยื่น",
                    "ประวัติ คำร้อง history รายการ ย้อนหลัง",
                    "/user/academic/history", "fas fa-history text-primary", false),
            new Entry("ยื่นคำร้องขอรับการประเมินการสอน", "แบบฟอร์มยื่นขอประเมินผลการสอนใหม่",
                    "ยื่นคำร้อง ขอประเมิน ประเมินการสอน การสอน new request แบบฟอร์ม",
                    "/user/academic/new-request", "fas fa-clipboard-check text-success", false),
            new Entry("ยื่นขอกำหนดตำแหน่งทางวิชาการ", "ยื่นขอ ผศ. / รศ. / ศ. (Phase 2)",
                    // The abbreviations carry their full stops and the words are
                    // spelled out. Nothing here is short enough to match by
                    // accident, which is the whole point of the rewrite.
                    "ขอตำแหน่ง ตำแหน่ง กำหนดตำแหน่ง position ผศ. รศ. ศ. "
                            + "ผู้ช่วยศาสตราจารย์ รองศาสตราจารย์ ศาสตราจารย์",
                    "/user/position/dashboard", "fas fa-university text-info", false),
            new Entry("คลังเอกสาร / ข้อบังคับ", "ดาวน์โหลดแบบฟอร์มและข้อบังคับมหาวิทยาลัย",
                    "เอกสาร ข้อบังคับ แบบฟอร์ม ประกาศ ระเบียบ document doc",
                    "/user/academic/documents", "fas fa-book-open text-primary", false),
            new Entry("คู่มือการใช้งานระบบ", "คำแนะนำขั้นตอนการยื่นคำร้องและการใช้งาน",
                    "คู่มือ guide วิธีใช้ ช่วยเหลือ help",
                    "/user/academic/guide", "fas fa-book text-info", false),
            new Entry("ศูนย์การแจ้งเตือน", "ดูรายการแจ้งเตือนและสถานะคำร้องทั้งหมด",
                    "แจ้งเตือน notification กล่องข้อความ เตือน",
                    "/user/notifications", "fas fa-bell text-warning", false),
            new Entry("การตั้งค่าบัญชีและโปรไฟล์", "ปรับแต่งข้อมูลส่วนตัวและการแจ้งเตือน",
                    "ตั้งค่า setting โปรไฟล์ profile รหัสผ่าน password",
                    "/user/academic/settings", "fas fa-cog text-secondary", false),

            // ---------- admin ----------
            new Entry("คำร้องประเมินผลการสอน (แอดมิน)", "จัดการและตรวจสอบคำร้องประเมินผลการสอน",
                    "คำร้องประเมิน ประเมินการสอน evaluation แดชบอร์ด dashboard จัดการคำร้อง",
                    "/admin/academic/requests?type=evaluation", "fas fa-clipboard-check text-primary", true),
            new Entry("คำร้องขอตำแหน่งทางวิชาการ (แอดมิน)", "จัดการคำร้องขอกำหนดตำแหน่ง (Phase 2)",
                    "คำร้องตำแหน่ง กำหนดตำแหน่ง position ผศ. รศ. ศ. ศาสตราจารย์",
                    "/admin/academic/requests?type=position", "fas fa-university text-info", true),
            new Entry("จัดการบุคลากร / กรรมการ", "รายชื่อบุคลากรและคณะกรรมการประเมิน",
                    "บุคลากร กรรมการ staff อาจารย์ คณะกรรมการ",
                    "/admin/academic/staff", "fas fa-users-cog text-success", true),
            new Entry("จัดการผู้ใช้งานทั่วไป", "ดูและจัดการบัญชีผู้ยื่นคำร้อง",
                    "ผู้ใช้ ผู้ใช้งาน user บัญชี account",
                    "/admin/users?type=1", "fas fa-users text-secondary", true),
            new Entry("จัดการผู้ดูแลระบบ", "ดูรายชื่อผู้ดูแลระบบทั้งหมด",
                    "ผู้ดูแล admin แอดมิน สิทธิ์",
                    "/admin/users?type=2", "fas fa-user-shield text-danger", true),
            new Entry("เพิ่มผู้ใช้ / แอดมินใหม่", "สร้างบัญชีผู้ใช้งานใหม่ในระบบ",
                    "เพิ่มผู้ใช้ เพิ่มแอดมิน add user สมัคร สร้างบัญชี",
                    "/admin/add-admin", "fas fa-user-plus text-success", true),
            new Entry("ประวัติกิจกรรมระบบ", "ตรวจสอบประวัติการเข้าใช้งานและการแก้ไข",
                    "ประวัติ กิจกรรม log activity audit",
                    "/admin/activity-logs", "fas fa-history text-secondary", true),
            new Entry("จัดการไฟล์ระบบ", "พื้นที่จัดเก็บไฟล์กลาง 10 GB",
                    "ไฟล์ จัดการไฟล์ file storage คลังไฟล์",
                    "/admin/file-manager", "fas fa-folder-open text-warning", true),
            new Entry("คู่มือการใช้งานสำหรับแอดมิน", "ขั้นตอนการจัดการคำร้องและฟังก์ชันแอดมิน",
                    "คู่มือ guide ช่วยเหลือ help แอดมิน",
                    "/admin/academic/guide", "fas fa-book text-info", true),
            new Entry("การตั้งค่าระบบ", "ตั้งค่าระบบงานวิชาการและอีเมล",
                    "ตั้งค่า setting ระบบ อีเมล email",
                    "/admin/academic/settings", "fas fa-cog text-secondary", true),
            new Entry("สถานะดัชนีการค้นหา", "ตรวจสอบความครบถ้วนของ index และสั่งสร้างใหม่",
                    "index ดัชนี ค้นหา search reindex สถานะการค้นหา",
                    "/admin/search/status", "fas fa-magnifying-glass-chart text-secondary", true));

    /** The menu entries as index documents, ready to be upserted. */
    public List<SearchDocument> documents() {
        List<SearchDocument> documents = new java.util.ArrayList<>(ENTRIES.size());
        for (int i = 0; i < ENTRIES.size(); i++) {
            documents.add(toDocument(ENTRIES.get(i), (long) (i + 1)));
        }
        return documents;
    }

    private SearchDocument toDocument(Entry entry, Long id) {
        SearchDocument d = new SearchDocument();
        d.setEntityType(SearchEntityType.NAVIGATION);
        d.setEntityId(id);
        d.setDocPart("MAIN");
        d.setTitle(entry.title());
        d.setSubtitle(entry.subtitle());
        d.setKeywords(entry.keywords());
        d.setCategory(entry.adminOnly() ? "เมนูแอดมิน" : "เมนูระบบ");
        d.setIcon(entry.icon());
        d.setUrl(entry.url());
        d.setAdminUrl(entry.url());
        d.setBadge(entry.adminOnly() ? "Admin" : "เมนู");
        d.setBadgeClass(entry.adminOnly() ? "bg-primary" : "bg-light text-dark border");
        d.setVisibility(entry.adminOnly() ? SearchVisibility.ADMIN : SearchVisibility.PUBLIC);
        d.setWeight(SearchEntityType.NAVIGATION.getDefaultWeight());
        d.setIndexedAt(LocalDateTime.now());
        return d;
    }
}
