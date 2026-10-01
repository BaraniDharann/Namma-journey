import { test, expect, Browser, BrowserContext, Page, TestInfo } from '@playwright/test';
import { readAccounts } from '../helpers/env';
import { withApiIp, watch, summarize, PageProblems } from '../helpers/browser';

/**
 * The Postcard redesign, module by module, for all three roles.
 *
 * Each role signs in through its real login form, then every page it owns is opened in turn.
 * On every page we check the things that make it a Postcard page and a healthy one: the teal
 * rail and the page heading are there, the navigation and page chrome carry drawn icons rather
 * than emoji, nothing threw, no API call failed, and at phone width nothing scrolls sideways.
 * A screenshot of each page is attached to the HTML report.
 */

const acc = readAccounts();

/** Any pictographic emoji — what the old UI used in place of icons. */
const EMOJI = /\p{Extended_Pictographic}/u;

type Role = {
  name: 'owner' | 'driver' | 'traveller';
  loginPath: string;
  dashboard: string;
  fill: (page: Page) => Promise<void>;
  routes: string[];
};

const ROLES: Role[] = [
  {
    name: 'owner',
    loginPath: '/owner/login',
    dashboard: '/owner/dashboard',
    fill: async (page) => {
      await page.getByPlaceholder('owner@example.com').first().fill(acc.owner.email);
      await page.getByPlaceholder('Enter your password').first().fill(acc.owner.password);
    },
    routes: [
      '/owner/dashboard', '/owner/bookings', '/owner/drivers', '/owner/payments', '/owner/reviews',
      '/owner/packages', '/owner/package-bookings', '/owner/revenue', '/owner/profile',
    ],
  },
  {
    name: 'driver',
    loginPath: '/driver/login',
    dashboard: '/driver/dashboard',
    fill: async (page) => {
      await page.getByPlaceholder('9876543210').first().fill(acc.driver.mobile);
      await page.getByPlaceholder('Enter your password').first().fill(acc.driver.password);
    },
    routes: ['/driver/dashboard', '/driver/bookings', '/driver/profile'],
  },
  {
    name: 'traveller',
    loginPath: '/login',
    dashboard: '/user/dashboard',
    fill: async (page) => {
      await page.getByPlaceholder('you@example.com').first().fill(acc.user.email);
      await page.locator('form input[type="password"]').first().fill(acc.user.password);
    },
    routes: [
      '/user/dashboard', '/user/bookings', '/user/bookings/new', '/user/payments',
      '/user/package-bookings', '/user/profile',
    ],
  },
];

async function signIn(browser: Browser, role: Role, label: string, viewport = { width: 1440, height: 900 }) {
  const ctx = await browser.newContext({ viewport, ...(viewport.width < 500 ? { isMobile: true, hasTouch: true } : {}) });
  await withApiIp(ctx, label);
  const page = await ctx.newPage();
  await page.goto(role.loginPath, { waitUntil: 'domcontentloaded' });
  await role.fill(page);
  await page.locator('form button[type="submit"]').first().click();
  await page.waitForURL(`**${role.dashboard}`, { timeout: 30_000 });
  return { ctx, page };
}

/** Open a route and wait until its Postcard heading has rendered and the data calls settle. */
async function open(page: Page, route: string): Promise<PageProblems> {
  const problems = watch(page, route);
  await page.goto(route, { waitUntil: 'domcontentloaded' });
  await expect(page.locator('.pc-app'), `${route}: Postcard shell`).toBeVisible({ timeout: 20_000 });
  await expect(page.locator('main h1').first(), `${route}: page heading`).toBeVisible({ timeout: 20_000 });
  await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => {});
  await page.waitForTimeout(400);
  return problems;
}

async function snap(page: Page, info: TestInfo, name: string) {
  await info.attach(name, { body: await page.screenshot({ fullPage: true }), contentType: 'image/png' });
}

