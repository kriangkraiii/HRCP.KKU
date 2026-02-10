package com.ecom.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service สำหรับสร้าง PromptPay QR Code ผ่าน promptpay.io
 * 
 * ใช้ API ของ promptpay.io สร้าง QR Code:
 * - URL format: https://promptpay.io/{promptpay_id}/{amount}.png
 * - ตัวอย่าง: https://promptpay.io/1468700018206/100.50.png
 * - รองรับทั้งเลขบัตรประชาชน (13 หลัก) และเบอร์โทร (10 หลัก)
 * - QR Code สร้างตามมาตรฐาน EMVCo โดยอัตโนมัติ
 */
@Service
public class PromptPayService {

    private static final String PROMPTPAY_IO_BASE_URL = "https://promptpay.io";

    @Value("${promptpay.id}")
    private String promptPayId;

    /**
     * สร้าง URL สำหรับ QR Code รูปภาพ (.png) จาก promptpay.io
     * URL: https://promptpay.io/{id}/{amount}.png
     */
    public String generateQrImageUrl(double amount) {
        String amountStr = String.format("%.2f", amount);
        return PROMPTPAY_IO_BASE_URL + "/" + promptPayId + "/" + amountStr + ".png";
    }

    /**
     * สร้าง URL สำหรับหน้า promptpay.io (มี QR + ข้อมูล)
     * URL: https://promptpay.io/{id}/{amount}
     */
    public String generatePromptPayPageUrl(double amount) {
        String amountStr = String.format("%.2f", amount);
        return PROMPTPAY_IO_BASE_URL + "/" + promptPayId + "/" + amountStr;
    }

    /**
     * สร้าง URL สำหรับ QR Code แบบไม่มีจำนวนเงิน (Static QR)
     */
    public String generateStaticQrImageUrl() {
        return PROMPTPAY_IO_BASE_URL + "/" + promptPayId + ".png";
    }

    /**
     * ดึง PromptPay ID
     */
    public String getPromptPayId() {
        return promptPayId;
    }
}
