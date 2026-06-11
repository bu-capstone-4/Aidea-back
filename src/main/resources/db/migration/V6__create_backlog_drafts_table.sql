CREATE TABLE backlog_drafts (
    id           VARCHAR(255) NOT NULL,
    teamspace_id VARCHAR(100) NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    error_code   VARCHAR(255) NULL,
    error_message TEXT NULL,
    created_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_backlog_drafts_teamspace_id (teamspace_id)
);
