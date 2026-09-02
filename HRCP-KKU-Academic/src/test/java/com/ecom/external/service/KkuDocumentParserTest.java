package com.ecom.external.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ecom.external.model.KkuRegulationDoc;

class KkuDocumentParserTest {

    private KkuDocumentParser parser;

    @BeforeEach
    void setUp() {
        parser = new KkuDocumentParser();
    }

    @Test
    void parse_validHtml_extractsDocumentsAndCategories() {
        String html = """
            <html>
            <body>
                <div class="fusion_builder_column">
                    <div class="fusion-title">
                        <h2>ข้อบังคับมหาวิทยาลัยขอนแก่น</h2>
                    </div>
                    <ul class="fusion-checklist">
                        <li class="fusion-li-item">
                            <div class="fusion-li-item-content">
                                <a href="https://hr2.kku.ac.th/wp-content/uploads/ข้อบังคับฯ-ต.วิชาการของพนักงานมหาวิทยาลัยฯ-1.pdf">
                                    ข้อบังคับมหาวิทยาลัยขอนแก่นว่าด้วยคุณสมบัติ พ.ศ. 2569 🆕🆕
                                </a>
                            </div>
                        </li>
                        <li class="fusion-li-item">
                            <div class="fusion-li-item-content">
                                <a href="https://hr2.kku.ac.th/wp-content/uploads/ข้อบังคับฯ-ต.วิชาการ-พนักงานมหาวิทยาลัย-พ.ศ.-2565.pdf">
                                    ข้อบังคับมหาวิทยาลัยขอนแก่น พ.ศ. 2565
                                </a>
                            </div>
                        </li>
                    </ul>
                </div>
                <div class="fusion_builder_column">
                    <div class="fusion-title">
                        <h4>ประกาศมหาวิทยาลัยขอนแก่น</h4>
                    </div>
                    <ul class="fusion-checklist">
                        <li class="fusion-li-item">
                            <div class="fusion-li-item-content">
                                <a href="https://hr2.kku.ac.th/wp-content/uploads/ประกาศมหาวิทยาลัยขอนแก่น-1669_69-ประเมินการสอ.pdf">
                                    ประกาศมหาวิทยาลัยขอนแก่น (ฉบับที่ 1669/2569) เรื่อง หลักเกณฑ์ประเมินการสอน ( ใหม่ )
                                </a>
                            </div>
                        </li>
                    </ul>
                </div>
            </body>
            </html>
            """;

        List<KkuRegulationDoc> docs = parser.parse(html);

        assertNotNull(docs);
        assertEquals(3, docs.size());

        // First doc
        KkuRegulationDoc doc1 = docs.get(0);
        assertEquals("ข้อบังคับมหาวิทยาลัยขอนแก่น", doc1.getCategory());
        assertTrue(doc1.getTitle().contains("2569"));
        assertTrue(doc1.getIsNew());
        assertEquals("2569", doc1.getPublishedYear());
        assertNotNull(doc1.getFileKey());

        // Second doc
        KkuRegulationDoc doc2 = docs.get(1);
        assertEquals("ข้อบังคับมหาวิทยาลัยขอนแก่น", doc2.getCategory());
        assertFalse(doc2.getIsNew());
        assertEquals("2565", doc2.getPublishedYear());

        // Third doc
        KkuRegulationDoc doc3 = docs.get(2);
        assertEquals("ประกาศมหาวิทยาลัยขอนแก่น", doc3.getCategory());
        assertTrue(doc3.getIsNew());
        assertEquals("2569", doc3.getPublishedYear());
    }

    @Test
    void parse_emptyOrNull_returnsEmptyList() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse("   ").isEmpty());
    }
}
