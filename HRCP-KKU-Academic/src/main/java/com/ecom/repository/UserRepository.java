package com.ecom.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.model.UserDtls;

public interface UserRepository extends JpaRepository<UserDtls, Integer> {

	public UserDtls findByEmail(String email);

	public List<UserDtls> findByRole(String role);

	public UserDtls findByResetToken(String token);

	public Boolean existsByEmail(String email);
	Integer countByCreatedDateBetween(Date startDate, Date endDate);
	List<UserDtls> findTop5ByOrderByCreatedDateDesc();

	UserDtls findTopByApplicantIdStartingWithOrderByApplicantIdDesc(String prefix);

	List<UserDtls> findByRoleAndApplicantIdIsNull(String role);

	@org.springframework.data.jpa.repository.Query("SELECT u FROM UserDtls u WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.mobileNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))")
	List<UserDtls> searchUsers(@org.springframework.data.repository.query.Param("keyword") String keyword);
}
