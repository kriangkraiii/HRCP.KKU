package com.ecom.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.model.ProductOrder;

public interface ProductOrderRepository extends JpaRepository<ProductOrder, Integer> {

	List<ProductOrder> findByUserId(Integer userId);

	ProductOrder findByOrderId(String orderId);
	@Query("SELECT o.status, COUNT(*) FROM ProductOrder o GROUP BY o.status")
	List<Object[]> getOrderStatusCounts();
	 @Query("SELECT po FROM ProductOrder po WHERE po.product.id = :productId")
	    List<ProductOrder> findByProductId(@Param("productId") Integer productId);
	 List<ProductOrder> findByOrderDate(LocalDate orderDate);

}
