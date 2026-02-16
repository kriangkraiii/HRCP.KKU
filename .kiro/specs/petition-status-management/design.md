# เอกสารการออกแบบ: ระบบจัดการสถานะคำร้อง

## ภาพรวม

ระบบจัดการสถานะคำร้องเป็นการปรับปรุงระบบคำร้องที่มีอยู่ใน Spring Boot application โดยเพิ่มการควบคุมการยื่นคำร้องซ้ำ เพิ่มสถานะใหม่ และปรับปรุงการแสดงผลสถานะให้มีความชัดเจน การออกแบบนี้จะใช้ Spring Boot, JPA/Hibernate สำหรับการจัดการข้อมูล และ Thymeleaf สำหรับการแสดงผล

## สถาปัตยกรรม

### ภาพรวมสถาปัตยกรรม

```
┌─────────────────┐
│  Thymeleaf View │
│   (Templates)   │
└────────┬────────┘
         │
┌────────▼────────┐
│   Controller    │
│  (Petition)     │
└────────┬────────┘
         │
┌────────▼────────┐
│    Service      │
│ (PetitionSvc)   │
└────────┬────────┘
         │
┌────────▼────────┐
│   Repository    │
│ (JPA/Hibernate) │
└────────┬────────┘
         │
┌────────▼────────┐
│    Database     │
│   (Petition,    │
│ PetitionStatus) │
└─────────────────┘
```

### การแยกส่วนประกอบ

- **Presentation Layer**: Thymeleaf templates สำหรับแสดงผลสถานะคำร้องพร้อมสีและไอคอน
- **Controller Layer**: จัดการ HTTP requests และ validation การยื่นคำร้อง
- **Service Layer**: Business logic สำหรับการตรวจสอบคำร้องที่กำลังดำเนินการและการจัดการสถานะ
- **Repository Layer**: การเข้าถึงข้อมูลผ่าน JPA
- **Data Layer**: Entity classes และ database schema

## คอมโพเนนต์และอินเทอร์เฟซ

### 1. Entity Classes

#### Petition Entity
```java
@Entity
@Table(name = "petitions")
public class Petition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @OneToMany(mappedBy = "petition", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<PetitionStatus> statusHistory;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    // Helper method
    public PetitionStatus getCurrentStatus() {
        return statusHistory.isEmpty() ? null : 
               statusHistory.get(statusHistory.size() - 1);
    }
    
    public boolean isActive() {
        PetitionStatus current = getCurrentStatus();
        if (current == null) return false;
        
        StatusType type = current.getStatusType();
        return type != StatusType.REJECTED && type != StatusType.COMPLETED;
    }
}
```

#### PetitionStatus Entity
```java
@Entity
@Table(name = "petition_statuses")
public class PetitionStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "petition_id", nullable = false)
    private Petition petition;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType statusType;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(columnDefinition = "TEXT")
    private String note;
}
```

#### StatusType Enum
```java
public enum StatusType {
    RECEIVED("รับคำร้อง", "blue", "fa-inbox"),
    COMMITTEE_ASSIGNED("แต่งตั้งอนุกรรมการ", "orange", "fa-users"),
    MEETING_SCHEDULED("นัดหมายวันประชุม", "purple", "fa-calendar"),
    RESULT_APPROVED("แจ้งผล - ผ่าน", "green", "fa-check-circle"),
    RESULT_REVISION("แจ้งผล - แก้ไข", "yellow", "fa-edit"),
    REJECTED("ไม่รับคำร้อง", "red", "fa-times-circle"),
    COMPLETED("เสร็จสิ้น", "dark-green", "fa-check");
    
    private final String displayName;
    private final String color;
    private final String iconClass;
    
    StatusType(String displayName, String color, String iconClass) {
        this.displayName = displayName;
        this.color = color;
        this.iconClass = iconClass;
    }
    
    // Getters
    public String getDisplayName() { return displayName; }
    public String getColor() { return color; }
    public String getIconClass() { return iconClass; }
}
```

### 2. Repository Layer

#### PetitionRepository
```java
public interface PetitionRepository extends JpaRepository<Petition, Long> {
    
    // Find active petition for a user
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
    Optional<Petition> findActivePetitionByUserId(@Param("userId") Long userId);
    
    // Find all petitions by user with status history
    @Query("SELECT DISTINCT p FROM Petition p " +
           "LEFT JOIN FETCH p.statusHistory " +
           "WHERE p.user.id = :userId " +
           "ORDER BY p.createdAt DESC")
    List<Petition> findAllByUserIdWithStatusHistory(@Param("userId") Long userId);
}
```

