package com.ecom.academic.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.Petition;
import com.ecom.academic.model.PetitionStatus;
import com.ecom.academic.model.StatusType;
import com.ecom.academic.repository.PetitionRepository;
import com.ecom.academic.repository.PetitionStatusRepository;
import com.ecom.exception.ActivePetitionExistsException;
import com.ecom.exception.UserNotFoundException;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Service for managing petitions and their lifecycle.
 * Handles business logic for petition submission, status management, and validation.
 */
@Service
public class PetitionService {

    @Autowired
    private PetitionRepository petitionRepository;

    @Autowired
    private PetitionStatusRepository petitionStatusRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Check if a user can submit a new petition.
     * A user can submit a petition if they don't have any active petition.
     * Active petition is one with status other than REJECTED or COMPLETED.
     * 
     * @param userId the user ID to check
     * @return true if user can submit a new petition, false otherwise
     */
    public boolean canUserSubmitPetition(Integer userId) {
        return petitionRepository.findActivePetitionByUserId(userId).isEmpty();
    }

    /**
     * Get the active petition for a user if it exists.
     * 
     * @param userId the user ID
     * @return Optional containing the active petition if exists, empty otherwise
     */
    public Optional<Petition> getActivePetition(Integer userId) {
        return petitionRepository.findActivePetitionByUserId(userId);
    }


    /**
     * Create a new petition for a user.
     * Automatically adds initial RECEIVED status.
     *
     * @param userId the user ID
     * @param title the petition title
     * @param description the petition description
     * @return the created petition
     * @throws ActivePetitionExistsException if user already has an active petition
     * @throws UserNotFoundException if user is not found
     */
    @Transactional
    public Petition createPetition(Integer userId, String title, String description) {
        // Check for active petition
        if (!canUserSubmitPetition(userId)) {
            throw new ActivePetitionExistsException(
                "ไม่สามารถยื่นคำร้องใหม่ได้ เนื่องจากมีคำร้องที่กำลังดำเนินการอยู่"
            );
        }

        // Find user
        UserDtls user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        // Create petition
        Petition petition = new Petition();
        petition.setUser(user);
        petition.setTitle(title);
        petition.setDescription(description);
        petition.setCreatedAt(LocalDateTime.now());
        petition.setStatusHistory(new ArrayList<>());

        // Save petition
        Petition savedPetition = petitionRepository.save(petition);

        // Add initial RECEIVED status
        addStatus(savedPetition.getId(), StatusType.RECEIVED, "รับคำร้องเข้าระบบ");

        return savedPetition;
    }

    /**
     * Add a new status to a petition.
     *
     * @param petitionId the petition ID
     * @param statusType the status type to add
     * @param note optional note for the status
     * @return the created petition status
     * @throws com.ecom.exception.PetitionNotFoundException if petition is not found
     */
    @Transactional
    public PetitionStatus addStatus(Long petitionId, StatusType statusType, String note) {
        Petition petition = petitionRepository.findById(petitionId)
            .orElseThrow(() -> new com.ecom.exception.PetitionNotFoundException("Petition not found with id: " + petitionId));

        PetitionStatus status = new PetitionStatus();
        status.setPetition(petition);
        status.setStatusType(statusType);
        status.setNote(note);
        status.setCreatedAt(LocalDateTime.now());

        return petitionStatusRepository.save(status);
    }

    /**
     * Get a petition with its full status history.
     * 
     * @param petitionId the petition ID
     * @return the petition with status history loaded
     * @throws com.ecom.exception.PetitionNotFoundException if petition is not found
     */
    @Transactional(readOnly = true)
    public Petition getPetitionWithHistory(Long petitionId) {
        return petitionRepository.findById(petitionId)
            .orElseThrow(() -> new com.ecom.exception.PetitionNotFoundException("Petition not found with id: " + petitionId));
    }

    /**
     * Get all petitions for a user with their status history.
     * Results are ordered by creation date descending (newest first).
     * 
     * @param userId the user ID
     * @return list of petitions with status history
     */
    @Transactional(readOnly = true)
    public List<Petition> getUserPetitions(Integer userId) {
        return petitionRepository.findAllByUserIdWithStatusHistory(userId);
    }

}
