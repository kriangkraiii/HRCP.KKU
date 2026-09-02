# ภาคผนวก — แผนภาพสถานะ (State Diagrams)

---

## 1. สถานะคำร้องประเมินผลการสอน (Academic Request — Phase 1)

| สถานะ | ชื่อภาษาไทย | คำอธิบาย |
|-------|-------------|---------|
| DRAFT | แบบร่าง | คำร้องถูกสร้างแล้วแต่ยังไม่ส่ง |
| RECEIVED | รับคำร้อง | ผู้ดูแลระบบรับคำร้องเข้าสู่กระบวนการ |
| SUB_COMMITTEE_APPOINTED | แต่งตั้งอนุกรรมการ | แต่งตั้งคณะอนุกรรมการประเมิน |
| MEETING_SCHEDULED | นัดหมายวันประชุม | กำหนดวันประชุมพิจารณา |
| COMPLETED_PASS | แจ้งผล - ผ่าน | ผลการประเมินผ่าน |
| COMPLETED_REVISE | แจ้งผล - แก้ไข | ต้องแก้ไขเอกสารเพิ่มเติม |
| COMPLETED_FAIL | แจ้งผล - ไม่ผ่าน | ผลการประเมินไม่ผ่าน (สิ้นสุดกระบวนการ) |
| REJECTED | ไม่รับคำร้อง | คำร้องถูกปฏิเสธ (สิ้นสุดกระบวนการ) |
| COMPLETED | เสร็จสิ้น | กระบวนการเสร็จสมบูรณ์ |

```mermaid
stateDiagram-v2
    [*] --> DRAFT : สร้างคำร้อง
    DRAFT --> RECEIVED : ส่งคำร้อง
    RECEIVED --> SUB_COMMITTEE_APPOINTED : แต่งตั้งอนุกรรมการ
    RECEIVED --> REJECTED : ไม่รับคำร้อง
    SUB_COMMITTEE_APPOINTED --> MEETING_SCHEDULED : นัดหมายวันประชุม
    MEETING_SCHEDULED --> COMPLETED_PASS : แจ้งผล - ผ่าน
    MEETING_SCHEDULED --> COMPLETED_REVISE : แจ้งผล - แก้ไข
    MEETING_SCHEDULED --> COMPLETED_FAIL : แจ้งผล - ไม่ผ่าน
    COMPLETED_PASS --> COMPLETED : ดำเนินการเสร็จสิ้น
    COMPLETED_REVISE --> COMPLETED : แก้ไขเสร็จ/ดำเนินการเสร็จสิ้น
    REJECTED --> [*]
    COMPLETED_FAIL --> [*]
    COMPLETED --> [*]

    state DRAFT {
        [*] : แบบร่าง
    }
    state RECEIVED {
        [*] : รับคำร้อง
    }
    state SUB_COMMITTEE_APPOINTED {
        [*] : แต่งตั้งอนุกรรมการ
    }
    state MEETING_SCHEDULED {
        [*] : นัดหมายวันประชุม
    }
    state COMPLETED_PASS {
        [*] : แจ้งผล - ผ่าน
    }
    state COMPLETED_REVISE {
        [*] : แจ้งผล - แก้ไข
    }
    state COMPLETED_FAIL {
        [*] : แจ้งผล - ไม่ผ่าน
    }
    state REJECTED {
        [*] : ไม่รับคำร้อง
    }
    state COMPLETED {
        [*] : เสร็จสิ้น
    }
```

---

## 2. สถานะคำร้องขอกำหนดตำแหน่ง (Position Request — Phase 2)

| สถานะ | ชื่อภาษาไทย | คำอธิบาย |
|-------|-------------|---------|
| DRAFT | แบบร่าง | คำร้องถูกสร้างแล้วแต่ยังไม่ส่ง |
| DOCUMENT_RECEIVED | รับคำร้อง | รับเอกสารเข้าสู่กระบวนการ |
| DOCUMENT_VERIFICATION | ตรวจสอบความถูกต้อง/ครบถ้วน | ตรวจสอบเอกสารทั้งหมด |
| SCREENING_COMMITTEE | เสนอวาระกลั่นกรองฯ | เสนอต่อคณะกรรมการกลั่นกรอง |
| REVISION_REQUESTED | ส่งแก้ไข | ส่งกลับให้แก้ไขเอกสาร |
| SCREENING_APPROVED | รับรองมติกลั่นกรองฯ | คณะกรรมการกลั่นกรองรับรอง |
| COLLEGE_COMMITTEE | เสนอวาระคณะกรรมการวิทยาลัยฯ | เสนอต่อคณะกรรมการวิทยาลัย |
| COLLEGE_APPROVED | รับรองมติคณะกรรมการวิทยาลัยฯ | คณะกรรมการวิทยาลัยรับรอง |
| SENT_TO_HR | ส่งออกกองทรัพยากรบุคคล มข. | ส่งเรื่องไปยังกองทรัพยากรบุคคล (สิ้นสุด) |

