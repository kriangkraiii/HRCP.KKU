-- Combined Migration Script for Petition Status Management System
-- This script creates both petitions and petition_statuses tables with proper indexes
-- Requirements: 3.3
-- 
-- Usage:
--   mysql -u root -p ecommerce_db < src/main/resources/db/migration/combined_migration.sql

-- ============================================================================
-- Create petitions table
-- ============================================================================

CREATE TABLE IF NOT EXISTS petitions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_petition_user 
        FOREIGN KEY (user_id) 
        REFERENCES user_dtls(id) 
        ON DELETE RESTRICT,
    
    INDEX idx_petition_user_id (user_id),
    INDEX idx_petition_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- Create petition_statuses table
-- ============================================================================

CREATE TABLE IF NOT EXISTS petition_statuses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    petition_id BIGINT NOT NULL,
    status_type VARCHAR(50) NOT NULL,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_petition_status_petition 
        FOREIGN KEY (petition_id) 
        REFERENCES petitions(id) 
        ON DELETE CASCADE,
    
    INDEX idx_petition_status_petition_id (petition_id),
    INDEX idx_petition_status_created_at (created_at),
    INDEX idx_petition_status_composite (petition_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- Verification queries (optional - uncomment to verify)
-- ============================================================================

-- SHOW TABLES LIKE 'petition%';
-- DESCRIBE petitions;
-- DESCRIBE petition_statuses;
-- SHOW INDEX FROM petitions;
-- SHOW INDEX FROM petition_statuses;
