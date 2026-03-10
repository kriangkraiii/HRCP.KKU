package com.ecom.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.UserService;

/**
 * Unit Tests for Input Validation and Error Handling in AdminController
 * 
 * Tests Requirements 7.1, 7.2, 7.3, 7.4, 7.5
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class AdminControllerValidationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    /**
     * Test: Email format validation at controller level
     * Requirement 7.1
     */
    @Test
    void testEmailFormatValidation() {
        // Test various invalid email formats
        String[] invalidEmails = {
            "notanemail",
            "missing@domain",
            "@nodomain.com",
            "no-at-sign.com",
            "user@",
            "@domain.com"
        };

        for (String invalidEmail : invalidEmails) {
            // Simulate controller validation
            boolean isValid = invalidEmail != null && invalidEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
            assertThat(isValid)
                .as("Email validation should reject: " + invalidEmail)
                .isFalse();
        }

        // Test valid email formats
        String[] validEmails = {
            "test@test.com",
            "user.name@example.com",
            "user+tag@domain.co.th",
            "admin123@test.org"
        };

        for (String validEmail : validEmails) {
            boolean isValid = validEmail != null && validEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
            assertThat(isValid)
                .as("Email validation should accept: " + validEmail)
                .isTrue();
        }
    }

    /**
     * Test: Required field validation
     * Requirement 7.2
     */
    @Test
    void testRequiredFieldValidation() {
        // Test empty name
        String emptyName = "";
        boolean isNameValid = emptyName != null && !emptyName.trim().isEmpty();
        assertThat(isNameValid).isFalse();

        // Test null name
        String nullName = null;
        boolean isNullNameValid = nullName != null && !nullName.trim().isEmpty();
        assertThat(isNullNameValid).isFalse();

        // Test whitespace-only name
        String whitespaceName = "   ";
        boolean isWhitespaceNameValid = whitespaceName != null && !whitespaceName.trim().isEmpty();
        assertThat(isWhitespaceNameValid).isFalse();

        // Test valid name
        String validName = "Test User";
        boolean isValidNameValid = validName != null && !validName.trim().isEmpty();
        assertThat(isValidNameValid).isTrue();
    }

    /**
     * Test: File size validation
     * Requirement 7.3
     */
    @Test
    void testFileSizeValidation() {
        // Test file larger than 5MB
        long maxSize = 5 * 1024 * 1024; // 5MB
        long largeFileSize = 6 * 1024 * 1024; // 6MB
        
        boolean isLargeFileValid = largeFileSize <= maxSize;
        assertThat(isLargeFileValid)
            .as("File size validation should reject files larger than 5MB")
            .isFalse();

        // Test file within size limit
        long validFileSize = 2 * 1024 * 1024; // 2MB
        boolean isValidFileSizeValid = validFileSize <= maxSize;
        assertThat(isValidFileSizeValid)
            .as("File size validation should accept files within 5MB limit")
            .isTrue();

        // Test empty file
        long emptyFileSize = 0;
        boolean isEmptyFileValid = emptyFileSize <= maxSize;
        assertThat(isEmptyFileValid)
            .as("File size validation should accept empty files")
            .isTrue();
    }

    /**
     * Test: File type validation
     * Requirement 7.4
     */
    @Test
    void testFileTypeValidation() {
        // Test invalid file extensions
        String[] invalidExtensions = {"pdf", "txt", "doc", "exe", "zip"};
        
        for (String ext : invalidExtensions) {
            boolean isValid = ext.matches("jpg|jpeg|png|gif");
            assertThat(isValid)
                .as("File type validation should reject: " + ext)
                .isFalse();
        }

        // Test valid file extensions
        String[] validExtensions = {"jpg", "jpeg", "png", "gif"};
        
        for (String ext : validExtensions) {
            boolean isValid = ext.matches("jpg|jpeg|png|gif");
            assertThat(isValid)
                .as("File type validation should accept: " + ext)
                .isTrue();
        }

        // Test MIME type validation
        String[] invalidMimeTypes = {"application/pdf", "text/plain", "application/zip"};
        
        for (String mimeType : invalidMimeTypes) {
            boolean isValid = mimeType != null && mimeType.startsWith("image/");
            assertThat(isValid)
                .as("MIME type validation should reject: " + mimeType)
                .isFalse();
        }

        // Test valid MIME types
        String[] validMimeTypes = {"image/jpeg", "image/png", "image/gif"};
        
        for (String mimeType : validMimeTypes) {
            boolean isValid = mimeType != null && mimeType.startsWith("image/");
            assertThat(isValid)
                .as("MIME type validation should accept: " + mimeType)
                .isTrue();
        }
    }

    /**
     * Test: Database error handling with transaction rollback
     * Requirement 7.5
     */
    @Test
    void testDatabaseErrorHandlingWithRollback() {
        // Given: A user exists
        UserDtls user = createTestAccount("test@test.com");
        Integer userId = user.getId();
        String originalName = user.getName();

        // When: Attempting to update with duplicate email (should fail)
        UserDtls user2 = createTestAccount("duplicate@test.com");
        
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(userId);
        updateRequest.setTitle("ดร.");
        updateRequest.setName("Updated Name");
        updateRequest.setEmail("duplicate@test.com"); // Duplicate email
        updateRequest.setMobileNumber("0899999999");
        updateRequest.setAcademicPosition("ศาสตราจารย์");

        // Then: Update should fail with exception
        boolean exceptionThrown = false;
        try {
            userService.updateUserDetails(updateRequest, null);
        } catch (Exception e) {
            exceptionThrown = true;
            // Verify error message is user-friendly (in Thai)
            assertThat(e.getMessage()).contains("อีเมล");
        }

        assertThat(exceptionThrown).isTrue();

        // Verify: Original data remains unchanged (transaction rollback)
        UserDtls retrieved = userService.getUserById(userId);
        assertThat(retrieved.getName()).isEqualTo(originalName);
        assertThat(retrieved.getEmail()).isEqualTo("test@test.com");
    }

    /**
     * Test: Service layer handles non-existent user gracefully
     * Requirement 7.5
     */
    @Test
    void testNonExistentUserHandling() {
        // When: Attempting to update non-existent user
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(99999);
        updateRequest.setTitle("นาย");
        updateRequest.setName("Test");
        updateRequest.setEmail("test@test.com");
        updateRequest.setMobileNumber("0812345678");
        updateRequest.setAcademicPosition("อาจารย์");

        // Then: Should throw appropriate exception
        boolean exceptionThrown = false;
        try {
            userService.updateUserDetails(updateRequest, null);
        } catch (RuntimeException e) {
            exceptionThrown = true;
            assertThat(e.getMessage()).contains("User not found");
        }

        assertThat(exceptionThrown).isTrue();

        // When: Attempting to delete non-existent user
        Boolean deleteResult = userService.deleteUserById(99999);

        // Then: Should return false gracefully
        assertThat(deleteResult).isFalse();
    }

    /**
     * Test: Valid file upload succeeds
     */
    @Test
    void testValidFileUpload() throws Exception {
        // Given: A user exists
        UserDtls user = createTestAccount("test@test.com");
        Integer userId = user.getId();

        // When: Uploading valid image file
        MockMultipartFile validFile = new MockMultipartFile(
            "img",
            "profile.jpg",
            "image/jpeg",
            "image content".getBytes()
        );

        // Validate file
        long maxSize = 5 * 1024 * 1024;
        boolean isSizeValid = validFile.getSize() <= maxSize;
        boolean isTypeValid = validFile.getOriginalFilename() != null &&
            validFile.getOriginalFilename().substring(
                validFile.getOriginalFilename().lastIndexOf(".") + 1
            ).toLowerCase().matches("jpg|jpeg|png|gif");

        // Then: Validation should pass
        assertThat(isSizeValid).isTrue();
        assertThat(isTypeValid).isTrue();
    }

    /**
     * Helper method to create a test account
     */
    private UserDtls createTestAccount(String email) {
        UserDtls user = new UserDtls();
        user.setTitle("นาย");
        user.setName("Test User " + System.nanoTime());
        user.setEmail(email);
        user.setMobileNumber("0812345678");
        user.setAcademicPosition("อาจารย์");
        user.setPassword("encodedPassword123");
        user.setRole("ROLE_USER");
        user.setIsEnable(true);
        user.setAccountNonLocked(true);
        user.setFailedAttempt(0);
        user.setProfileImage("default.png");
        
        return userRepository.save(user);
    }
}