```mermaid
stateDiagram-v2
    [*] --> DRAFT : สร้างคำร้อง
    DRAFT --> DOCUMENT_RECEIVED : ส่งคำร้อง
    DOCUMENT_RECEIVED --> DOCUMENT_VERIFICATION : เริ่มตรวจสอบ
    DOCUMENT_VERIFICATION --> SCREENING_COMMITTEE : เอกสารครบถ้วน
    SCREENING_COMMITTEE --> REVISION_REQUESTED : ส่งแก้ไข
    SCREENING_COMMITTEE --> SCREENING_APPROVED : รับรองมติกลั่นกรอง
    REVISION_REQUESTED --> SCREENING_COMMITTEE : แก้ไขเสร็จ
    SCREENING_APPROVED --> COLLEGE_COMMITTEE : เสนอวาระวิทยาลัย
    COLLEGE_COMMITTEE --> COLLEGE_APPROVED : รับรองมติวิทยาลัย
    COLLEGE_APPROVED --> SENT_TO_HR : ส่งออก กองทรัพยากรบุคคล
    SENT_TO_HR --> [*]

    state DRAFT {
        [*] : แบบร่าง
    }
    state DOCUMENT_RECEIVED {
        [*] : รับคำร้อง
    }
    state DOCUMENT_VERIFICATION {
        [*] : ตรวจสอบความถูกต้อง
    }
    state SCREENING_COMMITTEE {
        [*] : เสนอวาระกลั่นกรองฯ
    }
    state REVISION_REQUESTED {
        [*] : ส่งแก้ไข
    }
    state SCREENING_APPROVED {
        [*] : รับรองมติกลั่นกรองฯ
    }
    state COLLEGE_COMMITTEE {
        [*] : เสนอวาระคณะกรรมการวิทยาลัยฯ
    }
    state COLLEGE_APPROVED {
        [*] : รับรองมติวิทยาลัยฯ
    }
    state SENT_TO_HR {
        [*] : ส่งออก กองทรัพยากรบุคคล
    }
```

---

## 3. สถานะคำร้องทั่วไป (Petition)

| สถานะ | ชื่อภาษาไทย | คำอธิบาย |
|-------|-------------|---------|
| RECEIVED | รับคำร้อง | รับคำร้องเข้าสู่ระบบ |
| COMMITTEE_ASSIGNED | แต่งตั้งอนุกรรมการ | แต่งตั้งคณะกรรมการพิจารณา |
| MEETING_SCHEDULED | นัดหมายวันประชุม | กำหนดวันประชุมพิจารณา |
| RESULT_APPROVED | แจ้งผล - ผ่าน | คำร้องได้รับการอนุมัติ |
| RESULT_REVISION | แจ้งผล - แก้ไข | ต้องแก้ไขเพิ่มเติม |
| REJECTED | ไม่รับคำร้อง | คำร้องถูกปฏิเสธ |
| COMPLETED | เสร็จสิ้น | กระบวนการเสร็จสมบูรณ์ |

```mermaid
stateDiagram-v2
    [*] --> RECEIVED : ยื่นคำร้อง
    RECEIVED --> COMMITTEE_ASSIGNED : แต่งตั้งอนุกรรมการ
    RECEIVED --> REJECTED : ไม่รับคำร้อง
    COMMITTEE_ASSIGNED --> MEETING_SCHEDULED : นัดหมายวันประชุม
    MEETING_SCHEDULED --> RESULT_APPROVED : แจ้งผล - ผ่าน
    MEETING_SCHEDULED --> RESULT_REVISION : แจ้งผล - แก้ไข
    MEETING_SCHEDULED --> REJECTED : ไม่รับคำร้อง
    RESULT_APPROVED --> COMPLETED : ดำเนินการเสร็จสิ้น
    RESULT_REVISION --> COMPLETED : แก้ไขเสร็จ
    REJECTED --> [*]
    COMPLETED --> [*]

    state RECEIVED {
        [*] : รับคำร้อง
    }
    state COMMITTEE_ASSIGNED {
        [*] : แต่งตั้งอนุกรรมการ
    }
    state MEETING_SCHEDULED {
        [*] : นัดหมายวันประชุม
    }
    state RESULT_APPROVED {
        [*] : แจ้งผล - ผ่าน
    }
    state RESULT_REVISION {
        [*] : แจ้งผล - แก้ไข
    }
    state REJECTED {
        [*] : ไม่รับคำร้อง
    }
    state COMPLETED {
        [*] : เสร็จสิ้น
    }
```
