ALTER TABLE diary
    ADD COLUMN attachment_bindings LONGTEXT NULL COMMENT '日记附件与段落绑定（JSON数组）',
    ADD COLUMN attachment_display_mode VARCHAR(16) NOT NULL DEFAULT 'INLINE' COMMENT '附件展示模式：INLINE/TRIGGER';
