import { test, expect, Browser, BrowserContext, Page, TestInfo } from '@playwright/test';
import { readAccounts } from '../helpers/env';
import { withApiIp, watch, summarize } from '../helpers/browser';

/**
 * The traveller dashboard opens on an animated road trip (RoadTripStory.jsx): four friends plan
 * a weekend beside the car, climb in with the driver, drive past the Namma Journey billboard,
 * arrive somewhere green and take a selfie that lands in a polaroid. One loop is ~19s.
 *
 * These specs sign in through the real form and watch the loop play in real time — the story
 * runs off requestAnimationFrame, so a faked clock would test the fake, not the page. Each stage
 * is attached to the HTML report as a screenshot, so a run doubles as a visual record.
 */

const acc = readAccounts();

/** One full loop plus headroom: a stage we just missed comes round again within this. */
const LOOP = 25_000;

async function signedIn(browser: Browser, label: string, opts: Parameters<Browser['newContext']>[0] = {}) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, ...opts });
  await withApiIp(ctx, label);
  const page = await ctx.newPage();
  const problems = watch(page, '/user/dashboard');
  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await page.getByPlaceholder('you@example.com').first().fill(acc.user.email);
  await page.locator('form input[type="password"]').first().fill(acc.user.password);
  await page.locator('form button[type="submit"]').first().click();
  await page.waitForURL('**/user/dashboard', { timeout: 30_000 });
  return { ctx, page, problems };
}

async function snap(page: Page, info: TestInfo, name: string) {
  await info.attach(name, { body: await page.locator('.rts').screenshot(), contentType: 'image/png' });
}

const chip = (page: Page) => page.locator('.rts-chip');
const opacityOf = (page: Page, sel: string) =>
  page.locator(sel).first().evaluate((el) => Number(getComputedStyle(el).opacity));
const progressOf = (page: Page) =>
  page.locator('.rts-progress').evaluate((el) => (el as HTMLElement).style.transform);

