# Divan 5.7 visual redesign

The native Android app keeps its package IDs, signing certificate, game engines, repositories, saved-game format and offline behavior. The shared TeaVM build uses the same screen code. No host deployment or release publication is part of this change.

| Saved theme id | New identity | Layout |
| --- | --- | --- |
| cobalt | اطلس / Atlas | Dark cyan dashboard, compact two-column games, grid background |
| iranic | کاغذ / Paper | Warm paper, editorial headline, single-column game list, angular controls |
| neon | اوربیت / Orbit | Violet glass, centered hero, asymmetric game grid, compact navigation |
| emerald | نخل / Palm | Light sage, organic rounded cards, centered opening and roomy game tiles |
| copper | ولتا / Volta | Charcoal and acid yellow, squared controls, bold borders and full-width navigation |
| ivory | موج / Wave | Ice blue, horizontal game rows, pill controls and floating navigation |

All six identities are selectable in the appearance page. Existing saved IDs select their corresponding redesigned identity. The first-launch chooser previews each style before saving; later selections save immediately. Settings has a compact link to the full appearance gallery.

Shared components cover screen headers, game hubs, score cards and hand rows, fields, dialogs, controls, navigation, empty states, timers, game/role emblems and avatar frames. The existing player symbol IDs and photo files remain unchanged. Role cards preserve selected portraits, hidden/revealed state, role details and eliminated status. Paper and Volta use a typographic countdown; the other styles use their own circular countdown treatment. Timer bookkeeping and game/audio lifecycle are unchanged.

The launcher, loading surface and web icons use the new Divan mark. `tools/render_app_icons.py` regenerates the PNG icon assets with Pillow; it is not a dependency of the Android build.

Validation:
- `gradle --no-daemon testReleaseUnitTest assembleRelease` tests/builds all three Android flavors and verifies the official signing certificate in CI.
- Theme tests cover all six saved IDs, shape/layout distinctions, persistence and foreground contrast.
- Server regression and payment compatibility suites remain enabled.
- `python3 webapp/build-play.py` builds the shared screen implementation.
- `webapp/tests/visual-smoke.cjs` runs the production shared build in Chrome at 390 × 844, captures home/setup/board/score entry/settings in all six styles, exercises new-game navigation and reload, blocks external requests and checks for browser exceptions. Images and the report are CI artifacts. This browser review does not substitute for testing Android on a physical device.

Preserved features include selected-player filtering, Mafia subscription/trial rules, narration ducking and user audio, saved previous pantomime word, league editing, rewarded Pro/cooldown, notices, sponsorship contacts and direct Iranian hosting at divan-emtiaz.ir.
