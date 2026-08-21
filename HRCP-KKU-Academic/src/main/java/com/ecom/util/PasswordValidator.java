package com.ecom.util;

import java.util.ArrayList;
import java.util.List;

import org.passay.DefaultPasswordValidator;
import org.passay.PasswordData;
import org.passay.RuleResultDetail;
import org.passay.ValidationResult;
import org.passay.data.EnglishCharacterData;
import org.passay.rule.CharacterRule;
import org.passay.rule.LengthRule;
import org.passay.rule.WhitespaceRule;

/**
 * Password validation utility powered by Passay 2.0.0 following international standards (ISO/IEC 27001 / NIST SP 800-63B).
 *
 * Rules:
 * - Minimum 8 characters, maximum 128 characters
 * - At least 1 uppercase letter (A-Z)
 * - At least 1 lowercase letter (a-z)
 * - At least 1 digit (0-9)
 * - At least 1 special character (!@#$%^&*...)
 * - No whitespace allowed
 */
public final class PasswordValidator {

    private PasswordValidator() {
    }

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private static final org.passay.PasswordValidator PASSAY_VALIDATOR = new DefaultPasswordValidator(List.of(
            new LengthRule(MIN_LENGTH, MAX_LENGTH),
            new WhitespaceRule(),
            new CharacterRule(EnglishCharacterData.UpperCase, 1),
            new CharacterRule(EnglishCharacterData.LowerCase, 1),
            new CharacterRule(EnglishCharacterData.Digit, 1),
            new CharacterRule(EnglishCharacterData.Special, 1)
    ));

    /**
     * Validate password strength using Passay 2.0.0.
     *
     * @param password plaintext password
     * @return null if valid, or error message (Thai) if invalid
     */
    public static String validate(String password) {
        if (password == null || password.isEmpty()) {
            return "กรุณากรอกรหัสผ่าน";
        }

        ValidationResult result = PASSAY_VALIDATOR.validate(new PasswordData(password));
        if (result.isValid()) {
            return null;
        }

        List<String> errors = new ArrayList<>();
        for (RuleResultDetail detail : result.getDetails()) {
            String code = detail.getErrorCode();
            if (code == null) continue;

            if (LengthRule.ERROR_CODE_MIN.equals(code)) {
                errors.add("อย่างน้อย " + MIN_LENGTH + " ตัวอักษร");
            } else if (LengthRule.ERROR_CODE_MAX.equals(code)) {
                errors.add("ไม่เกิน " + MAX_LENGTH + " ตัวอักษร");
            } else if (WhitespaceRule.ERROR_CODE.equals(code)) {
                errors.add("ห้ามมีช่องว่าง");
            } else if (code.startsWith("INSUFFICIENT_") || code.contains("CHARACTER")) {
                Object ruleType = detail.getParameters().get("characterType");
                if (ruleType != null) {
                    String typeStr = ruleType.toString();
                    if (typeStr.contains("UPPERCASE")) {
                        errors.add("ตัวอักษรพิมพ์ใหญ่อย่างน้อย 1 ตัว (A-Z)");
                    } else if (typeStr.contains("LOWERCASE")) {
                        errors.add("ตัวอักษรพิมพ์เล็กอย่างน้อย 1 ตัว (a-z)");
                    } else if (typeStr.contains("DIGIT")) {
                        errors.add("ตัวเลขอย่างน้อย 1 ตัว (0-9)");
                    } else if (typeStr.contains("SPECIAL")) {
                        errors.add("อักขระพิเศษอย่างน้อย 1 ตัว (!@#$%^&*...)");
                    } else {
                        errors.add(typeStr);
                    }
                } else if (code.contains("UPPERCASE")) {
                    errors.add("ตัวอักษรพิมพ์ใหญ่อย่างน้อย 1 ตัว (A-Z)");
                } else if (code.contains("LOWERCASE")) {
                    errors.add("ตัวอักษรพิมพ์เล็กอย่างน้อย 1 ตัว (a-z)");
                } else if (code.contains("DIGIT")) {
                    errors.add("ตัวเลขอย่างน้อย 1 ตัว (0-9)");
                } else if (code.contains("SPECIAL")) {
                    errors.add("อักขระพิเศษอย่างน้อย 1 ตัว (!@#$%^&*...)");
                } else {
                    errors.add("ต้องมีตัวอักษรตามเกณฑ์ที่กำหนด");
                }
            } else {
                errors.add(code);
            }
        }

        if (errors.isEmpty()) {
            return null;
        }

        return "รหัสผ่านไม่ผ่านเกณฑ์: " + String.join(", ", errors);
    }
}
