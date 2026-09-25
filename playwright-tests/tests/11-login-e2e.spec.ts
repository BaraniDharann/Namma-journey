import { test, expect, Browser, Page, BrowserContext } from '@playwright/test';
import crypto from 'crypto';
import { readAccounts, backendEnv, WEB_BASE } from '../helpers/env';
import { apiContext, freshIp } from '../helpers/api';
import { withApiIp, watch, summarize, PageProblems } from '../helpers/browser';

/**
 * End-to-end login coverage for all three roles, driven through the real browser forms.
 *
 * 02-auth covers the login *endpoints*; the route sweep in 07 covers pages once a session
 * already exists. Neither exercised what a person actually does — type credentials, land on a
 * dashboard, reload, log out, come back — for every role, nor what happens when the session
 * they are carrying is no longer good. Both gaps hid real bugs:
 *
 *   - an unauthenticated call came back 403 instead of 401, so the SPA never noticed the
 *     session was dead and sat on the page retrying behind a permanent "Access denied" toast
 *     (see "a token the server rejects logs the session out" below);
 *   - Sign-in with Google accepted an *unsigned* token, so anyone could log in as anyone by
 *     writing their email into the payload (see the Google describe block).
 *
 * Every browser context gets its own synthetic client IP: the auth endpoints allow 10 req/min
 * per bucket and this file signs in many times over.
 */

const acc = readAccounts();

const b64url = (o: unknown) =>
  Buffer.from(JSON.stringify(o)).toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');

const sign = (data: string, secret: string) =>
  crypto.createHmac('sha256', secret).update(data).digest('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');

/**
 * A well-formed, unexpired token signed with the WRONG secret — exactly what every client is
 * holding after JWT_SECRET is rotated. It passes any client-side `exp` check, so only the
 * server can tell it is worthless.
 */
function tokenWithWrongSecret(subject: string, role: string): string {
  const now = Math.floor(Date.now() / 1000);
  const data = `${b64url({ alg: 'HS256', typ: 'JWT' })}.${b64url({ role, userId: subject, sub: subject, iat: now, exp: now + 3600 })}`;
  return `${data}.${sign(data, 'this-is-not-the-servers-signing-secret-at-all')}`;
}

/** A token signed with the real secret but already past its expiry. */
function expiredToken(subject: string, role: string): string {
  const now = Math.floor(Date.now() / 1000);
  const data = `${b64url({ alg: 'HS256', typ: 'JWT' })}.${b64url({ role, userId: subject, sub: subject, iat: now - 7200, exp: now - 3600 })}`;
  return `${data}.${sign(data, backendEnv.JWT_SECRET)}`;
}

