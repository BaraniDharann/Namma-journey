import { test, expect, Browser, Page } from '@playwright/test';
import { readAccounts, API_BASE } from '../helpers/env';
import { apiContext, freshIp } from '../helpers/api';
import { contexts, disposeAll, bookingPayload, Ctxs } from '../helpers/booking';
import { createUser, NewUser } from '../helpers/accounts';
import { authedContext, watch, summarize, Session } from '../helpers/browser';

/**
 * The live-tracking pipeline, end to end and through the real UI:
 *
 *   driver browser  --STOMP /app/location.update-->  backend  --/topic/booking/{id}/location-->  user browser
 *                   \--REST fallback POST /api/driver/location/update--/        \--REST poll GET /api/driver/location/{id}--/
 *
 * The assertions are chosen so a passing run really means "the car moves on the user's map":
 *  - the backend's stored coordinates must CHANGE while only the driver UI is open
 *    (proves the frontend is emitting, not just rendering a decorative animation), and
 *  - the user's map must show the "updated Ns ago" freshness counter and the re-center
 *    button, both of which only render once real driver coordinates have arrived.
 */

const acc = readAccounts();
const D = acc.driver.driverId;

/**
 * Tracking gets its OWN freshly signed-up traveller. The shared account accumulates a
 * couple of dozen bookings across the other specs, and `/api/user/{id}/bookings` returns
 * them in no defined order — so which booking lands on which paginated page is not stable.
 * A dedicated user keeps this spec's list to a single page and the target unambiguous.
 */
let trackUser: NewUser;
let U: string;
let userSession: Session;

const driverSession: Session = {
  token: acc.driver.token, userId: D, role: 'ROLE_DRIVER',
  name: acc.driver.name, email: acc.driver.email, mobile: acc.driver.mobile,
};

/** API contexts where the "user" is this spec's dedicated traveller. */
async function tctx(label: string): Promise<Ctxs> {
  return {
    user: await apiContext({ token: trackUser.token, ip: freshIp(`${label}-tuser`) }),
    driver: await apiContext({ token: acc.driver.token, ip: freshIp(`${label}-driver`) }),
    owner: await apiContext({ token: acc.owner.token, ip: freshIp(`${label}-owner`) }),
  };
}

/**
 * Earlier specs leave several trackable bookings behind. Drive every one of them to a
 * terminal state so exactly one "📍 Track" button exists on each page and the test can
 * click it unambiguously.
 */
async function quiesceOtherTrips(c: Ctxs, keepBookingId?: string) {
  const res = await c.driver.get(`/api/driver/${D}/bookings`);
  if (res.status() !== 200) return;

  for (const b of await res.json()) {
    if (b.bookingId === keepBookingId) continue;
    if (b.status === 'CONFIRMED') {
      await c.driver.post(`/api/driver/${D}/bookings/${b.bookingId}/start-trip`);
    }
    if (b.status === 'CONFIRMED' || b.status === 'STARTED') {
      // end-trip only raises a UPI invoice; the booking leaves STARTED when the owner
      // verifies that payment.
      const ended = await c.driver.post(`/api/driver/${D}/bookings/${b.bookingId}/end-trip`);
      if (ended.status() === 200) {
        const invoice = await ended.json();
        if (invoice.paymentId) await c.owner.post(`/api/owner/payments/${invoice.paymentId}/verify`);
      }
    }
  }

  // Any remaining user-side PENDING/CONFIRMED booking would also render a Track button.
  const mine = await c.user.get(`/api/user/${U}/bookings`);
  if (mine.status() !== 200) return;
  for (const b of await mine.json()) {
    if (b.bookingId === keepBookingId) continue;
    if (b.status === 'PENDING') await c.user.delete(`/api/user/${U}/bookings/${b.bookingId}`);
  }
}

async function startedTrackingBooking(c: Ctxs, label: string) {
  await quiesceOtherTrips(c);

  // A unique drop label makes the booking identifiable in the UI and in screenshots.
  const created = await c.user.post(`/api/user/${U}/bookings`, {
    data: bookingPayload({ toPlace: `Chennai ${label}` }),
  });
  expect(created.status(), (await created.text()).slice(0, 300)).toBe(201);
  const b = await created.json();

  expect((await c.owner.post(`/api/owner/bookings/${b.bookingId}/assign-driver`, { data: { driverId: Number(D) } })).status()).toBe(200);
  expect((await c.driver.post(`/api/driver/${D}/bookings/${b.bookingId}/action`, { data: { action: 'ACCEPT' } })).status()).toBe(200);
  const started = await c.driver.post(`/api/driver/${D}/bookings/${b.bookingId}/start-trip`);
  expect(started.status()).toBe(200);
  return await started.json();
}

