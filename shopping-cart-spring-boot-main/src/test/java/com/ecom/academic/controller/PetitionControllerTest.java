package com.ecom.academic.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.ecom.academic.dto.PetitionForm;
import com.ecom.academic.model.Petition;
import com.ecom.academic.model.StatusType;
import com.ecom.academic.repository.PetitionRepository;
import com.ecom.academic.repository.PetitionStatusRepository;
import com.ecom.academic.service.PetitionService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Unit tests for PetitionController
 * 
 * Tests Requirements: 1.2, 1.3, 5.1, 6.1
 * 
 * Test Coverage:
 * - GET /petitions/new shows form when no active petition
 * - GET /petitions/new shows cannot_submit when active petition exists
 * - POST /petitions/create creates petition successfully
 * - POST /petitions/create fails with validation errors
 * - GET /petitions/{id} shows petition details correctly
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class PetitionControllerTest {

    @Autowired
    private PetitionController petitionController;

    @Autowired
    private PetitionService petitionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PetitionRepository petitionRepository;

    @Autowired
    private PetitionStatusRepository petitionStatusRepository;

    private UserDtls testUser;
    private MockPrincipal mockPrincipal;

    @BeforeEach
    void setUp() {
        petitionStatusRepository.deleteAll();
        petitionRepository.deleteAll();
        userRepository.deleteAll();

        // Setup test user
        testUser = new UserDtls();
        testUser.setName("Test User");
        testUser.setEmail("test@example.com");
        testUser.setPassword("encodedPassword");
        testUser.setRole("ROLE_USER");
        testUser.setIsEnable(true);
        testUser.setAccountNonLocked(true);
        testUser.setFailedAttempt(0);
        testUser.setProfileImage("default.png");
        testUser = userRepository.save(testUser);

        mockPrincipal = new MockPrincipal("test@example.com");
    }

    @AfterEach
    void tearDown() {
        petitionStatusRepository.deleteAll();
        petitionRepository.deleteAll();
        userRepository.deleteAll();
    }

    // Helper class for mock Principal
    private static class MockPrincipal implements java.security.Principal {
        private final String name;

        public MockPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    /**
     * Test GET /petitions/new shows form when no active petition
     * Validates Requirement 1.2: Allow petition submission when no active petition
     */
    @Test
    void testGetNewPetition_NoActivePetition_ShowsForm() {
        // Given: User has no active petition
        Model model = new ExtendedModelMap();

        // When: User requests new petition form
        String viewName = petitionController.showNewPetitionForm(model, mockPrincipal);

        // Then: Returns new petition form
        assertThat(viewName).isEqualTo("petition/new");
        assertThat(model.containsAttribute("petition")).isTrue();
        assertThat(model.getAttribute("petition")).isInstanceOf(PetitionForm.class);
    }

    /**
     * Test GET /petitions/new shows cannot_submit when active petition exists
     * Validates Requirement 1.2: Block petition submission when active petition exists
     */
    @Test
    void testGetNewPetition_WithActivePetition_ShowsCannotSubmit() {
        // Given: User has an active petition
        petitionService.createPetition(testUser.getId(), "Active Petition", "Description");
        Model model = new ExtendedModelMap();

        // When: User requests new petition form
        String viewName = petitionController.showNewPetitionForm(model, mockPrincipal);

        // Then: Returns cannot_submit page
        assertThat(viewName).isEqualTo("petition/cannot_submit");
        assertThat(model.containsAttribute("error")).isTrue();
        assertThat(model.containsAttribute("activePetition")).isTrue();
        
        Petition activePetition = (Petition) model.getAttribute("activePetition");
        assertThat(activePetition).isNotNull();
        assertThat(activePetition.getTitle()).isEqualTo("Active Petition");
    }

    /**
     * Test POST /petitions/create creates petition successfully
     * Validates Requirement 1.3: Create petition with initial RECEIVED status
     */
    @Test
    void testPostCreatePetition_ValidData_CreatesPetition() {
        // Given: Valid petition form
        PetitionForm form = new PetitionForm();
        form.setTitle("Test Petition");
        form.setDescription("Test Description");
        
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "petition");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // When: User submits petition
        String result = petitionController.createPetition(
            form, bindingResult, mockPrincipal, model, redirectAttributes
        );

        // Then: Petition is created and redirects to view page
        assertThat(result).startsWith("redirect:/petitions/");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("success");
        
        // Verify petition was created in database
        List<Petition> petitions = petitionRepository.findAll();
        assertThat(petitions).hasSize(1);
        
        Petition created = petitions.get(0);
        assertThat(created.getTitle()).isEqualTo("Test Petition");
        assertThat(created.getDescription()).isEqualTo("Test Description");
        assertThat(created.getUser().getId()).isEqualTo(testUser.getId());
        
        // Verify initial RECEIVED status was added
        assertThat(created.getStatusHistory()).hasSize(1);
        assertThat(created.getCurrentStatus().getStatusType()).isEqualTo(StatusType.RECEIVED);
    }

    /**
     * Test POST /petitions/create fails with validation errors
     * Validates Requirement 1.3: Validate petition form data
     */
    @Test
    void testPostCreatePetition_InvalidData_ReturnsFormWithErrors() {
        // Given: Invalid petition form (empty title)
        PetitionForm form = new PetitionForm();
        form.setTitle("");
        form.setDescription("Test Description");
        
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "petition");
        bindingResult.rejectValue("title", "error.title", "กรุณากรอกหัวข้อคำร้อง");
        
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // When: User submits invalid petition
        String result = petitionController.createPetition(
            form, bindingResult, mockPrincipal, model, redirectAttributes
        );

        // Then: Returns to form with errors
        assertThat(result).isEqualTo("petition/new");
        
        // Verify no petition was created
        List<Petition> petitions = petitionRepository.findAll();
        assertThat(petitions).isEmpty();
    }

    /**
     * Test POST /petitions/create fails when active petition exists
     * Validates Requirement 1.2: Block duplicate petition submission
     */
    @Test
    void testPostCreatePetition_WithActivePetition_ReturnsError() {
        // Given: User already has an active petition
        petitionService.createPetition(testUser.getId(), "Active Petition", "Description");
        
        PetitionForm form = new PetitionForm();
        form.setTitle("New Petition");
        form.setDescription("New Description");
        
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "petition");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // When: User tries to submit another petition
        String result = petitionController.createPetition(
            form, bindingResult, mockPrincipal, model, redirectAttributes
        );

        // Then: Returns to form with error
        assertThat(result).isEqualTo("petition/new");
        assertThat(model.containsAttribute("error")).isTrue();
        
        // Verify only one petition exists
        List<Petition> petitions = petitionRepository.findAll();
        assertThat(petitions).hasSize(1);
    }

    /**
     * Test GET /petitions/{id} shows petition details correctly
     * Validates Requirements 5.1, 6.1: Display petition with status history and user info
     */
    @Test
    void testGetViewPetition_ShowsDetailsCorrectly() {
        // Given: A petition exists
        Petition petition = petitionService.createPetition(
            testUser.getId(), "Test Petition", "Test Description"
        );
        
        Model model = new ExtendedModelMap();

        // When: User views petition
        String viewName = petitionController.viewPetition(petition.getId(), model, mockPrincipal);

        // Then: Returns view page with petition data
        assertThat(viewName).isEqualTo("petition/view");
        assertThat(model.containsAttribute("petition")).isTrue();
        assertThat(model.containsAttribute("statusHistory")).isTrue();
        
        Petition viewedPetition = (Petition) model.getAttribute("petition");
        assertThat(viewedPetition.getId()).isEqualTo(petition.getId());
        assertThat(viewedPetition.getTitle()).isEqualTo("Test Petition");
        assertThat(viewedPetition.getUser().getName()).isEqualTo("Test User");
        
        @SuppressWarnings("unchecked")
        List<?> statusHistory = (List<?>) model.getAttribute("statusHistory");
        assertThat(statusHistory).hasSize(1);
    }

    /**
     * Test GET /petitions/my-petitions shows all user petitions
     * Validates Requirement 5.1: Display list of user's petitions
     */
    @Test
    void testGetMyPetitions_ShowsAllUserPetitions() {
        // Given: User has multiple petitions
        Petition petition1 = petitionService.createPetition(
            testUser.getId(), "Petition 1", "Description 1"
        );
        
        // Complete first petition
        petitionService.addStatus(petition1.getId(), StatusType.COMPLETED, "Completed");
        
        // Create second petition
        petitionService.createPetition(testUser.getId(), "Petition 2", "Description 2");
        
        Model model = new ExtendedModelMap();

        // When: User views their petitions
        String viewName = petitionController.myPetitions(model, mockPrincipal);

        // Then: Returns list page with all petitions
        assertThat(viewName).isEqualTo("petition/list");
        assertThat(model.containsAttribute("petitions")).isTrue();
        
        @SuppressWarnings("unchecked")
        List<Petition> petitions = (List<Petition>) model.getAttribute("petitions");
        assertThat(petitions).hasSize(2);
    }

    /**
     * Test GET /petitions/{id} blocks access to other user's petition
     * Validates security: Users can only view their own petitions
     */
    @Test
    void testGetViewPetition_BlocksAccessToOtherUserPetition() {
        // Given: Another user's petition exists
        UserDtls anotherUser = new UserDtls();
        anotherUser.setName("Another User");
        anotherUser.setEmail("another@example.com");
        anotherUser.setPassword("encodedPassword");
        anotherUser.setRole("ROLE_USER");
        anotherUser.setIsEnable(true);
        anotherUser.setAccountNonLocked(true);
        anotherUser.setFailedAttempt(0);
        anotherUser.setProfileImage("default.png");
        anotherUser = userRepository.save(anotherUser);
        
        Petition otherPetition = petitionService.createPetition(
            anotherUser.getId(), "Other Petition", "Other Description"
        );
        
        Model model = new ExtendedModelMap();

        // When: Test user tries to view another user's petition
        String viewName = petitionController.viewPetition(otherPetition.getId(), model, mockPrincipal);

        // Then: Redirects to my-petitions
        assertThat(viewName).isEqualTo("redirect:/petitions/my-petitions");
    }
}
