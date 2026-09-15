import { test, expect, Browser, BrowserContext } from '@playwright/test';
import fs from 'fs';
import path from 'path';
import { readAccounts } from '../helpers/env';
import { withApiIp } from '../helpers/browser';
import { API_BASE } from '../helpers/env';
import { request } from '@playwright/test';

/**
 * Sweeps every page in the app at three breakpoints and fails on horizontal overflow
 * (the concrete symptom of "not responsive": a page wider than its viewport, forcing a
 * sideways scrollbar / clipped content on phones and tablets).
 *
 * Screenshots for every route x viewport combination are saved to ./responsive-screenshots
 * so layout can be eyeballed even when the overflow check passes.
 */

const acc = readAccounts();

type Session = { token: string; userId: string; role: string; name: string; email: string; mobile: string };

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

const VIEWPORTS = [
  { name: 'mobile', width: 375, height: 812 },   // iPhone X/12/13 class
  { name: 'tablet', width: 768, height: 1024 },  // iPad portrait
  { name: 'desktop', width: 1440, height: 900 },
];

let packageId = '1';

test.beforeAll(async () => {
  const ctx = await request.newContext({ baseURL: API_BASE });
  try {
    const res = await ctx.get('/api/public/packages');
    if (res.status() === 200) {
      const list = await res.json();
      if (Array.isArray(list) && list.length > 0) packageId = String(list[0].id);
    }
  } finally {
    await ctx.dispose();
  }
});

const PUBLIC_ROUTES = ['/', '/login', '/signup', '/driver/login', '/owner/login'];
// PackageDetail's id is resolved at runtime (beforeAll), so it's added as a function.
const PACKAGE_ROUTE = () => `/packages/${packageId}`;

const AUTH_ROUTES: Record<string, { session: Session; routes: string[] }> = {
  user: {
    session: userSession,
    routes: ['/user/dashboard', '/user/bookings', '/user/bookings/new', '/user/payments', '/user/profile', '/user/package-bookings'],
  },
  driver: {
    session: driverSession,
    routes: ['/driver/dashboard', '/driver/bookings', '/driver/profile'],
  },
  owner: {
    session: ownerSession,
    routes: ['/owner/dashboard', '/owner/bookings', '/owner/drivers', '/owner/payments', '/owner/reviews', '/owner/revenue', '/owner/profile', '/owner/packages', '/owner/package-bookings'],
  },
};

const SCREENSHOT_DIR = path.join(__dirname, '..', 'responsive-screenshots');

async function mkContext(browser: Browser, viewport: { width: number; height: number }, session: Session | null, label: string): Promise<BrowserContext> {
  const ctx = await browser.newContext({ viewport });
  await withApiIp(ctx, label);
  if (session) {
    await ctx.addInitScript(
      ([token, user]) => {
        localStorage.setItem('nj_token', token as string);
        localStorage.setItem('nj_user', user as string);
      },
      [session.token, JSON.stringify({ ...session, message: 'ok' })]
    );
  }
  return ctx;
}

type OverflowReport = {
  overflowPx: number;
  scrollWidth: number;
  clientWidth: number;
  culprits: string[];
};

async function checkOverflow(page: import('@playwright/test').Page): Promise<OverflowReport> {
  return page.evaluate(() => {
    const doc = document.documentElement;
    const clientWidth = doc.clientWidth;
    const scrollWidth = Math.max(doc.scrollWidth, document.body.scrollWidth);

    const culprits: string[] = [];
    const all = document.querySelectorAll('body *');
    for (const el of Array.from(all)) {
      const rect = (el as HTMLElement).getBoundingClientRect();
      if (rect.right > clientWidth + 2 && rect.width > 0) {
        const tag = el.tagName.toLowerCase();
        const id = (el as HTMLElement).id ? `#${(el as HTMLElement).id}` : '';
        const cls = (el as HTMLElement).className && typeof (el as HTMLElement).className === 'string'
          ? '.' + (el as HTMLElement).className.trim().split(/\s+/).slice(0, 2).join('.')
          : '';
        culprits.push(`${tag}${id}${cls} (right=${Math.round(rect.right)}, width=${Math.round(rect.width)})`);
      }
      if (culprits.length >= 8) break;
    }

    return { overflowPx: scrollWidth - clientWidth, scrollWidth, clientWidth, culprits };
  });
}

