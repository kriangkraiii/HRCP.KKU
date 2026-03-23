import os
import re
import sys
from datetime import date

try:
    from docx import Document
    from docx.shared import Pt, RGBColor, Cm
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.oxml.ns import qn
    from docx.oxml import OxmlElement
except ImportError:
    print("python-docx is not installed. Please install it first.")
    sys.exit(1)

# ── Configurable constants ────────────────────────────────────────────────────
TABLE_START_NUM = 1
TESTER_NAME     = "นายเกรียงไกร ประเสริฐ  นายปาณวัฒน์ จันทร์ทองหลาง"
PROJECT_CODE    = "HRCP-KKU"
VERSION         = "1.0"
# ─────────────────────────────────────────────────────────────────────────────

THAI_MONTHS = [
    "", "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน",
    "พฤษภาคม", "มิถุนายน", "กรกฎาคม", "สิงหาคม",
    "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม",
]

def thai_date(d: date) -> str:
    return f"วันที่ {d.day} {THAI_MONTHS[d.month]} พ.ศ. {d.year + 543}"

GIVEN_RE = re.compile(r"//\s*Given:\s*(.+)")
WHEN_RE  = re.compile(r"//\s*When:\s*(.+)")
THEN_RE  = re.compile(r"//\s*Then:\s*(.+)")
# Match @Test ... @DisplayName("...") method_name() { body }
TEST_BODY_RE = re.compile(
    r"@Test\s+@DisplayName\(\"([^\"]+)\"\)\s+\w[\w\s<>]*\([^)]*\)\s*\{([^{}]*)\}",
    re.DOTALL,
)

FONT_NAME = "TH SarabunPSK"
FONT_SIZE = Pt(14)
LABEL_BG  = "D9D9D9"


def _set_cell_bg(cell, hex_color: str):
    tc = cell._tc
    tcPr = tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_color)
    tcPr.append(shd)


def _cell_text(cell, text: str, bold: bool = False, size=None):
    cell.text = ""
    p = cell.paragraphs[0]
    run = p.add_run(text)
    run.font.name = FONT_NAME
    run.font.size = size or FONT_SIZE
    run.font.bold = bold


def make_meta_table(doc, scenario_id, module, scenario_name, given_text, desc_text):
    tbl = doc.add_table(rows=6, cols=4)
    tbl.style = "Table Grid"

    labels = [
        ("รหัสสถานการณ์ทดสอบ", scenario_id,  "รหัสโครงงาน", PROJECT_CODE),
        ("โปรแกรมที่อยู่ระหว่างทดสอบ", module, "ผู้ทดสอบ",   TESTER_NAME),
        ("วันที่ทดสอบ", thai_date(date.today()), "เวอร์ชัน",  VERSION),
    ]
    for r, (l1, v1, l2, v2) in enumerate(labels):
        _cell_text(tbl.cell(r, 0), l1, bold=True)
        _set_cell_bg(tbl.cell(r, 0), LABEL_BG)
        _cell_text(tbl.cell(r, 1), v1)
        _cell_text(tbl.cell(r, 2), l2, bold=True)
        _set_cell_bg(tbl.cell(r, 2), LABEL_BG)
        _cell_text(tbl.cell(r, 3), v2)

    # Merged rows 3-5
    merged_rows = [
        ("ชื่อสถานการณ์การทดสอบ", scenario_name),
        ("ข้อกำหนดเบื้องต้น",     given_text),
        ("คำอธิบาย",               desc_text),
    ]
    for r, (label, value) in enumerate(merged_rows, start=3):
        _cell_text(tbl.cell(r, 0), label, bold=True)
        _set_cell_bg(tbl.cell(r, 0), LABEL_BG)
        merged_cell = tbl.cell(r, 1).merge(tbl.cell(r, 3))
        _cell_text(merged_cell, value)

    # Column widths: label cols narrower
    for r in range(6):
        for c, w in enumerate([Cm(5), Cm(6.5), Cm(4), Cm(4)]):
            tbl.cell(r, c).width = w

    return tbl


def _remove_para_between_tables(tbl1, tbl2):
    """Remove auto-inserted <w:p> between two adjacent tables."""
    body = tbl1._element.getparent()
    children = list(body)
    idx1 = children.index(tbl1._element)
    idx2 = children.index(tbl2._element)
    for child in children[idx1 + 1:idx2]:
        if child.tag == qn("w:p"):
            body.remove(child)


