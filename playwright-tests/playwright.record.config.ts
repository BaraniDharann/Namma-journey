import base from './playwright.config';

/**
 * Same suite as playwright.config.ts, but every test records a video and keeps its trace.
 *
 *   npx playwright test -c playwright.record.config.ts
 *
 * Stays HEADLESS on purpose. The headed config adds slowMo, and 08-live-tracking asserts
 * that coordinates advance inside fixed wall-clock windows — slowing the browser starves
 * those timers and fails tests that are actually fine. Headless still produces a clean
 * webm per test, which is what the report embeds.
 */
export default {
  ...base,
  use: {
    ...base.use,
    video: 'on' as const,
    trace: 'on' as const,
  },
};
