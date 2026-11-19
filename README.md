# DIAL — Android dialer project

This source project contains the DIAL Android application. It is designed to be selected as the Android default phone app and uses Android Telecom for call control.

## Open and build

1. Install a recent Android Studio release with the Android SDK required by `app/build.gradle.kts` (compile SDK 36).
2. Open this project folder in Android Studio and allow Gradle sync to complete.
3. The uploaded archive does not include `gradlew`, the Gradle wrapper JAR, or a `debug.keystore`. If Android Studio reports a missing `debug.keystore`, follow the existing build-file note and remove the `debug` build type's `signingConfig = signingConfigs.getByName("debugConfig")` assignment so Android Gradle Plugin can use its standard debug signing configuration. Do not remove the release signing configuration.
4. Create a local `.env` file only if you intend to use the configured Firebase/Gemini features. Never commit API keys.
5. Build the debug APK from Android Studio and install it on a test device.
6. Set DIAL as the default phone app when Android requests it, then test calls, contacts, recents, call routing, and voicemail import.

## Important platform limitations

- Native two-way cellular-call recording uses Android's protected `VOICE_CALL` audio source. A normal sideloaded app does not receive `CAPTURE_AUDIO_OUTPUT`; DIAL checks this and refuses to substitute microphone-on-speaker recording. Actual recording remains unavailable unless the device OEM grants the required system privilege.
- The system feature flag used by Google Dialer is not itself a permission grant.
- Voicemail audio imported by the user is copied into DIAL's private app storage. Automatic carrier Visual Voicemail sync is not implemented in this build.
- Gemini voicemail summarization requires an existing transcript. Audio-to-text transcription is not wired in this update.
- `android:allowBackup="false"` prevents automatic app-data backup. Export local audio before uninstalling the app or resetting the phone.

See [CHANGES.md](CHANGES.md) for this update's detailed implementation notes and known gaps.


## UX update — November 4, 2026

The product/UX brief bundled at `public/assets/aistudio/Paste_November_02__2026_-_1_42PM_3670eae2` was reviewed alongside this README and CHANGES history. This update preserves DIAL's primary purpose as a native Android dialer, its Nothing-inspired visual language, real Telecom-driven call state, lower-screen one-handed controls, and the rule against simulated call success.

Implemented in this iteration:
- Keypad bottom action area now contains one centred primary Call button. The adjacent Recents, Video Call and Contacts shortcuts have been removed; the app's main navigation remains unchanged.
- The connected-call Record control has been replaced with Video Call. DIAL reads each call's Telecom video capability flags and only sends an audio-to-video upgrade request when a video provider and bidirectional support for both endpoints are present. The carrier and remote endpoint may still reject the request. No camera permission is requested because this iteration does not implement local camera capture or video surfaces.
- Add Call now displays a searchable contacts-only picker. It searches contact names and their phone numbers, and does not offer manual number entry from that sheet.
- MainActivity no longer has permanent `showWhenLocked` or `turnScreenOn` flags. It temporarily applies those flags only while Telecom reports an incoming ringing call, and clears them when the call changes state or the composable is disposed.
- The Add Call contact rows have a larger minimum touch height.

Recording and voicemail implementation are intentionally paused in this iteration. The in-call recording control and active recording indicator are removed. Existing recording and voicemail screens/services remain in the source tree for later work; the voicemail implementation was not modified.

### UX documentation reviewed

- Android default phone application and Telecom requirements: https://developer.android.com/develop/connectivity/telecom/dialer-app
- Android `InCallService` contract: https://developer.android.com/reference/android/telecom/InCallService
- Compose accessibility and interactive target sizing: https://developer.android.com/develop/ui/compose/accessibility/api-defaults
- Android architecture recommendations: single source of truth, layered responsibilities, unidirectional data flow and lifecycle-aware state: https://developer.android.com/topic/architecture/recommendations
- Android activity/keyguard behavior: https://developer.android.com/reference/android/app/Activity#setShowWhenLocked(boolean)
- Android Telecom video session upgrade API: https://developer.android.com/reference/android/telecom/InCallService.VideoCall#sendSessionModifyRequest(android.telecom.VideoProfile)

This project already follows several of these recommendations through a single Activity, Compose UI, ViewModel state, StateFlow and Telecom callbacks. The update focuses on the requested interactions rather than an unnecessary redesign.

### Video calling limitation
The Video Call action sends an Android Telecom session-modification request only when the current call exposes bidirectional video capabilities and a video provider. This is only an upgrade request, not proof that a video call has started. The mobile network, SIM, carrier service and remote participant must support and accept it. This iteration does not yet render local/remote video surfaces or implement full camera controls; do not treat the action as a complete video-calling feature until separately implemented and tested.
