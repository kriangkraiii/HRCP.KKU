#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""รวมไฟล์ Markdown 01-03 เป็นเอกสาร DOCX ฉบับเดียวพร้อมส่งคณะ

ผลลัพธ์: หนังสือขอข้อมูลงานวิจัย.docx
  - กระดาษ A4 ฟอนต์ TH Sarabun New
  - แต่ละไฟล์ต้นทางขึ้นหน้าใหม่
  - ตาราง Markdown แปลงเป็นตาราง Word (Table Grid) หัวตารางตัวหนามีแรเงา

ต้องติดตั้ง python-docx ก่อน:
    pip install python-docx

วิธีใช้:
    python build_docx.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

try:
    from docx import Document
    from docx.enum.table import WD_TABLE_ALIGNMENT
    from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
    from docx.oxml import OxmlElement
    from docx.oxml.ns import qn
    from docx.shared import Cm, Pt, RGBColor
except ImportError:
    sys.exit("ไม่พบไลบรารี python-docx — กรุณาติดตั้งก่อนด้วยคำสั่ง:  pip install python-docx")

BASE_DIR = Path(__file__).parent
OUT_PATH = BASE_DIR / "หนังสือขอข้อมูลงานวิจัย.docx"
SOURCES = [
    BASE_DIR / "01-request-letter.md",
    BASE_DIR / "02-data-dictionary.md",
    BASE_DIR / "03-delivery-requirements.md",
]

FONT_NAME = "TH Sarabun New"
FONT_FALLBACK = "Angsana New"  # ใช้เมื่อเครื่องปลายทางไม่มี TH Sarabun New
BODY_SIZE = Pt(16)
TABLE_SIZE = Pt(12)
HEADING_SIZES = {1: Pt(20), 2: Pt(18), 3: Pt(16)}
HEADER_SHADE = "D9E2F3"

# ---------------------------------------------------------------- ชั้นจัดรูปแบบ


def _apply_font(run, size: Pt, bold: bool = False, mono: bool = False) -> None:
    """ตั้งฟอนต์ให้ครบทั้ง ascii / hAnsi / cs — ภาษาไทยใช้ค่า w:cs จึงต้องตั้งด้วย"""
    name = "Consolas" if mono else FONT_NAME
    run.font.name = name
    run.font.size = size
    run.font.bold = bold
    rpr = run._element.get_or_add_rPr()
    fonts = rpr.find(qn("w:rFonts"))
    if fonts is None:
        fonts = OxmlElement("w:rFonts")
        rpr.append(fonts)
    for attr in ("w:ascii", "w:hAnsi", "w:cs", "w:eastAsia"):
        fonts.set(qn(attr), name)
    # ขนาดฟอนต์ฝั่ง complex script (ภาษาไทย)
    sz_cs = OxmlElement("w:szCs")
    sz_cs.set(qn("w:val"), str(int(size.pt * 2)))
    rpr.append(sz_cs)
    if bold:
        b_cs = OxmlElement("w:bCs")
        rpr.append(b_cs)
    if mono:
        run.font.color.rgb = RGBColor(0xB0, 0x30, 0x60)


def _shade_cell(cell, hex_color: str) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:fill"), hex_color)
    tc_pr.append(shd)


INLINE_RE = re.compile(r"(\*\*.+?\*\*|`[^`]+`)")


def _add_inline(paragraph, text: str, size: Pt, bold: bool = False) -> None:
    """แปลง **ตัวหนา** และ `code` เป็น run แยก ส่วนอื่นเป็นข้อความธรรมดา"""
    text = text.replace("\\|", "|").replace("<br>", "")
    for part in INLINE_RE.split(text):
        if not part:
            continue
        if part.startswith("**") and part.endswith("**"):
            _apply_font(paragraph.add_run(part[2:-2]), size, bold=True)
        elif part.startswith("`") and part.endswith("`"):
            _apply_font(paragraph.add_run(part[1:-1]), size, bold=bold, mono=True)
        else:
            _apply_font(paragraph.add_run(part), size, bold=bold)


# ---------------------------------------------------------------- ตัวแยก Markdown


