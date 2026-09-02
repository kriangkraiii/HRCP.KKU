# -*- coding: utf-8 -*-
"""
Replace UI screenshots in screenshot-descriptions.docx for:
  - Section 2 (Academic Evaluation - Admin)  → user interface/Academic Evaluation/ [admin files]
  - Section 3 (Academic Evaluation - User)   → user interface/Academic Evaluation/ [user files]
  - Section 4 (Position Request - Admin)     → user interface/Position Request/ [admin files]
  - Section 5 (Position Request - User)      → user interface/Position Request/ [user files]
"""
import os
from docx import Document
from docx.oxml.ns import qn

DOC_PATH  = r"c:/Projects/RM/HRCP-KKU-Academic/docs/screenshot-descriptions.docx"
ACAD_DIR  = r"c:/Projects/RM/HRCP-KKU-Academic/docs/screenshots/user interface/Academic Evaluation"
POS_DIR   = r"c:/Projects/RM/HRCP-KKU-Academic/docs/screenshots/user interface/Position Request"

# ── helpers ──────────────────────────────────────────────────────────────────

def load_images(folder, prefix):
    """Return sorted list of full paths whose filename starts with prefix."""
    files = sorted(
        f for f in os.listdir(folder)
        if f.lower().endswith(".png") and prefix in f
    )
    return [os.path.join(folder, f) for f in files]

def get_section_rids(doc, start_para, end_para):
    """Collect r:embed rIds for every inline image in paragraph range [start, end)."""
    rids = []
    for i in range(start_para, end_para):
        para = doc.paragraphs[i]
        for blip in para._element.findall(".//" + qn("a:blip")):
            r_embed = blip.get(qn("r:embed"))
            if r_embed:
                rids.append(r_embed)
    return rids

def replace_images(doc, rids, new_paths):
    """Replace image blobs in doc for each (rId, new_path) pair."""
    replaced = 0
    for rid, path in zip(rids, new_paths):
        if rid in doc.part.rels:
            img_part = doc.part.rels[rid].target_part
            with open(path, "rb") as f:
                img_part._blob = f.read()
            replaced += 1
            print(f"  OK {rid} <- {os.path.basename(path)}")
        else:
            print(f"  SKIP rId {rid} not found in document")
    return replaced

# ── section boundaries (paragraph indices, 0-based) ──────────────────────────
SECTIONS = {
    "acad_admin": (40,  102),   # Section 2
    "acad_user":  (102, 136),   # Section 3
    "pos_admin":  (136, 158),   # Section 4
    "pos_user":   (158, 196),   # Section 5
}

# ── main ──────────────────────────────────────────────────────────────────────
doc = Document(DOC_PATH)

tasks = [
    ("acad_admin", ACAD_DIR, "admin"),
    ("acad_user",  ACAD_DIR, "user"),
    ("pos_admin",  POS_DIR,  "admin"),
    ("pos_user",   POS_DIR,  "user"),
]

total_replaced = 0
for key, folder, prefix in tasks:
    start, end = SECTIONS[key]
    rids      = get_section_rids(doc, start, end)
    new_paths = load_images(folder, prefix)

    print(f"\n[{key}]  slots={len(rids)}  new_images={len(new_paths)}")
    if not new_paths:
        print("  (no images found, skipping)")
        continue

    n = replace_images(doc, rids, new_paths)
    total_replaced += n
    if len(new_paths) < len(rids):
        print(f"  WARN: {len(rids) - len(new_paths)} slot(s) unchanged (fewer new images than slots)")
    elif len(new_paths) > len(rids):
        print(f"  WARN: {len(new_paths) - len(rids)} new image(s) unused (more images than slots)")

doc.save(DOC_PATH)
print(f"\nDone — {total_replaced} images replaced. Saved to: {DOC_PATH}")
