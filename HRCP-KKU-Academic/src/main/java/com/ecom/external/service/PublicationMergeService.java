package com.ecom.external.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.repository.PositionRequestPublicationRepository;
import com.ecom.external.harvest.PublicationHarmonizer;
import com.ecom.external.model.ScopusPublication;
import com.ecom.external.repository.ScopusPublicationRepository;
import com.ecom.search.index.SearchIndexer;
import com.ecom.search.model.SearchEntityType;
import com.ecom.service.AfterCommitRunner;

/**
 * รวมผลงานตีพิมพ์ที่ซ้ำกันอยู่ในฐานข้อมูลแล้ว (docs/PLAN-bughunt-decisions.md ข้อ 2b)
 *
 * <p>ก่อนแก้การกรองซ้ำ ผลงานที่ OpenAlex/Crossref เจอก่อน Scopus จะ index มีสองแถว ตัวเลือกผลงานซ่อนผลงานที่ใช้ไปแล้ว
 * ด้วย id ผู้ยื่นที่ใช้แถวหนึ่งไปแล้วจึงเห็นอีกแถวและยื่นผลงานเดิมซ้ำได้
 *
 * <p>จับคู่ภายในเจ้าของเดียวกันด้วย DOI แล้วด้วย {@code dedup_hash} — ไม่ใช้ความคล้ายของชื่อเรื่อง เพราะรวมผิดแล้วแก้ยาก
 * แถวที่เหลือคือแถวของ Scopus (FS sync อัปเดตต่อด้วย eid จริง) กลุ่มที่มีแถว Scopus มากกว่าหนึ่งไม่รวมให้ —
 * Scopus เองมี DOI ซ้ำได้จริง (เช่น erratum) ต้องให้คนตรวจ
 *
 * <p>แถวที่ถูกรวมไม่ถูกลบ ({@code merged_into_id}) id เดิมใน JSON ของเอกสารยังหาแถวที่เหลือได้
 */
@Service
public class PublicationMergeService {

    private static final String SCOPUS = "SCOPUS";

    private final ScopusPublicationRepository publications;
    private final PositionRequestPublicationRepository links;
    private final SearchIndexer searchIndexer;
    private final AfterCommitRunner afterCommit;

    public PublicationMergeService(ScopusPublicationRepository publications,
            PositionRequestPublicationRepository links, SearchIndexer searchIndexer, AfterCommitRunner afterCommit) {
        this.publications = publications;
        this.links = links;
        this.searchIndexer = searchIndexer;
        this.afterCommit = afterCommit;
    }

    /** ผลงานชิ้นเดียวกันหนึ่งกลุ่ม: แถวที่จะเหลือ และแถวที่จะถูกรวมเข้าไป */
    public record Group(Long fsUserId, ScopusPublication survivor, List<ScopusPublication> losers) {
    }

    /**
     * @param mergeable กลุ่มที่รวมได้
     * @param skipped   กลุ่มที่มีแถว Scopus มากกว่าหนึ่ง — ต้องให้คนตรวจ
     */
    public record Review(List<Group> mergeable, List<List<ScopusPublication>> skipped) {

        public int rowsToHide() {
            return mergeable.stream().mapToInt(g -> g.losers().size()).sum();
        }
    }

    private record Key(long id, long owner, String doi, String hash) {
    }

