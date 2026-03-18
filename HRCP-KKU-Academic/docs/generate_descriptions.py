#!/usr/bin/env python3
"""
Generate two DOCX files:
1. screenshot-descriptions.docx — actual screenshots with Thai captions
2. ssd-descriptions.docx — System Sequence Diagram descriptions
"""

from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.style import WD_STYLE_TYPE
import os
import glob

DOCS_DIR = os.path.dirname(os.path.abspath(__file__))
SCREENSHOTS_DIR = os.path.join(DOCS_DIR, "screenshots")
SSD_IMG_DIR = os.path.join(SCREENSHOTS_DIR, "system sequence diagram screenshot")

# ============================================================
# Screenshot groups + caption mapping
# ============================================================

SCREENSHOT_GROUPS = [
    {
        "name": "1. ระบบยืนยันตัวตน (Authentication)",
        "pattern_captions": [
            ("signin",
             "หน้าจอเข้าสู่ระบบ แสดงฟอร์มกรอกอีเมลและรหัสผ่าน",
             "หน้าจอนี้เป็นจุดเริ่มต้นของระบบ ใช้สำหรับให้ผู้ใช้ยืนยันตัวตนก่อนเข้าใช้งาน "
             "ประกอบด้วยช่องกรอกอีเมลและรหัสผ่าน ปุ่ม 'เข้าสู่ระบบ' สำหรับส่ง credentials ไปตรวจสอบ "
             "ลิงก์ 'เข้าสู่ระบบครั้งแรก' สำหรับผู้ใช้ใหม่ที่ยังไม่เคยตั้งรหัสผ่าน "
             "และลิงก์ 'ลืมรหัสผ่าน' สำหรับรีเซ็ตรหัสผ่านผ่านอีเมล "
             "มุมขวาบนมี dropdown สลับภาษาไทย/อังกฤษ แสดงข้อความแจ้งเตือนเมื่อ login สำเร็จ/ผิดพลาด"),
            ("first-login-set-password",
             "หน้าจอตั้งรหัสผ่านใหม่ หลังยืนยัน OTP สำเร็จ",
             "หน้าจอนี้เป็นขั้นตอนที่ 3 (สุดท้าย) ของการเข้าสู่ระบบครั้งแรก "
             "ผู้ใช้กรอกรหัสผ่านใหม่และยืนยันรหัสผ่าน ระบบตรวจสอบเงื่อนไข (≥ 6 ตัวอักษร) แบบ real-time "
             "แสดงเครื่องหมายถูก/กากบาทเมื่อผ่าน/ไม่ผ่านเงื่อนไข ปุ่มบันทึกจะ enable เมื่อเงื่อนไขครบ "
             "เมื่อตั้งรหัสผ่านสำเร็จจะ activate บัญชีและกลับไปหน้า Login"),
            ("first-login-verify-otp",
             "หน้าจอยืนยัน OTP กรอกรหัส 8 หลักที่ได้รับทางอีเมล",
             "หน้าจอนี้เป็นขั้นตอนที่ 2 ของการเข้าสู่ระบบครั้งแรก "
             "ผู้ใช้กรอกรหัส OTP 8 หลักที่ได้รับทางอีเมล ช่องกรอกขนาดใหญ่ตรงกลาง "
             "มีข้อความแจ้งว่า OTP มีอายุ 5 นาที ปุ่ม 'ยืนยัน OTP' ส่งรหัสไปตรวจสอบ "
             "หากรหัสถูกต้องจะไปขั้นตอนตั้งรหัสผ่าน หากผิดจะแสดงข้อความ error"),
            ("first-login",
             "หน้าจอเข้าสู่ระบบครั้งแรก กรอกอีเมลเพื่อรับ OTP",
             "หน้าจอนี้เป็นขั้นตอนที่ 1 ของการเข้าสู่ระบบครั้งแรก สำหรับผู้ใช้ที่ยังไม่เคยตั้งรหัสผ่าน "
             "ผู้ใช้กรอกอีเมลที่ Admin ลงทะเบียนไว้ ระบบจะส่ง OTP ไปทางอีเมล "
             "แสดง Step Indicator 3 ขั้นตอน (กรอกอีเมล → ยืนยัน OTP → ตั้งรหัสผ่าน) "
             "มีลิงก์ 'กลับหน้า Login' ด้านล่าง"),
            ("forgot-password",
             "หน้าจอลืมรหัสผ่าน กรอกอีเมลเพื่อรับลิงก์รีเซ็ต",
             "หน้าจอนี้ใช้เมื่อผู้ใช้ลืมรหัสผ่าน ผู้ใช้กรอกอีเมลที่ลงทะเบียนไว้ "
             "ระบบจะสร้าง token และส่งลิงก์รีเซ็ตรหัสผ่านไปทางอีเมล "
             "มีปุ่ม 'ส่งลิงก์รีเซ็ตรหัสผ่าน' และลิงก์ 'กลับไปหน้าเข้าสู่ระบบ' "
             "แสดงข้อความสำเร็จ/ผิดพลาดตามผลลัพธ์"),
            ("reset-password",
             "หน้าจอรีเซ็ตรหัสผ่าน กรอกรหัสผ่านใหม่",
             "หน้าจอนี้เปิดจากลิงก์ที่ได้รับทางอีเมล ใช้สำหรับตั้งรหัสผ่านใหม่ "
             "ผู้ใช้กรอกรหัสผ่านใหม่และยืนยันรหัสผ่าน ระบบตรวจสอบว่าตรงกันแบบ real-time "
             "มี hidden field เก็บ token สำหรับยืนยันความถูกต้อง "
             "หาก token ไม่ถูกต้องหรือหมดอายุจะแสดงข้อความแจ้ง"),
        ],
    },
    {
        "name": "2. ระบบคำร้องประเมินผลการสอน — Admin",
        "pattern_captions": [
            ("admin-academic-requests",
             "หน้ารายการคำร้องประเมินผลการสอน แสดง Status Cards และตารางคำร้อง",
             "หน้าจอนี้เป็นหน้าหลักของ Admin สำหรับจัดการคำร้อง แสดง Tab Navigation 2 แท็บ "
             "(ประเมินผลการสอน / ขอกำหนดตำแหน่ง) มี Status Cards แสดงจำนวนคำร้องแต่ละสถานะ "
             "คลิก card เพื่อกรองตารางตามสถานะ มีช่องค้นหาตามชื่อผู้ยื่น "
             "ตารางแสดงรหัส ชื่อผู้ยื่น อีเมล วันที่ส่ง สถานะ (Badge สี) และปุ่มจัดการ"),
            ("admin-academic-request-*-document-",
             "หน้ากรอกเอกสาร แสดงฟอร์มตามประเภทเอกสาร",
             "หน้าจอนี้ใช้สำหรับ Admin กรอกเอกสารแต่ละประเภท (Type 0-8) "
             "ฟิลด์แตกต่างกันตามประเภท เช่น เอกสาร 2 มี dropdown เลือกกรรมการ, เอกสาร 6 มีช่องคะแนน "
             "ระบบ auto-fill ข้อมูลจากเอกสารก่อนหน้า มี Thai date picker สำหรับวันที่ "
             "ปุ่ม 'ดูตัวอย่าง' แสดง Preview DOCX, ปุ่ม 'บันทึกและสร้างเอกสาร' สร้างไฟล์ DOCX"),
            ("admin-academic-request-",
             "หน้ารายละเอียดคำร้อง แสดงข้อมูลผู้ยื่น เอกสาร และประวัติสถานะ",
             "หน้าจอนี้แสดงรายละเอียดทั้งหมดของคำร้อง แบ่งเป็น 2 คอลัมน์ "
             "ซ้าย: ฟอร์มอัพเดทสถานะ (dropdown + หมายเหตุ), คลังเอกสารแนบ (อัปโหลด/ดาวน์โหลด/ลบ), "
             "ประวัติสถานะ (timeline) "
             "ขวา: จัดการเอกสาร 8 ประเภท แต่ละประเภทมีปุ่มกรอก/ดาวน์โหลด DOCX/PDF "
             "ปุ่ม 'ดาวน์โหลดทั้งหมด ZIP' รวมไฟล์ทั้งหมด"),
            ("admin-academic-settings",
             "หน้าตั้งค่าระบบ แสดง Toggle switches สำหรับ Auto-Draft และแจ้งเตือน",
             "หน้าจอนี้ใช้กำหนดค่าระบบสำหรับผู้ใช้ แบ่ง 2 ส่วน: "
             "(1) ตั้งค่าทั่วไป — Toggle เปิด/ปิด Auto-Draft (บันทึกอัตโนมัติ) และแจ้งเตือนอีเมล "
             "(2) แจ้งเตือนก่อนหมดอายุ — 4 ตัวเลือก (6 เดือน, 3 เดือน, 1 เดือน, 1 สัปดาห์) "
             "แต่ละตัวเลือกมี Toggle switch สีต่างกันตามความเร่งด่วน"),
        ],
    },
    {
        "name": "3. ระบบคำร้องประเมินผลการสอน — ผู้ยื่น",
        "pattern_captions": [
            ("user-academic-new-request",
             "หน้าสร้างคำร้องใหม่ แสดง Step Indicator 3 ขั้นตอน",
             "หน้าจอนี้ใช้สำหรับผู้ยื่นสร้างคำร้องใหม่ แสดง Step Indicator 3 ขั้นตอน "
             "(กรอกเอกสาร 0 → กรอกเอกสาร 1 → ส่งคำร้อง) แต่ละขั้นตอนมี badge แสดงสถานะ "
             "(กรอกแล้ว/รอกรอก) ปุ่ม 'กรอกเอกสาร' หรือ 'แก้ไขเอกสาร' ตามสถานะ "
             "เมื่อเอกสารครบจะแสดงปุ่ม 'ส่งคำร้อง' พร้อม Modal ยืนยันก่อนส่ง"),
            ("user-academic-request-*-document-0",
             "หน้ากรอกเอกสารที่ 0 (บันทึกข้อความ)",
             "หน้าจอนี้ใช้สำหรับผู้ยื่นกรอกเอกสารที่ 0 (บันทึกข้อความ) "
             "ประกอบด้วยข้อมูลหนังสือ (เลขที่/วันที่), ข้อมูลผู้ยื่น (คำนำหน้า/ชื่อ/ประเภท/ตำแหน่ง), "
             "ตำแหน่งที่ขอ (ผศ./รศ. เลือกได้ 1), ชื่อสำหรับพิมพ์เอกสาร "
             "มีปุ่ม 'ดูตัวอย่าง' Preview DOCX, ปุ่ม 'บันทึกและสร้างเอกสาร' สร้างไฟล์ DOCX "
             "และปุ่ม 'กลับ' กลับหน้า wizard"),
            ("user-academic-request-*-document-1",
             "หน้ากรอกเอกสารที่ 1 (แบบตรวจสอบเบื้องต้น)",
             "หน้าจอนี้ใช้สำหรับผู้ยื่นกรอกเอกสารที่ 1 (แบบตรวจสอบเบื้องต้น) "
             "แสดงตาราง Checklist 5 รายการเอกสารที่ต้องแนบ แต่ละรายการมี checkbox "
             "ระบบเก็บค่า '✓' เมื่อเลือกผ่าน hidden fields "
             "JavaScript โหลดข้อมูลเดิมจาก JSON และ toggle checkbox อัตโนมัติ"),
            ("user-academic-request-",
             "หน้ารายละเอียดคำร้อง แสดง Progress Tracker และเอกสาร",
             "หน้าจอนี้แสดงสถานะและรายละเอียดคำร้องของผู้ยื่น "
             "Progress Tracker แนวนอนแสดงขั้นตอน (รับคำร้อง → แต่งตั้ง → ประชุม → ผลลัพธ์) "
             "แสดงเฉพาะเอกสาร type 0, 1, 8 ที่ผู้ยื่นเห็นได้ พร้อมปุ่มดาวน์โหลด "
             "Alert แสดงผลลัพธ์: ผ่านการประเมิน (สีเขียว), ต้องแก้ไข (สีเหลือง + ฟอร์มอัปโหลด), "
             "ไม่รับคำร้อง (สีแดง) ไทม์ไลน์ประวัติสถานะด้านล่าง"),
        ],
    },
    {
        "name": "4. ระบบคำร้องขอกำหนดตำแหน่ง — Admin",
        "pattern_captions": [
            ("admin-position-request-*-document-",
             "หน้ากรอกเอกสารตำแหน่ง แสดงฟอร์มตามประเภท",
             "หน้าจอนี้ใช้สำหรับ Admin กรอกเอกสารตำแหน่ง (Phase 2) ประเภท 5 หรือ 8 "
             "ฟิลด์แตกต่างตามประเภท มี auto-fill จากเอกสาร Phase 1 "
             "ระบบใช้ Document Generator สร้างไฟล์ DOCX จาก template "
             "มีปุ่มดูตัวอย่าง บันทึก และกลับ"),
            ("admin-position-request-",
             "หน้ารายละเอียดคำร้องตำแหน่ง แสดง Progress Tracker และเอกสาร",
             "หน้าจอนี้แสดงรายละเอียดคำร้องตำแหน่ง (Phase 2) แบ่ง 2 คอลัมน์ "
             "ซ้าย: ฟอร์มอัพเดทสถานะ + ประวัติสถานะ (scrollable) + ข้อมูลคำร้อง "
             "ขวา: ตารางเอกสาร แสดงสถานะ ผู้กรอก (Admin/Applicant) "
             "ปุ่มกรอก/ดาวน์โหลด DOCX เมื่อมีเอกสาร"),
        ],
    },
    {
        "name": "5. ระบบคำร้องขอกำหนดตำแหน่ง — ผู้ยื่น",
        "pattern_captions": [
            ("user-position-dashboard",
             "หน้าแดชบอร์ดตำแหน่ง แสดง Countdown หมดอายุและคำร้อง",
             "หน้าจอนี้เป็นหน้าหลักของผู้ยื่นสำหรับคำร้องตำแหน่ง (Phase 2) "
             "แสดง Countdown Card นับถอยหลังวันหมดอายุผลประเมิน Phase 1 "
             "สีเปลี่ยนตามความเร่งด่วน (เขียว → เหลือง → ส้ม → แดง) อัพเดททุกวินาที "
             "แสดง Draft Card (คำร้องที่ค้างอยู่) และ Request Cards พร้อม Progress Tracker "
             "ปุ่ม 'สร้างคำร้องใหม่' ซ่อนเมื่อมีคำร้อง active"),
            ("user-position-new-request",
             "หน้าสร้างคำร้องตำแหน่งใหม่ เลือกผลประเมินที่ผ่าน Phase 1",
             "หน้าจอนี้ใช้เลือกผลประเมิน Phase 1 ที่ผ่านเกณฑ์เพื่อเริ่มคำร้อง Phase 2 "
             "แสดง card สำหรับแต่ละผลประเมินที่ eligible พร้อมวันที่ส่งและ badge สถานะ "
             "ปุ่ม 'เลือกและสร้างคำร้อง' สร้าง draft คำร้องตำแหน่ง "
             "แสดง alert เตือนเมื่อไม่มีผลประเมินที่ eligible"),
            ("user-position-request-*-document-",
             "หน้ากรอกเอกสารตำแหน่ง (ผู้ยื่น)",
             "หน้าจอนี้ใช้สำหรับผู้ยื่นกรอกเอกสารตำแหน่ง (APPLICANT_DOCS) "
             "ฟิลด์แตกต่างตามประเภทเอกสาร สามารถบันทึกเป็นแบบร่าง (draft) "
             "หรือบันทึกและสร้างเอกสาร (submit) "
             "ระบบสร้าง JSON จากข้อมูลฟอร์มแล้วบันทึกลงฐานข้อมูล"),
            ("user-position-request-",
             "หน้ารายละเอียดคำร้องตำแหน่ง แสดง Document Cards",
             "หน้าจอนี้แสดงสถานะคำร้องตำแหน่งของผู้ยื่น "
             "Step Indicator แสดงขั้นตอน (เสร็จ/pending) Document Cards แต่ละประเภท "
             "แสดง badge สถานะ (เสร็จสิ้น/รอดำเนินการ) + ปุ่มกรอก/แก้ไข "
             "ส่วนส่งคำร้อง: alert แจ้งพร้อมส่ง/ไม่ครบ + ปุ่มส่งพร้อม Modal ยืนยัน "
             "ประวัติสถานะ (timeline) แสดงด้านล่าง"),
        ],
    },
    {
        "name": "6. ระบบจัดการบุคลากร (Staff Management)",
        "pattern_captions": [
            ("admin-academic-staff-add",
             "หน้าเพิ่มบุคลากร แสดงฟอร์มกรอกข้อมูล",
             "หน้าจอนี้ใช้สำหรับ Admin เพิ่มบุคลากรใหม่เข้าระบบ "
             "ฟอร์มประกอบด้วย: dropdown ตำแหน่งทางวิชาการ (8 ตัวเลือก), ช่องชื่อเต็ม, "
             "dropdown ประเภทบุคลากร (3 ตัวเลือก), ช่องสาขา, dropdown บทบาท (5 ตัวเลือก) "
             "ปุ่ม 'บันทึก' บันทึกข้อมูลลงฐานข้อมูล ปุ่ม 'กลับ' กลับหน้ารายชื่อ"),
            ("admin-academic-staff-edit",
             "หน้าแก้ไขบุคลากร แสดงข้อมูลปัจจุบัน",
             "หน้าจอนี้ใช้ฟอร์มเดียวกับหน้าเพิ่ม แต่ข้อมูลถูก pre-fill จากฐานข้อมูล "
             "Admin แก้ไขข้อมูลแล้วกดบันทึก ระบบอัพเดทข้อมูลบุคลากร"),
            ("admin-academic-staff",
             "หน้ารายชื่อบุคลากร แสดงตารางและ Badge บทบาท",
             "หน้าจอนี้แสดงรายชื่อบุคลากรทั้งหมดในระบบ มีปุ่ม 'เพิ่มบุคลากร' "
             "ตารางแสดง: ตำแหน่งทางวิชาการ, ชื่อเต็ม, ประเภท, สาขา, บทบาท "
             "(Badge สีตามบทบาท: คณบดี=แดง, หัวหน้าสาขา=น้ำเงิน, กรรมการ=เขียว, HR=เหลือง) "
             "ปุ่ม 'แก้ไข' และ 'ลบ' ในแต่ละแถว"),
        ],
    },
    {
        "name": "7. ระบบจัดการไฟล์ (File Management)",
        "pattern_captions": [
            ("admin-file-manager",
             "หน้าจัดการไฟล์/ถังขยะ แสดง Summary Cards ตาราง และตัวกรอง",
             "หน้าจอนี้ใช้จัดการไฟล์ทั้งหมดในระบบ Summary Cards 3 ใบ แสดงสถิติ "
             "(จำนวนไฟล์, ขนาดรวม, จำนวนในถังขยะ) Tab สลับ 'ไฟล์ทั้งหมด'/'ถังขยะ' "
             "ช่องค้นหา + dropdown กรองประเภทไฟล์ (DOCX/PDF/JPG) + dropdown กรองคำร้อง "
             "ตารางแสดงชื่อไฟล์ (ไอคอนตามประเภท), ขนาด, รหัสคำร้อง, วันที่ "
             "โหมดไฟล์: ปุ่มดาวน์โหลด+ลบ / โหมดถังขยะ: ปุ่มกู้คืน+ลบถาวร "
             "การลบ/กู้คืนใช้ AJAX ไม่ reload หน้า"),
        ],
    },
    {
        "name": "8. ระบบจัดการผู้ใช้ (User Management)",
        "pattern_captions": [
            ("admin-add-admin",
             "หน้าเพิ่มแอดมิน แสดงฟอร์มข้อมูลส่วนตัวและบัญชี",
             "หน้าจอนี้ใช้สำหรับ Admin เพิ่มบัญชีแอดมินใหม่ ฟอร์มแบ่ง 3 ส่วน: "
             "(1) ข้อมูลส่วนตัว — คำนำหน้า, ชื่อ, ตำแหน่งวิชาการ, เบอร์โทร "
             "(2) ข้อมูลบัญชี — อีเมล, บทบาท "
             "(3) รูปโปรไฟล์ — อัปโหลดรูป (JPG/PNG/GIF ≤5MB) "
             "รหัสผ่านจะถูกตั้งผ่านขั้นตอน first-login"),
            ("admin-edit-admin",
             "หน้าแก้ไขแอดมิน แสดงข้อมูลปัจจุบันและรูปโปรไฟล์",
             "หน้าจอนี้ใช้แก้ไขข้อมูลแอดมิน/ผู้ใช้ ข้อมูลถูก pre-fill จากฐานข้อมูล "
             "รูปโปรไฟล์แสดง thumbnail วงกลม + file input สำหรับเปลี่ยน "
             "อัพเดทรูปแบบ real-time ผ่าน AJAX ไม่ต้อง reload "
             "JavaScript validate: email regex, file size ≤5MB, file type"),
            ("admin-users",
             "หน้ารายชื่อผู้ใช้/แอดมิน แสดง Toggle สถานะและแจ้งเตือน",
             "หน้าจอนี้แสดงรายชื่อผู้ใช้ (type=1) หรือแอดมิน (type=2) "
             "ตารางแสดง: รูปโปรไฟล์ (วงกลม), ชื่อ, อีเมล, เบอร์โทร, ตำแหน่ง "
             "Toggle switch เปิด/ปิดบัญชี (เปิด=เขียว/ปิด=เทา) "
             "Toggle แจ้งเตือนอีเมล (เฉพาะแอดมิน) "
             "ปุ่ม 'แก้ไข' + 'ลบ' ในแต่ละแถว ปุ่ม 'เพิ่มแอดมิน' แสดงเฉพาะ type=2"),
            ("admin-activity-logs",
             "หน้าประวัติการใช้งาน แสดง Summary Cards ตัวกรอง และ Pagination",
             "หน้าจอนี้แสดงประวัติกิจกรรมทั้งหมดของระบบ Summary Cards 4 ใบ "
             "(กิจกรรมทั้งหมด, สร้างบัญชี, อัพเดทสถานะ, สร้างเอกสาร) "
             "ตัวกรอง: ค้นหาข้อความ, dropdown ประเภทกิจกรรม, ช่วงวันที่ "
             "ตารางแสดง: วันเวลา, ชื่อแอดมิน, การกระทำ (Badge สี), รายละเอียด, IP "
             "Pagination แบ่งหน้า 20 รายการ ปุ่ม 'ส่งออก CSV' ดาวน์โหลดข้อมูล"),
        ],
    },
    {
        "name": "9. ระบบตั้งค่าและโปรไฟล์",
        "pattern_captions": [
            ("admin-profile",
             "หน้าโปรไฟล์ Admin แสดงรูปวงกลม ฟอร์มแก้ไข และ Modal เปลี่ยนรหัสผ่าน",
             "หน้าจอนี้ใช้สำหรับ Admin ดูและแก้ไขข้อมูลโปรไฟล์ "
             "แสดงรูปโปรไฟล์วงกลมขนาดใหญ่ ฟอร์ม: ชื่อ, เบอร์โทร, อีเมล (readonly), "
             "ตำแหน่งวิชาการ, คำนำหน้า, บทบาท (readonly), สถานะบัญชี (readonly) "
             "Modal เปลี่ยนรหัสผ่าน: กรอกรหัสเก่า + ใหม่ + ยืนยัน พร้อม validation"),
            ("user-profile",
             "หน้าโปรไฟล์ User แสดงรูปวงกลม ฟอร์มแก้ไข และ Modal เปลี่ยนรหัสผ่าน",
             "หน้าจอนี้ใช้สำหรับผู้ยื่นดูและแก้ไขข้อมูลโปรไฟล์ "
             "โครงสร้างเหมือนหน้า Admin Profile แต่ไม่มีฟิลด์ บทบาท/สถานะบัญชี "
             "ฟอร์ม: ชื่อ, เบอร์โทร, อีเมล (readonly), ตำแหน่งวิชาการ, คำนำหน้า "
             "อัปโหลดรูปโปรไฟล์ใหม่ได้ Modal เปลี่ยนรหัสผ่านเหมือนกัน"),
        ],
    },
]

