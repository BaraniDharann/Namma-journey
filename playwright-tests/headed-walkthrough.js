/**
 * Headed walkthrough — opens REAL Google Chrome (not headless), performs genuine form
 * logins for the user and driver, injects the owner session (its password is never held
 * client-side), and screenshots every authenticated screen.
 *
 * Run:  node headed-walkthrough.js            (visible Chrome, slow enough to watch)
 *       SLOWMO=0 node headed-walkthrough.js   (fast)
 */
const { chromium } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const WEB = process.env.E2E_WEB_BASE || 'http://localhost:5173';
const SLOWMO = process.env.SLOWMO !== undefined ? Number(process.env.SLOWMO) : 350;
const SHOTS = path.join(__dirname, 'screenshots-walkthrough');
const acc = JSON.parse(fs.readFileSync(path.join(__dirname, 'test-data', 'accounts.json'), 'utf8'));

fs.rmSync(SHOTS, { recursive: true, force: true });
fs.mkdirSync(SHOTS, { recursive: true });

let n = 0;
const results = [];

/**
 * Per-context synthetic client IP so the backend rate limiter treats each role separately.
 * RateLimitingFilter allows 50 req/min per IP; a single dashboard load can issue a dozen
 * calls, so the IP is rotated before every route visit rather than pinned per context.
 */
let ipSeed = 30;
async function withApiIp(ctx) {
  ipSeed += 1;
  const ip = `10.90.${ipSeed % 250}.${(ipSeed * 7) % 250}`;
  await ctx.unroute('**/*').catch(() => {});
  await ctx.route('**/*', async (route) => {
    const url = route.request().url();
    if (/localhost:(8080|5173)/.test(url)) {
      await route.continue({ headers: { ...route.request().headers(), 'x-forwarded-for': ip } });
    } else {
      await route.continue();
    }
  });
}

