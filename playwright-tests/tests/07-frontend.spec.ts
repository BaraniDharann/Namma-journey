import { test, expect, Browser } from '@playwright/test';
import { readAccounts } from '../helpers/env';
import { authedContext, watch, summarize, withApiIp, Session } from '../helpers/browser';
import { freshIp } from '../helpers/api';

const acc = readAccounts();

const userSession: Session = {
  token: acc.user.token, userId: acc.user.userId, role: 'ROLE_USER',
  name: acc.user.name, email: acc.user.email, mobile: acc.user.mobile,
};
const driverSession: Session = {
  token: acc.driver.token, userId: acc.driver.driverId, role: 'ROLE_DRIVER',
  name: acc.driver.name, email: acc.driver.email, mobile: acc.driver.mobile,
};
const ownerSession: Session = {
  token: acc.owner.token, userId: acc.owner.ownerId, role: 'ROLE_OWNER',
  name: 'Owner', email: 'owner@example.com', mobile: '',
};

test.describe('Frontend — public pages and real login', () => {
  test('landing page renders', async ({ page }) => {
    const problems = watch(page, '/');
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await expect(page).toHaveTitle(/.+/);
    await page.waitForLoadState('networkidle').catch(() => {});
    expect(problems.pageErrors, summarize(problems)).toEqual([]);
  });

  test('a brand-new user can log in through the login form', async ({ browser }) => {
    const ctx = await browser.newContext();
    await withApiIp(ctx, 'ui-user-login');
    const page = await ctx.newPage();
    const problems = watch(page, '/login');

    await page.goto('/login', { waitUntil: 'domcontentloaded' });
    await page.getByPlaceholder(/you@example\.com|email/i).first().fill(acc.user.email);
    await page.locator('input[type="password"]').first().fill(acc.user.password);
    await page.locator('form button[type="submit"], button[type="submit"]').first().click();

    await page.waitForURL('**/user/dashboard', { timeout: 30000 });
    expect(page.url()).toContain('/user/dashboard');

    const token = await page.evaluate(() => localStorage.getItem('nj_token'));
    expect(token, 'login must persist a token').toBeTruthy();
    expect(problems.pageErrors, summarize(problems)).toEqual([]);
    await ctx.close();
  });

  test('a brand-new driver can log in through the driver login form', async ({ browser }) => {
    const ctx = await browser.newContext();
    await withApiIp(ctx, 'ui-driver-login');
    const page = await ctx.newPage();
    const problems = watch(page, '/driver/login');

    await page.goto('/driver/login', { waitUntil: 'domcontentloaded' });
    await page.getByPlaceholder('9876543210').fill(acc.driver.mobile);
    await page.getByPlaceholder(/password/i).first().fill(acc.driver.password);
    await page.locator('button[type="submit"]').first().click();

    await page.waitForURL('**/driver/dashboard', { timeout: 30000 });
    expect(page.url()).toContain('/driver/dashboard');
    expect(problems.pageErrors, summarize(problems)).toEqual([]);
    await ctx.close();
  });

  test('login rejects bad credentials without navigating away', async ({ browser }) => {
    const ctx = await browser.newContext();
    await withApiIp(ctx, 'ui-bad-login');
    const page = await ctx.newPage();
    await page.goto('/login', { waitUntil: 'domcontentloaded' });
    await page.getByPlaceholder(/you@example\.com|email/i).first().fill(acc.user.email);
    await page.locator('input[type="password"]').first().fill('wrong-password-here');
    await page.locator('button[type="submit"]').first().click();

    await page.waitForTimeout(3000);
    expect(page.url()).toContain('/login');
    expect(await page.evaluate(() => localStorage.getItem('nj_token'))).toBeFalsy();
    await ctx.close();
  });

  test('signup page renders its first step', async ({ page }) => {
    await page.goto('/signup', { waitUntil: 'domcontentloaded' });
    await expect(page.getByPlaceholder('you@example.com')).toBeVisible();
    await expect(page.getByPlaceholder('9876543210')).toBeVisible();
  });

  test('an owner can log in through the owner login form', async ({ browser }) => {
    const ctx = await browser.newContext();
    await withApiIp(ctx, 'ui-owner-login');
    const page = await ctx.newPage();
    const problems = watch(page, '/owner/login');

    await page.goto('/owner/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[type="email"]').first().fill(acc.owner.testEmail);
    await page.locator('input[type="password"]').first().fill(acc.owner.testPassword);
    await page.locator('form button[type="submit"], button[type="submit"]').first().click();

    await page.waitForURL('**/owner/dashboard', { timeout: 30000 });
    expect(page.url()).toContain('/owner/dashboard');

    const token = await page.evaluate(() => localStorage.getItem('nj_token'));
    expect(token, 'owner login must persist a token').toBeTruthy();
    expect(problems.pageErrors, summarize(problems)).toEqual([]);
    await ctx.close();
  });

  test('owner login rejects a wrong password', async ({ browser }) => {
    const ctx = await browser.newContext();
    await withApiIp(ctx, 'ui-owner-bad-login');
    const page = await ctx.newPage();

    await page.goto('/owner/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[type="email"]').first().fill(acc.owner.testEmail);
    await page.locator('input[type="password"]').first().fill('definitely-not-the-password');
    await page.locator('button[type="submit"]').first().click();

    await page.waitForTimeout(3000);
    expect(page.url()).toContain('/owner/login');
    expect(await page.evaluate(() => localStorage.getItem('nj_token'))).toBeFalsy();
    await ctx.close();
  });

  test('owner login page renders', async ({ page }) => {
    await page.goto('/owner/login', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('input[type="password"]').first()).toBeVisible();
  });

  test('protected routes bounce anonymous visitors to /login', async ({ page }) => {
    for (const route of ['/user/dashboard', '/driver/bookings', '/owner/revenue']) {
      await page.goto(route, { waitUntil: 'domcontentloaded' });
      await page.waitForURL('**/login', { timeout: 15000 });
      expect(page.url(), `${route} should redirect`).toContain('/login');
    }
  });

  test('a user cannot open owner or driver pages', async ({ browser }) => {
    const ctx = await authedContext(browser, userSession, 'ui-role-guard');
    const page = await ctx.newPage();
    for (const route of ['/owner/dashboard', '/driver/dashboard']) {
      await page.goto(route, { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(1500);
      expect(page.url(), `${route} should not stay open for a user`).not.toContain(route);
    }
    await ctx.close();
  });
});

/** Sweep every authenticated route and fail on JS errors or failed API calls. */
const ROUTES: Record<string, string[]> = {
  user: ['/user/dashboard', '/user/bookings', '/user/bookings/new', '/user/payments', '/user/profile', '/user/package-bookings'],
  driver: ['/driver/dashboard', '/driver/bookings', '/driver/profile'],
  owner: ['/owner/dashboard', '/owner/bookings', '/owner/drivers', '/owner/payments', '/owner/reviews', '/owner/revenue', '/owner/profile', '/owner/packages', '/owner/package-bookings'],
};

for (const [role, routes] of Object.entries(ROUTES)) {
  test.describe(`Frontend — ${role} routes`, () => {
    const session = role === 'user' ? userSession : role === 'driver' ? driverSession : ownerSession;

    for (const route of routes) {
      test(`${route} loads cleanly`, async ({ browser }, testInfo) => {
        const ctx = await authedContext(browser, session, `sweep-${route}`);
        const page = await ctx.newPage();
        const problems = watch(page, route);

        await page.goto(route, { waitUntil: 'domcontentloaded' });
        await page.waitForLoadState('networkidle').catch(() => {});
        await page.waitForTimeout(1200);
        problems.finalUrl = new URL(page.url()).pathname;

        await testInfo.attach(`${role}${route.replace(/\//g, '-')}.png`, {
          body: await page.screenshot({ fullPage: true }),
          contentType: 'image/png',
        });

        expect(problems.finalUrl, `${route} redirected away — session or guard problem`).toBe(route);
        expect(problems.pageErrors, summarize(problems)).toEqual([]);
        expect(problems.failedApi, summarize(problems)).toEqual([]);
        // A rendered page should have real content, not a blank error boundary.
        expect((await page.locator('body').innerText()).trim().length).toBeGreaterThan(20);

        await ctx.close();
      });
    }
  });
}
