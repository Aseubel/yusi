-- 为diary表添加images字段
-- 用于存储图片的OSS objectKey列表（JSON数组格式）

ALTER TABLE diary ADD COLUMN images TEXT NULL COMMENT '图片列表（JSON数组格式存储OSS objectKey）';
