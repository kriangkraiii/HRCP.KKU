#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""สร้างไฟล์ CSV แยก ๑๔ ไฟล์ จากนิยามตารางใน table_defs.py

ใช้สำหรับงานอัตโนมัติ (SFTP / สคริปต์นำเข้า) — หากต้องการไฟล์เดียวสำหรับ
ส่งให้เจ้าหน้าที่กรอกด้วยมือ ให้ใช้ make_workbook.py แทน

หัวคอลัมน์แต่ละไฟล์มี ๒ แถว
  แถวที่ ๑ = ชื่อฟิลด์ (ตรงกับเอกสารแนบ ๑ — ห้ามแก้)
  แถวที่ ๒ = คำอธิบายภาษาไทย + ระดับความจำเป็น (M/R/O) — เป็นแถวช่วยอ่าน ลบได้

เขียนด้วย encoding utf-8-sig (UTF-8 with BOM) เพื่อให้ Microsoft Excel
แสดงภาษาไทยได้ถูกต้องเมื่อดับเบิลคลิกเปิดไฟล์

วิธีใช้:  python make_templates.py
"""

import csv
import sys
from pathlib import Path

# คอนโซล Windows ใช้ cp1252 เป็นค่าเริ่มต้น ทำให้ print ข้อความไทยล้มเหลว
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

from table_defs import TABLES

OUT_DIR = Path(__file__).parent


def main() -> None:
    for code, thai, columns in TABLES:
        path = OUT_DIR / f"{code}.csv"
        with path.open("w", encoding="utf-8-sig", newline="") as fh:
            writer = csv.writer(fh)
            writer.writerow([field for field, _ in columns])
            writer.writerow([desc for _, desc in columns])
        print(f"{path.name:34s} {len(columns):2d} คอลัมน์  {thai}")
    print(f"\nสร้างเสร็จ {len(TABLES)} ไฟล์ ที่ {OUT_DIR}")


if __name__ == "__main__":
    main()
