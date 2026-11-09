import { expect, test, Page } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import {
  USERS,
  decide,
  fillHeader,
  fillLine,
  futureDate,
  newUserPage,
  signIn,
  t,
} from './support';

/**
 * Developer-executed 20-step request-to-closure journey (AUTO-UAT).
 * Runs against the live app on :4200 → :8080. Screenshots land under docs/qa/evidence/.
 */
const EVIDENCE = process.env['QA_EVIDENCE'] + '/journey';
const SUFFIX = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14);
const TITLE = `AUTO-UAT — Pump seals — ${SUFFIX}`;

async function shot(page: Page, name: string): Promise<void> {
  mkdirSync(EVIDENCE, { recursive: true });
  await page.screenshot({ path: resolve(EVIDENCE, `${name}.png`), fullPage: true });
}

async function waitDelivered(page: Page, requestId: number): Promise<void> {
  const deadline = Date.now() + 60_000;
  while (Date.now() < deadline) {
    await page.goto(`/requests/${requestId}`);
    const status = page.locator('.dossier-status');
    await expect(status).toBeVisible({ timeout: 15_000 });
    const text = (await status.innerText()).toLowerCase();
    // User-facing badge becomes "Received" once internal status is DELIVERED (Solution Guide §8).
    if (
      text.includes('received') ||
      text.includes('delivered') ||
      text.includes('réception') ||
      text.includes('reception') ||
      text.includes('livré') ||
      text.includes('livree')
    ) {
      return;
    }
    await page.waitForTimeout(1_000);
  }
  throw new Error(`Request ${requestId} did not reach DELIVERED/Received within 60s`);
}

async function openReceiptPanel(page: Page, requestId: number, lang: 'en' | 'fr' | 'ar' = 'en'): Promise<void> {
  const deadline = Date.now() + 45_000;
  const recordBtn = () => page.getByRole('button', { name: t(lang, 'receipt.actions.record') });
  while (Date.now() < deadline) {
    await page.goto(`/requests/${requestId}`);
    await page.locator('app-language-select button[lang="en"]').click().catch(() => undefined);
    if (await recordBtn().isVisible().catch(() => false)) {
      await expect(recordBtn()).toBeVisible();
      return;
    }
    // Panel host can be present before Angular shows the record form — wait for the action.
    const host = page.locator('app-receipt-actions');
    if ((await host.count()) > 0) {
      try {
        await expect(recordBtn()).toBeVisible({ timeout: 5_000 });
        return;
      } catch {
        /* reload and retry */
      }
    }
    await page.waitForTimeout(1_000);
  }
  throw new Error(`Receipt record action not visible for request ${requestId}`);
}

