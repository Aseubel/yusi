SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `user_location`;

DROP TABLE IF EXISTS `soul_resonance`;

DROP TABLE IF EXISTS `soul_message`;

DROP TABLE IF EXISTS `soul_match`;

DROP TABLE IF EXISTS `soul_card`;

DROP TABLE IF EXISTS `situation_room`;

DROP TABLE IF EXISTS `situation_scenario`;

DROP TABLE IF EXISTS `room_message`;

DROP TABLE IF EXISTS `prompt_template`;

DROP TABLE IF EXISTS `interface_daily_usage`;

DROP TABLE IF EXISTS `diary`;

DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` VARCHAR(255) DEFAULT NULL,
    `username` VARCHAR(255) DEFAULT NULL,
    `password` VARCHAR(255) DEFAULT NULL,
    `email` VARCHAR(255) DEFAULT NULL,
    `is_match_enabled` TINYINT(1) DEFAULT NULL,
    `match_intent` VARCHAR(255) DEFAULT NULL,
    `permission_level` INT NOT NULL DEFAULT 0,
    `key_mode` VARCHAR(255) DEFAULT NULL,
    `has_cloud_backup` TINYINT(1) DEFAULT NULL,
    `encrypted_backup_key` VARCHAR(1024) DEFAULT NULL,
    `key_salt` VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_user_id` (`user_id`),
    UNIQUE KEY `uk_user_username` (`username`),
    KEY `idx_user_permission_level` (`permission_level`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE `diary` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `diary_id` VARCHAR(255) DEFAULT NULL,
    `user_id` VARCHAR(255) DEFAULT NULL,
    `title` VARCHAR(255) DEFAULT NULL,
    `content` TEXT,
    `client_encrypted` TINYINT(1) DEFAULT NULL,
    `visibility` TINYINT(1) DEFAULT NULL,
    `entry_date` DATE DEFAULT NULL,
    `ai_analysis_status` INT DEFAULT NULL,
    `ai_response` VARCHAR(1000) DEFAULT NULL,
    `emotion` VARCHAR(32) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `latitude` DOUBLE DEFAULT NULL,
    `longitude` DOUBLE DEFAULT NULL,
    `address` VARCHAR(255) DEFAULT NULL,
    `place_name` VARCHAR(255) DEFAULT NULL,
    `place_id` VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_diary_diary_id` (`diary_id`),
    KEY `idx_diary_user_id` (`user_id`),
    KEY `idx_diary_entry_date` (`entry_date`),
    KEY `idx_diary_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE `interface_daily_usage` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` VARCHAR(64) NOT NULL,
    `ip` VARCHAR(64) NOT NULL,
    `interface_name` VARCHAR(128) NOT NULL,
    `usage_date` DATE NOT NULL,
    `request_count` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_ip_interface_date` (
        `user_id`,
        `ip`,
        `interface_name`,
        `usage_date`
    ),
    KEY `idx_interface_daily_usage_date` (`usage_date`),
    KEY `idx_interface_daily_usage_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE `prompt_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) DEFAULT NULL,
    `template` TEXT,
    `version` VARCHAR(255) DEFAULT NULL,
    `active` TINYINT(1) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_prompt_template_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE `room_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `room_code` VARCHAR(32) NOT NULL,
    `sender_id` VARCHAR(64) NOT NULL,
    `sender_name` VARCHAR(64) DEFAULT NULL,
    `content` TEXT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_room_code` (`room_code`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE `situation_scenario` (
    `id` VARCHAR(32) NOT NULL,
    `title` VARCHAR(100) DEFAULT NULL,
    `description` TEXT,
    `submitter_id` VARCHAR(255) DEFAULT NULL,
    `reject_reason` TEXT,
    `status` INT DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE `situation_room` (
    `code` VARCHAR(32) NOT NULL,
    `status` VARCHAR(20) DEFAULT NULL,
    `owner_id` VARCHAR(255) DEFAULT NULL,
    `scenario_id` VARCHAR(255) DEFAULT NULL,
    `members` TEXT,
    `submissions` TEXT,
    `submission_visibility` TEXT,
    `cancel_votes` TEXT,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `report` TEXT,
    PRIMARY KEY (`code`),
    KEY `idx_situation_room_owner_id` (`owner_id`),
    KEY `idx_situation_room_status` (`status`),
    KEY `idx_situation_room_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE `soul_card` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `content` TEXT,
    `origin_id` VARCHAR(255) DEFAULT NULL,
    `user_id` VARCHAR(255) DEFAULT NULL,
    `type` VARCHAR(255) DEFAULT NULL,
    `emotion` VARCHAR(255) DEFAULT NULL,
    `resonance_count` INT DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_soul_card_user_id` (`user_id`),
    KEY `idx_soul_card_created_at` (`created_at`),
    KEY `idx_soul_card_emotion` (`emotion`),
    KEY `idx_soul_card_origin_id` (`origin_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE `soul_match` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_a_id` VARCHAR(255) DEFAULT NULL,
    `user_b_id` VARCHAR(255) DEFAULT NULL,
    `letter_a_to_b` VARCHAR(2000) DEFAULT NULL,
    `letter_b_to_a` VARCHAR(2000) DEFAULT NULL,
    `status_a` INT DEFAULT NULL,
    `status_b` INT DEFAULT NULL,
    `is_matched` TINYINT(1) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_soul_match_user_a` (`user_a_id`),
    KEY `idx_soul_match_user_b` (`user_b_id`),
    KEY `idx_soul_match_is_matched` (`is_matched`),
    KEY `idx_soul_match_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE `soul_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `match_id` BIGINT DEFAULT NULL,
    `sender_id` VARCHAR(255) DEFAULT NULL,
    `receiver_id` VARCHAR(255) DEFAULT NULL,
    `content` VARCHAR(1000) DEFAULT NULL,
    `is_read` TINYINT(1) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_soul_message_match_id` (`match_id`),
    KEY `idx_soul_message_receiver_id` (`receiver_id`),
    KEY `idx_soul_message_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE `soul_resonance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `card_id` BIGINT DEFAULT NULL,
    `user_id` VARCHAR(255) DEFAULT NULL,
    `type` VARCHAR(255) DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_soul_resonance_card_user` (`card_id`, `user_id`),
    KEY `idx_soul_resonance_user_id` (`user_id`),
    KEY `idx_soul_resonance_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE `user_location` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `location_id` VARCHAR(255) DEFAULT NULL,
    `user_id` VARCHAR(255) DEFAULT NULL,
    `name` VARCHAR(255) DEFAULT NULL,
    `latitude` DOUBLE DEFAULT NULL,
    `longitude` DOUBLE DEFAULT NULL,
    `address` VARCHAR(255) DEFAULT NULL,
    `place_id` VARCHAR(255) DEFAULT NULL,
    `location_type` VARCHAR(255) DEFAULT NULL,
    `icon` VARCHAR(255) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_location_location_id` (`location_id`),
    KEY `idx_user_location_user_id` (`user_id`),
    KEY `idx_user_location_place_id` (`place_id`),
    KEY `idx_user_location_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;
