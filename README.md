# AndroidMaster — Personal Android Control

A native Android companion, web dashboard, and WebSocket relay for controlling Android devices you own.

## Current implemented behavior

- Server-verified owner-password login for the web dashboard. Configure `ADMIN_PASSWORD` and a strong random `AUTH_SECRET` in the web host environment; never commit either value to source.
- The browser stores a long-lived random device enrollment code locally and reuses it after page refresh. Keep this code private.
- The relay allows a controller to reconnect after an ordinary browser reload without intentionally disconnecting the Android socket.
- Android stores the relay URL and device enrollment code locally, reconnects with backoff, and uses a foreground service with an ongoing notification while connected.
- Android reports real model, Android version, battery/charging status, lists launchable installed apps, and accepts app-launch requests.
- Web and Android both have explicit disconnect controls.
- The Android companion UI supports vertical scrolling and asks for notification permission for its ongoing connection notification.

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

This is not yet a complete AirDroid replacement. Screen mirroring, remote touch/swipe, file transfer, notification reading, SMS/contacts, camera preview, persistent server-side device registry, and robust multi-device management have not yet been implemented or verified end-to-end. The current relay's live room mappings are in memory and are lost on relay restart. Android force-stop, phone shutdown, network outages, battery restrictions, or OS policy can interrupt background operation even with a foreground service.

Android will only show permission prompts for permissions actually requested by implemented features. Screen capture requires Android's MediaProjection consent; remote touch typically requires a separately enabled AccessibilityService. These cannot be silently approved.

## Local development

Relay: `cd services/relay && npm install && npm start`

Web: `cd apps/web && npm install && cp .env.example .env.local && npm run dev`

Android: open `apps/android` in Android Studio, sync Gradle, install the companion on a device you own, then enroll it using the dashboard's device code.
