# Design Document: Admin User Management

## Overview

This design extends the existing Spring Boot admin management system to provide comprehensive CRUD operations for user and admin accounts. The system will add edit and delete capabilities while fixing a critical bug where profile image updates don't reflect on management pages without manual refresh.

The solution follows the existing Spring MVC architecture with controller-service-repository layers, leveraging the current UserDtls entity model and AdminLogService for audit logging. The design emphasizes consistency with existing patterns while introducing AJAX-based profile image updates for real-time UI refresh.

## Architecture

### System Context

The admin-user-management feature operates within the existing Spring Boot application architecture:

```
┌─────────────────────────────────────────────────────────────┐
│                     Admin Management UI                      │
│  ┌──────────────────┐         ┌──────────────────┐         │
│  │  User Management │         │ Admin Management │         │
│  │      Page        │         │      Page        │         │
│  └────────┬─────────┘         └────────┬─────────┘         │
└───────────┼──────────────────────────────┼──────────────────┘
            │                              │
            │         HTTP/AJAX            │
            ▼                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    AdminController                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Edit User    │  │ Delete User  │  │ Update Image │     │
│  │ Edit Admin   │  │ Delete Admin │  │              │     │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘     │
└─────────┼──────────────────┼──────────────────┼─────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────┐
│                      UserService                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ updateUser   │  │ deleteUser   │  │ updateProfile│     │
│  │ Details      │  │ ById         │  │ Image        │     │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘     │
└─────────┼──────────────────┼──────────────────┼─────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────┐
│                    UserRepository                            │
│                  (Spring Data JPA)                           │
└─────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────┐
│                    Database (UserDtls)                       │
└─────────────────────────────────────────────────────────────┘
```

### Component Interaction Flow

**Edit Account Flow:**
1. Admin clicks "Edit" button on management page
2. Modal/form displays with pre-populated account data
3. Admin modifies fields and submits
4. AdminController validates input and checks email uniqueness
5. UserService updates database record
6. AdminLogService logs the modification
7. Controller returns success response
8. UI updates the table row with new data

**Delete Account Flow:**
1. Admin clicks "Delete" button
2. JavaScript confirmation dialog appears
3. On confirmation, AJAX DELETE request sent
4. AdminController validates deletion (prevent self-deletion for admins)
5. UserService removes record from database
6. AdminLogService logs the deletion
7. Controller returns success response
8. UI removes the table row

**Profile Image Update Flow:**
1. Admin uploads new profile image
2. AdminController receives multipart file
3. File saved to uploads/profile_img/ directory
4. UserService updates profileImage field in database
5. Controller returns JSON with new image URL and timestamp
6. JavaScript updates img src with cache-busting parameter
7. Browser fetches and displays new image immediately

## Components and Interfaces

### 1. AdminController Extensions

**New Endpoints:**

```java
// Edit user account
@GetMapping("/edit-user")
public String loadEditUser(@RequestParam Integer id, Model m)

@PostMapping("/update-user")
public String updateUser(@ModelAttribute UserDtls user, 
                        @RequestParam("img") MultipartFile file,
                        HttpSession session)

// Edit admin account
@GetMapping("/edit-admin")
public String loadEditAdmin(@RequestParam Integer id, Model m)

@PostMapping("/update-admin")
public String updateAdmin(@ModelAttribute UserDtls user,
                         @RequestParam("img") MultipartFile file,
                         HttpSession session)

// Delete user account
@GetMapping("/delete-user")
public String deleteUser(@RequestParam Integer id,
                        @RequestParam Integer type,
                        HttpSession session,
                        Principal p)

// Delete admin account
@GetMapping("/delete-admin")
public String deleteAdmin(@RequestParam Integer id,
                         @RequestParam Integer type,
                         HttpSession session,
                         Principal p)

// AJAX profile image update
@PostMapping("/update-profile-image")
@ResponseBody
public ResponseEntity<Map<String, String>> updateProfileImage(
    @RequestParam Integer id,
    @RequestParam("img") MultipartFile file)
```

