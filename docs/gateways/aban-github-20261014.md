# Aban Gateway — official docs on GitHub (read 2026-10-14)

Source: https://github.com/AbanGateway/docs (commit f4e24d3, 2026-10-10), `api/README.md`, `api/invoices.md`,
`api/webhooks.md`, `api/errors.md`, `api/openapi.json`, `examples/php.md`.

Differences from the copy taken from abangateway.ir on 2026-10-13 (`aban-api-20261013.md`):

| | GitHub (used) | site copy |
|---|---|---|
| API address | `https://api.abangateway.ir/api/v1` | `https://abangateway.ir/api/v1` |
| `return_url` on create | yes | not listed |
| statuses | pending, paid, expired, cancelled | also partially_paid |
| error body | `{"error":{"code","message","details"}}`; 401 is `{"detail"}` | — |

Because the address has already changed once, the API address and the payment-page domain are fields in the Divan
panel (روش‌های پرداخت → آبان / بلوپال). Empty = the documented default.

## Calls used by Divan
- `POST /invoices` {amount_rial, order_id (our 20-char order code), callback_url (= /pay/webhook.php?m=aban),
  return_url (= /pay/return.php?o=…), description, expiry_minutes: 60} → 201 {invoice_id, status: pending,
  amount_rial, payable_rial, order_id, payment_url, is_test, …}
- `GET /invoices/{id}` → status pending/paid/expired/cancelled
- `POST /invoices/{id}/verify` → 200 {verified: true, …}; 409 already_verified = success; 402 not_yet_paid
- `POST /invoices/{id}/simulate-payment` — test token only (used only by the owner for a sandbox test)
- Webhook: POST to callback_url, header `X-Signature` = hex HMAC-SHA256(raw body, shop webhook secret);
  events invoice.paid / invoice.expired / invoice.cancelled; retried 5 times; any 2xx = received.
- Auth: `Authorization: Bearer ag_live_…` / `ag_test_…`; rate limit → 429 + Retry-After.

## Divan rules
- Grant only when GET says paid for the same invoice, the same amount_rial, the same order_id and the same
  is_test as the token, and verify succeeds (or says already_verified). One atomic order update → one grant.
- Test token only with the panel sandbox switch on; live token only with it off.
- Aban's servers geo-block foreign IPs (connection reset from outside Iran), so the default route is the Iran
  bridge (action `aban.call`, bridge 1.1.0). The bridge accepts the panel's address only if it is a plain HTTPS
  domain (optionally pinned with `aban_api_hosts` in its config) and only the four invoice calls.