def make_steps_table(doc, test_cases):
    headers = ["ลำดับ", "กรณีทดสอบ", "ขั้นตอน", "ผลลัพธ์ที่คาดหวัง", "ผลลัพธ์จริง", "ผลการทดสอบ"]
    tbl = doc.add_table(rows=1 + len(test_cases), cols=6)
    tbl.style = "Table Grid"

    hdr = tbl.rows[0].cells
    for i, h in enumerate(headers):
        _cell_text(hdr[i], h, bold=True)
        _set_cell_bg(hdr[i], LABEL_BG)

    for i, tc in enumerate(test_cases, start=1):
        row = tbl.rows[i].cells
        _cell_text(row[0], str(tc["no"]))
        _cell_text(row[1], tc["id_desc"])
        _cell_text(row[2], tc["when"])
        _cell_text(row[3], tc["then"])
        _cell_text(row[4], "")
        _cell_text(row[5], "Pass")

    # Column widths
    col_widths = [Cm(1.2), Cm(4.5), Cm(4), Cm(4), Cm(2.5), Cm(2)]
    for r in range(len(tbl.rows)):
        for c, w in enumerate(col_widths):
            tbl.cell(r, c).width = w

    return tbl


def parse_nested_blocks(content):
    """Parse all @Nested blocks from a Java file content."""
    blocks = []
    main_match = re.search(r'@DisplayName\("([^"]+)"\)\s*class', content)
    main_mod = main_match.group(1).replace("UAT: ", "") if main_match else "?"

    raw_blocks = content.split("@Nested")
    if len(raw_blocks) <= 1:
        return []

    for block in raw_blocks[1:]:
        dn_matches = re.findall(r'@DisplayName\("([^"]+)"\)', block)
        if not dn_matches:
            continue

        sub_full = dn_matches[0]
        if ": " in sub_full:
            sub_id, sub_name = sub_full.split(": ", 1)
        else:
            sub_id, sub_name = "", sub_full

        # Extract individual tests with bodies
        test_cases = []
        seq = 1
        for tc_display, body in TEST_BODY_RE.findall(block):
            if ": " in tc_display:
                tc_id, tc_desc = tc_display.split(": ", 1)
            else:
                tc_id, tc_desc = "", tc_display

            given = "; ".join(GIVEN_RE.findall(body))
            when  = "; ".join(WHEN_RE.findall(body))
            then  = "; ".join(THEN_RE.findall(body))

            test_cases.append({
                "no":      seq,
                "id_desc": f"{tc_id}: {tc_desc}" if tc_id else tc_desc,
                "given":   given,
                "when":    when,
                "then":    then,
            })
            seq += 1

        if not test_cases:
            continue

        blocks.append({
            "module":    main_mod,
            "sub_id":    sub_id,
            "sub_name":  sub_name,
            "test_cases": test_cases,
        })

    return blocks


def create_word():
    doc = Document()

    # Page margins
    for section in doc.sections:
        section.top_margin    = Cm(2.54)
        section.bottom_margin = Cm(2.54)
        section.left_margin   = Cm(2.54)
        section.right_margin  = Cm(2.54)

    uat_dir = r"c:\Projects\RM\HRCP-KKU-Academic\src\test\java\com\ecom\uat"
    java_files = sorted(f for f in os.listdir(uat_dir) if f.endswith(".java"))

    table_no   = TABLE_START_NUM
    scenario_no = 1
    total_tc   = 0
    first_page = True

    for fname in java_files:
        content = open(os.path.join(uat_dir, fname), encoding="utf-8").read()
        blocks  = parse_nested_blocks(content)

        for blk in blocks:
            if not first_page:
                doc.add_page_break()
            first_page = False

            # Table label
            lbl = doc.add_paragraph(
                f"ตารางที่ {table_no}  สถานการณ์ทดสอบที่ {scenario_no}"
            )
            lbl.alignment = WD_ALIGN_PARAGRAPH.LEFT
            run_lbl = lbl.runs[0] if lbl.runs else lbl.add_run(lbl.text)
            run_lbl.font.name = FONT_NAME
            run_lbl.font.size = FONT_SIZE
            run_lbl.font.bold = True

            # Derive precondition from first test's Given
            given_text = blk["test_cases"][0]["given"] if blk["test_cases"] else ""
            desc_text  = (
                f"ทดสอบสถานการณ์ {blk['sub_name']} "
                f"ในโมดูล{blk['module']} "
                f"ครอบคลุม {len(blk['test_cases'])} กรณีทดสอบ"
            )

            meta_tbl = make_meta_table(
                doc,
                scenario_id=blk["sub_id"],
                module=blk["module"],
                scenario_name=blk["sub_name"],
                given_text=given_text,
                desc_text=desc_text,
            )

            steps_tbl = make_steps_table(doc, blk["test_cases"])
            _remove_para_between_tables(meta_tbl, steps_tbl)

            table_no    += 1
            scenario_no += 1
            total_tc    += len(blk["test_cases"])

    out_path = r"c:\Projects\RM\HRCP-KKU-Academic\UAT_Test_Cases.docx"
    doc.save(out_path)
    print(f"Generated {out_path}")
    print(f"  Scenarios: {scenario_no - 1}  |  Test cases: {total_tc}")


if __name__ == "__main__":
    create_word()