**Controller Responsibilities:**
- Validate incoming requests
- Check email uniqueness for updates
- Prevent self-deletion for admin accounts
- Coordinate with UserService and AdminLogService
- Return appropriate success/error messages
- Handle file uploads for profile images
- Return JSON responses for AJAX requests

### 2. UserService Extensions

**New Service Methods:**

```java
public interface UserService {
    // Existing methods...
    
    // Update user/admin account details
    UserDtls updateUserDetails(UserDtls user, MultipartFile img);
    
    // Delete user/admin by ID
    Boolean deleteUserById(Integer id);
    
    // Check if user can be deleted (business rules)
    Boolean canDeleteUser(Integer id, String currentUserEmail);
    
    // Update profile image with real-time refresh support
    Map<String, String> updateProfileImageOnly(Integer id, MultipartFile img);
}
```

**Service Implementation Logic:**

```java
@Override
public UserDtls updateUserDetails(UserDtls user, MultipartFile img) {
    // 1. Fetch existing user from database
    UserDtls dbUser = userRepository.findById(user.getId())
        .orElseThrow(() -> new RuntimeException("User not found"));
    
    // 2. Update fields (preserve password, role, security fields)
    dbUser.setTitle(user.getTitle());
    dbUser.setName(user.getName());
    dbUser.setEmail(user.getEmail());
    dbUser.setMobileNumber(user.getMobileNumber());
    dbUser.setAcademicPosition(user.getAcademicPosition());
    
    // 3. Handle profile image if provided
    if (img != null && !img.isEmpty()) {
        String imageName = saveProfileImage(img);
        dbUser.setProfileImage(imageName);
    }
    
    // 4. Save and return
    return userRepository.save(dbUser);
}

@Override
public Boolean deleteUserById(Integer id) {
    try {
        Optional<UserDtls> user = userRepository.findById(id);
        if (user.isPresent()) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    } catch (Exception e) {
        return false;
    }
}

@Override
public Boolean canDeleteUser(Integer id, String currentUserEmail) {
    Optional<UserDtls> user = userRepository.findById(id);
    if (user.isEmpty()) return false;
    
    // Prevent self-deletion
    if (user.get().getEmail().equals(currentUserEmail)) {
        return false;
    }
    
    return true;
}

@Override
public Map<String, String> updateProfileImageOnly(Integer id, MultipartFile img) {
    Map<String, String> result = new HashMap<>();
    
    try {
        UserDtls user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Save image file
        String imageName = saveProfileImage(img);
        user.setProfileImage(imageName);
        userRepository.save(user);
        
        // Return new image URL with timestamp for cache busting
        long timestamp = System.currentTimeMillis();
        String imageUrl = "/uploads/profile_img/" + imageName + "?t=" + timestamp;
        
        result.put("success", "true");
        result.put("imageUrl", imageUrl);
        result.put("imageName", imageName);
        
    } catch (Exception e) {
        result.put("success", "false");
        result.put("error", e.getMessage());
    }
    
    return result;
}

private String saveProfileImage(MultipartFile img) throws IOException {
    String uploadDir = System.getProperty("user.dir") + "/uploads/profile_img/";
    File uploadFolder = new File(uploadDir);
    if (!uploadFolder.exists()) {
        uploadFolder.mkdirs();
    }
    
    String imageName = img.getOriginalFilename();
    Path filePath = Path.of(uploadDir, imageName);
    Files.copy(img.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
    
    return imageName;
}
```

### 3. Frontend JavaScript for Real-Time Image Updates

**Profile Image Update Handler:**

