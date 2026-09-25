import { test, expect, Browser } from '@playwright/test';
import { apiContext, freshIp } from '../helpers/api';
import { createUser, NewUser } from '../helpers/accounts';
import { query } from '../helpers/db';
import { authedContext, Session, watch, summarize } from '../helpers/browser';

/**
 * A traveller with no mobile number on file cannot be reached by the driver who takes their trip,
 * so booking is closed until they add one.
 *
 * This is not hypothetical: Sign-in with Google never collects a number — Google does not hand one
 * over — so every Google account arrives with that field empty. Before this rule existed those
 * travellers reached the booking form, filled it in, and were turned away at the end by bean
 * validation with "Phone number is required", which says nothing about where to fix it.
 *
 * The rule lives on the server (UserService.createBooking). The dashboard prompt and the panel that
 * stands in for the booking form are there so the refusal arrives early and explains itself.
 */

/** A traveller whose account has no phone — what a Google signup looks like. */
async function travellerWithoutMobile(label: string): Promise<NewUser> {
  const user = await createUser(label);
  await query('UPDATE users SET phone = NULL WHERE id = $1', [user.userId]);
  return { ...user, mobile: '' };
}

function sessionFor(u: NewUser, mobile: string): Session {
  return { token: u.token, userId: u.userId, role: 'ROLE_USER', name: u.name, email: u.email, mobile };
}

const BOOKING_PAYLOAD = {
  userName: 'E2E Traveller',
  userPhone: '9876500011',
  fromPlace: 'Coimbatore',
  toPlace: 'Ooty',
  fromLat: 11.0168,
  fromLon: 76.9558,
  toLat: 11.4102,
  toLon: 76.695,
  fromDate: '2026-12-01',
  toDate: '2026-12-02',
  travelMembers: 2,
  acType: 'AC',
  bookingType: 'DISTANCE_BASED',
};

test.describe('Booking requires a mobile number — server side', () => {
  test('a booking is refused when the account has no mobile, with a message that says what to do', async () => {
    const u = await travellerWithoutMobile('gate-api');
    const api = await apiContext({ token: u.token, ip: freshIp('gate-api') });

    const res = await api.post(`/api/user/${u.userId}/bookings`, { data: BOOKING_PAYLOAD });

    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.error).toMatch(/mobile number/i);
    expect(body.error, 'the message must point at the profile, not just state a field is missing').toMatch(/profile/i);
    await api.dispose();
  });

  test('supplying a phone in the payload does not get you past it', async () => {
    // The contact number on the booking comes from the request body, so the gate has to check the
    // *account*. Otherwise anyone could type a number into the payload and book anyway.
    const u = await travellerWithoutMobile('gate-payload');
    const api = await apiContext({ token: u.token, ip: freshIp('gate-payload') });

    const res = await api.post(`/api/user/${u.userId}/bookings`, {
      data: { ...BOOKING_PAYLOAD, userPhone: '9999900000' },
    });

    expect(res.status()).toBe(400);
    expect((await res.json()).error).toMatch(/mobile number/i);
    await api.dispose();
  });

  test('once a number is added the same booking goes through', async () => {
    const u = await travellerWithoutMobile('gate-unlock');
    const api = await apiContext({ token: u.token, ip: freshIp('gate-unlock') });

    expect((await api.post(`/api/user/${u.userId}/bookings`, { data: BOOKING_PAYLOAD })).status()).toBe(400);

    const mobile = `9${Date.now().toString().slice(-9)}`;
    const saved = await api.put(`/api/user/${u.userId}/profile`, { data: { name: u.name, phone: mobile } });
    expect(saved.status()).toBe(200);
    expect((await saved.json()).mobile).toBe(mobile);

    const booked = await api.post(`/api/user/${u.userId}/bookings`, { data: BOOKING_PAYLOAD });
    expect(booked.status(), 'the gate must lift as soon as a number is on file').toBe(201);
    await api.dispose();
  });

  test('a traveller who already has a number is never gated', async () => {
    const u = await createUser('gate-has-mobile');
    const api = await apiContext({ token: u.token, ip: freshIp('gate-has-mobile') });

    const res = await api.post(`/api/user/${u.userId}/bookings`, { data: BOOKING_PAYLOAD });
    expect(res.status()).toBe(201);
    await api.dispose();
  });
});

test.describe('Saving the mobile number', () => {
  test('a malformed number is rejected with a reason the traveller can act on', async () => {
    const u = await travellerWithoutMobile('mobile-format');
    const api = await apiContext({ token: u.token, ip: freshIp('mobile-format') });

    for (const bad of ['12345', '1234567890', 'abcdefghij', '98765432100']) {
      const res = await api.put(`/api/user/${u.userId}/profile`, { data: { phone: bad } });
      expect(res.status(), `${bad} should be refused`).toBe(400);
      expect((await res.json()).error).toMatch(/valid 10-digit/i);
    }
    await api.dispose();
  });

  test('a number already registered to someone else is refused, not surfaced as a database error', async () => {
    const taken = await createUser('mobile-taken');
    const u = await travellerWithoutMobile('mobile-dupe');
    const api = await apiContext({ token: u.token, ip: freshIp('mobile-dupe') });

    const res = await api.put(`/api/user/${u.userId}/profile`, { data: { phone: taken.mobile } });

    // Before this check the unique constraint surfaced as a 409 about "a database constraint".
    expect(res.status()).toBe(400);
    expect((await res.json()).error).toMatch(/already registered/i);
    await api.dispose();
  });

  test('re-saving your own number is not treated as a duplicate of yourself', async () => {
    const u = await createUser('mobile-self');
    const api = await apiContext({ token: u.token, ip: freshIp('mobile-self') });

    const res = await api.put(`/api/user/${u.userId}/profile`, { data: { name: 'Renamed', phone: u.mobile } });
    expect(res.status()).toBe(200);
    expect((await res.json()).mobile).toBe(u.mobile);
    await api.dispose();
  });
});

