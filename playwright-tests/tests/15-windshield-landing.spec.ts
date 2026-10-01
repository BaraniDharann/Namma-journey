import { test, expect, Browser, Page, TestInfo } from '@playwright/test';
import { readAccounts } from '../helpers/env';
import { withApiIp, watch, summarize } from '../helpers/browser';
import { contexts, disposeAll, bookingPayload } from '../helpers/booking';

/**
 * The landing page, its car-assembly intro, the memory album, and the celebration popups.
 *
 *  - The intro builds the car, then gets out of the way, once per browser session.
 *  - The hero is a game view: the Namma Journey car cruises by itself through five places
 *    (valley, waterfall, beach, backwaters, temple town) while a caption names the one it passes;
 *    the booking form sits beside it (below it on phones).
 *  - The memory album: at each stop the friends pose, the driver takes the photo, and the
 *    polaroid flies into the album, which counts up.
 *  - A successful form ends in a celebration popup; a UPI payment celebrates in two steps:
 *    "Payment sent" when the traveller taps "I've paid", "Payment confirmed" once the owner has
 *    verified it (the app polls while open).
 */

const acc = readAccounts();

async function snap(page: Page, info: TestInfo, name: string) {
  await info.attach(name, { body: await page.screenshot(), contentType: 'image/png' });
}

async function skipIntro(ctx: { addInitScript: (fn: () => void) => Promise<void> }) {
  await ctx.addInitScript(() => sessionStorage.setItem('nj_intro_seen', '1'));
}

async function signedInTraveller(browser: Browser, label: string) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await withApiIp(ctx, label);
  const page = await ctx.newPage();
  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await page.getByPlaceholder('you@example.com').first().fill(acc.user.email);
  await page.locator('form input[type="password"]').first().fill(acc.user.password);
  await page.locator('form button[type="submit"]').first().click();
  await page.waitForURL('**/user/dashboard', { timeout: 30_000 });
  return { ctx, page };
}

test.describe('Landing — the car builds itself', () => {
  test('the intro plays on the first visit, then reveals the page', async ({ browser }, info) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await ctx.newPage();
    const problems = watch(page, '/');
    await page.goto('/', { waitUntil: 'domcontentloaded' });

    const intro = page.locator('.nj-intro');
    await expect(intro).toBeVisible({ timeout: 20_000 });
    await expect(intro).toHaveAttribute('aria-label', 'Loading Namma Journey');
    await page.waitForTimeout(1600);
    await snap(page, info, 'intro-assembling');
    await expect(intro).toBeHidden({ timeout: 15_000 });
    await expect(page.locator('#rw-title')).toContainText('Leave the traffic.');
    await snap(page, info, 'landing-hero');

    // Same session, second visit: straight to the page.
    await page.reload({ waitUntil: 'domcontentloaded' });
    await expect(page.locator('#rw-title')).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('.nj-intro')).toHaveCount(0);

    expect(problems.pageErrors, summarize(problems)).toEqual([]);
    await ctx.close();
  });

  test('clicking the intro skips it', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    const intro = page.locator('.nj-intro');
    await expect(intro).toBeVisible({ timeout: 20_000 });
    await intro.click();
    await expect(intro).toBeHidden({ timeout: 3_000 });
    await ctx.close();
  });
});

test.describe('Landing — the car on the road', () => {
  test('the car cruises by itself through the places, with booking beside it', async ({ browser }, info) => {
    test.setTimeout(120_000);
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    await skipIntro(ctx);
    const page = await ctx.newPage();
    const problems = watch(page, '/');
    await page.goto('/', { waitUntil: 'domcontentloaded' });

    await expect(page.locator('#drive canvas'), 'the 3D road renders').toBeVisible({ timeout: 30_000 });
    // The hero says one thing in two lines, with one primary action.
    await expect(page.locator('#rw-title')).toHaveText('Leave the traffic.Keep the journey.');
    await expect(page.locator('#drive').getByRole('button', { name: 'Book a ride' })).toBeVisible();
    await expect(page.locator('#book').getByText('Quick booking')).toBeVisible();

    // Nobody scrolls, yet the car moves on: the caption names the next place within a segment.
    const caption = page.locator('.rw-place b');
    await expect(caption).toHaveText('Kashmir Valley', { timeout: 15_000 });
    await snap(page, info, 'cruise-valley');
    await expect(caption, 'the car drives on by itself').not.toHaveText('Kashmir Valley', { timeout: 45_000 });
    await snap(page, info, 'cruise-next-place');

    // "Book a ride" takes you to the form.
    await page.locator('#drive').getByRole('button', { name: 'Book a ride' }).click();
    await expect(page.locator('#book')).toBeInViewport();

    expect(problems.pageErrors, summarize(problems)).toEqual([]);
    expect(problems.failedApi, summarize(problems)).toEqual([]);
    await ctx.close();
  });

  test('at phone width the road stays visible and the form follows it', async ({ browser }, info) => {
    const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
    await skipIntro(ctx);
    const page = await ctx.newPage();
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('#rw-title')).toBeVisible({ timeout: 30_000 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth), 'no sideways scroll').toBeLessThanOrEqual(1);
    // The form is its own section after the scene, not on top of it.
    const heroBottom = await page.locator('#drive').evaluate((el) => el.getBoundingClientRect().bottom);
    const formTop = await page.locator('#book').evaluate((el) => el.getBoundingClientRect().top);
    expect(formTop).toBeGreaterThanOrEqual(heroBottom - 1);
    await snap(page, info, 'phone-hero');
    await page.locator('#drive').getByRole('button', { name: 'Book a ride' }).click();
    await expect(page.locator('#book').getByText('Quick booking')).toBeInViewport();
    await ctx.close();
  });

  test('with reduced motion there is no intro and no 3D, just a still road', async ({ browser }, info) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, reducedMotion: 'reduce' });
    const page = await ctx.newPage();
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('#rw-title')).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('.nj-intro')).toHaveCount(0);
    await expect(page.locator('#drive canvas')).toHaveCount(0);
    await expect(page.locator('.rw-still')).toBeVisible();
    // The album becomes a still grid of every memory.
    await expect(page.locator('#album .ma-still-grid .ma-polaroid')).toHaveCount(6);
    await snap(page, info, 'reduced-motion-still');
    await ctx.close();
  });
});