/** Read the backend's currently stored fix for a booking (404 until the driver publishes). */
async function storedLocation(bookingId: string) {
  // Must be the booking's own traveller — BookingAccessGuard rejects anyone else.
  const api = await apiContext({ token: trackUser.token, ip: freshIp('probe') });
  try {
    const res = await api.get(`/api/driver/location/${bookingId}`);
    return res.status() === 200 ? await res.json() : null;
  } finally {
    await api.dispose();
  }
}

async function waitForStoredLocation(bookingId: string, timeoutMs = 45000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const loc = await storedLocation(bookingId);
    if (loc) return loc;
    await new Promise((r) => setTimeout(r, 1500));
  }
  return null;
}

/**
 * Click the Track button belonging to one specific booking. Bookings carry a unique drop
 * label, so the target card is the deepest <div> that holds both that label and a Track
 * button — robust no matter how many other bookings are on the page.
 */
async function openTracking(page: Page, heading: string, label: string) {
  const anyTrack = page.getByRole('button', { name: '📍 Track' });

  // The list is fetched after mount. Wait on the header count, which renders for any
  // status — a Track button may legitimately be absent from the first page.
  await expect(
    page.getByText(/\d+ total (assigned )?trips/),
    'booking list never finished loading'
  ).toBeVisible({ timeout: 30000 });

  // The driver page has a STARTED filter; using it puts the live trip on page 1.
  const startedPill = page.getByRole('button', { name: 'STARTED', exact: true });
  if ((await startedPill.count()) > 0) {
    await startedPill.click();
    await page.waitForTimeout(600);
  }

  const cardFor = () => page.locator('div').filter({ hasText: label }).filter({ has: anyTrack }).last();

  // Both booking lists paginate at 6 rows, and a freshly created booking is not
  // necessarily on page 1 — walk the pages until the right card shows up.
  let card = cardFor();
  for (let i = 0; i < 10 && (await card.count()) === 0; i++) {
    const next = page.getByRole('button', { name: 'Next →' });
    if ((await next.count()) === 0 || (await next.isDisabled())) break;
    await next.click();
    await page.waitForTimeout(800);
    card = cardFor();
  }

  await expect(card, `no trackable booking card found for "${label}"`).toBeVisible({ timeout: 30000 });
  await card.getByRole('button', { name: '📍 Track' }).click();
  await expect(page.getByText(heading, { exact: false })).toBeVisible({ timeout: 15000 });
  await expect(page.locator('.leaflet-container').first()).toBeVisible({ timeout: 20000 });
}

