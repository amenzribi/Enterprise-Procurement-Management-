import { expect, test } from '@playwright/test';
import { USERS, demoPassword, fillHeader, signIn, t, uniqueTitle } from './support';

test('an expired session returns to sign-in, then back to the same page with unsaved work restorable', async ({ page, context }) => {
  await signIn(page, USERS.requesterTn, 'en');
  await page.goto('/requests/new');
  const title = uniqueTitle('Expired session draft');
  await fillHeader(page, title);
  // Let the form copy be written to session storage (debounced 400 ms).
  await page.waitForTimeout(600);

  // Simulate the server session ending (idle timeout): drop the session cookie, keep everything else.
  const cookies = await context.cookies();
  await context.clearCookies();
  // The session cookie (EPI_SESSION) is the HttpOnly one; the readable XSRF-TOKEN cookie stays.
  await context.addCookies(cookies.filter((cookie) => !cookie.httpOnly));

  await page.getByRole('button', { name: t('en', 'requests.form.saveDraft') }).click();

  await expect(page).toHaveURL(/\/sign-in\?.*reason=expired/);
  await expect(page.getByText(t('en', 'auth.signIn.expired'))).toBeVisible();

  await page.locator('#sign-in-email').fill(USERS.requesterTn);
  await page.locator('#sign-in-password').fill(demoPassword());
  await page.getByRole('button', { name: t('en', 'auth.signIn.submit') }).click();

  await expect(page).toHaveURL(/\/requests\/new$/);
  await expect(page.getByText(t('en', 'requests.form.restore.message'))).toBeVisible();
  await page.getByRole('button', { name: t('en', 'requests.form.restore.restore') }).click();
  await expect(page.locator('#request-title')).toHaveValue(title);
});

test('sign-out ends the session on the server', async ({ page }) => {
  await signIn(page, USERS.requesterTn, 'en');
  await page.locator('app-user-menu button').click();
  await page.getByRole('menuitem', { name: t('en', 'auth.signOut') }).click();

  await expect(page).toHaveURL(/\/sign-in\?reason=signedOut/);
  const me = await page.request.get('/api/v1/me');
  expect(me.status()).toBe(401);
  await page.goto('/requests');
  await expect(page).toHaveURL(/\/sign-in/);
});

test('leaving a form with unsaved changes asks first', async ({ page }) => {
  await signIn(page, USERS.requesterTn, 'en');
  await page.goto('/requests/new');
  await page.locator('#request-title').fill('Unsaved');

  await page.getByRole('link', { name: t('en', 'nav.items.overview') }).click();
  const dialog = page.getByRole('alertdialog');
  await expect(dialog).toContainText(t('en', 'requests.form.leave.title'));
  await dialog.getByRole('button', { name: t('en', 'requests.form.leave.cancel') }).click();
  await expect(page).toHaveURL(/\/requests\/new$/);
  await expect(page.locator('#request-title')).toHaveValue('Unsaved');
});

test('a wrong password gives the generic message and no session', async ({ page }) => {
  await page.goto('/sign-in');
  await page.locator('#sign-in-email').fill(USERS.requesterTn);
  await page.locator('#sign-in-password').fill('definitely-not-the-password');
  await page.getByRole('button', { name: t('en', 'auth.signIn.submit') }).click();

  await expect(page.getByRole('alert')).toContainText(t('en', 'auth.signIn.errors.invalid-credentials'));
  await expect(page).toHaveURL(/\/sign-in/);
});
