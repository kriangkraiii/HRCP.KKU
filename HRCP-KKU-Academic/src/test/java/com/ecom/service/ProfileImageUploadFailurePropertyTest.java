package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

/**
 * Property-Based Test for Profile Image Upload Failure Handling
 * 
 * **Validates: Requirements 5.6**
 * 
 * Property 8: Profile Image Upload Failure Handling
 * For any profile image upload that fails (due to file system errors or invalid file), 
 * the system should return an error response and the account's profileImage field 
 * should remain unchanged in the database.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class ProfileImageUploadFailurePropertyTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // Clean up before each test
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        userRepository.deleteAll();
    }

    /**
     * Property Test: Profile Image Upload Failure Handling
     * 
     * Tests that for any profile image upload that fails (empty file, non-existent user),
     * the system returns an error response and the account's profileImage field remains
     * unchanged in the database.
     * 
     * This test runs 100 iterations with randomly generated failure scenarios.
     */
    @Test
    void profileImageUploadFailureHandling() {
        // Create arbitrary generator for failure scenarios
        Arbitrary<FailureScenario> failureScenarios = generateFailureScenarios();
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            FailureScenario scenario = failureScenarios.sample();
            
            if (scenario.isNonExistentUser()) {
                // Scenario 1: Non-existent user
                testNonExistentUserScenario(scenario);
            } else {
                // Scenario 2: Empty or invalid file
                testInvalidFileScenario(scenario);
            }
        }
    }

    private void testNonExistentUserScenario(FailureScenario scenario) {
        // Given: A non-existent user ID
        Integer nonExistentId = scenario.getUserId();
        
        // When: Attempting to update profile image for non-existent user
        MockMultipartFile file = new MockMultipartFile(
            "img",
            scenario.getFileName(),
            "image/jpeg",
            scenario.getFileContent()
        );
        
        Map<String, String> result = userService.updateProfileImageOnly(nonExistentId, file);
        
        // Then: Returns error response
        assertThat(result.get("success")).isEqualTo("false");
        assertThat(result.get("error")).isNotNull();
        
        // And: No user exists with that ID
        assertThat(userRepository.findById(nonExistentId)).isEmpty();
    }

    private void testInvalidFileScenario(FailureScenario scenario) {
        // Given: An existing account with a profile image
        UserDtls user = createTestAccount();
        String originalImage = user.getProfileImage();
        
        // When: Attempting to upload an empty/invalid file
        MockMultipartFile file = new MockMultipartFile(
            "img",
            scenario.getFileName(),
            "image/jpeg",
            scenario.getFileContent()
        );
        
        Map<String, String> result = userService.updateProfileImageOnly(user.getId(), file);
        
        // Then: Returns error response
        assertThat(result.get("success")).isEqualTo("false");
        assertThat(result.get("error")).isNotNull();
        
        // And: Profile image remains unchanged in database
        UserDtls unchangedUser = userRepository.findById(user.getId()).orElse(null);
        assertThat(unchangedUser).isNotNull();
        assertThat(unchangedUser.getProfileImage()).isEqualTo(originalImage);
    }

    private UserDtls createTestAccount() {
        UserDtls user = new UserDtls();
        user.setName("Test User");
        // A clock in milliseconds is not a unique key: this is a property test and
        // several accounts are created inside the same millisecond, which the
        // unique constraint on the column rightly refuses.
        user.setEmail("test-" + java.util.UUID.randomUUID() + "@example.com");
        user.setMobileNumber("1234567890");
        user.setPassword("password");
        user.setRole("ROLE_USER");
        user.setProfileImage("original-image.jpg");
        user.setIsEnable(true);
        user.setAccountNonLocked(true);
        return userRepository.save(user);
    }

    private Arbitrary<FailureScenario> generateFailureScenarios() {
        // Generate scenarios with 50% non-existent user, 50% invalid file
        Arbitrary<Boolean> isNonExistentUser = Arbitraries.of(true, false);
        Arbitrary<Integer> nonExistentUserId = Arbitraries.integers().between(10000, 99999);
        Arbitrary<String> fileName = Arbitraries.of("empty.jpg", "invalid.png", "test.gif", "");
        Arbitrary<byte[]> fileContent = Arbitraries.of(
            new byte[0],  // Empty file
            new byte[]{0}  // Single byte (too small)
        );
        
        return isNonExistentUser.flatMap(isNonExistent -> {
            if (isNonExistent) {
                return nonExistentUserId.map(id -> 
                    new FailureScenario(true, id, "test.jpg", "valid content".getBytes())
                );
            } else {
                return fileName.flatMap(name ->
                    fileContent.map(content ->
                        new FailureScenario(false, null, name, content)
                    )
                );
            }
        });
    }

    /**
     * Data class representing a failure scenario
     */
    private static class FailureScenario {
        private final boolean nonExistentUser;
        private final Integer userId;
        private final String fileName;
        private final byte[] fileContent;

        public FailureScenario(boolean nonExistentUser, Integer userId, String fileName, byte[] fileContent) {
            this.nonExistentUser = nonExistentUser;
            this.userId = userId;
            this.fileName = fileName;
            this.fileContent = fileContent;
        }

        public boolean isNonExistentUser() {
            return nonExistentUser;
        }

        public Integer getUserId() {
            return userId;
        }

        public String getFileName() {
            return fileName;
        }

        public byte[] getFileContent() {
            return fileContent;
        }
    }
}
