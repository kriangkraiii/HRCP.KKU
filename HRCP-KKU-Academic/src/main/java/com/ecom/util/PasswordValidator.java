package com.ecom.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Password validation utility following international standards (NIST SP 800-63B).
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

    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?`~]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    /**
     * Validate password strength.
     *
     * @return null if valid, or error message (Thai) if invalid
     */
    public static String validate(String password) {
        if (password == null || password.isEmpty()) {
            return "กรุณากรอกรหัสผ่าน";
        }

        List<String> errors = new ArrayList<>();

        if (password.length() < MIN_LENGTH) {
            errors.add("อย่างน้อย " + MIN_LENGTH + " ตัวอักษร");
        }
        if (password.length() > MAX_LENGTH) {
            errors.add("ไม่เกิน " + MAX_LENGTH + " ตัวอักษร");
        }
        if (WHITESPACE.matcher(password).find()) {
            errors.add("ห้ามมีช่องว่าง");
        }
        if (!UPPERCASE.matcher(password).find()) {
            errors.add("ตัวอักษรพิมพ์ใหญ่อย่างน้อย 1 ตัว (A-Z)");
        }
        if (!LOWERCASE.matcher(password).find()) {
            errors.add("ตัวอักษรพิมพ์เล็กอย่างน้อย 1 ตัว (a-z)");
        }
        if (!DIGIT.matcher(password).find()) {
            errors.add("ตัวเลขอย่างน้อย 1 ตัว (0-9)");
        }
        if (!SPECIAL.matcher(password).find()) {
            errors.add("อักขระพิเศษอย่างน้อย 1 ตัว (!@#$%^&*...)");
        }

        if (errors.isEmpty()) {
            return null;
        }

        return "รหัสผ่านไม่ผ่านเกณฑ์: " + String.join(", ", errors);
    }
}
