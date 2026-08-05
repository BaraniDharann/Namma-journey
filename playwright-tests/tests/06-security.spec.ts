import { test, expect } from '@playwright/test';
import { readAccounts } from '../helpers/env';
import { apiContext, mintToken, freshIp } from '../helpers/api';
import { contexts, disposeAll, bookingPayload, createStartedBooking, Ctxs } from '../helpers/booking';
import crypto from 'crypto';

const acc = readAccounts();
const U = acc.user.userId;
const D = acc.driver.driverId;

test.describe('Authorization and abuse controls', () => {
  let c: Ctxs;
  test.beforeEach(async ({}, testInfo) => {
    c = await contexts(`sec-${testInfo.title}`);
  });
  test.afterEach(async () => disposeAll(c));

  test('a user cannot read another user\'s bookings (IDOR)', async () => {
    const otherUserId = crypto.randomUUID();
    const res = await c.user.get(`/api/user/${otherUserId}/bookings`);
    expect(res.status(), 'path userId must be checked against the token subject').toBe(403);
  });

  test('a user cannot create a booking under another user id', async () => {
    const res = await c.user.post(`/api/user/${crypto.randomUUID()}/bookings`, { data: bookingPayload() });
    expect(res.status()).toBe(403);
  });

  test('roles cannot cross into each other\'s areas', async () => {
    const cases: Array<[string, any, string]> = [
      ['user -> owner', c.user, '/api/owner/bookings'],
      ['user -> driver', c.user, `/api/driver/${D}/bookings`],
      ['driver -> owner', c.driver, '/api/owner/drivers'],
      ['driver -> user', c.driver, `/api/user/${U}/bookings`],
      ['owner -> user', c.owner, `/api/user/${U}/bookings`],
      ['owner -> driver', c.owner, `/api/driver/${D}/bookings`],
    ];
    for (const [label, ctx, path] of cases) {
      const res = await ctx.get(path);
      expect([401, 403], `${label} ${path} -> ${res.status()}`).toContain(res.status());
    }
  });

  test('a driver cannot act on another driver\'s id', async () => {
    const otherDriverId = Number(D) + 1000;
    const res = await c.driver.get(`/api/driver/${otherDriverId}/bookings`);
    expect(res.status()).toBe(403);
  });

  test('a driver cannot publish a location under another driver id', async () => {
    const booking = await createStartedBooking(c);
    const res = await c.driver.post('/api/driver/location/update', {
      data: {
        driverId: Number(D) + 500, // impersonation attempt
        bookingId: booking.bookingId,
        latitude: 11.1,
        longitude: 77.1,
        heading: 0,
        timestamp: Date.now(),
      },
    });
    expect(res.status(), 'DTO driverId must be checked against the JWT subject').toBe(403);
  });

  test('a non-driver cannot publish a location at all', async () => {
    const res = await c.user.post('/api/driver/location/update', {
      data: { driverId: Number(D), bookingId: 'anything', latitude: 1, longitude: 1, heading: 0, timestamp: Date.now() },
    });
    expect([401, 403]).toContain(res.status());
  });

  test('a stranger cannot read the live location of someone else\'s booking', async () => {
    const booking = await createStartedBooking(c);
    await c.driver.post('/api/driver/location/update', {
      data: { driverId: Number(D), bookingId: booking.bookingId, latitude: 11.2, longitude: 77.2, heading: 0, timestamp: Date.now() },
    });

    // The rightful user can read it.
    const mine = await c.user.get(`/api/driver/location/${booking.bookingId}`);
    expect(mine.status()).toBe(200);

    // An unrelated user cannot.
    const stranger = await apiContext({ token: mintToken(crypto.randomUUID(), 'ROLE_USER'), ip: freshIp('stranger') });
    const theirs = await stranger.get(`/api/driver/location/${booking.bookingId}`);
    expect([401, 403]).toContain(theirs.status());
    await stranger.dispose();
  });

  test('notification recipient id cannot be spoofed', async () => {
    const res = await c.user.get(`/api/notifications?recipientId=${crypto.randomUUID()}&role=ROLE_USER`);
    expect([401, 403]).toContain(res.status());

    const asOwner = await c.user.get('/api/notifications?recipientId=owner&role=ROLE_OWNER');
    expect([401, 403]).toContain(asOwner.status());
  });

  test('a tampered JWT signature is rejected', async () => {
    const good = acc.user.token;
    const tampered = good.slice(0, -4) + (good.slice(-4) === 'AAAA' ? 'BBBB' : 'AAAA');
    const api = await apiContext({ token: tampered, ip: freshIp('tampered') });
    const res = await api.get(`/api/user/${U}/bookings`);
    expect([401, 403]).toContain(res.status());
    await api.dispose();
  });

  test('an expired JWT is rejected', async () => {
    const b64url = (b: string) => Buffer.from(b).toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
    const past = Math.floor(Date.now() / 1000) - 7200;
    const data =
      b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' })) +
      '.' +
      b64url(JSON.stringify({ role: 'ROLE_USER', userId: U, sub: U, iat: past - 60, exp: past }));
    const { backendEnv } = await import('../helpers/env');
    const sig = crypto.createHmac('sha256', backendEnv.JWT_SECRET).update(data).digest();
    const expired = `${data}.${sig.toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')}`;

    const api = await apiContext({ token: expired, ip: freshIp('expired') });
    const res = await api.get(`/api/user/${U}/bookings`);
    expect([401, 403]).toContain(res.status());
    await api.dispose();
  });

  test('the auth rate limiter returns 429 after 10 requests a minute', async () => {
    const pinned = '203.0.113.77'; // one dedicated bucket, so this cannot affect other specs
    const api = await apiContext({ ip: pinned });

    let limited = false;
    let sent = 0;
    for (let i = 0; i < 16; i++) {
      const res = await api.post('/api/auth/user/login', {
        data: { loginType: 'EMAIL', email: 'ratelimit@njtest.local', password: 'x' },
      });
      sent++;
      if (res.status() === 429) {
        limited = true;
        break;
      }
    }
    expect(limited, `no 429 after ${sent} auth requests from one IP`).toBe(true);
    expect(sent, 'limit should trip at or before the 11th request').toBeLessThanOrEqual(12);
    await api.dispose();
  });

  test('the general rate limiter protects anonymous non-auth endpoints', async () => {
    const pinned = '203.0.113.88';
    const api = await apiContext({ ip: pinned }); // no token: budgeted per IP

    let limited = false;
    let sent = 0;
    for (let i = 0; i < 140; i++) {
      const res = await api.get('/api/public/pricing');
      sent++;
      if (res.status() === 429) {
        limited = true;
        break;
      }
    }
    expect(limited, `no 429 within ${sent} anonymous requests from one IP`).toBe(true);
    await api.dispose();
  });

  /**
   * Regression guard. Signed-in traffic used to share the anonymous per-IP budget, and a single
   * owner dashboard render costs roughly thirty calls — so browsing a few pages inside a minute
   * returned 429 to a legitimate user, and everyone behind one NAT shared the same allowance.
   * Authenticated requests are now budgeted per account instead.
   */
  test('a signed-in caller is budgeted per account, not per IP', async () => {
    const pinned = '203.0.113.99';
    const api = await apiContext({ token: acc.user.token, ip: pinned });

    let limited = false;
    for (let i = 0; i < 60; i++) {
      const res = await api.get('/api/public/pricing');
      if (res.status() === 429) {
        limited = true;
        break;
      }
    }
    expect(limited, 'a signed-in caller was throttled at the anonymous per-IP budget').toBe(false);
    await api.dispose();
  });
});