def _split_table_row(line: str) -> list[str]:
    """แยกเซลล์ โดยไม่ตัดที่ \\| ซึ่งเป็นขีดตั้งที่ต้องการแสดงจริง"""
    line = line.strip()
    if line.startswith("|"):
        line = line[1:]
    if line.endswith("|"):
        line = line[:-1]
    cells, buf, i = [], "", 0
    while i < len(line):
        if line[i] == "\\" and i + 1 < len(line) and line[i + 1] == "|":
            buf += "|"
            i += 2
        elif line[i] == "|":
            cells.append(buf.strip())
            buf = ""
            i += 1
        else:
            buf += line[i]
            i += 1
    cells.append(buf.strip())
    return cells


def _is_separator(line: str) -> bool:
    return bool(re.fullmatch(r"\|[\s:\-\|]+\|", line.strip()))


def _add_table(doc, rows: list[list[str]]) -> None:
    ncols = max(len(r) for r in rows)
    table = doc.add_table(rows=0, cols=ncols)
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    for r_idx, row in enumerate(rows):
        cells = table.add_row().cells
        for c_idx in range(ncols):
            value = row[c_idx] if c_idx < len(row) else ""
            cell = cells[c_idx]
            para = cell.paragraphs[0]
            para.paragraph_format.space_before = Pt(1)
            para.paragraph_format.space_after = Pt(1)
            _add_inline(para, value, TABLE_SIZE, bold=(r_idx == 0))
            if r_idx == 0:
                _shade_cell(cell, HEADER_SHADE)
    doc.add_paragraph()


def _add_heading(doc, level: int, text: str) -> None:
    para = doc.add_paragraph()
    para.paragraph_format.space_before = Pt(12 if level > 1 else 6)
    para.paragraph_format.space_after = Pt(6)
    para.paragraph_format.keep_with_next = True
    _add_inline(para, text, HEADING_SIZES.get(level, BODY_SIZE), bold=True)


def _add_body(doc, text: str, *, indent_cm: float = 0.0, bullet: str = "") -> None:
    para = doc.add_paragraph()
    para.paragraph_format.space_after = Pt(3)
    if indent_cm:
        para.paragraph_format.left_indent = Cm(indent_cm)
    _add_inline(para, f"{bullet}{text}" if bullet else text, BODY_SIZE)


def render_markdown(doc, lines: list[str]) -> None:
    i = 0
    while i < len(lines):
        raw = lines[i]
        line = raw.rstrip()
        stripped = line.strip()

        if not stripped:
            i += 1
            continue

        # เส้นคั่น
        if re.fullmatch(r"-{3,}", stripped):
            para = doc.add_paragraph()
            para.paragraph_format.space_after = Pt(6)
            pbdr = OxmlElement("w:pBdr")
            bottom = OxmlElement("w:bottom")
            bottom.set(qn("w:val"), "single")
            bottom.set(qn("w:sz"), "6")
            bottom.set(qn("w:color"), "999999")
            pbdr.append(bottom)
            para._p.get_or_add_pPr().append(pbdr)
            i += 1
            continue

        # หัวข้อ
        m = re.match(r"^(#{1,6})\s+(.*)$", stripped)
        if m:
            _add_heading(doc, len(m.group(1)), m.group(2))
            i += 1
            continue

        # ตาราง
        if stripped.startswith("|"):
            block = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                if not _is_separator(lines[i]):
                    block.append(_split_table_row(lines[i]))
                i += 1
            if block:
                _add_table(doc, block)
            continue

        # ข้อความอ้างอิง (blockquote) — รวมหลายบรรทัดเป็นย่อหน้าเดียว
        if stripped.startswith(">"):
            chunk = []
            while i < len(lines) and lines[i].strip().startswith(">"):
                chunk.append(lines[i].strip().lstrip(">").strip())
                i += 1
            para = doc.add_paragraph()
            para.paragraph_format.left_indent = Cm(0.8)
            para.paragraph_format.space_before = Pt(4)
            para.paragraph_format.space_after = Pt(6)
            _add_inline(para, " ".join(c for c in chunk if c), BODY_SIZE)
            continue

        # รายการหัวข้อย่อย
        m = re.match(r"^(\s*)[-*]\s+(.*)$", line)
        if m:
            depth = len(m.group(1)) // 2
            _add_body(doc, m.group(2), indent_cm=0.8 + depth * 0.6, bullet="• ")
            i += 1
            continue

        # รายการลำดับเลข
        m = re.match(r"^(\s*)(\d+)\.\s+(.*)$", line)
        if m:
            depth = len(m.group(1)) // 3
            _add_body(doc, m.group(3), indent_cm=0.8 + depth * 0.6,
                      bullet=f"{m.group(2)}. ")
            i += 1
            continue

        # ย่อหน้าธรรมดา — รวมบรรทัดต่อเนื่องเข้าด้วยกัน
        chunk = []
        while i < len(lines):
            nxt = lines[i].strip()
            if (not nxt or nxt.startswith(("|", ">", "#"))
                    or re.match(r"^\s*([-*]\s|\d+\.\s)", lines[i])
                    or re.fullmatch(r"-{3,}", nxt)):
                break
            chunk.append(nxt)
            i += 1
        _add_body(doc, " ".join(chunk))


