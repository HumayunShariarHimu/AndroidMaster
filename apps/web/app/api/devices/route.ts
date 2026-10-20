import { get, put } from "@vercel/blob";
import { NextRequest, NextResponse } from "next/server";
import { isAuthenticated } from "../../lib/session";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const REGISTRY_PATH = "androidmaster/registry/devices.json";
const NO_STORE = { "Cache-Control": "no-store" };

type SavedDevice = { id: string; name: string; code: string };

function validateDevices(value: unknown): SavedDevice[] | null {
  if (!Array.isArray(value) || value.length > 50) return null;
  const ids = new Set<string>();
  const codes = new Set<string>();
  const devices: SavedDevice[] = [];
  for (const item of value) {
    if (!item || typeof item !== "object") return null;
    const candidate = item as Record<string, unknown>;
    if (typeof candidate.id !== "string" || !/^[0-9a-f-]{36}$/i.test(candidate.id)) return null;
    if (typeof candidate.name !== "string" || !candidate.name.trim() || candidate.name.trim().length > 80) return null;
    if (typeof candidate.code !== "string" || !/^[a-f0-9]{48}$/i.test(candidate.code)) return null;
    if (ids.has(candidate.id) || codes.has(candidate.code.toLowerCase())) return null;
    ids.add(candidate.id);
    codes.add(candidate.code.toLowerCase());
    devices.push({ id: candidate.id, name: candidate.name.trim(), code: candidate.code.toLowerCase() });
  }
  return devices;
}

async function readRegistry(): Promise<SavedDevice[]> {
  const blob = await get(REGISTRY_PATH, { access: "private" });
  if (!blob || blob.statusCode !== 200) return [];
  const raw = await new Response(blob.stream).text();
  const parsed = JSON.parse(raw) as { version?: unknown; devices?: unknown };
  if (parsed.version !== 1) throw new Error("Unsupported device registry version");
  const devices = validateDevices(parsed.devices);
  if (!devices) throw new Error("Stored device registry is invalid");
  return devices;
}

export async function GET(request: NextRequest) {
  if (!isAuthenticated(request)) return NextResponse.json({ error: "Authentication required." }, { status: 401, headers: NO_STORE });
  if (!process.env.BLOB_READ_WRITE_TOKEN) return NextResponse.json({ error: "Persistent device storage is not configured." }, { status: 503, headers: NO_STORE });
  try {
    const devices = await readRegistry();
    return NextResponse.json({ devices, persistent: true }, { headers: NO_STORE });
  } catch {
    return NextResponse.json({ error: "Unable to load the saved device registry." }, { status: 503, headers: NO_STORE });
  }
}

export async function PUT(request: NextRequest) {
  if (!isAuthenticated(request)) return NextResponse.json({ error: "Authentication required." }, { status: 401, headers: NO_STORE });
  if (!process.env.BLOB_READ_WRITE_TOKEN) return NextResponse.json({ error: "Persistent device storage is not configured." }, { status: 503, headers: NO_STORE });
  let input: unknown;
  try { input = await request.json(); } catch { return NextResponse.json({ error: "Invalid JSON body." }, { status: 400, headers: NO_STORE }); }
  const devices = validateDevices((input as { devices?: unknown } | null)?.devices);
  if (!devices) return NextResponse.json({ error: "Invalid device registry. Use at most 50 devices with unique IDs and pairing codes." }, { status: 400, headers: NO_STORE });
  try {
    await put(REGISTRY_PATH, JSON.stringify({ version: 1, updatedAt: new Date().toISOString(), devices }), {
      access: "private",
      addRandomSuffix: false,
      allowOverwrite: true,
      contentType: "application/json",
      cacheControlMaxAge: 0,
    });
    return NextResponse.json({ devices, persistent: true }, { headers: NO_STORE });
  } catch {
    return NextResponse.json({ error: "Unable to save the device registry." }, { status: 503, headers: NO_STORE });
  }
}