#### PetitionStatusRepository
```java
public interface PetitionStatusRepository extends JpaRepository<PetitionStatus, Long> {
    
    // Find status history for a petition
    List<PetitionStatus> findByPetitionIdOrderByCreatedAtAsc(Long petitionId);
}
```

### 3. Service Layer

#### PetitionService Interface
```java
public interface PetitionService {
    
    /**
     * Check if user can submit a new petition
     */
    boolean canUserSubmitPetition(Long userId);
    
    /**
     * Get active petition for user (if exists)
     */
    Optional<Petition> getActivePetition(Long userId);
    
    /**
     * Create new petition
     * @throws ActivePetitionExistsException if user has active petition
     */
    Petition createPetition(Long userId, String title, String description);
    
    /**
     * Add status to petition
     */
    PetitionStatus addStatus(Long petitionId, StatusType statusType, String note);
    
    /**
     * Get petition with full status history
     */
    Petition getPetitionWithHistory(Long petitionId);
    
    /**
     * Get all petitions for user
     */
    List<Petition> getUserPetitions(Long userId);
}
```

#### PetitionServiceImpl
```java
@Service
@Transactional
public class PetitionServiceImpl implements PetitionService {
    
    private final PetitionRepository petitionRepository;
    private final PetitionStatusRepository statusRepository;
    private final UserRepository userRepository;
    
    @Override
    public boolean canUserSubmitPetition(Long userId) {
        return petitionRepository.findActivePetitionByUserId(userId).isEmpty();
    }
    
    @Override
    public Optional<Petition> getActivePetition(Long userId) {
        return petitionRepository.findActivePetitionByUserId(userId);
    }
    
    @Override
    public Petition createPetition(Long userId, String title, String description) {
        // Check for active petition
        if (!canUserSubmitPetition(userId)) {
            throw new ActivePetitionExistsException(
                "ไม่สามารถยื่นคำร้องใหม่ได้ เนื่องจากมีคำร้องที่กำลังดำเนินการอยู่"
            );
        }
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found"));
        
        Petition petition = new Petition();
        petition.setUser(user);
        petition.setTitle(title);
        petition.setDescription(description);
        petition.setCreatedAt(LocalDateTime.now());
        petition.setStatusHistory(new ArrayList<>());
        
        Petition saved = petitionRepository.save(petition);
        
        // Add initial status
        addStatus(saved.getId(), StatusType.RECEIVED, "รับคำร้องเข้าระบบ");
        
        return saved;
    }
    
    @Override
    public PetitionStatus addStatus(Long petitionId, StatusType statusType, String note) {
        Petition petition = petitionRepository.findById(petitionId)
            .orElseThrow(() -> new PetitionNotFoundException("Petition not found"));
        
        PetitionStatus status = new PetitionStatus();
        status.setPetition(petition);
        status.setStatusType(statusType);
        status.setNote(note);
        status.setCreatedAt(LocalDateTime.now());
        
        return statusRepository.save(status);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Petition getPetitionWithHistory(Long petitionId) {
        return petitionRepository.findById(petitionId)
            .orElseThrow(() -> new PetitionNotFoundException("Petition not found"));
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Petition> getUserPetitions(Long userId) {
        return petitionRepository.findAllByUserIdWithStatusHistory(userId);
    }
}
```

### 4. Controller Layer

#### PetitionController
```java
@Controller
@RequestMapping("/petitions")
public class PetitionController {
    
    private final PetitionService petitionService;
    
    @GetMapping("/new")
    public String showNewPetitionForm(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        
        // Check if user can submit
        if (!petitionService.canUserSubmitPetition(user.getId())) {
            Petition activePetition = petitionService.getActivePetition(user.getId())
                .orElseThrow();
            model.addAttribute("error", "ไม่สามารถยื่นคำร้องใหม่ได้ เนื่องจากมีคำร้องที่กำลังดำเนินการอยู่");
            model.addAttribute("activePetition", activePetition);
            return "petition/cannot_submit";
        }
        
        model.addAttribute("petition", new PetitionForm());
        return "petition/new";
    }
    
    @PostMapping("/create")
    public String createPetition(@Valid @ModelAttribute PetitionForm form,
                                 BindingResult result,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "petition/new";
        }
        
        User user = getCurrentUser(userDetails);
        
        try {
            Petition petition = petitionService.createPetition(
                user.getId(), 
                form.getTitle(), 
                form.getDescription()
            );
            redirectAttributes.addFlashAttribute("success", "ยื่นคำร้องสำเร็จ");
            return "redirect:/petitions/" + petition.getId();
        } catch (ActivePetitionExistsException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/petitions/new";
        }
    }
    
    @GetMapping("/{id}")
    public String viewPetition(@PathVariable Long id, Model model) {
        Petition petition = petitionService.getPetitionWithHistory(id);
        model.addAttribute("petition", petition);
        model.addAttribute("statusHistory", petition.getStatusHistory());
        return "petition/view";
    }
    
    @GetMapping("/my-petitions")
    public String myPetitions(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        List<Petition> petitions = petitionService.getUserPetitions(user.getId());
        model.addAttribute("petitions", petitions);
        return "petition/list";
    }
}
```