    /** ผลงานที่ซ้ำกันอยู่ตอนนี้ — อ่านอย่างเดียว ใช้แสดงให้แอดมินตรวจก่อนรวม */
    @Transactional(readOnly = true)
    public Review findDuplicates() {
        List<Key> keys = publications.findDedupKeys().stream()
                .map(row -> new Key(((Number) row[0]).longValue(), ((Number) row[1]).longValue(),
                        PublicationHarmonizer.normalizeDoi((String) row[2]), (String) row[3]))
                .toList();

        // แถวที่มี DOI หรือ hash ตรงกันอยู่กลุ่มเดียวกัน (ต่อกันเป็นทอดได้: A–B ด้วย DOI, B–C ด้วย hash)
        Map<Long, Long> parent = new HashMap<>();
        Map<String, Long> firstWithKey = new HashMap<>();
        for (Key k : keys) {
            parent.putIfAbsent(k.id(), k.id());
            List<String> matchKeys = new ArrayList<>(2);
            if (k.doi() != null && !k.doi().isBlank()) {
                matchKeys.add(k.owner() + "|doi|" + k.doi());
            }
            if (k.hash() != null && !k.hash().isBlank()) {
                matchKeys.add(k.owner() + "|hash|" + k.hash());
            }
            for (String matchKey : matchKeys) {
                Long other = firstWithKey.putIfAbsent(matchKey, k.id());
                if (other != null) {
                    union(parent, k.id(), other);
                }
            }
        }

        Map<Long, List<Long>> components = new LinkedHashMap<>();
        for (Long id : parent.keySet()) {
            components.computeIfAbsent(find(parent, id), root -> new ArrayList<>()).add(id);
        }

        List<Group> mergeable = new ArrayList<>();
        List<List<ScopusPublication>> skipped = new ArrayList<>();
        for (List<Long> ids : components.values()) {
            if (ids.size() < 2) {
                continue;
            }
            List<ScopusPublication> rows = new ArrayList<>(publications.findAllById(ids));
            rows.sort(Comparator.comparing(ScopusPublication::getId));
            List<ScopusPublication> scopusRows = rows.stream().filter(p -> SCOPUS.equals(p.getDataSource())).toList();
            if (scopusRows.size() > 1) {
                skipped.add(rows);
                continue;
            }
            ScopusPublication survivor = scopusRows.isEmpty() ? rows.get(0) : scopusRows.get(0);
            List<ScopusPublication> losers = rows.stream().filter(p -> !p.getId().equals(survivor.getId())).toList();
            mergeable.add(new Group(survivor.getFsUserId(), survivor, losers));
        }
        mergeable.sort(Comparator.comparing(Group::fsUserId).thenComparing(g -> g.survivor().getId()));
        return new Review(mergeable, skipped);
    }

    /**
     * รวมทุกกลุ่มที่รวมได้ — หาใหม่ตอนนี้ ไม่เชื่อรายการที่เคยแสดง (ข้อมูลอาจเปลี่ยนระหว่างนั้น)
     *
     * @return จำนวนแถวที่ถูกซ่อน
     */
    @Transactional
    public int mergeAll() {
        List<Long> hidden = new ArrayList<>();
        for (Group group : findDuplicates().mergeable()) {
            Long survivor = group.survivor().getId();
            for (ScopusPublication loser : group.losers()) {
                links.dropLinksAlreadyOn(loser.getId(), survivor);
                links.moveLinks(loser.getId(), survivor);
                publications.repointMerged(loser.getId(), survivor);
                publications.markMerged(loser.getId(), survivor);
                hidden.add(loser.getId());
            }
        }
        // ดัชนีค้นหา: native update ไม่ผ่าน entity listener — สั่งลบเองหลัง commit (โหลดไม่ได้ = ลบออกจากดัชนี)
        afterCommit.run(() -> hidden.forEach(id -> searchIndexer.reindex(SearchEntityType.PUBLICATION, id)));
        return hidden.size();
    }

    private static Long find(Map<Long, Long> parent, Long id) {
        Long root = id;
        while (!parent.get(root).equals(root)) {
            root = parent.get(root);
        }
        // ย่อทางให้สั้นลงสำหรับรอบหน้า
        Long node = id;
        while (!parent.get(node).equals(root)) {
            Long next = parent.get(node);
            parent.put(node, root);
            node = next;
        }
        return root;
    }

    private static void union(Map<Long, Long> parent, Long a, Long b) {
        Long rootA = find(parent, a);
        Long rootB = find(parent, b);
        if (!rootA.equals(rootB)) {
            parent.put(rootA, rootB);
        }
    }
}