test.describe('Landing — the memory album', () => {
  test('the driver takes the photo and it lands in the album', async ({ browser }, info) => {
    test.setTimeout(150_000);
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    await skipIntro(ctx);
    const page = await ctx.newPage();
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    const album = page.locator('#album');
    await album.scrollIntoViewIfNeeded();

    // Four friends pose in the viewfinder; the photographer stands beside it.
    await expect(album.locator('.ma-finder .ma-group > g')).toHaveCount(4);
    await expect(album.locator('.ma-photographer')).toBeVisible();

    // The shutter fires and the album count goes up.
    await expect(album.locator('.ma-album-head b')).toHaveText(/^[1-6] of 6$/, { timeout: 10_000 });
    await expect(album.locator('.ma-pile .ma-polaroid').first()).toBeVisible({ timeout: 10_000 });
    await snap(page, info, 'album-first-photo');

    // Pause stops the trip; the count holds.
    await album.getByRole('button', { name: 'Pause the trip' }).click();
    const held = await album.locator('.ma-album-head b').innerText();
    await page.waitForTimeout(6500);
    await expect(album.locator('.ma-album-head b')).toHaveText(held);
    await album.getByRole('button', { name: 'Resume the trip' }).click();
    await ctx.close();
  });
});

test.describe('Celebrations', () => {
  test('saving the profile celebrates', async ({ browser }) => {
    const { ctx, page } = await signedInTraveller(browser, 'cel-profile');
    await page.goto('/user/profile', { waitUntil: 'domcontentloaded' });
    // A traveller who already has a number sees their details first; editing is one click away.
    await page.getByRole('button', { name: /edit profile/i }).first().click();
    await page.getByRole('button', { name: /save changes/i }).click();
    const pop = page.getByRole('dialog', { name: 'Profile saved' });
    await expect(pop).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/profile updated successfully/i)).toBeVisible();
    await pop.getByRole('button').last().click();
    await expect(pop).toBeHidden();
    await ctx.close();
  });

  test('a UPI payment celebrates twice: sent, then confirmed once the owner verifies it', async ({ browser }, info) => {
    test.setTimeout(150_000);
    const c = await contexts('cel-pay');
    const label = `PAY${Date.now().toString().slice(-6)}`;
    const created = await c.user.post(`/api/user/${acc.user.userId}/bookings`, { data: bookingPayload({ toPlace: `Chennai ${label}` }) });
    expect(created.status(), (await created.text()).slice(0, 300)).toBe(201);
    const booking = await created.json();
    const confirm = await c.user.post(`/api/user/${acc.user.userId}/bookings/${booking.bookingId}/confirm`);
    expect(confirm.status(), (await confirm.text()).slice(0, 300)).toBe(200);

    const { ctx, page } = await signedInTraveller(browser, 'cel-pay-ui');
    const problems = watch(page, '/user/bookings');
    await page.goto('/user/bookings', { waitUntil: 'domcontentloaded' });
    const card = page.locator('div').filter({ hasText: label }).filter({ has: page.getByRole('button', { name: 'Pay', exact: true }) }).last();
    await card.getByRole('button', { name: 'Pay', exact: true }).click();
    await page.getByRole('radio', { name: 'UPI' }).click();
    await page.getByRole('button', { name: 'Pay Now' }).click();
    await page.getByRole('button', { name: "I've paid" }).click();

    const sent = page.getByRole('dialog', { name: 'Payment sent' });
    await expect(sent, 'step one: the calm "payment sent" popup').toBeVisible({ timeout: 15_000 });
    await snap(page, info, 'payment-sent');
    await sent.getByRole('button').last().click();

    // The owner verifies it, exactly as they would from the Payments page.
    const payments = await (await c.user.get(`/api/user/${acc.user.userId}/payments`)).json();
    const mine = (Array.isArray(payments) ? payments : []).find((p: any) => p.bookingId === booking.bookingId);
    expect(mine, 'the payment the traveller sent exists').toBeTruthy();
    const verified = await c.owner.post(`/api/owner/payments/${mine.paymentId}/verify`);
    expect(verified.status(), (await verified.text()).slice(0, 300)).toBe(200);

    const confirmed = page.getByRole('dialog', { name: 'Payment confirmed' });
    await expect(confirmed, 'step two: the party popup once verified (polled every 10s)').toBeVisible({ timeout: 30_000 });
    await expect(confirmed).toContainText('₹');
    await snap(page, info, 'payment-confirmed');

    expect(problems.pageErrors, summarize(problems)).toEqual([]);
    await ctx.close();
    await disposeAll(c);
  });
});
