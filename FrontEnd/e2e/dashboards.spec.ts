import { expect, test } from '@playwright/test';
import { USERS, signIn, t } from './support';

test('Dashboards pipeline for Mehdi; group panel for Hedi; Sami forbidden (EN)', async ({ page, browser }) => {
  await signIn(page, USERS.purchasingManager, 'en', '/dashboards');
  await expect(page.getByRole('heading', { name: t('en', 'dashboards.title') })).toBeVisible();
  await expect(page.getByRole('heading', { name: t('en', 'dashboards.pipeline.title') })).toBeVisible();
  await expect(page.getByRole('button', { name: t('en', 'dashboards.exportRequests') })).toBeVisible();
  await expect(page.getByRole('heading', { name: t('en', 'dashboards.group.title') })).toHaveCount(0);

  const hedi = await browser.newContext().then((c) => c.newPage());
  await signIn(hedi, USERS.ceo, 'en', '/dashboards');
  await expect(hedi.getByRole('heading', { name: t('en', 'dashboards.group.title') })).toBeVisible();
  await hedi.context().close();

  const sami = await browser.newContext().then((c) => c.newPage());
  await sami.goto('/dashboards');
  await expect(sami).toHaveURL(/\/sign-in/);
  await signIn(sami, USERS.requesterTn, 'en', '/dashboards');
  await expect(sami).not.toHaveURL(/\/dashboards/);
  await sami.context().close();
});

test('Dashboards AR RTL and mobile width', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 720 });
  await signIn(page, USERS.managerTn, 'ar', '/dashboards');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await expect(page.getByRole('heading', { name: t('ar', 'dashboards.title') })).toBeVisible();
});
