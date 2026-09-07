package com.ecom.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.model.UserDtls;

public interface UserRepository extends JpaRepository<UserDtls, Integer> {

	public UserDtls findByEmail(String email);
	public UserDtls findByEmailIgnoreCase(String email);

	public List<UserDtls> findByRole(String role);

	public UserDtls findByResetToken(String token);

	public Boolean existsByEmail(String email);
	Integer countByCreatedDateBetween(Date startDate, Date endDate);
	List<UserDtls> findTop5ByOrderByCreatedDateDesc();

	UserDtls findTopByApplicantIdStartingWithOrderByApplicantIdDesc(String prefix);

	List<UserDtls> findByRoleAndApplicantIdIsNull(String role);

	/**
	 * Finds people by any name they are known under, by e-mail, phone or applicant id.
	 *
	 * <p>Every name column is listed on purpose. {@code name} is the legacy
	 * combined column and is the only one the previous version of this query
	 * looked at — but {@code SsoUserProvisioner} and {@code UserDirectorySync}
	 * write {@code firstName}/{@code lastName} and never touch it, so searching
	 * {@code name} alone cannot find anyone who arrived through SSO. That is
	 * everyone the directory syncs. {@code name} stays in the list for rows
	 * created before SSO; {@link UserDtls#getName()} papers over the difference
	 * in Java, but JPQL reads the column, not the getter.
	 *
	 * <p>The {@code firstName + ' ' + lastName} clause is not redundant with the
	 * two single-column clauses: people type "สมชาย ใจดี", which matches neither
	 * column on its own.
	 *
	 * @param pattern a {@code %term%} pattern from
	 *                {@link com.ecom.search.service.SearchQueryNormalizer#likePattern},
	 *                already lower-cased — see the note there on why it is not
	 *                built inside the query
	 */
	@Query("""
			SELECT u FROM UserDtls u
			WHERE LOWER(COALESCE(u.firstName, '')) LIKE :pattern
			   OR LOWER(COALESCE(u.lastName, '')) LIKE :pattern
			   OR LOWER(CONCAT(COALESCE(u.firstName, ''), ' ', COALESCE(u.lastName, ''))) LIKE :pattern
			   OR LOWER(COALESCE(u.firstNameEn, '')) LIKE :pattern
			   OR LOWER(COALESCE(u.lastNameEn, '')) LIKE :pattern
			   OR LOWER(CONCAT(COALESCE(u.firstNameEn, ''), ' ', COALESCE(u.lastNameEn, ''))) LIKE :pattern
			   OR LOWER(COALESCE(u.name, '')) LIKE :pattern
			   OR LOWER(COALESCE(u.email, '')) LIKE :pattern
			   OR LOWER(COALESCE(u.mobileNumber, '')) LIKE :pattern
			   OR LOWER(COALESCE(u.applicantId, '')) LIKE :pattern
			""")
	List<UserDtls> searchUsers(@Param("pattern") String pattern);

	long countByProfileImage(String profileImage);
	List<UserDtls> findByProfileImage(String profileImage);

	List<UserDtls> findByFirstNameIgnoreCaseAndLastNameIgnoreCase(String firstName, String lastName);
	List<UserDtls> findByFirstNameEnIgnoreCaseAndLastNameEnIgnoreCase(String firstNameEn, String lastNameEn);
}
