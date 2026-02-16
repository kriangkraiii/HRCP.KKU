# Implementation Plan: Admin User Management

## Overview

This implementation plan breaks down the admin-user-management feature into incremental coding tasks. Each task builds on previous work, starting with backend service methods, then controller endpoints, followed by frontend UI updates, and finally testing. The implementation follows the existing Spring Boot MVC architecture and integrates with the current AdminLogService for audit logging.

## Tasks

- [x] 1. Extend UserService interface and implementation with new methods
  - Add method signatures to UserService interface: updateUserDetails, deleteUserById, canDeleteUser, updateProfileImageOnly
  - Implement updateUserDetails in UserServiceImpl to update account fields while preserving security fields (password, role, etc.)
  - Implement deleteUserById in UserServiceImpl with proper exception handling
  - Implement canDeleteUser in UserServiceImpl to prevent self-deletion
  - Implement updateProfileImageOnly in UserServiceImpl with file saving and cache-busting URL generation
  - Add private helper method saveProfileImage for file upload handling
  - _Requirements: 1.2, 1.3, 2.2, 2.3, 3.2, 4.2, 5.1, 5.2, 5.3_

- [x] 1.1 Write property test for account update persistence
  - **Property 1: Account Update Persistence**
  - **Validates: Requirements 1.3, 2.3**

- [x] 1.2 Write property test for email uniqueness enforcement
  - **Property 3: Email Uniqueness Enforcement**
  - **Validates: Requirements 1.4, 2.4**

- [x] 1.3 Write property test for account deletion removal
  - **Property 4: Account Deletion Removal**
  - **Validates: Requirements 3.2, 4.2**

- [x] 1.4 Write property test for profile image upload and database update
  - **Property 7: Profile Image Upload and Database Update**
  - **Validates: Requirements 5.1, 5.2, 5.3, 5.5**

- [x] 2. Add edit user account endpoints to AdminController
  - [x] 2.1 Implement GET /admin/edit-user endpoint
    - Fetch user by ID using userService.getUserById
    - Add user object to model for form pre-population
    - Return "admin/edit_user" view
    - _Requirements: 1.1_
  
  - [x] 2.2 Implement POST /admin/update-user endpoint
    - Validate input data (email format, required fields)
    - Check email uniqueness using userService.existsEmail (exclude current user)
    - Call userService.updateUserDetails with user data and profile image
    - Log action using adminLogService with "EDIT_USER_ACCOUNT" action type
    - Set success/error message in session
    - Redirect to /admin/users with appropriate type parameter
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 6.1, 7.1, 7.2_

- [x] 2.3 Write unit tests for edit user endpoints
  - Test GET endpoint returns correct view with user data
  - Test POST endpoint with valid data updates user
  - Test POST endpoint with duplicate email returns error
  - Test POST endpoint with invalid email format returns error
  - Test audit logging is called with correct parameters
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 6.1_

- [x] 3. Add edit admin account endpoints to AdminController
  - [x] 3.1 Implement GET /admin/edit-admin endpoint
    - Fetch admin by ID using userService.getUserById
    - Add admin object to model for form pre-population
    - Return "admin/edit_admin" view
    - _Requirements: 2.1_
  
  - [x] 3.2 Implement POST /admin/update-admin endpoint
    - Validate input data (email format, required fields)
    - Check email uniqueness using userService.existsEmail (exclude current admin)
    - Call userService.updateUserDetails with admin data and profile image
    - Log action using adminLogService with "EDIT_ADMIN_ACCOUNT" action type
    - Set success/error message in session
    - Redirect to /admin/users with appropriate type parameter
    - _Requirements: 2.2, 2.3, 2.4, 2.5, 6.2, 7.1, 7.2_

- [x] 3.3 Write unit tests for edit admin endpoints
  - Test GET endpoint returns correct view with admin data
  - Test POST endpoint with valid data updates admin
  - Test POST endpoint with duplicate email returns error
  - Test audit logging is called with correct parameters
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 6.2_

- [x] 4. Add delete account endpoints to AdminController
  - [x] 4.1 Implement GET /admin/delete-user endpoint
    - Get current user's email from Principal
    - Call userService.canDeleteUser to check if deletion is allowed
    - If allowed, call userService.deleteUserById
    - Log action using adminLogService with "DELETE_USER_ACCOUNT" action type
    - Set success/error message in session
    - Redirect to /admin/users with type parameter
    - _Requirements: 3.2, 3.4, 3.5, 6.3_
  
  - [x] 4.2 Implement GET /admin/delete-admin endpoint
    - Get current user's email from Principal
    - Call userService.canDeleteUser to check for self-deletion
    - If self-deletion, reject with error message
    - Otherwise, call userService.deleteUserById
    - Log action using adminLogService with "DELETE_ADMIN_ACCOUNT" action type
    - Set success/error message in session
    - Redirect to /admin/users with type parameter
    - _Requirements: 4.2, 4.4, 4.5, 6.4_

- [x] 4.3 Write property test for deletion audit logging
  - **Property 5: Deletion Audit Logging**
  - **Validates: Requirements 3.4, 4.4, 6.3, 6.4, 6.5**

- [x] 4.4 Write unit tests for delete endpoints
  - Test delete user endpoint removes user and logs action
  - Test delete admin endpoint removes admin and logs action
  - Test self-deletion prevention returns error
  - Test deletion of non-existent account returns error
  - _Requirements: 3.2, 3.5, 4.2, 4.5_

- [x] 5. Checkpoint - Ensure backend endpoints work correctly
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Add AJAX profile image update endpoint to AdminController
  - Implement POST /admin/update-profile-image endpoint with @ResponseBody
  - Accept id and img parameters
  - Call userService.updateProfileImageOnly
  - Return ResponseEntity with JSON containing success status, imageUrl, and imageName
  - Handle exceptions and return error JSON response
  - _Requirements: 5.1, 5.2, 5.3, 5.6_

