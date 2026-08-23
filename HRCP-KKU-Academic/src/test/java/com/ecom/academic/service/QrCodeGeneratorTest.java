package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

/**
 * The verification QR printed on signed documents.
 *
 * <p>Decodes what it produces rather than only checking that bytes came back:
 * a QR that renders but does not scan is worse than none at all, because it
 * appears on an official document as if it worked.
 */
@DisplayName("QR ตรวจสอบเอกสาร")
class QrCodeGeneratorTest {

    private final QrCodeGenerator generator = new QrCodeGenerator();

    @Test
    @DisplayName("สแกนกลับได้ URL เดิมทุกตัวอักษร")
    void decodesBackToTheSameUrl() throws Exception {
        String url = "https://hrcp.kku.ac.th/esign/verify/ABCD234XYZ";

        byte[] png = generator.pngFor(url);
        assertThat(png).isNotEmpty();

        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).isNotNull();

        var decoded = new MultiFormatReader().decode(
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))));
        assertThat(decoded.getText()).isEqualTo(url);
    }

    @Test
    @DisplayName("ไม่มีเนื้อหา ต้องไม่สร้าง QR")
    void refusesEmptyContent() {
        assertThat(generator.pngFor(null)).isNull();
        assertThat(generator.pngFor("   ")).isNull();
    }
}
