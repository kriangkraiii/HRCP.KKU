package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

/**
 * Property-Based Test for Account Update Persistence
 * 
 * **Validates: Requirements 1.3, 2.3**
 * 
 * Property 1: Account Update Persistence
 * For any valid account (user or admin) and any valid field updates (title, name, 
 * email, mobile number, academic position), when an administrator submits the updates, 
 * the system should persist all changes to the database and subsequent queries should 
 * return the updated values.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class AccountUpdatePersistencePropertyTest {

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
     * Property Test: Account Update Persistence
     * 
     * Tests that for any valid account updates, the changes are persisted to the 
     * database and can be retrieved with the updated values.
     * 
     * This test runs 100 iterations with randomly generated valid user data.
     */
    @Test
    void accountUpdatePersistence() {
        // Create arbitrary generator for user updates
        Arbitrary<UserUpdateData> userUpdatesArbitrary = validUserUpdates();
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            UserUpdateData update = userUpdatesArbitrary.sample();
            
            // Given: An existing account in the database
            UserDtls original = createTestAccount();
            Integer accountId = original.getId();
            
            // When: Administrator updates the account with new valid data
            UserDtls updateRequest = new UserDtls();
            updateRequest.setId(accountId);
            updateRequest.setTitle(update.getTitle());
            updateRequest.setName(update.getName());
            updateRequest.setEmail(update.getEmail());
            updateRequest.setMobileNumber(update.getMobileNumber());
            updateRequest.setAcademicPosition(update.getAcademicPosition());
            
            UserDtls updated = userService.updateUserDetails(updateRequest, null);
            
            // Then: Changes are persisted and can be retrieved
            UserDtls retrieved = userService.getUserById(accountId);
            
            assertThat(retrieved).isNotNull();
            assertThat(retrieved.getId()).isEqualTo(accountId);
            assertThat(retrieved.getTitle()).isEqualTo(update.getTitle());
            assertThat(retrieved.getName()).isEqualTo(update.getName());
            assertThat(retrieved.getEmail()).isEqualTo(update.getEmail());
            assertThat(retrieved.getMobileNumber()).isEqualTo(update.getMobileNumber());
            assertThat(retrieved.getAcademicPosition()).isEqualTo(update.getAcademicPosition());
            
            // Verify security fields are preserved (not modified by update)
            assertThat(retrieved.getPassword()).isEqualTo(original.getPassword());
            assertThat(retrieved.getRole()).isEqualTo(original.getRole());
            assertThat(retrieved.getIsEnable()).isEqualTo(original.getIsEnable());
            assertThat(retrieved.getAccountNonLocked()).isEqualTo(original.getAccountNonLocked());
            
            // Clean up
            userRepository.deleteById(accountId);
        }
    }

    /**
     * Provides arbitrary valid user update data for property testing
     */
    private Arbitrary<UserUpdateData> validUserUpdates() {
        Arbitrary<String> titles = Arbitraries.of(
            "นาย", "นาง", "นางสาว", "ดร.", "ศ.ดร.", "รศ.ดร.", "ผศ.ดร."
        );
        
        // A valid name has a non-blank first and last part, which is what the
        // application validates. Generating bare strings (" ", "abc") produced
        // data the app is supposed to reject.
        Arbitrary<String> nameParts = Arbitraries.strings()
            .alpha()
            .ofMinLength(2)
            .ofMaxLength(20);
        Arbitrary<String> names = Combinators.combine(nameParts, nameParts)
            .as((first, last) -> first + " " + last);
        
        Arbitrary<String> emails = Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(3)
            .ofMaxLength(20)
            .map(s -> s.toLowerCase() + "@example.com");
        
        Arbitrary<String> mobileNumbers = Arbitraries.strings()
            .numeric()
            .ofLength(10);
        
        Arbitrary<String> academicPositions = Arbitraries.of(
            "อาจารย์", 
            "ผู้ช่วยศาสตราจารย์", 
            "รองศาสตราจารย์", 
            "ศาสตราจารย์",
            "นักวิจัย",
            "เจ้าหน้าที่"
        );
        
        return Combinators.combine(titles, names, emails, mobileNumbers, academicPositions)
            .as(UserUpdateData::new);
    }

    /**
     * Helper method to create a test account in the database
     */
    private UserDtls createTestAccount() {
        UserDtls user = new UserDtls();
        user.setTitle("นาย");
        user.setName("Test User");
        user.setEmail("test" + System.currentTimeMillis() + System.nanoTime() + "@test.com");
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

    /**
     * Data class to hold user update information for property testing
     */
    public static class UserUpdateData {
        private final String title;
        private final String name;
        private final String email;
        private final String mobileNumber;
        private final String academicPosition;

        public UserUpdateData(String title, String name, String email, 
                            String mobileNumber, String academicPosition) {
            this.title = title;
            this.name = name;
            this.email = email;
            this.mobileNumber = mobileNumber;
            this.academicPosition = academicPosition;
        }

        public String getTitle() {
            return title;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public String getMobileNumber() {
            return mobileNumber;
        }

        public String getAcademicPosition() {
            return academicPosition;
        }
    }
}
