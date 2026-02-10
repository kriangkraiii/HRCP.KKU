package com.ecom.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.model.Category;
import com.ecom.model.Transaction;
import com.ecom.model.UserDtls;
import com.ecom.model.Wallet;
import com.ecom.model.WalletTransfer;
import com.ecom.repository.UserRepository;
import com.ecom.service.CartService;
import com.ecom.service.CategoryService;
import com.ecom.service.UserService;
import com.ecom.service.WalletService;
import com.ecom.service.WalletService.TopUpResult;
import com.ecom.service.WalletService.TransferResult;

/**
 * Controller สำหรับ Digital Wallet
 * 
 * Endpoints:
 * - GET  /user/wallet                   → หน้า Wallet หลัก (Thymeleaf)
 * - GET  /user/wallet/topup/qr          → สร้าง QR Code (PNG image)
 * - POST /user/wallet/topup/verify      → ตรวจสอบสลิปและเติมเงิน
 * - GET  /user/wallet/balance           → ดึงยอดเงินคงเหลือ (JSON)
 * - GET  /user/wallet/transactions      → ดึงประวัติธุรกรรม (JSON)
 * - GET  /user/wallet/transfer/search   → ค้นหาผู้รับจากอีเมล
 * - POST /user/wallet/transfer          → โอนเงินให้ User อื่น
 * - GET  /user/wallet/transfers         → ดึงประวัติการโอนเงิน (JSON)
 */
@Controller
@RequestMapping("/user/wallet")
public class WalletController {

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private CartService cartService;

    @Autowired
    private CategoryService categoryService;

    // ========== @ModelAttribute: ให้ navbar มีข้อมูลผู้ใช้ ==========
    @ModelAttribute
    public void getUserDetails(Principal p, Model m) {
        if (p != null) {
            try {
                String email = p.getName();
                UserDtls userDtls = userService.getUserByEmail(email);
                if (userDtls != null) {
                    m.addAttribute("user", userDtls);
                    Integer countCart = cartService.getCountCart(userDtls.getId());
                    m.addAttribute("countCart", countCart != null ? countCart : 0);
                }
            } catch (Exception e) {
                e.printStackTrace();
                m.addAttribute("countCart", 0);
            }
        }
        try {
            List<Category> allActiveCategory = categoryService.getAllActiveCategory();
            m.addAttribute("categorys", allActiveCategory != null ? allActiveCategory : new ArrayList<>());
        } catch (Exception e) {
            e.printStackTrace();
            m.addAttribute("categorys", new ArrayList<>());
        }
    }

    // ==================== PAGES ====================

    /**
     * หน้า Wallet หลัก (Thymeleaf view)
     */
    @GetMapping
    public String walletPage(Principal principal, Model model) {
        UserDtls user = getLoggedInUser(principal);
        Wallet wallet = walletService.getOrCreateWallet(user);
        List<Transaction> transactions = walletService.getTransactionHistory(user);

        List<WalletTransfer> transfers = walletService.getTransferHistory(user);

        model.addAttribute("wallet", wallet);
        model.addAttribute("transactions", transactions);
        model.addAttribute("transfers", transfers);
        model.addAttribute("user", user);
        return "user/wallet";
    }

    // ==================== QR CODE ENDPOINTS ====================

    /**
     * GET /user/wallet/topup/qr?amount=xxx
     * Redirect ไปยัง promptpay.io เพื่อดึง QR Code PNG โดยตรง
     */
    @GetMapping("/topup/qr")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateQrCode(@RequestParam("amount") double amount) {
        Map<String, Object> response = new HashMap<>();
        if (amount <= 0 || amount > 100000) {
            response.put("success", false);
            response.put("message", "จำนวนเงินไม่ถูกต้อง (ต้อง 0.01 - 100,000 บาท)");
            return ResponseEntity.badRequest().body(response);
        }

        String qrImageUrl = walletService.generateQrImageUrl(amount);
        String pageUrl = walletService.generatePromptPayPageUrl(amount);

        response.put("success", true);
        response.put("amount", amount);
        response.put("qrImageUrl", qrImageUrl);
        response.put("promptPayPageUrl", pageUrl);
        response.put("message", String.format("สแกน QR เพื่อโอนเงิน %.2f บาท", amount));
        return ResponseEntity.ok(response);
    }

    // ==================== TOP UP VERIFICATION ====================

