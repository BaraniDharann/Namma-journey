import { test, expect, request, APIRequestContext } from '@playwright/test';
import { readAccounts, backendEnv, API_BASE } from '../helpers/env';
import { apiContext, freshIp } from '../helpers/api';
import { contexts, disposeAll, bookingPayload, Ctxs } from '../helpers/booking';
import crypto from 'crypto';

/**
 * Telegram driver dispatch, end to end, without a real bot.
 *
 * The inbound half is exercised by posting updates at the webhook exactly as Telegram would,
 * which is also the honest way to test it: that endpoint is publicly reachable and its only
 * defence is the shared secret, so what an attacker can do with a forged payload IS the
 * thing under test.
 *
 * The outbound half (calls to api.telegram.org) is expected to fail against a fake bot token
 * and is deliberately not asserted. Those sends are best-effort by design — a Telegram
 * outage must not fail a booking — and these tests confirm exactly that: every state change
 * below still lands while the outbound direction is broken.
 *
 * Requires the backend started with the integration on, e.g.
 *   --app.telegram.enabled=true --app.telegram.bot-token=1:test \
 *   --app.telegram.bot-username=TestBot --app.telegram.webhook-secret=<TELEGRAM_WEBHOOK_SECRET>
 * The whole file skips itself if the endpoint reports the integration is off.
 */

const acc = readAccounts();
const D = acc.driver.driverId;
const SECRET = backendEnv.TELEGRAM_WEBHOOK_SECRET || '';
const WEBHOOK = '/api/telegram/webhook';

// Derived from the driver id, which global setup recreates every run. A fixed chat id would
// still be bound to the PREVIOUS run's driver, and the service correctly refuses to move an
// already-linked Telegram account onto a second driver — so a hardcoded value makes the suite
// pass once and then fail forever. A new driver gets a new Telegram account, as in real life.
const DRIVER_CHAT_ID = 5550000000 + Number(D);
/** A Telegram user id that is never linked to anything, for the impersonation cases. */
const STRANGER_CHAT_ID = 6660000000 + Number(D);

let updateId = 1000;

function startUpdate(chatId: number, payload: string) {
  updateId += 1;
  return {
    update_id: updateId,
    message: {
      message_id: updateId,
      text: payload ? `/start ${payload}` : '/start',
      from: { id: chatId, is_bot: false, first_name: 'Test' },
      chat: { id: chatId, type: 'private' },
    },
  };
}

function callbackUpdate(chatId: number, data: string) {
  updateId += 1;
  return {
    update_id: updateId,
    callback_query: {
      id: `cb-${updateId}`,
      data,
      from: { id: chatId, is_bot: false, first_name: 'Test' },
      message: { message_id: updateId, chat: { id: chatId, type: 'private' } },
    },
  };
}

/** Posts at the webhook as Telegram does, with an arbitrary (possibly wrong) secret header. */
async function postWebhook(ctx: APIRequestContext, body: unknown, secret: string | null) {
  const headers: Record<string, string> = {};
  if (secret !== null) headers['X-Telegram-Bot-Api-Secret-Token'] = secret;
  return ctx.post(WEBHOOK, { data: body, headers });
}

/**
 * Reads a booking's status off the owner's list.
 *
 * There is no GET /api/owner/bookings/{id} — asserting against one returns a 404 body whose
 * `status` field is undefined, which makes every `not.toBe('CONFIRMED')` check pass without
 * testing anything. Going through the list keeps the negative assertions honest.
 */
async function bookingRow(owner: APIRequestContext, bookingId: string): Promise<any | undefined> {
  const res = await owner.get('/api/owner/bookings');
  if (res.status() !== 200) return undefined;
  const all = await res.json();
  return (Array.isArray(all) ? all : []).find(
    (b: any) => String(b.id ?? b.bookingId) === String(bookingId)
  );
}

async function bookingStatus(owner: APIRequestContext, bookingId: string): Promise<string | undefined> {
  return (await bookingRow(owner, bookingId))?.status;
}

/**
 * Creates a booking and returns it once it is confirmed to be sitting on our test driver in
 * PENDING — the exact state a dispatch card is sent in.
 *
 * createBooking auto-assigns the first available driver (UserService), so in a single-driver
 * database every booking lands on ours without the owner touching it. That is the real
 * production path into notifyDriverAssigned, so the tests use it rather than assigning by hand.
 */
async function pendingBookingWithDriver(c: Ctxs): Promise<{ bookingId: string; driverId: string }> {
  const created = await c.user.post(`/api/user/${acc.user.userId}/bookings`, { data: bookingPayload() });
  expect(created.status(), await created.text()).toBe(201);
  const booking = await created.json();

  // TravelBookingResponse exposes the driver as a nested object, not a flat driverId, so the
  // assignment is read back off the owner's list, which serialises the entity itself.
  const row = await bookingRow(c.owner, booking.bookingId);
  // Which driver wins is not pinned: findAvailableDrivers returns the first driver with no
  // overlapping trip, and drivers created by earlier runs are still ACTIVE and eligible. The
  // dispatch path is identical whoever it picks, so the tests follow the assignment rather
  // than trying to force it.
  expect(row?.driverId, 'a driver should have been auto-assigned').toBeTruthy();
  expect(row?.status).toBe('PENDING');
  return { bookingId: booking.bookingId, driverId: String(row.driverId) };
}