function decodeJwt(token: string): any {
  const part = token.split('.')[1];
  return JSON.parse(Buffer.from(part.replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString('utf8'));
}

type Creds = { id: string; password: string };

type RoleCase = {
  role: 'user' | 'driver' | 'owner';
  jwtRole: string;
  loginPath: string;
  dashboard: string;
  /** Fill the login form. Each role's form asks for a different identifier. */
  fill: (page: Page, creds: Creds) => Promise<void>;
  good: Creds;
  /** An identifier of the right shape that belongs to nobody. */
  unknownId: string;
  /** The subject the issued token must carry. */
  expectedSubject: string;
  ownRoutes: string[];
  /** Routes belonging to the other two roles. */
  foreignRoutes: string[];
};

const CASES: RoleCase[] = [
  {
    role: 'user',
    jwtRole: 'ROLE_USER',
    loginPath: '/login',
    dashboard: '/user/dashboard',
    fill: async (page, c) => {
      await page.getByPlaceholder('you@example.com').first().fill(c.id);
      await page.locator('form input[type="password"]').first().fill(c.password);
    },
    good: { id: acc.user.email, password: acc.user.password },
    unknownId: `nobody.${Date.now()}@njtest.local`,
    expectedSubject: acc.user.userId,
    ownRoutes: ['/user/dashboard', '/user/bookings', '/user/payments', '/user/profile'],
    foreignRoutes: ['/driver/dashboard', '/owner/dashboard', '/owner/revenue'],
  },
  {
    role: 'driver',
    jwtRole: 'ROLE_DRIVER',
    loginPath: '/driver/login',
    dashboard: '/driver/dashboard',
    fill: async (page, c) => {
      await page.getByPlaceholder('9876543210').first().fill(c.id);
      await page.getByPlaceholder('Enter your password').first().fill(c.password);
    },
    good: { id: acc.driver.mobile, password: acc.driver.password },
    unknownId: '6000000009',
    expectedSubject: acc.driver.driverId,
    ownRoutes: ['/driver/dashboard', '/driver/bookings', '/driver/profile'],
    foreignRoutes: ['/user/dashboard', '/owner/dashboard', '/user/bookings'],
  },
  {
    role: 'owner',
    jwtRole: 'ROLE_OWNER',
    loginPath: '/owner/login',
    dashboard: '/owner/dashboard',
    fill: async (page, c) => {
      await page.getByPlaceholder('owner@example.com').first().fill(c.id);
      await page.getByPlaceholder('Enter your password').first().fill(c.password);
    },
    good: { id: acc.owner.email, password: acc.owner.password },
    unknownId: `no.such.owner.${Date.now()}@njtest.local`,
    expectedSubject: acc.owner.ownerId,
    ownRoutes: ['/owner/dashboard', '/owner/bookings', '/owner/drivers', '/owner/revenue'],
    foreignRoutes: ['/user/dashboard', '/driver/dashboard', '/user/bookings'],
  },
];

/** A fresh browser context with its own rate-limit bucket and empty storage. */
async function cleanContext(browser: Browser, label: string): Promise<BrowserContext> {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await withApiIp(ctx, label);
  return ctx;
}

/**
 * A context that starts out holding the given session, seeded through `storageState`.
 *
 * Deliberately NOT `addInitScript`: that re-runs on every document, so it would put the token
 * back after the app cleared it and redirected — which is exactly the behaviour under test here.
 */
async function contextHolding(browser: Browser, label: string, token: string, user: object): Promise<BrowserContext> {
  const ctx = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    storageState: {
      cookies: [],
      origins: [
        {
          origin: WEB_BASE,
          localStorage: [
            { name: 'nj_token', value: token },
            { name: 'nj_user', value: JSON.stringify(user) },
          ],
        },
      ],
    },
  });
  await withApiIp(ctx, label);
  return ctx;
}

/** Drive the login form all the way to the dashboard. */
async function logInThroughForm(ctx: BrowserContext, c: RoleCase): Promise<{ page: Page; problems: PageProblems }> {
  const page = await ctx.newPage();
  const problems = watch(page, c.loginPath);
  await page.goto(c.loginPath, { waitUntil: 'domcontentloaded' });
  await c.fill(page, c.good);
  await page.locator('form button[type="submit"]').first().click();
  await page.waitForURL(`**${c.dashboard}`, { timeout: 30_000 });
  return { page, problems };
}

