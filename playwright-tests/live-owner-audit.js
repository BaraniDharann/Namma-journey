// Visible walk through every owner module on the LIVE site, for a usability review.
//   node live-owner-audit.js <outDir>
// Credentials come from env (OWNER_LOGIN, OWNER_PASSWORD); nothing is written to the site.
const { chromium } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const BASE = process.env.BASE_URL || 'https://nammajourneys.site';
const OUT = process.argv[2] || 'audit-out';
fs.mkdirSync(OUT, { recursive: true });

(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: false, slowMo: 250 });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  const problems = [];
  page.on('console', (m) => { if (m.type() === 'error') problems.push(`[console] ${page.url()} ${m.text()}`); });
  page.on('response', (r) => {
    if (r.url().includes('/api/') && r.status() >= 400) problems.push(`[http ${r.status()}] ${page.url()} -> ${r.request().method()} ${r.url()}`);
  });

  await page.goto(`${BASE}/owner/login`);
  await page.locator('input[name="username"]').fill(process.env.OWNER_LOGIN);
  await page.locator('input[autocomplete="current-password"]').fill(process.env.OWNER_PASSWORD);
  await page.locator('form button[type="submit"]').last().click();
  await page.waitForURL(/\/owner\/(dashboard|change-password)/, { timeout: 30000 });
  await page.waitForTimeout(2500);

  const links = await page.$$eval('nav.pc-nav a', (as) => as.map((a) => ({ href: a.getAttribute('href'), label: a.textContent.trim() })));
  fs.writeFileSync(path.join(OUT, 'nav.json'), JSON.stringify(links, null, 2));

  for (const [i, l] of links.entries()) {
    await page.locator(`nav.pc-nav a[href="${l.href}"]`).click();
    await page.waitForURL((u) => u.pathname === l.href, { timeout: 20000 }).catch(() => {});
    await page.waitForLoadState('networkidle').catch(() => {});
    await page.waitForTimeout(1200);
    const name = `${String(i).padStart(2, '0')}-${l.href.replace(/\W+/g, '_')}.png`;
    await page.screenshot({ path: path.join(OUT, name), fullPage: true });
    console.log('visited', l.href, l.label);
  }

  // Phone width pass over the same screens.
  await page.setViewportSize({ width: 390, height: 844 });
  for (const [i, l] of links.entries()) {
    await page.goto(`${BASE}${l.href}`);
    await page.waitForLoadState('networkidle').catch(() => {});
    await page.waitForTimeout(800);
    await page.screenshot({ path: path.join(OUT, `m${String(i).padStart(2, '0')}-${l.href.replace(/\W+/g, '_')}.png`), fullPage: false });
  }

  fs.writeFileSync(path.join(OUT, 'problems.txt'), [...new Set(problems)].join('\n'));
  console.log(`done: ${links.length} modules, ${new Set(problems).size} problems`);
  await page.waitForTimeout(1500);
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
