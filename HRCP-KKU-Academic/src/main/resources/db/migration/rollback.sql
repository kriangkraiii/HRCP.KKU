-- Rollback Script for Petition Status Management System
-- This script removes the petition_statuses and petitions tables
-- 
-- WARNING: This will delete all data in these tables!
-- Make sure to backup your data before running this script.
--
-- Usage:
--   mysql -u root -p ecommerce_db < src/main/resources/db/migration/rollback.sql

-- ============================================================================
-- Drop tables in reverse order (child tables first)
-- ============================================================================

-- Drop petition_statuses table first (has foreign key to petitions)
DROP TABLE IF EXISTS petition_statuses;

-- Drop petitions table
DROP TABLE IF EXISTS petitions;

-- ============================================================================
-- Verification (optional - uncomment to verify)
-- ============================================================================

-- SHOW TABLES LIKE 'petition%';
