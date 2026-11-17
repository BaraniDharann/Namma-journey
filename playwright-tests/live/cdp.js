// Drives the three visible Chrome windows of the live walkthrough over CDP. Each window is its
// own Chrome profile (separate logins), started by start-windows.ps1, and stays open between steps.
//   const { page, done, shot } = await open('owner')
const { chromium } = require('@playwright/test');
const path = require('path');
const fs = require('fs');

const PORTS = { owner: 9301, user: 9302, driver: 9303, admin: 9304 };
const BASE = process.env.BASE_URL || 'https://nammajourneys.site';
const SHOTS = process.env.SHOTS || path.join(__dirname, 'shots');
fs.mkdirSync(SHOTS, { recursive: true });

async function open(who) {
  const browser = await chromium.connectOverCDP(`http://127.0.0.1:${PORTS[who]}`);
  const context = browser.contexts()[0];
  const page = context.pages().find((p) => p.url().startsWith('http')) || context.pages()[0] || await context.newPage();
  page.setDefaultTimeout(20000);
  const problems = [];
  page.on('response', (r) => { if (r.url().includes('/api/') && r.status() >= 400) problems.push(`${r.status()} ${r.request().method()} ${r.url().replace(BASE, '')}`); });
  let n = 0;
  const shot = async (name) => {
    const file = path.join(SHOTS, `${who}-${String(Date.now()).slice(-6)}-${name || n++}.png`);
    await page.screenshot({ path: file });
    console.log('shot', file);
    return file;
  };
  const done = async () => {
    if (problems.length) console.log('api problems:', [...new Set(problems)].join(' | '));
    await browser.close().catch(() => {}); // over CDP this only disconnects; the window stays
  };
  return { browser, context, page, shot, done, BASE, problems };
}

/** Run `fn(ctx)` against a window; on failure, screenshot and print the visible errors. */
async function step(who, fn) {
  const ctx = await open(who);
  try {
    await fn(ctx);
  } catch (e) {
    console.log('STEP FAILED:', e.message.split('\n')[0]);
    const errs = await ctx.page.locator('.pc-error, [role=alert], .alert-error').allInnerTexts().catch(() => []);
    if (errs.length) console.log('on-screen errors:', errs.join(' | '));
    await ctx.shot('fail');
    process.exitCode = 1;
  } finally {
    await ctx.done();
  }
}

module.exports = { open, step, BASE, PORTS };
