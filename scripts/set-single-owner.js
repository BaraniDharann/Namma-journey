/*
 * Makes the platform have exactly one owner account.
 *
 * Reads DB_URL / DB_USERNAME / DB_PASSWORD / OWNER_EMAIL / OWNER_PASSWORD from the backend .env.
 * No credentials are hardcoded here.
 *
 * Why this exists: global-setup.ts used to create a throwaway ROLE_OWNER row on every E2E run,
 * all sharing one password committed to this repo. Thirty-one of them accumulated. The suite no
 * longer does that, and this script collapses the existing rows down to the single real owner.
 *
 * Usage — from anywhere in the repo:
 *
 *   node scripts/set-single-owner.js show     # list owners, change nothing
 *   node scripts/set-single-owner.js create   # back up, create/reset the owner, repoint audit cols
 *   node scripts/set-single-owner.js prune    # DESTRUCTIVE: delete every other owner row
 *   node scripts/set-single-owner.js restore  # put the backed-up rows back
 *
 * `create` is safe and reversible on its own. `prune` refuses to run unless a backup exists and
 * the owner it is keeping resolves to exactly one row.
 */
const fs = require('fs');
const path = require('path');
const { createRequire } = require('module');

const ROOT = path.resolve(__dirname, '..');

// `pg` and `bcryptjs` are dependencies of the E2E suite, not of the repo root. require() resolves
// from this file's own directory, so anchor the lookup at playwright-tests instead of relying on
// the caller's working directory.
const deps = createRequire(path.join(ROOT, 'playwright-tests', 'package.json'));
const { Client } = deps('pg');
const bcrypt = deps('bcryptjs');
const ENV_PATH = process.env.NJ_ENV_PATH || path.join(ROOT, '.env');
const BACKUP = path.join(ROOT, 'scripts', 'owners-backup.json');

function loadEnv(file) {
  const env = {};
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)$/);
    if (m) env[m[1]] = m[2].trim();
  }
  return env;
}

const env = loadEnv(ENV_PATH);
for (const key of ['DB_URL', 'DB_USERNAME', 'OWNER_EMAIL', 'OWNER_PASSWORD']) {
  if (!env[key]) {
    console.error(`${key} is not set in ${ENV_PATH}`);
    process.exit(1);
  }
}

const OWNER_EMAIL = env.OWNER_EMAIL;
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

async function show(c) {
  const rows = (await c.query('SELECT id, email, role FROM owners ORDER BY id::int')).rows;
  console.log(`--- owners (${rows.length}) ---`);
  console.table(rows);
  return rows;
}

async function create(c) {
  const before = (await c.query('SELECT id, email, password, role, created_at FROM owners ORDER BY id::int')).rows;
  fs.writeFileSync(BACKUP, JSON.stringify(before, null, 2));
  console.log(`backed up ${before.length} owner row(s) -> ${BACKUP}`);

  const hash = bcrypt.hashSync(env.OWNER_PASSWORD, 10);
  const up = await c.query(
    `INSERT INTO owners (email, password, role, created_at)
     VALUES ($1, $2, 'ROLE_OWNER', NOW())
     ON CONFLICT (email) DO UPDATE SET password = EXCLUDED.password, role = 'ROLE_OWNER'
     RETURNING id`,
    [OWNER_EMAIL, hash]
  );
  const id = String(up.rows[0].id);
  console.log(`owner ready: ${OWNER_EMAIL} (id ${id})`);

  // pricing_config.updated_by and travel_packages.created_by hold an owner id as a plain column
  // with no foreign key, so once the other owners go they would reference rows that do not exist.
  const pc = await c.query('UPDATE pricing_config SET updated_by = $1', [id]);
  const tp = await c.query('UPDATE travel_packages SET created_by = $1', [id]);
  console.log(`repointed pricing_config.updated_by=${pc.rowCount}, travel_packages.created_by=${tp.rowCount}`);

  await show(c);
}

async function prune(c) {
  if (!fs.existsSync(BACKUP)) throw new Error('no backup found — run `create` first');
  const keep = (await c.query('SELECT id FROM owners WHERE email = $1', [OWNER_EMAIL])).rows;
  if (keep.length !== 1) {
    throw new Error(`refusing to prune: expected exactly 1 row for ${OWNER_EMAIL}, found ${keep.length}`);
  }
  const res = await c.query('DELETE FROM owners WHERE email <> $1', [OWNER_EMAIL]);
  console.log(`deleted ${res.rowCount} owner row(s); kept ${OWNER_EMAIL} (id ${keep[0].id})`);
  await show(c);
}

async function restore(c) {
  const rows = JSON.parse(fs.readFileSync(BACKUP, 'utf8'));
  for (const r of rows) {
    await c.query(
      `INSERT INTO owners (id, email, password, role, created_at)
       VALUES ($1,$2,$3,$4,$5) ON CONFLICT (id) DO NOTHING`,
      [r.id, r.email, r.password, r.role, r.created_at]
    );
  }
  console.log(`restored ${rows.length} row(s) from ${BACKUP}`);
  await show(c);
}

const COMMANDS = { show, create, prune, restore };

async function main() {
  const cmd = process.argv[2];
  const fn = COMMANDS[cmd];
  if (!fn) {
    console.error('usage: node set-single-owner.js show|create|prune|restore');
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
