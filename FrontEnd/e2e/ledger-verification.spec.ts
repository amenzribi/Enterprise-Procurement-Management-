import AxeBuilder from '@axe-core/playwright';
import { Browser, Page, expect, test } from '@playwright/test';
import { resolve } from 'node:path';
import { Lang, USERS, createAndSubmit, newUserPage, signIn, t, uniqueTitle } from './support';

/**
 * Verification of the Ledger redesign: layouts, languages, role restrictions by direct URL, keyboard and touch use,
 * focus restoration, and the loading, empty, error and session-expiry states of the rebuilt inbox.
 */
const SHOTS = resolve(__dirname, '../../docs/design/verification');

const WIDTHS = [375, 768, 1280, 1920];
const UNTRANSLATED = /\b(approvals|requests|purchasing|receipt|overview|nav|errors|common|tracker|logistics)\.[a-zA-Z-]+(\.[a-zA-Z-]+)*\b/;
const INBOX = '**/api/v1/approvals/inbox*';

async function clearAbsences(page: Page, lang: Lang): Promise<void> {
  await page.goto('/absences');
  await expect(page.locator('#absence-delegate')).toBeVisible();
  while ((await page.getByRole('button', { name: t(lang, 'absences.remove') }).count()) > 0) {
    await page.getByRole('button', { name: t(lang, 'absences.remove') }).first().click();
    await page.getByRole('alertdialog').getByRole('button', { name: t(lang, 'absences.remove') }).click();
    await expect(page.getByText(t(lang, 'absences.removed')).last()).toBeVisible();
  }
}

async function submitted(browser: Browser, lang: Lang, count = 1): Promise<{ id: number; number: string }[]> {
  const sami = await newUserPage(browser);
  await signIn(sami, USERS.requesterTn, lang);
  const made = [];
  for (let i = 0; i < count; i++) {
    made.push(await createAndSubmit(sami, lang, uniqueTitle('Ledger check')));
  }
  await sami.context().close();
  return made;
}