# ============================================================
# SSD descriptions
# ============================================================

SSD_SECTIONS = [
    {
        "name": "1. ระบบยืนยันตัวตน (Authentication)",
        "diagrams": [
            {
                "id": "SSD-AUTH-01",
                "title": "เข้าสู่ระบบ",
                "description": (
                    "ผู้ใช้ทั่วไปเข้าถึงหน้า Login แล้วกรอกอีเมลและรหัสผ่าน "
                    "ระบบค้นหาผู้ใช้จากฐานข้อมูลและตรวจสอบรหัสผ่านด้วย BCrypt "
                    "หากถูกต้องและบัญชี enabled จะสร้าง Session กำหนด Role "
                    "แล้ว redirect ไปหน้า /admin/ หรือ /user/ ตาม Role "
                    "หากผิดพลาดจะแสดงข้อความ error บนหน้า Login"
                ),
            },
            {
                "id": "SSD-AUTH-02",
                "title": "เข้าสู่ระบบครั้งแรก (OTP)",
                "description": (
                    "ผู้ใช้ครั้งแรกกรอกอีเมลที่ลงทะเบียนไว้ ระบบสร้าง OTP "
                    "แล้วส่งทางอีเมล ผู้ใช้กรอก OTP 8 หลักเพื่อยืนยัน "
                    "หากถูกต้องจะเก็บ otpVerified=true ใน session แล้วให้ตั้งรหัสผ่านใหม่ "
                    "ระบบตรวจสอบรหัสผ่านตรงกันและ ≥ 6 ตัวอักษร "
                    "จากนั้น activate บัญชีและ redirect ไปหน้า Login พร้อมข้อความสำเร็จ"
                ),
            },
            {
                "id": "SSD-AUTH-03",
                "title": "ลืมรหัสผ่าน + รีเซ็ตรหัสผ่าน",
                "description": (
                    "ผู้ใช้กรอกอีเมลในหน้าลืมรหัสผ่าน ระบบค้นหาอีเมลในฐานข้อมูล "
                    "หากพบจะสร้าง resetToken (UUID) บันทึกลง DB "
                    "แล้วส่งลิงก์รีเซ็ตทางอีเมล ผู้ใช้คลิกลิงก์เพื่อเข้าหน้ารีเซ็ต "
                    "ระบบตรวจสอบ token หากถูกต้องจะแสดงฟอร์มตั้งรหัสผ่านใหม่ "
                    "บันทึกรหัสผ่านใหม่ (BCrypt) ลบ token และ redirect ไปหน้า Login"
                ),
            },
        ],
    },
    {
        "name": "2. ระบบคำร้องประเมินผลการสอน (Academic Request)",
        "diagrams": [
            {
                "id": "SSD-ACAD-01",
                "title": "ผู้ยื่นสร้างคำร้องและกรอกเอกสาร",
                "description": (
                    "ผู้ยื่นเข้าหน้าสร้างคำร้องใหม่ ระบบตรวจสอบ hasActiveRequest "
                    "แล้วสร้างคำร้องในสถานะ DRAFT จากนั้นผู้ยื่นกรอกเอกสารที่ 0 "
                    "ระบบสร้าง JSON จากข้อมูลฟอร์ม ใช้ template สร้างไฟล์ DOCX "
                    "แล้วบันทึก JSON + filePath ลงฐานข้อมูล"
                ),
            },
            {
                "id": "SSD-ACAD-02",
                "title": "ผู้ยื่นส่งคำร้อง",
                "description": (
                    "ผู้ยื่นกดส่งคำร้อง ระบบตรวจสอบว่าสถานะเป็น DRAFT "
                    "จากนั้นเปลี่ยนสถานะเป็น RECEIVED บันทึก status history "
                    "และส่งอีเมลแจ้ง Admin ทุกคน แล้ว redirect พร้อมข้อความ 'ส่งคำร้องสำเร็จ'"
                ),
            },
            {
                "id": "SSD-ACAD-03",
                "title": "Admin อัพเดทสถานะคำร้อง",
                "description": (
                    "Admin เลือกสถานะใหม่จาก dropdown พร้อมกรอกหมายเหตุ "
                    "หากเป็น MEETING_SCHEDULED จะกำหนดวันประชุม สถานที่ และผู้ตั้ง "
                    "ระบบบันทึก status history ส่งอีเมลแจ้งผู้ยื่น "
                    "และบันทึก admin log"
                ),
            },
            {
                "id": "SSD-ACAD-04",
                "title": "Admin กรอกเอกสาร + สร้าง DOCX",
                "description": (
                    "Admin เข้าหน้ากรอกเอกสาร ระบบ auto-fill จากเอกสารก่อนหน้า "
                    "และดึงรายชื่อบุคลากร/กรรมการ Admin กรอกข้อมูลแล้วส่ง "
                    "ระบบประมวลผล formData (checkbox, คะแนน, เลขไทย) "
                    "ใช้ Document Generator สร้าง DOCX (ประเภท 4 สร้าง 3 สำเนา) "
                    "แล้วบันทึกเอกสารและ admin log"
                ),
            },
            {
                "id": "SSD-ACAD-05",
                "title": "Admin ส่งข้อเสนอแนะ",
                "description": (
                    "Admin ส่งข้อเสนอแนะจากเอกสาร 5 ระบบดึง suggestions_text จาก JSON "
                    "เปลี่ยนสถานะคำร้องเป็น COMPLETED_REVISE "
                    "บันทึก status history ส่งอีเมลข้อเสนอแนะถึงผู้ยื่น "
                    "และบันทึก admin log"
                ),
            },
            {
                "id": "SSD-ACAD-06",
                "title": "ดาวน์โหลดเอกสาร",
                "description": (
                    "ผู้ใช้เลือกดาวน์โหลดเอกสาร (DOCX หรือ PDF) "
                    "ระบบอ่านไฟล์ DOCX จาก File Storage "
                    "หาก format=pdf จะใช้ LibreOffice แปลง DOCX เป็น PDF "
                    "แล้วส่งไฟล์กลับให้ผู้ใช้ดาวน์โหลด"
                ),
            },
        ],
    },
    {
        "name": "3. ระบบคำร้องขอกำหนดตำแหน่ง (Position Request)",
        "diagrams": [
            {
                "id": "SSD-POS-01",
                "title": "ผู้ยื่นสร้างคำร้องตำแหน่ง",
                "description": (
                    "ผู้ยื่นเข้าหน้าสร้างคำร้องตำแหน่ง ระบบตรวจสอบ hasActiveRequest "
                    "และดึงรายการผลประเมิน Phase 1 ที่ eligible "
                    "ผู้ยื่นเลือกผลประเมินแล้วกดสร้าง ระบบสร้างคำร้องในสถานะ DRAFT "
                    "เชื่อมกับ evaluationId แล้ว redirect ไปหน้ารายละเอียด"
                ),
            },
            {
                "id": "SSD-POS-02",
                "title": "ผู้ยื่นกรอกเอกสาร",
                "description": (
                    "ผู้ยื่นเข้ากรอกเอกสารตามประเภท (เฉพาะ APPLICANT_DOCS) "
                    "ระบบสร้าง JSON จาก formData อนุญาตให้บันทึกเป็นแบบร่าง (draft) "
                    "หรือส่งปกติ (submit) แล้ว redirect พร้อมข้อความสำเร็จ"
                ),
            },
            {
                "id": "SSD-POS-03",
                "title": "ผู้ยื่นส่งคำร้องตำแหน่ง",
                "description": (
                    "ผู้ยื่นกดส่งคำร้อง ระบบตรวจสอบสถานะ DRAFT "
                    "และตรวจสอบว่า APPLICANT_DOCS ครบทุกประเภท "
                    "หากครบจะเปลี่ยนสถานะเป็น DOCUMENT_RECEIVED "
                    "หากไม่ครบจะแสดง error 'เอกสารไม่ครบ'"
                ),
            },
            {
                "id": "SSD-POS-04",
                "title": "Admin อัพเดทสถานะ + กรอกเอกสาร",
                "description": (
                    "Admin อัพเดทสถานะคำร้องตำแหน่งพร้อมหมายเหตุ "
                    "จากนั้นเข้ากรอกเอกสารประเภท 5 หรือ 8 "
                    "ระบบดึง existing data + รายชื่อบุคลากร "
                    "ใช้ Document Generator สร้าง DOCX แล้วบันทึก (source=ADMIN)"
                ),
            },
        ],
    },
    {
        "name": "4. ระบบคำร้องทั่วไป (Petition)",
        "diagrams": [
            {
                "id": "SSD-PET-01",
                "title": "ผู้ยื่นสร้างคำร้องทั่วไป",
                "description": (
                    "ผู้ยื่นเข้าหน้าสร้างคำร้อง ระบบตรวจสอบว่าไม่มีคำร้อง active "
                    "ผู้ยื่นกรอกหัวข้อและรายละเอียด ระบบ Validate แล้วสร้างคำร้อง "
                    "ในสถานะ RECEIVED บันทึกประวัติสถานะ "
                    "แล้ว redirect ไปหน้ารายละเอียดพร้อมข้อความ 'ยื่นคำร้องสำเร็จ'"
                ),
            },
            {
                "id": "SSD-PET-02",
                "title": "ผู้ยื่นดูรายละเอียดคำร้อง",
                "description": (
                    "ผู้ยื่นเข้าดูรายละเอียดคำร้อง ระบบดึงข้อมูลคำร้อง + ประวัติสถานะ "
                    "ตรวจสอบสิทธิ์ (เจ้าของหรือ Admin) "
                    "แล้วแสดงหน้ารายละเอียดพร้อมไทม์ไลน์สถานะ"
                ),
            },
            {
                "id": "SSD-PET-03",
                "title": "ผู้ดูแลอัพเดทสถานะคำร้อง",
                "description": (
                    "Admin เปิดหน้ารายละเอียดคำร้อง ระบบแสดงตัวเลือกสถานะ "
                    "Admin เลือกสถานะใหม่ ระบบบันทึกสถานะใหม่ "
                    "และบันทึกประวัติการเปลี่ยนสถานะ แล้วแสดงข้อความ 'อัพเดทสถานะสำเร็จ'"
                ),
            },
        ],
    },
    {
        "name": "5. ระบบจัดการบุคลากร (Staff Management)",
        "diagrams": [
            {
                "id": "SSD-STAFF-01",
                "title": "เพิ่ม/แก้ไขบุคลากร",
                "description": (
                    "Admin เข้าแบบฟอร์มเพิ่มบุคลากร กรอกข้อมูล "
                    "(ชื่อเต็ม ตำแหน่งทางวิชาการ ประเภท สาขา บทบาท) "
                    "ระบบบันทึก StaffMember ลงฐานข้อมูล (isActive=true) "
                    "แล้ว redirect พร้อมข้อความ 'เพิ่มบุคลากรสำเร็จ' "
                    "การแก้ไขใช้ฟอร์มเดียวกันโดย pre-fill ข้อมูลเดิม"
                ),
            },
            {
                "id": "SSD-STAFF-02",
                "title": "ลบบุคลากร (Soft Delete)",
                "description": (
                    "Admin กดลบบุคลากร ระบบอัพเดท isActive=false (Soft Delete) "
                    "ไม่ลบ record จากฐานข้อมูลจริง "
                    "แล้ว redirect พร้อมข้อความ 'ลบบุคลากรสำเร็จ'"
                ),
            },
        ],
    },
    {
        "name": "6. ระบบจัดการไฟล์ (File Management)",
        "diagrams": [
            {
                "id": "SSD-FILE-01",
                "title": "Soft Delete และกู้คืนไฟล์",
                "description": (
                    "Admin คลิกลบไฟล์ (พร้อมยืนยัน) Browser ส่ง AJAX request "
                    "ระบบ Soft Delete โดยเปลี่ยนสถานะเป็น deleted "
                    "แล้วส่ง JSON response กลับอัพเดท UI แบบไม่ reload "
                    "การกู้คืนใช้กระบวนการเดียวกัน (AJAX) เปลี่ยนสถานะกลับเป็น active"
                ),
            },
            {
                "id": "SSD-FILE-02",
                "title": "ลบถาวร / ล้างถังขยะ",
                "description": (
                    "Admin กดลบถาวรหรือล้างถังขยะ (พร้อมยืนยัน) "
                    "ระบบลบ record จากฐานข้อมูลจริง "
                    "และลบไฟล์จากดิสก์ (File System) "
                    "แล้วส่ง JSON response กลับอัพเดท UI"
                ),
            },
        ],
    },
    {
        "name": "7. ระบบจัดการผู้ใช้ (User Management)",
        "diagrams": [
            {
                "id": "SSD-USER-01",
                "title": "เพิ่มแอดมินใหม่",
                "description": (
                    "Admin กรอกข้อมูลแอดมินใหม่ (ชื่อ อีเมล รูปโปรไฟล์) "
                    "ระบบตรวจสอบอีเมลไม่ซ้ำ บันทึกบัญชี "
                    "หากมีรูปโปรไฟล์จะอัปโหลดไปที่ uploads/profile_img/ "
                    "บันทึก Activity Log (CREATE_ACCOUNT) แล้ว redirect"
                ),
            },
            {
                "id": "SSD-USER-02",
                "title": "แก้ไขผู้ใช้/แอดมิน",
                "description": (
                    "Admin เข้าหน้าแก้ไข ระบบดึงข้อมูลปัจจุบันมาแสดงในฟอร์ม "
                    "Admin แก้ไขข้อมูล (ชื่อ อีเมล รูปโปรไฟล์) "
                    "ระบบ Validate (อีเมลรูปแบบ ชื่อไม่ว่าง ไฟล์ ≤5MB) "
                    "ตรวจสอบอีเมลไม่ซ้ำ (exclude ตัวเอง) แล้วบันทึก Activity Log"
                ),
            },
            {
                "id": "SSD-USER-03",
                "title": "ลบผู้ใช้/แอดมิน",
                "description": (
                    "Admin เลือกลบผู้ใช้ ระบบตรวจสอบ canDeleteUser (ป้องกันลบตัวเอง) "
                    "หากสามารถลบได้จะลบบัญชีจากฐานข้อมูล "
                    "บันทึก Activity Log (DELETE_USER_ACCOUNT) แล้ว redirect "
                    "หากเป็นการลบตัวเองจะแสดง error 'ไม่สามารถลบบัญชีนี้ได้'"
                ),
            },
        ],
    },
    {
        "name": "8. ระบบตั้งค่าและแจ้งเตือน (Settings & Notifications)",
        "diagrams": [
            {
                "id": "SSD-SET-01",
                "title": "บันทึกการตั้งค่า",
                "description": (
                    "ผู้ใช้เข้าหน้าตั้งค่า ระบบดึงการตั้งค่าปัจจุบัน "
                    "(autoDraft, emailNotification, expiryAlerts) "
                    "ผู้ใช้เปลี่ยนแปลง Toggle switches แล้วกดบันทึก "
                    "ระบบอัพเดท UserDtls ในฐานข้อมูล แล้ว redirect พร้อมข้อความสำเร็จ"
                ),
            },
            {
                "id": "SSD-SET-02",
                "title": "ตรวจสอบและส่งแจ้งเตือนหมดอายุ",
                "description": (
                    "Scheduler ตรวจสอบอย่างเป็นระยะ ดึงผลประเมินที่ยังไม่หมดอายุ "
                    "คำนวณเวลาที่เหลือสำหรับแต่ละผลประเมิน "
                    "ดึงผู้ใช้ที่เปิดแจ้งเตือนตามช่วงเวลา (6 เดือน, 3 เดือน, 1 เดือน, 1 สัปดาห์) "
                    "แล้วส่งอีเมลแจ้งเตือนหมดอายุให้ผู้ใช้ที่ตรงเงื่อนไข"
                ),
            },
        ],
    },
]


