const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { chromium } = require('playwright');

const ROOT = path.resolve(__dirname, '..');
const SHOTS = path.join(__dirname, 'screenshots');
const BASE = 'http://localhost:5173';

// ---- read secret/expiration from .env ----
const env = {};
for (const line of fs.readFileSync(path.join(ROOT, '.env'), 'utf8').split(/\r?\n/)) {
  const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)$/);
  if (m) env[m[1]] = m[2].trim();
}
const SECRET = env.JWT_SECRET;
const EXP_MS = parseInt(env.JWT_EXPIRATION || '86400000', 10);

function b64url(buf) {
  return Buffer.from(buf).toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}
function mintToken(userId, role) {
  const header = { alg: 'HS256', typ: 'JWT' };
  const now = Math.floor(Date.now() / 1000);
  const payload = { role, userId, sub: userId, iat: now, exp: now + Math.floor(EXP_MS / 1000) };
  const data = b64url(JSON.stringify(header)) + '.' + b64url(JSON.stringify(payload));
  const sig = crypto.createHmac('sha256', SECRET).update(data).digest();
  return data + '.' + b64url(sig);
}

const ACCOUNTS = {
  user: { userId: 'f54882fa-50ff-40b1-86b4-6500d573f5bd', role: 'ROLE_USER', name: 'Demo User', email: 'demo.user@example.com', mobile: '9000000000' },
  owner: { userId: '2', role: 'ROLE_OWNER', name: 'Owner', email: 'nammajourney06@gmail.com', mobile: '' },
  driver: { userId: '10', role: 'ROLE_DRIVER', name: 'Sarathi', email: 'sarasarathy86@gmail.com', mobile: '8220667367' },
};

const PUBLIC_ROUTES = [
  ['00-landing', '/'],
  ['01-login', '/login'],
  ['02-signup', '/signup'],
  ['03-driver-login', '/driver/login'],
  ['04-owner-login', '/owner/login'],
  ['05-package-detail-empty', '/packages/1'],
];
const USER_ROUTES = [
  ['user-00-dashboard', '/user/dashboard'],
  ['user-01-bookings', '/user/bookings'],
  ['user-02-new-booking', '/user/bookings/new'],
  ['user-03-payments', '/user/payments'],
  ['user-04-profile', '/user/profile'],
  ['user-05-package-bookings', '/user/package-bookings'],
];
const DRIVER_ROUTES = [
  ['driver-00-dashboard', '/driver/dashboard'],
  ['driver-01-bookings', '/driver/bookings'],
  ['driver-02-profile', '/driver/profile'],
];
const OWNER_ROUTES = [
  ['owner-00-dashboard', '/owner/dashboard'],
  ['owner-01-bookings', '/owner/bookings'],
  ['owner-02-drivers', '/owner/drivers'],
  ['owner-03-payments', '/owner/payments'],
  ['owner-04-reviews', '/owner/reviews'],
  ['owner-05-revenue', '/owner/revenue'],
  ['owner-06-profile', '/owner/profile'],
  ['owner-07-packages', '/owner/packages'],
  ['owner-08-package-bookings', '/owner/package-bookings'],
];

const report = [];

async function visit(context, name, route) {
  const page = await context.newPage();
  const consoleErrors = [];
  const apiErrors = [];
  const pageErrors = [];
  page.on('console', (msg) => { if (msg.type() === 'error') consoleErrors.push(msg.text().slice(0, 300)); });
  page.on('pageerror', (err) => pageErrors.push(String(err).slice(0, 300)));
  page.on('response', (res) => {
    const u = res.url();
    if (u.includes('/api/') && res.status() >= 400) apiErrors.push(`${res.status()} ${res.request().method()} ${u.replace('http://localhost:8080', '')}`);
  });
  let nav = 'ok';
  try {
    await page.goto(BASE + route, { waitUntil: 'networkidle', timeout: 30000 });
  } catch (e) {
    nav = 'TIMEOUT/' + String(e.message).slice(0, 80);
  }
  await page.waitForTimeout(1500);
  const finalUrl = page.url().replace(BASE, '');
  const title = await page.title().catch(() => '');
  await page.screenshot({ path: path.join(SHOTS, name + '.png'), fullPage: true }).catch(() => {});
  report.push({ name, route, finalUrl, nav, title, consoleErrors, apiErrors, pageErrors });
  console.log(`  [${name}] -> ${finalUrl}  nav=${nav}  apiErr=${apiErrors.length} consoleErr=${consoleErrors.length}`);
  await page.close();
}

(async () => {
  if (!SECRET) { console.error('JWT_SECRET not found in .env'); process.exit(1); }
  const browser = await chromium.launch({ headless: true });

  // ---- PUBLIC ----
  console.log('PUBLIC ROUTES');
  const pub = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  for (const [n, r] of PUBLIC_ROUTES) await visit(pub, n, r);
  await pub.close();

  // ---- AUTHENTICATED ----
  for (const [roleKey, routes] of [['user', USER_ROUTES], ['owner', OWNER_ROUTES], ['driver', DRIVER_ROUTES]]) {
    console.log(roleKey.toUpperCase() + ' ROUTES');
    const acc = ACCOUNTS[roleKey];
    const token = mintToken(acc.userId, acc.role);
    const userObj = { userId: acc.userId, role: acc.role, name: acc.name, email: acc.email, mobile: acc.mobile, message: 'ok' };
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    await ctx.addInitScript(([t, u]) => {
      localStorage.setItem('nj_token', t);
      localStorage.setItem('nj_user', u);
    }, [token, JSON.stringify(userObj)]);
    for (const [n, r] of routes) await visit(ctx, n, r);
    await ctx.close();
  }

  await browser.close();
  fs.writeFileSync(path.join(__dirname, 'report.json'), JSON.stringify(report, null, 2));

  // ---- summary ----
  console.log('\n================ SUMMARY ================');
  let problems = 0;
  for (const r of report) {
    const issues = [];
    if (r.nav !== 'ok') issues.push('NAV:' + r.nav);
    if (r.pageErrors.length) issues.push('PAGEERR:' + r.pageErrors.length);
    if (r.apiErrors.length) issues.push('API:' + r.apiErrors.join(' | '));
    if (r.consoleErrors.length) issues.push('CONSOLE:' + r.consoleErrors.length);
    if (issues.length) { problems++; console.log(`! ${r.name} (${r.finalUrl})\n    ${issues.join('\n    ')}`); }
  }
  console.log(`\n${report.length} pages visited, ${problems} with issues.`);
})();
