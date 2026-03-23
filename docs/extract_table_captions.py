from __future__ import annotations

import argparse
import re
from collections import Counter
from pathlib import Path

from docx import Document


CAPTION_RE = re.compile(r"^ตาราง\s*ที่\s*(\S+)\s+(.+)$")


def extract_captions(doc_path: Path) -> list[str]:
    doc = Document(str(doc_path))
    captions: list[str] = []
    for paragraph in doc.paragraphs:
        text = paragraph.text.strip()
        if text.startswith("ตาราง") and "ที่" in text:
            if CAPTION_RE.match(text):
                captions.append(text)
    return captions


def main() -> None:
    parser = argparse.ArgumentParser(description="Extract Thai table captions from DOCX.")
    parser.add_argument("--doc", default="docs/HRCP_KKU.docx", help="Path to DOCX file")
    parser.add_argument("--out", default="docs/list_of_tables_audit.md", help="Output markdown report")
    parser.add_argument("--target", type=int, default=168, help="Expected table count")
    args = parser.parse_args()

    doc_path = Path(args.doc)
    out_path = Path(args.out)

    if not doc_path.exists():
        raise FileNotFoundError(doc_path)

    captions = extract_captions(doc_path)
    counter = Counter(captions)
    duplicates = [c for c, n in counter.items() if n > 1]
    missing = max(args.target - len(captions), 0)

    lines: list[str] = []
    lines.append("# รายงานตรวจสอบสารบัญตาราง")
    lines.append("")
    lines.append(f"- ไฟล์เอกสาร: {doc_path.as_posix()}")
    lines.append(f"- จำนวน caption ตารางที่ตรวจพบ: {len(captions)}")
    lines.append(f"- จำนวนเป้าหมาย: {args.target}")
    lines.append(f"- จำนวนที่ยังขาดจากเป้าหมาย: {missing}")
    lines.append(f"- จำนวน caption ซ้ำ: {len(duplicates)}")
    lines.append("")

    if duplicates:
        lines.append("## Caption ซ้ำ")
        lines.append("")
        for item in duplicates:
            lines.append(f"- {item} (พบ {counter[item]} ครั้ง)")
        lines.append("")

    lines.append("## รายการ caption ตารางที่พบ")
    lines.append("")
    for i, caption in enumerate(captions, start=1):
        lines.append(f"{i}. {caption}")
    lines.append("")

    out_path.write_text("\n".join(lines), encoding="utf-8")
    print(f"written {out_path.as_posix()}")
    print(f"found {len(captions)}")
    print(f"missing {missing}")


if __name__ == "__main__":
    main()