for (const c of CASES) {
  test.describe(`Login E2E — ${c.role}`, () => {
    test(`${c.loginPath} renders its form with no JS errors`, async ({ browser }) => {
      const ctx = await cleanContext(browser, `render-${c.role}`);
      const page = await ctx.newPage();
      const problems = watch(page, c.loginPath);

      await page.goto(c.loginPath, { waitUntil: 'domcontentloaded' });
      await expect(page.locator('form input[type="password"]').first()).toBeVisible();
      await expect(page.locator('form button[type="submit"]').first()).toBeVisible();
      await page.waitForLoadState('networkidle').catch(() => {});

      expect(problems.pageErrors, summarize(problems)).toEqual([]);
      await ctx.close();
    });

    test('signs in, lands on the dashboard and stores a correctly scoped session', async ({ browser }) => {
      const ctx = await cleanContext(browser, `login-${c.role}`);
      const { page, problems } = await logInThroughForm(ctx, c);

      expect(page.url()).toContain(c.dashboard);

      const token = await page.evaluate(() => localStorage.getItem('nj_token'));
      const stored = await page.evaluate(() => localStorage.getItem('nj_user'));
      expect(token, 'login must persist a token').toBeTruthy();
      expect(stored, 'login must persist the user record AuthContext reads').toBeTruthy();

      // The token must carry this role and this account — not merely "a" session.
      const claims = decodeJwt(token as string);
      expect(claims.role).toBe(c.jwtRole);
      expect(String(claims.sub)).toBe(String(c.expectedSubject));
      expect(claims.exp * 1000, 'the issued token must not already be expired').toBeGreaterThan(Date.now());
      expect(JSON.parse(stored as string).role).toBe(c.jwtRole);

      await page.waitForLoadState('networkidle').catch(() => {});
      expect(problems.pageErrors, summarize(problems)).toEqual([]);
      expect(problems.failedApi, summarize(problems)).toEqual([]);
      await ctx.close();
    });

    test('the notification bell loads without a single failed call', async ({ browser }) => {
      // This is the reported symptom: every module screaming errors at once. The bell polls on a
      // 10s timer from the shared dashboard shell, so when auth is wrong it fails on every screen.
      const ctx = await cleanContext(browser, `notif-${c.role}`);
      const { page } = await logInThroughForm(ctx, c);

      const notifStatuses: number[] = [];
      page.on('response', (res) => {
        if (res.url().includes('/api/notifications')) notifStatuses.push(res.status());
      });

      await page.reload({ waitUntil: 'domcontentloaded' });
      await page.waitForResponse((r) => r.url().includes('/api/notifications'), { timeout: 20_000 });
      await page.waitForTimeout(1500);

      expect(notifStatuses.length, 'the bell should have polled at least once').toBeGreaterThan(0);
      expect(notifStatuses.filter((s) => s >= 400), `notification polls failed: ${notifStatuses.join(',')}`).toEqual([]);

      // And no error toast anywhere on screen.
      const body = await page.locator('body').innerText();
      expect(body).not.toContain('Access denied');
      expect(body).not.toContain('Unable to reach the server');
      await ctx.close();
    });

    test('the session survives a full page reload', async ({ browser }) => {
      const ctx = await cleanContext(browser, `reload-${c.role}`);
      const { page } = await logInThroughForm(ctx, c);

      await page.reload({ waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(2500);

      expect(page.url(), 'a reload must not bounce a valid session to /login').toContain(c.dashboard);
      expect(await page.evaluate(() => localStorage.getItem('nj_token'))).toBeTruthy();
      await ctx.close();
    });

    test('a wrong password is refused, shown inline, and stores nothing', async ({ browser }) => {
      const ctx = await cleanContext(browser, `badpass-${c.role}`);
      const page = await ctx.newPage();

      await page.goto(c.loginPath, { waitUntil: 'domcontentloaded' });
      await c.fill(page, { id: c.good.id, password: 'definitely-not-the-password' });
      await page.locator('form button[type="submit"]').first().click();

      await expect(page.locator('text=/invalid|incorrect|credential|wrong/i').first()).toBeVisible({ timeout: 15_000 });
      expect(page.url(), 'a failed login must not navigate').toContain(c.loginPath);
      expect(await page.evaluate(() => localStorage.getItem('nj_token'))).toBeFalsy();
      expect(await page.evaluate(() => localStorage.getItem('nj_user'))).toBeFalsy();
      await ctx.close();
    });

    test('an identifier belonging to nobody is refused', async ({ browser }) => {
      const ctx = await cleanContext(browser, `unknown-${c.role}`);
      const page = await ctx.newPage();

      await page.goto(c.loginPath, { waitUntil: 'domcontentloaded' });
      await c.fill(page, { id: c.unknownId, password: 'Whatever@123' });
      await page.locator('form button[type="submit"]').first().click();

      await page.waitForTimeout(3000);
      expect(page.url()).toContain(c.loginPath);
      expect(await page.evaluate(() => localStorage.getItem('nj_token'))).toBeFalsy();
      await ctx.close();
    });

    test('an empty form cannot be submitted', async ({ browser }) => {
      const ctx = await cleanContext(browser, `empty-${c.role}`);
      const page = await ctx.newPage();

      await page.goto(c.loginPath, { waitUntil: 'domcontentloaded' });
      await page.locator('form button[type="submit"]').first().click();
      await page.waitForTimeout(1500);

      expect(page.url(), 'required fields must block submission').toContain(c.loginPath);
      expect(await page.evaluate(() => localStorage.getItem('nj_token'))).toBeFalsy();
      await ctx.close();
    });

    test('logging out clears the session and the dashboard cannot be re-entered', async ({ browser }) => {
      const ctx = await cleanContext(browser, `logout-${c.role}`);
      const { page } = await logInThroughForm(ctx, c);

      await page.getByRole('button', { name: /logout/i }).first().click();
      await page.waitForURL((u) => !u.pathname.startsWith(`/${c.role}`), { timeout: 15_000 });

      expect(await page.evaluate(() => localStorage.getItem('nj_token')), 'logout must clear the token').toBeFalsy();
      expect(await page.evaluate(() => localStorage.getItem('nj_user')), 'logout must clear the user').toBeFalsy();

      // Walking back in by URL must not work either.
      await page.goto(c.dashboard, { waitUntil: 'domcontentloaded' });
      await page.waitForURL('**/login', { timeout: 15_000 });
      expect(page.url()).toContain('/login');
      await ctx.close();
    });

    test('every route this role owns opens while signed in', async ({ browser }) => {
      const ctx = await cleanContext(browser, `own-${c.role}`);
      const { page } = await logInThroughForm(ctx, c);

      for (const route of c.ownRoutes) {
        await page.goto(route, { waitUntil: 'domcontentloaded' });
        await page.waitForTimeout(1200);
        expect(new URL(page.url()).pathname, `${route} should stay open for a ${c.role}`).toBe(route);
      }
      await ctx.close();
    });

    test('the other roles routes stay shut', async ({ browser }) => {
      const ctx = await cleanContext(browser, `foreign-${c.role}`);
      const { page } = await logInThroughForm(ctx, c);

      for (const route of c.foreignRoutes) {
        await page.goto(route, { waitUntil: 'domcontentloaded' });
        await page.waitForTimeout(1500);
        expect(new URL(page.url()).pathname, `${route} must not open for a ${c.role}`).not.toBe(route);
      }
      await ctx.close();
    });

    test('visiting the login page while already signed in goes to the dashboard', async ({ browser }) => {
      const ctx = await cleanContext(browser, `resignin-${c.role}`);
      const { page } = await logInThroughForm(ctx, c);

      await page.goto(c.loginPath, { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(2500);
      expect(new URL(page.url()).pathname).toBe(c.dashboard);
      await ctx.close();
    });
  });
}

test.describe('Login E2E — dead sessions recover instead of getting stuck', () => {
  /**
   * Regression guard for the reported "Access denied" wall.
   *
   * A token the server will not accept (wrong signing secret — what every client holds after
   * JWT_SECRET is rotated) still looks fine to the client: it parses and its `exp` is in the
   * future, so AuthContext hydrates a logged-in shell. The server must answer 401 so the app
   * tears the session down and offers the login form. When it answered 403 instead, the app
   * stayed put and every poll raised "Access denied. Please log in with the correct account"
   * with no way forward.
   */
  test('a token the server rejects logs the session out and lands on /login', async ({ browser }) => {
    const ctx = await contextHolding(browser, 'rotated-secret', tokenWithWrongSecret(acc.user.userId, 'ROLE_USER'), {
      userId: acc.user.userId,
      role: 'ROLE_USER',
      name: acc.user.name,
      email: acc.user.email,
    });
    const page = await ctx.newPage();

    // The recovery is a hard `window.location.href = '/login'` fired from the API interceptor,
    // which aborts whatever navigation is still settling. Commit-only + polling the URL rides
    // that out; waitForURL would report the aborted load as the failure instead.
    await page.goto('/user/dashboard', { waitUntil: 'commit' }).catch(() => {});
    await expect.poll(() => new URL(page.url()).pathname, { timeout: 30_000 }).toBe('/login');

    // The URL flips at navigation commit, while the document behind it is still being replaced —
    // reading localStorage in that window throws "execution context was destroyed". Waiting for
    // the form itself is both the stable moment to read storage and the thing actually worth
    // asserting: the person is being offered a way back in, not staring at a dead dashboard.
    await expect(page.getByPlaceholder('you@example.com')).toBeVisible({ timeout: 20_000 });

    expect(await page.evaluate(() => localStorage.getItem('nj_token')), 'the dead token must be discarded').toBeFalsy();
    expect(await page.evaluate(() => localStorage.getItem('nj_user')), 'the stale user record must go too').toBeFalsy();
    await ctx.close();
  });

  test('the server answers an unusable token 401, never a bare 403', async () => {
    const cases: Array<[string, string]> = [
      ['wrong signing secret', tokenWithWrongSecret(acc.user.userId, 'ROLE_USER')],
      ['expired', expiredToken(acc.user.userId, 'ROLE_USER')],
      ['garbage', 'not.a.jwt.at.all'],
    ];
    for (const [label, token] of cases) {
      const api = await apiContext({ token, ip: freshIp(`unusable-${label}`) });
      const res = await api.get(`/api/notifications?recipientId=${acc.user.userId}&role=ROLE_USER`);
      expect(res.status(), `${label} token should be 401 so the client can recover`).toBe(401);
      await api.dispose();
    }

    // No token at all is the same story.
    const anon = await apiContext({ ip: freshIp('unusable-none') });
    expect((await anon.get(`/api/user/${acc.user.userId}/bookings`)).status()).toBe(401);
    await anon.dispose();
  });

  test('a valid token used outside its role is 403, not 401 — the user stays signed in', async () => {
    // The distinction matters: 401 tears the session down. A driver poking at an owner endpoint
    // has a perfectly good session and must keep it.
    const asDriver = await apiContext({ token: acc.driver.token, ip: freshIp('wrong-role-driver') });
    expect((await asDriver.get('/api/owner/bookings')).status()).toBe(403);
    await asDriver.dispose();

    const asUser = await apiContext({ token: acc.user.token, ip: freshIp('wrong-role-user') });
    expect((await asUser.get(`/api/driver/${acc.driver.driverId}/bookings`)).status()).toBe(403);
    await asUser.dispose();
  });

  test('an expired token never even renders a logged-in shell', async ({ browser }) => {
    const ctx = await contextHolding(browser, 'expired-hydrate', expiredToken(acc.user.userId, 'ROLE_USER'), {
      userId: acc.user.userId,
      role: 'ROLE_USER',
    });
    const page = await ctx.newPage();

    await page.goto('/user/dashboard', { waitUntil: 'domcontentloaded' });
    await page.waitForURL('**/login', { timeout: 20_000 });
    expect(await page.evaluate(() => localStorage.getItem('nj_token'))).toBeFalsy();
    await ctx.close();
  });

  test('anonymous visitors are bounced off every protected route', async ({ browser }) => {
    const ctx = await cleanContext(browser, 'anon-sweep');
    const page = await ctx.newPage();
    const routes = [
      '/user/dashboard', '/user/bookings', '/user/bookings/new', '/user/payments', '/user/profile',
      '/driver/dashboard', '/driver/bookings', '/driver/profile',
      '/owner/dashboard', '/owner/bookings', '/owner/drivers', '/owner/revenue', '/owner/packages',
    ];
    for (const route of routes) {
      await page.goto(route, { waitUntil: 'domcontentloaded' });
      await page.waitForURL('**/login', { timeout: 15_000 });
      expect(page.url(), `${route} must redirect an anonymous visitor`).toContain('/login');
    }
    await ctx.close();
  });
});

test.describe('Login E2E — Sign-in with Google', () => {
  /**
   * The backend used to "verify" a Google ID token by base64-decoding the payload and trusting
   * its `email`. No signature check, so a hand-written token with `{"alg":"none"}` and any
   * address in it was accepted and returned a real session for that account — a complete
   * authentication bypass against every user, including ones who had never used Google sign-in.
   */
  const forged = (payload: Record<string, unknown>, alg = 'none') =>
    `${b64url({ alg, typ: 'JWT' })}.${b64url(payload)}.not-a-real-signature`;

  test('an unsigned token naming an existing account is refused', async () => {
    const api = await apiContext({ ip: freshIp('google-forge-existing') });
    const res = await api.post('/api/auth/user/login', {
      data: {
        loginType: 'GOOGLE',
        token: forged({ email: acc.user.email, name: 'Impersonator', email_verified: true }),
      },
    });
    expect(res.status(), 'a forged Google token must never mint a session').toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
    expect(await res.text()).not.toContain('"token"');
    await api.dispose();
  });

  test('an unsigned token is refused even with the right issuer and audience filled in', async () => {
    const api = await apiContext({ ip: freshIp('google-forge-claims') });
    const res = await api.post('/api/auth/user/login', {
      data: {
        loginType: 'GOOGLE',
        token: forged(
          {
            email: acc.user.email,
            email_verified: true,
            iss: 'https://accounts.google.com',
            aud: backendEnv.GOOGLE_CLIENT_ID,
            exp: Math.floor(Date.now() / 1000) + 3600,
          },
          'RS256'
        ),
      },
    });
    expect(res.status(), 'claims are not evidence — only the signature is').toBeGreaterThanOrEqual(400);
    expect(await res.text()).not.toContain('"token"');
    await api.dispose();
  });

  test('a forged token must not silently create a new account either', async () => {
    const victim = `ghost.${Date.now()}@njtest.local`;
    const api = await apiContext({ ip: freshIp('google-forge-new') });
    const res = await api.post('/api/auth/user/login', {
      data: { loginType: 'GOOGLE', token: forged({ email: victim, name: 'Ghost', email_verified: true }) },
    });
    expect(res.status()).toBeGreaterThanOrEqual(400);
    await api.dispose();

    const { query } = await import('../helpers/db');
    const rows = await query('SELECT id FROM users WHERE lower(email) = lower($1)', [victim]);
    expect(rows.length, 'a rejected Google login must not have created an account').toBe(0);
  });

  test('empty and malformed Google tokens are refused', async () => {
    const tokens = ['', 'not-a-jwt', 'a.b', '...'];
    for (let i = 0; i < tokens.length; i++) {
      const api = await apiContext({ ip: freshIp(`google-bad-${i}`) });
      const res = await api.post('/api/auth/user/login', { data: { loginType: 'GOOGLE', token: tokens[i] } });
      expect(res.status(), `token ${JSON.stringify(tokens[i])} must be refused`).toBeGreaterThanOrEqual(400);
      expect(res.status()).toBeLessThan(500);
      await api.dispose();
    }
  });

  test('the login page still offers Google sign-in alongside the email form', async ({ browser }) => {
    const ctx = await cleanContext(browser, 'google-button');
    const page = await ctx.newPage();
    await page.goto('/login', { waitUntil: 'domcontentloaded' });

    await expect(page.getByText(/or sign in with email/i)).toBeVisible();
    await expect(page.getByPlaceholder('you@example.com')).toBeVisible();
    await ctx.close();
  });
});

test.describe('Login E2E — the sessions the forms hand out actually work', () => {
  /** A session is only real if it opens that role's own data and nothing else. */
  test('each role can log in over the API and use its own endpoints', async () => {
    const user = await apiContext({ ip: freshIp('api-user-login') });
    const uRes = await user.post('/api/auth/user/login', {
      data: { loginType: 'EMAIL', email: acc.user.email, password: acc.user.password },
    });
    expect(uRes.status()).toBe(200);
    const uBody = await uRes.json();
    expect(uBody.role).toBe('ROLE_USER');
    await user.dispose();

    const asUser = await apiContext({ token: uBody.token, ip: freshIp('api-user-use') });
    expect((await asUser.get(`/api/user/${uBody.userId}/bookings`)).status()).toBe(200);
    await asUser.dispose();

    const driver = await apiContext({ ip: freshIp('api-driver-login') });
    const dRes = await driver.post('/api/auth/driver/login', {
      data: { mobile: acc.driver.mobile, password: acc.driver.password },
    });
    expect(dRes.status()).toBe(200);
    const dBody = await dRes.json();
    expect(dBody.role).toBe('ROLE_DRIVER');
    await driver.dispose();

    const asDriver = await apiContext({ token: dBody.token, ip: freshIp('api-driver-use') });
    expect((await asDriver.get(`/api/driver/${dBody.userId}/bookings`)).status()).toBe(200);
    await asDriver.dispose();

    const owner = await apiContext({ ip: freshIp('api-owner-login') });
    const oRes = await owner.post('/api/auth/owner/login', {
      data: { email: acc.owner.email, password: acc.owner.password },
    });
    expect(oRes.status()).toBe(200);
    const oBody = await oRes.json();
    expect(oBody.role).toBe('ROLE_OWNER');
    await owner.dispose();

    const asOwner = await apiContext({ token: oBody.token, ip: freshIp('api-owner-use') });
    expect((await asOwner.get('/api/owner/bookings')).status()).toBe(200);
    await asOwner.dispose();
  });

  test('a login response never leaks the password hash', async () => {
    const api = await apiContext({ ip: freshIp('leak-check') });
    const res = await api.post('/api/auth/user/login', {
      data: { loginType: 'EMAIL', email: acc.user.email, password: acc.user.password },
    });
    const text = await res.text();
    expect(text).not.toContain('$2a$');
    expect(text).not.toContain('$2b$');
    expect(JSON.parse(text).password).toBeUndefined();
    await api.dispose();
  });

  test('one role cannot read another role notification bucket', async () => {
    const asUser = await apiContext({ token: acc.user.token, ip: freshIp('notif-cross') });
    expect((await asUser.get('/api/notifications?recipientId=owner&role=ROLE_OWNER')).status()).toBe(403);
    expect((await asUser.get(`/api/notifications?recipientId=${acc.driver.driverId}&role=ROLE_DRIVER`)).status()).toBe(403);
    // Its own bucket is fine.
    expect((await asUser.get(`/api/notifications?recipientId=${acc.user.userId}&role=ROLE_USER`)).status()).toBe(200);
    await asUser.dispose();
  });
});
