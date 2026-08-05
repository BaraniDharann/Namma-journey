import { Client } from 'pg';
import bcrypt from 'bcryptjs';
import { backendEnv } from './env';

/**
 * Direct Postgres access. Two things in this app are only reachable out-of-band:
 *   - the signup/first-login OTP (random 6 digits, emailed, also persisted to `otps`)
 *   - the driver's initial password (random, emailed, never returned by the API)
 * Reading them from the DB lets the suite drive the *real* auth flows end to end
 * instead of stubbing them out.
 */
function config() {
  const url = new URL(backendEnv.DB_URL.replace('jdbc:', ''));
  return {
    host: url.hostname,
    port: Number(url.port || 5432),
    database: url.pathname.slice(1),
    user: backendEnv.DB_USERNAME,
    password: backendEnv.DB_PASSWORD,
  };
}

export async function query<T = any>(sql: string, params: any[] = []): Promise<T[]> {
  const client = new Client(config());
  await client.connect();
  try {
    const res = await client.query(sql, params);
    return res.rows as T[];
  } finally {
    await client.end();
  }
}

/** Newest unverified, unexpired OTP for an email — exactly what OtpService.verifyOtp will match. */
export async function latestOtp(email: string): Promise<string> {
  const rows = await query<{ otp: string }>(
    `SELECT otp FROM otps
      WHERE lower(email) = lower($1) AND verified = false AND expiry_time > now()
      ORDER BY created_at DESC, id DESC LIMIT 1`,
    [email]
  );
  if (!rows.length) throw new Error(`No live OTP row found for ${email}`);
  return rows[0].otp;
}

/** Wait for an OTP to land (sendOtp writes synchronously, but be tolerant of clock/commit skew). */
export async function waitForOtp(email: string, timeoutMs = 15000): Promise<string> {
  const deadline = Date.now() + timeoutMs;
  let lastErr: unknown;
  while (Date.now() < deadline) {
    try {
      return await latestOtp(email);
    } catch (e) {
      lastErr = e;
      await new Promise((r) => setTimeout(r, 500));
    }
  }
  throw lastErr;
}

/** Stand in for the credentials email: give the new driver a password we know. */
export async function setDriverPassword(driverId: string | number, plaintext: string) {
  const hash = bcrypt.hashSync(plaintext, 10);
  await query('UPDATE drivers SET password = $1 WHERE id = $2', [hash, driverId]);
}

/**
 * Provision a throwaway owner with a password we know, so the owner login form can be tested
 * for real rather than by injecting a minted token.
 *
 * <p>Written straight to the table on purpose. POST /api/auth/owner/create-admin refuses once
 * any owner exists, and the alternative — resetting the existing owner's password — would
 * change the credentials of the account the operator actually signs in with.
 */
export async function createTestOwner(email: string, plaintext: string): Promise<string> {
  const hash = bcrypt.hashSync(plaintext, 10);
  const rows = await query(
    `INSERT INTO owners (email, password, role, created_at)
     VALUES ($1, $2, 'ROLE_OWNER', NOW())
     ON CONFLICT (email) DO UPDATE SET password = EXCLUDED.password
     RETURNING id`,
    [email, hash]
  );
  return String(rows[0].id);
}

export async function bookingRow(bookingId: string) {
  const rows = await query(`SELECT * FROM travel_bookings WHERE id = $1`, [bookingId]);
  return rows[0] || null;
}
