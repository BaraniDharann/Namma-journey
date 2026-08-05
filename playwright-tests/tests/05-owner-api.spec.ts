import { test, expect } from '@playwright/test';
import { readAccounts, OWNER_ID } from '../helpers/env';
import { contexts, disposeAll, Ctxs, futureDate } from '../helpers/booking';
import { unique } from '../helpers/api';

const acc = readAccounts();

test.describe('Owner API', () => {
  let c: Ctxs;
  test.beforeEach(async ({}, testInfo) => {
    c = await contexts(`owner-${testInfo.title}`);
  });
  test.afterEach(async () => disposeAll(c));

  test('lists all bookings', async () => {
    const res = await c.owner.get('/api/owner/bookings');
    expect(res.status()).toBe(200);
    expect(Array.isArray(await res.json())).toBe(true);
  });

  test('sets and reads pricing', async () => {
    const km = await c.owner.post(`/api/owner/pricing/set?pricePerKm=14&ownerId=${OWNER_ID}`);
    expect(km.status(), (await km.text()).slice(0, 200)).toBe(200);

    const hr = await c.owner.post(`/api/owner/pricing/set-hourly?pricePerHour=275&ownerId=${OWNER_ID}`);
    expect(hr.status()).toBe(200);

    const current = await c.owner.get('/api/owner/pricing/current');
    expect(current.status()).toBe(200);
    const p = await current.json();
    expect(Number(p.pricePerKm)).toBe(14);
    expect(Number(p.pricePerHour)).toBe(275);

    // Restore what global setup configured.
    await c.owner.post(`/api/owner/pricing/set?pricePerKm=12&ownerId=${OWNER_ID}`);
    await c.owner.post(`/api/owner/pricing/set-hourly?pricePerHour=250&ownerId=${OWNER_ID}`);
  });

  test('rejects non-positive pricing', async () => {
    for (const q of ['pricePerKm=0', 'pricePerKm=-5']) {
      const res = await c.owner.post(`/api/owner/pricing/set?${q}&ownerId=${OWNER_ID}`);
      expect(res.status(), `${q} -> ${res.status()}`).toBeGreaterThanOrEqual(400);
    }
    const hourly = await c.owner.post(`/api/owner/pricing/set-hourly?pricePerHour=-100&ownerId=${OWNER_ID}`);
    expect(hourly.status()).toBeGreaterThanOrEqual(400);
  });

  test('pricing/current is readable by user and driver too', async () => {
    for (const [role, ctx] of [['user', c.user], ['driver', c.driver]] as const) {
      const res = await ctx.get('/api/owner/pricing/current');
      expect(res.status(), `${role} -> ${res.status()}`).toBe(200);
    }
  });

  test('lists drivers and includes the newly created one', async () => {
    const res = await c.owner.get('/api/owner/drivers');
    expect(res.status()).toBe(200);
    const drivers = await res.json();
    expect(Array.isArray(drivers)).toBe(true);
    expect(drivers.map((d: any) => String(d.driverId))).toContain(acc.driver.driverId);

    const one = await c.owner.get(`/api/owner/drivers/${acc.driver.driverId}`);
    expect(one.status()).toBe(200);
    expect((await one.json()).email).toBe(acc.driver.email);
  });

  test('rejects duplicate driver details on creation', async () => {
    const ids = unique();
    const dupeMobile = await c.owner.post('/api/owner/drivers', {
      multipart: {
        name: 'Dupe Mobile',
        mobile: acc.driver.mobile, // already taken
        email: ids.driverEmail,
        licenseNumber: ids.license,
        aadhaarNumber: ids.aadhaar,
      },
    });
    expect(dupeMobile.status()).toBe(400);
    expect((await dupeMobile.text()).toLowerCase()).toContain('already registered');

    const ids2 = unique();
    const dupeEmail = await c.owner.post('/api/owner/drivers', {
      multipart: {
        name: 'Dupe Email',
        mobile: ids2.driverMobile,
        email: acc.driver.email, // already taken
        licenseNumber: ids2.license,
        aadhaarNumber: ids2.aadhaar,
      },
    });
    expect(dupeEmail.status()).toBe(400);
  });

  test('creates then deletes a throwaway driver', async () => {
    const ids = unique();
    const created = await c.owner.post('/api/owner/drivers', {
      multipart: {
        name: 'E2E Disposable Driver',
        mobile: ids.driverMobile,
        email: ids.driverEmail,
        licenseNumber: ids.license,
        aadhaarNumber: ids.aadhaar,
      },
    });
    expect(created.status(), (await created.text()).slice(0, 300)).toBe(201);
    const body = await created.json();
    expect(body.id).toBeTruthy();

    const deleted = await c.owner.delete(`/api/owner/drivers/${body.id}`);
    expect([200, 204]).toContain(deleted.status());

    const gone = await c.owner.get(`/api/owner/drivers/${body.id}`);
    expect([404, 400]).toContain(gone.status());
  });

  test('revenue endpoints answer for day, month and year', async () => {
    const today = new Date();
    const daily = await c.owner.get(`/api/owner/revenue/daily?date=${today.toISOString().slice(0, 10)}`);
    expect(daily.status(), (await daily.text()).slice(0, 200)).toBe(200);

    const monthly = await c.owner.get(
      `/api/owner/revenue/monthly?year=${today.getFullYear()}&month=${today.getMonth() + 1}`
    );
    expect(monthly.status()).toBe(200);

    const yearly = await c.owner.get(`/api/owner/revenue/yearly?year=${today.getFullYear()}`);
    expect(yearly.status()).toBe(200);
  });

  test('lists reviews and pending payments', async () => {
    const reviews = await c.owner.get('/api/owner/reviews');
    expect(reviews.status()).toBe(200);
    expect(Array.isArray(await reviews.json())).toBe(true);

    const pending = await c.owner.get('/api/owner/payments/pending');
    expect(pending.status()).toBe(200);
    expect(Array.isArray(await pending.json())).toBe(true);
  });

  test('creates, updates, toggles and deletes a travel package', async () => {
    const name = `E2E Package ${Date.now()}`;
    const pkg = {
      name,
      description: 'Created by the E2E suite',
      category: 'HILL_STATION',
      state: 'Tamil Nadu',
      durationDays: 3,
      durationNights: 2,
      pricePerPerson: 4999,
      maxGroupSize: 12,
      placesIncluded: ['Ooty', 'Coonoor'],
      foodIncluded: true,
      accommodationIncluded: true,
      transportIncluded: true,
      highlights: ['Toy train', 'Tea estates'],
      itinerary: [{ day: 1, title: 'Arrival', description: 'Check in', activities: ['Rest'] }],
    };

    const created = await c.owner.post(`/api/owner/packages?ownerId=${OWNER_ID}`, { data: pkg });
    expect(created.status(), (await created.text()).slice(0, 300)).toBe(201);
    const body = await created.json();
    const id = body.id;
    expect(id).toBeTruthy();

    const updated = await c.owner.put(`/api/owner/packages/${id}`, { data: { ...pkg, pricePerPerson: 5499 } });
    expect(updated.status()).toBe(200);
    expect(Number((await updated.json()).pricePerPerson)).toBe(5499);

    const toggled = await c.owner.post(`/api/owner/packages/${id}/toggle`);
    expect(toggled.status()).toBe(200);

    const list = await c.owner.get('/api/owner/packages');
    expect(list.status()).toBe(200);

    const deleted = await c.owner.delete(`/api/owner/packages/${id}`);
    expect(deleted.status()).toBe(200);
  });

  test('rejects an invalid package payload', async () => {
    const res = await c.owner.post(`/api/owner/packages?ownerId=${OWNER_ID}`, {
      data: { name: '', category: '', state: '', durationDays: 0, durationNights: -1, pricePerPerson: 0, maxGroupSize: 0 },
    });
    expect(res.status()).toBe(400);
  });

  test('lists package bookings', async () => {
    const res = await c.owner.get('/api/owner/package-bookings');
    expect(res.status()).toBe(200);
    expect(Array.isArray(await res.json())).toBe(true);
  });

  test('owner notifications are readable', async () => {
    const res = await c.owner.get('/api/notifications?recipientId=owner&role=ROLE_OWNER');
    expect(res.status()).toBe(200);
    expect(Array.isArray(await res.json())).toBe(true);

    const count = await c.owner.get('/api/notifications/unread-count?recipientId=owner&role=ROLE_OWNER');
    expect(count.status()).toBe(200);
    expect(typeof (await count.json()).count).toBe('number');
  });

  test('cannot delete a driver who has an active booking', async () => {
    // The suite's driver is tied to live bookings from the driver spec.
    const res = await c.owner.delete(`/api/owner/drivers/${acc.driver.driverId}`);
    expect(res.status(), 'deleting a driver mid-trip must be blocked').toBe(400);
    expect((await res.text()).toLowerCase()).toContain('active booking');
  });

  test('assign-driver validates its payload', async () => {
    const bookings = await (await c.owner.get('/api/owner/bookings')).json();
    test.skip(!bookings.length, 'no bookings to assign against');
    const res = await c.owner.post(`/api/owner/bookings/${bookings[0].bookingId}/assign-driver`, { data: {} });
    expect(res.status()).toBe(400);
  });
});
