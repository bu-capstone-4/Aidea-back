ALTER TABLE drafts
    ADD COLUMN questions JSON NULL AFTER idea_context,
    ADD COLUMN answers   JSON NULL AFTER questions;
