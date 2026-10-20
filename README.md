# AndroidMaster — Personal Android Control

A native Android companion, web dashboard, and WebSocket relay for controlling Android devices you own.

## Current implemented behavior

- Server-verified owner-password login for the web dashboard. Configure `ADMIN_PASSWORD` and a strong random `AUTH_SECRET` in the web host environment; never commit either value to source.
- The web dashboard stores up to 50 enrolled device records in a private Vercel Blob registry behind the owner-authenticated `/api/devices` route. Browser local storage is used only as a migration/cache aid; the server registry is the source of truth. Each phone has a separate 192-bit random pairing code.
- The browser and Android companion use reconnect backoff; Android also listens for network availability. After ordinary network loss or relay restart, both ends retry the same room code. The Android foreground connection service helps keep the socket alive when the activity is closed. A boot receiver attempts to restart the saved connection after `BOOT_COMPLETED` and after an app update when Android permits background service startup.
- Android reports real device model, Android version, battery/charging status and installed launchable apps. Remote app launch, live-screen tap, Back/Home/Recents/notification-shade actions and swipe gestures require the user to enable AndroidMaster's Accessibility service.
- Screen sharing uses Android MediaProjection. The device owner must approve the system capture prompt; a visible foreground-service notification remains while sharing.
- File manager lists files/folders inside a user-selected Storage Access Framework folder, navigates subfolders, creates folders, renames and deletes items, downloads files, and uploads files in chunks (up to 200 MB) into the currently open folder. The user grants access to the chosen folder on the phone.
- Notification reading uses an Android NotificationListenerService and requires the user to enable AndroidMaster under Android Notification access settings. The dashboard can request a snapshot, receive updates, and dismiss an individual notification.
- The Contacts panel can request a snapshot of up to 250 unique phone numbers after the device owner grants the Android Contacts runtime permission. It does not silently read contacts or bypass permission prompts.
- The Clipboard panel can send up to 10,000 characters to the phone clipboard. Reading the phone clipboard remotely is intentionally not implemented because Android restricts background clipboard access.
- Camera preview uses Camera2 and a foreground service. Camera permission must be granted and preview started from the Android app while it is visible; Android restricts background camera starts.
- The dashboard can detach without deleting the phone's saved pairing: the Android companion stays connected to the relay and can be controlled again when the owner reconnects with the same pairing secret. The owner can still explicitly disconnect on the phone to stop background reconnection and clear the saved enrollment.
- CI builds the web dashboard and Android APK and runs relay protocol integration tests.

## Layout

- `apps/web` — Next.js dashboard and server-side login API
- `apps/android` — Kotlin + Jetpack Compose companion
- `services/relay` — Node.js WebSocket relay

## Deployment configuration

Web environment variables:
- `NEXT_PUBLIC_RELAY_URL=wss://<your-relay-host>`
- `ADMIN_PASSWORD` — owner password; use a strong password for production.
- `AUTH_SECRET` — cryptographically random secret used to sign the HttpOnly authentication cookie.

The relay is deployed separately from the Vercel web UI. Set the host's `PORT` and use TLS/WSS in production. The relay must remain running for remote control.

## Important limitations

This implementation is not a complete AirDroid replacement. SMS, arbitrary system-setting toggles, granting device-wide app permissions, full desktop-grade file operations, clipboard reading/synchronization from phone to dashboard, and full multi-device end-to-end validation remain outstanding. The persistent device registry is stored in a private Vercel Blob object behind the authenticated API; the relay live socket-room mappings are in memory and are rebuilt when the phone and dashboard reconnect. Boot recovery and all device permissions still require real-phone testing against the deployed relay.

Android force-stop, phone shutdown, battery restrictions, captive portals, OS background-start rules, or a device without internet can interrupt background operation. The boot receiver attempts reconnect after reboot, but this needs real-device verification and some manufacturers' battery policies may block it. Android requires explicit owner consent for screen capture, Accessibility, Notification access, camera and shared-folder permissions; an app cannot silently grant these. Camera preview must be started from the visible Android app because modern Android restricts background camera starts.

The CI build proves that source compiles and protocol tests pass; it does not replace testing on a real phone, confirming Render relay health, or verifying each permission and transfer end-to-end.

## Release verification checklist

1. Confirm the relay health endpoint returns `ok: true` and the web dashboard loads over HTTPS.
2. Install the generated debug APK, enter the dashboard enrollment code, and verify the device returns online after Wi-Fi/mobile-data is toggled off and back on.
3. Enable Accessibility, start screen sharing from the phone, then test tap, swipe, Back/Home/Recents and app launch from the dashboard.
4. Select a shared folder and test subfolder navigation, upload, download and delete with small files first.
5. Enable Notification access and verify notification snapshot plus posted/removed updates.
6. Start camera preview from the visible phone app, verify frames reach the dashboard, then stop it.
7. Reboot the phone without force-stopping AndroidMaster and verify the saved relay connection returns after boot/network recovery.
8. Repeat with two enrolled phones and switch between their separate codes in the same browser.

These are manual device checks; do not mark them complete based on CI alone.

## Local development

Relay: `cd services/relay && npm install && npm start`

Web: `cd apps/web && npm install && cp .env.example .env.local && npm run dev`

Android: open `apps/android` in Android Studio, sync Gradle, install the companion on a device you own, then enroll it using the dashboard's device code.
