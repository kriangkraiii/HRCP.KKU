-- Migration script for petitions table
-- This script creates the petitions table if it doesn't exist
-- Requirements: 3.3

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
