import { expect, test } from '@playwright/test';
import { USERS, createAndSubmit, decide, newUserPage, signIn, t, uniqueTitle } from './support';

/**
 * Sprint 5–6 path: assign → accept → RFQ/quote → validations → award → PO draft → issue →
 * in-app supplier confirmation (buyer) → shipment → milestones → delivery note.
 */
test('Award → PO → confirm → shipment milestones (EN)', async ({ page, browser }) => {
  await signIn(page, USERS.requesterTn, 'en');
  const request = await createAndSubmit(page, 'en', uniqueTitle('Order and ship'));
  await expect(page.locator('.dossier-status')).toContainText(t('en', 'requests.userStatus.SUBMITTED'));

  const karim = await newUserPage(browser);
  await signIn(karim, USERS.managerTn, 'en');
  await decide(karim, 'en', request.id, 'APPROVE');
  await karim.context().close();

  const mehdi = await newUserPage(browser);
  await signIn(mehdi, USERS.purchasingManager, 'en', `/requests/${request.id}`);
  const assignPanel = mehdi.locator('app-purchasing-actions');
  await assignPanel.getByRole('button', { name: t('en', 'purchasing.actions.assign') }).click();
  await assignPanel.getByRole('button', { name: t('en', 'purchasing.actions.assignConfirm') }).click();
  await expect(mehdi.getByText(t('en', 'purchasing.actions.assigned', { name: 'Ines Jaziri (Demo)' })).first()).toBeVisible();
  await mehdi.context().close();

  const ines = await newUserPage(browser);
  await signIn(ines, USERS.buyer, 'en', `/requests/${request.id}`);
  const purchasing = ines.locator('app-purchasing-actions');
  await purchasing.getByRole('button', { name: t('en', 'purchasing.actions.accept') }).click();
  await expect(ines.getByText(t('en', 'purchasing.actions.accepted')).first()).toBeVisible();

  const sourcing = ines.locator('app-sourcing-actions');
  await expect(sourcing).toBeVisible();
  await sourcing.locator('#rfq-supplier').selectOption({ index: 1 });
  await sourcing.getByRole('button', { name: t('en', 'sourcing.actions.create') }).click();
  await expect(sourcing.getByRole('button', { name: t('en', 'sourcing.actions.submitQuote') })).toBeVisible({
    timeout: 15_000,
  });
  await sourcing.locator('#quote-price').fill('25');
  await sourcing.getByRole('button', { name: t('en', 'sourcing.actions.submitQuote') }).click();
  await sourcing.locator('#tech-comment').fill('Fits the plant');
  await sourcing.getByRole('button', { name: t('en', 'sourcing.actions.validateTechnical') }).click();
  await expect(sourcing.locator('#comm-comment')).toBeVisible({ timeout: 15_000 });
  await sourcing.locator('#comm-comment').fill('Price OK');
  await sourcing.getByRole('button', { name: t('en', 'sourcing.actions.validateCommercial') }).click();

  const ordering = ines.locator('app-ordering-actions');
  await expect(ordering.getByRole('button', { name: t('en', 'ordering.actions.award') })).toBeVisible({
    timeout: 15_000,
  });
  await ordering.locator('#award-quote').selectOption({ index: 1 });
  await ordering.locator('#award-reason').fill('Best landed cost for seals');
  await ordering.getByRole('button', { name: t('en', 'ordering.actions.award') }).click();
  await expect(ines.getByText(t('en', 'ordering.actions.awarded')).first()).toBeVisible();

  await ordering.getByRole('button', { name: t('en', 'ordering.actions.createPo') }).click();
  await expect(ines.getByText(t('en', 'ordering.actions.poCreated')).first()).toBeVisible();
  await ordering.getByRole('button', { name: t('en', 'ordering.actions.issue') }).click();
  await expect(ines.getByText(t('en', 'ordering.actions.issued')).first()).toBeVisible();
  await ordering.locator('#po-confirm-note').fill('Supplier OK (demo)');
  await ordering.getByRole('button', { name: t('en', 'ordering.actions.confirm') }).click();
  await expect(ines.getByText(t('en', 'ordering.actions.confirmed')).first()).toBeVisible();

  const logistics = ines.locator('app-logistics-actions');
  await expect(logistics).toBeVisible();
  await logistics.getByRole('button', { name: t('en', 'logistics.actions.create') }).click();
  await expect(ines.getByText(t('en', 'logistics.actions.created')).first()).toBeVisible();
  await logistics.getByRole('button', { name: t('en', 'logistics.actions.recordActual') }).first().click();
  await expect(ines.getByText(t('en', 'logistics.actions.milestoneDone')).first()).toBeVisible();
  await logistics.locator('input[id^="doc-ref-"]').fill('DN-E2E-1');
  await logistics.getByRole('button', { name: t('en', 'logistics.actions.addDoc') }).click();
  await expect(ines.getByText(t('en', 'logistics.actions.docAdded')).first()).toBeVisible();

  // Claire (other plant) cannot open the Tunisia dossier.
  const claire = await newUserPage(browser);
  await signIn(claire, USERS.requesterFr, 'en', `/requests/${request.id}`);
  await expect(claire.getByRole('heading', { level: 1, name: t('en', 'requests.notFound.title') })).toBeVisible();
  await claire.context().close();

  await ines.context().close();
});
