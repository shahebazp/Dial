# DIAL — QA pass, November 19, 2026

## Fixed
- **Remind Me did nothing.** It only declined the call and showed a toast; no alarm or notification existed. New
  `CallbackReminderManager` schedules an `AlarmManager` alarm (`setAndAllowWhileIdle`, no exact-alarm permission),
  stores pending reminders, restores them after reboot (`CallbackReminderBootReceiver`) and posts a
  "Call back <name>" notification with **Call back** / **Dismiss** actions. If notifications are blocked the user is told
  and the POST_NOTIFICATIONS prompt is shown. Unknown/private numbers are reported instead of silently "succeeding".
- **Call screens ignored Light/Dark/OLED themes.** Outgoing, connected, DTMF keypad and incoming-without-photo screens
  used hard-coded dark colours while the avatar and bottom sheets followed the theme. They now use `CallPalette`,
  derived from the active colour scheme. Incoming calls with a contact photo keep the dark scrim (white text) for readability.
- **Status/navigation bar icons** followed the phone's dark mode, not DIAL's theme (white icons on a white screen).
  `DialTheme` now syncs them and the window background.
- **`tel:` links while DIAL is open were ignored** (only the launch intent was read). Now handled via `onNewIntent`;
  the launch intent is no longer re-applied after rotation (it used to overwrite the typed number).
- Outgoing screen always said "VoLTE" regardless of the real network; label removed.
- `android:allowBackup` was `true` although the README promises `false`; set to `false`.
- Duplicate `testTag` calls (second silently replaced the first) on the answer/decline buttons and slide track.

## Known issues / recommendations (not changed)
- `fallbackToDestructiveMigration(dropAllTables = true)` wipes blocked numbers/speed dials on any schema bump; add migrations.
- Quick-reply SMS: when SEND_SMS is not yet granted the permission prompt and the fallback Messaging-app intent fire together.
- Emergency-number list is hard-coded (India/US/UK); use `TelecomManager.isEmergencyNumber` (+ locale-aware lists).
- `google-services.json` API key is committed; restrict it in Google Cloud (package + SHA-1) and use App Check enforcement.
- `NothingKey` meta-data is `"test"`; a real Glyph key is needed for production.
- No automated test covers call screens, reminders or theming; add Compose tests for the incoming/connected screens in each theme.

