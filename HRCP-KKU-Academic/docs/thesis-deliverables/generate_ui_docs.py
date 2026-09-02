#!/usr/bin/env python3
"""
Generate UI Design DOCX for HRCP-KKU-Academic system.
Output: ui-design.docx (all 8 subsystems, ~35 screens)
Format: Headings + bullet lists (no tables)
"""

from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.style import WD_STYLE_TYPE
import os

# ============================================================
# Screen data: all 8 subsystems
# ============================================================

SUBSYSTEMS = [
    {
        "name": "1. ระบบยืนยันตัวตน (Authentication)",
        "screens": [
            {
                "id": "UI-AUTH-01",
                "title": "หน้าเข้าสู่ระบบ",
                "url": "/signin",
                "role": "ผู้ใช้ทั่วไป (Guest)",
                "use_cases": "UC-AUTH-01",
                "screenshot": "screenshots/auth-guest-login.png",
                "description": (
                    "หน้าจอพื้นหลังไล่สีน้ำเงินเข้ม (gradient จาก #0d1b3e ไป #1565c0) ตรงกลางมีกล่องสีขาวมุมมน (border-radius 16px) "
                    "ความกว้าง 440px ด้านบนกล่องแสดงโลโก้ระบบ (cphr_new.png ขนาด 100x100px พื้นขาวมุมมน) "
                    "และหัวเรื่อง 'ระบบเอกสารและการติดตามผลการยื่นขอกำหนดตำแหน่งทางวิชาการ' ตัวอักษรสีขาว "
                    "ภายในกล่องขาวมีหัว 'เข้าสู่ระบบ' สีน้ำเงินเข้ม (#1a237e) ตามด้วยช่องกรอกอีเมล "
                    "(ไอคอน fa-envelope ด้านซ้าย) และรหัสผ่าน (ไอคอน fa-lock) ทั้งคู่มีขอบมน "
                    "ปุ่ม 'เข้าสู่ระบบ' สีน้ำเงิน gradient เต็มความกว้าง คั่นด้วยเส้นแบ่ง 'หรือ' "
                    "แล้วมีปุ่ม 'เข้าสู่ระบบครั้งแรก' แบบ outline สีน้ำเงินพร้อมไอคอน fa-user-plus "
                    "ด้านล่างมีลิงก์ 'ลืมรหัสผ่าน?' มุมขวาบนมี dropdown สลับภาษา TH/EN "
                    "แบบ glassmorphism (backdrop-filter blur) ด้านล่างสุดมีข้อความ copyright"
                ),
                "components": [
                    ("อีเมล", "Text Input (required)", "กรอกอีเมลผู้ใช้"),
                    ("รหัสผ่าน", "Password Input (required)", "กรอกรหัสผ่าน"),
                    ("ปุ่ม \"เข้าสู่ระบบ\"", "Button (primary)", "ส่ง credentials → POST /signin"),
                    ("ลิงก์ \"เข้าสู่ระบบครั้งแรก\"", "Link", "สำหรับผู้ใช้ใหม่ → GET /first-login"),
                    ("ลิงก์ \"ลืมรหัสผ่าน\"", "Link", "ขอรีเซ็ตรหัสผ่าน → GET /forgot-password"),
                    ("ข้อความแจ้งเตือน", "Alert (error/success)", "แสดง flash message"),
                ],
                "rules": [
                    "แสดงข้อความ success เมื่อ redirect จาก logout, set-password, reset-password",
                    "แสดงข้อความ error เมื่อ login ผิดพลาด",
                ],
            },
            {
                "id": "UI-AUTH-02",
                "title": "หน้าเข้าสู่ระบบครั้งแรก",
                "url": "/first-login",
                "role": "ผู้ใช้ทั่วไป (Guest)",
                "use_cases": "UC-AUTH-02",
                "screenshot": "screenshots/auth-guest-first-login.png",
                "description": (
                    "หน้าจอพื้นหลังไล่สีน้ำเงินเข้มเหมือนหน้า Login ตรงกลางมีกล่องขาวมุมมน (440px) "
                    "ด้านบนแสดงโลโก้ระบบ (70x70px พื้นขาวมุมมน) และชื่อระบบสีขาว "
                    "ใต้โลโก้มี Step Indicator เป็นแถบ 3 ขั้นตอน (แถบ 40x4px) ขั้นตอนที่ 1 สีน้ำเงินเข้ม (#1a237e) "
                    "ขั้นตอนที่ 2-3 สีเทาอ่อน (#e0e0e0) ภายในกล่องขาวมีหัว 'เข้าสู่ระบบครั้งแรก' "
                    "ช่องกรอกอีเมล (ไอคอน fa-envelope ด้านซ้าย ขอบมน 10px) "
                    "ปุ่ม 'ส่ง OTP' สีน้ำเงิน gradient พร้อมไอคอน fa-paper-plane เต็มความกว้าง "
                    "ด้านล่างมีลิงก์ 'กลับหน้า Login' แสดง alert สีเขียว/แดงตามผลลัพธ์"
                ),
                "components": [
                    ("อีเมล", "Text Input (required)", "กรอกอีเมลที่ลงทะเบียนไว้"),
                    ("ปุ่ม \"ส่ง OTP\"", "Button (primary)", "ส่ง OTP ไปทางอีเมล → POST /first-login"),
                    ("ข้อความแจ้งเตือน", "Alert", "แสดง success/error message"),
                ],
                "rules": [],
            },
            {
                "id": "UI-AUTH-03",
                "title": "หน้ายืนยัน OTP",
                "url": "/first-login/verify-otp",
                "role": "ผู้ใช้ทั่วไป (Guest)",
                "use_cases": "UC-AUTH-02",
                "screenshot": "screenshots/auth-guest-verify-otp.png",
                "description": (
                    "หน้าจอพื้นหลังไล่สีน้ำเงินเข้มเหมือนหน้า Login ตรงกลางมีกล่องขาวมุมมน "
                    "Step Indicator แสดงขั้นตอนที่ 1 สีเขียว (#4caf50 = เสร็จแล้ว) ขั้นตอนที่ 2 สีน้ำเงินเข้ม (active) "
                    "ขั้นตอนที่ 3 สีเทา ภายในกล่องขาวมีหัว 'ยืนยัน OTP' และข้อความแจ้งอีเมลที่ส่ง OTP ไป "
                    "ช่องกรอก OTP ขนาดใหญ่ตรงกลาง (font-size 28px, letter-spacing 8px, ตัวหนา) "
                    "รับเฉพาะตัวเลข 8 หลัก ใต้ช่องกรอกมีกล่องสีเทา (#f5f5f5 มุมมน 8px) "
                    "แสดงไอคอน fa-clock พร้อมข้อความ 'OTP มีอายุ 5 นาที' "
                    "ปุ่ม 'ยืนยัน OTP' สีน้ำเงิน gradient พร้อมไอคอน fa-check-circle"
                ),
                "components": [
                    ("ข้อความแจ้งอีเมล", "Text", "แสดงอีเมลที่ส่ง OTP ไป"),
                    ("รหัส OTP", "Text Input (required)", "กรอกรหัส OTP 6 หลัก"),
                    ("ปุ่ม \"ยืนยัน\"", "Button (primary)", "ตรวจสอบ OTP → POST /first-login/verify-otp"),
                ],
                "rules": [
                    "ถ้า session ไม่มี otpEmail จะ redirect ไป /first-login",
                    "ถ้า OTP ผิด แสดง error message แล้วกลับมาหน้านี้",
                ],
            },
            {
                "id": "UI-AUTH-04",
                "title": "หน้าตั้งรหัสผ่าน",
                "url": "/first-login/set-password",
                "role": "ผู้ใช้ทั่วไป (Guest)",
                "use_cases": "UC-AUTH-02",
                "screenshot": "screenshots/auth-guest-set-password.png",
                "description": (
                    "หน้าจอพื้นหลังไล่สีน้ำเงินเข้มเหมือนหน้า Login ตรงกลางมีกล่องขาวมุมมน "
                    "Step Indicator แสดงขั้นตอนที่ 1-2 สีเขียว (#4caf50 = เสร็จแล้ว) ขั้นตอนที่ 3 สีน้ำเงินเข้ม (active) "
                    "ภายในกล่องขาวมีหัว 'ตั้งรหัสผ่าน' ช่องกรอกรหัสผ่านใหม่ (ไอคอน fa-lock ด้านซ้าย) "
                    "และช่องยืนยันรหัสผ่าน (ไอคอน fa-lock) ใต้ช่องกรอกมีกล่องเงื่อนไข (พื้นเทา #f5f5f5) "
                    "แสดงไอคอน fa-info-circle พร้อมรายการเงื่อนไข: ≥ 6 ตัวอักษร โดยแต่ละข้อมีไอคอน fa-circle "
                    "เปลี่ยนเป็น fa-check-circle สีเขียว (#2e7d32) เมื่อผ่านเงื่อนไข "
                    "ระหว่างช่องกรอกมีข้อความตรวจสอบ: '✓ รหัสผ่านตรงกัน' (สีเขียว) หรือ '✗ รหัสผ่านไม่ตรงกัน' (สีแดง #c62828) "
                    "ปุ่ม 'ตั้งรหัสผ่าน' เริ่มต้น disabled (สีเทา #ccc) จะ enable เมื่อเงื่อนไขครบ"
                ),
                "components": [
                    ("รหัสผ่านใหม่", "Password Input (required)", "กรอกรหัสผ่าน (≥ 6 ตัวอักษร)"),
                    ("ยืนยันรหัสผ่าน", "Password Input (required)", "กรอกรหัสผ่านซ้ำ"),
                    ("ปุ่ม \"ตั้งรหัสผ่าน\"", "Button (primary)", "บันทึกรหัสผ่าน → POST /first-login/set-password"),
                ],
                "rules": [
                    "ต้องมี otpVerified=true ใน session ถึงจะเข้าได้",
                    "รหัสผ่านต้องตรงกัน + ≥ 6 ตัวอักษร",
                ],
            },
            {
                "id": "UI-AUTH-05",
                "title": "หน้าลืมรหัสผ่าน",
                "url": "/forgot-password",
                "role": "ผู้ใช้ทั่วไป (Guest)",
                "use_cases": "UC-AUTH-03",
                "screenshot": "screenshots/auth-guest-forgot-password.png",
                "description": (
                    "หน้าจอพื้นหลังไล่สีน้ำเงินเข้มเหมือนหน้า Login ตรงกลางมีกล่องขาวมุมมน (440px) "
                    "ไม่มี Step Indicator ภายในกล่องขาวมีหัว 'ลืมรหัสผ่าน' พร้อมไอคอน fa-key "
                    "ช่องกรอกอีเมล (ไอคอน fa-envelope ด้านซ้าย ขอบมน 10px) "
                    "ปุ่ม 'ส่งลิงก์รีเซ็ตรหัสผ่าน' สีน้ำเงิน gradient พร้อมไอคอน fa-paper-plane เต็มความกว้าง "
                    "ด้านล่างมีลิงก์ 'กลับไปหน้าเข้าสู่ระบบ' แสดง alert สีเขียว/แดงตามผลลัพธ์"
                ),
                "components": [
                    ("อีเมล", "Text Input (required)", "กรอกอีเมลที่ลงทะเบียนไว้"),
                    ("ปุ่ม \"ส่งลิงก์รีเซ็ต\"", "Button (primary)", "ส่งลิงก์รีเซ็ตไปทางอีเมล → POST /forgot-password"),
                    ("ลิงก์ \"กลับไปหน้า Login\"", "Link", "กลับไปหน้าเข้าสู่ระบบ → GET /signin"),
                ],
                "rules": [],
            },
            {
                "id": "UI-AUTH-06",
                "title": "หน้ารีเซ็ตรหัสผ่าน",
                "url": "/reset-password?token=xxx",
                "role": "ผู้ใช้ทั่วไป (Guest)",
                "use_cases": "UC-AUTH-04",
                "screenshot": "screenshots/auth-guest-reset-password.png",
                "description": (
                    "หน้าจอพื้นหลังไล่สีน้ำเงินเข้มเหมือนหน้า Login ตรงกลางมีกล่องขาวมุมมน (440px) "
                    "ภายในกล่องขาวมีหัว 'รีเซ็ตรหัสผ่าน' ช่องกรอกรหัสผ่านใหม่ (ไอคอน fa-lock) "
                    "และช่องยืนยันรหัสผ่าน (ไอคอน fa-lock) มี hidden field เก็บ token สำหรับ validate "
                    "ใต้ช่องยืนยันมีข้อความตรวจสอบ: '✓ รหัสผ่านตรงกัน' (สีเขียว #2e7d32) "
                    "หรือ '✗ รหัสผ่านไม่ตรงกัน' (สีแดง #c62828) ตรวจสอบแบบ real-time ด้วย JavaScript "
                    "ปุ่ม 'รีเซ็ตรหัสผ่าน' สีน้ำเงิน gradient พร้อมไอคอน fa-check-circle "
                    "แสดง alert แดงเมื่อ token ไม่ถูกต้องหรือหมดอายุ"
                ),
                "components": [
                    ("รหัสผ่านใหม่", "Password Input (required)", "กรอกรหัสผ่านใหม่ (≥ 6 ตัวอักษร)"),
                    ("ยืนยันรหัสผ่าน", "Password Input (required)", "กรอกรหัสผ่านซ้ำ"),
                    ("ปุ่ม \"รีเซ็ตรหัสผ่าน\"", "Button (primary)", "บันทึกรหัสผ่านใหม่ → POST /reset-password"),
                    ("Hidden field: token", "Hidden Input", "ส่ง token กลับไปเพื่อ validate"),
                ],
                "rules": [
                    "ถ้า token ไม่ถูกต้องจะแสดงหน้า message \"ลิงก์ไม่ถูกต้องหรือหมดอายุ\" แทน",
                    "รหัสผ่านต้องตรงกัน + ≥ 6 ตัวอักษร",
                ],
            },
        ],
    },
    {
        "name": "2. ระบบคำร้องประเมินผลการสอน (Academic Request)",
        "screens": [
            {
                "id": "UI-ACAD-A01",
                "title": "หน้ารายการคำร้องประเมินผลการสอน (Admin)",
                "url": "/admin/academic/requests",
                "role": "ผู้ดูแลระบบ (ROLE_ADMIN)",
                "use_cases": "UC-ACAD-A01",
                "screenshot": "screenshots/academic-admin-request-list.png",
                "description": (
                    "หน้าจอใช้ base_academic layout ด้านบนมีหัวเรื่อง พร้อมไอคอน fa-columns "
                    "และปุ่ม 'จัดการบุคลากร' (fa-users) แบบ conditional "
                    "ถัดมามี Tab Navigation 2 แท็บ: แท็บ 'ประเมินผลการสอน' (fa-clipboard-check สีน้ำเงิน #1565c0) "
                    "และแท็บ 'ขอกำหนดตำแหน่ง' (fa-university สีเขียว #2e7d32) แต่ละแท็บมี badge แสดงจำนวน "
                    "ใต้แท็บมี Status Cards เรียงเป็นแถว (6 คอลัมน์) แต่ละ card มีขอบซ้ายสี 4px ตามสถานะ "
                    "วงกลมไอคอนพื้นสีจาง (20% opacity) ตัวเลขจำนวนตัวหนา คลิกเพื่อกรอง "
                    "มี hover effect (translateY -2px + shadow) ถัดมามี search bar (fa-search + input + ปุ่มค้นหา) "
                    "ตารางคำร้องแบบ responsive (table-hover) แสดงคอลัมน์: ลำดับ, รหัส, ชื่อผู้ยื่น, อีเมล, "
                    "วันที่ส่ง, อัพเดทล่าสุด, สถานะ (badge rounded-pill สีตามสถานะ), ปุ่มจัดการ (fa-cog btn-academic) "
                    "แสดง filter indicator alert เมื่อมีการกรอง Empty state แสดงไอคอน fa-folder-open 3x"
                ),
                "components": [
                    ("Tabs: Phase 1 / Phase 2", "Tab bar", "สลับระหว่างคำร้องประเมินผลการสอน / ขอกำหนดตำแหน่ง"),
                    ("Status cards", "Cards (colored)", "แสดงจำนวนคำร้องแต่ละสถานะ → คลิกเพื่อกรอง"),
                    ("ช่องค้นหา", "Text Input + ปุ่มค้นหา", "ค้นหาตามชื่อผู้ยื่น → GET ?search="),
                    ("ตารางกำลังดำเนินการ", "Table", "รหัส, ชื่อผู้ยื่น, วันที่ส่ง, สถานะ, ปุ่มดู"),
                    ("ตารางเสร็จสิ้น", "Table (collapsible)", "คำร้องที่ terminal แล้ว"),
                    ("ปุ่ม \"ดู\" ในแต่ละแถว", "Button/Link", "ไปหน้ารายละเอียด → GET /admin/academic/request/{id}"),
                ],
                "rules": [
                    "ไม่แสดงคำร้องที่สถานะ DRAFT",
                    "เรียงลำดับ: กำลังดำเนินการ = วันที่ส่ง (เก่า→ใหม่), เสร็จสิ้น = วันที่อัพเดท (ใหม่→เก่า)",
                    "Status cards แสดงสี/ไอคอนตาม StatusType (INFO/WARNING/SUCCESS/DANGER)",
                ],
            },
            {
                "id": "UI-ACAD-A02",
                "title": "หน้ารายละเอียดคำร้อง (Admin)",
                "url": "/admin/academic/request/{id}",
                "role": "ผู้ดูแลระบบ (ROLE_ADMIN)",
                "use_cases": "UC-ACAD-A02, A03, A04, A06, A07, A08, A09",
                "screenshot": "screenshots/academic-admin-request-detail.png",
                "description": (
                    "หน้าจอแบ่ง 2 คอลัมน์ (5/7) คอลัมน์ซ้าย: "
                    "(1) Card อัพเดทสถานะ — ไอคอน fa-exchange-alt, dropdown เลือกสถานะ, "
                    "เมื่อเลือก MEETING_SCHEDULED จะแสดงช่อง datetime-local และสถานที่เพิ่มเติม, "
                    "textarea หมายเหตุ (2 บรรทัด), ปุ่มบันทึก (fa-save) "
                    "(2) Card คลังเอกสาร — ไอคอน fa-folder-open, badge แสดง 'X/10 ไฟล์', "
                    "ฟอร์มอัปโหลด (accept .pdf,.docx), ตาราง responsive แสดงไฟล์แนบ: ลำดับ, "
                    "ชื่อไฟล์ (ไอคอน fa-file-pdf สีแดง / fa-file-word สีน้ำเงิน), ประเภท (badge), "
                    "ขนาด (KB/MB), วันที่, ปุ่มดาวน์โหลด/ลบ "
                    "(3) Card ประวัติสถานะ — ไอคอน fa-history, รายการ scrollable (max-height 300px) "
                    "แสดง badge สถานะ + timestamp + หมายเหตุ "
                    "คอลัมน์ขวา: Card จัดการเอกสาร — ไอคอน fa-file-word, หัว 'จัดการเอกสาร (8 แบบฟอร์ม)', "
                    "ปุ่ม 'ดาวน์โหลดทั้งหมด ZIP' (fa-file-archive), การ์ดเอกสารแต่ละประเภท "
                    "แสดงปุ่มกรอก (fa-edit), ปุ่มดาวน์โหลด DOCX + พิมพ์ PDF, "
                    "มี Modal ยืนยันการแจ้งเตือนอีเมล (fa-bell สีเหลือง)"
                ),
                "components": [
                    ("Progress bar", "Stepper", "แสดงขั้นตอนสถานะ (จาก getProgressSteps)"),
                    ("ข้อมูลผู้ยื่น", "Card", "ชื่อ, อีเมล, สาขา, วันที่ส่ง"),
                    ("อัพเดทสถานะ", "Form (dropdown + textarea + button)", "เลือกสถานะ + หมายเหตุ + ปุ่มอัพเดท → POST .../status"),
                    ("วันประชุม", "Date/Time picker + Text", "แสดงเมื่อเลือก MEETING_SCHEDULED"),
                    ("ตารางเอกสาร (0-8)", "Table", "ประเภท, ชื่อ, สถานะ, ปุ่มกรอก/ดาวน์โหลด"),
                    ("ปุ่มดาวน์โหลดทั้งหมด", "Button", "ดาวน์โหลด ZIP → GET .../download-all"),
                    ("ส่วนอัปโหลดไฟล์แนบ", "File Input + Button", "อัปโหลดไฟล์เสริม (PDF/DOCX) → POST .../upload-result"),
                    ("รายการไฟล์แนบ", "List", "ชื่อไฟล์, ขนาด, ปุ่มดาวน์โหลด/ลบ"),
                    ("ไทม์ไลน์สถานะ", "Timeline/List", "ประวัติการเปลี่ยนสถานะตามเวลา"),
                ],
                "rules": [],
            },
            {
                "id": "UI-ACAD-A03",
                "title": "หน้ากรอกเอกสาร (Admin)",
                "url": "/admin/academic/request/{id}/document/{type}",
                "role": "ผู้ดูแลระบบ (ROLE_ADMIN)",
                "use_cases": "UC-ACAD-A04, A05",
                "screenshot": "screenshots/academic-admin-document-form.png",
                "description": (
                    "หน้าจอใช้ base_academic layout มี card-academic ด้านบนแสดงหัวเรื่องตามประเภทเอกสาร "
                    "ข้อมูลรหัสคำร้องและชื่อผู้ยื่น (ตัวเล็กสีเทา) "
                    "ฟอร์มแบ่งเป็นหลายส่วน (section) ตามประเภทเอกสาร แต่ละส่วนมีหัว h5 พร้อมเส้นใต้ "
                    "ฟิลด์แตกต่างกันตามประเภท: เช่น เอกสาร 0 มี dropdown คำนำหน้า, ช่องชื่อ, radio ตำแหน่ง, "
                    "เอกสาร 2 มี dropdown เลือกกรรมการ (multiple), เอกสาร 6 มีช่อง score input "
                    "พร้อมคำนวณ weighted score อัตโนมัติ (4 sections น้ำหนัก 20/30/30/20%) "
                    "มี Thai date picker (ปฏิทินพุทธศักราช แปลงเดือน/ปี อัตโนมัติ) "
                    "ด้านล่างมี 3 ปุ่ม: 'ดูตัวอย่าง' (fa-eye outline), 'บันทึกและสร้างเอกสาร' (fa-save btn-academic), "
                    "'กลับ' (fa-arrow-left outline-secondary) "
                    "มี Modal ยืนยันการแจ้งเตือนอีเมล สำหรับเอกสารประเภท 3, 4, 6, 8"
                ),
                "components": [
                    ("หัวเรื่อง", "Text (h2)", "ชื่อประเภทเอกสาร (จาก DOC_LABELS)"),
                    ("ฟอร์มข้อมูล", "Form (dynamic)", "ฟิลด์ต่างๆ ตามประเภทเอกสาร (auto-fill จากเอกสารก่อนหน้า)"),
                    ("Dropdown บุคลากร", "Select (multiple)", "เลือกกรรมการ/คณบดี/หัวหน้าสาขา"),
                    ("ปุ่ม \"บันทึกแบบร่าง\"", "Button (secondary)", "บันทึก JSON ไม่สร้าง DOCX → POST (action=draft)"),
                    ("ปุ่ม \"บันทึกและสร้างเอกสาร\"", "Button (primary)", "สร้าง DOCX + บันทึก → POST (action=submit)"),
                    ("ปุ่ม \"ดูตัวอย่าง\"", "Button (outline)", "Preview DOCX แบบ real-time → POST /api/academic/preview/{type}"),
                ],
                "rules": [
                    "ฟิลด์จะแตกต่างกันตามประเภทเอกสาร (แต่ละ type มี fragment HTML แยก)",
                    "Auto-fill: doc_0 → doc_3/6/7/8 (ชื่อ/ตำแหน่ง), doc_2 → อื่นๆ (รายชื่อกรรมการ)",
                    "เอกสารที่ 6: แสดง score input + คำนวณ weighted score อัตโนมัติ",
                ],
            },
            {
                "id": "UI-ACAD-U01",
                "title": "หน้าแดชบอร์ด (User)",
                "url": "/user/academic/dashboard",
                "role": "ผู้ยื่นคำร้อง (ROLE_USER)",
                "use_cases": "UC-ACAD-U01",
                "screenshot": "screenshots/academic-user-dashboard.png",
                "description": (
                    "หน้าจอใช้ base_academic layout ด้านบนมีหัว 'แดชบอร์ด' (fa-tachometer-alt) "
                    "พร้อมปุ่ม 'ยื่นคำร้องใหม่' (fa-plus-circle btn-academic) ซึ่ง disabled เมื่อมีคำร้อง active อยู่ "
                    "แสดง alert-warning เมื่อมีคำร้อง active และ alert-info อธิบายสถานะ "
                    "Draft Card: card ขอบเหลือง (border-warning) แสดงไอคอน fa-edit, badge 'แบบร่าง' สีเหลือง, "
                    "ปุ่ม 'ดำเนินการต่อ' (fa-arrow-right) "
                    "ถัดมาเป็น Request Cards แต่ละ card แสดง: รหัสคำร้อง + ชื่อผู้ยื่น ใน header, "
                    "วันที่ส่ง/อัพเดทล่าสุด, badge สถานะ (สีตาม StatusType) "
                    "Progress Tracker แนวนอน: วงกลม + เส้นเชื่อม แสดงขั้นตอน (รับคำร้อง → แต่งตั้ง → ประชุม → ผลลัพธ์) "
                    "สีเขียว (#2e7d32) = ผ่าน, สีส้ม (#f57f17) = แก้ไข, สีแดง (#c62828) = ไม่รับ "
                    "Alert แสดงวันประชุม (fa-calendar-alt สีฟ้า) "
                    "Alert ผลลัพธ์: สีเขียว (ผ่าน), สีเหลือง (แก้ไข + ฟอร์มอัปโหลด), สีแดง (ไม่รับ) "
                    "ปุ่ม 'ดูรายละเอียด' (fa-eye outline-primary) Empty state แสดง fa-folder-open 3x"
                ),
                "components": [
                    ("Draft card", "Card (highlight)", "แสดง draft ที่ยังไม่ได้ส่ง (ถ้ามี) → คลิก → new-request"),
                    ("ปุ่ม \"สร้างคำร้องใหม่\"", "Button (primary)", "สร้างคำร้องใหม่ → GET /user/academic/new-request"),
                    ("ตารางคำร้อง", "Table", "รหัส, วันที่ส่ง, สถานะ, ปุ่มดู"),
                    ("ปุ่ม \"ดู\"", "Button/Link", "ไปหน้ารายละเอียด → GET /user/academic/request/{id}"),
                ],
                "rules": [
                    "ปุ่ม \"สร้างคำร้องใหม่\" ซ่อนเมื่อ hasActiveRequest = true",
                    "Draft card แสดงเฉพาะเมื่อมี draft ที่ยังไม่ส่ง",
                ],
            },
            {
                "id": "UI-ACAD-U02",
                "title": "หน้าสร้างคำร้องใหม่ (User)",
                "url": "/user/academic/new-request",
                "role": "ผู้ยื่นคำร้อง (ROLE_USER)",
                "use_cases": "UC-ACAD-U02, U03, U04, U05",
                "screenshot": "screenshots/academic-user-new-request.png",
                "description": (
                    "หน้าจอใช้ base_academic layout ด้านบนมีหัว 'ยื่นคำร้องใหม่' (fa-edit) "
                    "พร้อม badge 'แบบร่าง' สีเทา ถัดมามี Step Indicator 3 ขั้นตอน แนวนอนตรงกลาง: "
                    "แต่ละขั้นตอนเป็น pill (padding 8px 16px, border-radius 8px) มีวงกลมตัวเลข (28x28px) "
                    "สถานะ active = พื้นสีแดงเข้ม (--primary-color #8B0000) ตัวขาว, "
                    "completed = พื้นเขียวอ่อน (#e8f5e9) ตัวเขียว (#2e7d32) มีไอคอน fa-check, "
                    "pending = พื้นเทา (#f0f0f0) คั่นด้วย fa-chevron-right "
                    "Card เอกสารที่ 0 (fa-file-signature): header แสดง badge 'กรอกแล้ว' สีเขียว / 'รอกรอก' สีเหลือง, "
                    "ปุ่ม 'กรอกเอกสาร' (fa-plus-circle btn-academic) หรือ 'แก้ไขเอกสาร' (fa-edit btn-outline-success) "
                    "Card เอกสารที่ 1 (fa-clipboard-check): โครงสร้างเหมือนกัน "
                    "Card ตรวจสอบและส่ง (fa-check-double): เมื่อเอกสารครบ แสดง alert-success 'พร้อมส่ง' "
                    "พร้อมปุ่ม 'ส่งคำร้อง' (fa-paper-plane btn-academic btn-lg) "
                    "เมื่อไม่ครบ แสดง alert-warning + ปุ่ม disabled "
                    "Modal ยืนยันก่อนส่ง: header สีเหลือง (warning-subtle), "
                    "ข้อความ 'กรุณานำส่งเอกสารฉบับจริง' ไอคอน fa-file-alt 3x สีน้ำเงิน"
                ),
                "components": [
                    ("Stepper (3 ขั้นตอน)", "Progress indicator", "เอกสารที่ 0 → เอกสารที่ 1 → ส่งคำร้อง"),
                    ("สถานะเอกสาร 0", "Badge ([YES]/⏳)", "แสดงว่ากรอกแล้วหรือยัง"),
                    ("ปุ่ม \"กรอกเอกสารที่ 0\"", "Button", "ไปหน้ากรอกเอกสารที่ 0 → GET .../document-0"),
                    ("สถานะเอกสาร 1", "Badge ([YES]/⏳)", "แสดงว่ากรอกแล้วหรือยัง"),
                    ("ปุ่ม \"กรอกเอกสารที่ 1\"", "Button", "ไปหน้ากรอกเอกสารที่ 1 → GET .../document-1"),
                    ("ปุ่ม \"ส่งคำร้อง\"", "Button (primary, disabled until ready)", "ส่งคำร้อง → POST .../submit"),
                ],
                "rules": [],
            },
            {
                "id": "UI-ACAD-U03",
                "title": "หน้ากรอกเอกสารที่ 0 (User)",
                "url": "/user/academic/request/{id}/document-0",
                "role": "ผู้ยื่นคำร้อง (ROLE_USER)",
                "use_cases": "UC-ACAD-U03",
                "screenshot": "screenshots/academic-user-document-0.png",
                "description": (
                    "หน้าจอใช้ base_academic layout มี card-academic แสดงหัว 'เอกสารที่ 0: บันทึกข้อความ' (fa-file-alt) "
                    "ข้อมูลรหัสคำร้องและชื่อผู้ยื่นสีเทา ฟอร์มแบ่งเป็นส่วน: "
                    "(1) ข้อมูลบันทึกข้อความ — เลขที่หนังสือ (text input), วันที่ (Thai date picker "
                    "มีปุ่ม fa-calendar-alt ทริกเกอร์ HTML5 date แล้วแปลงเป็นวันเดือนปีพุทธศักราช) "
                    "(2) ข้อมูลผู้ยื่น — dropdown คำนำหน้า (นาย/นาง/นางสาว), ช่องชื่อเต็ม, "
                    "radio ประเภทบุคลากร (พนักงานมหาวิทยาลัย/ข้าราชการ), dropdown ตำแหน่งปัจจุบัน "
                    "(3) ตำแหน่งที่ขอ — checkbox 2 ตัว (ผศ./รศ.) เลือกได้ทีละ 1 (JavaScript mutex) "
                    "(4) ชื่อเต็มสำหรับพิมพ์เอกสาร — text input พร้อมคำอธิบายสีเทา "
                    "เมื่อมีเอกสารที่สร้างแล้ว แสดงส่วน 'เอกสารที่สร้างแล้ว' พร้อมปุ่มดาวน์โหลด DOCX/PDF "
                    "ด้านล่างมีปุ่ม: 'ดูตัวอย่าง' (fa-eye), 'บันทึกและสร้างเอกสาร' (fa-paper-plane), "
                    "'กลับ' (fa-arrow-left) แสดง alert-success เมื่อบันทึกแบบร่างสำเร็จ"
                ),
                "components": [
                    ("ชื่อ-สกุล", "Text Input", "ชื่อเต็มผู้ยื่น"),
                    ("ตำแหน่งปัจจุบัน", "Text Input", "ตำแหน่งทางวิชาการปัจจุบัน"),
                    ("สังกัด", "Text Input", "สาขา/ภาควิชา"),
                    ("ตำแหน่งที่ขอ", "Radio (chk1/chk2/chk3)", "ผศ. / รศ. / ศ."),
                    ("ปุ่ม \"บันทึกแบบร่าง\"", "Button (secondary)", "บันทึก draft → POST (action=draft)"),
                    ("ปุ่ม \"บันทึกและสร้างเอกสาร\"", "Button (primary)", "สร้าง DOCX → POST (action=submit)"),
                    ("ปุ่ม \"ดูตัวอย่าง\"", "Button (outline)", "Preview DOCX"),
                ],
                "rules": [],
            },
            {
                "id": "UI-ACAD-U04",
                "title": "หน้ากรอกเอกสารที่ 1 (User)",
                "url": "/user/academic/request/{id}/document-1",
                "role": "ผู้ยื่นคำร้อง (ROLE_USER)",
                "use_cases": "UC-ACAD-U04",
                "screenshot": "screenshots/academic-user-document-1.png",
                "description": (
                    "หน้าจอใช้ base_academic layout มี card-academic แสดงหัว 'เอกสารที่ 1: แบบตรวจสอบเบื้องต้น' "
                    "(fa-clipboard-check) ส่วนข้อมูลผู้ยื่น: ช่องชื่อผู้ยื่น (pre-filled) "
                    "ตาราง Checklist (table-responsive): header สีเทาอ่อน (table-light) "
                    "คอลัมน์: ลำดับ (5%), ชื่อเอกสาร, จำนวน (8%), ผู้ยื่น checkbox (12%) "
                    "มี 5 รายการ checklist แต่ละรายการมีคำอธิบายเอกสารที่ต้องแนบ (ภาษาไทย) "
                    "checkbox ใช้ form-check พร้อม data-target attribute เชื่อมกับ hidden input "
                    "(เก็บค่า '✓' เมื่อเลือก, ว่างเมื่อไม่เลือก) "
                    "มี hidden fields 10 ตัว (chk_app_1-5, chk_off_1-5, text_1-5) "
                    "ด้านล่างมีปุ่ม: 'ดูตัวอย่าง' (fa-eye), 'บันทึกและสร้างเอกสาร' (fa-save btn-academic), "
                    "'กลับ' (fa-arrow-left) JavaScript โหลดข้อมูลเดิมจาก JSON + toggle checkbox"
                ),
                "components": [
                    ("รายการ Checklist", "Checkboxes", "เอกสารที่ต้องแนบ/ตรวจสอบ"),
                    ("ฟิลด์เพิ่มเติม", "Text Inputs", "ข้อมูลเสริมตามประเภท"),
                    ("ปุ่ม \"บันทึกแบบร่าง\"", "Button (secondary)", "บันทึก → POST (action=draft)"),
                    ("ปุ่ม \"บันทึกและสร้างเอกสาร\"", "Button (primary)", "สร้าง DOCX → POST (action=submit)"),
                ],
                "rules": [],
            },
            {
                "id": "UI-ACAD-U05",
                "title": "หน้ารายละเอียดคำร้อง (User)",
                "url": "/user/academic/request/{id}",
                "role": "ผู้ยื่นคำร้อง (ROLE_USER)",
                "use_cases": "UC-ACAD-U06, U07",
                "screenshot": "screenshots/academic-user-request-detail.png",
                "description": (
                    "หน้าจอใช้ base_academic layout ด้านบนมี card header แสดงรหัสคำร้อง + badge สถานะ (สีตาม StatusType) "
                    "วันที่ส่ง/อัพเดทล่าสุด Progress Tracker แนวนอน: วงกลม (36x36px) มีไอคอน fa-check (เสร็จ) "
                    "หรือตัวเลข (pending) พร้อมเส้นเชื่อม สีเขียว (#2e7d32)/สีส้ม (#f57f17)/สีแดง (#c62828) "
                    "Alert ตามสถานะ: alert-success 'ผ่านการประเมิน' (สีเขียว), "
                    "alert-warning 'ต้องแก้ไขเอกสาร' พร้อมฟอร์มอัปโหลดไฟล์แก้ไข, "
                    "alert-danger 'ไม่รับคำร้อง' (สีแดง), alert-info วันประชุม (fa-calendar-alt + เวลา/สถานที่) "
                    "Card รายการเอกสาร: ตารางแสดงเฉพาะเอกสาร type 0, 1, 8 ที่ผู้ยื่นเห็นได้ "
                    "คอลัมน์: ชื่อเอกสาร, วันที่สร้าง, ปุ่มดาวน์โหลด DOCX (fa-file-word) + PDF (fa-file-pdf) "
                    "Card ไทม์ไลน์สถานะ: แสดงประวัติเปลี่ยนสถานะตามลำดับเวลา badge + timestamp + หมายเหตุ"
                ),
                "components": [
                    ("Progress bar", "Stepper", "แสดงขั้นตอนสถานะ"),
                    ("ข้อมูลคำร้อง", "Card", "รหัส, วันที่ส่ง, สถานะปัจจุบัน"),
                    ("เอกสารที่เห็นได้", "Table", "เฉพาะเอกสาร type 0, 1, 8 พร้อมปุ่มดาวน์โหลด → GET .../download/{docId}"),
                    ("อัปโหลดเอกสารแก้ไข", "File Input + Button", "แสดงเมื่อสถานะ COMPLETED_REVISE → POST .../upload-revision/{id}"),
                    ("ไทม์ไลน์สถานะ", "Timeline", "ประวัติการเปลี่ยนสถานะ"),
                ],
                "rules": [
                    "ผู้ยื่นเห็นเฉพาะเอกสาร type 0 (บันทึกข้อความ), 1 (แบบตรวจสอบ), 8 (แจ้งผล)",
                    "ส่วนอัปโหลดเอกสารแก้ไข แสดงเฉพาะเมื่อสถานะ COMPLETED_REVISE",
                ],
            },
        ],
    },
    {
        "name": "3. ระบบคำร้องขอกำหนดตำแหน่ง (Position Request)",
        "screens": [
            {
                "id": "UI-POS-A01",
                "title": "หน้ารายการคำร้องตำแหน่ง (Admin)",
                "url": "/admin/position/requests",
                "role": "ผู้ดูแลระบบ (ROLE_ADMIN)",
                "use_cases": "UC-POS-A01",
                "screenshot": "screenshots/position-admin-request-list.png",
                "description": (
                    "หน้าจอใช้ base_academic layout หัวเรื่องมีไอคอน fa-university "
                    "แสดง empty state เมื่อไม่มีคำร้อง (ไอคอน fa-inbox ขนาดใหญ่ สีเทา + ข้อความ) "
                    "ตาราง responsive (table-hover, align-middle) header สีเข้ม (table-dark) "
                    "คอลัมน์: รหัสคำร้อง, ชื่อผู้ยื่น, ตำแหน่งที่ขอ, วันที่ส่ง (dd/MM/yyyy), "
                    "สถานะ (badge สีตาม StatusType), ปุ่ม 'ดู' (btn-outline-primary fa-eye)"
                ),
                "components": [
                    ("ตารางคำร้อง", "Table", "รหัส, ชื่อผู้ยื่น, ผลประเมิน Phase 1, สถานะ"),
                    ("ปุ่ม \"ดู\"", "Button/Link", "ไปหน้ารายละเอียด → GET /admin/position/request/{id}"),
                ],
                "rules": [],
            },
            {
                "id": "UI-POS-A02",
                "title": "หน้ารายละเอียดคำร้องตำแหน่ง (Admin)",
                "url": "/admin/position/request/{id}",
                "role": "ผู้ดูแลระบบ (ROLE_ADMIN)",
                "use_cases": "UC-POS-A02, A03, A04",
                "screenshot": "screenshots/position-admin-request-detail.png",
                "description": (
                    "หน้าจอใช้ base_academic layout ด้านบนมี card header แสดงรหัสคำร้อง + badge สถานะ "
                    "Progress Tracker: วงกลม (36x36px) พร้อมไอคอนและเส้นเชื่อม "
                    "สีเขียวอ่อน (#e8f5e9) = เสร็จ, สีเทา = pending "
                    "แบ่ง 2 คอลัมน์ (5/7): คอลัมน์ซ้าย — "
                    "(1) Card อัพเดทสถานะ: select dropdown + textarea หมายเหตุ + ปุ่มบันทึก "
                    "(2) Card ประวัติสถานะ: scrollable (max-height 350px) แสดง badge + timestamp + divider "
                    "(3) Card ข้อมูลคำร้อง: definition list (dt/dd) แสดงรายละเอียด "
                    "คอลัมน์ขวา — Card จัดการเอกสาร: ตารางแสดงเอกสาร, สถานะ, ผู้กรอก (Admin/Applicant), "
                    "ปุ่มกรอก (outline-primary), ปุ่มดาวน์โหลด DOCX (outline-success) เมื่อมีเอกสาร "
                    "badge สถานะ: 'เสร็จสิ้น' (bg-success) vs 'รอดำเนินการ' (bg-warning)"
                ),
                "components": [
                    ("Progress bar", "Stepper", "ขั้นตอนสถานะ (getProgressSteps)"),
                    ("ข้อมูลผู้ยื่น", "Card", "ชื่อ, ผลประเมิน Phase 1 ที่อ้างอิง"),
                    ("อัพเดทสถานะ", "Form (dropdown + textarea)", "เลือกสถานะ + หมายเหตุ → POST .../status"),
                    ("ตารางเอกสาร", "Table", "ประเภท, ชื่อ, ผู้กรอก (Admin/Applicant), สถานะ"),
                    ("ปุ่ม \"กรอกเอกสาร\" (5, 8)", "Button", "ไปฟอร์มกรอก Admin → GET .../document/{5|8}"),
                    ("ไทม์ไลน์สถานะ", "Timeline", "ประวัติการเปลี่ยนสถานะ"),
                ],
                "rules": [],
            },
            {
                "id": "UI-POS-U01",
                "title": "หน้าแดชบอร์ดตำแหน่ง (User)",
                "url": "/user/position/dashboard",
                "role": "ผู้ยื่นคำร้อง (ROLE_USER)",
                "use_cases": "UC-POS-U01",
                "screenshot": "screenshots/position-user-dashboard.png",
                "description": (
                    "หน้าจอใช้ base_academic layout ด้านบนมีหัวเรื่อง + ปุ่ม 'สร้างคำร้องใหม่' (conditional disabled) "
                    "Expiry Countdown Card: ขอบซ้ายหนา 5px สีเปลี่ยนตามความเร่งด่วน (เขียว → เหลือง → ส้ม → แดง) "
                    "ไอคอน fa-hourglass-half, วันหมดอายุผลประเมิน, ตัวเลขนับถอยหลังขนาดใหญ่ (1.4rem, letter-spacing) "
                    "Progress bar (สูง 8px) gradient พร้อมข้อความเตือน (emoji + ภาษาไทย) "
                    "JavaScript setInterval อัพเดททุก 1000ms "
                    "Draft Card: ขอบเหลือง (border-warning) มี badge + ปุ่ม 'ดำเนินการต่อ' "
                    "Empty state: fa-folder-open 3x + ข้อความ "
                    "Request Cards: card-academic แสดงรหัส, วันที่ส่ง, ตำแหน่งที่ขอ (small muted), "
                    "badge สถานะ, Progress Tracker (วงกลม + เส้นเชื่อม)"
                ),
                "components": [
                    ("Countdown หมดอายุ", "Card (warning)", "แสดงวันหมดอายุผลประเมิน Phase 1"),
                    ("Draft card", "Card (highlight)", "แสดง draft ที่ค้างอยู่ → คลิก → request detail"),
                    ("ปุ่ม \"สร้างคำร้องใหม่\"", "Button (primary)", "เริ่มสร้างคำร้องตำแหน่ง → GET /user/position/new-request"),
                    ("ตารางคำร้อง", "Table", "รหัส, วันที่ส่ง, สถานะ, ปุ่มดู"),
                ],
                "rules": [
                    "ซ่อนปุ่ม \"สร้างคำร้องใหม่\" เมื่อ hasActiveRequest = true",
                    "Countdown แสดงเฉพาะเมื่อมี evaluationExpiryDate",
                ],
            },
            {
                "id": "UI-POS-U02",
                "title": "หน้าสร้างคำร้องตำแหน่งใหม่ (User)",
                "url": "/user/position/new-request",
                "role": "ผู้ยื่นคำร้อง (ROLE_USER)",
                "use_cases": "UC-POS-U02",
                "screenshot": "screenshots/position-user-new-request.png",
                "description": (
                    "หน้าจอใช้ base_academic layout หัวเรื่องมีไอคอน fa-university + subtitle "
                    "แสดง card เลือกผลประเมิน: card-academic แต่ละผลประเมินมี fa-check-circle, "
                    "วันที่ส่ง, badge สถานะ (bg-success), ปุ่ม 'เลือกและสร้างคำร้อง' (btn-academic) "
                    "แสดง alert-info (สีฟ้า) อธิบายเงื่อนไข "
                    "แสดง alert-warning (สีเหลือง) เมื่อไม่มีผลประเมินที่ eligible "
                    "ปุ่ม 'กลับ' (outline-secondary)"
                ),
                "components": [
                    ("รายการผลประเมิน", "Radio/Card list", "เลือกผลประเมิน Phase 1 ที่ eligible"),
                    ("ข้อมูลผลประเมิน", "Card detail", "แสดงรายละเอียดผลประเมินที่เลือก"),
                    ("ปุ่ม \"สร้างคำร้อง\"", "Button (primary)", "สร้าง draft linked to evaluation → POST .../create-request"),
                ],
                "rules": [
                    "ถ้าไม่มีผลประเมินที่ eligible แสดงข้อความ \"ไม่มีผลประเมินที่ใช้ได้\"",
                    "ปุ่ม disabled ถ้ายังไม่เลือกผลประเมิน",
                ],
            },
            {
                "id": "UI-POS-U03",
                "title": "หน้ารายละเอียดคำร้องตำแหน่ง (User)",
                "url": "/user/position/request/{id}",
                "role": "ผู้ยื่นคำร้อง (ROLE_USER)",
                "use_cases": "UC-POS-U03, U04, U05",
                "screenshot": "screenshots/position-user-request-detail.png",
                "description": (
                    "หน้าจอใช้ base_academic layout ด้านบนมี card header แสดงรหัสคำร้อง + badge สถานะ "
                    "Step Indicator: วงกลม (36x36px) พร้อมเลข/ไอคอน fa-check, เส้นเชื่อม "
                    "สถานะ completed = พื้นเขียวอ่อน (#e8f5e9) สีเขียว (#2e7d32), pending = พื้นเทา "
                    "Document Cards แต่ละประเภท (card-academic): header แสดงเลข + ชื่อเอกสาร, "
                    "badge สถานะ 'เสร็จสิ้น' (สีเขียว) / 'รอดำเนินการ' (สีเหลือง), "
                    "ปุ่ม 'กรอกเอกสาร' (btn-academic) หรือ 'แก้ไข' (btn-outline-success) "
                    "ส่วนส่งคำร้อง (conditional DRAFT): alert-success เมื่อเอกสารครบ + ปุ่มส่ง (fa-paper-plane btn-lg), "
                    "alert-warning เมื่อไม่ครบ (แสดงจำนวน X/Y) + ปุ่ม disabled "
                    "Modal ยืนยัน: header สีเหลือง (warning-subtle), ไอคอน fa-university ตรงกลาง "
                    "Card ประวัติสถานะ (conditional): timeline badge + วันที่ + หมายเหตุ"
                ),
                "components": [
                    ("ข้อมูลคำร้อง", "Card", "รหัส, สถานะ, ผลประเมิน Phase 1"),
                    ("ตารางเอกสาร (APPLICANT_DOCS)", "Table", "ประเภท, ชื่อ, สถานะ ([YES]/⏳), ปุ่มกรอก"),
                    ("ปุ่ม \"กรอกเอกสาร\"", "Button ต่อแถว", "ไปฟอร์มกรอกเอกสาร → GET .../document/{type}"),
                    ("ปุ่ม \"ส่งคำร้อง\"", "Button (primary, disabled until all done)", "ส่งคำร้อง → POST .../submit"),
                    ("ไทม์ไลน์สถานะ", "Timeline", "ประวัติการเปลี่ยนสถานะ"),
                ],
                "rules": [
                    "แสดงเฉพาะเอกสาร APPLICANT_DOCS (1,2,3,4,6,7,9)",
                    "ปุ่ม \"ส่งคำร้อง\" enabled เฉพาะเมื่อเอกสารครบทุกประเภท + สถานะ DRAFT",
                    "เอกสารที่กรอกแล้วแสดง [YES] เอกสารที่ยังไม่กรอกแสดง ⏳",
                ],
            },
        ],
    },
    {
        "name": "4. ระบบคำร้องทั่วไป (Petition)",
        "screens": [
            {
                "id": "UI-PET-01",
                "title": "หน้ายื่นคำร้องใหม่",
                "url": "/petitions/new",
                "role": "ผู้ยื่นคำร้อง (ROLE_USER)",
                "use_cases": "UC-PET-01",
                "screenshot": "screenshots/petition-user-new.png",
                "description": (
                    "หน้าจอใช้ base_academic layout คอลัมน์กลาง (centered) "
                    "หัวเรื่อง h3 พร้อมไอคอน fa-plus-circle และ subtitle "
                    "Card ฟอร์ม (card-academic shadow-sm): header พื้นขาว + ไอคอนสีน้ำเงิน "
                    "ช่องหัวข้อคำร้อง (text input, maxlength 255) "
                    "ช่องรายละเอียด (textarea 6 บรรทัด) ทั้งคู่ required "
                    "alert เตือนฟิลด์จำเป็น (border อ่อน, shadow-sm, ไอคอน fa-asterisk) "
                    "ด้านล่างมีปุ่ม 'ยกเลิก' (outline-secondary) และ 'ยื่นคำร้อง' (primary) "
                    "กล่อง info (alert border-0 shadow-sm) ไอคอน fa-lightbulb ให้คำแนะนำ "
                    "แสดง validation error เป็น alert เมื่อมี"
                ),
                "components": [
                    ("หัวข้อคำร้อง", "Text Input (required)", "กรอกหัวข้อคำร้อง"),
                    ("รายละเอียด", "Textarea (required)", "กรอกรายละเอียดคำร้อง"),
                    ("ปุ่ม \"ยื่นคำร้อง\"", "Button (primary)", "ส่งแบบฟอร์ม → POST /petitions/create"),
                    ("ข้อความแจ้งเตือน", "Alert", "แสดงเมื่อมี validation error"),
                ],
                "rules": [
                    "หัวข้อและรายละเอียดต้องไม่ว่าง (validated ด้วย @Valid)",
                    "หากมีคำร้องที่ active อยู่ จะ redirect ไปหน้า cannot_submit แทน",
                ],
            },
            {
                "id": "UI-PET-02",
                "title": "หน้าไม่สามารถยื่นคำร้องได้",
                "url": "/petitions/new (redirect ภายใน)",
                "role": "ผู้ยื่นคำร้อง (ROLE_USER)",
                "use_cases": "UC-PET-01 (AF-1)",
                "screenshot": "screenshots/petition-user-cannot-submit.png",
                "description": (
                    "หน้าจอใช้ base_academic layout คอลัมน์กลาง "
                    "Alert ขนาดใหญ่ (warning border-0 shadow-sm): ไอคอน fa-exclamation-triangle 3x ด้านซ้าย "
                    "ข้อความ 'ไม่สามารถยื่นคำร้องได้' ด้านขวา "
                    "Card แสดงคำร้องที่มีอยู่: หัวข้อ + badge สถานะ (สีตาม StatusType) "
                    "timeline อ้างอิงสถานะ + alert-info แสดง note "
                    "ปุ่ม 'กลับหน้าหลัก' (outline-secondary)"
                ),
                "components": [
                    ("ข้อความแจ้งเตือน", "Alert (warning)", "แสดงเหตุผลที่ยื่นไม่ได้"),
                    ("ข้อมูลคำร้องที่มีอยู่", "Card", "แสดงหัวข้อ สถานะ วันที่ของคำร้อง active"),
                    ("ลิงก์ \"ดูรายละเอียด\"", "Link", "ไปยังหน้ารายละเอียดคำร้องที่มีอยู่ → GET /petitions/{id}"),
                ],
                "rules": [],
            },
            {
                "id": "UI-PET-03",
                "title": "หน้ารายละเอียดคำร้อง",
                "url": "/petitions/{id}",
                "role": "ผู้ยื่นคำร้อง / ผู้ดูแลระบบ",
                "use_cases": "UC-PET-02, UC-PET-04",
                "screenshot": "screenshots/petition-view.png",
                "description": (
                    "หน้าจอใช้ base_academic layout หัวเรื่องมีไอคอน fa-file-alt "
                    "Card รายละเอียด (card-academic): แสดงข้อมูล 2 คอลัมน์ (ผู้ยื่น, วันที่ส่ง) "
                    "รายละเอียดเต็มความกว้าง "
                    "Card ไทม์ไลน์สถานะ: custom CSS .status-timeline แสดงรายการแต่ละสถานะ "
                    "มี .status-badge span พร้อมไอคอน + วันที่ + หมายเหตุ (conditional) "
                    "แสดง alert-success เมื่อยื่นสำเร็จ (flash attribute)"
                ),
                "components": [
                    ("หัวข้อคำร้อง", "Text (h2)", "แสดงหัวข้อคำร้อง"),
                    ("สถานะปัจจุบัน", "Badge", "แสดงสถานะด้วยสีตาม StatusType"),
                    ("รายละเอียดคำร้อง", "Text block", "แสดงรายละเอียดเต็ม"),
                    ("ไทม์ไลน์สถานะ", "Timeline/List", "แสดงประวัติการเปลี่ยนสถานะตามลำดับเวลา"),
                    ("ข้อความแจ้งเตือน", "Alert (success)", "แสดงเมื่อยื่นคำร้องสำเร็จ (flash attribute)"),
                ],
                "rules": [],
            },
            {
                "id": "UI-PET-04",
                "title": "หน้ารายการคำร้องของฉัน",
                "url": "/petitions/my-petitions",
                "role": "ผู้ยื่นคำร้อง (ROLE_USER)",
                "use_cases": "UC-PET-03",
                "screenshot": "screenshots/petition-user-list.png",
                "description": (
                    "หน้าจอใช้ base_academic layout หัวเรื่อง fa-list-alt + ปุ่ม 'ยื่นคำร้องใหม่' (primary) "
                    "แสดงเป็น Card list (ไม่ใช่ตาราง) แต่ละ card (card-academic mb-3) แบ่ง 2 คอลัมน์ (8/4): "
                    "ซ้าย: ไอคอน fa-file + หัวข้อคำร้อง, รายละเอียด (ตัด truncate), วันที่สร้าง "
                    "ขวา: badge สถานะ (สีตาม StatusType) + ปุ่ม 'ดูรายละเอียด' (outline-primary) "
                    "Empty state: ไอคอน fa-inbox 5x สีเทา + ข้อความ 'ยังไม่มีคำร้อง' "
                    "พร้อมปุ่ม 'ยื่นคำร้องใหม่'"
                ),
                "components": [
                    ("ตารางรายการคำร้อง", "Table", "แสดง: หัวข้อ, วันที่ยื่น, สถานะ"),
                    ("ปุ่ม \"ดู\" ในแต่ละแถว", "Link/Button", "ไปหน้ารายละเอียด → GET /petitions/{id}"),
                    ("ปุ่ม \"ยื่นคำร้องใหม่\"", "Button (primary)", "ไปหน้าสร้างคำร้อง → GET /petitions/new"),
                    ("ข้อความเมื่อไม่มีคำร้อง", "Empty state", "แสดงเมื่อยังไม่เคยยื่นคำร้อง"),
                ],
                "rules": [],
            },
        ],
    },
    {
        "name": "5. ระบบจัดการบุคลากร (Staff Management)",
        "screens": [
            {
                "id": "UI-STAFF-01",
                "title": "หน้ารายชื่อบุคลากร",
                "url": "/admin/academic/staff",
                "role": "ผู้ดูแลระบบ (ROLE_ADMIN)",
                "use_cases": "UC-STAFF-01, UC-STAFF-04",
                "screenshot": "screenshots/staff-admin-list.png",
                "description": (
                    "หน้าจอใช้ base_academic layout หัวเรื่องไอคอน fa-users + ปุ่ม 'เพิ่มบุคลากร' (btn-academic) "
                    "แสดง alert success/error (dismissible) ตามผลลัพธ์ "
                    "Empty state: ไอคอน fa-user-plus 3x + ข้อความ "
                    "ตาราง (table-hover, align-middle) คอลัมน์: ลำดับ, ตำแหน่งทางวิชาการ, ชื่อเต็ม, "
                    "ประเภทบุคลากร, สาขา, บทบาท (badge สีตามบทบาท: "
                    "DEAN=danger แดง, HEAD=primary น้ำเงิน, COMMITTEE=success เขียว, "
                    "HR=warning เหลือง, GENERAL=secondary เทา), "
                    "ปุ่ม 'แก้ไข' (btn-warning fa-edit) + 'ลบ' (btn-danger fa-trash) "
                    "การลบมี confirm dialog ก่อนดำเนินการ"
                ),
                "components": [
                    ("ปุ่ม \"เพิ่มบุคลากร\"", "Button (primary)", "ไปหน้าเพิ่มบุคลากร → GET /admin/academic/staff/add"),
                    ("ตารางบุคลากร", "Table", "แสดง: ชื่อเต็ม, ตำแหน่งทางวิชาการ, ประเภท, สาขา, บทบาท"),
                    ("ปุ่ม \"แก้ไข\" ในแต่ละแถว", "Button (warning)", "ไปหน้าแก้ไข → GET /admin/academic/staff/edit/{id}"),
                    ("ปุ่ม \"ลบ\" ในแต่ละแถว", "Button (danger)", "ลบบุคลากร (confirm ก่อน) → POST /admin/academic/staff/delete/{id}"),
                    ("ข้อความแจ้งเตือน", "Alert (success)", "แสดงเมื่อ added/updated/deleted สำเร็จ"),
                ],
                "rules": [],
            },
            {
                "id": "UI-STAFF-02",
                "title": "หน้าเพิ่ม/แก้ไขบุคลากร",
                "url": "/admin/academic/staff/add หรือ /admin/academic/staff/edit/{id}",
                "role": "ผู้ดูแลระบบ (ROLE_ADMIN)",
                "use_cases": "UC-STAFF-02, UC-STAFF-03",
                "screenshot": "screenshots/staff-admin-form.png",
                "description": (
                    "หน้าจอใช้ base_academic layout card กลางหน้า (max-width 700px) "
                    "header: ไอคอน fa-user-plus (เพิ่ม) หรือ fa-user-edit (แก้ไข) + หัวเรื่อง "
                    "ฟอร์ม: dropdown ตำแหน่งทางวิชาการ (8 ตัวเลือก: อาจารย์ ถึง ศาสตราจารย์), "
                    "ช่องชื่อเต็ม (text input), dropdown ประเภทบุคลากร (3 ตัวเลือก), "
                    "ช่องสาขา (text input), dropdown บทบาท (5 ตัวเลือกพร้อมคำอธิบาย) "
                    "หน้าแก้ไข pre-fill ข้อมูลจากฐานข้อมูล "
                    "ปุ่ม 'บันทึก' (btn-academic) + 'กลับ' (outline-secondary)"
                ),
                "components": [
                    ("ชื่อเต็ม (fullName)", "Text Input (required)", "ชื่อ-นามสกุล"),
                    ("ตำแหน่งทางวิชาการ (academicTitle)", "Text Input (required)", "เช่น ผศ.ดร., รศ.ดร."),
                    ("ประเภทบุคลากร (staffType)", "Select/Text (required)", "เช่น ข้าราชการ, พนักงานมหาวิทยาลัย"),
                    ("สาขา (department)", "Text Input (optional)", "สาขาวิชาที่สังกัด"),
                    ("บทบาท (staffRole)", "Select (required)", "คณบดี, หัวหน้าสาขา, กรรมการ, HR"),
                    ("ปุ่ม \"บันทึก\"", "Button (primary)", "บันทึกข้อมูล → POST"),
                    ("ปุ่ม \"ยกเลิก\"", "Button (secondary)", "กลับหน้ารายชื่อ → GET /admin/academic/staff"),
                ],
                "rules": [
                    "หน้าแก้ไขจะ pre-fill ข้อมูลบุคลากรที่มีอยู่",
                    "ฟิลด์ department เป็น optional (required = false)",
                    "ใช้ฟอร์มเดียวกันสำหรับทั้งเพิ่มและแก้ไข (แยกโดย URL)",
                ],
            },
        ],
    },
    {
        "name": "6. ระบบจัดการไฟล์ (File Management)",
        "screens": [
            {
                "id": "UI-FILE-01",
                "title": "หน้ารายการไฟล์ / ถังขยะ",
                "url": "/admin/file-manager (ไฟล์ทั้งหมด) หรือ /admin/file-manager/trash (ถังขยะ)",
                "role": "ผู้ดูแลระบบ (ROLE_ADMIN)",
                "use_cases": "UC-FILE-01 ~ UC-FILE-07",
                "screenshot": "screenshots/file-admin-manager.png",
                "description": (
                    "หน้าจอใช้ base_academic layout (container-fluid) หัวเรื่อง fa-folder-open + subtitle "
                    "Summary Cards 3 ใบ: วงกลมไอคอนสีจาง (10% opacity) + ตัวเลขสถิติ — "
                    "จำนวนไฟล์ทั้งหมด (สีน้ำเงิน), ขนาดรวม (สีเขียว), จำนวนในถังขยะ (สีแดง) "
                    "Tab Buttons: 'ไฟล์ทั้งหมด' (active=primary, inactive=outline-secondary) + "
                    "'ถังขยะ' (พร้อม badge จำนวน) + 'ล้างถังขยะ' (outline-danger, conditional) "
                    "Search/Filter: ช่องค้นหา (fa-search), dropdown ประเภทไฟล์ (DOCX/PDF/JPG/DOCUMENT/ATTACHMENT), "
                    "dropdown คำร้อง (dynamically populated) "
                    "ตาราง (border-0 shadow-sm responsive) คอลัมน์: ลำดับ, ชื่อไฟล์ (ไอคอน: "
                    "fa-file-word สีน้ำเงิน, fa-file-pdf สีแดง, fa-file-image สีเขียว), "
                    "ประเภท (badge), ขนาด (B/KB/MB/GB), รหัสคำร้อง, ผู้ยื่น, เอกสาร, วันที่, "
                    "ปุ่ม: ดาวน์โหลด + ไปคำร้อง + ลบ (โหมดไฟล์) / กู้คืน + ลบถาวร (โหมดถังขยะ) "
                    "Modal ยืนยัน (rounded 16px, centered) + Toast notification (fixed bottom-right)"
                ),
                "components": [
                    ("สรุปจำนวนไฟล์", "Card/Stats", "แสดง: จำนวนไฟล์ทั้งหมด, ขนาดรวม, จำนวนในถังขยะ"),
                    ("แท็บ \"ไฟล์ทั้งหมด\"", "Tab", "สลับดูไฟล์ active → GET /admin/file-manager"),
                    ("แท็บ \"ถังขยะ\"", "Tab (badge)", "สลับดูถังขยะ (แสดงจำนวน) → GET /admin/file-manager/trash"),
                    ("ตารางไฟล์", "Table", "แสดง: ชื่อ, ประเภท, ขนาด, คำร้องที่เกี่ยวข้อง, วันที่"),
                    ("ปุ่ม \"ดาวน์โหลด\"", "Button (info)", "ดาวน์โหลดไฟล์ → GET /admin/file-manager/download/{type}/{id}"),
                    ("ปุ่ม \"ลบ\"", "Button (danger)", "ย้ายไปถังขยะ (AJAX) → POST /admin/file-manager/delete/{type}/{id}"),
                    ("ปุ่ม \"กู้คืน\"", "Button (success)", "กู้คืนจากถังขยะ (แสดงในโหมดถังขยะ) → POST .../restore/{type}/{id}"),
                    ("ปุ่ม \"ลบถาวร\"", "Button (danger)", "ลบไฟล์ถาวร (แสดงในโหมดถังขยะ) → POST .../permanent-delete/{type}/{id}"),
                    ("ปุ่ม \"ล้างถังขยะ\"", "Button (danger)", "ลบทั้งหมดในถังขยะ (แสดงในโหมดถังขยะ) → POST .../empty-trash"),
                ],
                "rules": [
                    "โหมด \"ไฟล์ทั้งหมด\" (viewMode = \"files\"): แสดงปุ่ม ดาวน์โหลด + ลบ",
                    "โหมด \"ถังขยะ\" (viewMode = \"trash\"): แสดงปุ่ม กู้คืน + ลบถาวร + ล้างถังขยะ",
                    "การลบ/กู้คืน/ลบถาวร ใช้ AJAX (ไม่ reload หน้า)",
                    "ขนาดไฟล์แสดงในรูปแบบ B / KB / MB / GB",
                ],
            },
        ],
    },
    {
        "name": "7. ระบบจัดการผู้ใช้ (User Management)",
        "screens": [
            {
                "id": "UI-USER-01",
                "title": "หน้ารายชื่อผู้ใช้/แอดมิน",
                "url": "/admin/users?type=1 (ผู้ใช้) หรือ ?type=2 (แอดมิน)",
                "role": "ผู้ดูแลระบบ (ROLE_ADMIN)",
                "use_cases": "UC-USER-01, UC-USER-05, UC-USER-06, UC-USER-07, UC-USER-08",
                "screenshot": "screenshots/user-admin-list.png",
                "description": (
                    "หน้าจอใช้ base_academic layout (container-fluid) Card header (card-sh) "
                    "หัวเรื่องตรงกลาง (fs-4): 'รายชื่อผู้ใช้' หรือ 'รายชื่อแอดมิน' "
                    "ข้อความ success/error (สีเขียว/แดง ตัวหนา) "
                    "ตาราง: คอลัมน์ ลำดับ, [รหัสผู้ยื่น ถ้า type=1], รูปโปรไฟล์ (วงกลม 70x70px, "
                    "fallback default.png), ชื่อ, อีเมล, เบอร์โทร, ตำแหน่งวิชาการ, "
                    "[Toggle แจ้งเตือนอีเมล ถ้า type=2], Toggle สถานะ (form-switch ขนาด 3em x 1.5em "
                    "เปิด=text-success 'เปิด' / ปิด=text-secondary 'ปิด'), "
                    "ปุ่ม 'แก้ไข' (btn-warning fa-edit) + 'ลบ' (btn-danger fa-trash) "
                    "ปุ่ม 'เพิ่มแอดมิน' (primary) แสดงเฉพาะ type=2"
                ),
                "components": [
                    ("ตารางผู้ใช้", "Table", "แสดง: รูป, ชื่อ, อีเมล, สถานะ, แจ้งเตือน"),
                    ("Toggle สถานะ", "Switch", "เปิด/ปิดบัญชี → GET /admin/updateSts"),
                    ("Toggle แจ้งเตือน", "Switch", "เปิด/ปิดการแจ้งเตือนอีเมล → GET /admin/updateEmailNotification"),
                    ("ปุ่ม \"แก้ไข\"", "Button (warning)", "ไปหน้าแก้ไข → GET /admin/edit-user?id={id}"),
                    ("ปุ่ม \"ลบ\"", "Button (danger)", "ลบบัญชี → GET /admin/delete-user?id={id}"),
                    ("ปุ่ม \"เพิ่มแอดมิน\"", "Button (primary)", "ไปหน้าเพิ่มแอดมิน (เฉพาะ type=2) → GET /admin/add-admin"),
                ],
                "rules": [],
            },
            {
                "id": "UI-USER-02",
                "title": "หน้าเพิ่มแอดมิน",
                "url": "/admin/add-admin",
                "role": "ผู้ดูแลระบบ (ROLE_ADMIN)",
                "use_cases": "UC-USER-02",
                "screenshot": "screenshots/user-admin-add.png",
                "description": (
                    "หน้าจอใช้ base_academic layout (container-fluid) คอลัมน์กลาง (col-lg-8) "
                    "Card header gradient (linear-gradient 135deg #1a237e → #0d47a1) ตัวขาว "
                    "ไอคอน fa-user-plus + หัวเรื่อง "
                    "ฟอร์มแบ่ง 3 ส่วน (h6 + ไอคอน + hr divider): "
                    "(1) ข้อมูลส่วนตัว — dropdown คำนำหน้า (4 col) + ชื่อ (8 col), "
                    "dropdown ตำแหน่งวิชาการ + ช่องเบอร์โทร (6/6 split) "
                    "(2) ข้อมูลบัญชี — อีเมล (8 col) + dropdown บทบาท (4 col) "
                    "(3) รูปโปรไฟล์ — file input + ข้อความเล็กสีเทา (JPG/PNG/GIF ≤5MB) "
                    "ปุ่ม 'บันทึก' gradient primary (rounded 10px) "
                    "alert-info (#e8eaf6) แจ้งว่ารหัสผ่านตั้งผ่าน first-login"
                ),
                "components": [
                    ("ชื่อ (name)", "Text Input (required)", "ชื่อแอดมิน"),
                    ("อีเมล (email)", "Email Input (required)", "อีเมลแอดมิน"),
                    ("รหัสผ่าน (password)", "Password Input (required)", "รหัสผ่าน"),
                    ("รูปโปรไฟล์ (img)", "File Input (optional)", "อัปโหลดรูปภาพ (JPG, PNG, GIF สูงสุด 5MB)"),
                    ("ปุ่ม \"บันทึก\"", "Button (primary)", "สร้างบัญชี → POST /admin/save-admin"),
                ],
                "rules": [],
            },
            {
                "id": "UI-USER-03",
                "title": "หน้าแก้ไขผู้ใช้/แอดมิน",
                "url": "/admin/edit-user?id={id} หรือ /admin/edit-admin?id={id}",
                "role": "ผู้ดูแลระบบ (ROLE_ADMIN)",
                "use_cases": "UC-USER-03, UC-USER-04",
                "screenshot": "screenshots/user-admin-edit.png",
                "description": (
                    "หน้าจอโครงสร้างเหมือนหน้าเพิ่มแอดมิน แต่ข้อมูลถูก pre-fill จากฐานข้อมูล "
                    "ส่วนรูปโปรไฟล์: รูปวงกลม thumbnail (80x80px) แสดงรูปปัจจุบัน + file input ข้างๆ "
                    "อัพเดทรูปแบบ real-time ผ่าน AJAX (POST /admin/update-profile-image) "
                    "แสดง success/error message แบบ inline "
                    "ปุ่ม 'บันทึก' (gradient primary) + 'ยกเลิก' (secondary) "
                    "JavaScript validation: email regex, file size ≤5MB, file type (JPG/PNG/GIF)"
                ),
                "components": [
                    ("ชื่อ (name)", "Text Input (required, pre-filled)", "ชื่อผู้ใช้"),
                    ("อีเมล (email)", "Email Input (required, pre-filled)", "อีเมล"),
                    ("รูปโปรไฟล์ (img)", "File Input + Preview (optional)", "เปลี่ยนรูปโปรไฟล์ → AJAX upload"),
                    ("ปุ่ม \"บันทึก\"", "Button (primary)", "อัพเดทข้อมูล → POST /admin/update-user"),
                ],
                "rules": [
                    "ข้อมูลปัจจุบันถูก pre-fill ในฟอร์ม",
                    "รูปโปรไฟล์แสดง preview ของรูปปัจจุบัน",
                    "Validation: อีเมลรูปแบบถูกต้อง, ชื่อไม่ว่าง, อีเมลไม่ซ้ำ, รูปภาพ ≤5MB (JPG/PNG/GIF)",
                ],
            },
            {
                "id": "UI-USER-04",
                "title": "หน้าประวัติการใช้งาน (Activity Logs)",
                "url": "/admin/activity-logs",
                "role": "ผู้ดูแลระบบ (ROLE_ADMIN)",
                "use_cases": "UC-USER-09",
                "screenshot": "screenshots/user-admin-activity-logs.png",
                "description": (
                    "หน้าจอใช้ base_academic layout (container-fluid) "
                    "Summary Cards 4 ใบ: วงกลมไอคอนสีจาง (10% opacity) + ตัวเลข — "
                    "กิจกรรมทั้งหมด (primary), สร้างบัญชี (success), อัพเดทสถานะ (warning), "
                    "สร้างเอกสาร (info) "
                    "หัวเรื่อง + ปุ่ม 'ส่งออก CSV' (outline-success) "
                    "Search/Filter ฟอร์ม (4 ช่อง): ค้นหาข้อความ, dropdown Action, "
                    "วันที่เริ่มต้น (date input), วันที่สิ้นสุด (date input) "
                    "ปุ่มค้นหา (primary) + ปุ่มรีเซ็ต (outline-secondary) "
                    "ตาราง (font-size 0.85rem) คอลัมน์: ลำดับ, วันเวลา, ชื่อแอดมิน/อีเมล, "
                    "การกระทำ (badge สีตามประเภท: CREATE=success, EDIT/UPDATE=warning, "
                    "DELETE=danger, GENERATE=primary, PAGE_VIEW=secondary), รายละเอียด, IP, Resource, User Agent "
                    "Pagination อัจฉริยะ: แสดงหน้าแรก 3, หน้าสุดท้าย 3, หน้าปัจจุบัน ±1 "
                    "ลูกศร Previous/Next + ตัวเลขหน้า (20 รายการต่อหน้า)"
                ),
                "components": [
                    ("Summary Cards", "Cards (3)", "แสดงจำนวน: สร้างบัญชี, อัพเดทสถานะ, สร้างเอกสาร"),
                    ("ช่องค้นหา", "Text Input", "ค้นหาตามชื่อ/อีเมล/รายละเอียด"),
                    ("ตัวกรอง Action", "Select", "กรองตามประเภทการกระทำ"),
                    ("ตัวกรอง วันที่เริ่ม", "Date Input", "กรองวันที่เริ่มต้น"),
                    ("ตัวกรอง วันที่สิ้นสุด", "Date Input", "กรองวันที่สิ้นสุด"),
                    ("ตาราง Logs", "Table (paginated)", "แสดง: วันเวลา, ผู้ดำเนินการ, อีเมล, การกระทำ, รายละเอียด, IP"),
                    ("Pagination", "Pagination", "แบ่งหน้า 20 รายการต่อหน้า"),
                    ("ปุ่ม \"ส่งออก CSV\"", "Button (secondary)", "ดาวน์โหลด CSV → GET /admin/activity-logs/export"),
                ],
                "rules": [],
            },
        ],
    },
    {
        "name": "8. ระบบตั้งค่าและแจ้งเตือน (Settings & Notifications)",
        "screens": [
            {
                "id": "UI-SET-01",
                "title": "หน้าตั้งค่า",
                "url": "/user/academic/settings หรือ /admin/academic/settings",
                "role": "ผู้ยื่นคำร้อง / ผู้ดูแลระบบ",
                "use_cases": "UC-SET-01, UC-SET-02",
                "screenshot": "screenshots/settings-page.png",
                "description": (
                    "หน้าจอใช้ base_academic layout คอลัมน์กลาง (col-lg-8) "
                    "ส่วนที่ 1 — Card ตั้งค่าทั่วไป (fa-sliders-h): "
                    "Toggle switch 2 ตัว (ขนาด 3em x 1.5em): "
                    "Auto-draft (ไอคอน fa-save) + การแจ้งเตือนอีเมล (ไอคอน fa-envelope) "
                    "ส่วนที่ 2 — Card แจ้งเตือนก่อนหมดอายุ (fa-bell สีเหลือง): "
                    "alert-info อธิบายเงื่อนไข, cards 4 ใบในตาราง 2x2 "
                    "แต่ละ card มีขอบซ้ายหนา 4px สีต่างกัน: "
                    "6 เดือน (เขียว #4caf50), 3 เดือน (ส้ม #ff9800), "
                    "1 เดือน (แดง #f44336), 1 สัปดาห์ (ม่วง #9c27b0) "
                    "แต่ละ card มี toggle switch + ไอคอน + คำอธิบาย "
                    "ปุ่ม 'บันทึก' (primary btn-lg) + 'กลับ' (outline-secondary btn-lg)"
                ),
                "components": [
                    ("Auto-Draft", "Toggle Switch", "เปิด/ปิดบันทึกแบบร่างอัตโนมัติ"),
                    ("การแจ้งเตือนอีเมล", "Toggle Switch", "เปิด/ปิดการแจ้งเตือนทางอีเมล"),
                    ("แจ้งเตือน 6 เดือน", "Checkbox", "แจ้งเตือนก่อนหมดอายุ 6 เดือน"),
                    ("แจ้งเตือน 3 เดือน", "Checkbox", "แจ้งเตือนก่อนหมดอายุ 3 เดือน"),
                    ("แจ้งเตือน 1 เดือน", "Checkbox", "แจ้งเตือนก่อนหมดอายุ 1 เดือน"),
                    ("แจ้งเตือน 1 สัปดาห์", "Checkbox", "แจ้งเตือนก่อนหมดอายุ 1 สัปดาห์"),
                    ("ปุ่ม \"บันทึก\"", "Button (primary)", "บันทึกการตั้งค่า → POST .../settings"),
                ],
                "rules": [
                    "หน้า settings ใช้ template เดียวกัน (academic/settings.html) ทั้ง user และ admin",
                    "settingsBasePath เปลี่ยนตาม role (user/admin)",
                ],
            },
            {
                "id": "UI-SET-02",
                "title": "หน้าโปรไฟล์ (User)",
                "url": "/user/profile",
                "role": "ผู้ยื่นคำร้อง (ROLE_USER)",
                "use_cases": "UC-SET-03, UC-SET-04, UC-SET-05",
                "screenshot": "screenshots/settings-user-profile.png",
                "description": (
                    "หน้าจอใช้ base_academic layout (container-fluid bg-light) "
                    "หัว 'โปรไฟล์ของฉัน' (fs-3 ตรงกลาง) "
                    "รูปโปรไฟล์วงกลม (180x180px, object-fit cover, border + padding) ตรงกลาง "
                    "Card ข้อมูลโปรไฟล์ (shadow-sm): header สีน้ำเงิน (bg-primary) ตัวขาว "
                    "ฟอร์ม: ชื่อ + เบอร์โทร (6/6 split), อีเมล readonly + dropdown ตำแหน่งวิชาการ (6/6), "
                    "dropdown คำนำหน้า (6 col), file input รูปโปรไฟล์ (6 col) "
                    "ปุ่ม 'บันทึก' (primary px-5) "
                    "Modal เปลี่ยนรหัสผ่าน: header สีเหลือง (bg-warning) "
                    "3 ช่อง: รหัสเก่า + รหัสใหม่ + ยืนยันรหัส "
                    "validation ตรวจรหัสผ่านตรงกัน (is-invalid class) "
                    "ปุ่มบันทึก (warning)"
                ),
                "components": [
                    ("รูปโปรไฟล์", "Image + File Input", "แสดงรูป + ปุ่มอัปโหลดใหม่"),
                    ("ชื่อ", "Text Input", "แก้ไขชื่อ"),
                    ("อีเมล", "Text (readonly)", "แสดงอีเมล (ไม่สามารถเปลี่ยนได้)"),
                    ("เบอร์โทร", "Text Input", "แก้ไขเบอร์โทร"),
                    ("ปุ่ม \"บันทึก\"", "Button (primary)", "บันทึกข้อมูลโปรไฟล์ → POST /user/update-profile"),
                    ("ส่วนเปลี่ยนรหัสผ่าน", "Form (separate)", "รหัสเก่า + ใหม่ + ยืนยัน → POST /user/change-password"),
                ],
                "rules": [],
            },
            {
                "id": "UI-SET-03",
                "title": "หน้าโปรไฟล์ (Admin)",
                "url": "/admin/profile",
                "role": "ผู้ดูแลระบบ (ROLE_ADMIN)",
                "use_cases": "UC-SET-03, UC-SET-04, UC-SET-05",
                "screenshot": "screenshots/settings-admin-profile.png",
                "description": (
                    "หน้าจอโครงสร้างเกือบเหมือนหน้าโปรไฟล์ User (bg-light, รูปวงกลม 180px, card shadow-sm) "
                    "แต่มีฟิลด์ readonly เพิ่มเติม: บทบาท (ROLE_ADMIN/ROLE_USER) + สถานะบัญชี (true/false) "
                    "ฟอร์มเหมือนกัน: ชื่อ, เบอร์โทร, อีเมล (readonly), ตำแหน่งวิชาการ, คำนำหน้า, รูปโปรไฟล์ "
                    "Modal เปลี่ยนรหัสผ่านเหมือนกัน (header bg-warning, 3 ช่อง, validation) "
                    "ลิงก์กลับไปหน้า /admin/users แทน dashboard"
                ),
                "components": [
                    ("รูปโปรไฟล์", "Image + File Input", "แสดงรูป + อัปโหลดใหม่"),
                    ("ชื่อ", "Text Input", "แก้ไขชื่อ"),
                    ("อีเมล", "Text (readonly)", "แสดงอีเมล"),
                    ("เบอร์โทร", "Text Input", "แก้ไขเบอร์โทร"),
                    ("ปุ่ม \"บันทึก\"", "Button (primary)", "บันทึกข้อมูลโปรไฟล์ → POST /admin/update-profile"),
                    ("ส่วนเปลี่ยนรหัสผ่าน", "Form (separate)", "รหัสเก่า + ใหม่ + ยืนยัน → POST /admin/change-password"),
                ],
                "rules": [],
            },
        ],
    },
]


