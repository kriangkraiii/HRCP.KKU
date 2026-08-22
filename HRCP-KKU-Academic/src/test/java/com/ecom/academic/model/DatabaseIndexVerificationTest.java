package com.ecom.academic.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.external.model.FsFaculty;
import com.ecom.external.model.FsFacultyChange;
import com.ecom.external.model.ScopusPublication;
import com.ecom.model.AdminLog;
import com.ecom.model.Notification;
import com.ecom.model.UserDtls;

import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Automated test suite to verify that all JPA entities across HRCP.KKU
 * have proper database indexing configured, that index names are unique,
 * and that high-frequency query columns are covered.
 */
class DatabaseIndexVerificationTest {

    private static final List<Class<?>> INDEXED_ENTITIES = List.of(
            UserDtls.class,
            Notification.class,
            AdminLog.class,
            AcademicRequest.class,
            PositionRequest.class,
            AcademicAttachment.class,
            PositionAttachment.class,
            AcademicDocument.class,
            PositionDocument.class,
            RequestStatusHistory.class,
            PositionStatusHistory.class,
            AcademicDocumentEditLog.class,
            PositionDocumentEditLog.class,
            StaffMember.class,
            Petition.class,
            PetitionStatus.class,
            UserFolder.class,
            UserFile.class,
            AdminFolder.class,
            AdminFile.class,
            FsFaculty.class,
            ScopusPublication.class,
            FsFacultyChange.class
    );

    @Test
    @DisplayName("Every core entity must have @Table annotation with at least one index")
    void allEntitiesMustHaveIndexes() {
        for (Class<?> entityClass : INDEXED_ENTITIES) {
            Table table = entityClass.getAnnotation(Table.class);
            assertNotNull(table, "Entity " + entityClass.getSimpleName() + " must have @Table annotation");
            assertTrue(table.indexes().length > 0,
                    "Entity " + entityClass.getSimpleName() + " must define at least one index in @Table(indexes = {...})");
        }
    }

    @Test
    @DisplayName("All index names must be unique across the entire application and follow naming conventions")
    void indexNamesMustBeUniqueAndFollowConvention() {
        Set<String> indexNames = new HashSet<>();
        List<String> duplicates = new ArrayList<>();

        for (Class<?> entityClass : INDEXED_ENTITIES) {
            Table table = entityClass.getAnnotation(Table.class);
            if (table == null) continue;

            for (Index idx : table.indexes()) {
                String name = idx.name();
                assertFalse(name.isBlank(), "Index on " + entityClass.getSimpleName() + " must have a name");
                assertTrue(name.startsWith("idx_"),
                        "Index " + name + " on " + entityClass.getSimpleName() + " should start with 'idx_'");

                if (!indexNames.add(name)) {
                    duplicates.add(name + " in " + entityClass.getSimpleName());
                }
            }
        }

        assertTrue(duplicates.isEmpty(), "Duplicate index names found: " + duplicates);
    }

    @Test
    @DisplayName("UserDtls must have indexes for email, resetToken, and role")
    void userDtlsSpecificIndexes() {
        assertEntityHasColumnsInIndex(UserDtls.class, "email");
        assertEntityHasColumnsInIndex(UserDtls.class, "resetToken");
        assertEntityHasColumnsInIndex(UserDtls.class, "role");
    }

    @Test
    @DisplayName("Notification must have composite indexes for recipient and unread/active status")
    void notificationSpecificIndexes() {
        assertEntityHasColumnsInIndex(Notification.class, "recipient_id");
        assertEntityHasColumnsInIndex(Notification.class, "createdAt DESC");
    }

    @Test
    @DisplayName("Academic and Position Requests must index applicant and status")
    void requestSpecificIndexes() {
        assertEntityHasColumnsInIndex(AcademicRequest.class, "applicant_id");
        assertEntityHasColumnsInIndex(AcademicRequest.class, "current_status");
        assertEntityHasColumnsInIndex(PositionRequest.class, "applicant_id");
        assertEntityHasColumnsInIndex(PositionRequest.class, "current_status");
    }

    @Test
    @DisplayName("Attachments and Documents must index request_id")
    void attachmentAndDocumentIndexes() {
        assertEntityHasColumnsInIndex(AcademicAttachment.class, "request_id");
        assertEntityHasColumnsInIndex(PositionAttachment.class, "request_id");
        assertEntityHasColumnsInIndex(AcademicDocument.class, "request_id");
        assertEntityHasColumnsInIndex(PositionDocument.class, "request_id");
    }

    private void assertEntityHasColumnsInIndex(Class<?> entityClass, String expectedColumn) {
        Table table = entityClass.getAnnotation(Table.class);
        assertNotNull(table, entityClass.getSimpleName() + " must have @Table");

        boolean found = false;
        for (Index idx : table.indexes()) {
            if (idx.columnList().contains(expectedColumn)) {
                found = true;
                break;
            }
        }
        assertTrue(found, entityClass.getSimpleName() + " must have an index covering column: " + expectedColumn);
    }
}
