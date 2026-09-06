package com.ecom.visual;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/**
 * เทียบภาพสองภาพแบบพิกเซลต่อพิกเซล และเขียนภาพส่วนต่างไว้ให้คนดู
 *
 * <p>มีไว้เพราะบั๊กสองหมวดที่เจอบ่อยที่สุดในโปรเจกต์นี้ไม่มีทางจับได้จากการอ่านโค้ด
 * หรืออ่าน markup: ลายเซ็นที่ถูกตัดขอบในเอกสาร PDF และหน้าจอที่เพี้ยนในโหมดมืด
 * ทั้งสองอย่างต้อง <b>เรนเดอร์ออกมาแล้วดู</b> เท่านั้น
 *
 * <p>ตัวอย่างที่ชัดที่สุดคือ {@code SignatureStampingTest} ซึ่งยืนยันสตริง
 * {@code w:lineRule="exact"} ในไฟล์ XML ว่าถูกต้อง ทั้งที่สตริงนั้นเองคือสาเหตุที่
 * ลายเซ็นถูกเฉือนหายไปหนึ่งในสามตอนแปลงเป็น PDF เทสเขียวสนิทมาตลอด
 *
 * <p><b>วิธีอัปเดต baseline</b> เมื่อแก้หน้าตาโดยตั้งใจ:
 * {@code ./mvnw test -Dvisual.update=true}
 */
final class ImageDiff {

    /** ที่เก็บภาพอ้างอิงที่ commit ไว้ */
    static final Path BASELINE_DIR = Path.of("src", "test", "resources", "visual");

    /** ที่เขียนภาพจริงและภาพส่วนต่างเมื่อไม่ตรง — CI เก็บโฟลเดอร์นี้เป็น artifact */
    static final Path OUTPUT_DIR = Path.of("target", "visual");

    /**
     * ความต่างของค่าสีต่อช่องที่ยอมได้ ก่อนจะนับว่าพิกเซลนั้น "ต่าง"
     *
     * <p>ไม่ใช่ศูนย์เพราะการเรนเดอร์ตัวอักษรมีการปรับความคมของขอบ (anti-aliasing)
     * ที่ต่างกันได้เล็กน้อยระหว่างเครื่อง ค่านี้กันเสียงรบกวนระดับนั้นออกไป
     * โดยยังจับความต่างที่ตามองเห็นได้ทุกกรณี
     */
    private static final int CHANNEL_TOLERANCE = 12;

    private ImageDiff() {
    }

    /** ผลการเทียบหนึ่งครั้ง */
    record Result(double percentDifferent, String detail) {
        boolean withinTolerance(double allowedPercent) {
            return percentDifferent <= allowedPercent;
        }
    }

    static boolean updatingBaselines() {
        return Boolean.getBoolean("visual.update");
    }

    /**
     * เทียบภาพกับ baseline ที่ชื่อ {@code name}
     *
     * <p>ถ้ายังไม่มี baseline หรือรันด้วย {@code -Dvisual.update=true} จะเขียน
     * baseline ใหม่แล้วคืนผลว่าไม่ต่าง — การรันครั้งแรกจึงสร้างชุดอ้างอิงให้เอง
     * และเป็นหน้าที่ของคนรีวิวที่จะดูภาพเหล่านั้นก่อน commit
     */
    static Result compareWithBaseline(String name, BufferedImage actual) {
        Path baseline = BASELINE_DIR.resolve(name + ".png");

        if (updatingBaselines() || !Files.exists(baseline)) {
            write(baseline, actual);
            return new Result(0, "เขียน baseline ใหม่ที่ " + baseline);
        }

        BufferedImage expected = read(baseline);
        if (expected.getWidth() != actual.getWidth() || expected.getHeight() != actual.getHeight()) {
            Path actualPath = OUTPUT_DIR.resolve(name + "-actual.png");
            write(actualPath, actual);
            return new Result(100, String.format(
                    "ขนาดภาพไม่เท่ากัน: baseline %dx%d แต่ได้ %dx%d (ภาพจริงอยู่ที่ %s)",
                    expected.getWidth(), expected.getHeight(),
                    actual.getWidth(), actual.getHeight(), actualPath));
        }

        BufferedImage diff = new BufferedImage(
                actual.getWidth(), actual.getHeight(), BufferedImage.TYPE_INT_RGB);
        long different = 0;

        for (int y = 0; y < actual.getHeight(); y++) {
            for (int x = 0; x < actual.getWidth(); x++) {
                if (pixelsDiffer(expected.getRGB(x, y), actual.getRGB(x, y))) {
                    different++;
                    diff.setRGB(x, y, 0xFF0000); // จุดที่ต่าง ทำเป็นสีแดงให้เห็นชัด
                } else {
                    diff.setRGB(x, y, fade(actual.getRGB(x, y)));
                }
            }
        }

        double percent = 100.0 * different / (actual.getWidth() * (long) actual.getHeight());
        if (different == 0) {
            return new Result(0, "ตรงกับ baseline");
        }

        Path actualPath = OUTPUT_DIR.resolve(name + "-actual.png");
        Path diffPath = OUTPUT_DIR.resolve(name + "-diff.png");
        write(actualPath, actual);
        write(diffPath, diff);

        return new Result(percent, String.format(
                "ต่างจาก baseline %.3f%% (%d พิกเซล)%n  baseline: %s%n  ภาพจริง : %s%n  ส่วนต่าง: %s",
                percent, different, baseline, actualPath, diffPath));
    }

    private static boolean pixelsDiffer(int a, int b) {
        return Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) > CHANNEL_TOLERANCE
                || Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)) > CHANNEL_TOLERANCE
                || Math.abs((a & 0xFF) - (b & 0xFF)) > CHANNEL_TOLERANCE;
    }

    /** ทำให้ส่วนที่เหมือนกันจางลง เพื่อให้จุดสีแดงเด่นขึ้นในภาพส่วนต่าง */
    private static int fade(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int grey = (r + g + b) / 3;
        int washed = 200 + grey * 55 / 255;
        return (washed << 16) | (washed << 8) | washed;
    }

    static void write(Path target, BufferedImage image) {
        try {
            Files.createDirectories(target.getParent());
            ImageIO.write(image, "png", target.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("เขียนภาพไม่สำเร็จ: " + target, e);
        }
    }

    private static BufferedImage read(Path source) {
        try {
            return ImageIO.read(source.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("อ่านภาพไม่สำเร็จ: " + source, e);
        }
    }
}