# ============================================================
# Document generation
# ============================================================

def create_document():
    doc = Document()

    # --- Custom styles ---
    style = doc.styles["Normal"]
    style.font.name = "TH SarabunPSK"
    style.font.size = Pt(14)
    style.paragraph_format.space_after = Pt(4)

    for level in range(1, 4):
        h = doc.styles[f"Heading {level}"]
        h.font.name = "TH SarabunPSK"
        h.font.bold = True
        if level == 1:
            h.font.size = Pt(20)
        elif level == 2:
            h.font.size = Pt(16)
        else:
            h.font.size = Pt(14)

    # Create a custom bullet style
    bullet_style = doc.styles.add_style("BulletItem", WD_STYLE_TYPE.PARAGRAPH)
    bullet_style.font.name = "TH SarabunPSK"
    bullet_style.font.size = Pt(14)
    bullet_style.paragraph_format.left_indent = Cm(1.27)
    bullet_style.paragraph_format.space_after = Pt(2)

    # --- Cover page ---
    for _ in range(6):
        doc.add_paragraph("")

    cover = doc.add_paragraph()
    cover.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = cover.add_run("การออกแบบหน้าจอผู้ใช้งาน\n(User Interface Design)")
    run.font.size = Pt(28)
    run.font.bold = True
    run.font.name = "TH SarabunPSK"

    doc.add_paragraph("")

    subtitle = doc.add_paragraph()
    subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run2 = subtitle.add_run(
        "ระบบบริหารจัดการงานวิชาการ\nHRCP-KKU-Academic\n\n"
        "คณะวิทยาศาสตร์ มหาวิทยาลัยขอนแก่น"
    )
    run2.font.size = Pt(18)
    run2.font.name = "TH SarabunPSK"

    doc.add_page_break()

    # --- Table of Contents placeholder ---
    doc.add_heading("สารบัญ", level=1)
    screen_count = 0
    for sub in SUBSYSTEMS:
        p = doc.add_paragraph(sub["name"])
        p.style = doc.styles["Normal"]
        p.runs[0].bold = True
        for scr in sub["screens"]:
            screen_count += 1
            doc.add_paragraph(
                f"    {scr['id']}: {scr['title']}",
                style="Normal",
            )
    doc.add_paragraph("")
    p_total = doc.add_paragraph(f"รวมทั้งหมด {screen_count} หน้าจอ")
    p_total.runs[0].italic = True
    doc.add_page_break()

    # --- Each subsystem ---
    for sub in SUBSYSTEMS:
        doc.add_heading(sub["name"], level=1)

        for scr in sub["screens"]:
            doc.add_heading(f"{scr['id']}: {scr['title']}", level=2)

            # Metadata
            meta = doc.add_paragraph()
            meta.style = doc.styles["Normal"]
            _add_bold_run(meta, "URL: ")
            meta.add_run(scr["url"])
            meta.add_run("\n")
            _add_bold_run(meta, "บทบาท: ")
            meta.add_run(scr["role"])
            meta.add_run("\n")
            _add_bold_run(meta, "Use Cases: ")
            meta.add_run(scr["use_cases"])

            # Screenshot description (from HTML template analysis)
            if scr.get("description"):
                desc_header = doc.add_paragraph()
                run_dh = desc_header.add_run("คำอธิบายภาพหน้าจอ:")
                run_dh.font.bold = True
                run_dh.font.size = Pt(14)
                run_dh.font.color.rgb = RGBColor(80, 80, 80)

                desc_para = doc.add_paragraph()
                run_dp = desc_para.add_run(scr["description"])
                run_dp.font.italic = True
                run_dp.font.size = Pt(13)
                run_dp.font.color.rgb = RGBColor(100, 100, 100)
            else:
                placeholder = doc.add_paragraph()
                placeholder.alignment = WD_ALIGN_PARAGRAPH.CENTER
                run_ph = placeholder.add_run(f"[ ภาพหน้าจอ: {scr['screenshot']} ]")
                run_ph.font.color.rgb = RGBColor(128, 128, 128)
                run_ph.font.italic = True
                run_ph.font.size = Pt(12)

            # Components
            doc.add_heading("องค์ประกอบหน้าจอ", level=3)
            for i, (name, comp_type, desc) in enumerate(scr["components"], 1):
                p = doc.add_paragraph(style="BulletItem")
                _add_bold_run(p, f"{name}")
                p.add_run(f"  ({comp_type})")
                p.add_run(f"\n    {desc}")

            # Display rules
            if scr.get("rules"):
                doc.add_heading("กฎการแสดงผล", level=3)
                for rule in scr["rules"]:
                    p = doc.add_paragraph(style="BulletItem")
                    p.add_run(f"- {rule}")

            # Separator
            doc.add_paragraph("")

        doc.add_page_break()

    return doc


def _add_bold_run(paragraph, text):
    run = paragraph.add_run(text)
    run.bold = True
    return run


if __name__ == "__main__":
    output_dir = os.path.dirname(os.path.abspath(__file__))
    output_path = os.path.join(output_dir, "ui-design.docx")

    doc = create_document()
    doc.save(output_path)

    total = sum(len(s["screens"]) for s in SUBSYSTEMS)
    print(f"Generated: {output_path}")
    print(f"Total screens: {total}")
    print(f"Subsystems: {len(SUBSYSTEMS)}")
