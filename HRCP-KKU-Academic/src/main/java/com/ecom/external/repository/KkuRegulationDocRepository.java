package com.ecom.external.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ecom.external.model.KkuRegulationDoc;

@Repository
public interface KkuRegulationDocRepository extends JpaRepository<KkuRegulationDoc, Long> {

    Optional<KkuRegulationDoc> findByFileKey(String fileKey);

    List<KkuRegulationDoc> findAllByOrderByDisplayOrderAscIdAsc();

    List<KkuRegulationDoc> findByCategoryOrderByDisplayOrderAscIdAsc(String category);

    long countByIsNewTrue();

    @Query("SELECT DISTINCT d.category FROM KkuRegulationDoc d ORDER BY MIN(d.displayOrder) ASC")
    List<String> findDistinctCategoriesOrdered();
}
