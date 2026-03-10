-- Verification Script for Database Indexes
-- This script checks if all required indexes are created properly
-- Requirements: 3.3
--
-- Usage:
--   mysql -u root -p ecommerce_db < src/main/resources/db/migration/verify_indexes.sql

-- ============================================================================
-- Show all tables
-- ============================================================================

SELECT '=== Tables ===' AS '';
SHOW TABLES LIKE 'petition%';

-- ============================================================================
-- Show petitions table structure
-- ============================================================================

SELECT '=== Petitions Table Structure ===' AS '';
DESCRIBE petitions;

SELECT '=== Petitions Table Indexes ===' AS '';
SHOW INDEX FROM petitions;

-- ============================================================================
-- Show petition_statuses table structure
-- ============================================================================

SELECT '=== Petition Statuses Table Structure ===' AS '';
DESCRIBE petition_statuses;

SELECT '=== Petition Statuses Table Indexes ===' AS '';
SHOW INDEX FROM petition_statuses;

-- ============================================================================
-- Verify index performance with EXPLAIN
-- ============================================================================

SELECT '=== Query Performance Analysis ===' AS '';

-- Test query 1: Find active petition by user
EXPLAIN SELECT p.* 
FROM petitions p 
WHERE p.user_id = 1;

-- Test query 2: Find status history for a petition
EXPLAIN SELECT ps.* 
FROM petition_statuses ps 
WHERE ps.petition_id = 1 
ORDER BY ps.created_at ASC;

-- Test query 3: Find latest status for a petition
EXPLAIN SELECT ps.* 
FROM petition_statuses ps 
WHERE ps.petition_id = 1 
ORDER BY ps.created_at DESC 
LIMIT 1;

-- ============================================================================
-- Index statistics
-- ============================================================================

SELECT '=== Index Statistics ===' AS '';

SELECT 
    TABLE_NAME,
    INDEX_NAME,
    SEQ_IN_INDEX,
    COLUMN_NAME,
    CARDINALITY,
    INDEX_TYPE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('petitions', 'petition_statuses')
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;