test.describe('Dashboard road-trip story', () => {
  let ctx: BrowserContext;
  test.afterEach(async () => {
    await ctx?.close();
  });

  test('plays the whole trip: plan, board, drive past the brand, arrive, selfie', async ({ browser }, info) => {
    test.setTimeout(LOOP * 4);
    const s = await signedIn(browser, 'story-full');
    ctx = s.ctx;
    const { page, problems } = s;

    const story = page.locator('section.rts');
    await expect(story).toBeVisible();
    await expect(story.locator('svg[role="img"]')).toHaveAttribute('aria-label', /Namma Journey driver/);

    // 1. Four friends talking beside the car.
    await expect(chip(page)).toHaveText('Planning the weekend', { timeout: LOOP });
    await expect(story.getByText('Weekend trip?')).toBeAttached();
    await page.waitForTimeout(1200);
    await snap(page, info, '1-planning');

    // 2. They climb in; the driver is already at the wheel.
    await expect(chip(page)).toHaveText('Everyone in', { timeout: LOOP });
    await page.waitForTimeout(1500);
    await snap(page, info, '2-boarding');

    // 3. On the road: all four friends and the driver are behind the glass, wheels turning.
    await expect(chip(page)).toHaveText('On the road', { timeout: LOOP });
    await expect(story.locator('svg.rts-driving')).toBeAttached();
    await expect
      .poll(() => story.locator('.rts-seat').evaluateAll((els) => els.filter((e) => getComputedStyle(e).opacity === '1').length))
      .toBe(5);

    // The billboard wipes the brand in: its clip grows to the full sign width. framer-motion
    // writes the animated width with a unit ("320px"), hence parseFloat rather than Number.
    await expect
      .poll(
        () => story.locator('clipPath rect[height="84"]').evaluate((r) => parseFloat(r.getAttribute('width') ?? getComputedStyle(r).width)),
        { timeout: 8_000 }
      )
      .toBeGreaterThan(300);
    await expect(story.locator('text', { hasText: 'Namma Journey' }).first()).toBeAttached();
    await snap(page, info, '3-billboard');

    // 4. Arrival: the destination photograph takes over.
    const arrived = await expect(chip(page)).toHaveText(/^Arrived at /, { timeout: LOOP }).then(() => chip(page).innerText());
    const place = arrived.replace('Arrived at ', '');
    await expect(story.locator('image')).toHaveAttribute('href', /travel%20places\/.+\.webp$/);
    await page.waitForTimeout(1500);
    await snap(page, info, '4-arrived');

    // 5. Selfie: phone up, then the flash, then the polaroid with hearts rising.
    await expect(chip(page)).toHaveText('Say cheese!', { timeout: LOOP });
    await expect.poll(() => opacityOf(page, '.rts-frame'), { timeout: 5_000 }).toBeGreaterThan(0.95);
    await expect(story.locator('.rts-heart')).toHaveCount(5);
    await expect(story.locator('.rts-frame-cap')).toContainText(place);
    await expect(story.locator('.rts-frame-cap')).toContainText('Namma Journey');
    await page.waitForTimeout(600);
    await snap(page, info, '5-polaroid');

    // 6. The loop comes round again, to the next destination.
    await expect(chip(page)).toHaveText('Planning the weekend', { timeout: LOOP });
    await expect.poll(() => opacityOf(page, '.rts-frame')).toBeLessThan(0.05);

    expect(problems.pageErrors, summarize(problems)).toEqual([]);
    expect(problems.failedApi, summarize(problems)).toEqual([]);
  });

  test('the pause button freezes the story and play resumes it', async ({ browser }, info) => {
    const s = await signedIn(browser, 'story-pause');
    ctx = s.ctx;
    const { page } = s;

    const pause = page.getByRole('button', { name: 'Pause the trip animation' });
    await expect(pause).toBeVisible();
    await page.waitForTimeout(1500);
    await pause.click();

    const play = page.getByRole('button', { name: 'Play the trip animation' });
    await expect(play).toHaveAttribute('aria-pressed', 'true');
    const frozen = await progressOf(page);
    await page.waitForTimeout(2000);
    expect(await progressOf(page), 'nothing may move while paused').toBe(frozen);
    await snap(page, info, 'paused');

    await play.click();
    await expect(pause).toHaveAttribute('aria-pressed', 'false');
    await expect.poll(() => progressOf(page)).not.toBe(frozen);
  });

  test('reduced motion shows the happy polaroid as a still, with no controls', async ({ browser }, info) => {
    const s = await signedIn(browser, 'story-reduced', { reducedMotion: 'reduce' });
    ctx = s.ctx;
    const { page } = s;

    await expect(page.locator('section.rts')).toBeVisible();
    await expect.poll(() => opacityOf(page, '.rts-frame')).toBeGreaterThan(0.95);
    await expect(page.locator('.rts-frame-cap')).toContainText('Kashmir Valley');
    await expect(page.locator('.rts-ctrl')).toHaveCount(0);
    await expect(page.locator('.rts-progress')).toHaveCount(0);

    const before = await page.locator('.rts').screenshot();
    await page.waitForTimeout(2000);
    expect(Buffer.compare(before, await page.locator('.rts').screenshot()), 'a reduced-motion still must not change').toBe(0);
    await snap(page, info, 'reduced-motion');
  });

  test('on a phone the scene crops to the friends and the car without overflowing', async ({ browser }, info) => {
    const s = await signedIn(browser, 'story-mobile', { viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
    ctx = s.ctx;
    const { page } = s;

    const svg = page.locator('section.rts svg[role="img"]');
    await expect(svg).toHaveAttribute('viewBox', '286 0 704 380');
    const box = await page.locator('section.rts').boundingBox();
    expect(box!.width).toBeLessThanOrEqual(390);
    expect(await page.evaluate(() => document.documentElement.scrollWidth), 'no sideways page scroll').toBeLessThanOrEqual(390);

    await expect(chip(page)).toHaveText('Planning the weekend', { timeout: LOOP });
    await page.waitForTimeout(1200);
    await snap(page, info, 'mobile-planning');
  });
});
