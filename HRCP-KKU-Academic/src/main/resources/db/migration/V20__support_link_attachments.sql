-- V20: Support external file links in academic_attachment
-- Expand stored_file_path to 2048 characters to accommodate long URLs (Google Drive, OneDrive, SharePoint, etc.)
-- Expand original_filename to 500 characters for descriptive link titles

ALTER TABLE academic_attachment
    ALTER COLUMN stored_file_path TYPE VARCHAR(2048);

ALTER TABLE academic_attachment
    ALTER COLUMN original_filename TYPE VARCHAR(500);
