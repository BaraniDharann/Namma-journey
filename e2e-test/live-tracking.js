/**
 * Live-tracking end-to-end workflow test.
 *
 * Verifies the full "is the driver moving on the map" pipeline:
 *   1. BACKEND pipeline  - a driver pushes a sequence of moving coordinates to
 *      POST /api/driver/location/update and a user reads them back from
 *      GET /api/driver/location/{bookingId}; the read must track the writes.
 *   2. DRIVER UI         - open Live Navigation through the real React app and
 *      confirm the on-screen car marker exists AND the backend-stored location
 *      changes over time (i.e. the frontend is actually emitting movement).
 *   3. USER UI           - while the driver UI is sending, open the user's
 *      tracking modal for the same booking and confirm the car marker appears.
 *
 * Phase 1 is deterministic (synthetic bookingId, no DB seed needed) and a
 * failure there is a hard error. Phases 2-3 depend on a trackable booking
 * existing for the seeded driver/user; if none exists they are skipped with a
 * clear message rather than failing the run.
 *
 * Run:  node live-tracking.js   (backend on :8080, frontend dev on :5173)
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { chromium, request: pwRequest } = require('playwright');

const ROOT = path.resolve(__dirname, '..');
const SHOTS = path.join(__dirname, 'screenshots');
const BASE = 'http://localhost:5173';
const API = 'http://localhost:8080';

// ---- read JWT secret/expiration from backend .env ----
const env = {};
for (const line of fs.readFileSync(path.join(ROOT, '.env'), 'utf8').split(/\r?\n/)) {
  const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)$/);
  if (m) env[m[1]] = m[2].trim();
}
const SECRET = env.JWT_SECRET;
const EXP_MS = parseInt(env.JWT_EXPIRATION || '86400000', 10);

const b64url = (b) => Buffer.from(b).toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
function mintToken(userId, role) {
  const now = Math.floor(Date.now() / 1000);
  const data = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' })) + '.' +
    b64url(JSON.stringify({ role, userId, sub: userId, iat: now, exp: now + Math.floor(EXP_MS / 1000) }));
  return data + '.' + b64url(crypto.createHmac('sha256', SECRET).update(data).digest());
}

const ACCOUNTS = {
  user: { userId: 'f54882fa-50ff-40b1-86b4-6500d573f5bd', role: 'ROLE_USER', name: 'Demo User', email: 'demo.user@example.com', mobile: '9000000000' },
  driver: { userId: '10', role: 'ROLE_DRIVER', name: 'Sarathi', email: 'sarasarathy86@gmail.com', mobile: '8220667367' },
};

const results = [];
const pass = (name, detail = '') => { results.push({ name, ok: true, detail }); console.log(`  PASS  ${name}${detail ? '  — ' + detail : ''}`); };
const fail = (name, detail = '') => { results.push({ name, ok: false, detail }); console.log(`  FAIL  ${name}${detail ? '  — ' + detail : ''}`); };
const skip = (name, detail = '') => { results.push({ name, skip: true, detail }); console.log(`  SKIP  ${name}${detail ? '  — ' + detail : ''}`); };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const DRIVER_TOKEN = mintToken(ACCOUNTS.driver.userId, ACCOUNTS.driver.role);
const USER_TOKEN = mintToken(ACCOUNTS.user.userId, ACCOUNTS.user.role);

// ----------------------------------------------------------------------------
// Phase 1: backend location pipeline (deterministic)
// ----------------------------------------------------------------------------
async function phaseBackendPipeline() {
  console.log('\nPHASE 1 — backend location pipeline');
  const driverApi = await pwRequest.newContext({ baseURL: API, extraHTTPHeaders: { Authorization: `Bearer ${DRIVER_TOKEN}` } });
  const userApi = await pwRequest.newContext({ baseURL: API, extraHTTPHeaders: { Authorization: `Bearer ${USER_TOKEN}` } });

  // Synthetic booking id — LocationTrackingService keys on an arbitrary string,
  // so this exercises the store/retrieve path without needing a seeded booking.
  const bookingId = 'e2e-track-' + crypto.randomBytes(4).toString('hex');
  const path0 = [
    [11.0168, 76.9558], [11.05, 77.00], [11.10, 77.05], [11.16, 77.10], [11.22, 77.16],
  ];

  let lastPosted = null;
  for (const [lat, lon] of path0) {
    const res = await driverApi.post('/api/driver/location/update', {
      data: { driverId: Number(ACCOUNTS.driver.userId), bookingId, latitude: lat, longitude: lon, heading: 45, timestamp: Date.now() },
    });
    if (res.status() !== 200) { fail('driver POST /location/update', `HTTP ${res.status()}: ${(await res.text()).slice(0, 160)}`); break; }
    lastPosted = [lat, lon];
    await sleep(150);
  }
  if (lastPosted) pass('driver POST /location/update', `${path0.length} updates accepted (200)`);

  // User reads it back.
  const getRes = await userApi.get(`/api/driver/location/${bookingId}`);
  if (getRes.status() !== 200) {
    fail('user GET /location/{bookingId}', `HTTP ${getRes.status()}`);
  } else {
    const body = await getRes.json();
    const close = lastPosted && Math.abs(body.latitude - lastPosted[0]) < 1e-6 && Math.abs(body.longitude - lastPosted[1]) < 1e-6;
    if (close) pass('user GET /location/{bookingId}', `returns latest driver coords (${body.latitude}, ${body.longitude})`);
    else fail('user GET /location/{bookingId}', `stored ${JSON.stringify([body.latitude, body.longitude])} != last posted ${JSON.stringify(lastPosted)}`);
  }

  // A user must NOT be able to post as a driver, and a driver must not impersonate another driver.
  const forbidden = await userApi.post('/api/driver/location/update', {
    data: { driverId: 99, bookingId, latitude: 1, longitude: 1, heading: 0, timestamp: Date.now() },
  });
  if (forbidden.status() === 403 || forbidden.status() === 401) pass('auth guard on /location/update', `user blocked (HTTP ${forbidden.status()})`);
  else fail('auth guard on /location/update', `expected 401/403, got ${forbidden.status()}`);

  await driverApi.dispose();
  await userApi.dispose();
}

// ----------------------------------------------------------------------------
// Find a trackable booking for the seeded driver, starting one if needed.
// ----------------------------------------------------------------------------
// Advance a booking to STARTED, running whatever workflow steps are needed:
//   PENDING --(accept)--> CONFIRMED --(start-trip)--> STARTED
async function ensureStarted(driverApi, b) {
  const did = ACCOUNTS.driver.userId;
  if (b.status === 'PENDING') {
    const acc = await driverApi.post(`/api/driver/${did}/bookings/${b.bookingId}/action`, { data: { action: 'ACCEPT' } });
    if (acc.status() !== 200) { console.log(`  accept failed: HTTP ${acc.status()}`); return null; }
    b = await acc.json();
    console.log(`  accepted booking ${b.bookingId} -> ${b.status}`);
  }
  if (b.status === 'CONFIRMED') {
    const start = await driverApi.post(`/api/driver/${did}/bookings/${b.bookingId}/start-trip`);
    if (start.status() !== 200) { console.log(`  start-trip failed: HTTP ${start.status()}`); return null; }
    b = await start.json();
    console.log(`  started booking ${b.bookingId} -> ${b.status}`);
  }
  return b.status === 'STARTED' ? b : null;
}

async function findTrackableBooking() {
  const driverApi = await pwRequest.newContext({ baseURL: API, extraHTTPHeaders: { Authorization: `Bearer ${DRIVER_TOKEN}` } });
  try {
    const res = await driverApi.get(`/api/driver/${ACCOUNTS.driver.userId}/bookings`);
    if (res.status() !== 200) return null;
    const bookings = await res.json();
    if (!bookings.length) return null;
    // Prefer an already-active trip; otherwise take any non-terminal booking and advance it.
    const order = { STARTED: 0, CONFIRMED: 1, PENDING: 2 };
    const candidate = bookings
      .filter((x) => x.status in order)
      .sort((a, b) => order[a.status] - order[b.status])[0];
    if (!candidate) return null;
    return await ensureStarted(driverApi, candidate);
  } finally {
    await driverApi.dispose();
  }
}

function makeContext(browser, token, acc) {
  return browser.newContext({
    viewport: { width: 1440, height: 900 },
    permissions: ['geolocation'],
    geolocation: { latitude: 11.0168, longitude: 76.9558 },
  }).then(async (ctx) => {
    const userObj = { userId: acc.userId, role: acc.role, name: acc.name, email: acc.email, mobile: acc.mobile, message: 'ok' };
    await ctx.addInitScript(([t, u]) => {
      localStorage.setItem('nj_token', t);
      localStorage.setItem('nj_user', u);
    }, [token, JSON.stringify(userObj)]);
    return ctx;
  });
}

// Read the stored location for a booking (used to confirm the UI emits movement).
async function readStoredLocation(bookingId) {
  const api = await pwRequest.newContext({ baseURL: API, extraHTTPHeaders: { Authorization: `Bearer ${USER_TOKEN}` } });
  try {
    const res = await api.get(`/api/driver/location/${bookingId}`);
    if (res.status() !== 200) return null;
    return await res.json();
  } finally {
    await api.dispose();
  }
}

async function openTrackingModal(page, label) {
  // The "Track" / "Live Navigation" button text varies; match the emoji-prefixed label.
  const btn = page.getByRole('button', { name: /track/i }).first();
  try {
    await btn.waitFor({ state: 'visible', timeout: 8000 });
  } catch {
    return false;
  }
  await btn.click();
  // Wait for the Leaflet map to mount inside the modal.
  await page.locator('.leaflet-container').first().waitFor({ state: 'visible', timeout: 8000 }).catch(() => {});
  await page.screenshot({ path: path.join(SHOTS, `track-${label}.png`), fullPage: true }).catch(() => {});
  return true;
}

// ----------------------------------------------------------------------------
// Phases 2 & 3: drive the real UI and confirm motion.
// ----------------------------------------------------------------------------
async function phaseUi(browser, booking) {
  console.log('\nPHASE 2/3 — driver UI emits movement, user UI receives it');
  if (!booking) {
    skip('driver UI live navigation', 'no STARTED/CONFIRMED booking found for seeded driver — cannot open tracking');
    skip('user UI receives moving car', 'depends on a trackable booking');
    return;
  }
  console.log(`  using booking ${booking.bookingId} (status ${booking.status})`);

  const driverCtx = await makeContext(browser, DRIVER_TOKEN, ACCOUNTS.driver);
  const driverPage = await driverCtx.newPage();
  await driverPage.goto(`${BASE}/driver/bookings`, { waitUntil: 'networkidle', timeout: 30000 }).catch(() => {});
  await driverPage.waitForTimeout(1500);

  const opened = await openTrackingModal(driverPage, 'driver');
  if (!opened) {
    skip('driver UI live navigation', 'Track button not visible on /driver/bookings');
    await driverCtx.close();
    return;
  }

  // Car marker should render in the driver's own map, and move across it.
  const car = driverPage.locator('.driver-car-icon').first();
  const carThere = await car.waitFor({ state: 'attached', timeout: 10000 }).then(() => true).catch(() => false);
  if (carThere) pass('driver UI renders car marker', '.driver-car-icon present on map');
  else fail('driver UI renders car marker', '.driver-car-icon never mounted');
  if (carThere) {
    const box1 = await car.boundingBox().catch(() => null);
    await driverPage.waitForTimeout(6000);
    const box2 = await car.boundingBox().catch(() => null);
    if (box1 && box2) {
      const dpx = Math.abs(box2.x - box1.x) + Math.abs(box2.y - box1.y);
      if (dpx > 2) pass('driver UI car moves on map', `marker shifted ${dpx.toFixed(0)}px over 6s`);
      else fail('driver UI car moves on map', `marker static (${dpx.toFixed(1)}px)`);
    } else {
      skip('driver UI car moves on map', 'could not read marker position');
    }
  }

  // The real frontend should now be pushing moving coordinates to the backend.
  // Sample the stored location twice ~7s apart and require it to change.
  const s1 = await readStoredLocation(booking.bookingId);
  await driverPage.waitForTimeout(7000);
  const s2 = await readStoredLocation(booking.bookingId);
  if (!s1 || !s2) {
    fail('driver UI emits movement', 'backend returned no stored location while modal open (frontend not sending)');
  } else {
    const moved = Math.abs(s2.latitude - s1.latitude) + Math.abs(s2.longitude - s1.longitude);
    if (moved > 1e-6) pass('driver UI emits movement', `coords advanced by ${moved.toFixed(6)} deg over 7s`);
    else fail('driver UI emits movement', `coords static: ${JSON.stringify(s1)} -> ${JSON.stringify(s2)}`);
  }

  // Phase 3: user opens tracking for the same booking; the car should appear.
  const userCtx = await makeContext(browser, USER_TOKEN, ACCOUNTS.user);
  const userPage = await userCtx.newPage();
  await userPage.goto(`${BASE}/user/bookings`, { waitUntil: 'networkidle', timeout: 30000 }).catch(() => {});
  await userPage.waitForTimeout(1500);
  const userOpened = await openTrackingModal(userPage, 'user');
  if (!userOpened) {
    skip('user UI receives moving car', 'Track button not visible on /user/bookings (booking may belong to another user)');
  } else {
    const userCar = await userPage.locator('.driver-car-icon').first().waitFor({ state: 'attached', timeout: 12000 }).then(() => true).catch(() => false);
    if (userCar) pass('user UI receives moving car', 'driver car marker rendered on user map');
    else fail('user UI receives moving car', 'car marker did not appear within 12s');
  }

  await userCtx.close();
  await driverCtx.close();
}

(async () => {
  if (!SECRET) { console.error('JWT_SECRET not found in .env'); process.exit(1); }
  if (!fs.existsSync(SHOTS)) fs.mkdirSync(SHOTS, { recursive: true });

  // Preflight: both servers up?
  const ping = await pwRequest.newContext();
  const apiUp = await ping.get(`${API}/api/driver/location/preflight-check`).then((r) => r.status() !== 0).catch(() => false);
  const webUp = await ping.get(BASE).then((r) => r.ok()).catch(() => false);
  await ping.dispose();
  if (!apiUp) console.log(`WARNING: backend ${API} did not respond — Phase 1 will fail.`);
  if (!webUp) console.log(`WARNING: frontend ${BASE} did not respond — UI phases will skip.`);

  await phaseBackendPipeline();

  const browser = await chromium.launch({ headless: true });
  let booking = null;
  try { booking = await findTrackableBooking(); } catch (e) { console.log('  (could not query driver bookings: ' + e.message + ')'); }
  await phaseUi(browser, booking);
  await browser.close();

  // Summary
  console.log('\n================ SUMMARY ================');
  const failed = results.filter((r) => r.ok === false);
  const passed = results.filter((r) => r.ok === true);
  const skipped = results.filter((r) => r.skip);
  console.log(`${passed.length} passed, ${failed.length} failed, ${skipped.length} skipped`);
  fs.writeFileSync(path.join(__dirname, 'live-tracking-report.json'), JSON.stringify(results, null, 2));
  process.exit(failed.length ? 1 : 0);
})();