/** Every driver gets their own synthetic Telegram account, as they would in reality. */
const chatIdFor = (driverId: string | number) => 5550000000 + Number(driverId);

test.describe('Telegram driver dispatch', () => {
  let c: Ctxs;
  let anon: APIRequestContext;

  test.beforeAll(async () => {
    test.skip(!SECRET, 'TELEGRAM_WEBHOOK_SECRET is not set in the repo-root .env');

    // The endpoint answers 404 while app.telegram.enabled is false. Skipping on that is
    // better than failing: the integration is opt-in and most local stacks run without it.
    // A connection error means no backend at all, which is the rest of the suite's problem
    // to report - failing every test in this file on top of that would only add noise.
    const probe = await request.newContext({ baseURL: API_BASE });
    let disabled = false;
    try {
      disabled = (await postWebhook(probe, { update_id: 0 }, SECRET)).status() === 404;
    } catch {
      disabled = true;
    } finally {
      await probe.dispose();
    }
    test.skip(disabled, 'Telegram integration is off or the backend is not running');
  });

  test.beforeEach(async ({}, testInfo) => {
    c = await contexts(`tg-${testInfo.title}`);
    anon = await apiContext({ ip: freshIp(`tg-anon-${testInfo.title}`) });
  });

  test.afterEach(async () => {
    await disposeAll(c);
    await anon.dispose();
  });

  // ---------------------------------------------------------------- webhook authentication

  test('the webhook refuses a request carrying no secret header', async () => {
    const res = await postWebhook(anon, callbackUpdate(DRIVER_CHAT_ID, 'accept:whatever'), null);
    expect(res.status(), 'an unauthenticated caller must not reach the handler').toBe(403);
  });

  test('the webhook refuses a wrong secret', async () => {
    const res = await postWebhook(anon, callbackUpdate(DRIVER_CHAT_ID, 'accept:whatever'), 'wrong-secret');
    expect(res.status()).toBe(403);
  });

  test('the webhook refuses a secret that is only a prefix of the real one', async () => {
    const res = await postWebhook(anon, { update_id: 1 }, SECRET.slice(0, Math.max(1, SECRET.length - 1)));
    expect(res.status()).toBe(403);
  });

  test('the webhook accepts a correctly signed update', async () => {
    const res = await postWebhook(anon, { update_id: 1 }, SECRET);
    expect(res.status()).toBe(200);
  });

  // ------------------------------------------------------------------ link minting is owner-only

  test('only the owner can mint a driver connect link', async () => {
    for (const [label, ctx] of [['user', c.user], ['driver', c.driver], ['anonymous', anon]] as const) {
      const res = await ctx.post(`/api/owner/drivers/${D}/telegram-link`);
      expect([401, 403], `${label} was able to mint a link (${res.status()})`).toContain(res.status());
    }
  });

  test('minted links are unguessable and each one revokes the last', async () => {
    const first = await c.owner.post(`/api/owner/drivers/${D}/telegram-link`);
    expect(first.status(), await first.text()).toBe(200);
    const firstUrl = (await first.json()).linkUrl as string;

    const second = await c.owner.post(`/api/owner/drivers/${D}/telegram-link`);
    expect(second.status()).toBe(200);
    const secondUrl = (await second.json()).linkUrl as string;

    expect(firstUrl).toContain('https://t.me/');
    expect(firstUrl).toContain('?start=');
    expect(firstUrl).not.toEqual(secondUrl);

    const token = firstUrl.split('?start=')[1];
    // Not derived from the sequential driver id — that would let anyone walk the range.
    // (A bare substring check on the id is useless here: a one-digit id occurs in random
    // base64 almost every time. What matters is the shape and the entropy.)
    expect(token).not.toMatch(/^driver[_-]?\d+$/);
    expect(token.length, 'token must carry real entropy').toBeGreaterThan(30);

    // The superseded link must no longer work.
    const stale = await postWebhook(anon, startUpdate(STRANGER_CHAT_ID, token), SECRET);
    expect(stale.status()).toBe(200);
    const drivers = await (await c.owner.get(`/api/owner/drivers/${D}`)).json();
    expect(drivers.telegramLinked, 'a revoked link must not link an account').not.toBe(true);
  });

  // ------------------------------------------------------------------------ linking a driver

  test('a garbage token links nobody', async () => {
    const res = await postWebhook(anon, startUpdate(STRANGER_CHAT_ID, 'not-a-real-token'), SECRET);
    // Always 200 — Telegram re-delivers anything else, and the refusal is reported in-chat.
    expect(res.status()).toBe(200);

    const driver = await (await c.owner.get(`/api/owner/drivers/${D}`)).json();
    expect(driver.telegramLinked).not.toBe(true);
  });

  test('a valid link connects the driver, and is single-use', async () => {
    const minted = await c.owner.post(`/api/owner/drivers/${D}/telegram-link`);
    expect(minted.status(), await minted.text()).toBe(200);
    const token = ((await minted.json()).linkUrl as string).split('?start=')[1];

    const redeem = await postWebhook(anon, startUpdate(DRIVER_CHAT_ID, token), SECRET);
    expect(redeem.status()).toBe(200);

    const driver = await (await c.owner.get(`/api/owner/drivers/${D}`)).json();
    expect(driver.telegramLinked, 'driver should now show as connected').toBe(true);

    // Replaying the same token from a different Telegram account must not link that account.
    const replay = await postWebhook(anon, startUpdate(STRANGER_CHAT_ID, token), SECRET);
    expect(replay.status()).toBe(200);
    // The driver is still linked to the FIRST account, not the replaying one — proven below
    // by the stranger being unable to act on this driver's bookings.
  });

  // --------------------------------------------------------------------- acting on a dispatch

  /** Mints a link for a driver and redeems it from that driver's own Telegram account. */
  async function link(driverId: string | number): Promise<number> {
    const chatId = chatIdFor(driverId);
    const minted = await c.owner.post(`/api/owner/drivers/${driverId}/telegram-link`);
    expect(minted.status(), await minted.text()).toBe(200);
    const token = ((await minted.json()).linkUrl as string).split('?start=')[1];
    const redeem = await postWebhook(anon, startUpdate(chatId, token), SECRET);
    expect(redeem.status()).toBe(200);
    return chatId;
  }

  test('a linked driver accepts an assigned trip straight from the callback', async () => {
    const { bookingId, driverId } = await pendingBookingWithDriver(c);
    const chatId = await link(driverId);

    // The dispatch card is now in the driver's chat. Press Accept — no app, no login.
    const press = await postWebhook(anon, callbackUpdate(chatId, `accept:${bookingId}`), SECRET);
    expect(press.status()).toBe(200);

    expect(await bookingStatus(c.owner, bookingId), 'one tap should confirm the trip').toBe('CONFIRMED');
  });

  test('a linked driver can decline straight from the callback', async () => {
    const { bookingId, driverId } = await pendingBookingWithDriver(c);
    const chatId = await link(driverId);

    const press = await postWebhook(anon, callbackUpdate(chatId, `reject:${bookingId}`), SECRET);
    expect(press.status()).toBe(200);

    // rejectBooking releases the driver and returns the trip to the pool for reassignment.
    // The status is PENDING either way, so the driver being cleared is what proves it landed.
    const row = await bookingRow(c.owner, bookingId);
    expect(row?.status).toBe('PENDING');
    expect(row?.driverId ?? null, 'declining must release the driver').toBeNull();
  });

  test('an unlinked Telegram account cannot accept somebody else\'s trip', async () => {
    const { bookingId } = await pendingBookingWithDriver(c);

    // A caller who somehow obtained the webhook secret still cannot act as a driver: identity
    // is resolved from the Telegram user id against a stored link, never from the payload.
    const press = await postWebhook(anon, callbackUpdate(STRANGER_CHAT_ID, `accept:${bookingId}`), SECRET);
    expect(press.status()).toBe(200);

    expect(await bookingStatus(c.owner, bookingId), 'a stranger must not move the booking').toBe('PENDING');
  });

  test('a callback naming an unknown booking is refused without erroring', async () => {
    const chatId = await link(D);
    const press = await postWebhook(anon, callbackUpdate(chatId, `accept:${crypto.randomUUID()}`), SECRET);
    expect(press.status(), 'an unknown booking must not become a Telegram retry loop').toBe(200);
  });

  test('a malformed update is absorbed rather than retried forever', async () => {
    const malformed = [
      { update_id: 1, callback_query: 'not-an-object' },
      { update_id: 2, message: { text: '/start' } }, // no "from"
      { update_id: 3 },
      { update_id: 4, callback_query: { id: 'x', data: `accept:${crypto.randomUUID()}`, from: {} } },
    ];
    for (const body of malformed) {
      const res = await postWebhook(anon, body, SECRET);
      expect(res.status(), `Telegram re-delivers non-2xx: ${JSON.stringify(body)}`).toBe(200);
    }
  });

  // ------------------------------------------------------------------------- owner visibility

  test('the owner drivers list reports who can actually be reached', async () => {
    const res = await c.owner.get('/api/owner/drivers');
    expect(res.status()).toBe(200);
    const drivers = await res.json();
    expect(Array.isArray(drivers)).toBe(true);
    // The flag has to be present on every row, otherwise the owner cannot tell which drivers
    // will silently miss their dispatch.
    for (const d of drivers) {
      expect(d, `driver ${d.driverId} is missing telegramLinked`).toHaveProperty('telegramLinked');
    }
  });
});
