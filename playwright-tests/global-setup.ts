import fs from 'fs';
import path from 'path';
import { request } from '@playwright/test';
import { API_BASE, WEB_BASE, OWNER_ID, ACCOUNTS_FILE, Accounts } from './helpers/env';
import { apiContext, mintToken, unique, freshIp } from './helpers/api';
import { waitForOtp, setDriverPassword, createTestOwner, query } from './helpers/db';

/**
 * Creates the two brand-new accounts the whole suite runs on:
 *
 *   USER   — real signup: POST /api/auth/otp/send -> read the OTP out of the `otps`
 *            table -> POST /api/auth/user/signup -> POST /api/auth/user/login.
 *   DRIVER — real onboarding: owner POSTs /api/owner/drivers, then (standing in for the
 *            credentials email, which is the only way the generated password ever leaves
 *            the server) we set a known bcrypt password directly, then drive the genuine
 *            first-login flow: login -> firstLogin:true + OTP -> verify-otp -> login again.
 *
 * The OWNER is an existing seeded account whose password we don't hold, so its token is
 * minted with the backend's own JWT secret.
 */

async function waitForServer(url: string, label: string, timeoutMs = 120000) {
  const ctx = await request.newContext({ ignoreHTTPSErrors: true });
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const res = await ctx.get(url, { timeout: 5000 });
      if (res.status() < 500) {
        await ctx.dispose();
        return;
      }
    } catch {
      /* not up yet */
    }
    await new Promise((r) => setTimeout(r, 1500));
  }
  await ctx.dispose();
  throw new Error(`${label} not reachable at ${url} within ${timeoutMs}ms`);
}

