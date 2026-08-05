import { Browser, BrowserContext, Page } from '@playwright/test';
import { freshIp } from './api';

export type Session = { token: string; userId: string; role: string; name: string; email: string; mobile: string };

/**
 * A browser context already holding a valid session, matching exactly what the real app
 * writes on login (localStorage keys `nj_token` / `nj_user`, read by AuthContext).
 * Each context gets its own synthetic client IP so page loads don't trip the rate limiter.
 */
export async function authedContext(browser: Browser, s: Session, label: string): Promise<BrowserContext> {
  const ctx = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    permissions: ['geolocation'],
    geolocation: { latitude: 11.0168, longitude: 76.9558 },
    // Contexts built here bypass the runner's `video` fixture — that only covers the built-in
    // `page`. Without this, a recorded run captures the handful of specs using `page` and
    // silently misses every authenticated screen. Opt-in so normal runs stay fast.
    ...(process.env.E2E_VIDEO_DIR
      ? { recordVideo: { dir: process.env.E2E_VIDEO_DIR, size: { width: 1440, height: 900 } } }
      : {}),
  });

  // Scope the synthetic client IP to our own API only. Setting it as a blanket
  // extraHTTPHeader would add a custom header to third-party calls too (OSRM routing,
  // map tiles), and those cross-origin requests get CORS-blocked as a result.
  await withApiIp(ctx, label);
  await ctx.addInitScript(
    ([token, user]) => {
      localStorage.setItem('nj_token', token as string);
      localStorage.setItem('nj_user', user as string);
    },
    [s.token, JSON.stringify({ ...s, message: 'ok' })]
  );
  return ctx;
}

/**
 * Attach a per-context synthetic client IP to backend requests only, so the rate limiter
 * sees each browser context as a distinct client without disturbing third-party calls.
 */
export async function withApiIp(ctx: BrowserContext, label: string) {
  const ip = freshIp(label);
  await ctx.route('**/*', async (route) => {
    const url = route.request().url();
    if (/localhost:(8080|5173)/.test(url)) {
      await route.continue({ headers: { ...route.request().headers(), 'x-forwarded-for': ip } });
    } else {
      await route.continue();
    }
  });
}

export type PageProblems = {
  route: string;
  finalUrl: string;
  pageErrors: string[];
  consoleErrors: string[];
  failedApi: string[];
};

/** Attach listeners that record everything that went wrong while a page was open. */
export function watch(page: Page, route: string): PageProblems {
  const problems: PageProblems = { route, finalUrl: '', pageErrors: [], consoleErrors: [], failedApi: [] };

  page.on('pageerror', (err) => problems.pageErrors.push(String(err).slice(0, 300)));
  page.on('console', (msg) => {
    if (msg.type() !== 'error') return;
    const text = msg.text();
    // Leaflet tile fetches and the public OSRM demo server are third-party and flaky;
    // they say nothing about this application's health.
    if (/tile\.openstreetmap|router\.project-osrm|favicon|ERR_INTERNET_DISCONNECTED/i.test(text)) return;
    problems.consoleErrors.push(text.slice(0, 300));
  });
  page.on('response', (res) => {
    const url = res.url();
    if (!url.includes('/api/')) return;
    if (res.status() < 400) return;
    // A missing live fix is the documented empty state, not a failure.
    if (res.status() === 404 && /\/api\/driver\/location\//.test(url)) return;
    problems.failedApi.push(`${res.status()} ${res.request().method()} ${url.replace(/^https?:\/\/[^/]+/, '')}`);
  });

  return problems;
}

export function summarize(p: PageProblems): string {
  const parts: string[] = [];
  if (p.pageErrors.length) parts.push(`JS errors: ${p.pageErrors.join(' | ')}`);
  if (p.failedApi.length) parts.push(`failed API calls: ${p.failedApi.join(' | ')}`);
  if (p.consoleErrors.length) parts.push(`console errors: ${p.consoleErrors.join(' | ')}`);
  return parts.join('\n');
}
