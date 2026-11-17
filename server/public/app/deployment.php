<?php
/** Public deployment defaults; never put credentials in this file. */
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }
return [
    'public_origin' => 'https://divan-emtiaz.ir',
    'service_base' => 'https://divan-emtiaz.ir/divan',
    'direct_payments' => true,
    'legacy_hosts' => ['httpuser87890.ir', 'www.httpuser87890.ir'],
];
