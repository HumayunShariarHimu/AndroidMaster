# AndroidMaster Relay

Deploy as a persistent Node.js service (for example Railway), separate from the Vercel web UI.

- Health: GET /healthz
- WebSocket: /ws?role=controller&room=<48-hex-secret> or /ws?role=device&room=<same-secret>
- Controller opens first; Android joins using the secret copied from the dashboard.
- One controller and one device per room; in-memory room state; 64 KiB max message; 12-hour room lifetime.
- Set PORT from the host. Use TLS termination and wss:// for internet access. Never log pairing secrets.
- Single-instance design; do not horizontally scale without shared coordination/storage.