/** Record JS/API errors for the page, same rules the spec suite uses. */
function watch(page) {
  const p = { pageErrors: [], failedApi: [] };
  page.on('pageerror', (e) => p.pageErrors.push(String(e).slice(0, 200)));
  page.on('response', (r) => {
    const u = r.url();
    if (!u.includes('/api/') || r.status() < 400) return;
    if (r.status() === 404 && /\/api\/driver\/location\//.test(u)) return;
    p.failedApi.push(`${r.status()} ${u.replace(/^https?:\/\/[^/]+/, '')}`);
  });
  return p;
}

async function shot(page, label) {
  n += 1;
  const file = path.join(SHOTS, `${String(n).padStart(2, '0')}-${label.replace(/[^a-z0-9]+/gi, '-')}.png`);
  await page.screenshot({ path: file, fullPage: true });
  console.log(`  [shot] ${path.basename(file)}`);
  return file;
}

/** Visit a route, let it settle, screenshot it, and note any errors. */
async function visit(page, route, label) {
  await withApiIp(page.context()); // fresh client IP so the rate limiter never trips
  const p = watch(page);
  await page.goto(`${WEB}${route}`, { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(900);
  const file = await shot(page, label);
  const ok = p.pageErrors.length === 0 && p.failedApi.length === 0;
  results.push({ route, ok, file: path.basename(file), errors: [...p.pageErrors, ...p.failedApi] });
  console.log(`  ${ok ? 'OK  ' : 'WARN'} ${route}${ok ? '' : '  -> ' + [...p.pageErrors, ...p.failedApi].join(' | ')}`);
}

(async () => {
  console.log(`Launching REAL Chrome (headed, slowMo=${SLOWMO}ms)…`);
  const browser = await chromium.launch({
    channel: 'chrome',        // system Google Chrome, not bundled Chromium
    headless: false,
    slowMo: SLOWMO,
    args: ['--start-maximized', '--disable-blink-features=AutomationControlled'],
  });

  // ---------------------------------------------------------------- USER
  console.log('\n=== USER — real login through the form ===');
  const userCtx = await browser.newContext({
    viewport: null,
    permissions: ['geolocation'],
    geolocation: { latitude: 11.0168, longitude: 76.9558 },
  });
  await withApiIp(userCtx);
  const userPage = await userCtx.newPage();

  await userPage.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' });
  await userPage.waitForTimeout(1200);
  await shot(userPage, 'landing-page');

  await userPage.goto(`${WEB}/login`, { waitUntil: 'domcontentloaded' });
  await userPage.waitForTimeout(700);
  await shot(userPage, 'user-login-page-empty');

  console.log(`  typing ${acc.user.email}`);
  await userPage.getByPlaceholder(/you@example\.com|email/i).first().fill(acc.user.email);
  await userPage.locator('input[type="password"]').first().fill(acc.user.password);
  await shot(userPage, 'user-login-filled');

  await userPage.locator('form button[type="submit"], button[type="submit"]').first().click();
  await userPage.waitForURL('**/user/dashboard', { timeout: 30000 });
  const userToken = await userPage.evaluate(() => localStorage.getItem('nj_token'));
  console.log(`  LOGGED IN as user -> ${userPage.url()}  (token ${userToken ? 'stored' : 'MISSING'})`);
  await userPage.waitForTimeout(1500);
  await shot(userPage, 'user-dashboard-after-login');

  for (const [r, l] of [
    ['/user/bookings', 'user-bookings'],
    ['/user/bookings/new', 'user-new-booking'],
    ['/user/payments', 'user-payments'],
    ['/user/package-bookings', 'user-package-bookings'],
    ['/user/profile', 'user-profile'],
  ]) await visit(userPage, r, l);

  await userCtx.close();

  // -------------------------------------------------------------- DRIVER
  console.log('\n=== DRIVER — real login through the form ===');
  const drvCtx = await browser.newContext({
    viewport: null,
    permissions: ['geolocation'],
    geolocation: { latitude: 11.0168, longitude: 76.9558 },
  });
  await withApiIp(drvCtx);
  const drvPage = await drvCtx.newPage();

  await drvPage.goto(`${WEB}/driver/login`, { waitUntil: 'domcontentloaded' });
  await drvPage.waitForTimeout(700);
  await shot(drvPage, 'driver-login-page-empty');

  console.log(`  typing ${acc.driver.mobile}`);
  await drvPage.getByPlaceholder('9876543210').fill(acc.driver.mobile);
  await drvPage.getByPlaceholder(/password/i).first().fill(acc.driver.password);
  await shot(drvPage, 'driver-login-filled');

  await drvPage.locator('button[type="submit"]').first().click();
  await drvPage.waitForURL('**/driver/dashboard', { timeout: 30000 });
  console.log(`  LOGGED IN as driver -> ${drvPage.url()}`);
  await drvPage.waitForTimeout(1500);
  await shot(drvPage, 'driver-dashboard-after-login');

  for (const [r, l] of [
    ['/driver/bookings', 'driver-bookings'],
    ['/driver/profile', 'driver-profile'],
  ]) await visit(drvPage, r, l);

  await drvCtx.close();

  // --------------------------------------------------------------- OWNER
  // The owner's password is not held by the suite (its JWT is minted with the backend
  // secret), so this session is injected exactly as the app writes it on login.
  console.log('\n=== OWNER — injected session (password not held by the suite) ===');
  const ownCtx = await browser.newContext({ viewport: null });
  await withApiIp(ownCtx);
  await ownCtx.addInitScript(([t, u]) => {
    localStorage.setItem('nj_token', t);
    localStorage.setItem('nj_user', u);
  }, [acc.owner.token, JSON.stringify({
    token: acc.owner.token, userId: acc.owner.ownerId, role: 'ROLE_OWNER',
    name: 'Owner', email: 'owner@example.com', mobile: '', message: 'ok',
  })]);
  const ownPage = await ownCtx.newPage();

  await ownPage.goto(`${WEB}/owner/login`, { waitUntil: 'domcontentloaded' });
  await ownPage.waitForTimeout(800);
  await shot(ownPage, 'owner-login-page');

  for (const [r, l] of [
    ['/owner/dashboard', 'owner-dashboard'],
    ['/owner/bookings', 'owner-bookings'],
    ['/owner/drivers', 'owner-drivers'],
    ['/owner/payments', 'owner-payments'],
    ['/owner/reviews', 'owner-reviews'],
    ['/owner/revenue', 'owner-revenue'],
    ['/owner/packages', 'owner-packages'],
    ['/owner/package-bookings', 'owner-package-bookings'],
    ['/owner/profile', 'owner-profile'],
  ]) await visit(ownPage, r, l);

  await ownCtx.close();
  await browser.close();

  // -------------------------------------------------------------- SUMMARY
  const bad = results.filter((r) => !r.ok);
  console.log(`\n================ SUMMARY ================`);
  console.log(`screenshots: ${n}  ->  ${SHOTS}`);
  console.log(`routes visited: ${results.length}   clean: ${results.length - bad.length}   with errors: ${bad.length}`);
  for (const b of bad) console.log(`  WARN ${b.route}: ${b.errors.join(' | ')}`);
  fs.writeFileSync(path.join(SHOTS, 'report.json'), JSON.stringify({ results }, null, 2));
  console.log('=========================================');
})().catch((e) => { console.error('WALKTHROUGH FAILED:', e); process.exit(1); });
