import { expect, test } from '@playwright/test';
import { USERS, createAndSubmit, decide, demoPassword, newUserPage, signIn, t, uniqueTitle } from './support';

/**
 * Sprint 11: buyer opens RFQ conversation, sends EXTERNAL message, supplier replies; INTERNAL stays hidden.
 */
test('buyer ↔ supplier messaging on RFQ (EN)', async ({ page, browser }) => {
  await signIn(page, USERS.requesterTn, 'en');
  const request = await createAndSubmit(page, 'en', uniqueTitle('Messaging'));
  const karim = await newUserPage(browser);
  await signIn(karim, USERS.managerTn, 'en');
  await decide(karim, 'en', request.id, 'APPROVE');
  await karim.context().close();

  const mehdi = await newUserPage(browser);
  await signIn(mehdi, USERS.purchasingManager, 'en', `/requests/${request.id}`);
  const panel = mehdi.locator('app-purchasing-actions');
  await panel.getByRole('button', { name: t('en', 'purchasing.actions.assign') }).click();
  await panel.getByRole('button', { name: t('en', 'purchasing.actions.assignConfirm') }).click();
  await expect(mehdi.getByText(t('en', 'purchasing.actions.assigned', { name: 'Ines Jaziri (Demo)' })).first()).toBeVisible();
  await mehdi.context().close();

  const ines = await newUserPage(browser);
  await signIn(ines, USERS.buyer, 'en', `/requests/${request.id}`);
  const buyerPanel = ines.locator('app-purchasing-actions');
  await buyerPanel.getByRole('button', { name: t('en', 'purchasing.actions.accept') }).click();
  await expect(ines.getByText(t('en', 'purchasing.actions.accepted')).first()).toBeVisible();

  const sourcing = ines.locator('app-sourcing-actions');
  await expect(sourcing).toBeVisible({ timeout: 15_000 });
  await sourcing.locator('#rfq-supplier').selectOption({ index: 1 });
  await sourcing.getByRole('button', { name: t('en', 'sourcing.actions.create') }).click();
  await expect(sourcing.getByText(t('en', 'sourcing.status.OPEN')).first()).toBeVisible({ timeout: 15_000 });

  const messaging = ines.locator('app-conversation-panel');
  await expect(messaging).toBeVisible();
  await messaging.getByRole('button', { name: t('en', 'messaging.panel.open') }).click();
  await messaging.locator('#msg-body').fill('Please confirm packing');
  await messaging.getByRole('button', { name: t('en', 'messaging.send') }).click();
  await expect(messaging.getByText('Please confirm packing')).toBeVisible();

  // Internal note
  await messaging.locator('input[value="INTERNAL"]').check();
  await messaging.locator('#msg-body').fill('Internal evaluation note');
  await messaging.getByRole('button', { name: t('en', 'messaging.send') }).click();
  await expect(messaging.getByText('Internal evaluation note')).toBeVisible();

  const supplier = await newUserPage(browser);
  await supplier.goto('/supplier/messages');
  await expect(supplier).toHaveURL(/\/sign-in/);
  await supplier.locator('#sign-in-email').fill('contact.skf@demo.local');
  await supplier.locator('#sign-in-password').fill(demoPassword());
  await supplier.getByRole('button', { name: t('en', 'auth.signIn.submit') }).click();
  await expect(supplier).toHaveURL(/\/supplier\/messages/);
  await expect(supplier.getByRole('heading', { level: 1, name: t('en', 'messaging.portal.title') })).toBeVisible();
  await supplier.locator('.item').first().click();
  await expect(supplier.getByText('Please confirm packing')).toBeVisible();
  await expect(supplier.getByText('Internal evaluation note')).toHaveCount(0);
  await supplier.locator('#portal-body').fill('Packing confirmed');
  await supplier.getByRole('button', { name: t('en', 'messaging.send') }).click();
  await expect(supplier.getByText('Packing confirmed')).toBeVisible();
  await supplier.context().close();

  await ines.reload();
  await expect(ines.locator('app-conversation-panel').getByText('Packing confirmed')).toBeVisible({ timeout: 15_000 });
  await ines.context().close();
});
