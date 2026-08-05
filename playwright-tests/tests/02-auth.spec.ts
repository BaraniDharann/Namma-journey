import { test, expect } from '@playwright/test';
import { apiContext, freshIp, unique } from '../helpers/api';
import { readAccounts } from '../helpers/env';
import { waitForOtp } from '../helpers/db';

/**
 * The happy-path signup/onboarding for both roles already ran in global setup (that is how
 * the accounts under test were born). These specs re-verify login and cover the rejection
 * paths, which is where auth bugs actually live.
 */
test.describe('Authentication', () => {
  const acc = readAccounts();

  test('newly created user can log in with email + password', async () => {
    const api = await apiContext({ ip: freshIp('auth-login-ok') });
    const res = await api.post('/api/auth/user/login', {
      data: { loginType: 'EMAIL', email: acc.user.email, password: acc.user.password },
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.token).toBeTruthy();
    expect(body.role).toBe('ROLE_USER');
    expect(body.userId).toBe(acc.user.userId);
    expect(body.email).toBe(acc.user.email);
    await api.dispose();
  });

  test('user login rejects a wrong password', async () => {
    const api = await apiContext({ ip: freshIp('auth-login-bad') });
    const res = await api.post('/api/auth/user/login', {
      data: { loginType: 'EMAIL', email: acc.user.email, password: 'definitely-wrong' },
    });
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
    await api.dispose();
  });

  test('user login rejects an unknown email', async () => {
    const api = await apiContext({ ip: freshIp('auth-login-nouser') });
    const res = await api.post('/api/auth/user/login', {
      data: { loginType: 'EMAIL', email: `nobody.${Date.now()}@njtest.local`, password: 'whatever' },
    });
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
    await api.dispose();
  });

  test('signup rejects a duplicate email', async () => {
    const api = await apiContext({ ip: freshIp('auth-dupe') });
    const send = await api.post('/api/auth/otp/send', { data: { email: acc.user.email } });
    expect(send.status()).toBe(200);
    const otp = await waitForOtp(acc.user.email);

    const res = await api.post('/api/auth/user/signup', {
      data: { email: acc.user.email, name: 'Dupe', mobile: `9${Date.now().toString().slice(-9)}`, otp, password: 'Whatever@123' },
    });
    expect(res.status()).toBe(400);
    expect((await res.text()).toLowerCase()).toContain('already registered');
    await api.dispose();
  });

  test('signup rejects a wrong OTP', async () => {
    const api = await apiContext({ ip: freshIp('auth-badotp') });
    const ids = unique();
    const send = await api.post('/api/auth/otp/send', { data: { email: ids.userEmail } });
    expect(send.status()).toBe(200);

    const res = await api.post('/api/auth/user/signup', {
      data: { email: ids.userEmail, name: 'Bad OTP', mobile: ids.userMobile, otp: '000000', password: 'Whatever@123' },
    });
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect((await res.text()).toLowerCase()).toMatch(/otp/);
    await api.dispose();
  });

  test('signup rejects a malformed email and missing fields', async () => {
    const api = await apiContext({ ip: freshIp('auth-validation') });
    const bad = await api.post('/api/auth/user/signup', {
      data: { email: 'not-an-email', name: 'X', mobile: '9000000001', otp: '123456', password: 'x' },
    });
    expect(bad.status()).toBe(400);

    const missing = await api.post('/api/auth/user/signup', { data: { email: 'a@b.com' } });
    expect(missing.status()).toBe(400);
    await api.dispose();
  });

  test('newly onboarded driver logs in with the password set via OTP', async () => {
    const api = await apiContext({ ip: freshIp('auth-driver-ok') });
    const res = await api.post('/api/auth/driver/login', {
      data: { mobile: acc.driver.mobile, password: acc.driver.password },
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.firstLogin).toBe(false);
    expect(body.token).toBeTruthy();
    expect(String(body.userId)).toBe(acc.driver.driverId);
    await api.dispose();
  });

  test('driver login rejects a wrong password and an unknown mobile', async () => {
    const api = await apiContext({ ip: freshIp('auth-driver-bad') });
    const wrong = await api.post('/api/auth/driver/login', {
      data: { mobile: acc.driver.mobile, password: 'nope-not-it' },
    });
    expect(wrong.status()).toBeGreaterThanOrEqual(400);
    expect(wrong.status()).toBeLessThan(500);

    const unknown = await api.post('/api/auth/driver/login', { data: { mobile: '6000000000', password: 'x' } });
    expect(unknown.status()).toBeGreaterThanOrEqual(400);
    expect(unknown.status()).toBeLessThan(500);
    await api.dispose();
  });

  test('driver password reset OTP request does not leak whether a mobile exists', async () => {
    const api = await apiContext({ ip: freshIp('auth-reset') });
    const known = await api.post('/api/auth/driver/request-reset-otp', { data: { mobile: acc.driver.mobile } });
    const unknown = await api.post('/api/auth/driver/request-reset-otp', { data: { mobile: '6000000001' } });

    expect(known.status()).toBe(200);
    expect(unknown.status()).toBe(200);
    expect(JSON.stringify(await known.json())).toBe(JSON.stringify(await unknown.json()));
    await api.dispose();
  });

  test('a superseded OTP is rejected and only the newest one works', async () => {
    const api = await apiContext({ ip: freshIp('auth-otp-newest') });
    const ids = unique();

    expect((await api.post('/api/auth/otp/send', { data: { email: ids.userEmail } })).status()).toBe(200);
    const firstOtp = await waitForOtp(ids.userEmail);

    // Re-request: verifyOtp matches only the newest unverified row, so the first code dies.
    expect((await api.post('/api/auth/otp/send', { data: { email: ids.userEmail } })).status()).toBe(200);
    let secondOtp = firstOtp;
    for (let i = 0; i < 20 && secondOtp === firstOtp; i++) {
      await new Promise((r) => setTimeout(r, 300));
      secondOtp = await waitForOtp(ids.userEmail);
    }
    test.skip(secondOtp === firstOtp, 'both OTPs collided on the same random value');

    const stale = await api.post('/api/auth/user/signup', {
      data: { email: ids.userEmail, name: 'Stale', mobile: ids.userMobile, otp: firstOtp, password: 'Whatever@123' },
    });
    expect(stale.status(), 'superseded OTP must not be accepted').toBeGreaterThanOrEqual(400);

    const fresh = await api.post('/api/auth/user/signup', {
      data: { email: ids.userEmail, name: 'Fresh', mobile: ids.userMobile, otp: secondOtp, password: 'Whatever@123' },
    });
    expect(fresh.status()).toBe(201);
    await api.dispose();
  });
});
