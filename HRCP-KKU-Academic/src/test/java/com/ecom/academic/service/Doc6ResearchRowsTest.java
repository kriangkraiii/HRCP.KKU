package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/** เอกสารที่ 6 — ตารางผลงานพิมพ์เฉพาะแถวที่มีผลงาน ไม่ต้องมีแถว 2-5 ว่าง ๆ ตามเทมเพลต */
@DisplayName("เอกสารที่ 6: แถวผลงานในตาราง")
class Doc6ResearchRowsTest {

    private final DocumentGenerationService service = new DocumentGenerationService();
    private final ObjectMapper mapper = new ObjectMapper();

    /** ข้อความของแถวในตารางผลงาน ไม่รวมหัวตารางและแถว "1. ผลงานวิจัย จำนวน ... เรื่อง" */
    private List<String> workRows(Map<String, String> data) throws IOException {
        byte[] docx = service.generateP2PreviewDocx(6, mapper.writeValueAsString(data));
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            List<XWPFTableRow> rows = document.getTables().get(0).getRows();
            return rows.subList(2, rows.size()).stream()
                    .map(r -> r.getTableCells().get(0).getText())
                    .toList();
        }
    }

    @Test
    @DisplayName("ยื่น 1 เรื่อง — เหลือแถวเดียว")
    void singleWorkKeepsOnlyFirstRow() throws IOException {
        List<String> rows = workRows(Map.of("research_count", "1", "des_research1", "งาน A"));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).contains("งาน A");
    }

    @Test
    @DisplayName("ยื่น 3 เรื่อง — ตัดแถว 4-5")
    void threeWorksDropTrailingRows() throws IOException {
        List<String> rows = workRows(Map.of("research_count", "3",
                "des_research1", "งาน A", "des_research2", "งาน B", "des_research3", "งาน C"));

        assertThat(rows).hasSize(3);
        assertThat(rows.get(2)).contains("งาน C");
    }

    @Test
    @DisplayName("ยังไม่กรอกผลงาน — คงแถวแรกไว้ให้เห็นช่อง")
    void noWorkKeepsFirstRow() throws IOException {
        assertThat(workRows(Map.of())).hasSize(1);
    }

    @Test
    @DisplayName("ยื่นเกิน 5 เรื่อง — ยังโคลนแถวต่อท้ายครบ")
    void moreThanFiveWorksStillClone() throws IOException {
        List<String> rows = workRows(Map.of("research_count", "6",
                "des_research1", "งาน A", "des_research2", "งาน B", "des_research3", "งาน C",
                "des_research4", "งาน D", "des_research5", "งาน E", "des_research6", "งาน F"));

        assertThat(rows).hasSize(6);
        assertThat(rows.get(5)).contains("งาน F");
    }
}