async function inspect(page: Page, label: string, axe: boolean): Promise<void> {
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(300);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect.soft(overflow, `${label}: horizontal scroll`).toBeLessThanOrEqual(0);
  const text = await page.locator('main').innerText();
  expect.soft(text.match(UNTRANSLATED)?.[0] ?? null, `${label}: untranslated key`).toBeNull();
  if (axe) {
    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze();
    expect.soft(results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`), `${label}: axe`).toEqual([]);
  }
  await page.screenshot({ path: `${SHOTS}/${label}.png` });
}

for (const lang of ['en', 'fr', 'ar'] as Lang[]) {
  test(`layouts ${lang}: inbox, my work and record at every width`, async ({ page, browser }) => {
    test.setTimeout(420_000);
    await signIn(page, USERS.managerTn, lang, '/absences');
    await clearAbsences(page, lang);
    const [request] = await submitted(browser, lang);
    await expect(page.locator('html')).toHaveAttribute('dir', lang === 'ar' ? 'rtl' : 'ltr');
    for (const width of WIDTHS) {
      await page.setViewportSize({ width, height: 900 });
      const axe = width === 375 || width === 1280;
      for (const [name, path] of [['my-work', '/overview'], ['inbox', '/approvals'], ['record', `/requests/${request.id}`]]) {
        await page.goto(path);
        await inspect(page, `${lang}-${name}-${width}`, axe);
      }
    }
    if (lang === 'ar') {
      // Reference numbers are isolated so they stay left to right inside right-to-left text.
      await page.setViewportSize({ width: 1280, height: 900 });
      await page.goto('/approvals');
      await expect(page.locator('.card-number bdi').first()).toBeVisible();
      expect(await page.locator('.card-number bdi').first().evaluate((el) => getComputedStyle(el).unicodeBidi)).toBe('isolate');
    }
  });

  test(`layouts ${lang}: dense purchasing and requests tables at every width`, async ({ page, browser }) => {
    test.setTimeout(180_000);
    await signIn(page, USERS.purchasingManager, lang, '/purchasing');
    for (const width of WIDTHS) {
      await page.setViewportSize({ width, height: 900 });
      await page.goto('/purchasing');
      await inspect(page, `${lang}-purchasing-${width}`, width === 375 || width === 1280);
    }
    const sami = await newUserPage(browser);
    await signIn(sami, USERS.requesterTn, lang);
    for (const width of WIDTHS) {
      await sami.setViewportSize({ width, height: 900 });
      await sami.goto('/requests');
      await inspect(sami, `${lang}-requests-${width}`, width === 375 || width === 1280);
    }
    await sami.context().close();
  });
}

test('direct URLs respect roles', async ({ page, browser }) => {
  test.setTimeout(120_000);
  const [request] = await submitted(browser, 'en');

  await page.goto('/approvals');
  await expect(page).toHaveURL(/\/sign-in\?returnUrl=/);

  await signIn(page, USERS.requesterTn, 'en', '/requests');
  for (const path of ['/purchasing', '/logistics', '/admin/users', '/admin/requests', '/dashboards']) {
    await page.goto(path);
    await expect(page, `requester on ${path}`).toHaveURL(/\/not-found/);
  }
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

  const other = await newUserPage(browser);
  await signIn(other, USERS.requesterFr, 'en');
  await other.goto(`/requests/${request.id}`);
  await expect(other.getByRole('heading', { level: 1, name: t('en', 'requests.notFound.title') })).toBeVisible();
  expect((await other.request.get(`/api/v1/requests/${request.id}`)).status()).toBe(404);
  expect((await other.request.get(`/api/v1/receipts/requests/${request.id}`)).status()).toBe(404);
  await other.context().close();

  const buyer = await newUserPage(browser);
  await signIn(buyer, USERS.buyer, 'en');
  await buyer.goto('/admin/users');
  await expect(buyer).toHaveURL(/\/not-found/);
  expect((await buyer.request.get('/api/v1/admin/accounts')).status()).toBe(403);
  await buyer.context().close();
});

test.describe('inbox states', () => {
  test.use({ viewport: { width: 1280, height: 900 } });

  test('loading shows a skeleton, an error can be retried, an empty queue says so', async ({ page }) => {
    await signIn(page, USERS.managerTn, 'en', '/approvals');

    await page.route(INBOX, async (route) => {
      await new Promise((done) => setTimeout(done, 1200));
      await route.continue();
    });
    await page.reload({ waitUntil: 'commit' });
    await expect(page.locator('app-skeleton').first()).toBeVisible();
    await expect(page.locator('app-skeleton')).toHaveCount(0, { timeout: 15_000 });
    await page.unrouteAll({ behavior: 'wait' });

    await page.route(INBOX, (route) => route.fulfill({ status: 500, contentType: 'application/problem+json', body: '{"code":"internal-error"}' }));
    await page.reload();
    await expect(page.locator('app-error-state')).toBeVisible();
    await page.unroute(INBOX);
    await page.locator('app-error-state button').click();
    await expect(page.locator('app-error-state')).toHaveCount(0);

    await page.route(INBOX, (route) => route.fulfill({ json: { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 } }));
    await page.reload();
    await expect(page.locator('app-empty-state')).toBeVisible();
    await expect(page.locator('.pane')).toHaveCount(0);
  });

  test('a failed decision panel can be retried without leaving the queue', async ({ page, browser }) => {
    await signIn(page, USERS.managerTn, 'en', '/absences');
    await clearAbsences(page, 'en');
    await submitted(browser, 'en');
    await page.route('**/api/v1/requests/*/approval', (route) => route.fulfill({ status: 500, json: { code: 'internal-error' } }));
    await page.goto('/approvals');
    await expect(page.locator('.pane app-error-state')).toBeVisible();
    await page.unroute('**/api/v1/requests/*/approval');
    await page.locator('.pane app-error-state button').click();
    await expect(page.locator('.pane app-approval-actions')).toBeVisible();
  });

  test('an expired session returns to sign-in', async ({ page, context }) => {
    await signIn(page, USERS.managerTn, 'en', '/approvals');
    const cookies = await context.cookies();
    await context.clearCookies();
    await context.addCookies(cookies.filter((cookie) => !cookie.httpOnly));
    // Any call now gets 401; a background poll may already have redirected before this click.
    await page.getByRole('button', { name: t('en', 'approvals.inbox.views.decided') }).click({ timeout: 5_000 }).catch(() => undefined);
    await expect(page).toHaveURL(/\/sign-in\?.*reason=expired/);
    await expect(page.getByText(t('en', 'auth.signIn.expired'))).toBeVisible();
  });
});

test.describe('keyboard and focus', () => {
  test('skip link moves focus to the main content', async ({ page }) => {
    await signIn(page, USERS.requesterTn, 'en', '/requests');
    await page.reload();
    await page.waitForLoadState('networkidle');
    await page.keyboard.press('Tab');
    await expect(page.locator('.skip-link')).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.locator('main#main')).toBeFocused();
  });

  test('deciding keeps focus in the queue and reports the result', async ({ page, browser }) => {
    test.setTimeout(120_000);
    await page.setViewportSize({ width: 1280, height: 900 });
    await signIn(page, USERS.managerTn, 'en', '/absences');
    await clearAbsences(page, 'en');
    await submitted(browser, 'en', 2);
    await page.goto('/approvals');
    await expect(page.locator('.card').nth(1)).toBeVisible({ timeout: 20_000 });
    const pane = page.locator('.pane');
    await pane.getByRole('button', { name: t('en', 'approvals.actions.APPROVE'), exact: true }).click();
    await pane.getByRole('button', { name: t('en', 'approvals.confirm.APPROVE') }).click();
    await expect(page.getByRole('status').filter({ hasText: t('en', 'approvals.done.APPROVE') }).first()).toBeVisible();
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(500);
    await expect(page.locator('.pane')).toBeVisible();
    const focused = await page.evaluate(() => document.activeElement?.tagName ?? 'NONE');
    expect(focused, 'focus must not be lost to the page body').not.toBe('BODY');
  });

  test('the navigation drawer closes with Escape and restores focus', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 900 });
    await signIn(page, USERS.requesterTn, 'en', '/requests');
    const menu = page.locator('.menu-button');
    await menu.focus();
    await page.keyboard.press('Enter');
    await expect(page.locator('.sidebar .nav-link').first()).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(menu).toBeFocused();
  });

  test('the user menu closes with Escape and restores focus', async ({ page }) => {
    await signIn(page, USERS.requesterTn, 'en', '/requests');
    const trigger = page.locator('app-user-menu button').first();
    await trigger.focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('menuitem').first()).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('menuitem')).toHaveCount(0);
    await expect(trigger).toBeFocused();
  });

  test('a dialog traps focus, closes with Escape and restores focus', async ({ page }) => {
    await signIn(page, USERS.requesterTn, 'en', '/requests');
    await page.goto('/requests/new');
    await page.locator('#request-title').fill('Unsaved');
    const link = page.getByRole('link', { name: t('en', 'nav.items.overview') });
    await link.focus();
    await page.keyboard.press('Enter');
    const dialog = page.getByRole('alertdialog');
    await expect(dialog).toBeVisible();
    expect(await dialog.evaluate((el) => el.contains(document.activeElement))).toBe(true);
    await page.keyboard.press('Escape');
    await expect(dialog).toHaveCount(0);
    await expect(link).toBeFocused();
  });

  test('folded purchasing filters work by keyboard and stay open while in use', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await signIn(page, USERS.purchasingManager, 'en', '/purchasing');
    const summary = page.locator('.more summary');
    await summary.focus();
    await page.keyboard.press('Enter');
    const priorityLike = page.locator('#queue-complete');
    await expect(priorityLike).toBeVisible();
    await priorityLike.selectOption('true');
    await expect(page).toHaveURL(/complete=true/);
    await expect(priorityLike).toBeVisible();
    await priorityLike.selectOption('');
    await expect(page).not.toHaveURL(/complete=/);
    await page.waitForTimeout(300);
    await expect(priorityLike, 'clearing a filter must not fold the panel under the user').toBeVisible();
  });
});

test.describe('touch', () => {
  test('a tap on a phone opens the record', async ({ browser }) => {
    test.setTimeout(120_000);
    const context = await browser.newContext({ viewport: { width: 375, height: 800 }, hasTouch: true });
    const page = await context.newPage();
    await signIn(page, USERS.managerTn, 'en', '/absences');
    await clearAbsences(page, 'en');
    await submitted(browser, 'en');
    await page.goto('/approvals');
    await page.locator('.card a[href^="/requests/"]').last().tap();
    await expect(page).toHaveURL(/\/requests\/\d+$/);
    await context.close();
  });

  test('a tap on a wide tablet selects the row', async ({ browser }) => {
    test.setTimeout(120_000);
    const context = await browser.newContext({ viewport: { width: 1180, height: 820 }, hasTouch: true });
    const page = await context.newPage();
    await signIn(page, USERS.managerTn, 'en', '/absences');
    await clearAbsences(page, 'en');
    await submitted(browser, 'en', 2);
    await page.goto('/approvals');
    const second = page.locator('.card').nth(1);
    await expect(second).toBeVisible({ timeout: 20_000 });
    const title = (await second.locator('.card-title').innerText()).trim();
    // The row is the target except for its title, which links to the record; tap the free space beside the amount.
    const box = (await second.boundingBox())!;
    await page.touchscreen.tap(box.x + box.width * 0.72, box.y + box.height / 2);
    await expect(page.locator('.pane-title')).toHaveText(title);
    await context.close();
  });
});
