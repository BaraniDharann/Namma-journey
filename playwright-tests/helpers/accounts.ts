import { apiContext, freshIp, unique } from './api';
import { waitForOtp } from './db';

export type NewUser = { userId: string; email: string; mobile: string; name: string; password: string; token: string };

/**
 * Register a brand-new traveller through the real endpoints: request an OTP, read it from
 * the `otps` table (the only place it is observable), sign up, then log in for a token.
 */
export async function createUser(label = 'user'): Promise<NewUser> {
  const ids = unique();
  const password = 'E2eUser@123';
  const api = await apiContext({ ip: freshIp(`signup-${label}`) });
  try {
    const send = await api.post('/api/auth/otp/send', { data: { email: ids.userEmail } });
    if (send.status() !== 200) throw new Error(`otp/send ${send.status()}: ${(await send.text()).slice(0, 200)}`);

    const otp = await waitForOtp(ids.userEmail);
    const signup = await api.post('/api/auth/user/signup', {
      data: { email: ids.userEmail, name: `E2E ${label}`, mobile: ids.userMobile, otp, password },
    });
    if (signup.status() !== 201) throw new Error(`signup ${signup.status()}: ${(await signup.text()).slice(0, 200)}`);

    const login = await api.post('/api/auth/user/login', {
      data: { loginType: 'EMAIL', email: ids.userEmail, password },
    });
    if (login.status() !== 200) throw new Error(`login ${login.status()}: ${(await login.text()).slice(0, 200)}`);
    const body = await login.json();

    return {
      userId: body.userId,
      email: ids.userEmail,
      mobile: ids.userMobile,
      name: `E2E ${label}`,
      password,
      token: body.token,
    };
  } finally {
    await api.dispose();
  }
}