test.describe('Responsive sweep', () => {
  test.beforeAll(() => {
    fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  });

  for (const viewport of VIEWPORTS) {
    test.describe(`${viewport.name} (${viewport.width}x${viewport.height})`, () => {
      for (const route of PUBLIC_ROUTES) {
        test(`public ${route}`, async ({ browser }, testInfo) => {
          const ctx = await mkContext(browser, viewport, null, `resp-${viewport.name}-${route}`);
          const page = await ctx.newPage();
          await page.goto(route, { waitUntil: 'domcontentloaded' });
          await page.waitForLoadState('networkidle').catch(() => {});
          await page.waitForTimeout(500);

          const report = await checkOverflow(page);
          const slug = route === '/' ? 'home' : route.replace(/\//g, '-');
          const shot = await page.screenshot({ fullPage: true });
          await testInfo.attach(`${viewport.name}${slug}.png`, { body: shot, contentType: 'image/png' });
          fs.writeFileSync(path.join(SCREENSHOT_DIR, `${viewport.name}${slug}.png`), shot);

          expect(report.overflowPx, `${route} @ ${viewport.name}: horizontal overflow ${report.overflowPx}px (scrollWidth=${report.scrollWidth} clientWidth=${report.clientWidth}). Culprits:\n${report.culprits.join('\n')}`).toBeLessThanOrEqual(2);

          await ctx.close();
        });
      }

      test(`public ${PACKAGE_ROUTE()} (package detail)`, async ({ browser }, testInfo) => {
        const route = PACKAGE_ROUTE();
        const ctx = await mkContext(browser, viewport, null, `resp-${viewport.name}-pkg`);
        const page = await ctx.newPage();
        await page.goto(route, { waitUntil: 'domcontentloaded' });
        await page.waitForLoadState('networkidle').catch(() => {});
        await page.waitForTimeout(500);

        const report = await checkOverflow(page);
        const shot = await page.screenshot({ fullPage: true });
        await testInfo.attach(`${viewport.name}-package-detail.png`, { body: shot, contentType: 'image/png' });
        fs.writeFileSync(path.join(SCREENSHOT_DIR, `${viewport.name}-package-detail.png`), shot);

        expect(report.overflowPx, `${route} @ ${viewport.name}: horizontal overflow ${report.overflowPx}px. Culprits:\n${report.culprits.join('\n')}`).toBeLessThanOrEqual(2);

        await ctx.close();
      });

      for (const [role, { session, routes }] of Object.entries(AUTH_ROUTES)) {
        for (const route of routes) {
          test(`${role} ${route}`, async ({ browser }, testInfo) => {
            const ctx = await mkContext(browser, viewport, session, `resp-${viewport.name}-${role}-${route}`);
            const page = await ctx.newPage();
            await page.goto(route, { waitUntil: 'domcontentloaded' });
            await page.waitForLoadState('networkidle').catch(() => {});
            await page.waitForTimeout(700);

            const report = await checkOverflow(page);
            const slug = route.replace(/\//g, '-');
            const shot = await page.screenshot({ fullPage: true });
            await testInfo.attach(`${viewport.name}-${role}${slug}.png`, { body: shot, contentType: 'image/png' });
            fs.writeFileSync(path.join(SCREENSHOT_DIR, `${viewport.name}-${role}${slug}.png`), shot);

            expect(report.overflowPx, `${route} @ ${viewport.name}: horizontal overflow ${report.overflowPx}px (scrollWidth=${report.scrollWidth} clientWidth=${report.clientWidth}). Culprits:\n${report.culprits.join('\n')}`).toBeLessThanOrEqual(2);

            await ctx.close();
          });
        }
      }
    });
  }
});
