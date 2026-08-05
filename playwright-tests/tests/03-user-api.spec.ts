import { test, expect } from '@playwright/test';
import { readAccounts } from '../helpers/env';
import { contexts, disposeAll, bookingPayload, futureDate, Ctxs } from '../helpers/booking';

const acc = readAccounts();
const U = acc.user.userId;

test.describe('User API', () => {
  let c: Ctxs;
  test.beforeEach(async ({}, testInfo) => {
    // A fresh synthetic client IP per test keeps the 50 req/min limiter out of the way.
    c = await contexts(`user-${testInfo.title}`);
  });
  test.afterEach(async () => disposeAll(c));

  test('creates a distance-based booking and reads it back', async () => {
    const res = await c.user.post(`/api/user/${U}/bookings`, { data: bookingPayload() });
    expect(res.status(), (await res.text()).slice(0, 300)).toBe(201);
    const b = await res.json();

    expect(b.bookingId).toBeTruthy();
    expect(b.status).toBe('PENDING');
    expect(b.fromPlace).toBe('Coimbatore');
    expect(b.toPlace).toBe('Chennai');
    expect(Number(b.distanceKm), 'Coimbatore->Chennai should be a few hundred km').toBeGreaterThan(100);
    expect(Number(b.totalAmount), 'total must be priced').toBeGreaterThan(0);

    const one = await c.user.get(`/api/user/${U}/bookings/${b.bookingId}`);
    expect(one.status()).toBe(200);
    expect((await one.json()).bookingId).toBe(b.bookingId);

    const list = await c.user.get(`/api/user/${U}/bookings`);
    expect(list.status()).toBe(200);
    const all = await list.json();
    expect(Array.isArray(all)).toBe(true);
    expect(all.map((x: any) => x.bookingId)).toContain(b.bookingId);
  });

  test('creates an hour-based booking', async () => {
    const res = await c.user.post(`/api/user/${U}/bookings`, {
      data: bookingPayload({ bookingType: 'HOUR_BASED', bookingHours: 6 }),
    });
    expect(res.status(), (await res.text()).slice(0, 300)).toBe(201);
    const b = await res.json();
    expect(Number(b.totalAmount)).toBeGreaterThan(0);
  });

  test('rejects invalid booking payloads', async () => {
    const bad: Array<[string, Record<string, any>]> = [
      ['phone not 10-digit Indian mobile', { userPhone: '12345' }],
      ['travelMembers above max', { travelMembers: 99 }],
      ['travelMembers below min', { travelMembers: 0 }],
      ['acType outside AC/NON_AC', { acType: 'SEMI_AC' }],
      ['missing destination coordinates', { toLat: null, toLon: null }],
      ['blank pickup place', { fromPlace: '' }],
      ['bookingHours above max', { bookingType: 'HOUR_BASED', bookingHours: 48 }],
    ];
    for (const [label, override] of bad) {
      const res = await c.user.post(`/api/user/${U}/bookings`, { data: bookingPayload(override) });
      expect(res.status(), `${label} -> ${res.status()}`).toBe(400);
    }
  });

  test('updates and cancels a booking', async () => {
    const created = await c.user.post(`/api/user/${U}/bookings`, { data: bookingPayload() });
    expect(created.status()).toBe(201);
    const b = await created.json();

    const updated = await c.user.put(`/api/user/${U}/bookings/${b.bookingId}`, {
      data: bookingPayload({ travelMembers: 5, fromDate: futureDate(10), toDate: futureDate(12) }),
    });
    expect(updated.status(), (await updated.text()).slice(0, 300)).toBe(200);
    expect((await updated.json()).travelMembers).toBe(5);

    const deleted = await c.user.delete(`/api/user/${U}/bookings/${b.bookingId}`);
    expect(deleted.status()).toBe(204);
  });

  test('returns a booking summary', async () => {
    const created = await c.user.post(`/api/user/${U}/bookings`, { data: bookingPayload() });
    const b = await created.json();
    const res = await c.user.get(`/api/user/${U}/bookings/${b.bookingId}/summary`);
    expect(res.status(), (await res.text()).slice(0, 300)).toBe(200);
    expect(await res.json()).toBeTruthy();
  });

  test('records a UPI payment and lists it', async () => {
    const created = await c.user.post(`/api/user/${U}/bookings`, { data: bookingPayload() });
    const b = await created.json();

    // Payment is gated on confirmation — that gate is asserted separately below.
    const confirm = await c.user.post(`/api/user/${U}/bookings/${b.bookingId}/confirm`);
    expect(confirm.status(), (await confirm.text()).slice(0, 300)).toBe(200);

    const pay = await c.user.post(`/api/user/${U}/bookings/${b.bookingId}/payment`, {
      data: { paymentMethod: 'UPI', upiTransactionId: `E2E${Date.now()}` },
    });
    expect(pay.status(), (await pay.text()).slice(0, 300)).toBe(201);

    const list = await c.user.get(`/api/user/${U}/payments`);
    expect(list.status()).toBe(200);
    expect(Array.isArray(await list.json())).toBe(true);
  });

  test('rejects a payment with no method', async () => {
    const created = await c.user.post(`/api/user/${U}/bookings`, { data: bookingPayload() });
    const b = await created.json();
    await c.user.post(`/api/user/${U}/bookings/${b.bookingId}/confirm`);
    const pay = await c.user.post(`/api/user/${U}/bookings/${b.bookingId}/payment`, { data: { paymentMethod: '' } });
    expect(pay.status()).toBe(400);
  });

  test('payment is refused before the booking is confirmed', async () => {
    const created = await c.user.post(`/api/user/${U}/bookings`, { data: bookingPayload() });
    const b = await created.json();
    expect(b.status).toBe('PENDING');

    const pay = await c.user.post(`/api/user/${U}/bookings/${b.bookingId}/payment`, {
      data: { paymentMethod: 'UPI', upiTransactionId: `E2E${Date.now()}` },
    });
    expect(pay.status()).toBe(400);
    expect((await pay.text()).toLowerCase()).toContain('confirmed');
  });

  test('updates the profile', async () => {
    const res = await c.user.put(`/api/user/${U}/profile`, {
      data: { name: 'E2E Test User', phone: acc.user.mobile },
    });
    expect(res.status(), (await res.text()).slice(0, 300)).toBe(200);
    const body = await res.json();
    expect(body.userId).toBe(U);
    expect(body.role).toBe('ROLE_USER');
  });

  test('rejects a review rating outside 1-5', async () => {
    const created = await c.user.post(`/api/user/${U}/bookings`, { data: bookingPayload() });
    const b = await created.json();
    const res = await c.user.post(`/api/user/${U}/bookings/${b.bookingId}/reviews`, {
      data: { rating: 9, feedback: 'out of range' },
    });
    expect(res.status()).toBe(400);
  });

  test('lists package bookings and validates package booking input', async () => {
    const list = await c.user.get(`/api/user/${U}/package-bookings`);
    expect(list.status()).toBe(200);
    expect(Array.isArray(await list.json())).toBe(true);

    const bad = await c.user.post(`/api/user/${U}/package-bookings`, {
      data: { packageId: null, userName: '', userEmail: '', userPhone: '', numberOfPersons: 0 },
    });
    expect(bad.status()).toBe(400);
  });

  test('books a travel package when one is published', async () => {
    const pkgs = await c.user.get('/api/public/packages');
    const list = await pkgs.json();
    test.skip(!Array.isArray(list) || list.length === 0, 'no published packages in this database');

    const res = await c.user.post(`/api/user/${U}/package-bookings`, {
      data: {
        packageId: list[0].id,
        userName: acc.user.name,
        userEmail: acc.user.email,
        userPhone: acc.user.mobile,
        numberOfPersons: 2,
        travelDate: futureDate(20),
        specialRequests: 'E2E test booking',
      },
    });
    expect(res.status(), (await res.text()).slice(0, 300)).toBe(201);
    const booked = await res.json();

    const cancel = await c.user.post(`/api/user/${U}/package-bookings/${booked.bookingId || booked.id}/cancel`, {
      data: { reason: 'E2E cleanup' },
    });
    expect([200, 204]).toContain(cancel.status());
  });

  test('notifications endpoints answer for this user', async () => {
    const list = await c.user.get(`/api/notifications?recipientId=${U}&role=ROLE_USER`);
    expect(list.status()).toBe(200);
    expect(Array.isArray(await list.json())).toBe(true);

    const count = await c.user.get(`/api/notifications/unread-count?recipientId=${U}&role=ROLE_USER`);
    expect(count.status()).toBe(200);
    expect(typeof (await count.json()).count).toBe('number');
  });

  test('a 404 booking id is reported as not found', async () => {
    const res = await c.user.get(`/api/user/${U}/bookings/00000000-0000-0000-0000-000000000000`);
    expect([400, 404]).toContain(res.status());
  });
});