```javascript
function updateProfileImage(userId, fileInput) {
    const formData = new FormData();
    formData.append('id', userId);
    formData.append('img', fileInput.files[0]);
    
    fetch('/admin/update-profile-image', {
        method: 'POST',
        body: formData
    })
    .then(response => response.json())
    .then(data => {
        if (data.success === 'true') {
            // Update all instances of this user's profile image on the page
            const imgElements = document.querySelectorAll(`img[data-user-id="${userId}"]`);
            imgElements.forEach(img => {
                img.src = data.imageUrl;
            });
            
            // Show success message
            showSuccessMessage('อัพเดทรูปโปรไฟล์สำเร็จ');
        } else {
            showErrorMessage('เกิดข้อผิดพลาด: ' + data.error);
        }
    })
    .catch(error => {
        showErrorMessage('เกิดข้อผิดพลาดในการอัพโหลด');
        console.error('Error:', error);
    });
}
```

### 4. AdminLogService Integration

**Audit Log Actions:**

The existing AdminLogService will be used to log all account modifications:

```java
// In AdminController after successful operations:

// Edit user
adminLogService.log(
    adminEmail,
    adminName,
    "EDIT_USER_ACCOUNT",
    "แก้ไขบัญชีผู้ใช้ ID:" + userId + " (" + user.getEmail() + ")",
    getClientIpAddress(request)
);

// Edit admin
adminLogService.log(
    adminEmail,
    adminName,
    "EDIT_ADMIN_ACCOUNT",
    "แก้ไขบัญชีแอดมิน ID:" + adminId + " (" + admin.getEmail() + ")",
    getClientIpAddress(request)
);

// Delete user
adminLogService.log(
    adminEmail,
    adminName,
    "DELETE_USER_ACCOUNT",
    "ลบบัญชีผู้ใช้ ID:" + userId + " (" + deletedEmail + ")",
    getClientIpAddress(request)
);

// Delete admin
adminLogService.log(
    adminEmail,
    adminName,
    "DELETE_ADMIN_ACCOUNT",
    "ลบบัญชีแอดมิน ID:" + adminId + " (" + deletedEmail + ")",
    getClientIpAddress(request)
);
```

## Data Models

### UserDtls Entity (Existing - No Changes Required)

The existing UserDtls entity already contains all necessary fields:

```java
@Entity
public class UserDtls {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    private String title;
    private String name;
    private String mobileNumber;
    private String email;
    private String academicPosition;
    private String password;
    private String profileImage;
    private String role;  // "ROLE_USER" or "ROLE_ADMIN"
    private Boolean isEnable;
    private Boolean accountNonLocked;
    // ... other fields
}
```

**Key Fields for This Feature:**
- `id`: Primary key for identifying accounts to edit/delete
- `email`: Must be unique, validated during updates
- `profileImage`: Filename stored in database, actual file in uploads/profile_img/
- `role`: Distinguishes between users and admins
- All editable fields: title, name, mobileNumber, email, academicPosition

### AdminLog Entity (Existing - No Changes Required)

The existing AdminLog entity supports the new action types:

```java
@Entity
public class AdminLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String adminEmail;
    private String adminName;
    private String action;  // New values: EDIT_USER_ACCOUNT, EDIT_ADMIN_ACCOUNT, 
                           // DELETE_USER_ACCOUNT, DELETE_ADMIN_ACCOUNT
    private String details;
    private String ipAddress;
    private LocalDateTime timestamp;
}
```

### Response DTOs

**ProfileImageUpdateResponse (New):**

```java
public class ProfileImageUpdateResponse {
    private boolean success;
    private String imageUrl;
    private String imageName;
    private String error;
    
    // Constructors, getters, setters
}
```

This will be returned as JSON for AJAX profile image updates.

## Data Flow Diagrams

### Edit Account Data Flow

