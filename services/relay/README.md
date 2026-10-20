# AndroidMaster Relay

Deploy as a persistent Node.js service (for example Render), separate from the Vercel web UI.

- Health: `GET /healthz`
- WebSocket: `/ws?role=controller&room=<48-hex-device-code>` or `/ws?role=device&room=<same-device-code>`
- The device code is a long-lived enrollment credential. Generate it with a cryptographically secure random generator; never publish or log it.
- The controller may reconnect with the same code after a browser refresh. An unexpected controller disconnect leaves the Android socket alive so the dashboard can reattach.
- An explicit `controller.disconnect` closes both ends. Android's own Disconnect button clears the saved device code and stops its reconnect loop.
- Android stores the relay URL and device code locally and reconnects with backoff. A foreground service helps keep the socket alive while the app UI is closed; Android force-stop, device shutdown, network loss, battery restrictions, or a relay restart can still interrupt service.
- The relay currently keeps live socket-room mappings in process memory. Reconnect works across browser reloads while this relay process stays up, but server restarts erase active room mappings; durable multi-device registry/storage is not implemented yet.
- Set `PORT` from the host. Use TLS termination and `wss://` for public access. Single-instance only; do not horizontally scale without shared coordination/storage.
