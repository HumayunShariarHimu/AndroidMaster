import { createHmac, timingSafeEqual } from "node:crypto";
import { NextRequest, NextResponse } from "next/server";

const COOKIE = "androidmaster_auth";
const TTL_SECONDS = 60 * 60 * 24 * 30;
const attempts = new Map<string, { count: number; until: number }>();

function sign(value: string) {
  return createHmac("sha256", process.env.AUTH_SECRET ?? "").update(value).digest("base64url");
}
function validToken(token: string | undefined) {
  if (!token || !process.env.AUTH_SECRET) return false;
  const split = token.lastIndexOf(".");
  if (split < 1) return false;
  const body = token.slice(0, split);
  const signature = token.slice(split + 1);
  const expected = sign(body);
  const a = Buffer.from(signature);
  const b = Buffer.from(expected);
  if (a.length !== b.length || !timingSafeEqual(a, b)) return false;
  try {
    const data = JSON.parse(Buffer.from(body, "base64url").toString("utf8"));
    return data.exp > Math.floor(Date.now() / 1000) && data.v === 1;
  } catch { return false; }
}
function cookieOptions() {
  return { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "strict" as const, path: "/", maxAge: TTL_SECONDS };
}
export async function GET(request: NextRequest) {
  return NextResponse.json({ authenticated: validToken(request.cookies.get(COOKIE)?.value) }, { headers: { "Cache-Control": "no-store" } });
}
export async function POST(request: NextRequest) {
  const password = process.env.ADMIN_PASSWORD;
  const secret = process.env.AUTH_SECRET;
  if (!password || !secret) return NextResponse.json({ error: "Authentication is not configured on the server." }, { status: 503 });
  const ip = request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "unknown";
  const now = Date.now();
  const entry = attempts.get(ip);
  if (entry && entry.until > now && entry.count >= 8) return NextResponse.json({ error: "Too many attempts. Try again later." }, { status: 429 });
  let input = "";
  try { input = String((await request.json()).password ?? ""); } catch { return NextResponse.json({ error: "Invalid request." }, { status: 400 }); }
  const a = Buffer.from(input);
  const b = Buffer.from(password);
  const matches = a.length === b.length && timingSafeEqual(a, b);
  if (!matches) {
    const current = entry && entry.until > now ? entry : { count: 0, until: now + 15 * 60 * 1000 };
    current.count++;
    attempts.set(ip, current);
    return NextResponse.json({ error: "Incorrect password." }, { status: 401 });
  }
  attempts.delete(ip);
  const body = Buffer.from(JSON.stringify({ v: 1, exp: Math.floor(now / 1000) + TTL_SECONDS })).toString("base64url");
  const response = NextResponse.json({ authenticated: true });
  response.cookies.set(COOKIE, body + "." + sign(body), cookieOptions());
  response.headers.set("Cache-Control", "no-store");
  return response;
}
export async function DELETE(request: NextRequest) {
  const response = NextResponse.json({ authenticated: false });
  response.cookies.set(COOKIE, "", { ...cookieOptions(), maxAge: 0 });
  response.headers.set("Cache-Control", "no-store");
  return response;
}
