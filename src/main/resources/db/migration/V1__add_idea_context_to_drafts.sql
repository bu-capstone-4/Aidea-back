ALTER TABLE drafts
    ADD COLUMN idea_context MEDIUMTEXT NULL
    AFTER document_id;