test('QA journey request to payment and closure (EN)', async ({ page, browser }) => {
  test.setTimeout(300_000);

  // —— 1–2 Sami draft + submit ——
  await signIn(page, USERS.requesterTn, 'en');
  await page.goto('/requests/new');
  // Demo accounts may keep a non-English preferred language from prior sessions.
  await page.locator('app-language-select button[lang="en"]').click();
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.locator('#request-title')).toBeVisible();
  await fillHeader(page, TITLE);
  // Spare parts category (DEMO-SPARE)
  const category = page.locator('#request-categoryId');
  const spare = category.locator('option', { hasText: /Spare|Pièces|قطع/i });
  if ((await spare.count()) > 0) {
    await category.selectOption({ label: (await spare.first().innerText()).trim() });
  } else {
    await category.selectOption({ index: 1 });
  }
  await page.locator('#request-justification').fill('Maintenance replacement');
  await page.locator('#request-deliveryLocation').fill('Route de Tunis km 8, Zone industrielle (Demo), Sfax 3000 — fictional test address');
  await fillLine(page, 0, {
    description: 'Seal for pump P-204',
    quantity: '10',
    unit: 'PCE',
    price: '20',
  });
  // Save draft first (submit button is also save+preview; use Save draft if present)
  const saveDraft = page.getByRole('button', { name: t('en', 'requests.form.saveDraft') });
  await expect(saveDraft).toBeVisible();
  await saveDraft.click();
  // Saving a new draft stays on the edit form at /requests/{id}/edit (no definitive PR number yet).
  await expect(page).toHaveURL(/\/requests\/\d+\/edit/, { timeout: 30_000 });
  const draftId = Number(/\/requests\/(\d+)\/edit/.exec(page.url())![1]);
  await expect(page.getByRole('heading', { level: 1 })).toContainText(/Edit request|Modifier/i);
  await expect(page.getByText(/TND\s*200/i).first()).toBeVisible();
  await shot(page, '01-sami-draft');
  // Open the dossier to confirm DRAFT and absence of company PR number, then return to edit to submit.
  await page.goto(`/requests/${draftId}`);
  await expect(page.locator('.dossier-status')).toContainText(t('en', 'requests.userStatus.DRAFT'), {
    timeout: 15_000,
  });
  const numText = ((await page.locator('.dossier-number').innerText().catch(() => '')) || '').trim();
  expect(numText).not.toMatch(/DEMO-TN-PLT-PR-\d{4}-\d+/);
  await page.getByRole('link', { name: t('en', 'requests.detail.edit') }).click();
  await expect(page).toHaveURL(/\/requests\/\d+\/edit/);
  await page.getByRole('button', { name: t('en', 'requests.form.submit') }).click();
  const preview = page.getByRole('alertdialog');
  await expect(preview).toContainText(t('en', 'approvals.preview.title'));
  await preview.getByRole('button', { name: t('en', 'requests.submit.confirm') }).click();
  const successTitle = page.getByRole('heading', { name: t('en', 'requests.submit.successTitle') });
  await expect(successTitle).toBeVisible({ timeout: 30_000 });
  const success = page.getByRole('alertdialog').filter({ has: successTitle });
  const number = (await success.locator('.number').innerText()).trim();
  expect(number).toMatch(/DEMO-TN-PLT-PR-\d{4}-\d+/);
  await success.getByRole('button', { name: t('en', 'requests.submit.openRequest') }).click();
  await expect(page).toHaveURL(/\/requests\/\d+$/);
  const requestId = Number(/\/requests\/(\d+)$/.exec(page.url())![1]);
  await expect(page.locator('.dossier-number')).toContainText(number);
  await expect(page.locator('.dossier-status')).toContainText(t('en', 'requests.userStatus.SUBMITTED'));
  await shot(page, '02-sami-submitted');
  await page.context().close();

  // —— 3 Karim approve ——
  const karim = await newUserPage(browser);
  await signIn(karim, USERS.managerTn, 'en', '/approvals');
  await expect(karim.getByRole('heading', { name: t('en', 'approvals.inbox.title') })).toBeVisible({
    timeout: 15_000,
  });
  // Prefer search if present; otherwise open dossier directly
  const search = karim.locator('input[type="search"], #approval-search, #inbox-search').first();
  if (await search.isVisible().catch(() => false)) {
    await search.fill(number);
  }
  await decide(karim, 'en', requestId, 'APPROVE');
  await shot(karim, '03-karim-approved');
  await karim.context().close();

  // —— 4 Mehdi assign Ines ——
  const mehdi = await newUserPage(browser);
  await signIn(mehdi, USERS.purchasingManager, 'en', '/purchasing');
  await mehdi.locator('#queue-search').fill(number);
  await expect(mehdi.locator('.card', { hasText: number })).toBeVisible({ timeout: 20_000 });
  await mehdi.goto(`/requests/${requestId}`);
  const assignPanel = mehdi.locator('app-purchasing-actions');
  await expect(assignPanel).toBeVisible({ timeout: 15_000 });
  await shot(mehdi, '04-mehdi-before-assign');
  await assignPanel.getByRole('button', { name: t('en', 'purchasing.actions.assign') }).click();
  await assignPanel.getByRole('button', { name: t('en', 'purchasing.actions.assignConfirm') }).click();
  await expect(
    mehdi.getByText(t('en', 'purchasing.actions.assigned', { name: 'Ines Jaziri (Demo)' })).first(),
  ).toBeVisible();
  await shot(mehdi, '04-mehdi-assigned');
  await mehdi.context().close();

  // —— 5–14 Ines sourcing → award → PO → ship → deliver ——
  const ines = await newUserPage(browser);
  await signIn(ines, USERS.buyer, 'en', `/requests/${requestId}`);
  const purchasing = ines.locator('app-purchasing-actions');
  await purchasing.getByRole('button', { name: t('en', 'purchasing.actions.accept') }).click();
  await expect(ines.getByText(t('en', 'purchasing.actions.accepted')).first()).toBeVisible();
  await shot(ines, '05-ines-accepted');

  const sourcing = ines.locator('app-sourcing-actions');
  await expect(sourcing).toBeVisible({ timeout: 15_000 });
  await sourcing.locator('#rfq-kind').selectOption('QUICK');
  const supplier = sourcing.locator('#rfq-supplier');
  const skf = supplier.locator('option', { hasText: /DEMO-SKF|SKF/i });
  expect(await skf.count(), 'DEMO-SKF must be eligible').toBeGreaterThan(0);
  await supplier.selectOption({ value: await skf.first().getAttribute('value') });
  await sourcing.getByRole('button', { name: t('en', 'sourcing.actions.create') }).click();
  await expect(sourcing.getByRole('button', { name: t('en', 'sourcing.actions.submitQuote') })).toBeVisible({
    timeout: 15_000,
  });
  await shot(ines, '06-ines-rfq');

  await sourcing.locator('#quote-price').fill('25');
  await sourcing.getByRole('button', { name: t('en', 'sourcing.actions.submitQuote') }).click();
  await expect(sourcing.getByText(/250/)).toBeVisible({ timeout: 15_000 });
  await shot(ines, '07-ines-quote-250');

  await sourcing.locator('#tech-comment').fill('Seal geometry matches pump P-204 spare specification (demo).');
  await sourcing.getByRole('button', { name: t('en', 'sourcing.actions.validateTechnical') }).click();
  await expect(sourcing.locator('#comm-comment')).toBeVisible({ timeout: 15_000 });
  await sourcing.locator('#comm-comment').fill('Unit price 25 TND accepted versus estimate 20 TND (demo).');
  await sourcing.getByRole('button', { name: t('en', 'sourcing.actions.validateCommercial') }).click();
  await shot(ines, '08-ines-validated');

  const ordering = ines.locator('app-ordering-actions');
  await expect(ordering.getByRole('button', { name: t('en', 'ordering.actions.award') })).toBeVisible({
    timeout: 15_000,
  });
  await ordering.locator('#award-quote').selectOption({ index: 1 });
  await ordering.locator('#award-reason').fill('DEMO-SKF quotation selected: seals available, 25 TND/unit.');
  await ordering.getByRole('button', { name: t('en', 'ordering.actions.award') }).click();
  await expect(ordering.getByRole('button', { name: t('en', 'ordering.actions.createPo') })).toBeVisible({
    timeout: 15_000,
  });
  await shot(ines, '09-ines-award');

  await ordering.locator('#po-terms').fill('Net 30');
  await ordering.locator('#po-incoterm').fill('DAP');
  await ordering.locator('#po-delivery').fill('Route de Tunis km 8, Zone industrielle (Demo), Sfax — fictional');
  await ordering.getByRole('button', { name: t('en', 'ordering.actions.createPo') }).click();
  await expect(ordering.getByRole('button', { name: t('en', 'ordering.actions.issue') })).toBeVisible({
    timeout: 15_000,
  });
  await expect(ordering).toContainText(/250/);
  await shot(ines, '10-ines-po-draft');

  await ordering.getByRole('button', { name: t('en', 'ordering.actions.issue') }).click();
  await expect(ordering.locator('#po-confirm-note')).toBeVisible({ timeout: 15_000 });
  await shot(ines, '11-ines-po-issued');

  await ordering.locator('#po-confirm-note').fill('Supplier confirmation recorded in-app by buyer (demo — not portal).');
  await ordering.getByRole('button', { name: t('en', 'ordering.actions.confirm') }).click();
  await expect(ines.locator('app-logistics-actions')).toBeVisible({ timeout: 15_000 });
  await shot(ines, '12-ines-po-confirmed');

  const logistics = ines.locator('app-logistics-actions');
  await logistics.getByRole('button', { name: t('en', 'logistics.actions.create') }).click();
  await expect(ines.getByText(t('en', 'logistics.actions.created')).first()).toBeVisible();
  await expect(logistics.getByRole('button', { name: t('en', 'logistics.actions.recordActual') }).first()).toBeVisible({
    timeout: 15_000,
  });
  await shot(ines, '13-ines-shipment');
  for (let i = 0; i < 6; i++) {
    const actual = logistics.getByRole('button', { name: t('en', 'logistics.actions.recordActual') });
    const remaining = await actual.count();
    if (remaining === 0) {
      break;
    }
    // Wait for busy() to clear — do not treat a temporarily disabled button as "done".
    await expect(actual.first()).toBeEnabled({ timeout: 20_000 });
    await actual.first().click();
    await expect(ines.getByText(t('en', 'logistics.actions.milestoneDone')).first()).toBeVisible();
    await expect(actual).toHaveCount(remaining - 1, { timeout: 20_000 });
  }
  const docInput = logistics.locator('input[id^="doc-ref-"]');
  if (await docInput.isVisible().catch(() => false)) {
    await docInput.fill(`DN-AUTO-UAT-${SUFFIX}`);
    await logistics.getByRole('button', { name: t('en', 'logistics.actions.addDoc') }).click();
    await expect(ines.getByText(t('en', 'logistics.actions.docAdded')).first()).toBeVisible();
  }
  await waitDelivered(ines, requestId);
  await shot(ines, '14-ines-delivered');
  await ines.context().close();

  // —— 15 Omar receipt ——
  const omar = await newUserPage(browser);
  await signIn(omar, USERS.receiverTn, 'en');
  await openReceiptPanel(omar, requestId);
  const receipt = omar.locator('app-receipt-actions');
  await receipt.getByRole('button', { name: t('en', 'receipt.actions.record') }).click();
  await expect(omar.getByText(t('en', 'receipt.actions.recorded')).first()).toBeVisible({ timeout: 20_000 });
  await expect(receipt).toContainText(/10/);
  await shot(omar, '15-omar-receipt');
  await omar.context().close();

  // —— 16 Sami confirm acceptance ——
  const sami = await newUserPage(browser);
  await signIn(sami, USERS.requesterTn, 'en', `/requests/${requestId}`);
  const samiReceipt = sami.locator('app-receipt-actions');
  await expect(samiReceipt).toBeVisible({ timeout: 20_000 });
  await samiReceipt.getByRole('button', { name: t('en', 'receipt.actions.confirm') }).click();
  await expect(sami.getByText(t('en', 'receipt.actions.confirmed')).first()).toBeVisible();
  await shot(sami, '16-sami-acceptance');
  await sami.context().close();

  // —— 17 Ines invoice 250 TND PENDING ——
  const ines2 = await newUserPage(browser);
  await signIn(ines2, USERS.buyer, 'en', `/requests/${requestId}`);
  const inv = ines2.locator('app-receipt-actions');
  await expect(inv).toBeVisible({ timeout: 20_000 });
  await inv.locator('#inv-no').fill(`INV-AUTO-UAT-${SUFFIX}`);
  await inv.locator('#inv-amt').fill('250');
  await inv.locator('#inv-doc').fill(`PDF-AUTO-UAT-${SUFFIX}`);
  const attest = inv.locator('input[type="checkbox"]');
  if (!(await attest.isChecked())) {
    await attest.check();
  }
  await inv.getByRole('button', { name: t('en', 'receipt.actions.recordInvoice') }).click();
  await expect(ines2.getByText(t('en', 'receipt.actions.invoiceSaved')).first()).toBeVisible();
  await expect(inv).toContainText(/250/);
  await expect(inv).toContainText(/PENDING|pending|En attente/i);
  await shot(ines2, '17-18-ines-invoice-pending');
  const paidBtn = inv.getByRole('button', { name: t('en', 'receipt.actions.markPaid') });
  await expect(paidBtn).toBeEnabled({ timeout: 15_000 });
  await paidBtn.click();
  await expect(ines2.getByText(t('en', 'receipt.actions.paidToast')).first()).toBeVisible();
  await expect(paidBtn).toHaveCount(0, { timeout: 15_000 });
  await shot(ines2, '18b-ines-paid');
  await ines2.context().close();

  // —— 19 Sami close with score 5 ——
  const samiClose = await newUserPage(browser);
  await signIn(samiClose, USERS.requesterTn, 'en', `/requests/${requestId}`);
  const closePanel = samiClose.locator('app-receipt-actions');
  await expect(closePanel).toBeVisible({ timeout: 20_000 });
  await closePanel.locator('#score').fill('5');
  await closePanel.getByRole('button', { name: t('en', 'receipt.actions.close') }).click();
  await expect(samiClose.getByText(t('en', 'receipt.actions.closedToast')).first()).toBeVisible();
  await expect(samiClose.locator('.dossier-status')).toContainText(t('en', 'requests.userStatus.CLOSED'));
  await shot(samiClose, '19-sami-closed');

  // —— 20 reload by reference ——
  await samiClose.goto('/requests');
  await samiClose.locator('input[type="search"], #request-search, #list-search').first().fill(number).catch(() => {});
  await samiClose.goto(`/requests/${requestId}`);
  await expect(samiClose.locator('.dossier-status')).toContainText(t('en', 'requests.userStatus.CLOSED'));
  await expect(samiClose.locator('.dossier-number')).toContainText(number);
  await expect(samiClose.locator('app-receipt-actions')).toContainText(/250/);
  await shot(samiClose, '20-sami-reopen-closed');
  await samiClose.context().close();

  // Persist references for the QA report
  mkdirSync(EVIDENCE, { recursive: true });
  const { writeFileSync } = await import('node:fs');
  writeFileSync(
    resolve(EVIDENCE, 'auto-uat-refs.json'),
    JSON.stringify({ title: TITLE, number, requestId, suffix: SUFFIX, testedAt: new Date().toISOString() }, null, 2),
    'utf8',
  );
});

