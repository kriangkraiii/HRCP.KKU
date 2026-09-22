package com.ecom.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.AcademicRank;

@DisplayName("ตัวเลือกคำนำหน้าชื่อ")
class NameTitleOptionTest {

    /**
     * บักเดิมคือหน้าโปรไฟล์แอดมินมีแค่ 8 ตัวเลือก (ขาดแบบไม่มี ดร.) แอดมินที่เป็น "รศ."
     * เปิดหน้าตัวเองแล้วกดบันทึก คำนำหน้าหายเพราะไม่มี option ไหนตรงให้ browser เลือก
     * เทสต์นี้ล็อกไว้ว่าทุกค่าที่ระบบผลิตได้เองต้องมีที่ยืนใน list
     */
    @Test
    @DisplayName("ทุกคำนำหน้าที่ AcademicTitleResolver ผลิตได้ ต้องมีเป็นตัวเลือกให้เลือก")
    void everyTitleTheResolverCanProduceIsSelectable() {
        for (AcademicRank rank : AcademicRank.values()) {
            String withoutDoctorate = AcademicTitleResolver.resolveShortTitle(rank.thaiLabel());
            String withDoctorate = AcademicTitleResolver.resolveShortTitle(rank.thaiLabel(), "ดร.");

            assertThat(NameTitleOption.knownValues())
                    .as("คำนำหน้าของ %s แบบไม่มีวุฒิ", rank.thaiLabel())
                    .contains(withoutDoctorate);
            assertThat(NameTitleOption.knownValues())
                    .as("คำนำหน้าของ %s แบบมีวุฒิ", rank.thaiLabel())
                    .contains(withDoctorate);
        }

        assertThat(NameTitleOption.knownValues())
                .contains("ดร.", "นาย", "นาง", "นางสาว");
    }

    @Test
    @DisplayName("ชื่อเต็มที่ sync มาจาก directory ต้องเลือก option ย่อตัวเดียวกันได้")
    void fullThaiFormsMapOntoTheAbbreviatedOption() {
        assertThat(optionFor("ผู้ช่วยศาสตราจารย์")).isEqualTo("ผศ.");
        assertThat(optionFor("รองศาสตราจารย์")).isEqualTo("รศ.");
        assertThat(optionFor("ศาสตราจารย์")).isEqualTo("ศ.");
        assertThat(optionFor("อ.")).isEqualTo("อาจารย์");
        assertThat(optionFor("ผู้ช่วยศาสตราจารย์ ดร.")).isEqualTo("ผศ.ดร.");
    }

    @Test
    @DisplayName("ค่าที่ไม่รู้จักต้องไม่ถูกนับว่ารู้จัก ฟอร์มจะได้เสนอมันเป็นตัวเลือกค่าเดิม")
    void unknownValuesAreReportedAsUnknown() {
        assertThat(NameTitleOption.knownValues()).doesNotContain("Prof.Dr.", "ว่าที่ ร.ต.");
    }

    /** ค่าเดียวกับที่ template ใช้ตัดสินว่า option ไหนควรถูกเลือกไว้ */
    private String optionFor(String stored) {
        return NameTitleOption.all().stream()
                .filter(o -> o.value().equals(stored) || o.aliases().contains(stored))
                .findFirst()
                .map(NameTitleOption::value)
                .orElse(null);
    }
}
