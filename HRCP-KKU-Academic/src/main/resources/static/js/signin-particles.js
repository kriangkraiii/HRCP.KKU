/*
 * ลูกเล่นหน้าเข้าสู่ระบบ: สนามจุดแบบหน้าแรกของ antigravity.google
 *
 * ดูจากของจริงแล้วมันทำงานแบบนี้ และตรงนี้ทำตามนั้น:
 * - ทั้งจอมีจุดเทาเล็ก ๆ กระจายห่าง ๆ แบบสุ่ม (ไม่ใช่ตาราง)
 * - รอบเคอร์เซอร์จุดจะกลายเป็นขีดสีที่หันออกจากศูนย์กลางแบบหลวม ๆ — ไม่จัดเรียงเป็นวง ไม่เป็นรัศมีเป๊ะ
 *   ทุกตัวมีความยาว มุมเอียง สี และระยะเหลื่อมต่างกัน ภาพจึงไม่ออกมาเป็นลายดาวกระจาย
 *   เป็นวงแหวน: แถวเมาส์โปร่ง ขีดเล็กจาง ยิ่งออกไปยิ่งยาวและเข้ม แล้วค่อย ๆ กลืนเป็นจุดเทาทั่วจอ
 *   รอยต่อจากวงสีไปจุดเทาไม่มีแถบจางคั่น — ความเข้มไม่มีทางต่ำกว่าจุดเทารอบ ๆ
 * - สีไล่ตามทิศรอบศูนย์กลาง (ฟ้า ม่วง แดง ส้ม เหลือง) และวงสีค่อย ๆ หมุนไปเรื่อย ๆ
 * - จุดทุกตัวลอยไปเองอย่างอิสระเหมือนฝุ่นในอากาศ: แต่ละตัวมีทิศและความเร็วของตัวเอง ทิศค่อย ๆ
 *   เลี้ยวไปเรื่อย ๆ หลุดขอบจอก็โผล่อีกฝั่ง ไม่มีคลื่นหรือจังหวะร่วมที่ทำให้ทั้งจอขยับพร้อมกันเป็นแพทเทิร์น
 * - จุดยืดหดตามการเคลื่อนไหว: ยิ่งเคลื่อนเร็วยิ่งยืดยาวไปตามทิศที่มันวิ่ง พอช้าลงก็หดกลับ
 *   (ความเร็วถูกเกลี่ยไว้หลายเฟรม การยืดหดจึงค่อย ๆ เปลี่ยน ไม่กะพริบ)
 * - เมาส์หยุดแล้วสนามก็ยังมีชีวิต: ศูนย์กลางลอยวนรอบเคอร์เซอร์ช้า ๆ
 *   (ของจริงก็ทำแบบนี้ — แคปภาพตอนเมาส์นิ่งห่างกันไม่กี่วินาที วงย้ายที่และสีหมุนไป)
 * - ความลื่นมาจากการไล่ตามแบบนุ่ม (ease) สองชั้น: ศูนย์กลางไล่ตามเมาส์ และจุดแต่ละตัวไหลไปหาที่
 *   ของมัน ไม่ใช้สปริง — สปริงทำให้จุดเลยเป้าแล้วเด้งกลับ ดูกระดุกกระดิก
 *
 * ทั้งหน้าใช้สนามเดียวกัน แต่ละฝั่ง (แผงชื่อระบบพื้นเข้ม และฝั่งฟอร์มพื้นขาว) มีผ้าใบของตัวเอง
 * อยู่ใต้เนื้อหา ฝั่งพื้นเข้มใช้สีสว่างขึ้น ขีดที่ตกหลังการ์ดฟอร์มจางลงให้อ่านช่องกรอกง่าย
 * ไม่มีเมาส์ วงแหวนลอยช้า ๆ เอง ตั้งลดการเคลื่อนไหวไว้ได้ภาพนิ่ง หยุดวาดเมื่อแท็บไม่ได้เปิด
 */
