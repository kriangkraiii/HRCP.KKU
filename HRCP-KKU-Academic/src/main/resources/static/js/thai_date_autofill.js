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
    var NO_AUTOFILL = [
        'birth_date',
        'lecturer_appointment_date',
        'assistant_appointment_date',
        'associate_appointment_date',
        'currentpositiondate',
        'book_publish_date'
    ];

    // ชื่อเดือนภาษาไทย
    var THAI_MONTHS = [
        'มกราคม', 'กุมภาพันธ์', 'มีนาคม', 'เมษายน', 'พฤษภาคม', 'มิถุนายน',
        'กรกฎาคม', 'สิงหาคม', 'กันยายน', 'ตุลาคม', 'พฤศจิกายน', 'ธันวาคม'
    ];

    // ตัวเลขไทย
    var THAI_DIGITS = ['๐', '๑', '๒', '๓', '๔', '๕', '๖', '๗', '๘', '๙'];

    function toThaiDigits(num) {
        return String(num).split('').map(function(d) { return THAI_DIGITS[parseInt(d)] || d; }).join('');
    }

    function formatThaiDate(dateObj) {
        var day = toThaiDigits(dateObj.getDate());
        var month = THAI_MONTHS[dateObj.getMonth()];
        var year = toThaiDigits(dateObj.getFullYear() + 543);
        return 'วันที่ ' + day + ' ' + month + ' พ.ศ. ' + year;
    }

    function shouldSkipAutofill(name) {
        return NO_AUTOFILL.some(function(ex) { return name.includes(ex); });
    }

    function isDateField(input) {
        var name = input.name || '';
        return /date/i.test(name);
    }

    function syncPickerToText(picker, textInput) {
        if (!picker.value) return;
        var parts = picker.value.split('-');
        if (parts.length !== 3) return;
        var selected = new Date(parseInt(parts[0]), parseInt(parts[1]) - 1, parseInt(parts[2]));
        textInput.value = formatThaiDate(selected);
    }

    // หาทุก form ในหน้า
    var forms = document.querySelectorAll('form');
    if (!forms.length) return;

    var today = new Date();
    var todayThai = formatThaiDate(today);
    var isoToday = today.toISOString().split('T')[0];

    forms.forEach(function (form) {
        // หา input ที่เป็นวันที่
        var inputs = form.querySelectorAll('input[type="text"]');
        inputs.forEach(function (input) {
            if (!isDateField(input)) return;

            var skipAutoFill = shouldSkipAutofill(input.name);

            // ถ้าไม่อยู่ใน NO_AUTOFILL → ใส่วันปัจจุบัน (ถ้ายังว่าง)
            if (!skipAutoFill && (!input.value || input.value.trim() === '')) {
                input.value = todayThai;
            }

            // เปลี่ยน placeholder
            input.placeholder = 'เช่น ' + todayThai;

            // เพิ่ม date picker ข้าง input
            var picker = document.createElement('input');
            picker.type = 'date';
            picker.className = 'form-control form-control-sm';
            picker.style.cssText = 'width:auto;display:inline-block;max-width:180px;margin-left:8px;vertical-align:middle;';
            picker.title = 'เลือกวันที่';
            if (!skipAutoFill) picker.value = isoToday;

            // Event-based (works on some browsers)
            picker.addEventListener('change', function() { syncPickerToText(picker, input); });
            picker.addEventListener('input', function() { syncPickerToText(picker, input); });

            // Polling-based fallback (macOS date picker doesn't fire events reliably)
            var lastKnownValue = picker.value;
            var pollTimer = null;

            function startPolling() {
                if (pollTimer) return;
                pollTimer = setInterval(function() {
                    if (picker.value !== lastKnownValue) {
                        lastKnownValue = picker.value;
                        syncPickerToText(picker, input);
                    }
                }, 300);
            }

            function stopPolling() {
                // Delay stop to catch final value after popup closes
                setTimeout(function() {
                    if (picker.value !== lastKnownValue) {
                        lastKnownValue = picker.value;
                        syncPickerToText(picker, input);
                    }
                    clearInterval(pollTimer);
                    pollTimer = null;
                }, 500);
            }

            picker.addEventListener('focus', startPolling);
            picker.addEventListener('click', startPolling);
            picker.addEventListener('blur', stopPolling);

            // ใส่ picker ข้าง input
            input.parentNode.insertBefore(picker, input.nextSibling);
        });
    });
})();
