# AndroidMaster Relay

Deploy as a persistent Node.js service (for example Render), separate from the Vercel web UI.

- Health: `GET /healthz`
- WebSocket: `/ws?role=controller&room=<48-hex-device-code>` or `/ws?role=device&room=<same-device-code>`
- The device code is a long-lived enrollment credential. Generate it with a cryptographically secure random generator; never publish or log it.
- The controller can reconnect after browser refresh and automatically retries after socket/network failures. Android retries with exponential backoff and also reacts to default-network availability.
- An unexpected controller disconnect leaves the Android socket alive. An explicit `controller.disconnect` is sent to Android before both sockets close; Android clears saved enrollment and stops its foreground connection service so it does not silently reconnect.
- The controller/device protocol forwards app launch, remote navigation gestures, screen frames, camera frames, notification snapshots, file listing, chunked file upload/download, and file deletion requests.
- Screen and camera frames use bounded JSON/base64 messages; the relay's maximum WebSocket message size is 1 MiB. File transfers are chunked below that limit.
- Live room mappings remain in process memory. After a relay restart, the Android service and dashboard must reconnect using their saved enrollment code to recreate the room. There is no persistent server-side device history or cross-instance coordination; deploy a single relay instance unless shared state is added.
- Set `PORT` from the host. Use TLS termination and `wss://` for public access. Single-instance only; do not horizontally scale without shared coordination/storage.
