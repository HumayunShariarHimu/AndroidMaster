# AndroidMaster — Personal Android Control

A native Android companion, web dashboard, and WebSocket relay for controlling Android devices you own.

## Current implemented behavior

- Server-verified owner-password login for the web dashboard. Configure `ADMIN_PASSWORD` and a strong random `AUTH_SECRET` in the web host environment; never commit either value to source.
- The browser keeps a local device registry with a separate long-lived enrollment code per phone. Codes remain in that browser's local storage and must be enrolled on each Android companion.
- The browser and Android companion use reconnect backoff; Android also listens for network availability. After ordinary network loss or relay restart, both ends retry the same room code. Android's foreground connection service helps keep the socket alive when the activity is closed.
- Android reports real device model, Android version, battery/charging status and installed launchable apps. Remote app launch, Back/Home/Recents/notification-shade actions and swipe gestures require the user to enable AndroidMaster's Accessibility service.
- Screen sharing uses Android MediaProjection. The device owner must approve the system capture prompt; a visible foreground-service notification remains while sharing.
- File manager lists files/folders inside a user-selected Storage Access Framework folder, navigates subfolders, downloads files, uploads files in chunks (up to 200 MB), and requests deletion. The user grants access to the chosen folder on the phone.
- Notification reading uses an Android NotificationListenerService and requires the user to enable AndroidMaster under Android Notification access settings.
- Camera preview uses Camera2 and a foreground service. Camera permission must be granted and preview started from the Android app while it is visible; Android restricts background camera starts.
- Web and Android both have explicit disconnect controls. A dashboard disconnect is propagated to Android and clears the saved enrollment so it does not silently reconnect.
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

This implementation is substantially expanded but is not yet a complete AirDroid replacement. SMS/contacts, remote clipboard, arbitrary settings toggles, device-wide app permissions, full desktop-grade file operations, persistent server-side device history, and full multi-device end-to-end validation remain outstanding. The device registry currently lives in the browser's local storage; the relay's live socket-room mappings are in memory. Both clients retry their saved enrollment code after network or relay recovery, but this recovery still needs real-phone testing against the deployed Render relay.

Android force-stop, phone shutdown, reboot, battery restrictions, captive portals, OS background-start rules, or a device without internet can interrupt background operation. Android requires explicit owner consent for screen capture, Accessibility, Notification access, camera and shared-folder permissions; an app cannot silently grant these. Camera preview must be started from the visible Android app because modern Android restricts background camera starts.

The CI build proves that source compiles and protocol tests pass; it does not replace testing on a real phone, confirming Render relay health, or verifying each permission and transfer end-to-end.

## Local development

Relay: `cd services/relay && npm install && npm start`

Web: `cd apps/web && npm install && cp .env.example .env.local && npm run dev`

Android: open `apps/android` in Android Studio, sync Gradle, install the companion on a device you own, then enroll it using the dashboard's device code.