# ============================================================
# Helper functions
# ============================================================

def _setup_styles(doc):
    """Set up Thai font styles."""
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


def _add_cover(doc, title, subtitle):
    """Add a cover page."""
    for _ in range(6):
        doc.add_paragraph("")
    cover = doc.add_paragraph()
    cover.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = cover.add_run(title)
    run.font.size = Pt(28)
    run.font.bold = True
    run.font.name = "TH SarabunPSK"

    doc.add_paragraph("")
    sub = doc.add_paragraph()
    sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run2 = sub.add_run(subtitle)
    run2.font.size = Pt(18)
    run2.font.name = "TH SarabunPSK"
    doc.add_page_break()


def _find_ssd_image(ssd_id):
    """Find SSD PNG file matching given ID (e.g. 'SSD-AUTH-01')."""
    if not os.path.isdir(SSD_IMG_DIR):
        return None
    for fname in os.listdir(SSD_IMG_DIR):
        if fname.upper().startswith(ssd_id.upper()):
            return os.path.join(SSD_IMG_DIR, fname)
    return None


def _match_files(pattern, all_files):
    """Match screenshot files to a pattern (substring match with wildcard support)."""
    # Convert URL pattern to filename fragment
    # e.g. "admin-academic-request-*-document-" matches files containing that pattern
    pattern_clean = pattern.replace("*", "")

    matched = []
    for f in all_files:
        fname = os.path.basename(f).lower()
        # Remove the "screencapture-localhost-8080-" prefix and date suffix
        core = fname.replace("screencapture-localhost-8080-", "").rsplit("-2026-", 1)[0]
        if pattern_clean in core:
            matched.append(f)
    return sorted(matched)


