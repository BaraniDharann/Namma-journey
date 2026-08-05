/**
 * Live-tracking "every 5 seconds" check.
 *
 * Answers one concrete question: while a trip is live, does the driver location
 * actually update? It samples every 5 seconds and prints whether each interval
 * changed, on two layers:
 *
 *   A. BACKEND STORE  - GET /api/driver/location/{bookingId} every 5s.
 *   B. USER MAP       - the user's tracking modal: does the on-screen car marker
 *      (.driver-car-icon) move between 5s samples?
 *
 * A real driver client must be emitting for either layer to move, so this opens
 * the driver's Live Navigation first (on desktop it simulates driving along the
 * route, exactly like the app does in normal use), then watches as the user.
 *
 * Run:  node location-5s-check.js
 *       node location-5s-check.js <bookingId>     (skip auto-discovery)
 *
 * Needs backend on :8080 and frontend dev on :5173.
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { chromium, request: pwRequest } = require('playwright');

const ROOT = path.resolve(__dirname, '..');
const SHOTS = path.join(__dirname, 'screenshots');
const BASE = 'http://localhost:5173';
const API = 'http://localhost:8080';

const INTERVAL_MS = 5000;   // poll cadence the user asked about
const SAMPLES = 7;          // ~30s of observation

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
  user: { userId: 'f54882fa-50ff-40b1-86b4-6500d573f5bd', role: 'ROLE_USER', name: 'Lokesh Lokesh', email: 'tlokeshthiru123@gmail.com', mobile: '9597965911' },
  driver: { userId: '10', role: 'ROLE_DRIVER', name: 'Sarathi', email: 'sarasarathy86@gmail.com', mobile: '8220667367' },
};

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const DRIVER_TOKEN = mintToken(ACCOUNTS.driver.userId, ACCOUNTS.driver.role);
const USER_TOKEN = mintToken(ACCOUNTS.user.userId, ACCOUNTS.user.role);

async function ensureStarted(driverApi, b) {
  const did = ACCOUNTS.driver.userId;
  if (b.status === 'PENDING') {
    const acc = await driverApi.post(`/api/driver/${did}/bookings/${b.bookingId}/action`, { data: { action: 'ACCEPT' } });
    if (acc.status() !== 200) { console.log(`  accept failed: HTTP ${acc.status()}`); return null; }
    b = await acc.json();
  }
  if (b.status === 'CONFIRMED') {
    const start = await driverApi.post(`/api/driver/${did}/bookings/${b.bookingId}/start-trip`);
    if (start.status() !== 200) { console.log(`  start-trip failed: HTTP ${start.status()}`); return null; }
    b = await start.json();
  }
  return b.status === 'STARTED' ? b : null;
}

async function findTrackableBooking() {
  const driverApi = await pwRequest.newContext({ baseURL: API, extraHTTPHeaders: { Authorization: `Bearer ${DRIVER_TOKEN}` } });
  try {
    const res = await driverApi.get(`/api/driver/${ACCOUNTS.driver.userId}/bookings`);
    if (res.status() !== 200) { console.log(`  driver bookings HTTP ${res.status()}`); return null; }
    const bookings = await res.json();
    if (!bookings.length) return null;
    const order = { STARTED: 0, CONFIRMED: 1, PENDING: 2 };
    const candidate = bookings.filter((x) => x.status in order).sort((a, b) => order[a.status] - order[b.status])[0];
    if (!candidate) return null;
    return await ensureStarted(driverApi, candidate);
  } finally {
    await driverApi.dispose();
  }
}

async function readStoredLocation(bookingId) {
  const api = await pwRequest.newContext({ baseURL: API, extraHTTPHeaders: { Authorization: `Bearer ${USER_TOKEN}` } });
  try {
    const res = await api.get(`/api/driver/location/${bookingId}`);
    if (res.status() !== 200) return { error: `HTTP ${res.status()}` };
    return await res.json();
  } catch (e) {
    return { error: e.message };
  } finally {
    await api.dispose();
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

async function openTrackingModal(page, label) {
  const btn = page.getByRole('button', { name: /track/i }).first();
  try { await btn.waitFor({ state: 'visible', timeout: 8000 }); } catch { return false; }
  await btn.click();
  await page.locator('.leaflet-container').first().waitFor({ state: 'visible', timeout: 8000 }).catch(() => {});
  await page.screenshot({ path: path.join(SHOTS, `5s-${label}.png`), fullPage: true }).catch(() => {});
  return true;
}

(async () => {
  if (!SECRET) { console.error('JWT_SECRET not found in .env'); process.exit(1); }
  if (!fs.existsSync(SHOTS)) fs.mkdirSync(SHOTS, { recursive: true });

  let bookingId = process.argv[2];
  if (!bookingId) {
    console.log('Discovering a trackable booking for the seeded driver...');
    const b = await findTrackableBooking();
    if (!b) {
      console.error('\nNo STARTED/CONFIRMED/PENDING booking available for the seeded driver.');
      console.error('Pass one explicitly:  node location-5s-check.js <bookingId>');
      process.exit(2);
    }
    bookingId = b.bookingId;
    console.log(`Using booking ${bookingId} (status ${b.status})\n`);
  } else {
    console.log(`Using booking ${bookingId} (from argv)\n`);
  }

  const browser = await chromium.launch({ headless: true });

  // 1) Driver opens Live Navigation so the app emits movement (as in real use).
  const driverCtx = await makeContext(browser, DRIVER_TOKEN, ACCOUNTS.driver);
  const driverPage = await driverCtx.newPage();
  await driverPage.goto(`${BASE}/driver/bookings`, { waitUntil: 'networkidle', timeout: 30000 }).catch(() => {});
  await driverPage.waitForTimeout(1500);
  const driverOpened = await openTrackingModal(driverPage, 'driver');
  console.log(driverOpened
    ? 'Driver Live Navigation opened — app is now emitting location.\n'
    : 'WARNING: could not open driver tracking UI; backend may have no fresh updates.\n');

  // 2) User opens tracking for the same booking (optional; skipped if no button).
  const userCtx = await makeContext(browser, USER_TOKEN, ACCOUNTS.user);
  const userPage = await userCtx.newPage();
  await userPage.goto(`${BASE}/user/bookings`, { waitUntil: 'networkidle', timeout: 30000 }).catch(() => {});
  await userPage.waitForTimeout(1500);
  const userOpened = await openTrackingModal(userPage, 'user');
  const userCar = userOpened
    ? await userPage.locator('.driver-car-icon').first()
    : null;
  console.log(userOpened
    ? 'User tracking modal opened — will watch the on-screen car marker.\n'
    : 'NOTE: user Track button not visible (booking may belong to another user); watching backend store only.\n');

  // 3) Sample every 5 seconds.
  console.log(`Polling every ${INTERVAL_MS / 1000}s for ${SAMPLES} samples (~${(SAMPLES - 1) * INTERVAL_MS / 1000}s total):\n`);
  console.log('  #   t(s)   backend(lat,lon)               Δstore     userMarkerΔpx');
  console.log('  --  -----  -----------------------------  ---------  -------------');

  const samples = [];
  let prevStore = null, prevBox = null;
  const t0 = Date.now();
  for (let i = 0; i < SAMPLES; i++) {
    const store = await readStoredLocation(bookingId);
    const box = userCar ? await userCar.boundingBox().catch(() => null) : null;

    let dStore = '—';
    if (!store.error && prevStore && !prevStore.error) {
      const d = Math.abs(store.latitude - prevStore.latitude) + Math.abs(store.longitude - prevStore.longitude);
      dStore = d.toFixed(6);
    }
    let dPx = userCar ? '—' : 'n/a';
    if (box && prevBox) dPx = (Math.abs(box.x - prevBox.x) + Math.abs(box.y - prevBox.y)).toFixed(0);

    const t = ((Date.now() - t0) / 1000).toFixed(0);
    const coords = store.error ? `ERR ${store.error}` : `${store.latitude.toFixed(5)}, ${store.longitude.toFixed(5)}`;
    console.log(
      `  ${String(i).padStart(2)}  ${String(t).padStart(5)}  ${coords.padEnd(29)}  ${String(dStore).padStart(9)}  ${String(dPx).padStart(13)}`
    );

    samples.push({ i, t: Number(t), store, dStore, dPx });
    prevStore = store;
    prevBox = box;
    if (i < SAMPLES - 1) await sleep(INTERVAL_MS);
  }

  // 4) Verdict.
  const valid = samples.filter((s) => !s.store.error);
  const storeChanges = samples.filter((s) => s.dStore !== '—' && Number(s.dStore) > 1e-6).length;
  const storeIntervals = samples.filter((s) => s.dStore !== '—').length;
  const carChanges = samples.filter((s) => s.dPx !== '—' && s.dPx !== 'n/a' && Number(s.dPx) > 2).length;
  const carIntervals = samples.filter((s) => s.dPx !== '—' && s.dPx !== 'n/a').length;

  console.log('\n================ VERDICT ================');
  if (!valid.length) {
    console.log('FAIL  backend returned no location at all — nothing is being stored for this booking.');
  } else if (storeChanges === 0) {
    console.log(`FAIL  backend location is STATIC across ${storeIntervals} five-second intervals — it never updated.`);
  } else {
    console.log(`PASS  backend location updated in ${storeChanges}/${storeIntervals} five-second intervals.`);
  }
  if (userOpened) {
    if (carChanges === 0) console.log(`FAIL  user map car marker did NOT move across ${carIntervals} intervals.`);
    else console.log(`PASS  user map car marker moved in ${carChanges}/${carIntervals} intervals.`);
  } else {
    console.log('SKIP  user map check (Track button not available for the seeded user on this booking).');
  }

  const report = {
    bookingId, intervalMs: INTERVAL_MS, samples: SAMPLES,
    driverOpened, userOpened,
    storeChanges, storeIntervals, carChanges, carIntervals,
    data: samples,
  };
  fs.writeFileSync(path.join(__dirname, 'location-5s-report.json'), JSON.stringify(report, null, 2));
  console.log('\nDetail written to e2e-test/location-5s-report.json');

  await userCtx.close();
  await driverCtx.close();
  await browser.close();

  const backendOk = valid.length && storeChanges > 0;
  process.exit(backendOk ? 0 : 1);
})();
