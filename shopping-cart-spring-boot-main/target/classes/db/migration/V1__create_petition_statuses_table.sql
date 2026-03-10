-- Migration script for petition_statuses table
-- This script creates the petition_statuses table with proper indexes for performance
-- Requirements: 3.3

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
