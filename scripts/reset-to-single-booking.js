/*
 * Empties the database of accumulated test data, keeping exactly one real trip.
 *
 * The database had picked up months of E2E residue — 410 bookings, 131 users, 37 drivers, 1768
 * notifications, 218 OTPs — which is what made the owner dashboard unreadable. This keeps the one
 * genuine customer booking and clears everything else.
 *
 * KEPT:
 *   users             the customer who made the kept booking
 *   drivers           the driver assigned to it (so the trip still shows a driver)
 *   travel_bookings   the kept booking
 *   notifications     only the ones raised for the kept booking
 *   travel_packages   the catalogue (so the public Packages page is not empty)
 *   pricing_config    the newest row only — OwnerService reads findTopByOrderByUpdatedAtDesc()
 *   owners            untouched (see set-single-owner.js)
 *
 * CLEARED:
 *   every other user, driver and booking; all payments, package bookings, OTPs,
 *   driver rejections, trip photos, reviews, and older pricing rows.
 *
 * There are no foreign keys in this schema, so nothing cascades and nothing protects you from a
 * dangling reference — which is exactly why the driver on the kept booking is kept too.
 *
 * Usage (run from anywhere in the repo; take a pg_dump first):
 *
 *   node scripts/reset-to-single-booking.js plan    # show what would change, touch nothing
 *   node scripts/reset-to-single-booking.js apply   # do it, in one transaction
 */
const fs = require('fs');
const path = require('path');
const { createRequire } = require('module');

const ROOT = path.resolve(__dirname, '..');
const deps = createRequire(path.join(ROOT, 'playwright-tests', 'package.json'));
const { Client } = deps('pg');

// ---- what survives -------------------------------------------------------
const KEEP_BOOKING_ID = '49d2a06d-fc2d-4257-a47d-f9bca81ec44e'; // Barani, Kanchipuram
const KEEP_USER_ID = '4840038f-fb65-4490-a433-f42678df4a19';    // Barani
const KEEP_DRIVER_ID = '23';                                     // assigned to that booking
// --------------------------------------------------------------------------

const ENV_PATH = process.env.NJ_ENV_PATH || path.join(ROOT, '.env');
const env = {};
for (const line of fs.readFileSync(ENV_PATH, 'utf8').split(/\r?\n/)) {
  const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)$/);
  if (m) env[m[1]] = m[2].trim();
}
const url = new URL(env.DB_URL.replace('jdbc:', ''));

function client() {
  return new Client({
    host: url.hostname,
    port: Number(url.port || 5432),
    database: url.pathname.slice(1),
    user: env.DB_USERNAME,
    password: env.DB_PASSWORD,
  });
}

/** Each step: a table, the rows that survive, and the WHERE clause that removes the rest. */
const STEPS = [
  { table: 'travel_bookings', keep: 'id = $1', params: [KEEP_BOOKING_ID] },
  { table: 'users', keep: 'id = $1', params: [KEEP_USER_ID] },
  { table: 'drivers', keep: 'id = $1', params: [KEEP_DRIVER_ID] },
  { table: 'notifications', keep: 'booking_id = $1', params: [KEEP_BOOKING_ID] },
  { table: 'pricing_config', keep: 'id = (SELECT id FROM pricing_config ORDER BY updated_at DESC LIMIT 1)', params: [] },
  { table: 'payments', keep: 'false', params: [] },
  { table: 'package_bookings', keep: 'false', params: [] },
  { table: 'otps', keep: 'false', params: [] },
  { table: 'booking_driver_rejections', keep: 'false', params: [] },
  { table: 'trip_driver_photos', keep: 'false', params: [] },
  { table: 'reviews', keep: 'false', params: [] },
];

async function counts(c) {
  const out = [];
  for (const s of STEPS) {
    const total = (await c.query(`SELECT count(*)::int n FROM ${s.table}`)).rows[0].n;
    const kept = (await c.query(`SELECT count(*)::int n FROM ${s.table} WHERE ${s.keep}`, s.params)).rows[0].n;
    out.push({ table: s.table, before: total, keeps: kept, deletes: total - kept });
  }
  return out;
}

async function plan(c) {
  console.table(await counts(c));
  console.log('\nUntouched: owners, travel_packages, flyway_schema_history');
}

async function apply(c) {
  const before = await counts(c);
  console.log('--- before ---');
  console.table(before);

  // Sanity: refuse to run if the rows we are keeping are not actually there. Without this a typo
  // in an id would quietly wipe the table instead of keeping one row.
  for (const s of STEPS) {
    if (s.keep === 'false') continue;
    const kept = (await c.query(`SELECT count(*)::int n FROM ${s.table} WHERE ${s.keep}`, s.params)).rows[0].n;
    if (kept < 1) throw new Error(`refusing to run: nothing matches the keep rule for ${s.table}`);
  }

  await c.query('BEGIN');
  try {
    for (const s of STEPS) {
      const res = await c.query(`DELETE FROM ${s.table} WHERE NOT (${s.keep})`, s.params);
      console.log(`${s.table}: deleted ${res.rowCount}`);
    }
    await c.query('COMMIT');
  } catch (e) {
    await c.query('ROLLBACK');
    throw e;
  }

  console.log('\n--- after ---');
  console.table(await counts(c));
}

async function main() {
  const cmd = process.argv[2];
  const fn = { plan, apply }[cmd];
  if (!fn) {
    console.error('usage: node reset-to-single-booking.js plan|apply');
    process.exit(1);
  }
  const c = client();
  await c.connect();
  try {
    await fn(c);
  } finally {
    await c.end();
  }
}

main().catch((e) => {
  console.error('ERR', e.message);
  process.exit(1);
});
