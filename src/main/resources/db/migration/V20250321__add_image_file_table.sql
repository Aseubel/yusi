CREATE TABLE IF NOT EXISTS `image_file` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `file_md5` VARCHAR(64) NOT NULL COMMENT '文件MD5/SHA256哈希',
    `object_key` VARCHAR(512) NOT NULL COMMENT 'OSS对象存储Key',
    `user_id` VARCHAR(64) NOT NULL COMMENT '上传用户ID',
    `file_name` VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
    `file_size` BIGINT DEFAULT NULL COMMENT '文件大小(字节)',
    `content_type` VARCHAR(64) DEFAULT NULL COMMENT 'MIME类型',
    `width` INT DEFAULT NULL COMMENT '图片宽度',
    `height` INT DEFAULT NULL COMMENT '图片高度',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_image_file_md5` (`file_md5`),
    KEY `idx_image_file_user_id` (`user_id`),
    KEY `idx_image_file_object_key` (`object_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片文件存储映射表(用于秒传和去重)';