### 5. View Layer (Thymeleaf Templates)

#### petition/view.html
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>รายละเอียดคำร้อง</title>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css">
    <style>
        .status-badge {
            display: inline-block;
            padding: 8px 16px;
            border-radius: 20px;
            margin: 5px;
            font-weight: 500;
        }
        .status-blue { background-color: #3498db; color: white; }
        .status-orange { background-color: #e67e22; color: white; }
        .status-purple { background-color: #9b59b6; color: white; }
        .status-green { background-color: #2ecc71; color: white; }
        .status-yellow { background-color: #f1c40f; color: #333; }
        .status-red { background-color: #e74c3c; color: white; }
        .status-dark-green { background-color: #27ae60; color: white; }
        
        .status-timeline {
            position: relative;
            padding-left: 30px;
        }
        .status-timeline::before {
            content: '';
            position: absolute;
            left: 10px;
            top: 0;
            bottom: 0;
            width: 2px;
            background: #ddd;
        }
        .status-item {
            position: relative;
            margin-bottom: 20px;
        }
        .status-item::before {
            content: '';
            position: absolute;
            left: -24px;
            top: 5px;
            width: 12px;
            height: 12px;
            border-radius: 50%;
            background: #3498db;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>รายละเอียดคำร้อง</h1>
        
        <div class="petition-info">
            <h2 th:text="${petition.title}">ชื่อคำร้อง</h2>
            <p><strong>ผู้ยื่นคำร้อง:</strong> 
                <span th:text="${petition.user.fullName != null ? petition.user.fullName : petition.user.username}">
                    ชื่อผู้ยื่น
                </span>
            </p>
            <p><strong>วันที่ยื่น:</strong> 
                <span th:text="${#temporals.format(petition.createdAt, 'dd/MM/yyyy HH:mm')}">
                    วันที่
                </span>
            </p>
            <p th:text="${petition.description}">รายละเอียดคำร้อง</p>
        </div>
        
        <div class="status-section">
            <h3>สถานะคำร้อง</h3>
            <div class="status-timeline">
                <div class="status-item" th:each="status : ${statusHistory}">
                    <span class="status-badge" 
                          th:classappend="'status-' + ${status.statusType.color}">
                        <i th:class="'fas ' + ${status.statusType.iconClass}"></i>
                        <span th:text="${status.statusType.displayName}">สถานะ</span>
                    </span>
                    <span class="status-date" 
                          th:text="${#temporals.format(status.createdAt, 'dd/MM/yyyy HH:mm')}">
                        วันที่
                    </span>
                    <p th:if="${status.note}" th:text="${status.note}" class="status-note">
                        หมายเหตุ
                    </p>
                </div>
            </div>
        </div>
    </div>
</body>
</html>
```

#### petition/cannot_submit.html
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>ไม่สามารถยื่นคำร้องได้</title>
</head>
<body>
    <div class="container">
        <div class="alert alert-warning">
            <h2>ไม่สามารถยื่นคำร้องใหม่ได้</h2>
            <p th:text="${error}">ข้อความแจ้งเตือน</p>
            <p>คุณมีคำร้องที่กำลังดำเนินการอยู่:</p>
            <div class="active-petition">
                <h3 th:text="${activePetition.title}">ชื่อคำร้อง</h3>
                <p>สถานะปัจจุบัน: 
                    <span th:text="${activePetition.currentStatus.statusType.displayName}">
                        สถานะ
                    </span>
                </p>
                <a th:href="@{/petitions/{id}(id=${activePetition.id})}" 
                   class="btn btn-primary">
                    ดูรายละเอียดคำร้อง
                </a>
            </div>
        </div>
    </div>
</body>
</html>
```

## โมเดลข้อมูล

### Database Schema

```sql
-- Petitions table
CREATE TABLE petitions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Petition statuses table
CREATE TABLE petition_statuses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    petition_id BIGINT NOT NULL,
    status_type VARCHAR(50) NOT NULL,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (petition_id) REFERENCES petitions(id) ON DELETE CASCADE
);

-- Index for performance
CREATE INDEX idx_petition_user ON petitions(user_id);
CREATE INDEX idx_status_petition ON petition_statuses(petition_id);
CREATE INDEX idx_status_created ON petition_statuses(created_at);
```

### Entity Relationships

```
User (1) ----< (N) Petition
Petition (1) ----< (N) PetitionStatus
```

- หนึ่ง User สามารถมีหลาย Petition
- หนึ่ง Petition สามารถมีหลาย PetitionStatus (status history)
- PetitionStatus เรียงลำดับตาม created_at เพื่อแสดงประวัติ

## Correctness Properties

Property คือลักษณะหรือพฤติกรรมที่ควรเป็นจริงในทุกการทำงานของระบบ - เป็นการระบุอย่างเป็นทางการว่าระบบควรทำอะไร Properties เป็นสะพานเชื่อมระหว่างข้อกำหนดที่มนุษย์อ่านได้กับการรับประกันความถูกต้องที่เครื่องตรวจสอบได้


### Property Reflection

After analyzing all acceptance criteria, I identified the following redundancies:
- Criteria 5.2 is redundant with 5.1 (inverse statement of the same property)
- Criteria 5.4 is redundant with 3.4 (both test status history ordering)
- Criteria 1.3 and 1.4 can be combined into a comprehensive property about submission rules

The remaining properties provide unique validation value and will be included below.

### Properties

Property 1: Active petition blocking
*For any* user with an active petition (status not REJECTED or COMPLETED), attempting to create a new petition should throw ActivePetitionExistsException
**Validates: Requirements 1.2**

Property 2: Terminal status allows submission
*For any* user whose most recent petition has status REJECTED or COMPLETED, the user should be able to submit a new petition successfully
**Validates: Requirements 1.3**

Property 3: Completed status is not active
*For any* petition with current status COMPLETED, the isActive() method should return false
**Validates: Requirements 2.4**

Property 4: Status addition includes timestamp
*For any* petition and status type, when adding a new status, the created PetitionStatus should have a non-null createdAt timestamp
**Validates: Requirements 3.3**

Property 5: Status history ordering
*For any* petition with multiple statuses, the status history should be ordered by createdAt in ascending order (oldest first)
**Validates: Requirements 3.4**

Property 6: Status history contains only actual records
*For any* petition, the status history should contain exactly the statuses that were explicitly added, with no additional or missing entries
**Validates: Requirements 5.1**

## การจัดการข้อผิดพลาด

### Exception Classes

```java
public class ActivePetitionExistsException extends RuntimeException {
    public ActivePetitionExistsException(String message) {
        super(message);
    }
}

public class PetitionNotFoundException extends RuntimeException {
    public PetitionNotFoundException(String message) {
        super(message);
    }
}

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
```

### Error Handling Strategy

1. **ActivePetitionExistsException**: โยนเมื่อผู้ใช้พยายามยื่นคำร้องใหม่ขณะมีคำร้องที่กำลังดำเนินการ
   - จัดการใน Controller โดยแสดงหน้า cannot_submit.html
   - แสดงข้อความเป็นภาษาไทยที่เข้าใจง่าย

2. **PetitionNotFoundException**: โยนเมื่อไม่พบคำร้องที่ระบุ
   - จัดการด้วย @ExceptionHandler ใน Controller
   - แสดงหน้า 404 พร้อมข้อความเป็นภาษาไทย

3. **UserNotFoundException**: โยนเมื่อไม่พบผู้ใช้
   - ไม่ควรเกิดในสถานการณ์ปกติ (user ต้อง authenticated)
   - Log error และแสดงหน้า error ทั่วไป

4. **Validation Errors**: ใช้ Bean Validation (@Valid)
   - ตรวจสอบ title และ description ไม่ว่าง
   - แสดง error messages ในฟอร์ม

### Global Exception Handler

```java
@ControllerAdvice
public class PetitionExceptionHandler {
    
    @ExceptionHandler(PetitionNotFoundException.class)
    public String handlePetitionNotFound(PetitionNotFoundException ex, Model model) {
        model.addAttribute("error", ex.getMessage());
        return "error/404";
    }
    
    @ExceptionHandler(UserNotFoundException.class)
    public String handleUserNotFound(UserNotFoundException ex, Model model) {
        model.addAttribute("error", "เกิดข้อผิดพลาดในการระบุตัวตนผู้ใช้");
        return "error/500";
    }
}
```

## กลยุทธ์การทดสอบ

### Dual Testing Approach

ระบบนี้จะใช้ทั้ง unit tests และ property-based tests เพื่อให้ครอบคลุมการทดสอบอย่างสมบูรณ์:

- **Unit tests**: ทดสอบกรณีเฉพาะเจาะจง, edge cases, และเงื่อนไขข้อผิดพลาด
- **Property tests**: ทดสอบ properties ที่ต้องเป็นจริงกับ input ทุกชุด

### Property-Based Testing Configuration

เราจะใช้ **JUnit-Quickcheck** สำหรับ property-based testing ใน Java/Spring Boot:

```xml
<dependency>
    <groupId>com.pholser</groupId>
    <artifactId>junit-quickcheck-core</artifactId>
    <version>1.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.pholser</groupId>
    <artifactId>junit-quickcheck-generators</artifactId>
    <version>1.0</version>
    <scope>test</scope>
</dependency>
```

**Configuration**:
- แต่ละ property test จะรันขั้นต่ำ 100 iterations
- แต่ละ test จะมี comment tag อ้างอิง property จาก design document
- Tag format: `// Feature: petition-status-management, Property {number}: {property_text}`

### Unit Testing Strategy

**Service Layer Tests**:
- ทดสอบ `canUserSubmitPetition()` กับผู้ใช้ที่มีและไม่มีคำร้อง
- ทดสอบ `createPetition()` โยน exception เมื่อมี active petition
- ทดสอบ `addStatus()` เพิ่มสถานะได้ถูกต้อง
- ทดสอบ edge cases: user ไม่มีชื่อ, petition ไม่มี status

**Repository Layer Tests**:
- ทดสอบ `findActivePetitionByUserId()` หา active petition ได้ถูกต้อง
- ทดสอบ query ไม่คืนค่า petition ที่มีสถานะ REJECTED หรือ COMPLETED
- ทดสอบ `findAllByUserIdWithStatusHistory()` โหลด status history ครบถ้วน

**Controller Layer Tests**:
- ทดสอบ GET `/petitions/new` แสดงฟอร์มเมื่อไม่มี active petition
- ทดสอบ GET `/petitions/new` แสดงหน้า cannot_submit เมื่อมี active petition
- ทดสอบ POST `/petitions/create` สร้างคำร้องสำเร็จ
- ทดสอบ POST `/petitions/create` ล้มเหลวเมื่อมี validation errors
- ทดสอบ GET `/petitions/{id}` แสดงรายละเอียดพร้อม status history

**Integration Tests**:
- ทดสอบ end-to-end flow: สร้างคำร้อง → เพิ่มสถานะ → ตรวจสอบ history
- ทดสอบ transaction rollback เมื่อเกิด error
- ทดสอบ concurrent petition creation (race condition)

### Property-Based Testing Strategy

แต่ละ correctness property จะถูก implement เป็น property-based test:

**Property 1 Test**: สร้าง random users และ petitions ที่มีสถานะต่างๆ, ทดสอบว่า active petition blocking ทำงานถูกต้อง

**Property 2 Test**: สร้าง random users ที่มี petition ในสถานะ terminal, ทดสอบว่าสามารถยื่นคำร้องใหม่ได้

**Property 3 Test**: สร้าง random petitions ที่มีสถานะ COMPLETED, ทดสอบว่า isActive() คืนค่า false

**Property 4 Test**: สร้าง random petitions และ status types, ทดสอบว่า timestamp ถูกตั้งค่าเสมอ

**Property 5 Test**: สร้าง random petitions ที่มีหลาย statuses, ทดสอบว่า ordering ถูกต้อง

**Property 6 Test**: สร้าง random petitions และเพิ่ม statuses, ทดสอบว่า history มีเฉพาะ records ที่เพิ่มจริง

### Test Data Generators

สำหรับ property-based testing เราจะสร้าง custom generators:

```java
public class PetitionGenerator extends Generator<Petition> {
    @Override
    public Petition generate(SourceOfRandomness random, GenerationStatus status) {
        // Generate random petition with random status history
    }
}

public class StatusTypeGenerator extends Generator<StatusType> {
    @Override
    public StatusType generate(SourceOfRandomness random, GenerationStatus status) {
        // Generate random status type
    }
}
```

### Coverage Goals

- **Line Coverage**: ≥ 80%
- **Branch Coverage**: ≥ 75%
- **Property Tests**: ครอบคลุมทุก correctness property
- **Unit Tests**: ครอบคลุม edge cases และ error conditions
