package com.ecom.academic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ecom.academic.model.Petition;

@Repository
public interface PetitionRepository extends JpaRepository<Petition, Long> {
    
    /**
     * Find active petition for a user.
     * Active petition is one that has a current status that is NOT REJECTED or COMPLETED.
     * 
     * @param userId the user ID
     * @return Optional containing the active petition if exists
     */
    @Query("SELECT p FROM Petition p " +
           "JOIN FETCH p.statusHistory sh " +
           "WHERE p.user.id = :userId " +
           "AND p.id IN (" +
           "  SELECT ps.petition.id FROM PetitionStatus ps " +
           "  WHERE ps.id IN (" +
           "    SELECT MAX(ps2.id) FROM PetitionStatus ps2 " +
           "    GROUP BY ps2.petition.id" +
           "  ) AND ps.statusType NOT IN ('REJECTED', 'COMPLETED')" +
           ")")
    Optional<Petition> findActivePetitionByUserId(@Param("userId") Integer userId);
    
    /**
     * Find all petitions by user with status history eagerly loaded.
     * Results are ordered by creation date descending (newest first).
     * 
     * @param userId the user ID
     * @return list of petitions with status history
     */
    @Query("SELECT DISTINCT p FROM Petition p " +
           "LEFT JOIN FETCH p.statusHistory " +
           "WHERE p.user.id = :userId " +
           "ORDER BY p.createdAt DESC")
    List<Petition> findAllByUserIdWithStatusHistory(@Param("userId") Integer userId);
    
    /**
     * Find petition by ID with status history eagerly loaded.
     * 
     * @param id the petition ID
     * @return Optional containing the petition with status history
     */
    @Query("SELECT p FROM Petition p " +
           "LEFT JOIN FETCH p.statusHistory " +
           "WHERE p.id = :id")
    Optional<Petition> findByIdWithStatusHistory(@Param("id") Long id);
}