test.describe('Booking requires a mobile number — what the traveller sees', () => {
  test('the dashboard prompts for the number straight after signing in', async ({ browser }) => {
    const u = await travellerWithoutMobile('gate-ui-prompt');
    const ctx = await authedContext(browser, sessionFor(u, ''), 'gate-ui-prompt');
    const page = await ctx.newPage();
    const problems = watch(page, '/user/dashboard');

    await page.goto('/user/dashboard', { waitUntil: 'domcontentloaded' });

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible({ timeout: 15_000 });
    await expect(dialog.getByText(/add your mobile number/i)).toBeVisible();
    await expect(dialog.getByText(/booking stays closed/i)).toBeVisible();

    // It is a nudge, not a trap — it can be dismissed.
    await dialog.getByRole('button', { name: /later/i }).click();
    await expect(dialog).toBeHidden();

    expect(problems.pageErrors, summarize(problems)).toEqual([]);
    await ctx.close();
  });

  test('the prompt sends the traveller to the profile, already open for editing', async ({ browser }) => {
    const u = await travellerWithoutMobile('gate-ui-nav');
    const ctx = await authedContext(browser, sessionFor(u, ''), 'gate-ui-nav');
    const page = await ctx.newPage();

    await page.goto('/user/dashboard', { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: /update my profile/i }).click();

    await page.waitForURL('**/user/profile', { timeout: 15_000 });
    await expect(page.getByText(/your mobile number is missing/i)).toBeVisible();
    // Opened straight into the form rather than making them find the Edit button.
    await expect(page.getByPlaceholder('Enter mobile number')).toBeVisible();
    await ctx.close();
  });

  test('the booking form is replaced by an explanation instead of failing at submit', async ({ browser }) => {
    const u = await travellerWithoutMobile('gate-ui-form');
    const ctx = await authedContext(browser, sessionFor(u, ''), 'gate-ui-form');
    const page = await ctx.newPage();

    await page.goto('/user/bookings/new', { waitUntil: 'domcontentloaded' });

    await expect(page.getByText(/add your mobile number to book a trip/i)).toBeVisible({ timeout: 15_000 });
    // The form itself must be gone — not merely disabled, or they will still fill it in.
    await expect(page.locator('form')).toHaveCount(0);
    await expect(page.getByRole('button', { name: /confirm booking/i })).toHaveCount(0);

    await page.getByRole('button', { name: /go to my profile/i }).click();
    await page.waitForURL('**/user/profile', { timeout: 15_000 });
    await ctx.close();
  });

  test('adding the number in the profile opens the booking form', async ({ browser }) => {
    const u = await travellerWithoutMobile('gate-ui-unlock');
    const ctx = await authedContext(browser, sessionFor(u, ''), 'gate-ui-unlock');
    const page = await ctx.newPage();

    await page.goto('/user/profile', { waitUntil: 'domcontentloaded' });
    const mobile = `9${Date.now().toString().slice(-9)}`;
    await page.getByPlaceholder('Enter mobile number').fill(mobile);
    await page.getByRole('button', { name: /save changes/i }).click();
    await expect(page.getByText(/profile updated successfully/i)).toBeVisible({ timeout: 15_000 });

    // Navigate the way a person would — through the sidebar. The gate reads the session the
    // profile save just refreshed, so it lifts immediately with no reload needed.
    // (A page.goto here would prove nothing: authedContext re-seeds the original localStorage on
    // every document, so a full load would put the empty mobile straight back.)
    await page.getByRole('link', { name: /new booking/i }).first().click();
    await page.waitForURL('**/user/bookings/new', { timeout: 15_000 });
    await expect(page.getByText(/fill in the details to book your journey/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/add your mobile number to book a trip/i)).toHaveCount(0);
    await ctx.close();
  });

  test('a bad number entered in the profile shows the reason the server gave', async ({ browser }) => {
    const u = await travellerWithoutMobile('gate-ui-badnum');
    const ctx = await authedContext(browser, sessionFor(u, ''), 'gate-ui-badnum');
    const page = await ctx.newPage();

    await page.goto('/user/profile', { waitUntil: 'domcontentloaded' });
    await page.getByPlaceholder('Enter mobile number').fill('12345');
    await page.getByRole('button', { name: /save changes/i }).click();

    // The page used to read `data.message` while the API sends `data.error`, so every server
    // reason was swallowed and replaced with a flat "Failed to update profile".
    // Scoped to the page body: the same text also lands in a toast, which is a separate element.
    await expect(page.getByRole('main').getByText(/valid 10-digit/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/^Failed to update profile$/)).toHaveCount(0);
    await ctx.close();
  });

  test('a traveller who has a number sees no prompt and the normal form', async ({ browser }) => {
    const u = await createUser('gate-ui-ok');
    const ctx = await authedContext(browser, sessionFor(u, u.mobile), 'gate-ui-ok');
    const page = await ctx.newPage();

    await page.goto('/user/dashboard', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1500);
    await expect(page.getByRole('dialog')).toHaveCount(0);

    await page.goto('/user/bookings/new', { waitUntil: 'domcontentloaded' });
    await expect(page.getByText(/fill in the details to book your journey/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/add your mobile number to book a trip/i)).toHaveCount(0);
    await ctx.close();
  });
});