- [x] 6.1 Write property test for profile image upload failure handling
  - **Property 8: Profile Image Upload Failure Handling**
  - **Validates: Requirements 5.6**

- [x] 6.2 Write unit tests for AJAX profile image endpoint
  - Test successful upload returns JSON with imageUrl
  - Test upload failure returns error JSON
  - Test imageUrl contains cache-busting timestamp
  - _Requirements: 5.3, 5.6_

- [x] 7. Create edit user form view (edit_user.html)
  - Create Thymeleaf template at src/main/resources/templates/admin/edit_user.html
  - Add form with fields: title, name, email, mobileNumber, academicPosition, profileImage
  - Pre-populate form fields using th:value with user object from model
  - Add file input for profile image upload
  - Add submit button and cancel button (redirect to users list)
  - Include client-side validation for required fields and email format
  - Display success/error messages from session attributes
  - _Requirements: 1.1, 1.5_

- [x] 8. Create edit admin form view (edit_admin.html)
  - Create Thymeleaf template at src/main/resources/templates/admin/edit_admin.html
  - Add form with fields: title, name, email, mobileNumber, academicPosition, profileImage
  - Pre-populate form fields using th:value with admin object from model
  - Add file input for profile image upload
  - Add submit button and cancel button (redirect to admins list)
  - Include client-side validation for required fields and email format
  - Display success/error messages from session attributes
  - _Requirements: 2.1, 2.5, 8.2_

- [x] 9. Update user management page (users.html) with edit and delete buttons
  - Add "Edit" button for each user row linking to /admin/edit-user?id={userId}
  - Add "Delete" button for each user row linking to /admin/delete-user?id={userId}&type={type}
  - Add JavaScript confirmation dialog for delete button
  - Add data-user-id attribute to profile image elements for AJAX updates
  - Ensure consistent table layout with profile images, names, emails, and action buttons
  - _Requirements: 3.1, 3.3, 8.1, 8.4_

- [x] 10. Update admin management page with edit and delete buttons
  - Add "Edit" button for each admin row linking to /admin/edit-admin?id={adminId}
  - Add "Delete" button for each admin row linking to /admin/delete-admin?id={adminId}&type={type}
  - Add JavaScript confirmation dialog for delete button with self-deletion warning
  - Add data-user-id attribute to profile image elements for AJAX updates
  - Ensure consistent table layout matching user management page
  - _Requirements: 4.1, 4.3, 8.1, 8.4, 8.5_

- [x] 11. Implement JavaScript for real-time profile image updates
  - Create JavaScript function updateProfileImage(userId, fileInput) in admin.js or inline
  - Use Fetch API to POST to /admin/update-profile-image with FormData
  - On success, update all img elements with matching data-user-id attribute
  - Set img.src to returned imageUrl (includes cache-busting timestamp)
  - Display success message using existing message display mechanism
  - On error, display error message from JSON response
  - Add event listeners to profile image file inputs on edit forms
  - _Requirements: 5.3, 5.4, 5.5_

- [x] 11.1 Write property test for edit audit logging
  - **Property 6: Edit Audit Logging**
  - **Validates: Requirements 6.1, 6.2, 6.5**

- [x] 12. Add input validation and error handling
  - [x] 12.1 Add email format validation in controller methods
    - Use regex pattern or Apache Commons Validator
    - Return error message "รูปแบบอีเมลไม่ถูกต้อง" for invalid format
    - _Requirements: 7.1_
  
  - [x] 12.2 Add required field validation in controller methods
    - Check for null or empty name and email fields
    - Return field-specific error messages
    - _Requirements: 7.2_
  
  - [x] 12.3 Add file upload validation
    - Check file size (max 5MB) in application.properties: spring.servlet.multipart.max-file-size=5MB
    - Check file type (jpg, jpeg, png, gif) by extension and MIME type
    - Return appropriate error messages for size and type violations
    - _Requirements: 7.3, 7.4_
  
  - [x] 12.4 Add database error handling with try-catch blocks
    - Wrap database operations in try-catch
    - Log technical details using logger
    - Return user-friendly error messages in Thai
    - Ensure @Transactional rollback on errors
    - _Requirements: 7.5_

- [x] 12.5 Write property test for input validation rejection
  - **Property 2: Input Validation Rejection**
  - **Validates: Requirements 1.2, 2.2, 7.1, 7.2**

- [x] 12.6 Write property test for database error handling
  - **Property 9: Database Error Handling**
  - **Validates: Requirements 3.5, 7.5**

- [x] 12.7 Write unit tests for validation and error handling
  - Test email format validation rejects invalid emails
  - Test required field validation rejects empty fields
  - Test file size validation rejects large files
  - Test file type validation rejects unsupported formats
  - Test database error handling returns user-friendly messages
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 13. Update AdminLogService to support new action types
  - Verify AdminLogService.log method accepts new action types: EDIT_USER_ACCOUNT, EDIT_ADMIN_ACCOUNT, DELETE_USER_ACCOUNT, DELETE_ADMIN_ACCOUNT
  - If needed, update action type constants or enum
  - Update activity logs page to display new action types with Thai labels
  - _Requirements: 6.1, 6.2, 6.3, 6.4_

- [x] 14. Final checkpoint - Integration testing and verification
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Property tests validate universal correctness properties using jqwik framework
- Unit tests validate specific examples and edge cases
- The implementation follows existing Spring Boot patterns in the codebase
- All Thai language messages should be consistent with existing UI text
- Profile image cache-busting uses timestamp query parameter to force browser refresh
- Self-deletion prevention is critical for admin accounts to maintain system access