    /**
     * POST /user/wallet/topup/verify
     * ตรวจสอบสลิปและเติมเงิน
     * 
     * Parameters:
     * - file: รูปสลิป (MultipartFile)
     * - amount: จำนวนเงินที่ต้องการเติม
     */
    @PostMapping("/topup/verify")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyTopUp(
            @RequestParam("file") MultipartFile file,
            @RequestParam("amount") double amount,
            Principal principal) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Validation
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("message", "กรุณาอัพโหลดรูปสลิป");
                return ResponseEntity.badRequest().body(response);
            }

            if (amount <= 0 || amount > 100000) {
                response.put("success", false);
                response.put("message", "จำนวนเงินไม่ถูกต้อง");
                return ResponseEntity.badRequest().body(response);
            }

            // ตรวจสอบ file type
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                response.put("success", false);
                response.put("message", "กรุณาอัพโหลดไฟล์รูปภาพเท่านั้น (JPEG, PNG)");
                return ResponseEntity.badRequest().body(response);
            }

            UserDtls user = getLoggedInUser(principal);

            // เรียก Service เพื่อตรวจสอบและเติมเงิน
            TopUpResult result = walletService.processTopUp(user, file, amount);

            if (result.isSuccess()) {
                response.put("success", true);
                response.put("message", "เติมเงินสำเร็จ!");
                response.put("amount", amount);
                response.put("newBalance", result.getNewBalance());
                response.put("transactionId", result.getTransaction().getId());
                response.put("refTransactionId", result.getTransaction().getRefTransactionId());
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", result.getMessage());
                if (result.getTransaction() != null) {
                    response.put("transactionId", result.getTransaction().getId());
                }
                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "เกิดข้อผิดพลาดภายใน: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ==================== WALLET INFO ====================

    /**
     * GET /user/wallet/balance
     * ดึงยอดเงินคงเหลือ
     */
    @GetMapping("/balance")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getBalance(Principal principal) {
        UserDtls user = getLoggedInUser(principal);
        Wallet wallet = walletService.getOrCreateWallet(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("balance", wallet.getBalance());
        response.put("totalTopup", wallet.getTotalTopup());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /user/wallet/transactions
     * ดึงประวัติธุรกรรม
     */
    @GetMapping("/transactions")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTransactions(Principal principal) {
        UserDtls user = getLoggedInUser(principal);
        List<Transaction> transactions = walletService.getTransactionHistory(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("transactions", transactions.stream().map(t -> {
            Map<String, Object> tx = new HashMap<>();
            tx.put("id", t.getId());
            tx.put("amount", t.getAmount());
            tx.put("status", t.getStatus().name());
            tx.put("senderName", t.getSenderName());
            tx.put("receiverName", t.getReceiverName());
            tx.put("refTransactionId", t.getRefTransactionId());
            tx.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
            tx.put("verifiedAt", t.getVerifiedAt() != null ? t.getVerifiedAt().toString() : null);
            tx.put("failureReason", t.getFailureReason());
            return tx;
        }).toList());
        return ResponseEntity.ok(response);
    }

    // ==================== WALLET TRANSFER ====================

    /**
     * GET /user/wallet/transfer/search?email=xxx
     * ค้นหาผู้รับจากอีเมล (สำหรับ AJAX)
     */
    @GetMapping("/transfer/search")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> searchUser(@RequestParam("email") String email, Principal principal) {
        Map<String, Object> response = new HashMap<>();

        if (email == null || email.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "กรุณาระบุอีเมล");
            return ResponseEntity.badRequest().body(response);
        }

        UserDtls currentUser = getLoggedInUser(principal);
        UserDtls receiver = walletService.findUserByEmail(email.trim());

        if (receiver == null) {
            response.put("success", false);
            response.put("message", "ไม่พบผู้ใช้ที่มีอีเมลนี้");
            return ResponseEntity.ok(response);
        }

        if (currentUser.getId().equals(receiver.getId())) {
            response.put("success", false);
            response.put("message", "ไม่สามารถโอนเงินให้ตัวเองได้");
            return ResponseEntity.ok(response);
        }

        response.put("success", true);
        response.put("userId", receiver.getId());
        response.put("name", receiver.getName());
        response.put("email", receiver.getEmail());
        response.put("profileImage", receiver.getProfileImage());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /user/wallet/transfer
     * โอนเงินให้ User อื่น
     *
     * Parameters:
     * - receiverEmail: อีเมลผู้รับ
     * - amount: จำนวนเงิน
     * - note: หมายเหตุ (optional)
     */
    @PostMapping("/transfer")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> transferMoney(
            @RequestParam("receiverEmail") String receiverEmail,
            @RequestParam("amount") double amount,
            @RequestParam(value = "note", required = false) String note,
            Principal principal) {

        Map<String, Object> response = new HashMap<>();

        try {
            UserDtls sender = getLoggedInUser(principal);
            TransferResult result = walletService.transferToUser(sender, receiverEmail.trim(), amount, note);

            if (result.isSuccess()) {
                response.put("success", true);
                response.put("message", result.getMessage());
                response.put("amount", amount);
                response.put("newBalance", result.getNewBalance());
                response.put("receiverName", result.getReceiverName());
                response.put("transferId", result.getTransfer().getId());
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", result.getMessage());
                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "เกิดข้อผิดพลาดภายใน: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * GET /user/wallet/transfers
     * ดึงประวัติการโอนเงิน (JSON)
     */
    @GetMapping("/transfers")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTransferHistory(Principal principal) {
        UserDtls user = getLoggedInUser(principal);
        List<WalletTransfer> transfers = walletService.getTransferHistory(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("transfers", transfers.stream().map(t -> {
            Map<String, Object> tx = new HashMap<>();
            tx.put("id", t.getId());
            tx.put("amount", t.getAmount());
            tx.put("status", t.getStatus().name());
            tx.put("senderName", t.getSender().getName());
            tx.put("senderEmail", t.getSender().getEmail());
            tx.put("receiverName", t.getReceiver().getName());
            tx.put("receiverEmail", t.getReceiver().getEmail());
            tx.put("note", t.getNote());
            tx.put("isSender", t.getSender().getId().equals(user.getId()));
            tx.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
            tx.put("failureReason", t.getFailureReason());
            return tx;
        }).toList());
        return ResponseEntity.ok(response);
    }

    // ==================== HELPER ====================

    private UserDtls getLoggedInUser(Principal principal) {
        String email = principal.getName();
        return userRepository.findByEmail(email);
    }
}
