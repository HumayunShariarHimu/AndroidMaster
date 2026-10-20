const test = require("node:test");
const assert = require("node:assert/strict");
const { spawn } = require("node:child_process");
const net = require("node:net");
const path = require("node:path");
const { WebSocket } = require("ws");

async function freePort() {
  const server = net.createServer();
  await new Promise((resolve, reject) => server.listen(0, "127.0.0.1", resolve).once("error", reject));
  const port = server.address().port;
  await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  return port;
}

function waitForMessage(socket, predicate, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      socket.off("message", onMessage);
      reject(new Error("Timed out waiting for relay message"));
    }, timeoutMs);
    const onMessage = raw => {
      let message;
      try { message = JSON.parse(raw.toString()); } catch { return; }
      if (!predicate(message)) return;
      clearTimeout(timer);
      socket.off("message", onMessage);
      resolve(message);
    };
    socket.on("message", onMessage);
  });
}

function waitForOpen(socket) {
  if (socket.readyState === WebSocket.OPEN) return Promise.resolve();
  return new Promise((resolve, reject) => {
    socket.once("open", resolve);
    socket.once("error", reject);
  });
}

async function waitForHealth(port, child) {
  const deadline = Date.now() + 7000;
  while (Date.now() < deadline) {
    if (child.exitCode !== null) throw new Error("Relay exited before becoming healthy");
    try {
      const response = await fetch(`http://127.0.0.1:${port}/healthz`);
      if (response.ok) return;
    } catch {}
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error("Relay health endpoint did not become ready");
}

test("dashboard disconnect preserves the paired device for reconnect", async t => {
  const port = await freePort();
  const relay = spawn(process.execPath, ["src/index.js"], {
    cwd: path.resolve(__dirname, ".."),
    env: { ...process.env, PORT: String(port) },
    stdio: "ignore"
  });
  const sockets = [];
  t.after(async () => {
    for (const socket of sockets) {
      if (socket.readyState !== WebSocket.CLOSED) socket.terminate();
    }
    relay.kill("SIGTERM");
    await new Promise(resolve => {
      if (relay.exitCode !== null) return resolve();
      relay.once("exit", resolve);
      setTimeout(resolve, 1500);
    });
  });

  await waitForHealth(port, relay);
  const room = "a".repeat(48);
  const device = new WebSocket(`ws://127.0.0.1:${port}/ws?role=device&room=${room}`);
  sockets.push(device);
  const deviceReady = waitForMessage(device, m => m.type === "relay.ready");
  await waitForOpen(device);
  await deviceReady;

  const controller = new WebSocket(`ws://127.0.0.1:${port}/ws?role=controller&room=${room}`);
  sockets.push(controller);
  const controllerReady = waitForMessage(controller, m => m.type === "relay.ready");
  const deviceConnected = waitForMessage(controller, m => m.type === "device.connected");
  const phoneConnected = waitForMessage(device, m => m.type === "controller.connected");
  await waitForOpen(controller);
  await controllerReady;
  await deviceConnected;
  await phoneConnected;

  const phoneDetached = waitForMessage(device, m => m.type === "controller.disconnected");
  const controllerClosed = new Promise(resolve => controller.once("close", resolve));
  controller.send(JSON.stringify({ type: "controller.disconnect", payload: {} }));
  await Promise.all([phoneDetached, controllerClosed]);
  assert.equal(device.readyState, WebSocket.OPEN, "the phone socket should stay connected");

  const reconnected = new WebSocket(`ws://127.0.0.1:${port}/ws?role=controller&room=${room}`);
  sockets.push(reconnected);
  const reconnectedReady = waitForMessage(reconnected, m => m.type === "relay.ready");
  const reconnectedDevice = waitForMessage(reconnected, m => m.type === "device.connected");
  const phoneRepaired = waitForMessage(device, m => m.type === "controller.connected");
  await waitForOpen(reconnected);
  await reconnectedReady;
  await reconnectedDevice;
  await phoneRepaired;

  const command = waitForMessage(device, m => m.type === "device.status.get");
  reconnected.send(JSON.stringify({ type: "device.status.get", payload: {} }));
  assert.equal((await command).type, "device.status.get");
});
