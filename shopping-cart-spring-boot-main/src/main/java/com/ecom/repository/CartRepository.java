package com.ecom.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.model.Cart;
import com.ecom.model.UserDtls;

import jakarta.transaction.Transactional;

public interface CartRepository extends JpaRepository<Cart, Integer> {

	public Cart findByProductIdAndUserId(Integer productId, Integer userId);

	public Integer countByUserId(Integer userId);

	public List<Cart> findByUserId(Integer userId);
	
	@Transactional
	@Modifying
	public void deleteByUser(UserDtls user);
	@Modifying
	@Query("DELETE FROM Cart c WHERE c.product.id = :productId")
	void deleteByProductId(@Param("productId") Integer productId);

}
