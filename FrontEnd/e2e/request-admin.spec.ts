import { expect, test } from '@playwright/test';
import { newUserPage, signIn, t, USERS } from './support';

/**
 * Requires backend with V17+ (REQUEST_DRAFT_ADMIN) and frontend on :4200.
 * GLOBAL_ADMIN and company ADMIN share draft administration; cleanup stays GLOBAL_ADMIN-capable.
 */
test('GLOBAL_ADMIN and company ADMIN open request administration', async ({ browser }) => {
  const global = await newUserPage(browser);
  await signIn(global, USERS.globalAdmin, 'en', '/admin/requests');
  await expect(global).toHaveURL(/\/admin\/requests/);
  await expect(global.getByRole('heading', { name: t('en', 'requestAdmin.title') })).toBeVisible({
    timeout: 15_000,
  });
  await expect(global.getByText(t('en', 'requestAdmin.boundary'))).toBeVisible();
  await global.getByRole('button', { name: t('en', 'requestAdmin.cleanupPreview') }).click();
  await expect(global.getByRole('heading', { name: t('en', 'requestAdmin.cleanup.title') })).toBeVisible({
    timeout: 15_000,
  });
  await expect(global.getByText(/Nothing runs until you confirm/i)).toBeVisible();
  await global.getByRole('button', { name: t('en', 'requestAdmin.cleanup.dismiss') }).click();
  await global.context().close();

  const admin = await newUserPage(browser);
  await signIn(admin, USERS.admin, 'en', '/admin/requests');
  await expect(admin).toHaveURL(/\/admin\/requests/);
  await expect(admin.getByRole('heading', { name: t('en', 'requestAdmin.title') })).toBeVisible({
    timeout: 15_000,
  });
  await admin.context().close();
});

test('GLOBAL_ADMIN opens draft detail with Correct draft when eligible', async ({ browser }) => {
  const global = await newUserPage(browser);
  await signIn(global, USERS.globalAdmin, 'en', '/admin/requests');
  await expect(global.getByRole('heading', { name: t('en', 'requestAdmin.title') })).toBeVisible({
    timeout: 15_000,
  });
  await global.locator('input[formControlName="status"]').fill('DRAFT');
  await global.getByRole('button', { name: t('en', 'requestAdmin.apply') }).click();
  const firstLink = global.locator('table tbody tr a').first();
  await expect(firstLink).toBeVisible({ timeout: 20_000 });
  await firstLink.click();
  await expect(global.getByRole('heading', { name: t('en', 'requestAdmin.detailTitle') })).toBeVisible({
    timeout: 15_000,
  });
  await expect(global.getByText(t('en', 'requestAdmin.actionMatrix'))).toBeVisible();
  const correct = global.getByRole('button', { name: t('en', 'requestAdmin.act.CORRECT_DRAFT') });
  if (await correct.isVisible()) {
    await expect(global.getByText(t('en', 'requestAdmin.form.draftHeading'))).toBeVisible();
    await global.locator('input[formControlName="title"]').fill('Admin e2e corrected draft');
    await global.getByText(t('en', 'requestAdmin.reason')).locator('xpath=..').locator('textarea').fill(
      'Playwright draft correction',
    );
    await correct.click();
    await expect(global.getByText(t('en', 'requestAdmin.actionDone'))).toBeVisible({ timeout: 15_000 });
  }
  await global.context().close();
});

test('Absences page states task-creation-only limitation', async ({ browser }) => {
  const karim = await newUserPage(browser);
  await signIn(karim, USERS.managerTn, 'en', '/absences');
  await expect(karim.getByRole('heading', { name: t('en', 'absences.title') })).toBeVisible({ timeout: 15_000 });
  await expect(karim.getByText(t('en', 'absences.policyNote'))).toBeVisible();
  await karim.context().close();
});
