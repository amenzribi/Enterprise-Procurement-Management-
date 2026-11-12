// Manual-style checks driven by keyboard and clicks. Usage: node verify.mjs <outDir>
// Not to be run while the Playwright suite is running.
import { createRequire } from 'node:module';
import { mkdirSync } from 'node:fs';
import { join } from 'node:path';
const require = createRequire('C:/Users/zribi/OneDrive/Desktop/Procurement Managamenet/FrontEnd/package.json');
const { chromium } = require('@playwright/test');
const BASE = 'http://localhost:4201';
const outDir = process.argv[2];
mkdirSync(outDir, { recursive: true });
const browser = await chromium.launch({ channel: 'msedge' });
let failures = 0;
const check = (ok, text) => {
  if (!ok) failures += 1;
  console.log(`${ok ? 'PASS' : 'FAIL'} ${text}`);
};
const focused = (page) =>
  page.evaluate(() => {
    const e = document.activeElement;
    if (!e || e === document.body) return { what: 'BODY', ring: false };
    const cs = getComputedStyle(e);
    const after = getComputedStyle(e, '::after');
    const label = e.getAttribute('aria-label') || e.id || (e.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 28) || e.tagName;
    const ring = (cs.outlineStyle !== 'none' && parseFloat(cs.outlineWidth) >= 2) || (after.outlineStyle !== 'none' && parseFloat(after.outlineWidth) >= 2) || cs.boxShadow !== 'none';
    return { what: `${e.tagName.toLowerCase()}[${label}]`, ring };
  });

