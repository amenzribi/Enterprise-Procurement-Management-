import { expect, test } from '@playwright/test';
import { USERS, createAndSubmit, decide, newUserPage, signIn, t, uniqueTitle } from './support';

/**
 * S3-AC-10: Sami submits → Karim approves → Ines sees the request in the CPA queue → Mehdi assigns Ines → Ines
 * accepts. Claire cannot open the purchasing inbox. Julien cannot open the Tunisia request.
 */
test('Sami → Karim → Ines queue → Mehdi assigns → Ines accepts (EN)', async ({ page, browser }) => {
  await signIn(page, USERS.requesterTn, 'en');
  const request = await createAndSubmit(page, 'en', uniqueTitle('Purchasing queue'));
  await expect(page.locator('.dossier-status')).toContainText(t('en', 'requests.userStatus.SUBMITTED'));

  const karim = await newUserPage(browser);
  await signIn(karim, USERS.managerTn, 'en');
  await decide(karim, 'en', request.id, 'APPROVE');
  await karim.context().close();

  await page.reload();
  await expect(page.locator('.dossier-status')).toContainText(t('en', 'requests.userStatus.APPROVED'));

  const ines = await newUserPage(browser);
  await signIn(ines, USERS.buyer, 'en', '/purchasing');
  await expect(ines.getByRole('heading', { level: 1, name: t('en', 'purchasing.inbox.title') })).toBeVisible();
  // Search by number: a busy demo queue paginates and the new card may not be on page 1.
  await ines.locator('#queue-search').fill(request.number!);
  await expect(ines.locator('.card', { hasText: request.number })).toBeVisible({ timeout: 15_000 });

  const mehdi = await newUserPage(browser);
  await signIn(mehdi, USERS.purchasingManager, 'en', `/requests/${request.id}`);
  const panel = mehdi.locator('app-purchasing-actions');
  await expect(panel).toBeVisible();
  await panel.getByRole('button', { name: t('en', 'purchasing.actions.assign') }).click();
  await expect(panel.locator('#purchase-buyer')).toBeVisible();
  await panel.getByRole('button', { name: t('en', 'purchasing.actions.assignConfirm') }).click();
  await expect(mehdi.getByText(t('en', 'purchasing.actions.assigned', { name: 'Ines Jaziri (Demo)' })).first()).toBeVisible();
  await mehdi.context().close();

  await ines.goto(`/requests/${request.id}`);
  const buyerPanel = ines.locator('app-purchasing-actions');
  await expect(buyerPanel.getByRole('button', { name: t('en', 'purchasing.actions.accept') })).toBeVisible();
  await buyerPanel.getByRole('button', { name: t('en', 'purchasing.actions.accept') }).click();
  await expect(ines.getByText(t('en', 'purchasing.actions.accepted')).first()).toBeVisible();
  await ines.context().close();

  const claire = await newUserPage(browser);
  await signIn(claire, USERS.requesterFr, 'en', '/purchasing');
  await expect(claire.getByRole('heading', { name: t('en', 'notFound.title') })).toBeVisible();
  await claire.context().close();

  const julien = await newUserPage(browser);
  await signIn(julien, USERS.managerFr, 'en', `/requests/${request.id}`);
  await expect(julien.getByRole('heading', { level: 1, name: t('en', 'requests.notFound.title') })).toBeVisible();
  await julien.context().close();
});
