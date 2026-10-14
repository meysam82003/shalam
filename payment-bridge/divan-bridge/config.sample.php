<?php
/**
 * Divan payment gateway settings. Copy this file to «config.php» in the same folder and fill it in.
 * config.php holds secrets: never put it in the public folder, in GitHub or in a chat.
 */
return [
    // Shared HMAC keys by id (64 hex characters each). The same key and id go into the Divan panel
    // (تنظیمات ← واسط پرداخت ایران). To change the key: add the new one here (e.g. 'k2'), switch the
    // panel to it, then remove the old one.
    'keys' => [
        'k1' => 'PASTE-64-HEX-CHARACTERS-FROM-THE-DIVAN-PANEL',
    ],

    // Only the main Divan server (Netherlands) may call the gateway.
    'allowed_ips' => ['190.2.143.208'],

    // Package names and product ids that may be checked (empty skus = any id the store knows).
    'packages' => ['com.meysam.divanemtiaz'],
    'skus' => ['divan_pro_1m', 'divan_pro_3m', 'divan_pro_6m', 'divan_pro_lifetime'],

    // Myket developer panel → API → X-Access-Token
    'myket_access_token' => '',
    // 'tokens' = documented GET …/applications/{pkg}/purchases/products/{sku}/tokens/{token} (default);
    // 'partners' = older POST …/partners/applications/{pkg}/purchases/products/{sku}/verify
    'myket_verify' => 'tokens',

    // Cafe Bazaar developer panel → API key (CAFEBAZAAR-PISHKHAN-API-SECRET)
    'bazaar_api_secret' => '',
    // Older Bazaar OAuth, only if you have no API key above.
    'bazaar_client_id' => '',
    'bazaar_client_secret' => '',
    'bazaar_refresh_token' => '',

    // ZarinPal (site version only): the buyer may only be returned to these hosts; optional merchant id allowlist.
    'callback_hosts' => ['httpuser87890.ir'],
    'zarinpal_merchants' => [],

    // Aban Gateway (site version only): its API address comes from the Divan panel. To pin it, list the
    // allowed API domains here (e.g. ['api.abangateway.ir']); empty = any HTTPS domain the panel sets.
    // The buyer and the webhook may only go to «callback_hosts» above.
    'aban_api_hosts' => [],

    // Changing Myket prices from the Divan panel (consumable products only). Off unless you need it.
    'allow_set_price' => false,

    'rate_per_minute' => 120,
    'timeout' => 20,
];