# ============================================================
# Generate Screenshot Descriptions DOCX
# ============================================================

def generate_screenshot_docx():
    doc = Document()
    _setup_styles(doc)
    _add_cover(
        doc,
        "คำอธิบายภาพหน้าจอ\n(Screenshot Descriptions)",
        "ระบบบริหารจัดการงานวิชาการ\nHRCP-KKU-Academic\n\n"
        "คณะวิทยาศาสตร์ มหาวิทยาลัยขอนแก่น",
    )

    # Collect all PNG files
    all_files = sorted(glob.glob(os.path.join(SCREENSHOTS_DIR, "*.png")))
    used_files = set()
    fig_num = 0
    group_num = 0

    for group in SCREENSHOT_GROUPS:
        group_num += 1
        doc.add_heading(group["name"], level=1)
        local_num = 0

        for pattern, base_caption, detail in group["pattern_captions"]:
            matched = _match_files(pattern, all_files)
            # Exclude files already used (for overlapping patterns)
            matched = [f for f in matched if f not in used_files]

            for filepath in matched:
                used_files.add(filepath)
                fig_num += 1
                local_num += 1

                # Determine specific caption
                fname = os.path.basename(filepath)
                core = fname.replace("screencapture-localhost-8080-", "").rsplit("-2026-", 1)[0]

                # Extract doc type number if present
                caption = base_caption
                if "-document-" in core:
                    parts = core.split("-document-")
                    if len(parts) > 1 and parts[1].isdigit():
                        doc_type = parts[1]
                        caption = caption.replace("ตามประเภทเอกสาร", f"ประเภทที่ {doc_type}")
                        caption = caption.replace("ตามประเภท", f"ประเภทที่ {doc_type}")

                # Add picture
                try:
                    doc.add_picture(filepath, width=Cm(15))
                    # Center the picture
                    last_paragraph = doc.paragraphs[-1]
                    last_paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
                except Exception as e:
                    p = doc.add_paragraph()
                    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
                    run = p.add_run(f"[ไม่สามารถโหลดรูป: {fname}]")
                    run.font.color.rgb = RGBColor(200, 0, 0)

                # Add caption
                cap = doc.add_paragraph()
                cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
                run_cap = cap.add_run(f"รูปที่ {group_num}.{local_num} {caption}")
                run_cap.font.italic = True
                run_cap.font.size = Pt(12)
                run_cap.font.color.rgb = RGBColor(80, 80, 80)
                run_cap.font.name = "TH SarabunPSK"

                # Add detail description
                if detail:
                    det = doc.add_paragraph()
                    run_det = det.add_run(detail)
                    run_det.font.size = Pt(14)
                    run_det.font.name = "TH SarabunPSK"

                doc.add_paragraph("")  # spacing

        doc.add_page_break()

    # Check for unused files
    unused = [f for f in all_files if f not in used_files and not f.endswith(".gitkeep")]
    if unused:
        doc.add_heading("ภาพหน้าจอเพิ่มเติม", level=1)
        for filepath in unused:
            fig_num += 1
            fname = os.path.basename(filepath)
            core = fname.replace("screencapture-localhost-8080-", "").rsplit("-2026-", 1)[0]

            try:
                doc.add_picture(filepath, width=Cm(15))
                doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
            except Exception:
                pass

            cap = doc.add_paragraph()
            cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
            run_cap = cap.add_run(f"รูปที่ {fig_num} {core}")
            run_cap.font.italic = True
            run_cap.font.size = Pt(12)
            run_cap.font.color.rgb = RGBColor(80, 80, 80)
            run_cap.font.name = "TH SarabunPSK"
            doc.add_paragraph("")

    return doc


