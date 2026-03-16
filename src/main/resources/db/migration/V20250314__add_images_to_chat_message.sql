-- 为chat_memory_message表添加images字段
-- 用于存储聊天消息中的图片OSS objectKey列表（JSON数组格式）

ALTER TABLE chat_memory_message ADD COLUMN images TEXT NULL COMMENT '图片列表（JSON数组格式存储OSS objectKey）';
