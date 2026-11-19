import { BrowserContext, Page, expect, test } from '@playwright/test';
import { USERS, mailedLink, newUserPage, signIn, t } from './support';

/**
 * Sprint 2 regression, seen from the browser: the set-up and reset e-mails carry a working link. Since Sprint 10 they
 * had carried the raw `{{link:password-reset:...}}` text; no test followed the link a person actually receives.
 * Each test reads the e-mail from the mail folder, opens its link, chooses a password and signs in with it.
 */

async function csrf(context: BrowserContext): Promise<string> {
  return (await context.cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? '';
}

async function choosePassword(page: Page, link: string, password: string): Promise<void> {
  await page.goto(link);
  await expect(page.getByRole('heading', { level: 1, name: t('en', 'passwordSetup.title') })).toBeVisible();
  await page.getByLabel(t('en', 'passwordSetup.newPassword')).fill(password);
  await page.getByLabel(t('en', 'passwordSetup.confirm')).fill(password);
  await page.getByRole('button', { name: t('en', 'passwordSetup.submit') }).click();
  await expect(page).toHaveURL(/\/sign-in/);
}

async function signInWith(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/sign-in');
  await page.locator('#sign-in-email').fill(email);
  await page.locator('#sign-in-password').fill(password);
  await page.getByRole('button', { name: t('en', 'auth.signIn.submit') }).click();
  await expect(page).not.toHaveURL(/\/sign-in/);
}

test('a new account is set up from the link in its set-up e-mail, and a reset e-mail link works too', async ({ page, browser }) => {
  test.setTimeout(150_000);
  const started = Date.now();
  await signIn(page, USERS.admin, 'en');
  const companies: { id: number }[] = await (await page.request.get('/api/v1/admin/organization/companies')).json();
  const email = `s2.links.${Date.now()}@demo.local`;
  const created = await page.request.post('/api/v1/admin/accounts', {
    data: { email, displayName: `Mail Link ${Date.now()} (Demo)`, companyId: companies[0].id, preferredLanguage: 'en', roles: [] },
    headers: { 'X-XSRF-TOKEN': await csrf(page.context()) },
  });
  expect(created.status()).toBe(201);
  const id = (await created.json()).id;

  // F-1: another administrator activates the account; activation sends the set-up e-mail.
  const global = await newUserPage(browser);
  await signIn(global, USERS.globalAdmin, 'en');
  const activated = await global.request.post(`/api/v1/admin/accounts/${id}/active`, {
    data: { active: true },
    headers: { 'X-XSRF-TOKEN': await csrf(global.context()) },
  });
  expect(activated.status()).toBe(200);

  const owner = await newUserPage(browser);
  const setUp = await mailedLink(browser, email, 'p', started);
  await choosePassword(owner, setUp, 'Set-Up-From-Mail-2026');
  await signInWith(owner, email, 'Set-Up-From-Mail-2026');
  await owner.context().close();

  // An administrator sends a reset link; the owner uses it from the e-mail.
  const resetAt = Date.now();
  const reset = await global.request.post(`/api/v1/admin/accounts/${id}/password-reset`, {
    data: {},
    headers: { 'X-XSRF-TOKEN': await csrf(global.context()) },
  });
  expect(reset.status()).toBe(202);
  const again = await newUserPage(browser);
  await choosePassword(again, await mailedLink(browser, email, 'p', resetAt), 'Reset-From-Mail-2026');
  await signInWith(again, email, 'Reset-From-Mail-2026');
  await again.context().close();
  await global.context().close();
});

test('a person who forgot the password resets it from the e-mail link', async ({ page, browser }) => {
  test.setTimeout(150_000);
  // An account of its own, so no shared demo password changes.
  await signIn(page, USERS.admin, 'en');
  const companies: { id: number }[] = await (await page.request.get('/api/v1/admin/organization/companies')).json();
  const email = `s2.forgot.${Date.now()}@demo.local`;
  const created = await page.request.post('/api/v1/admin/accounts', {
    data: { email, displayName: `Forgot ${Date.now()} (Demo)`, companyId: companies[0].id, preferredLanguage: 'en', roles: [] },
    headers: { 'X-XSRF-TOKEN': await csrf(page.context()) },
  });
  expect(created.status()).toBe(201);
  const global = await newUserPage(browser);
  await signIn(global, USERS.globalAdmin, 'en');
  expect((await global.request.post(`/api/v1/admin/accounts/${(await created.json()).id}/active`, {
    data: { active: true },
    headers: { 'X-XSRF-TOKEN': await csrf(global.context()) },
  })).status()).toBe(200);
  await global.context().close();

  const person = await newUserPage(browser);
  const askedAt = Date.now();
  await person.goto('/forgot-password');
  await person.locator('#forgot-email').fill(email);
  await person.getByRole('button', { name: t('en', 'auth.forgot.submit') }).click();
  await expect(person.getByText(t('en', 'auth.forgot.done'))).toBeVisible();
  await choosePassword(person, await mailedLink(browser, email, 'p', askedAt), 'Forgot-From-Mail-2026');
  await signInWith(person, email, 'Forgot-From-Mail-2026');
  await person.context().close();
});
