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

/**
 * The single owner account, taken from the backend's own .env so the suite and the running
 * application always agree on who the owner is.
 *
 * This used to mint a throwaway owner per run (and default OWNER_ID to a hardcoded '2'), which
 * left one abandoned ROLE_OWNER row behind every time the suite ran — 31 of them by the time
 * anyone looked, each with the same password baked into this repo. There is exactly one owner
 * now, and the suite signs in as it rather than manufacturing more.
 */
export const OWNER_EMAIL = process.env.E2E_OWNER_EMAIL || backendEnv.OWNER_EMAIL || '';
export const OWNER_PASSWORD = process.env.E2E_OWNER_PASSWORD || backendEnv.OWNER_PASSWORD || '';

export const ACCOUNTS_FILE = path.join(__dirname, '..', 'test-data', 'accounts.json');

export type Accounts = {
  user: { userId: string; email: string; mobile: string; name: string; password: string; token: string };
  driver: { driverId: string; email: string; mobile: string; name: string; password: string; token: string };
  /** The one owner. `token` comes from a real sign-in, not a minted one. */
  owner: { ownerId: string; email: string; password: string; token: string };
  createdAt: string;
};

export function readAccounts(): Accounts {
  if (!fs.existsSync(ACCOUNTS_FILE)) {
    throw new Error(`${ACCOUNTS_FILE} missing — global setup did not run or failed.`);
  }
  return JSON.parse(fs.readFileSync(ACCOUNTS_FILE, 'utf8'));
}