test.describe('Live tracking', () => {
  test.describe.configure({ timeout: 240_000 });

  test.beforeAll(async () => {
    trackUser = await createUser('tracker');
    U = trackUser.userId;
    userSession = {
      token: trackUser.token, userId: U, role: 'ROLE_USER',
      name: trackUser.name, email: trackUser.email, mobile: trackUser.mobile,
    };
    console.log(`[tracking] dedicated traveller ${trackUser.email} (${U})`);
  });

  test('the SockJS tracking endpoint is served', async () => {
    const api = await apiContext({ ip: freshIp('ws-info') });
    const res = await api.get('/ws/tracking/info');
    expect(res.status(), 'STOMP/SockJS handshake endpoint must be reachable').toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty('websocket');
    await api.dispose();
  });

  test('driver UI publishes moving coordinates and the user UI receives them', async ({ browser }, testInfo) => {
    const c = await tctx('track-main');
    const label = `E2E${Date.now().toString().slice(-6)}`;
    const booking = await startedTrackingBooking(c, label);
    const bookingId = booking.bookingId;
    testInfo.annotations.push({ type: 'booking', description: `${bookingId} (drop label ${label})` });

    // Nothing published yet.
    expect(await storedLocation(bookingId), 'no fix should exist before the driver opens tracking').toBeNull();

    // ---------------------------------------------------------------- driver
    const driverCtx = await authedContext(browser, driverSession, 'track-driver');
    const driverPage = await driverCtx.newPage();
    const driverProblems = watch(driverPage, '/driver/bookings');

    await driverPage.goto('/driver/bookings', { waitUntil: 'domcontentloaded' });
    await driverPage.waitForLoadState('networkidle').catch(() => {});
    await openTracking(driverPage, 'Live Navigation', booking.toPlace);

    await expect(driverPage.locator('.driver-car-icon').first()).toBeAttached({ timeout: 20000 });
    await testInfo.attach('driver-live-navigation.png', {
      body: await driverPage.screenshot({ fullPage: true }),
      contentType: 'image/png',
    });

    // The decisive check: the backend now holds a fix that the *frontend* sent.
    const first = await waitForStoredLocation(bookingId);
    expect(first, 'driver UI never published a location to the backend').not.toBeNull();

    // ...and it advances over time, i.e. the car is actually moving.
    let moved = 0;
    const deadline = Date.now() + 30000;
    let latest = first;
    while (Date.now() < deadline && moved <= 1e-6) {
      await driverPage.waitForTimeout(2500);
      latest = await storedLocation(bookingId);
      if (latest) moved = Math.abs(latest.latitude - first!.latitude) + Math.abs(latest.longitude - first!.longitude);
    }
    expect(moved, `coordinates stayed at ${JSON.stringify(first)} — the driver map is not moving`).toBeGreaterThan(1e-6);
    testInfo.annotations.push({ type: 'movement', description: `advanced ${moved.toFixed(6)}° from the driver UI` });

    // ------------------------------------------------------------------ user
    const userCtx = await authedContext(browser, userSession, 'track-user');
    const userPage = await userCtx.newPage();
    const userProblems = watch(userPage, '/user/bookings');

    await userPage.goto('/user/bookings', { waitUntil: 'domcontentloaded' });
    await userPage.waitForLoadState('networkidle').catch(() => {});
    await openTracking(userPage, 'Track Your Driver', booking.toPlace);

    // The freshness counter only renders after a real fix has been applied to the map.
    await expect(userPage.getByText(/updated \d+s ago/), 'user map never received a live fix').toBeVisible({ timeout: 45000 });
    // The re-center button only renders when driverPos is set.
    await expect(userPage.locator('[title="Re-center on driver"]')).toBeVisible({ timeout: 15000 });
    await expect(userPage.getByText("Connecting to driver's live location...")).toHaveCount(0);
    await expect(userPage.locator('.driver-car-icon').first()).toBeAttached();

    await testInfo.attach('user-track-your-driver.png', {
      body: await userPage.screenshot({ fullPage: true }),
      contentType: 'image/png',
    });

    // The user's map must keep updating, not freeze on the first fix.
    const freshnessText = async () => (await userPage.getByText(/updated \d+s ago/).innerText()).trim();
    const before = await freshnessText();
    const beforeLoc = await storedLocation(bookingId);
    await userPage.waitForTimeout(12000);
    const afterLoc = await storedLocation(bookingId);
    const drift = Math.abs(afterLoc.latitude - beforeLoc!.latitude) + Math.abs(afterLoc.longitude - beforeLoc!.longitude);
    expect(drift, 'position stopped advancing while the user watched').toBeGreaterThan(1e-6);
    testInfo.annotations.push({ type: 'user view', description: `freshness "${before}" -> "${await freshnessText()}", drift ${drift.toFixed(6)}°` });

    expect(driverProblems.pageErrors, summarize(driverProblems)).toEqual([]);
    expect(userProblems.pageErrors, summarize(userProblems)).toEqual([]);

    await driverCtx.close();
    await userCtx.close();
    await disposeAll(c);
  });

  test('the user map is driven by the WebSocket push, not only the REST poll', async ({ browser }, testInfo) => {
    const c = await tctx('track-ws');
    const booking = await startedTrackingBooking(c, `WS${Date.now().toString().slice(-6)}`);
    const bookingId = booking.bookingId;

    const driverCtx = await authedContext(browser, driverSession, 'ws-driver');
    const driverPage = await driverCtx.newPage();
    await driverPage.goto('/driver/bookings', { waitUntil: 'domcontentloaded' });
    await openTracking(driverPage, 'Live Navigation', booking.toPlace);
    expect(await waitForStoredLocation(bookingId), 'driver UI did not publish').not.toBeNull();

    const userCtx = await authedContext(browser, userSession, 'ws-user');
    const userPage = await userCtx.newPage();

    // Record whether a real WebSocket upgrade happened for the tracking endpoint.
    const sockets: string[] = [];
    userPage.on('websocket', (ws) => sockets.push(ws.url()));

    await userPage.goto('/user/bookings', { waitUntil: 'domcontentloaded' });
    await openTracking(userPage, 'Track Your Driver', booking.toPlace);
    await expect(userPage.getByText(/updated \d+s ago/)).toBeVisible({ timeout: 45000 });

    // The header prefixes a ● only while the STOMP client reports itself connected.
    const freshness = await userPage.getByText(/updated \d+s ago/).innerText();
    testInfo.annotations.push({
      type: 'sockets',
      description: sockets.length ? sockets.join(', ') : 'no websocket frames observed',
    });

    expect(sockets.some((u) => u.includes('/ws/tracking')), `no WebSocket opened to /ws/tracking (saw: ${sockets.join(', ') || 'none'})`).toBe(true);
    expect(freshness, `STOMP client not reporting connected — header read "${freshness}"`).toContain('●');

    await driverCtx.close();
    await userCtx.close();
    await disposeAll(c);
  });

  test('tracking shows distance remaining and an ETA', async ({ browser }) => {
    const c = await tctx('track-eta');
    const booking = await startedTrackingBooking(c, `ETA${Date.now().toString().slice(-6)}`);

    const driverCtx = await authedContext(browser, driverSession, 'eta-driver');
    const driverPage = await driverCtx.newPage();
    await driverPage.goto('/driver/bookings', { waitUntil: 'domcontentloaded' });
    await openTracking(driverPage, 'Live Navigation', booking.toPlace);

    await expect(driverPage.getByText('LIVE TRACKING')).toBeVisible();
    await expect(driverPage.getByText(/ETA:/)).toBeVisible();
    await expect(driverPage.getByText(/\d+(\.\d+)? km left/), 'distance remaining never populated').toBeVisible({ timeout: 45000 });

    await driverCtx.close();
    await disposeAll(c);
  });

  test('the driver dashboard embeds the live map for an active trip', async ({ browser }, testInfo) => {
    const c = await tctx('track-dash');
    await startedTrackingBooking(c, `DASH${Date.now().toString().slice(-6)}`);

    const ctx = await authedContext(browser, driverSession, 'dash-driver');
    const page = await ctx.newPage();
    const problems = watch(page, '/driver/dashboard');

    await page.goto('/driver/dashboard', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.leaflet-container').first(), 'active trip should render the map inline').toBeVisible({ timeout: 30000 });
    await expect(page.getByText('Start a trip to activate GPS tracking')).toHaveCount(0);

    await testInfo.attach('driver-dashboard-live-map.png', {
      body: await page.screenshot({ fullPage: true }),
      contentType: 'image/png',
    });
    expect(problems.pageErrors, summarize(problems)).toEqual([]);

    await ctx.close();
    await disposeAll(c);
  });

  test('location survives only for the booking it belongs to', async () => {
    const c = await tctx('track-isolation');
    const a = await startedTrackingBooking(c, `ISOA${Date.now().toString().slice(-6)}`);

    await c.driver.post('/api/driver/location/update', {
      data: { driverId: Number(D), bookingId: a.bookingId, latitude: 11.5, longitude: 77.5, heading: 90, timestamp: Date.now() },
    });

    const own = await c.user.get(`/api/driver/location/${a.bookingId}`);
    expect(own.status()).toBe(200);
    expect((await own.json()).latitude).toBeCloseTo(11.5, 4);

    // A different booking must not inherit that fix.
    const other = await c.user.post(`/api/user/${U}/bookings`, { data: bookingPayload() });
    const otherBooking = await other.json();
    const otherLoc = await c.user.get(`/api/driver/location/${otherBooking.bookingId}`);
    expect(otherLoc.status(), 'each booking must have its own location slot').toBe(404);

    await disposeAll(c);
  });
});
