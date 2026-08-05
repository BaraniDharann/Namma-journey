import crypto from 'crypto';
import { request, APIRequestContext } from '@playwright/test';
import { API_BASE, backendEnv } from './env';

/**
 * RateLimitingFilter buckets by X-Forwarded-For (falling back to remote addr) and allows
 * only 50 req/min overall and 10 req/min on /api/auth per bucket. A suite this size blows
 * through that in seconds, so every context gets its own synthetic client IP. Rate limiting
 * itself is covered deliberately in the security spec, using one pinned IP.
 */
let ipCounter = 0;
export function freshIp(label = 'e2e'): string {
  ipCounter += 1;
  const h = crypto.createHash('md5').update(label + ipCounter).digest();
  return `10.${h[0]}.${h[1]}.${(h[2] % 254) + 1}`;
}

const b64url = (b: Buffer | string) =>
  Buffer.from(b).toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');

/**
 * Mint a backend-valid JWT. Used only for the OWNER, whose password is not ours to reset —
 * the user and driver accounts under test authenticate through the real login endpoints.
 */
export function mintToken(subject: string, role: string): string {
  const expMs = parseInt(backendEnv.JWT_EXPIRATION || '86400000', 10);
  const now = Math.floor(Date.now() / 1000);
  const data =
    b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' })) +
    '.' +
    b64url(JSON.stringify({ role, userId: subject, sub: subject, iat: now, exp: now + Math.floor(expMs / 1000) }));
  const sig = crypto.createHmac('sha256', backendEnv.JWT_SECRET).update(data).digest();
  return `${data}.${b64url(sig)}`;
}

export async function apiContext(opts: { token?: string; ip?: string } = {}): Promise<APIRequestContext> {
  const headers: Record<string, string> = { 'X-Forwarded-For': opts.ip || freshIp() };
  if (opts.token) headers.Authorization = `Bearer ${opts.token}`;
  return request.newContext({ baseURL: API_BASE, extraHTTPHeaders: headers, ignoreHTTPSErrors: true });
}

/** Unique-per-run identifiers so reruns never collide on the uniqueness constraints. */
export function unique() {
  const stamp = Date.now().toString().slice(-9);
  const rand = crypto.randomInt(100, 999);
  return {
    stamp,
    userEmail: `e2e.user.${stamp}@njtest.local`,
    driverEmail: `e2e.driver.${stamp}@njtest.local`,
    // Booking validation requires ^[6-9]\d{9}$
    userMobile: `9${stamp}`,
    driverMobile: `8${stamp}`,
    license: `DLE2E${stamp}`,
    aadhaar: `${rand}${stamp}`.slice(0, 12),
  };
}

export async function jsonOf(res: { json: () => Promise<any>; text: () => Promise<string>; status: () => number }) {
  try {
    return await res.json();
  } catch {
    return { _raw: (await res.text()).slice(0, 400), _status: res.status() };
  }
}
