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
  await expect(ordering.getByRole('button', { name: t('en', 'ordering.actions.createPo') })).toBeVisible({
    timeout: 15_000,
  });

  await ordering.getByRole('button', { name: t('en', 'ordering.actions.createPo') }).click();
  // Assert durable panel state (Issue), not only the ephemeral toast that can clear under suite load.
  await expect(ordering.getByRole('button', { name: t('en', 'ordering.actions.issue') })).toBeVisible({
    timeout: 15_000,
  });
  await ordering.getByRole('button', { name: t('en', 'ordering.actions.issue') }).click();
  await expect(ordering.locator('#po-confirm-note')).toBeVisible({ timeout: 15_000 });
  await ordering.locator('#po-confirm-note').fill('Supplier OK (demo)');
  await ordering.getByRole('button', { name: t('en', 'ordering.actions.confirm') }).click();
  await expect(ines.locator('app-logistics-actions')).toBeVisible({ timeout: 15_000 });

  const logistics = ines.locator('app-logistics-actions');
  await expect(logistics).toBeVisible();
  await logistics.getByRole('button', { name: t('en', 'logistics.actions.create') }).click();
  await expect(ines.getByText(t('en', 'logistics.actions.created')).first()).toBeVisible();
  await expect(logistics.getByRole('button', { name: t('en', 'logistics.actions.recordActual') }).first()).toBeVisible({
    timeout: 15_000,
  });
  for (let i = 0; i < 6; i++) {
    const actual = logistics.getByRole('button', { name: t('en', 'logistics.actions.recordActual') });
    const remaining = await actual.count();
    if (remaining === 0) {
      break;
    }
    await expect(actual.first()).toBeEnabled({ timeout: 20_000 });
    await actual.first().click();
    await expect(ines.getByText(t('en', 'logistics.actions.milestoneDone')).first()).toBeVisible();
    await expect(actual).toHaveCount(remaining - 1, { timeout: 20_000 });
  }
  await logistics.locator('input[id^="doc-ref-"]').fill('DN-E2E-1');
  await logistics.getByRole('button', { name: t('en', 'logistics.actions.addDoc') }).click();
  await expect(ines.getByText(t('en', 'logistics.actions.docAdded')).first()).toBeVisible();
  // Full-suite flake: Omar opened before user-facing status became Received (internal DELIVERED).
  {
    const deadline = Date.now() + 60_000;
    let ready = false;
    while (Date.now() < deadline) {
      await ines.goto(`/requests/${request.id}`);
      const statusText = (await ines.locator('.dossier-status').innerText()).toLowerCase();
      if (
        statusText.includes('received') ||
        statusText.includes('delivered') ||
        statusText.includes('réception') ||
        statusText.includes('livré')
      ) {
        ready = true;
        break;
      }
      await ines.waitForTimeout(1_000);
    }
    expect(ready, 'request must reach Received/DELIVERED before receipt').toBe(true);
  }
  await ines.context().close();

  const omar = await newUserPage(browser);
  await signIn(omar, USERS.receiverTn, 'en', `/requests/${request.id}`);
  const receipt = omar.locator('app-receipt-actions');
  const recordBtn = omar.getByRole('button', { name: t('en', 'receipt.actions.record') });
  {
    const deadline = Date.now() + 45_000;
    let visible = false;
    while (Date.now() < deadline) {
      await omar.locator('app-language-select button[lang="en"]').click().catch(() => undefined);
      if (await recordBtn.isVisible().catch(() => false)) {
        visible = true;
        break;
      }
      await omar.goto(`/requests/${request.id}`);
      await omar.waitForTimeout(1_000);
    }
    expect(visible, 'Record goods receipt must appear for receiver after DELIVERED').toBe(true);
  }
  await expect(recordBtn).toBeVisible({ timeout: 15_000 });
  await recordBtn.click();
  await expect(omar.getByText(t('en', 'receipt.actions.recorded')).first()).toBeVisible({ timeout: 20_000 });
  await omar.context().close();

  const sami = await newUserPage(browser);
  await signIn(sami, USERS.requesterTn, 'en', `/requests/${request.id}`);
  const samiReceipt = sami.locator('app-receipt-actions');
  await expect(samiReceipt).toBeVisible({ timeout: 20_000 });
  await samiReceipt.getByRole('button', { name: t('en', 'receipt.actions.confirm') }).click();
  await expect(sami.getByText(t('en', 'receipt.actions.confirmed')).first()).toBeVisible();
  await sami.context().close();

  const ines2 = await newUserPage(browser);
  await signIn(ines2, USERS.buyer, 'en', `/requests/${request.id}`);
  const inv = ines2.locator('app-receipt-actions');
  await expect(inv).toBeVisible({ timeout: 20_000 });
  await inv.locator('#inv-no').fill('INV-E2E-1');
  await inv.locator('input[type="checkbox"]').check();
  await inv.getByRole('button', { name: t('en', 'receipt.actions.recordInvoice') }).click();
  await expect(ines2.getByText(t('en', 'receipt.actions.invoiceSaved')).first()).toBeVisible();
  await ines2.context().close();

  // Requester (author) may close — matches production policy (isAuthor || PURCHASING_MANAGE).
  const samiClose = await newUserPage(browser);
  await signIn(samiClose, USERS.requesterTn, 'en', `/requests/${request.id}`);
  await expect(samiClose.locator('app-receipt-actions')).toBeVisible({ timeout: 20_000 });
  await samiClose.locator('app-receipt-actions').getByRole('button', { name: t('en', 'receipt.actions.close') }).click();
  await expect(samiClose.getByText(t('en', 'receipt.actions.closedToast')).first()).toBeVisible();
  await samiClose.context().close();

  // Claire (other plant) cannot open the Tunisia dossier.
  const claire = await newUserPage(browser);
  await signIn(claire, USERS.requesterFr, 'en', `/requests/${request.id}`);
  await expect(claire.getByRole('heading', { level: 1, name: t('en', 'requests.notFound.title') })).toBeVisible();
  await claire.context().close();
});
