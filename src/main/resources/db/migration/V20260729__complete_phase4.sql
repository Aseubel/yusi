ALTER TABLE diary
    ADD COLUMN audio_object_key VARCHAR(512) NULL COMMENT '语音日记原始音频 OSS object key';

ALTER TABLE developer_config
    ADD COLUMN scopes VARCHAR(512) NOT NULL DEFAULT 'MEMORY_READ' COMMENT 'API Key capability scopes';

ALTER TABLE developer_config
    ADD COLUMN revoked_at DATETIME NULL COMMENT 'API Key revoke timestamp';
