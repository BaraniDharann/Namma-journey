import base from './playwright.config';

/**
 * Same suite, but in a VISIBLE window driven by real Google Chrome rather than bundled
 * Chromium. Used to watch the live-tracking pipeline actually move on screen.
 *
 *   npx playwright test tests/08-live-tracking.spec.ts -c playwright.headed.config.ts
 *
 * slowMo is kept small deliberately: the tracking spec asserts that coordinates advance
 * within fixed wall-clock windows, so a large slowMo would starve those timers.
 */
export default {
  ...base,
  use: {
    ...base.use,
    channel: 'chrome',
    headless: false,
    launchOptions: { slowMo: 150, args: ['--disable-blink-features=AutomationControlled'] },
  },
};
