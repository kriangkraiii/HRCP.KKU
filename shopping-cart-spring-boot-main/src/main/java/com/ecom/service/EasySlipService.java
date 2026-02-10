package com.ecom.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service สำหรับเรียก EasySlip API เพื่อตรวจสอบสลิปการโอนเงิน
 * 
 * EasySlip API:
 * - Endpoint: https://developer.easyslip.com/api/v1/verify
 * - Method: POST (multipart/form-data)
 * - Authorization: Bearer {API_KEY}
 * 
 * Response JSON structure (เมื่อสำเร็จ):
 * {
 *   "status": 200,
 *   "data": {
 *     "transRef": "...",
 *     "date": "...",
 *     "amount": { "amount": 100.00 },
 *     "sender": {
 *       "bank": { "id": "...", "name": "..." },
 *       "account": { "name": { "th": "...", "en": "..." } }
 *     },
 *     "receiver": {
 *       "bank": { "id": "...", "name": "..." },
 *       "account": { "name": { "th": "...", "en": "..." } }
 *     }
 *   }
 * }
 */
@Service
public class EasySlipService {

    private static final Logger log = LoggerFactory.getLogger(EasySlipService.class);

    @Value("${easyslip.api.url}")
    private String apiUrl;

    @Value("${easyslip.api.key}")
    private String apiKey;

    @Value("${easyslip.receiver.bank.id:}")
    private String expectedReceiverBankId;

    @Value("${easyslip.receiver.account.name:}")
    private String expectedReceiverName;

    @Value("${promptpay.id}")
    private String expectedPromptPayId;

    private final RestTemplate restTemplate;