for (const role of ROLES) {
  test.describe(`Postcard — ${role.name}`, () => {
    let ctx: BrowserContext;
    test.afterEach(async () => { await ctx?.close(); });

    test(`every ${role.name} module opens in the Postcard shell, cleanly`, async ({ browser }, info) => {
      test.setTimeout(30_000 + role.routes.length * 25_000);
      const s = await signIn(browser, role, `pc-${role.name}-desk`);
      ctx = s.ctx;
      const { page } = s;

      for (const route of role.routes) {
        const problems = await open(page, route);
        expect(new URL(page.url()).pathname, `${route} should stay open`).toBe(route);

        // The rail: teal, one link per module, the current one marked, drawn icons only.
        const rail = page.locator('.pc-rail');
        await expect(rail).toBeVisible();
        await expect(rail.locator('a[aria-current="page"]')).toHaveCount(1);
        const railLinks = rail.locator('.pc-nav a');
        expect(await railLinks.count()).toBe(role.routes.length);
        expect(await rail.locator('.pc-nav a svg.pc-ic').count(), `${route}: every nav link has an icon`).toBe(role.routes.length);
        expect(await rail.innerText(), `${route}: no emoji in the navigation`).not.toMatch(EMOJI);

        // Page chrome: heading row, top bar and every button carry no emoji.
        expect(await page.locator('.pc-top').innerText(), `${route}: no emoji in the top bar`).not.toMatch(EMOJI);
        expect(await page.locator('.pc-head').first().innerText(), `${route}: no emoji in the heading`).not.toMatch(EMOJI);
        const buttonText = (await page.locator('main button, main a.pc-btn, main a.btn-primary').allInnerTexts()).join(' | ');
        expect(buttonText, `${route}: no emoji on buttons`).not.toMatch(EMOJI);

        // Money is shown in whole rupees: no "₹6,000.223" style amounts anywhere on the page.
        const body = await page.locator('main').innerText();
        expect(body, `${route}: rupee amounts have no stray decimals`).not.toMatch(/₹\s?[\d,]+\.\d{3,}/);

        await snap(page, info, `${role.name}${route.replace(/\//g, '-')}.png`);
        expect(problems.pageErrors, summarize(problems)).toEqual([]);
        expect(problems.failedApi, summarize(problems)).toEqual([]);
      }
    });

    test(`the ${role.name} dashboard shows its cards and charts`, async ({ browser }, info) => {
      const s = await signIn(browser, role, `pc-${role.name}-dash`);
      ctx = s.ctx;
      const { page } = s;
      await open(page, role.dashboard);

      const panels = page.locator('main .pc-panel');
      expect(await panels.count(), 'the dashboard is built from Postcard panels').toBeGreaterThanOrEqual(4);

      // One analytics card holds every chart: tabs pick the data, icons pick the chart type.
      const card = page.locator('main .pc-analytics');
      await expect(card).toBeVisible();
      const tabs = card.locator('.pc-tab');
      expect(await tabs.count(), 'the analytics card offers more than one dataset').toBeGreaterThanOrEqual(3);
      await card.getByRole('button', { name: /show as bars chart/i }).click();
      await expect(card.getByRole('button', { name: /show as bars chart/i })).toHaveAttribute('aria-pressed', 'true');
      await tabs.last().click();
      await expect(tabs.last()).toHaveAttribute('aria-selected', 'true');
      await card.getByRole('button', { name: /show as pie chart/i }).click();
      await expect(card.locator('.pc-donut, .pc-nodata')).toBeVisible();
      // A pie of months would misread the data, so a time series disables it.
      // A pie always carries a legend, never colour alone.
      if (await card.locator('.pc-donut').count()) await expect(card.locator('.pc-donut .pc-legend li').first()).toBeVisible();
      await tabs.first().click();
      await expect(card.getByRole('button', { name: /show as pie chart/i })).toBeDisabled();
      // Every chart either draws or says plainly that there is nothing to chart yet.
      await expect(card.locator('.pc-donut')).toHaveCount(0);
      const charts = page.locator('main .pc-chart, main .pc-donut, main .pc-gauge, main .pc-hbars, main .pc-nodata, main .pc-empty');
      expect(await charts.count(), 'charts or honest empty states are present').toBeGreaterThanOrEqual(2);

      if (role.name === 'owner') {
        await expect(page.locator('.pc-today'), 'the "needs you today" block').toBeVisible();
        await expect(page.locator('.pc-strip'), 'the summary strip').toBeVisible();
        await expect(page.getByRole('link', { name: /add driver/i })).toBeVisible();
      }
      if (role.name === 'driver') {
        const sw = page.getByRole('switch');
        await expect(sw, 'the online switch').toBeVisible();
        await expect(sw).toHaveAttribute('aria-checked', /true|false/);
      }
      if (role.name === 'traveller') {
        await expect(page.locator('section.rts'), 'the road-trip story stays').toBeVisible();
        await expect(page.getByRole('link', { name: /new booking/i }).first()).toBeVisible();
      }
      await snap(page, info, `${role.name}-dashboard-cards.png`);
    });

    test(`at phone width every ${role.name} module fits, and the menu opens`, async ({ browser }, info) => {
      test.setTimeout(30_000 + role.routes.length * 20_000);
      const s = await signIn(browser, role, `pc-${role.name}-mob`, { width: 390, height: 844 });
      ctx = s.ctx;
      const { page } = s;

      for (const route of role.routes) {
        await open(page, route);
        const overflow = await page.evaluate(() => {
          const main = document.querySelector('.pc-main') as HTMLElement | null;
          return {
            doc: document.documentElement.scrollWidth - window.innerWidth,
            main: main ? main.scrollWidth - main.clientWidth : 0,
          };
        });
        expect(overflow.doc, `${route}: the page must not scroll sideways`).toBeLessThanOrEqual(1);
        expect(overflow.main, `${route}: the content area must not scroll sideways`).toBeLessThanOrEqual(1);
        await snap(page, info, `${role.name}${route.replace(/\//g, '-')}-phone.png`);
      }

      // The rail is a drawer on phones: hidden until the menu button opens it.
      await expect(page.locator('.pc-rail')).toBeHidden();
      await page.getByRole('button', { name: 'Open menu' }).click();
      await expect(page.locator('.pc-rail')).toBeVisible();
      await page.locator('.pc-rail .pc-nav a').nth(1).click();
      await expect(page.locator('.pc-rail')).toBeHidden();
    });
  });
}
