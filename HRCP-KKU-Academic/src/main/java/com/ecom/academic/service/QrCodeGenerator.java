package com.ecom.academic.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * Renders the QR code printed on a fully-signed document.
 *
 * <p>Uses the zxing library already declared in the POM for other features.
 */
@Component
public class QrCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(QrCodeGenerator.class);

    private static final int SIZE_PX = 320;

    /**
     * A QR code as PNG bytes.
     *
     * <p>Error correction is set high because this ends up photocopied, scanned
     * and faxed like any other university document, and a code that stops
     * scanning after one photocopy is no use.
     *
     * @return PNG bytes, or null if the code could not be produced
     */
    public byte[] pngFor(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            BitMatrix matrix = new QRCodeWriter().encode(
                    content, BarcodeFormat.QR_CODE, SIZE_PX, SIZE_PX,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H,
                            EncodeHintType.MARGIN, 1));

            BufferedImage image = new BufferedImage(SIZE_PX, SIZE_PX, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < SIZE_PX; x++) {
                for (int y = 0; y < SIZE_PX; y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException | com.google.zxing.WriterException e) {
            log.warn("Could not generate QR code: {}", e.toString());
            return null;
        }
    }
}