    public EasySlipService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * ส่งรูปสลิปไปยัง EasySlip API เพื่อตรวจสอบ
     */
    @SuppressWarnings("unchecked")
    public EasySlipResponse verifySlip(MultipartFile file) {
        try {
            // สร้าง Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(apiKey);

            // สร้าง Body (multipart/form-data)
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // เรียก API
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                return EasySlipResponse.failed("ไม่ได้รับ Response จาก EasySlip API");
            }

            // ===== LOG RAW RESPONSE เพื่อ debug =====
            log.info("===== EasySlip API Raw Response =====");
            log.info("Full response: {}", responseBody);

            // ตรวจสอบ status
            Object statusObj = responseBody.get("status");
            int status = 0;
            if (statusObj instanceof Integer) {
                status = (Integer) statusObj;
            } else if (statusObj instanceof Number) {
                status = ((Number) statusObj).intValue();
            }

            if (status != 200) {
                String errorMsg = "EasySlip API ตอบกลับ status: " + status;
                if (responseBody.containsKey("message")) {
                    errorMsg += " - " + responseBody.get("message");
                }
                return EasySlipResponse.failed(errorMsg);
            }

            // Parse data
            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            if (data == null) {
                return EasySlipResponse.failed("ไม่พบข้อมูลในสลิป (data is null)");
            }

            // Parse amount
            Map<String, Object> amountObj = (Map<String, Object>) data.get("amount");
            double slipAmount = 0.0;
            if (amountObj != null) {
                Object amountVal = amountObj.get("amount");
                if (amountVal instanceof Number) {
                    slipAmount = ((Number) amountVal).doubleValue();
                }
            }

            // Parse transRef
            String transRef = data.get("transRef") != null ? data.get("transRef").toString() : "";

            // Parse sender
            String senderName = "";
            Map<String, Object> sender = (Map<String, Object>) data.get("sender");
            if (sender != null) {
                Map<String, Object> senderAccount = (Map<String, Object>) sender.get("account");
                if (senderAccount != null) {
                    Map<String, Object> senderNameObj = (Map<String, Object>) senderAccount.get("name");
                    if (senderNameObj != null) {
                        senderName = senderNameObj.get("th") != null
                                ? senderNameObj.get("th").toString()
                                : (senderNameObj.get("en") != null ? senderNameObj.get("en").toString() : "");
                    }
                }
            }

            // Parse receiver — รองรับหลายรูปแบบ JSON ที่ EasySlip อาจส่งกลับมา
            String receiverBankId = "";
            String receiverBankName = "";
            String receiverName = "";
            String receiverProxyAccount = ""; // เลขพร้อมเพย์ผู้รับ
            Map<String, Object> receiver = (Map<String, Object>) data.get("receiver");
            if (receiver != null) {
                log.info("Receiver data: {}", receiver);

                // แบบที่ 1: receiver.bank.id
                Map<String, Object> receiverBank = (Map<String, Object>) receiver.get("bank");
                if (receiverBank != null) {
                    log.info("Receiver bank: {}", receiverBank);
                    receiverBankId = safeString(receiverBank.get("id"));
                    receiverBankName = safeString(receiverBank.get("name"));
                    // บาง response ใช้ short_name
                    if (receiverBankId.isEmpty()) {
                        receiverBankId = safeString(receiverBank.get("short_name"));
                    }
                    if (receiverBankId.isEmpty()) {
                        receiverBankId = safeString(receiverBank.get("code"));
                    }
                }

                // แบบที่ 2: receiver.account.name
                Map<String, Object> receiverAccount = (Map<String, Object>) receiver.get("account");
                if (receiverAccount != null) {
                    log.info("Receiver account: {}", receiverAccount);
                    Object nameObj = receiverAccount.get("name");
                    if (nameObj instanceof Map) {
                        Map<String, Object> receiverNameMap = (Map<String, Object>) nameObj;
                        receiverName = safeString(receiverNameMap.get("th"));
                        if (receiverName.isEmpty()) {
                            receiverName = safeString(receiverNameMap.get("en"));
                        }
                    } else if (nameObj instanceof String) {
                        receiverName = (String) nameObj;
                    }

                    // แบบที่ 4: receiver.account.proxy (สำหรับ PromptPay)
                    Map<String, Object> receiverProxy = (Map<String, Object>) receiverAccount.get("proxy");
                    if (receiverProxy != null) {
                        log.info("Receiver proxy: {}", receiverProxy);
                        receiverProxyAccount = safeString(receiverProxy.get("account"));
                        log.info("Receiver PromptPay ID (masked): {}", receiverProxyAccount);
                    }
                }

                // แบบที่ 3: receiver.displayName (บาง response มี field นี้)
                if (receiverName.isEmpty()) {
                    receiverName = safeString(receiver.get("displayName"));
                }
                if (receiverName.isEmpty()) {
                    receiverName = safeString(receiver.get("name"));
                }
            } else {
                log.warn("Receiver data is null");
            }

            log.info("Parsed => amount={}, transRef={}, senderName={}, receiverName={}, receiverBankId={}, receiverBankName={}, receiverProxyAccount={}",
                    slipAmount, transRef, senderName, receiverName, receiverBankId, receiverBankName, receiverProxyAccount);

            return EasySlipResponse.success(slipAmount, transRef, senderName, receiverName, receiverBankId, receiverBankName, receiverProxyAccount);

        } catch (Exception e) {
            return EasySlipResponse.failed("เกิดข้อผิดพลาดในการเรียก EasySlip API: " + e.getMessage());
        }
    }

    /**
     * ตรวจสอบว่าข้อมูลในสลิปตรงกับบัญชีผู้รับหรือไม่
     * 
     * ตรวจเฉพาะ 3 อย่าง:
     * 1. จำนวนเงินตรงกับที่ร้องขอ
     * 2. Transaction ID ไม่ซ้ำ (เช็คใน WalletService)
     * 3. เลขพร้อมเพย์ผู้รับตรงกับของเรา (บางส่วน เพราะ API mask)
     */
    public SlipValidationResult validateSlip(MultipartFile file, double expectedAmount) {
        EasySlipResponse slipData = verifySlip(file);

        if (!slipData.isSuccess()) {
            return SlipValidationResult.failed(slipData.getErrorMessage());
        }

        // ===== 1. ตรวจจำนวนเงิน =====
        if (Math.abs(slipData.getAmount() - expectedAmount) > 0.01) {
            return SlipValidationResult.failed(
                    String.format("จำนวนเงินไม่ตรงกัน: สลิป = %.2f บาท, ที่ร้องขอ = %.2f บาท",
                            slipData.getAmount(), expectedAmount));
        }
        log.info("✓ Amount matched: {} THB", slipData.getAmount());

        // ===== 2. Transaction ID (จะเช็คซ้ำใน WalletService) =====
        if (slipData.getTransRef() == null || slipData.getTransRef().isEmpty()) {
            log.warn("Transaction ID is empty");
        } else {
            log.info("✓ Transaction ID: {}", slipData.getTransRef());
        }

        // ===== 3. ตรวจเลขพร้อมเพย์ผู้รับ =====
        String proxyAccount = slipData.getReceiverProxyAccount();
        if (proxyAccount != null && !proxyAccount.isEmpty()) {
            // EasySlip mask เลขเป็น x-xxxx-xxxx8-20-6
            // เราต้องตรวจว่าส่วนท้ายตรงกับของเรา (1468700018206)
            String normalized = proxyAccount.replaceAll("[^0-9]", ""); // ลบ x, dash ออก
            String expectedNormalized = expectedPromptPayId.replaceAll("[^0-9]", "");

            // ตรวจว่า normalized มีตัวเลขจากของเราบางส่วน
            boolean proxyMatch = false;
            if (normalized.length() >= 4) {
                // ดึงตัวเลขที่ไม่ใช่ x ออกมา
                String visibleDigits = proxyAccount.replaceAll("[^0-9]", "");
                // ตรวจว่าตัวเลขที่เห็นมีในเลขพร้อมเพย์ของเราหรือไม่
                if (expectedNormalized.contains(visibleDigits) || visibleDigits.length() >= 4) {
                    // เช็คว่าตัวเลข 4 หลักสุดท้ายตรงกัน (ถ้ามี)
                    if (visibleDigits.length() >= 4) {
                        String lastDigits = visibleDigits.substring(Math.max(0, visibleDigits.length() - 4));
                        String expectedLast = expectedNormalized.substring(Math.max(0, expectedNormalized.length() - 4));
                        if (lastDigits.equals(expectedLast)) {
                            proxyMatch = true;
                        }
                    } else {
                        // มีตัวเลขน้อยเกิน ให้ผ่านไปก่อน
                        proxyMatch = true;
                    }
                }
            }

            if (proxyMatch) {
                log.info("✓ PromptPay ID matched: slip='{}' (masked), expected='{}'", proxyAccount, expectedPromptPayId);
                return SlipValidationResult.success(slipData);
            } else {
                return SlipValidationResult.failed(
                        String.format("เลขพร้อมเพย์ผู้รับไม่ตรงกัน: สลิป = '%s', ที่คาดหวัง = '%s'",
                                proxyAccount, expectedPromptPayId));
            }
        }

        // ถ้าไม่มี proxy account แต่มีชื่อผู้รับ → ตรวจชื่อ (fallback)
        String recvName = slipData.getReceiverName();
        if (recvName != null && !recvName.isEmpty()) {
            String normalizedRecvName = recvName.toLowerCase().trim().replaceAll("\\s+", "");
            String normalizedExpected = expectedReceiverName.toLowerCase().trim().replaceAll("\\s+", "");

            // ตรวจว่ามีคำซ้ำกันบางส่วน
            if (normalizedRecvName.contains(normalizedExpected.substring(0, Math.min(5, normalizedExpected.length())))
                    || normalizedExpected.contains(normalizedRecvName.substring(0, Math.min(5, normalizedRecvName.length())))) {
                log.info("✓ Receiver name matched (fallback): slip='{}', expected='{}'", recvName, expectedReceiverName);
                return SlipValidationResult.success(slipData);
            }

            return SlipValidationResult.failed(
                    String.format("ผู้รับไม่ตรงกัน: สลิป = '%s', ที่คาดหวัง = '%s'", recvName, expectedReceiverName));
        }

        // ไม่มีข้อมูลผู้รับเลย → ให้ผ่าน (ตรวจแค่ราคาและ transRef)
        log.warn("No receiver info available — validation passed (amount + transRef only)");
        return SlipValidationResult.success(slipData);
    }


    /**
     * Safe string — ป้องกัน null
     */
    private String safeString(Object obj) {
        return obj != null ? obj.toString().trim() : "";
    }

    // ====================== Inner Classes ======================

    /**
     * Response จาก EasySlip API (parsed)
     */
    public static class EasySlipResponse {
        private boolean success;
        private String errorMessage;
        private double amount;
        private String transRef;
        private String senderName;
        private String receiverName;
        private String receiverBankId;
        private String receiverBankName;
        private String receiverProxyAccount; // เลขพร้อมเพย์ผู้รับ (masked)

        public static EasySlipResponse success(double amount, String transRef,
                String senderName, String receiverName, String receiverBankId, 
                String receiverBankName, String receiverProxyAccount) {
            EasySlipResponse r = new EasySlipResponse();
            r.success = true;
            r.amount = amount;
            r.transRef = transRef;
            r.senderName = senderName;
            r.receiverName = receiverName;
            r.receiverBankId = receiverBankId;
            r.receiverBankName = receiverBankName;
            r.receiverProxyAccount = receiverProxyAccount;
            return r;
        }

        public static EasySlipResponse failed(String errorMessage) {
            EasySlipResponse r = new EasySlipResponse();
            r.success = false;
            r.errorMessage = errorMessage;
            return r;
        }

        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public double getAmount() { return amount; }
        public String getTransRef() { return transRef; }
        public String getSenderName() { return senderName; }
        public String getReceiverName() { return receiverName; }
        public String getReceiverBankId() { return receiverBankId; }
        public String getReceiverBankName() { return receiverBankName; }
        public String getReceiverProxyAccount() { return receiverProxyAccount; }
    }

    /**
     * ผลลัพธ์การตรวจสอบความถูกต้องของสลิป
     */
    public static class SlipValidationResult {
        private boolean valid;
        private String errorMessage;
        private EasySlipResponse slipData;

        public static SlipValidationResult success(EasySlipResponse slipData) {
            SlipValidationResult r = new SlipValidationResult();
            r.valid = true;
            r.slipData = slipData;
            return r;
        }

        public static SlipValidationResult failed(String errorMessage) {
            SlipValidationResult r = new SlipValidationResult();
            r.valid = false;
            r.errorMessage = errorMessage;
            return r;
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
        public EasySlipResponse getSlipData() { return slipData; }
    }
}
