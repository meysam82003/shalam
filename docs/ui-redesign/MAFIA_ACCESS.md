# Mafia access contract

- Every Mafia screen, scenario, card, audio tool, group, report and custom role requires active Pro or the same account/device's signed trial. Admin overrides cannot make Mafia free.
- Registration reserves one eligibility record per registration identity. It does not start the timer.
- After a logged-in client receives and validates all feature settings from `GET v1/config`, it acknowledges with `POST v1/mafia/trial/start`. The server atomically assigns a 600-second interval once. Failed config fetches do not start it. Retried start requests return the original interval.
- The trial is signed and bound to account and session device. It never grants other Pro features. Closing, backgrounding, logout, reconnecting and changing VPN do not pause or reset the server deadline.
- A monotonic in-memory lease closes access at the deadline even if wall time changes. Restarting the app process requires online account validation for any remaining trial time. Paid subscriptions retain their existing offline policy.
- Expiry removes Mafia screens from the navigation stack, dismisses dialogs, stops audio/recording, and presents the purchase screen without deleting saved games.
- Missing, malformed or incomplete remote settings use explicit cold-start defaults matching the supplied panel settings. Unknown modules are closed. Explicitly free scorekeeping remains offline.

## Device identity and limitations

The Android registration identifier is a namespaced SHA-256 digest of ANDROID_ID, without the app package in its namespace. The server stores a keyed hash, independently of IP addresses, in an atomic unique-key ledger. Accounts and subscription-device release cannot erase it. No IMEI or advertising ID is collected.

This is duplicate prevention for the official client, not hardware attestation: factory resets, another OS profile, signing-key changes or a modified client can change/spoof the identifier. Browser storage can be cleared. No guarantee of permanent one-person/one-physical-phone identity is made. VPN changes alone do not reset eligibility. Existing accounts remain usable but do not automatically receive a new registration trial.

## Rollout requirements

Migration 12 adds the ledger and session identity column without deleting existing records. Back up and stage-test the backend first. Deploy the updated server/API and official signed app together after approval. Older clients without registration_id cannot create new accounts after this change; login and paid access remain available. An old installed APK's offline first-launch bug cannot be repaired by a server-only update.

Preview CI builds are isolated debug packages. Unsigned-official release build outputs are compilation checks only, not production updates. No production deployment, main merge or release publication is included. Real-device smoke tests and an APK signed with the existing release key are release gates.

## Verification

Added Android regressions for cold-start/malformed settings, strict signed trial binding, pending eligibility, exact deadline and non-renewing monotonic lease. Added server regressions for deferred start, repeated starts, duplicate devices with changed IP, rollback, account/device mismatch and ledger survival. CI also runs existing Android, PHP/payment and shared-web compilation checks.
