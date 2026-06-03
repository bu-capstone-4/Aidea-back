ALTER TABLE drafts
    ADD COLUMN error_code VARCHAR(255) NULL
    AFTER error_message;
