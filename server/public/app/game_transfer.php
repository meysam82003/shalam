<?php
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }

/** Stores only authenticated ciphertext. Encryption and the decryption key stay on the phones. */
final class GameTransferLink
{
    public const MAX_PAYLOAD = 5600000;
    private const LIFETIME = 3600;
    private const STORAGE_LIMIT = 128 * 1024 * 1024;

    public static function create(array $input): array
    {
        if (!RateLimit::hit('game_transfer_write:' . Http::ip(), 30, 3600)) {
            throw new ApiError('rate_limited', 'بعداً دوباره کد انتقال بسازید.', 429);
        }
        $payload = $input['payload'] ?? null;
        if (!is_string($payload) || strlen($payload) < 40 || strlen($payload) > self::MAX_PAYLOAD ||
            !preg_match('/^[A-Za-z0-9_-]+$/D', $payload)) {
            throw new ApiError('invalid_transfer', 'فایل انتقال معتبر نیست.', 400);
        }
        self::prune();
        $used = (int)Db::value('SELECT COALESCE(SUM(byte_count), 0) FROM {game_transfers}');
        if ($used + strlen($payload) > self::STORAGE_LIMIT) {
            throw new ApiError('transfer_busy', 'فعلاً کد در دسترس نیست. فایل بازی را بفرستید.', 503);
        }
        $id = bin2hex(random_bytes(16));
        $expires = Clock::now() + self::LIFETIME;
        Db::insert('game_transfers', [
            'token' => $id, 'payload' => $payload, 'byte_count' => strlen($payload), 'expires_at' => $expires,
        ]);
        return ['id' => $id, 'expires_at' => $expires];
    }

    public static function read(array $input): array
    {
        $id = $input['id'] ?? null;
        if (!is_string($id) || !preg_match('/^[a-f0-9]{32}$/D', $id)) {
            throw new ApiError('invalid_transfer', 'کد انتقال معتبر نیست.', 400);
        }
        if (!RateLimit::hit('game_transfer_read:' . Http::ip(), 90, 3600)) {
            throw new ApiError('rate_limited', 'بعداً دوباره کد را اسکن کنید.', 429);
        }
        $row = Db::one('SELECT payload FROM {game_transfers} WHERE token = ? AND expires_at > ?', [$id, Clock::now()]);
        if ($row === null) throw new ApiError('expired_transfer', 'لینک انتقال پیدا نشد یا منقضی شده است.', 404);
        return $row;
    }

    public static function prune(): void
    {
        Db::run('DELETE FROM {game_transfers} WHERE expires_at <= ?', [Clock::now()]);
    }
}