# ============================================================
# Generate SSD Descriptions DOCX
# ============================================================

def generate_ssd_docx():
    doc = Document()
    _setup_styles(doc)
    _add_cover(
        doc,
        "คำอธิบาย System Sequence Diagram\n(SSD Descriptions)",
        "ระบบบริหารจัดการงานวิชาการ\nHRCP-KKU-Academic\n\n"
        "คณะวิทยาศาสตร์ มหาวิทยาลัยขอนแก่น",
    )

    # Table of contents
    doc.add_heading("สารบัญ", level=1)
    total = 0
    for section in SSD_SECTIONS:
        p = doc.add_paragraph(section["name"])
        p.runs[0].bold = True
        for diag in section["diagrams"]:
            total += 1
            doc.add_paragraph(f"    {diag['id']}: {diag['title']}", style="Normal")
    doc.add_paragraph("")
    p_total = doc.add_paragraph(f"รวมทั้งหมด {total} แผนภาพ")
    p_total.runs[0].italic = True
    doc.add_page_break()

    # Content
    for section in SSD_SECTIONS:
        doc.add_heading(section["name"], level=1)

        for diag in section["diagrams"]:
            doc.add_heading(f"{diag['id']}: {diag['title']}", level=2)

            # Insert SSD image + caption
            img_path = _find_ssd_image(diag["id"])
            if img_path:
                try:
                    doc.add_picture(img_path, width=Cm(15))
                    doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
                    cap = doc.add_paragraph()
                    cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
                    run_cap = cap.add_run(f"{diag['id']}: {diag['title']}")
                    run_cap.font.italic = True
                    run_cap.font.size = Pt(12)
                    run_cap.font.color.rgb = RGBColor(80, 80, 80)
                    run_cap.font.name = "TH SarabunPSK"
                except Exception:
                    pass

            desc_para = doc.add_paragraph()
            desc_para.style = doc.styles["Normal"]
            run = desc_para.add_run(diag["description"])
            run.font.size = Pt(14)
            run.font.name = "TH SarabunPSK"

            doc.add_paragraph("")  # spacing

        doc.add_page_break()

    return doc


# ============================================================
# Main
# ============================================================

if __name__ == "__main__":
    # Generate screenshot descriptions
    doc1 = generate_screenshot_docx()
    path1 = os.path.join(DOCS_DIR, "screenshot-descriptions.docx")
    doc1.save(path1)

    all_files = glob.glob(os.path.join(SCREENSHOTS_DIR, "*.png"))
    print(f"Generated: {path1}")
    print(f"  Total screenshots found: {len(all_files)}")

    # Generate SSD descriptions
    doc2 = generate_ssd_docx()
    path2 = os.path.join(DOCS_DIR, "ssd-descriptions.docx")
    doc2.save(path2)

    total_ssd = sum(len(s["diagrams"]) for s in SSD_SECTIONS)
    print(f"Generated: {path2}")
    print(f"  Total SSDs: {total_ssd}")
