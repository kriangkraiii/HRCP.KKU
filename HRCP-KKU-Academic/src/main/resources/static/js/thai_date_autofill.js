/**
 * Thai Date Auto-fill Script
 * 
 * ใส่ค่าเริ่มต้นวันที่ปัจจุบัน (รูปแบบไทย พ.ศ.) ให้กับ input ที่ชื่อมี date
 * field ที่อยู่ใน NO_AUTOFILL จะได้ date picker แต่ไม่ auto-fill วันปัจจุบัน
 * 
 * รันทันที (IIFE) เพราะ script โหลดท้าย body — DOM พร้อมแล้ว
 */
(function () {
    // ชื่อ field ที่ได้ picker แต่ไม่ auto-fill วันปัจจุบัน (เป็นวันเฉพาะที่ต้องเลือกเอง)
    const NO_AUTOFILL = [
        'birth_date',
        'lecturer_appointment_date',
        'assistant_appointment_date',
        'associate_appointment_date',
        'currentpositiondate',
        'book_publish_date'
    ];

    // ชื่อเดือนภาษาไทย
    const THAI_MONTHS = [
        'มกราคม', 'กุมภาพันธ์', 'มีนาคม', 'เมษายน', 'พฤษภาคม', 'มิถุนายน',
        'กรกฎาคม', 'สิงหาคม', 'กันยายน', 'ตุลาคม', 'พฤศจิกายน', 'ธันวาคม'
    ];

    // ตัวเลขไทย
    const THAI_DIGITS = ['๐', '๑', '๒', '๓', '๔', '๕', '๖', '๗', '๘', '๙'];

    function toThaiDigits(num) {
        return String(num).split('').map(d => THAI_DIGITS[parseInt(d)] || d).join('');
    }

    function formatThaiDate(dateObj) {
        const day = toThaiDigits(dateObj.getDate());
        const month = THAI_MONTHS[dateObj.getMonth()];
        const year = toThaiDigits(dateObj.getFullYear() + 543);
        return `วันที่ ${day} ${month} พ.ศ. ${year}`;
    }

    function shouldSkipAutofill(name) {
        return NO_AUTOFILL.some(ex => name.includes(ex));
    }

    function isDateField(input) {
        const name = input.name || '';
        if (!name.match(/date/i)) return false;
        return true;
    }

    // หาทุก form ในหน้า
    const forms = document.querySelectorAll('form');
    if (!forms.length) return;

    const today = new Date();
    const todayThai = formatThaiDate(today);
    const isoToday = today.toISOString().split('T')[0];

    forms.forEach(function (form) {
        // หา input ที่เป็นวันที่
        const inputs = form.querySelectorAll('input[type="text"]');
        inputs.forEach(function (input) {
            if (!isDateField(input)) return;

            const skipAutoFill = shouldSkipAutofill(input.name);

            // ถ้าไม่อยู่ใน NO_AUTOFILL → ใส่วันปัจจุบัน (ถ้ายังว่าง)
            if (!skipAutoFill && (!input.value || input.value.trim() === '')) {
                input.value = todayThai;
            }

            // เปลี่ยน placeholder
            input.placeholder = 'เช่น ' + todayThai;

            // เพิ่ม date picker ข้าง input
            const picker = document.createElement('input');
            picker.type = 'date';
            picker.className = 'form-control form-control-sm';
            picker.style.cssText = 'width:auto;display:inline-block;max-width:180px;margin-left:8px;vertical-align:middle;';
            picker.title = 'เลือกวันที่';
            if (!skipAutoFill) picker.value = isoToday;

            picker.addEventListener('change', function () {
                if (this.value) {
                    const parts = this.value.split('-');
                    const selected = new Date(
                        parseInt(parts[0]),
                        parseInt(parts[1]) - 1,
                        parseInt(parts[2])
                    );
                    input.value = formatThaiDate(selected);
                }
            });

            // ใส่ picker ข้าง input
            input.parentNode.insertBefore(picker, input.nextSibling);
        });
    });
})();
