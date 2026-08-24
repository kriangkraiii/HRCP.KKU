package com.ecom.academic.dto;

import com.ecom.academic.model.StaffMember;

/**
 * One choice in a signer picker.
 *
 * <p>A DTO rather than the entity because {@code StaffMember.user} is a lazy
 * association: serialising the entity straight to JSON would either drag the
 * account into every response or fail on the proxy. It also lets the picker say
 * <em>why</em> someone cannot be chosen, which the entity alone cannot express.
 *
 * @param staffId    the staff record, used when only a printed name is needed
 * @param userId     the login account that would receive the signing request,
 *                   or null when this person has none
 * @param displayName academic title + full name, as it appears in documents
 * @param department  free-text unit, shown to tell apart people with similar names
 * @param email       the account's email, so an administrator can confirm they
 *                    picked the right person before sending
 * @param signable    whether this person can be assigned a signing step at all
 */
public record SignerOptionDTO(
        Long staffId,
        Integer userId,
        String displayName,
        String department,
        String email,
        boolean signable) {

    public static SignerOptionDTO from(StaffMember staff) {
        boolean signable = staff.isSignable();
        return new SignerOptionDTO(
                staff.getId(),
                signable ? staff.getUser().getId() : null,
                staff.getDisplayName(),
                staff.getDepartment(),
                signable ? staff.getUser().getEmail() : null,
                signable);
    }

    /**
     * A choice built straight from a login account.
     *
     * <p>For people who can sign without having a {@code staff_member} row —
     * administrators in particular, who are often not in the staff directory at
     * all but still need to sign in their own name.
     */
    public static SignerOptionDTO fromUser(com.ecom.model.UserDtls user) {
        if (user == null) {
            return null;
        }
        String prefix = user.getAcademicPosition();
        if (prefix == null || prefix.isBlank()) {
            prefix = user.getTitle();
        }
        String fullName = user.getName();
        if ((fullName == null || fullName.isBlank()) && (user.getFirstName() != null || user.getLastName() != null)) {
            fullName = (user.getFirstName() != null ? user.getFirstName() : "") + " " + (user.getLastName() != null ? user.getLastName() : "");
        }
        if (fullName == null || fullName.isBlank()) {
            fullName = user.getEmail();
        }
        String displayName = (prefix == null || prefix.isBlank() ? "" : prefix + " ") + fullName;
        if (user.getRole() != null && ("ROLE_ADMIN".equalsIgnoreCase(user.getRole()) || "ADMIN".equalsIgnoreCase(user.getRole()))) {
            displayName = displayName.trim() + " [แอดมิน]";
        } else if (user.getRole() != null && ("ROLE_STAFF".equalsIgnoreCase(user.getRole()) || "STAFF".equalsIgnoreCase(user.getRole()))) {
            displayName = displayName.trim() + " [เจ้าหน้าที่]";
        }
        return new SignerOptionDTO(null, user.getId(), displayName.trim(), null, user.getEmail(), true);
    }
}
