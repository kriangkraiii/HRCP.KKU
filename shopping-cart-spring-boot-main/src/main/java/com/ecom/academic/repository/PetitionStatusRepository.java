package com.ecom.academic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ecom.academic.model.PetitionStatus;

@Repository
public interface PetitionStatusRepository extends JpaRepository<PetitionStatus, Long> {
    
    /**
     * Find status history for a petition ordered by creation time ascending
     * 
     * @param petitionId the petition ID
     * @return list of petition statuses ordered by createdAt ascending
     */
    List<PetitionStatus> findByPetitionIdOrderByCreatedAtAsc(Long petitionId);
}
