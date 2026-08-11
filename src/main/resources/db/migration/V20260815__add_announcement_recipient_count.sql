ALTER TABLE notification_announcement
    ADD COLUMN recipient_count BIGINT NOT NULL DEFAULT 0
        COMMENT 'Number of recipients at publication time'
        AFTER content;