export default async function globalSetup() {
  console.log('\n[setup] waiting for servers…');
  await waitForServer(`${API_BASE}/api/health`, 'backend');
  await waitForServer(WEB_BASE, 'frontend');

  const ids = unique();
  const ownerToken = mintToken(OWNER_ID, 'ROLE_OWNER');
  const owner = await apiContext({ token: ownerToken, ip: freshIp('setup-owner') });

  // Pricing must exist before any booking can be costed. Set both models.
  const pk = await owner.post(`/api/owner/pricing/set?pricePerKm=12&ownerId=${OWNER_ID}`);
  const ph = await owner.post(`/api/owner/pricing/set-hourly?pricePerHour=250&ownerId=${OWNER_ID}`);
  console.log(`[setup] pricing perKm=${pk.status()} perHour=${ph.status()}`);

  // A published travel package must exist, otherwise the package-booking spec skips itself and
  // the entire purchase path goes unverified while the run still reports green.
  const published = await owner.get('/api/public/packages');
  const publishedList = published.status() === 200 ? await published.json() : [];
  if (!Array.isArray(publishedList) || publishedList.length === 0) {
    const seeded = await owner.post(`/api/owner/packages?ownerId=${OWNER_ID}`, {
      data: {
        name: 'E2E Seed Package',
        description: 'Published by global-setup so the package booking path is always exercised',
        category: 'HILL_STATION',
        state: 'Tamil Nadu',
        durationDays: 3,
        durationNights: 2,
        pricePerPerson: 4999,
        maxGroupSize: 12,
        placesIncluded: ['Ooty', 'Coonoor'],
        foodIncluded: true,
        accommodationIncluded: true,
        transportIncluded: true,
        highlights: ['Toy train', 'Tea estates'],
        itinerary: [{ day: 1, title: 'Arrival', description: 'Check in', activities: ['Rest'] }],
      },
    });
    // createPackage sets active = true, so a new package is already publicly bookable.
    // Do NOT call /toggle here — it flips the flag, which would unpublish it again.
    if (seeded.status() === 201) {
      const pkgId = (await seeded.json()).id;
      console.log(`[setup] seeded travel package ${pkgId}`);
    } else {
      console.log(`[setup] WARNING: could not seed a travel package (${seeded.status()})`);
    }
  } else {
    console.log(`[setup] ${publishedList.length} published package(s) already present`);
  }

  // ---------------------------------------------------------------- USER
  const userPassword = 'E2eUser@123';
  const auth = await apiContext({ ip: freshIp('setup-user-auth') });

  const otpSend = await auth.post('/api/auth/otp/send', { data: { email: ids.userEmail } });
  if (otpSend.status() !== 200) {
    throw new Error(`otp/send failed: ${otpSend.status()} ${(await otpSend.text()).slice(0, 300)}`);
  }
  const userOtp = await waitForOtp(ids.userEmail);
  console.log(`[setup] user OTP from DB: ${userOtp}`);

  const signup = await auth.post('/api/auth/user/signup', {
    data: { email: ids.userEmail, name: 'E2E Test User', mobile: ids.userMobile, otp: userOtp, password: userPassword },
  });
  if (signup.status() !== 201) {
    throw new Error(`user signup failed: ${signup.status()} ${(await signup.text()).slice(0, 400)}`);
  }
  const signupBody = await signup.json();

  const userLogin = await auth.post('/api/auth/user/login', {
    data: { loginType: 'EMAIL', email: ids.userEmail, password: userPassword },
  });
  if (userLogin.status() !== 200) {
    throw new Error(`user login failed: ${userLogin.status()} ${(await userLogin.text()).slice(0, 300)}`);
  }
  const userBody = await userLogin.json();
  console.log(`[setup] user created: ${ids.userEmail} (${userBody.userId})`);

  // -------------------------------------------------------------- DRIVER
  const seedPassword = 'SeedPass@123';
  const driverPassword = 'E2eDriver@123';

  // The React owner form posts multipart/form-data (AdminController). Fall back to the
  // form-encoded OwnerController variant if multipart is rejected.
  let created = await owner.post('/api/owner/drivers', {
    multipart: {
      name: 'E2E Test Driver',
      mobile: ids.driverMobile,
      email: ids.driverEmail,
      licenseNumber: ids.license,
      aadhaarNumber: ids.aadhaar,
    },
  });
  if (created.status() >= 400) {
    console.log(`[setup] multipart driver create -> ${created.status()}, retrying form-encoded`);
    created = await owner.post('/api/owner/drivers', {
      form: {
        name: 'E2E Test Driver',
        mobile: ids.driverMobile,
        email: ids.driverEmail,
        licenseNumber: ids.license,
        aadhaarNumber: ids.aadhaar,
      },
    });
  }
  if (created.status() !== 201) {
    throw new Error(`driver create failed: ${created.status()} ${(await created.text()).slice(0, 400)}`);
  }
  const driverRow = await created.json();
  const driverId = String(driverRow.id);

  // Stand in for the credentials email.
  await setDriverPassword(driverId, seedPassword);

  const dAuth = await apiContext({ ip: freshIp('setup-driver-auth') });
  const first = await dAuth.post('/api/auth/driver/login', { data: { mobile: ids.driverMobile, password: seedPassword } });
  const firstBody = await first.json();
  if (first.status() !== 200 || firstBody.firstLogin !== true) {
    throw new Error(`expected firstLogin flow, got ${first.status()} ${JSON.stringify(firstBody).slice(0, 300)}`);
  }

  const driverOtp = await waitForOtp(ids.driverEmail);
  console.log(`[setup] driver OTP from DB: ${driverOtp}`);

  const verify = await dAuth.post('/api/auth/driver/verify-otp', {
    data: { email: ids.driverEmail, otp: driverOtp, newPassword: driverPassword },
  });
  if (verify.status() !== 200) {
    throw new Error(`driver verify-otp failed: ${verify.status()} ${(await verify.text()).slice(0, 300)}`);
  }

  const dLogin = await dAuth.post('/api/auth/driver/login', { data: { mobile: ids.driverMobile, password: driverPassword } });
  const dBody = await dLogin.json();
  if (dLogin.status() !== 200 || !dBody.token) {
    throw new Error(`driver login after verify failed: ${dLogin.status()} ${JSON.stringify(dBody).slice(0, 300)}`);
  }
  console.log(`[setup] driver created: ${ids.driverEmail} (id ${driverId})`);

  // --------------------------------------------------------------- OWNER
  // A throwaway owner with a known password, so the owner login form is covered by a real
  // sign-in instead of an injected token. Deliberately NOT the operator's own account.
  const testOwnerEmail = `e2e.owner.${ids.userMobile}@njtest.local`;
  const testOwnerPassword = 'E2eOwner@123';
  const testOwnerId = await createTestOwner(testOwnerEmail, testOwnerPassword);
  console.log(`[setup] test owner created: ${testOwnerEmail} (id ${testOwnerId})`);

  const accounts: Accounts = {
    user: {
      userId: userBody.userId || signupBody.userId,
      email: ids.userEmail,
      mobile: ids.userMobile,
      name: 'E2E Test User',
      password: userPassword,
      token: userBody.token,
    },
    driver: {
      driverId,
      email: ids.driverEmail,
      mobile: ids.driverMobile,
      name: 'E2E Test Driver',
      password: driverPassword,
      token: dBody.token,
    },
    owner: {
      ownerId: OWNER_ID,
      token: ownerToken,
      testOwnerId: testOwnerId,
      testEmail: testOwnerEmail,
      testPassword: testOwnerPassword,
    },
    createdAt: new Date().toISOString(),
  };

  fs.mkdirSync(path.dirname(ACCOUNTS_FILE), { recursive: true });
  fs.writeFileSync(ACCOUNTS_FILE, JSON.stringify(accounts, null, 2));
  // Booking date windows start fresh each run; the driver is new, so nothing can overlap.
  fs.writeFileSync(path.join(path.dirname(ACCOUNTS_FILE), 'slot.txt'), '0');

  // Sanity: both rows really exist.
  const [u] = await query('SELECT id, email, role FROM users WHERE id = $1', [accounts.user.userId]);
  const [d] = await query('SELECT id, email, first_login, email_verified FROM drivers WHERE id = $1', [driverId]);
  console.log(`[setup] verified in DB -> user ${u?.email} (${u?.role}), driver ${d?.email} firstLogin=${d?.first_login} emailVerified=${d?.email_verified}`);

  await owner.dispose();
  await auth.dispose();
  await dAuth.dispose();
}
