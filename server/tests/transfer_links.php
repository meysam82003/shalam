<?php
require_once dirname(__DIR__) . '/public/app/game_transfer.php';

scenario('One-scan game transfer stores an opaque, expiring payload', function () {
    freshIp();
    $payload = str_repeat('AbCd1234_-', 1200);
    $created = apiPost('v1/game-transfer', ['payload' => $payload]);
    check(strlen($created['id']) === 32 && $created['expires_at'] === Clock::now() + 3600, 'token expires after one hour');
    check(apiGet('v1/game-transfer', ['id' => $created['id']]) === ['payload' => $payload], 'API round-trips the full sealed game');
    throws(fn() => apiPost('v1/game-transfer', ['payload' => '{"private":"role"}']), 'invalid_transfer', 'clear game JSON is rejected');
    throws(fn() => apiGet('v1/game-transfer', ['id' => 'broken']), 'invalid_transfer', 'invalid public token is rejected');
    $now = Clock::$fixed;
    Clock::$fixed = $created['expires_at'];
    try {
        throws(fn() => apiGet('v1/game-transfer', ['id' => $created['id']]), 'expired_transfer', 'expired data cannot be loaded');
        GameTransferLink::prune();
        check(Db::value('SELECT token FROM {game_transfers} WHERE token = ?', [$created['id']]) === null, 'cleanup erases expired payload');
    } finally {
        Clock::$fixed = $now;
    }
});

scenario('Carousel and live scoring placements support the common media', function () {
    $formats = array_column(Ads::placements(), 'formats', 'k');
    foreach (['home_carousel','home_inline','history_inline','hub_inline','referee_idle','sticky_top'] as $key) {
        check(array_diff(['image','gif','video','text'], explode(',', $formats[$key] ?? '')) === [], "$key supports all four media");
    }
    check($formats['rewarded_offer'] === 'rewarded', 'rewarded placement keeps its video-earned flow');
});