```
Admin UI                Controller              Service                Repository
   │                        │                      │                        │
   │──Edit Request─────────>│                      │                        │
   │  (id=123)              │                      │                        │
   │                        │──getUserById(123)───>│                        │
   │                        │                      │──findById(123)────────>│
   │                        │                      │<──UserDtls─────────────│
   │<──Pre-filled Form──────│                      │                        │
   │                        │                      │                        │
   │──Submit Updates───────>│                      │                        │
   │  (modified data)       │──Validate Email──────│                        │
   │                        │──updateUserDetails──>│                        │
   │                        │                      │──save(user)───────────>│
   │                        │                      │<──Updated UserDtls─────│
   │                        │<──UserDtls───────────│                        │
   │                        │──Log Action──────────│                        │
   │<──Success Message──────│                      │                        │
   │  (refresh table)       │                      │                        │
```

### Delete Account Data Flow

```
Admin UI                Controller              Service                Repository
   │                        │                      │                        │
   │──Delete Request───────>│                      │                        │
   │  (id=123)              │──canDeleteUser()────>│                        │
   │                        │                      │──findById(123)────────>│
   │                        │                      │<──UserDtls─────────────│
   │                        │<──Boolean────────────│                        │
   │                        │  (check self-delete) │                        │
   │                        │──deleteUserById()───>│                        │
   │                        │                      │──deleteById(123)──────>│
   │                        │                      │<──Success──────────────│
   │                        │<──Boolean────────────│                        │
   │                        │──Log Action──────────│                        │
   │<──Success Response─────│                      │                        │
   │  (remove table row)    │                      │                        │
```

### Profile Image Update with Real-Time Refresh

```
Admin UI                Controller              Service                File System
   │                        │                      │                        │
   │──Upload Image─────────>│                      │                        │
   │  (AJAX POST)           │──updateProfileImage─>│                        │
   │                        │                      │──saveProfileImage()───>│
   │                        │                      │  (write file)          │
   │                        │                      │<──imageName────────────│
   │                        │                      │──save(user)────────────│
   │                        │<──Map<imageUrl>──────│                        │
   │<──JSON Response────────│                      │                        │
   │  {imageUrl: "...?t="}  │                      │                        │
   │                        │                      │                        │
   │──Update img.src────────│                      │                        │
   │  (with timestamp)      │                      │                        │
   │                        │                      │                        │
   │──Browser Fetch────────────────────────────────────────────────────────>│
   │<──New Image────────────────────────────────────────────────────────────│
```


## Correctness Properties

A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.

### Property 1: Account Update Persistence

*For any* valid account (user or admin) and any valid field updates (title, name, email, mobile number, academic position), when an administrator submits the updates, the system should persist all changes to the database and subsequent queries should return the updated values.

**Validates: Requirements 1.3, 2.3**

### Property 2: Input Validation Rejection

*For any* account update request with invalid data (invalid email format, empty required fields), the system should reject the submission and return a validation error without modifying the database.

**Validates: Requirements 1.2, 2.2, 7.1, 7.2**

### Property 3: Email Uniqueness Enforcement

*For any* account update attempt where the new email already exists in the system (for a different account), the system should reject the update and return an error message indicating the email is already in use.

**Validates: Requirements 1.4, 2.4**

### Property 4: Account Deletion Removal

*For any* account (user or admin) that exists in the system, when an administrator confirms deletion, the account should be removed from the database and subsequent queries for that account ID should return null or not found.

**Validates: Requirements 3.2, 4.2**

### Property 5: Deletion Audit Logging

*For any* successful account deletion operation, the system should create an audit log entry containing the administrator's email, name, timestamp, IP address, appropriate action type (DELETE_USER_ACCOUNT or DELETE_ADMIN_ACCOUNT), and the deleted account's details.

**Validates: Requirements 3.4, 4.4, 6.3, 6.4, 6.5**

### Property 6: Edit Audit Logging

*For any* successful account edit operation, the system should create an audit log entry containing the administrator's email, name, timestamp, IP address, appropriate action type (EDIT_USER_ACCOUNT or EDIT_ADMIN_ACCOUNT), and the modified account's details.

**Validates: Requirements 6.1, 6.2, 6.5**

