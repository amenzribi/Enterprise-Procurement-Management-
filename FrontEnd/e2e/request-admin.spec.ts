import { expect, test } from '@playwright/test';
import { newUserPage, signIn, t, USERS } from './support';

/**
 * Developer browser check: GLOBAL_ADMIN sees request administration; group ADMIN does not.
 * Preview only — does not execute cleanup of existing records.
 */
test('GLOBAL_ADMIN opens request administration; ADMIN is refused', async ({ browser }) => {
  const global = await newUserPage(browser);
  await signIn(global, USERS.globalAdmin, 'en', '/admin/requests');
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
  await expect(admin).not.toHaveURL(/\/admin\/requests$/);
  await admin.context().close();
});

test('Absences page states task-creation-only limitation', async ({ browser }) => {
  const karim = await newUserPage(browser);
  await signIn(karim, USERS.managerTn, 'en', '/absences');
  await expect(karim.getByRole('heading', { name: t('en', 'absences.title') })).toBeVisible({ timeout: 15_000 });
  await expect(karim.getByText(t('en', 'absences.policyNote'))).toBeVisible();
  await karim.context().close();
});
