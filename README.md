# AndroidMaster — Personal Android Control

A native Android companion, web dashboard, and WebSocket relay for controlling a device you own.

## Real functionality in this initial implementation
- Next.js dashboard creates a cryptographically random 48-hex pairing secret.
- Android app connects to the relay, reports actual device/battery status, lists launchable installed apps, and launches a selected app.
- Node.js relay pairs one controller with one Android device and validates allowed message types and app package names.
- No user registration is required; the pairing secret is a temporary bearer credential.

## Layout
- apps/web — Vercel-ready Next.js dashboard
- apps/android — Kotlin + Jetpack Compose app
- services/relay — persistent WebSocket relay; deploy separately from the Vercel UI

## Run
Relay: `cd services/relay && npm install && npm start`
Web: `cd apps/web && npm install && cp .env.example .env.local && npm run dev`; set `NEXT_PUBLIC_RELAY_URL=ws://localhost:8080`.
Android: open `apps/android` in Android Studio, sync Gradle, run on your own phone, then enter the relay URL and pairing secret shown in the web dashboard.

## Scope and honest status
Pairing, device status, installed-app discovery, and app launch are implemented in source. Screen mirroring/remote touch, file transfer, notifications, SMS/contacts, camera, APK install workflow, and end-to-end remote-access deployment are not yet implemented; they must not be represented as working until built and tested on a real Android device. No mock device or fake app list is used.

Android permission prompts and screen-capture consent cannot be silently bypassed. Android 11+ package visibility is restricted; this sideload-oriented personal build declares QUERY_ALL_PACKAGES for its app-manager use case. Check policy eligibility before any public app-store release.

The relay stores rooms in memory and is single-instance only. Use TLS/WSS for public access, treat pairing secrets like passwords, and add shared coordination before horizontal scaling.
