const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { chromium } = require('playwright');

const ROOT = path.resolve(__dirname, '..');
const SHOTS = path.join(__dirname, 'screenshots');
const BASE = 'http://localhost:5173';

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
  const data = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' })) + '.' + b64url(JSON.stringify({ role, userId, sub: userId, iat: now, exp: now + Math.floor(EXP_MS / 1000) }));
  return data + '.' + b64url(crypto.createHmac('sha256', SECRET).update(data).digest());
}

const JOBS = [
  { role: 'ROLE_OWNER', acc: { userId: '2', name: 'Owner', email: 'nammajourney06@gmail.com', mobile: '' }, name: 'rerun-owner-revenue', route: '/owner/revenue' },
  { role: 'ROLE_DRIVER', acc: { userId: '10', name: 'Sarathi', email: 'sarasarathy86@gmail.com', mobile: '8220667367' }, name: 'rerun-driver-dashboard', route: '/driver/dashboard' },
  { role: 'ROLE_DRIVER', acc: { userId: '10', name: 'Sarathi', email: 'sarasarathy86@gmail.com', mobile: '8220667367' }, name: 'rerun-driver-profile', route: '/driver/profile' },
];

(async () => {
  const browser = await chromium.launch({ headless: true });
  for (const job of JOBS) {
    const token = mintToken(job.acc.userId, job.role);
    const userObj = { userId: job.acc.userId, role: job.role, name: job.acc.name, email: job.acc.email, mobile: job.acc.mobile, message: 'ok' };
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    await ctx.addInitScript(([t, u]) => { localStorage.setItem('nj_token', t); localStorage.setItem('nj_user', u); }, [token, JSON.stringify(userObj)]);
    const page = await ctx.newPage();
    const apiErrors = [];
    page.on('response', (res) => { if (res.url().includes('/api/') && res.status() >= 400) apiErrors.push(`${res.status()} ${res.url().replace('http://localhost:8080', '')}`); });
    await page.goto(BASE + job.route, { waitUntil: 'networkidle', timeout: 30000 }).catch(() => {});
    await page.waitForTimeout(2000);
    await page.screenshot({ path: path.join(SHOTS, job.name + '.png'), fullPage: true });
    console.log(`[${job.name}] apiErrors=${apiErrors.length} ${apiErrors.join(' | ')}`);
    await ctx.close();
    await new Promise((r) => setTimeout(r, 8000)); // space out to stay under 50/min
  }
  await browser.close();
})();
