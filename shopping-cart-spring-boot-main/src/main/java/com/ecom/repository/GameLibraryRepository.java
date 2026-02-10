package com.ecom.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.model.GameLibrary;

public interface GameLibraryRepository extends JpaRepository<GameLibrary, Integer> {

	List<GameLibrary> findByUserId(Integer userId);

	List<GameLibrary> findByUserIdOrderByPurchaseDateDesc(Integer userId);

	Boolean existsByUserIdAndProductId(Integer userId, Integer productId);
}