### Property 7: Profile Image Upload and Database Update

*For any* valid image file upload for an account, the system should save the file to the uploads/profile_img/ directory, update the account's profileImage field in the database with the filename, and return a response containing the new image URL with a cache-busting timestamp.

**Validates: Requirements 5.1, 5.2, 5.3, 5.5**

### Property 8: Profile Image Upload Failure Handling

*For any* profile image upload that fails (due to file system errors or invalid file), the system should return an error response and the account's profileImage field should remain unchanged in the database.

**Validates: Requirements 5.6**

### Property 9: Database Error Handling

*For any* update or delete operation that encounters a database error, the system should return a user-friendly error message to the administrator and the database state should remain unchanged (transaction rollback).

**Validates: Requirements 3.5, 7.5**

## Error Handling

### Validation Errors

**Email Validation:**
- Pattern: RFC 5322 compliant email format
- Error Message: "รูปแบบอีเมลไม่ถูกต้อง" (Invalid email format)
- HTTP Status: 400 Bad Request (for AJAX) or redirect with session error (for form submission)

**Required Field Validation:**
- Fields: name, email
- Error Message: "กรุณากรอก [field name]" (Please fill in [field name])
- HTTP Status: 400 Bad Request or redirect with session error

**Email Uniqueness:**
- Check: Query database for existing email (excluding current user ID)
- Error Message: "อีเมลนี้มีในระบบแล้ว" (This email already exists in the system)
- HTTP Status: 409 Conflict or redirect with session error

### File Upload Errors

**File Size Limit:**
- Maximum: 5MB (configurable via application.properties)
- Error Message: "ไฟล์มีขนาดใหญ่เกินไป (สูงสุด 5MB)" (File size too large, max 5MB)
- HTTP Status: 413 Payload Too Large

**File Type Validation:**
- Allowed: .jpg, .jpeg, .png, .gif
- Validation: Check file extension and MIME type
- Error Message: "รองรับเฉพาะไฟล์รูปภาพ (JPG, PNG, GIF)" (Only image files supported)
- HTTP Status: 415 Unsupported Media Type

**File System Errors:**
- Scenarios: Disk full, permission denied, directory not writable
- Error Message: "ไม่สามารถบันทึกไฟล์ได้ กรุณาลองใหม่อีกครั้ง" (Cannot save file, please try again)
- Logging: Log full exception stack trace for debugging
- HTTP Status: 500 Internal Server Error

### Database Errors

**Constraint Violations:**
- Scenarios: Foreign key violations, unique constraint violations
- Error Message: "ไม่สามารถดำเนินการได้ กรุณาตรวจสอบข้อมูล" (Cannot proceed, please check data)
- Logging: Log SQL exception details
- HTTP Status: 409 Conflict

**Connection Errors:**
- Scenarios: Database unavailable, connection timeout
- Error Message: "เกิดข้อผิดพลาดในการเชื่อมต่อ กรุณาลองใหม่อีกครั้ง" (Connection error, please try again)
- Logging: Log connection exception
- HTTP Status: 503 Service Unavailable

**Transaction Failures:**
- Behavior: Automatic rollback via @Transactional annotation
- Error Message: "การดำเนินการล้มเหลว ข้อมูลไม่ได้รับการเปลี่ยนแปลง" (Operation failed, data unchanged)
- Logging: Log transaction exception
- HTTP Status: 500 Internal Server Error

### Business Logic Errors

**Self-Deletion Prevention:**
- Check: Compare current user's email with target account email
- Error Message: "ไม่สามารถลบบัญชีของตัวเองได้" (Cannot delete your own account)
- HTTP Status: 403 Forbidden

**Account Not Found:**
- Check: Verify account exists before update/delete
- Error Message: "ไม่พบบัญชีที่ระบุ" (Account not found)
- HTTP Status: 404 Not Found

### Error Response Formats

