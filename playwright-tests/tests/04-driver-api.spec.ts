import { test, expect } from '@playwright/test';
import { readAccounts } from '../helpers/env';
import { contexts, disposeAll, bookingPayload, createStartedBooking, Ctxs } from '../helpers/booking';
import { query } from '../helpers/db';

const acc = readAccounts();
const U = acc.user.userId;
const D = acc.driver.driverId;

test.describe('Driver API', () => {
  let c: Ctxs;
  test.beforeEach(async ({}, testInfo) => {
    c = await contexts(`driver-${testInfo.title}`);
  });
  test.afterEach(async () => disposeAll(c));

  test('returns the driver profile', async () => {
    const res = await c.driver.get(`/api/driver/${D}/profile`);
    expect(res.status(), (await res.text()).slice(0, 300)).toBe(200);
    const p = await res.json();
    expect(String(p.id ?? p.driverId)).toBe(D);
    expect(p.email).toBe(acc.driver.email);
  });

  test('lists the driver bookings', async () => {
    const res = await c.driver.get(`/api/driver/${D}/bookings`);
    expect(res.status()).toBe(200);
    expect(Array.isArray(await res.json())).toBe(true);
  });

  test('toggles availability', async () => {
    for (const status of ['INACTIVE', 'ACTIVE']) {
      const res = await c.driver.put(`/api/driver/${D}/availability`, { data: { status } });
      expect(res.status(), `${status} -> ${(await res.text()).slice(0, 200)}`).toBe(200);
    }
  });

  test('rejects an unknown availability status', async () => {
    const res = await c.driver.put(`/api/driver/${D}/availability`, { data: { status: 'ON_HOLIDAY' } });
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });

  test('walks a booking through assign -> accept -> start -> end -> cash', async () => {
    const created = await c.user.post(`/api/user/${U}/bookings`, { data: bookingPayload() });
    expect(created.status()).toBe(201);
    const b = await created.json();
    expect(b.status).toBe('PENDING');

    const assigned = await c.owner.post(`/api/owner/bookings/${b.bookingId}/assign-driver`, {
      data: { driverId: Number(D) },
    });
    expect(assigned.status(), (await assigned.text()).slice(0, 300)).toBe(200);

    // The booking must now be visible to the driver.
    const mine = await c.driver.get(`/api/driver/${D}/bookings`);
    expect((await mine.json()).map((x: any) => x.bookingId)).toContain(b.bookingId);

    const accepted = await c.driver.post(`/api/driver/${D}/bookings/${b.bookingId}/action`, {
      data: { action: 'ACCEPT' },
    });
    expect(accepted.status()).toBe(200);
    expect((await accepted.json()).status).toBe('CONFIRMED');

    const started = await c.driver.post(`/api/driver/${D}/bookings/${b.bookingId}/start-trip`);
    expect(started.status()).toBe(200);
    expect((await started.json()).status).toBe('STARTED');

    const ended = await c.driver.post(`/api/driver/${D}/bookings/${b.bookingId}/end-trip`);
    expect(ended.status(), (await ended.text()).slice(0, 300)).toBe(200);
    const payment = await ended.json();
    expect(payment).toBeTruthy();
    expect(JSON.stringify(payment)).toMatch(/upi|amount/i);

    const cash = await c.driver.post(`/api/driver/${D}/bookings/${b.bookingId}/cash-payment`, {
      data: { amountReceived: 500 },
    });
    expect([200, 201, 400]).toContain(cash.status()); // 400 if already settled by end-trip
  });

  test('assigning a driver moves the booking straight to CONFIRMED', async () => {
    const created = await c.user.post(`/api/user/${U}/bookings`, { data: bookingPayload() });
    const b = await created.json();
    expect(b.status).toBe('PENDING');

    const assigned = await c.owner.post(`/api/owner/bookings/${b.bookingId}/assign-driver`, {
      data: { driverId: Number(D) },
    });
    expect(assigned.status()).toBe(200);
    expect((await assigned.json()).status, 'assignment confirms the booking').toBe('CONFIRMED');
  });

  test('cannot start a trip that is already running', async () => {
    const created = await c.user.post(`/api/user/${U}/bookings`, { data: bookingPayload() });
    const b = await created.json();
    await c.owner.post(`/api/owner/bookings/${b.bookingId}/assign-driver`, { data: { driverId: Number(D) } });
    expect((await c.driver.post(`/api/driver/${D}/bookings/${b.bookingId}/start-trip`)).status()).toBe(200);

    const again = await c.driver.post(`/api/driver/${D}/bookings/${b.bookingId}/start-trip`);
    expect(again.status(), 'starting a STARTED trip must be refused').toBeGreaterThanOrEqual(400);
    expect((await again.text()).toLowerCase()).toContain('confirmed');
  });

  test('an unassigned driver cannot start someone else\'s booking', async () => {
    const created = await c.user.post(`/api/user/${U}/bookings`, { data: bookingPayload() });
    const b = await created.json();
    // Booking creation auto-allocates whichever driver is free, which can be this very
    // driver — so detach it explicitly. Without this the request trips the CONFIRMED check
    // first and never reaches the authorization branch this test exists to cover.
    await query('UPDATE travel_bookings SET driver_id = NULL WHERE id = $1', [b.bookingId]);
    const res = await c.driver.post(`/api/driver/${D}/bookings/${b.bookingId}/start-trip`);
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect((await res.text()).toLowerCase()).toContain('not assigned');
  });

  test('end-trip raises the invoice but the booking completes only once payment settles', async () => {
    const created = await c.user.post(`/api/user/${U}/bookings`, { data: bookingPayload() });
    const b = await created.json();
    await c.owner.post(`/api/owner/bookings/${b.bookingId}/assign-driver`, { data: { driverId: Number(D) } });
    await c.driver.post(`/api/driver/${D}/bookings/${b.bookingId}/start-trip`);

    const ended = await c.driver.post(`/api/driver/${D}/bookings/${b.bookingId}/end-trip`);
    expect(ended.status()).toBe(200);
    const invoice = await ended.json();
    expect(Number(invoice.amount)).toBeGreaterThan(0);

    const afterEnd = await c.user.get(`/api/user/${U}/bookings/${b.bookingId}`);
    expect(
      (await afterEnd.json()).status,
      'documented behaviour: end-trip alone does not complete the booking'
    ).toBe('STARTED');

    // end-trip raises a UPI invoice, so the cash route is correctly refused here.
    expect(invoice.paymentMethod).toBe('UPI');
    expect(invoice.status).toBe('PENDING');
    const cash = await c.driver.post(`/api/driver/${D}/bookings/${b.bookingId}/cash-payment`, {
      data: { amountReceived: Number(invoice.amount) },
    });
    expect(cash.status()).toBe(400);
    expect(await cash.text()).toContain('not a cash payment');

    // The owner verifying the UPI payment is what completes the trip.
    const verified = await c.owner.post(`/api/owner/payments/${invoice.paymentId}/verify`);
    expect(verified.status(), (await verified.text()).slice(0, 200)).toBe(200);

    const afterPay = await c.user.get(`/api/user/${U}/bookings/${b.bookingId}`);
    expect((await afterPay.json()).status).toBe('COMPLETED');
  });

  test('rejects an unknown booking action', async () => {
    const created = await c.user.post(`/api/user/${U}/bookings`, { data: bookingPayload() });
    const b = await created.json();
    await c.owner.post(`/api/owner/bookings/${b.bookingId}/assign-driver`, { data: { driverId: Number(D) } });

    const res = await c.driver.post(`/api/driver/${D}/bookings/${b.bookingId}/action`, { data: { action: 'MAYBE' } });
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });

  test('can REJECT an assigned booking', async () => {
    const created = await c.user.post(`/api/user/${U}/bookings`, { data: bookingPayload() });
    const b = await created.json();
    await c.owner.post(`/api/owner/bookings/${b.bookingId}/assign-driver`, { data: { driverId: Number(D) } });

    const res = await c.driver.post(`/api/driver/${D}/bookings/${b.bookingId}/action`, { data: { action: 'REJECT' } });
    expect(res.status(), (await res.text()).slice(0, 300)).toBe(200);
    expect(['REJECTED', 'PENDING', 'CANCELLED']).toContain((await res.json()).status);
  });

  test('driver location update + read-back round-trips', async () => {
    const booking = await createStartedBooking(c);
    const bookingId = booking.bookingId;

    const points = [
      [11.0168, 76.9558],
      [11.12, 77.05],
      [11.28, 77.21],
    ];
    for (const [latitude, longitude] of points) {
      const res = await c.driver.post('/api/driver/location/update', {
        data: { driverId: Number(D), bookingId, latitude, longitude, heading: 45, timestamp: Date.now() },
      });
      expect(res.status(), (await res.text()).slice(0, 200)).toBe(200);
      await new Promise((r) => setTimeout(r, 120));
    }

    const read = await c.user.get(`/api/driver/location/${bookingId}`);
    expect(read.status()).toBe(200);
    const loc = await read.json();
    expect(loc.latitude).toBeCloseTo(points[2][0], 5);
    expect(loc.longitude).toBeCloseTo(points[2][1], 5);
  });

  test('location read for a booking with no fix yet is a 404', async () => {
    const created = await c.user.post(`/api/user/${U}/bookings`, { data: bookingPayload() });
    const b = await created.json();
    const res = await c.user.get(`/api/driver/location/${b.bookingId}`);
    expect(res.status()).toBe(404);
  });
});
