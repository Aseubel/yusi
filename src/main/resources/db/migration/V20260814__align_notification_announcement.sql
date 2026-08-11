-- Keep administrator-authored announcement content separate from per-user inbox state.
CREATE TABLE notification_announcement (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    announcement_id VARCHAR(64) NOT NULL COMMENT 'Stable announcement identifier',
    title VARCHAR(120) NOT NULL COMMENT 'Announcement title',
    content TEXT NOT NULL COMMENT 'Announcement body',
    audience_type VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT 'Audience type: ALL',
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'Publication status: PUBLISHED',
    published_by VARCHAR(64) NOT NULL COMMENT 'Publishing administrator user ID',
    published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Publication timestamp',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp',
    PRIMARY KEY (id),
    UNIQUE KEY uk_announcement_id (announcement_id),
    KEY idx_announcement_status_published (status, published_at),
    KEY idx_announcement_publisher (published_by)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Administrator announcement publications';

ALTER TABLE user_notification
    ADD COLUMN announcement_id VARCHAR(64) DEFAULT NULL COMMENT 'Source announcement ID for announcement inbox items',
    ADD KEY idx_notification_announcement (announcement_id),
    ADD UNIQUE KEY uk_notification_user_announcement (user_id, announcement_id);
