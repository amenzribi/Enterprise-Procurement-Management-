import { expect, test } from '@playwright/test';
import { USERS, createAndSubmit, decide, newUserPage, signIn, t, uniqueTitle } from './support';

/**
 * Sprint 5–7 path: assign → accept → RFQ/quote → validations → award → PO draft → issue →
 * in-app supplier confirmation (buyer) → shipment → milestones → receipt → invoice → close.
 */
test('Award → PO → ship → receipt → invoice → close (EN)', async ({ page, browser }) => {
  // Full S5–S7 path opens several browser contexts; keep well above the default 60s suite timeout.
  test.setTimeout(180_000);
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
  for (let i = 0; i < 4; i++) {
    const actual = logistics.getByRole('button', { name: t('en', 'logistics.actions.recordActual') });
    if ((await actual.count()) === 0) {
      break;
    }
    await actual.first().click();
    await expect(ines.getByText(t('en', 'logistics.actions.milestoneDone')).first()).toBeVisible();
  }
  await logistics.locator('input[id^="doc-ref-"]').fill('DN-E2E-1');
  await logistics.getByRole('button', { name: t('en', 'logistics.actions.addDoc') }).click();
  await expect(ines.getByText(t('en', 'logistics.actions.docAdded')).first()).toBeVisible();
  await ines.context().close();

  const omar = await newUserPage(browser);
  await signIn(omar, USERS.receiverTn, 'en', `/requests/${request.id}`);
  const receipt = omar.locator('app-receipt-actions');
  await expect(receipt).toBeVisible({ timeout: 15_000 });
  await receipt.getByRole('button', { name: t('en', 'receipt.actions.record') }).click();
  await expect(omar.getByText(t('en', 'receipt.actions.recorded')).first()).toBeVisible();
  await omar.context().close();

  const sami = await newUserPage(browser);
  await signIn(sami, USERS.requesterTn, 'en', `/requests/${request.id}`);
  const samiReceipt = sami.locator('app-receipt-actions');
  await samiReceipt.getByRole('button', { name: t('en', 'receipt.actions.confirm') }).click();
  await expect(sami.getByText(t('en', 'receipt.actions.confirmed')).first()).toBeVisible();
  await sami.context().close();

  const ines2 = await newUserPage(browser);
  await signIn(ines2, USERS.buyer, 'en', `/requests/${request.id}`);
  const inv = ines2.locator('app-receipt-actions');
  await inv.getByRole('button', { name: t('en', 'receipt.actions.recordInvoice') }).click();
  await expect(ines2.getByText(t('en', 'receipt.actions.invoiceSaved')).first()).toBeVisible();
  await ines2.context().close();

  const mehdiClose = await newUserPage(browser);
  await signIn(mehdiClose, USERS.purchasingManager, 'en', `/requests/${request.id}`);
  await mehdiClose.locator('app-receipt-actions').getByRole('button', { name: t('en', 'receipt.actions.close') }).click();
  await expect(mehdiClose.getByText(t('en', 'receipt.actions.closedToast')).first()).toBeVisible();
  await mehdiClose.context().close();

  // Claire (other plant) cannot open the Tunisia dossier.
  const claire = await newUserPage(browser);
  await signIn(claire, USERS.requesterFr, 'en', `/requests/${request.id}`);
  await expect(claire.getByRole('heading', { level: 1, name: t('en', 'requests.notFound.title') })).toBeVisible();
  await claire.context().close();
});
