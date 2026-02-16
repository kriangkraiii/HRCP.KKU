package com.ecom.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.UserService;

/**
 * Unit tests for AdminController's AJAX profile image update endpoint
 * 
 * Tests Requirements: 5.1, 5.2, 5.3, 5.6
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class AdminControllerUpdateProfileImageTest {

    @Autowired
    private AdminController adminController;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private UserDtls testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        
        // Create test user
        testUser = new UserDtls();
        testUser.setName("Test User");
        testUser.setEmail("testuser@example.com");
        testUser.setMobileNumber("1234567890");
        testUser.setPassword("password");
        testUser.setRole("ROLE_USER");
        testUser.setProfileImage("old-image.jpg");
        testUser.setIsEnable(true);
        testUser.setAccountNonLocked(true);
        testUser = userRepository.save(testUser);
    }

    /**
     * Test successful profile image upload returns JSON with imageUrl
     * Requirements: 5.3
     */
    @Test
    void testSuccessfulUploadReturnsJsonWithImageUrl() {
        // Given: A valid image file
        MockMultipartFile imageFile = new MockMultipartFile(
            "img", 
            "new-profile.jpg", 
            "image/jpeg", 
            "test image content".getBytes()
        );

        // When: Admin uploads the image
        ResponseEntity<Map<String, String>> response = adminController.updateProfileImage(
            testUser.getId(), 
            imageFile
        );

        // Then: Returns success response with image URL
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo("true");
        assertThat(response.getBody().get("imageUrl")).isNotNull();
        assertThat(response.getBody().get("imageUrl")).contains("/uploads/profile_img/new-profile.jpg");
        assertThat(response.getBody().get("imageName")).isEqualTo("new-profile.jpg");
        
        // Verify database was updated
        UserDtls updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        assertThat(updatedUser).isNotNull();
        assertThat(updatedUser.getProfileImage()).isEqualTo("new-profile.jpg");
    }

    /**
     * Test upload failure returns error JSON for non-existent user
     * Requirements: 5.6
     */
    @Test
    void testUploadFailureReturnsErrorJsonForNonExistentUser() {
        // Given: An image file for a non-existent user
        MockMultipartFile imageFile = new MockMultipartFile(
            "img", 
            "invalid.jpg", 
            "image/jpeg", 
            "test content".getBytes()
        );

        // When: Admin uploads the image for non-existent user
        ResponseEntity<Map<String, String>> response = adminController.updateProfileImage(
            99999, // Non-existent ID
            imageFile
        );

        // Then: Returns error response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo("false");
        assertThat(response.getBody().get("error")).isNotNull();
    }

    /**
     * Test imageUrl contains cache-busting timestamp
     * Requirements: 5.3
     */
    @Test
    void testImageUrlContainsCacheBustingTimestamp() {
        // Given: A valid image file
        MockMultipartFile imageFile = new MockMultipartFile(
            "img", 
            "profile.jpg", 
            "image/jpeg", 
            "test content".getBytes()
        );

        // When: Admin uploads the image
        ResponseEntity<Map<String, String>> response = adminController.updateProfileImage(
            testUser.getId(), 
            imageFile
        );

        // Then: Image URL contains timestamp parameter
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("imageUrl")).contains("?t=");
        
        // Verify timestamp is a valid number
        String imageUrl = response.getBody().get("imageUrl");
        String timestamp = imageUrl.substring(imageUrl.indexOf("?t=") + 3);
        assertThat(Long.parseLong(timestamp)).isGreaterThan(0);
    }

    /**
     * Test empty file upload returns error
     * Requirements: 5.6
     */
    @Test
    void testEmptyFileUploadReturnsError() {
        // Given: An empty file
        MockMultipartFile emptyFile = new MockMultipartFile(
            "img", 
            "empty.jpg", 
            "image/jpeg", 
            new byte[0]
        );

        // When: Admin uploads the empty file
        ResponseEntity<Map<String, String>> response = adminController.updateProfileImage(
            testUser.getId(), 
            emptyFile
        );

        // Then: Returns error response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo("false");
        
        // Verify database was not updated
        UserDtls unchangedUser = userRepository.findById(testUser.getId()).orElse(null);
        assertThat(unchangedUser).isNotNull();
        assertThat(unchangedUser.getProfileImage()).isEqualTo("old-image.jpg");
    }
}
