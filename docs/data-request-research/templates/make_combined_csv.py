#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""รวมทุกตาราง A1–C7 เป็นไฟล์ CSV แผ่นเดียว

ผลลัพธ์: รายการข้อมูลที่ขอ-รวมทุกตาราง.csv
  หนึ่งแถว = หนึ่งฟิลด์ที่ขอ (รวม 184 แถวจาก 14 ตาราง)
  มีคอลัมน์ว่างท้ายตารางไว้ให้คณะกรอกตอบกลับว่ามีข้อมูลนั้นหรือไม่

เขียนด้วย encoding utf-8-sig (UTF-8 with BOM) ให้ Excel เปิดแล้วภาษาไทยไม่เพี้ยน

วิธีใช้:  python make_combined_csv.py
"""

from __future__ import annotations

import csv
import re
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

from table_defs import TABLES

OUT_PATH = Path(__file__).parent / "รายการข้อมูลที่ขอ-รวมทุกตาราง.csv"

HEADER = [
    "ตาราง",
    "ชื่อตาราง",
    "ลำดับ",
    "ฟิลด์",
    "คำอธิบาย",
    "ระดับ",
    "คณะมีข้อมูลนี้ (Y/N)",
    "ชื่อคอลัมน์ในระบบของคณะ",
    "หมายเหตุ",
]

LEVEL_RE = re.compile(r"\s*\[([MRO])([^\]]*)\]\s*$")


def split_level(desc: str) -> tuple[str, str]:
    """แยก '[M]' หรือ '[M ถ้าไม่มี title_en]' ออกจากท้ายคำอธิบาย"""
    m = LEVEL_RE.search(desc)
    if not m:
        return desc.strip(), ""
    level = m.group(1) + m.group(2)
    return LEVEL_RE.sub("", desc).strip(), level.strip()


def main() -> None:
    rows = []
    for code, thai, columns in TABLES:
        table_id = code.split("_", 1)[0]          # A1, A2, B1, ...
        for seq, (field, desc) in enumerate(columns, start=1):
            text, level = split_level(desc)
            rows.append([table_id, thai, seq, field, text, level, "", "", ""])

    with OUT_PATH.open("w", encoding="utf-8-sig", newline="") as fh:
        writer = csv.writer(fh)
        writer.writerow(HEADER)
        writer.writerows(rows)

    by_level: dict[str, int] = {}
    for r in rows:
        by_level[r[5][:1]] = by_level.get(r[5][:1], 0) + 1

    print(f"บันทึกแล้ว: {OUT_PATH}")
    print(f"{len(TABLES)} ตาราง รวม {len(rows)} แถว")
    print("แยกตามระดับ:", " · ".join(
        f"{k}={v}" for k, v in sorted(by_level.items())))


if __name__ == "__main__":
    main()
