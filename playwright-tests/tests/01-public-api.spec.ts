import { test, expect, APIRequestContext } from '@playwright/test';
import { apiContext, freshIp } from '../helpers/api';

test.describe('Public / unauthenticated API', () => {
  let api: APIRequestContext;

  test.beforeAll(async () => {
    api = await apiContext({ ip: freshIp('public') });
  });
  test.afterAll(async () => api.dispose());

  test('health endpoints report UP', async () => {
    for (const path of ['/api/health', '/api/v1/health']) {
      const res = await api.get(path);
      expect(res.status(), path).toBe(200);
      const body = await res.json();
      expect(body.status, path).toBe('UP');
      expect(body.version, path).toBeTruthy();
    }
  });

  test('actuator liveness probe reports the app is up', async () => {
    const live = await api.get('/actuator/health/liveness');
    expect(live.status()).toBe(200);
    expect((await live.json()).status).toBe('UP');

    // Overall health aggregates optional infrastructure (Redis) that may not be running
    // locally; record it rather than fail the app for it.
    const overall = await api.get('/actuator/health');
    test.info().annotations.push({
      type: 'actuator /health',
      description: `${overall.status()} ${(await overall.text()).slice(0, 120)}`,
    });
  });

  test('public pricing returns configured rates', async () => {
    const res = await api.get('/api/public/pricing');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty('pricePerKm');
    expect(Number(body.pricePerKm)).toBeGreaterThan(0);
  });

  test('public reviews returns a list', async () => {
    const res = await api.get('/api/public/reviews');
    expect(res.status()).toBe(200);
    expect(Array.isArray(await res.json())).toBe(true);
  });

  test('public packages list and detail', async () => {
    const res = await api.get('/api/public/packages');
    expect(res.status()).toBe(200);
    const list = await res.json();
    expect(Array.isArray(list)).toBe(true);

    if (list.length) {
      const detail = await api.get(`/api/public/packages/${list[0].id}`);
      expect(detail.status()).toBe(200);
      expect((await detail.json()).id).toBe(list[0].id);
    } else {
      test.info().annotations.push({ type: 'note', description: 'no packages seeded — detail check skipped' });
    }
  });

  test('place search validates query length and returns results', async () => {
    const short = await api.get('/api/places/search?q=a');
    expect(short.status(), 'q shorter than 2 chars must be rejected').toBe(400);

    const ok = await api.get('/api/places/search?q=Coimbatore');
    expect(ok.status()).toBe(200);
    expect(Array.isArray(await ok.json())).toBe(true);
  });

  test('route preview is public and returns a distance', async () => {
    const res = await api.get('/api/user/route-preview?fromLat=11.0168&fromLon=76.9558&toLat=13.0827&toLon=80.2707');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toBeTruthy();
    expect(JSON.stringify(body)).toMatch(/distance/i);
  });

  test('protected endpoints reject anonymous callers', async () => {
    const cases: Array<[string, string]> = [
      ['GET', '/api/owner/bookings'],
      ['GET', '/api/owner/drivers'],
      ['GET', '/api/notifications?recipientId=1&role=ROLE_USER'],
      ['GET', '/api/driver/1/bookings'],
    ];
    for (const [method, path] of cases) {
      const res = method === 'GET' ? await api.get(path) : await api.post(path);
      expect([401, 403], `${method} ${path} -> ${res.status()}`).toContain(res.status());
    }
  });

  test('a garbage bearer token is rejected', async () => {
    const bad = await apiContext({ token: 'not.a.real.jwt', ip: freshIp('badtoken') });
    const res = await bad.get('/api/owner/bookings');
    expect([401, 403]).toContain(res.status());
    await bad.dispose();
  });
});
