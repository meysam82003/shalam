<?php
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }
final class TgSchema
{
    public static function sql(): array
    {
        $e = 'ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci';
        return [
            "CREATE TABLE IF NOT EXISTS {tg_members} (id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY, admin_id INT UNSIGNED NOT NULL, telegram_id BIGINT UNSIGNED NOT NULL, role VARCHAR(20) NOT NULL, permissions TEXT NULL, active TINYINT NOT NULL DEFAULT 1, epoch INT NOT NULL DEFAULT 1, quiet_from INT NULL, quiet_to INT NULL, digest VARCHAR(10) NOT NULL DEFAULT 'off', created_at DATETIME NOT NULL, UNIQUE KEY uq_admin(admin_id), UNIQUE KEY uq_telegram(telegram_id)) $e",
            "CREATE TABLE IF NOT EXISTS {tg_links} (code_hash CHAR(64) PRIMARY KEY, admin_id INT UNSIGNED NOT NULL, telegram_id BIGINT UNSIGNED NOT NULL, role VARCHAR(20) NOT NULL, expires_at DATETIME NOT NULL, used_at DATETIME NULL, created_at DATETIME NOT NULL) $e",
            "CREATE TABLE IF NOT EXISTS {tg_sessions} (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, token_hash CHAR(64) NOT NULL, member_id INT UNSIGNED NOT NULL, epoch INT NOT NULL, expires_at DATETIME NOT NULL, revoked_at DATETIME NULL, created_at DATETIME NOT NULL, UNIQUE KEY uq_token(token_hash), KEY k_member(member_id)) $e",
            "CREATE TABLE IF NOT EXISTS {tg_replays} (digest CHAR(64) PRIMARY KEY, expires_at DATETIME NOT NULL) $e",
            "CREATE TABLE IF NOT EXISTS {tg_requests} (session_id BIGINT UNSIGNED NOT NULL, request_key VARCHAR(64) NOT NULL, request_hash CHAR(64) NOT NULL, result_json MEDIUMTEXT NOT NULL, created_at DATETIME NOT NULL, PRIMARY KEY(session_id,request_key)) $e",
            "CREATE TABLE IF NOT EXISTS {tg_events} (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, event_key CHAR(64) NOT NULL, type VARCHAR(40) NOT NULL, entity VARCHAR(32) NOT NULL, entity_id VARCHAR(64) NOT NULL, created_at DATETIME NOT NULL, UNIQUE KEY uq_event(event_key), KEY k_time(created_at)) $e",
            "CREATE TABLE IF NOT EXISTS {tg_outbox} (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, event_id BIGINT UNSIGNED NULL, member_id INT UNSIGNED NOT NULL, epoch INT NOT NULL, payload_enc MEDIUMTEXT NULL, state VARCHAR(16) NOT NULL DEFAULT 'pending', priority INT NOT NULL DEFAULT 1, attempts INT NOT NULL DEFAULT 0, available_at DATETIME NOT NULL, locked_at DATETIME NULL, message_id BIGINT NULL, last_error VARCHAR(120) NULL, created_at DATETIME NOT NULL, sent_at DATETIME NULL, UNIQUE KEY uq_delivery(event_id,member_id), KEY k_due(state,available_at,priority)) $e",
            "CREATE TABLE IF NOT EXISTS {tg_updates} (update_id BIGINT PRIMARY KEY, created_at DATETIME NOT NULL) $e",
            "CREATE TABLE IF NOT EXISTS {tg_limits} (k CHAR(64) PRIMARY KEY, window_start BIGINT NOT NULL, hits INT NOT NULL) $e",
            "CREATE TABLE IF NOT EXISTS {tg_ticket_meta} (request_id BIGINT UNSIGNED PRIMARY KEY, priority VARCHAR(12) NOT NULL DEFAULT 'normal', read_at DATETIME NULL) $e",
        ];
    }
}
