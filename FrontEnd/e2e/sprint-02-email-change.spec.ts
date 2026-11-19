import { BrowserContext, Page, expect, test } from '@playwright/test';
import { USERS, emailChangeLink, newUserPage, signIn, t } from './support';

/**
 * Sprint 2, ADR-016 step 5, seen from the browser: a global administrator asks to correct an account's address;
 * nothing changes until the new mailbox confirms; an unusable link gets the one neutral answer.
 */

async function csrf(context: BrowserContext): Promise<string> {
  return (await context.cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? '';
}

async function createAccount(page: Page, displayName: string): Promise<{ id: number; email: string }> {
  const companies: { id: number; code: string }[] = await (await page.request.get('/api/v1/admin/organization/companies')).json();
  const email = `s2.e2e.${Date.now()}@demo.local`;
  const created = await page.request.post('/api/v1/admin/accounts', {
    data: { email, displayName, companyId: companies[0].id, preferredLanguage: 'en', roles: [] },
    headers: { 'X-XSRF-TOKEN': await csrf(page.context()) },
  });
  expect(created.status()).toBe(201);
  return { id: (await created.json()).id, email };
}

test('a global administrator requests an address correction that waits for the new mailbox', async ({ page, browser }) => {
  test.setTimeout(120_000);
  const started = Date.now();
  await signIn(page, USERS.admin, 'en');
  const displayName = `Mail Fix ${Date.now()} (Demo)`;
  const account = await createAccount(page, displayName);

  // A company administrator is not offered the correction.
  await page.goto('/admin/users');
  await page.getByRole('searchbox').fill(account.email);
  await page.getByRole('button', { name: t('en', 'common.search') }).click();
  await page.getByRole('button', { name: displayName, exact: true }).click();
  await expect(page.getByRole('button', { name: t('en', 'usersAdmin.resetPassword') })).toBeVisible();
  await expect(page.getByRole('heading', { name: t('en', 'usersAdmin.emailChange.title') })).toHaveCount(0);

  const global = await newUserPage(browser);
  await signIn(global, USERS.globalAdmin, 'en');
  await global.goto('/admin/users');
  await global.getByRole('searchbox').fill(account.email);
  await global.getByRole('button', { name: t('en', 'common.search') }).click();
  await global.getByRole('button', { name: displayName, exact: true }).click();
  const section = global.locator('section.email-change');
  await expect(section.getByRole('heading', { name: t('en', 'usersAdmin.emailChange.title') })).toBeVisible();
  const next = `s2.e2e.new.${Date.now()}@demo.local`;
  await section.getByLabel(t('en', 'usersAdmin.emailChange.newEmail')).fill(next);
  await section.getByLabel(t('en', 'usersAdmin.emailChange.reason')).fill('Typo at creation');
  await section.getByRole('button', { name: t('en', 'usersAdmin.emailChange.submit') }).click();
  await expect(section.getByRole('status')).toContainText(next);

  // Nothing changed yet: the address stays until the new mailbox confirms.
  expect((await (await global.request.get(`/api/v1/admin/accounts/${account.id}`)).json()).email).toBe(account.email);

  // The owner of the new mailbox opens the link; opening it alone confirms nothing.
  const link = await emailChangeLink(browser, next, started);
  const owner = await browser.newPage();
  await owner.goto(link);
  await expect(owner.getByRole('heading', { level: 1, name: t('en', 'emailChange.title') })).toBeVisible();
  expect((await (await global.request.get(`/api/v1/admin/accounts/${account.id}`)).json()).email).toBe(account.email);
  await owner.getByRole('button', { name: t('en', 'emailChange.submit') }).click();
  await expect(owner.locator('main').getByRole('status')).toHaveText(t('en', 'emailChange.done'));
  expect((await (await global.request.get(`/api/v1/admin/accounts/${account.id}`)).json()).email).toBe(next);

  // The link works once.
  await owner.goto('/sign-in');
  await owner.goto(link);
  await owner.getByRole('button', { name: t('en', 'emailChange.submit') }).click();
  await expect(owner.locator('main').getByRole('alert')).toHaveText(t('en', 'emailChange.invalid'));
  await owner.context().close();
  await global.context().close();
});

test('an unusable confirmation link gets the neutral answer, and opening it confirms nothing', async ({ page }) => {
  await page.goto('/e#not-a-real-token');
  await expect(page.getByRole('heading', { level: 1, name: t('en', 'emailChange.title') })).toBeVisible();
  await page.getByRole('button', { name: t('en', 'emailChange.submit') }).click();
  await expect(page.locator('main').getByRole('alert')).toHaveText(t('en', 'emailChange.invalid'));
});