(function () {
    'use strict';

    var hosts = Array.prototype.slice.call(document.querySelectorAll('[data-signin-particles]'));
    if (!hosts.length || !window.HTMLCanvasElement) {
        return;
    }

    // สีตามทิศรอบศูนย์กลาง (องศาตามจอ: 0 = ขวา, 90 = ล่าง, 270 = บน)
    var STOPS = [
        [0, [244, 180, 0]],      // เหลือง
        [55, [250, 123, 23]],    // ส้ม
        [105, [234, 67, 53]],    // แดง
        [160, [197, 57, 160]],   // ชมพูม่วง
        [210, [132, 72, 220]],   // ม่วง
        [265, [66, 103, 234]],   // ฟ้า
        [320, [52, 140, 240]],   // ฟ้าสด
        [360, [244, 180, 0]]
    ];

    var reduceMotion = window.matchMedia
        && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    var layers = hosts.map(function (host) {
        var canvas = document.createElement('canvas');
        canvas.className = 'guest-particles';
        canvas.setAttribute('aria-hidden', 'true');
        host.insertBefore(canvas, host.firstChild);
        host.classList.add('has-particles');
        return {
            host: host,
            canvas: canvas,
            ctx: canvas.getContext('2d'),
            dark: host.hasAttribute('data-particles-dark'),
            rect: null
        };
    }).filter(function (layer) { return !!layer.ctx; });
    if (!layers.length) {
        return;
    }

    var card = document.querySelector('[data-particles-quiet]');
    var cardRect = null;

    var dpr = 1;
    var vw = 0;
    var vh = 0;
    var scale = 1;
    var particles = [];

    // ขนาดวงแหวน (ปรับตามขนาดจอใน build)
    var CORE = 95;       // แถวเมาส์โปร่ง จุดถูกดันออกนิด ๆ (ไล่ระดับ ไม่ใช่วงโล่งขอบแข็ง)
    var FULL = 270;      // ขีดยาวและเข้มที่สุดที่ระยะนี้
    var EDGE = 560;      // พ้นระยะนี้เป็นจุดเทาธรรมดา — ช่วงจางยาว ๆ ให้กลืนกับพื้นรอบ ๆ

    var pointer = { x: 0, y: 0, active: false };
    var focus = { x: 0, y: 0 };
    var running = false;
    var start = performance.now();

    function colorAt(deg) {
        deg = ((deg % 360) + 360) % 360;
        for (var i = 1; i < STOPS.length; i++) {
            if (deg <= STOPS[i][0]) {
                var a = STOPS[i - 1];
                var b = STOPS[i];
                var t = (deg - a[0]) / (b[0] - a[0]);
                return [
                    a[1][0] + (b[1][0] - a[1][0]) * t,
                    a[1][1] + (b[1][1] - a[1][1]) * t,
                    a[1][2] + (b[1][2] - a[1][2]) * t
                ];
            }
        }
        return STOPS[0][1];
    }

    function smooth(edge0, edge1, x) {
        var t = Math.min(1, Math.max(0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }

    function build() {
        vw = window.innerWidth;
        vh = window.innerHeight;
        dpr = Math.min(window.devicePixelRatio || 1, 2);
        scale = Math.max(0.7, Math.min(1.25, Math.min(vw, vh) / 850));
        measure();

        // จุดสุ่มกระจายทั่วจอแบบอิสระจริง ๆ (ไม่อิงตาราง) มีกลุ่มแน่นบ้างโล่งบ้างแบบธรรมชาติ
        // จำนวนเท่ากับตารางระยะห่าง spacing เดิม
        var spacing = 42 * scale;
        var total = Math.round(((vw + spacing) / spacing) * ((vh + spacing) / spacing));
        particles = [];
        for (var n = 0; n < total; n++) {
            var hx = Math.random() * (vw + spacing) - spacing / 2;
            var hy = Math.random() * (vh + spacing) - spacing / 2;
            particles.push({
                hx: hx, hy: hy,
                x: hx, y: hy,
                len: 1.4 + Math.random() * 0.8,
                grey: 0.24 + Math.random() * 0.22,
                // การลอยอิสระของแต่ละตัว: ทิศ ความเร็ว และจังหวะเลี้ยวไม่ซ้ำกันเลย
                heading: Math.random() * Math.PI * 2,
                speed: (3 + Math.random() * 7) * scale,
                turnRate: 0.00025 + Math.random() * 0.0005,
                turnPhase: Math.random() * Math.PI * 2,
                mx: 0, my: 0,
                // ความไม่สม่ำเสมอเฉพาะตัว — ทำให้วงรอบเมาส์ดูเป็นธรรมชาติ ไม่เป็นลายเรขาคณิต
                reachJit: 0.55 + Math.random() * 0.9,    // บางตัวติดสีจากไกลกว่า บางตัวใกล้กว่า
                tiltJit: (Math.random() - 0.5) * 1.6,    // เอียงออกจากแนวรัศมีได้ราว ±45°
                lenJit: 0.4 + Math.random() * 1.4,
                hueJit: (Math.random() - 0.5) * 50,
                pushJit: (Math.random() - 0.5) * 40,     // ในวงสีบางตัวถูกดันออกหรือดึงเข้าต่างกัน
                inkJit: 0.75 + Math.random() * 0.25
            });
        }
    }

    function measure() {
        layers.forEach(function (layer) {
            var rect = layer.host.getBoundingClientRect();
            var w = Math.max(1, Math.round(rect.width));
            var h = Math.max(1, Math.round(rect.height));
            if (!layer.rect || layer.rect.w !== w || layer.rect.h !== h) {
                layer.canvas.width = w * dpr;
                layer.canvas.height = h * dpr;
                layer.canvas.style.width = w + 'px';
                layer.canvas.style.height = h + 'px';
                layer.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
            }
            layer.rect = { x: rect.left, y: rect.top, w: w, h: h };
        });
        if (card) {
            var c = card.getBoundingClientRect();
            cardRect = { x0: c.left - 20, y0: c.top - 20, x1: c.right + 20, y1: c.bottom + 20 };
        }
    }

    // ตอนไม่มีเมาส์ วงแหวนลอยช้า ๆ ไปทั่วจอ
    function idleFocus(t) {
        var s = t / 1000;
        return {
            x: vw * (0.5 + 0.3 * Math.sin(s * 0.13)),
            y: vh * (0.5 + 0.24 * Math.sin(s * 0.19 + 1.1))
        };
    }

    // ศูนย์กลางไม่ได้ปักอยู่ที่ปลายเมาส์เป๊ะ ๆ แต่ลอยวนรอบมันช้า ๆ เป็นเส้นโค้งที่ไม่ซ้ำรอบ
    // เมาส์หยุดแล้วสนามก็ยังเคลื่อน เมาส์ขยับก็ยังตามทันเพราะวงเล็กกว่าระยะที่เมาส์ขยับ
    function orbit(t) {
        return {
            x: (80 * Math.sin(t * 0.00052) + 34 * Math.sin(t * 0.00131 + 0.7)) * scale,
            y: (62 * Math.cos(t * 0.00047) + 28 * Math.cos(t * 0.00117 + 2.1)) * scale
        };
    }

    var last = 0;

    function frame(now) {
        var t = now - start;
        var dt = last ? Math.min(50, now - last) : 16;
        last = now;
        var anchor = pointer.active ? pointer : idleFocus(t);
        var drift = orbit(t);
        var target = { x: anchor.x + drift.x, y: anchor.y + drift.y };

        // ศูนย์กลางไล่ตามเมาส์แบบนุ่ม ไม่เลยเป้า ไม่เด้ง
        focus.x += (target.x - focus.x) * 0.07;
        focus.y += (target.y - focus.y) * 0.07;

        measure();
        for (var l = 0; l < layers.length; l++) {
            layers[l].ctx.clearRect(0, 0, layers[l].rect.w, layers[l].rect.h);
            layers[l].ctx.lineCap = 'round';
        }

        var core = CORE * scale;
        var full = FULL * scale;
        var edge = EDGE * scale;
        // วงสีหมุนช้า ๆ ราว 35 องศาต่อวินาที
        var hueTurn = t * 0.035;

        var margin = 24;
        for (var i = 0; i < particles.length; i++) {
            var p = particles[i];

            // ลอยอิสระ: เลี้ยวช้า ๆ ตามจังหวะของตัวเอง แล้วเคลื่อนไปตามทิศนั้น
            p.heading += Math.sin(t * p.turnRate + p.turnPhase) * 0.0012 * dt;
            p.hx += Math.cos(p.heading) * p.speed * dt / 1000;
            p.hy += Math.sin(p.heading) * p.speed * dt / 1000;
            // หลุดขอบก็โผล่อีกฝั่ง — ย้ายตำแหน่งที่วาดไปด้วยทันที ไม่งั้นจะเห็นเป็นเส้นพาดข้ามจอ
            var wrapped = false;
            if (p.hx < -margin) { p.hx += vw + margin * 2; wrapped = true; }
            else if (p.hx > vw + margin) { p.hx -= vw + margin * 2; wrapped = true; }
            if (p.hy < -margin) { p.hy += vh + margin * 2; wrapped = true; }
            else if (p.hy > vh + margin) { p.hy -= vh + margin * 2; wrapped = true; }
            if (wrapped) {
                p.x = p.hx;
                p.y = p.hy;
            }

            var dx = p.hx - focus.x;
            var dy = p.hy - focus.y;
            var d = Math.sqrt(dx * dx + dy * dy) || 1;
            var ux = dx / d;
            var uy = dy / d;
            // ระยะที่ "รู้สึก" ต่างกันไปทีละตัว ขอบวงสีจึงขรุขระแบบธรรมชาติ ไม่เป็นวงกลมเป๊ะ
            var feel = d / p.reachJit;

            // วงแหวน: จากเมาส์ออกไป สีมาก่อน (ขีดเล็กมีสี) ขนาดโตตามทีหลัง แล้วจางลงช้า ๆ ที่ขอบนอก
            var fadeOut = 1 - smooth(full, edge, feel);
            var ring = smooth(0, full, feel) * fadeOut;
            var tint = smooth(0, full * 0.45, feel) * fadeOut;

            // แถวเมาส์ดันออกนิดหน่อยตามสัดส่วนให้โปร่ง บวกการเหลื่อมเข้าออกเฉพาะตัว
            var tr = d + core * 0.35 * (1 - smooth(0, core * 1.6, d)) + p.pushJit * scale * ring;

            var tx = focus.x + ux * tr;
            var ty = focus.y + uy * tr;

            // ไหลเข้าหาที่ของตัวเองแบบนุ่ม ไม่มีความเร็วสะสม จึงไม่เลยเป้าและไม่เด้ง
            var stepX = (tx - p.x) * 0.05;
            var stepY = (ty - p.y) * 0.05;
            p.x += stepX;
            p.y += stepY;
            // ความเร็วที่เกลี่ยแล้ว (px ต่อเฟรม) ใช้ยืดจุดไปตามทิศที่มันวิ่ง
            p.mx += (stepX - p.mx) * 0.2;
            p.my += (stepY - p.my) * 0.2;

            var radialAngle = Math.atan2(p.y - focus.y, p.x - focus.x);
            var c = colorAt(radialAngle * 180 / Math.PI + hueTurn + p.hueJit);
            // เอียงออกจากแนวรัศมีตามนิสัยของแต่ละตัว ยิ่งห่างจากวงยิ่งเอียงไปตามทิศที่มันลอยเอง
            var angle = radialAngle + p.tiltJit * (0.5 + 0.5 * ring);
            var len = p.len + ring * 4.8 * p.lenJit;
            var width = 1.5 + ring * 0.75;
            // ไม่ผสมเฉลี่ยกับจุดเทา — เดิมตรงรอยต่อความเข้มตกลงจนเห็นเป็นแถบว่างรอบวง
            var alphaInk = Math.max(p.grey, tint * (0.45 + ring * 0.45) * p.inkJit);
            if (cardRect && p.x > cardRect.x0 && p.x < cardRect.x1 && p.y > cardRect.y0 && p.y < cardRect.y1) {
                alphaInk *= 0.35;
            }
            // ยืดหดตามการเคลื่อนไหว: ยาวขึ้นตามความเร็ว และหันไปตามทิศที่วิ่งมากขึ้นเมื่อยิ่งเร็ว
            // ขีดสมมาตรหัวท้าย จึงกลับทิศการวิ่งให้อยู่ฝั่งเดียวกับแนวรัศมีก่อนผสม ไม่ให้หักมุมกลับหัว
            var rx = Math.cos(angle);
            var ry = Math.sin(angle);
            var speed = Math.sqrt(p.mx * p.mx + p.my * p.my);
            var stretch = Math.min(12 * scale, speed * 3.2);
            if (stretch > 0.05) {
                var mxu = p.mx / speed;
                var myu = p.my / speed;
                if (mxu * rx + myu * ry < 0) {
                    mxu = -mxu;
                    myu = -myu;
                }
                var w = stretch / (stretch + len);
                var bx = rx * (1 - w) + mxu * w;
                var by = ry * (1 - w) + myu * w;
                var bl = Math.sqrt(bx * bx + by * by) || 1;
                rx = bx / bl;
                ry = by / bl;
                len += stretch;
                // ยืดแล้วบางลงนิดหน่อย ให้ดูเป็นการยืดจริง ไม่ใช่แค่ยาวขึ้น
                width *= 1 - Math.min(0.3, stretch / (40 * scale));
            }
            var cx = rx * len * 0.5;
            var cy = ry * len * 0.5;

            for (var j = 0; j < layers.length; j++) {
                var layer = layers[j];
                var lx = p.x - layer.rect.x;
                var ly = p.y - layer.rect.y;
                if (lx < -8 || ly < -8 || lx > layer.rect.w + 8 || ly > layer.rect.h + 8) {
                    continue;
                }
                // จุดเทา → สี ตามน้ำหนักวงแหวน บนพื้นเข้มเทากลายเป็นขาวจาง และสีสว่างขึ้น
                var base = layer.dark ? [210, 222, 240] : [60, 64, 72];
                var lift = layer.dark ? 0.3 : 0;
                var r = base[0] + (c[0] + (255 - c[0]) * lift - base[0]) * tint;
                var g = base[1] + (c[1] + (255 - c[1]) * lift - base[1]) * tint;
                var b = base[2] + (c[2] + (255 - c[2]) * lift - base[2]) * tint;
                var a = layer.dark ? Math.min(1, alphaInk * (tint > 0.05 ? 1 : 0.8)) : alphaInk;

                var ctx = layer.ctx;
                ctx.strokeStyle = 'rgba(' + (r | 0) + ',' + (g | 0) + ',' + (b | 0) + ',' + a.toFixed(3) + ')';
                ctx.lineWidth = width;
                ctx.beginPath();
                ctx.moveTo(lx - cx, ly - cy);
                ctx.lineTo(lx + cx, ly + cy);
                ctx.stroke();
            }
        }
    }

    function loop(now) {
        if (!running) {
            return;
        }
        frame(now);
        requestAnimationFrame(loop);
    }

    function play() {
        if (running || reduceMotion || document.hidden) {
            return;
        }
        running = true;
        requestAnimationFrame(loop);
    }

    build();
    var first = idleFocus(0);
    focus.x = first.x;
    focus.y = first.y;

    if (reduceMotion) {
        // ภาพนิ่ง: ให้จุดเข้าที่ก่อนแล้ววาดครั้งเดียว
        for (var k = 0; k < 90; k++) {
            frame(start);
        }
    } else {
        window.addEventListener('pointermove', function (e) {
            pointer.x = e.clientX;
            pointer.y = e.clientY;
            pointer.active = true;
        }, { passive: true });
        document.documentElement.addEventListener('pointerleave', function () { pointer.active = false; });
        window.addEventListener('blur', function () { pointer.active = false; });
        document.addEventListener('visibilitychange', function () {
            if (document.hidden) {
                running = false;
            } else {
                play();
            }
        });
        play();
    }

    var resizeTimer = null;
    window.addEventListener('resize', function () {
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(function () {
            build();
            if (reduceMotion) {
                for (var k = 0; k < 90; k++) {
                    frame(performance.now());
                }
            }
        }, 120);
    });
})();
