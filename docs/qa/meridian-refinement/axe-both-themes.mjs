// WCAG 2.x A/AA scan (axe) of the main screens in both themes. Usage: node axe.mjs
import { createRequire } from 'node:module';
const require = createRequire('C:/Users/zribi/OneDrive/Desktop/Procurement Managamenet/FrontEnd/package.json');
const { chromium } = require('@playwright/test');
const AxeBuilder = require('@axe-core/playwright').default;

const BASE = 'http://localhost:4201';
const PASSWORD = process.env.DEMO_USER_PASSWORD;
const browser = await chromium.launch({ channel: 'msedge' });
let total = 0;

async function scan(page, label) {
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(700);
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze();
  total += results.violations.length;
  console.log(`${results.violations.length === 0 ? 'PASS' : 'FAIL'} ${label}`);
  for (const v of results.violations) {
    console.log(`   ${v.id} (${v.nodes.length}): ${v.nodes.slice(0, 3).map((n) => n.target.join(' ') + ' :: ' + (n.any[0]?.message ?? '').slice(0, 110)).join(' | ')}`);
  }
}

for (const theme of ['light', 'dark']) {
  for (const [email, routes] of [
    [null, ['/sign-in', '/forgot-password', '/a']],
    ['manager.tn@demo.local', ['/overview', '/approvals', '/approvals?view=decided', '/absences']],
    ['requester.tn@demo.local', ['/requests', '/requests/new', '@detail']],
    ['buyer@demo.local', ['/purchasing', '/logistics', '/suppliers', '@detail']],
    ['ceo@demo.local', ['/dashboards', '/requests?scope=visible']],
    ['global.admin@demo.local', ['/admin/users', '/admin/approval-rules', '/system-status']],
  ]) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: theme, reducedMotion: 'reduce' });
    const page = await context.newPage();
    await page.goto(BASE + '/sign-in');
    if (email) {
      await page.locator('#sign-in-email').fill(email);
      await page.locator('#sign-in-password').fill(PASSWORD);
      await page.locator('form button[type="submit"]').click();
      await page.waitForURL((u) => !u.pathname.startsWith('/sign-in'));
    }
    for (const route of routes) {
      let target = route;
      if (route === '@detail') {
        await page.goto(BASE + '/requests?scope=visible');
        const link = page.locator('a[href^="/requests/"]:not([href$="/new"])').first();
        try {
          await link.waitFor({ timeout: 8000 });
        } catch {
          console.log('SKIP detail (no request) for', email);
          continue;
        }
        target = await link.getAttribute('href');
      }
      await page.goto(BASE + target);
      await scan(page, `${theme} ${email ?? 'guest'} ${route}`);
    }
    await context.close();
  }
}
console.log('violations total:', total);
await browser.close();