**Form Submission (Redirect with Session):**
```java
session.setAttribute("errorMsg", "error message in Thai");
return "redirect:/admin/users?type=" + type;
```

**AJAX Response (JSON):**
```java
Map<String, String> response = new HashMap<>();
response.put("success", "false");
response.put("error", "error message in Thai");
return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
```

## Testing Strategy

### Dual Testing Approach

This feature requires both unit testing and property-based testing to ensure comprehensive coverage:

**Unit Tests** focus on:
- Specific examples of valid and invalid inputs
- Edge cases (empty strings, null values, boundary conditions)
- Error conditions (file system failures, database errors)
- Integration points between controller, service, and repository layers
- Self-deletion prevention logic
- Audit logging integration

**Property-Based Tests** focus on:
- Universal properties that hold for all valid inputs
- Comprehensive input coverage through randomization
- Invariants that must be maintained (email uniqueness, data persistence)
- Round-trip properties (save then retrieve should return same data)

### Property-Based Testing Configuration

**Framework:** Use **jqwik** for Java property-based testing
- Minimum 100 iterations per property test
- Each test must reference its design document property
- Tag format: `@Tag("Feature: admin-user-management, Property N: [property text]")`

**Example Property Test Structure:**
```java
@Property
@Tag("Feature: admin-user-management, Property 1: Account Update Persistence")
void accountUpdatePersistence(@ForAll("validUserUpdates") UserUpdateData update) {
    // Given: An existing account
    UserDtls original = createTestAccount();
    
    // When: Administrator updates the account
    UserDtls updated = userService.updateUserDetails(update.toUserDtls(original.getId()), null);
    
    // Then: Changes are persisted
    UserDtls retrieved = userService.getUserById(original.getId());
    assertThat(retrieved.getName()).isEqualTo(update.getName());
    assertThat(retrieved.getEmail()).isEqualTo(update.getEmail());
    // ... assert all updated fields
}

@Provide
Arbitrary<UserUpdateData> validUserUpdates() {
    return Combinators.combine(
        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100),  // name
        Arbitraries.emails(),  // email
        Arbitraries.strings().numeric().ofLength(10),  // mobile
        Arbitraries.of("อาจารย์", "ผู้ช่วยศาสตราจารย์", "รองศาสตราจารย์")  // position
    ).as(UserUpdateData::new);
}
```

### Unit Testing Coverage

**Controller Layer Tests:**
- Test edit endpoints return correct view with pre-populated data
- Test update endpoints with valid data return success
- Test update endpoints with invalid data return errors
- Test delete endpoints remove accounts and return success
- Test self-deletion prevention returns error
- Test profile image upload returns JSON with image URL
- Test audit logging is called for all operations

**Service Layer Tests:**
- Test updateUserDetails persists all field changes
- Test updateUserDetails with duplicate email throws exception
- Test deleteUserById removes account from database
- Test canDeleteUser returns false for self-deletion
- Test updateProfileImageOnly saves file and updates database
- Test error handling for file system failures
- Test transaction rollback on database errors

**Integration Tests:**
- Test complete edit flow from controller to database
- Test complete delete flow with audit logging
- Test profile image upload with file system interaction
- Test email uniqueness validation across the stack
- Test error handling propagation from service to controller

### Test Data Management

**Test Database:**
- Use H2 in-memory database for tests
- Reset database state between tests
- Use @Transactional with rollback for test isolation

**Test Files:**
- Create temporary directory for test image uploads
- Clean up test files after each test
- Use small test images (< 1KB) for fast execution

**Test Users:**
- Create test users with known IDs and emails
- Use factory methods for consistent test data creation
- Include both ROLE_USER and ROLE_ADMIN accounts

### Coverage Goals

- Line Coverage: > 80%
- Branch Coverage: > 75%
- Property Tests: All 9 properties implemented
- Unit Tests: All edge cases and error conditions covered
- Integration Tests: All user-facing flows tested end-to-end
