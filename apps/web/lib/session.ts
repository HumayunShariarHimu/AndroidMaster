import { createHmac, timingSafeEqual } from "node:crypto";
import type { NextRequest } from "next/server";

const COOKIE = "androidmaster_auth";

export function isAuthenticated(request: NextRequest): boolean {
  const token = request.cookies.get(COOKIE)?.value;
  const secret = process.env.AUTH_SECRET;
  if (!token || !secret) return false;
  const split = token.lastIndexOf(".");
  if (split < 1) return false;
  const body = token.slice(0, split);
  const signature = token.slice(split + 1);
  const expected = createHmac("sha256", secret).update(body).digest("base64url");
  const a = Buffer.from(signature);
  const b = Buffer.from(expected);
  if (a.length !== b.length || !timingSafeEqual(a, b)) return false;
  try {
    const data = JSON.parse(Buffer.from(body, "base64url").toString("utf8"));
    return data.exp > Math.floor(Date.now() / 1000) && data.v === 1;
  } catch {
    return false;
  }
}