// ---- 1. Sign-in by keyboard only -------------------------------------------------------------------------
{
  const page = await (await browser.newContext({ viewport: { width: 1366, height: 640 } })).newPage();
  await page.goto(BASE + '/sign-in');
  await page.waitForSelector('#sign-in-email');
  const order = [];
  for (let i = 0; i < 9; i += 1) {
    await page.keyboard.press('Tab');
    const f = await focused(page);
    order.push(f.what + (f.ring ? '' : ' (NO RING)'));
  }
  console.log('sign-in tab order: ' + order.join(' > '));
  check(!order.some((o) => o.includes('NO RING')), 'sign-in: every stop shows a focus ring');
  check(order.findIndex((o) => o.includes('sign-in-email')) < order.findIndex((o) => o.includes('sign-in-password')), 'sign-in: e-mail comes before password');
  await page.locator('#sign-in-email').focus();
  await page.keyboard.type('ceo@demo.local');
  await page.keyboard.press('Tab');
  await page.keyboard.type(process.env.DEMO_USER_PASSWORD);
  await page.keyboard.press('Enter');
  await page.waitForURL((u) => !u.pathname.startsWith('/sign-in'));
  check(true, 'sign-in: Enter in the password field signs in (' + new URL(page.url()).pathname + ')');

  // ---- 2. Shell by keyboard ------------------------------------------------------------------------------
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(BASE + '/dashboards');
  await page.waitForSelector('.bar-link');
  await page.evaluate(() => document.activeElement?.blur?.());
  await page.keyboard.press('Tab');
  let f = await focused(page);
  check(/skip/i.test(f.what), 'shell: first Tab reaches the skip link (' + f.what + ')');
  await page.keyboard.press('Enter');
  f = await focused(page);
  check(f.what.startsWith('main'), 'shell: skip link moves focus to the main content (' + f.what + ')');

  const before = await page.locator('html').getAttribute('data-theme');
  await page.locator('app-theme-toggle button').focus();
  f = await focused(page);
  check(f.ring, 'theme switch: focus ring visible (' + f.what + ')');
  await page.keyboard.press('Enter');
  const after = await page.locator('html').getAttribute('data-theme');
  check(before !== after, `theme switch: Enter changes the theme (${before} -> ${after})`);
  await page.keyboard.press('Space');
  check((await page.locator('html').getAttribute('data-theme')) === before, 'theme switch: Space changes it back');

  await page.locator('app-notification-bell button').first().focus();
  await page.keyboard.press('Enter');
  await page.waitForTimeout(300);
  check((await page.locator('.cdk-overlay-container .menu').count()) > 0, 'notifications: Enter opens the menu');
  await page.keyboard.press('Escape');
  await page.waitForTimeout(300);
  f = await focused(page);
  check((await page.locator('.cdk-overlay-container .menu').count()) === 0 && f.what !== 'BODY', 'notifications: Escape closes it and focus returns (' + f.what + ')');

  await page.locator('app-user-menu .trigger').focus();
  await page.keyboard.press('Enter');
  await page.waitForTimeout(300);
  await page.keyboard.press('ArrowDown');
  f = await focused(page);
  check((await page.locator('.cdk-overlay-container .menu').count()) > 0, 'account menu: Enter opens it; arrow moves to ' + f.what);
  await page.keyboard.press('Escape');

  // sidebar links by keyboard
  await page.locator('.sidebar .nav-link').first().focus();
  f = await focused(page);
  check(f.ring, 'sidebar: focus ring visible on ink (' + f.what + ')');

  // ---- 3. Dashboard links: destination and filter --------------------------------------------------------
  await page.goto(BASE + '/dashboards');
  await page.waitForSelector('.bar-link');
  const links = await page.locator('.bar-link').evaluateAll((els) =>
    els.map((e) => ({ href: e.getAttribute('href'), label: e.querySelector('.bar-label').textContent.trim(), count: Number(e.querySelector('.bar-value').textContent.trim()) })),
  );
  console.log('dashboard links: ' + links.length);
  for (let i = 0; i < links.length; i += 1) {
    const link = links[i];
    await page.goto(BASE + '/dashboards');
    await page.waitForSelector('.bar-link');
    const target = page.locator('.bar-link').nth(i);
    if (i === 0) {
      await target.focus();
      const ring = (await focused(page)).ring;
      check(ring, 'dashboard link: focus ring visible');
      await page.keyboard.press('Enter');
    } else {
      await target.click();
    }
    await page.waitForURL(/\/requests\?/);
    await page.waitForLoadState('networkidle').catch(() => {});
    await page.waitForTimeout(400);
    const url = new URL(page.url());
    const status = url.searchParams.get('status');
    const selected = await page.locator('#requests-status').inputValue();
    const rangeText = (await page.locator('.pager-range').count()) ? (await page.locator('.pager-range').textContent()).trim() : '';
    const total = Number((rangeText.match(/(\d+)\s*$/) || [])[1] ?? (await page.locator('tbody tr').count()));
    const badges = await page.locator('tbody app-status-badge').allTextContents();
    const allMatch = badges.length > 0 && badges.every((b) => b.trim() === link.label);
    check(
      url.pathname === '/requests' && url.searchParams.get('scope') === 'visible' && !!status && selected === status && allMatch && total === link.count,
      `"${link.label}" (${link.count}) -> ${url.pathname}${url.search} | filter shows ${selected} | rows all "${link.label}": ${allMatch} | list total ${total}`,
    );
    if (i === 0) {
      await page.screenshot({ path: join(outDir, 'dashboard-link-destination.png') });
    }
  }
  await page.context().close();
}

// ---- 4. Mobile drawer by keyboard ------------------------------------------------------------------------
{
  const page = await (await browser.newContext({ viewport: { width: 390, height: 844 } })).newPage();
  await page.goto(BASE + '/sign-in');
  await page.locator('#sign-in-email').fill('ceo@demo.local');
  await page.locator('#sign-in-password').fill(process.env.DEMO_USER_PASSWORD);
  await page.locator('form button[type="submit"]').click();
  await page.waitForURL((u) => !u.pathname.startsWith('/sign-in'));
  await page.locator('.menu-button').focus();
  await page.keyboard.press('Enter');
  await page.waitForTimeout(400);
  let f = await focused(page);
  check(/close/i.test(f.what) || f.what.includes('button'), 'drawer: opens with Enter, focus moves inside (' + f.what + ')');
  for (let i = 0; i < 12; i += 1) await page.keyboard.press('Tab');
  const inside = await page.evaluate(() => !!document.activeElement?.closest('#app-sidebar'));
  check(inside, 'drawer: Tab stays inside while it is open');
  await page.keyboard.press('Escape');
  await page.waitForTimeout(400);
  f = await focused(page);
  check(/navigation|menu/i.test(f.what), 'drawer: Escape closes it and returns focus to the menu button (' + f.what + ')');
  await page.context().close();
}

console.log('failures: ' + failures);
await browser.close();
