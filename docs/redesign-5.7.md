# Divan 5.7: cinematic native interface

The supplied references guide visual presentation only. App labels, game rules, plan prices and access decisions continue to come from the existing code and panel.

The home presents four card-game tiles, three party-game tiles, the Pro entry and the previous tool shortcuts in a dedicated tools hub. Existing resume selection, privacy of hidden scores, ad slots and gift access remain in place.

`CinemaUi.kt` implements native material drawing, dimensional symbols, scene backgrounds and artwork views. All text, fields, buttons, lists and game controls remain native views. The ornamental backdrop is a bundled, existing project asset; no picture of a complete interface is used as a screen. The Mafia and pantomime header illustrations are bundled locally. Cached artwork keys incorporate asynchronous bitmap readiness in the shared browser runtime.

| Stored id | Material |
| --- | --- |
| cobalt | زمرد سلطنتی — emerald velvet and gold |
| iranic | مروارید — ivory and champagne |
| neon | تئاتر نئون — violet glass and stage light |
| emerald | نیمه‌شب — navy and polished steel |
| copper | مخمل سرخ — burgundy and warm metal |
| ivory | کریستال — pale glass and silver blue |

Stored theme IDs are unchanged. Four dark and two light materials apply to the app's shared controls. Game presentation scopes add Mafia crimson, theatre violet and spy blue without changing stored settings. Original avatar IDs, personal photos, role portraits and hidden/revealed states are preserved. Pantomime's revealed word uses an ornamental paper card while its previous-word display and privacy controls retain their behavior.

Game engines, saved-game formats, repositories, billing, subscription/device-trial enforcement, reward limits, audio lifecycle and direct Iranian hosting are unchanged by this visual layer. Package IDs and the official signing identity are retained.

Validation in GitHub Actions:
- JVM game/theme regressions across all three Android release flavors.
- Release APK assembly and verification of the original signing certificate.
- Server and payment compatibility regressions.
- Compilation of the same screen sources to the shared browser runtime.
- Real-screen browser smoke captures at 390 × 844 in all six materials: home, setup, score board, score entry and settings, plus pantomime/spy hubs and the enforced Mafia access gate in the main dark materials. Browser errors are checked and external requests are blocked.

Physical Android device testing, merging, release publication and live-host deployment are not performed automatically.
