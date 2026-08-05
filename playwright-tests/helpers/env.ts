import fs from 'fs';
import path from 'path';

/** Repo root — playwright-tests/ lives directly under it. */
export const ROOT = path.resolve(__dirname, '..', '..');

/** Backend .env, parsed once. The suite reads DB creds + JWT secret from here so it
 *  always matches whatever the running backend is using. */
export const backendEnv: Record<string, string> = (() => {
  const out: Record<string, string> = {};
  const file = path.join(ROOT, '.env');
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)$/);
    if (m) out[m[1]] = m[2].trim();
  }
  return out;
})();

export const API_BASE = process.env.E2E_API_BASE || 'http://localhost:8080';
export const WEB_BASE = process.env.E2E_WEB_BASE || 'http://localhost:5173';

/** Known owner seeded in this database (owners table has exactly one row). */
export const OWNER_ID = process.env.E2E_OWNER_ID || '2';

export const ACCOUNTS_FILE = path.join(__dirname, '..', 'test-data', 'accounts.json');

export type Accounts = {
  user: { userId: string; email: string; mobile: string; name: string; password: string; token: string };
  driver: { driverId: string; email: string; mobile: string; name: string; password: string; token: string };
  // ownerId/token are the seeded operator account, whose password the suite does not hold, so
  // its token is minted with the backend secret. testEmail/testPassword belong to a separate
  // throwaway owner created by global setup purely so the owner login FORM can be exercised
  // for real — resetting the operator's own password would lock them out of their app.
  owner: { ownerId: string; token: string; testOwnerId: string; testEmail: string; testPassword: string };
  createdAt: string;
};

export function readAccounts(): Accounts {
  if (!fs.existsSync(ACCOUNTS_FILE)) {
    throw new Error(`${ACCOUNTS_FILE} missing — global setup did not run or failed.`);
  }
  return JSON.parse(fs.readFileSync(ACCOUNTS_FILE, 'utf8'));
}