# ---------------------------------------------------------------- ประกอบเอกสาร


def _setup_document() -> "Document":
    doc = Document()
    section = doc.sections[0]
    section.page_width = Cm(21.0)
    section.page_height = Cm(29.7)
    section.top_margin = Cm(2.5)
    section.bottom_margin = Cm(2.0)
    section.left_margin = Cm(2.5)
    section.right_margin = Cm(2.0)

    normal = doc.styles["Normal"]
    normal.font.name = FONT_NAME
    normal.font.size = BODY_SIZE
    rpr = normal.element.get_or_add_rPr()
    fonts = rpr.get_or_add_rFonts()
    for attr in ("w:ascii", "w:hAnsi", "w:cs", "w:eastAsia"):
        fonts.set(qn(attr), FONT_NAME)
    return doc


def _add_cover(doc) -> None:
    for _ in range(3):
        doc.add_paragraph()
    for text, size, bold in [
        ("ขอความอนุเคราะห์ข้อมูลผลงานวิจัยและผลงานทางวิชาการ", Pt(24), True),
        ("เพื่อการเชื่อมโยงข้อมูลเข้าระบบสนับสนุนการขอกำหนดตำแหน่งทางวิชาการ", Pt(18), False),
        ("(HRCP-KKU-Academic)", Pt(18), False),
    ]:
        para = doc.add_paragraph()
        para.alignment = WD_ALIGN_PARAGRAPH.CENTER
        para.paragraph_format.space_after = Pt(10)
        _apply_font(para.add_run(text), size, bold=bold)

    doc.add_paragraph()
    for text in ["วิทยาลัยการคอมพิวเตอร์", "มหาวิทยาลัยขอนแก่น"]:
        para = doc.add_paragraph()
        para.alignment = WD_ALIGN_PARAGRAPH.CENTER
        para.paragraph_format.space_after = Pt(4)
        _apply_font(para.add_run(text), Pt(16))

    doc.add_paragraph()
    doc.add_paragraph()
    para = doc.add_paragraph()
    para.alignment = WD_ALIGN_PARAGRAPH.CENTER
    _apply_font(para.add_run("สารบัญ"), Pt(18), bold=True)
    contents = [
        "ส่วนที่ ๑  บันทึกข้อความขอความอนุเคราะห์ข้อมูล",
        "ส่วนที่ ๒  เอกสารแนบ ๑ — พจนานุกรมข้อมูล (Data Dictionary) ๑๔ ตาราง",
        "ส่วนที่ ๓  เอกสารแนบ ๒ — ข้อกำหนดการส่งมอบข้อมูล",
        "ส่วนที่ ๔  เอกสารแนบ ๓ — แบบฟอร์มกรอกข้อมูลงานวิจัย.xlsx (ไฟล์แนบแยก ๑๔ แผ่นงาน)",
    ]
    for item in contents:
        para = doc.add_paragraph()
        para.paragraph_format.left_indent = Cm(2.0)
        para.paragraph_format.space_after = Pt(4)
        _apply_font(para.add_run(item), Pt(16))


def _page_break(doc) -> None:
    para = doc.add_paragraph()
    para.add_run().add_break(WD_BREAK.PAGE)


def main() -> None:
    missing = [p.name for p in SOURCES if not p.exists()]
    if missing:
        sys.exit("ไม่พบไฟล์ต้นทาง: " + ", ".join(missing))

    doc = _setup_document()
    _add_cover(doc)

    for path in SOURCES:
        _page_break(doc)
        render_markdown(doc, path.read_text(encoding="utf-8").splitlines())
        print(f"  รวม {path.name}")

    doc.save(OUT_PATH)
    print(f"\nบันทึกแล้ว: {OUT_PATH}")
    print(f"ฟอนต์ที่ใช้: {FONT_NAME} (สำรอง: {FONT_FALLBACK})")


if __name__ == "__main__":
    main()
