import fs from 'fs';
import path from 'path';
import { APIRequestContext, expect } from '@playwright/test';
import { apiContext, freshIp } from './api';
import { readAccounts } from './env';

export function futureDate(daysAhead: number): string {
  const d = new Date();
  d.setDate(d.getDate() + daysAhead);
  return d.toISOString().slice(0, 10);
}

/** Coimbatore -> Chennai, the coordinates the whole suite tracks against. */
export const ROUTE = {
  fromPlace: 'Coimbatore',
  toPlace: 'Chennai',
  fromLat: 11.0168,
  fromLon: 76.9558,
  toLat: 13.0827,
  toLon: 80.2707,
};

/**
 * The backend refuses to assign a driver to a trip that overlaps one they are already on
 * ("Driver already has an active trip overlapping these dates"). That rule is correct, so
 * every booking the suite creates gets its own non-overlapping window instead.
 */
/* Each spec file runs in its own module scope, so the counter is kept on disk to stay
 * unique across the whole run rather than restarting at zero per file. */
const SLOT_FILE = path.join(__dirname, '..', 'test-data', 'slot.txt');

function nextSlot(): number {
  let n = 0;
  try {
    n = parseInt(fs.readFileSync(SLOT_FILE, 'utf8').trim(), 10) || 0;
  } catch {
    n = 0;
  }
  fs.mkdirSync(path.dirname(SLOT_FILE), { recursive: true });
  fs.writeFileSync(SLOT_FILE, String(n + 1));
  return n;
}

export function bookingPayload(overrides: Record<string, any> = {}) {
  const acc = readAccounts();
  const start = 3 + nextSlot() * 4;
  return {
    userName: acc.user.name,
    userPhone: acc.user.mobile,
    ...ROUTE,
    fromDate: futureDate(start),
    toDate: futureDate(start + 1),
    travelMembers: 3,
    acType: 'AC',
    bookingType: 'DISTANCE_BASED',
    ...overrides,
  };
}

export type Ctxs = { user: APIRequestContext; driver: APIRequestContext; owner: APIRequestContext };

export async function contexts(label: string): Promise<Ctxs> {
  const acc = readAccounts();
  return {
    user: await apiContext({ token: acc.user.token, ip: freshIp(`${label}-user`) }),
    driver: await apiContext({ token: acc.driver.token, ip: freshIp(`${label}-driver`) }),
    owner: await apiContext({ token: acc.owner.token, ip: freshIp(`${label}-owner`) }),
  };
}

export async function disposeAll(c: Ctxs) {
  await Promise.all([c.user.dispose(), c.driver.dispose(), c.owner.dispose()]);
}

/**
 * Walk a booking all the way to STARTED — the only status where live tracking is
 * available on both the driver and the user side:
 *   user creates -> owner assigns our driver -> driver ACCEPTs (CONFIRMED) -> driver starts.
 */
export async function createStartedBooking(c: Ctxs): Promise<any> {
  const acc = readAccounts();

  const created = await c.user.post(`/api/user/${acc.user.userId}/bookings`, { data: bookingPayload() });
  expect(created.status(), `create booking: ${(await created.text()).slice(0, 300)}`).toBe(201);
  const booking = await created.json();

  const assigned = await c.owner.post(`/api/owner/bookings/${booking.bookingId}/assign-driver`, {
    data: { driverId: Number(acc.driver.driverId) },
  });
  expect(assigned.status(), `assign driver: ${(await assigned.text()).slice(0, 300)}`).toBe(200);

  const accepted = await c.driver.post(
    `/api/driver/${acc.driver.driverId}/bookings/${booking.bookingId}/action`,
    { data: { action: 'ACCEPT' } }
  );
  expect(accepted.status(), `driver accept: ${(await accepted.text()).slice(0, 300)}`).toBe(200);
  expect((await accepted.json()).status).toBe('CONFIRMED');

  const started = await c.driver.post(
    `/api/driver/${acc.driver.driverId}/bookings/${booking.bookingId}/start-trip`
  );
  expect(started.status(), `start trip: ${(await started.text()).slice(0, 300)}`).toBe(200);
  const startedBody = await started.json();
  expect(startedBody.status).toBe('STARTED');

  return startedBody;
}
