import { APIRequestContext, expect, request as pwRequest, test } from '@playwright/test';
import { USERS, demoPassword, futureDate, newUserPage, signIn, t, uniqueTitle } from './support';

/**
 * Regression coverage for the receipt rules from independent QA (QA-001, QA-002, QA-003, QA-006, QA-007): the setup uses
 * the API to reach delivery quickly, and each rule is then exercised through the browser, the API, or both.
 */
const BASE = process.env['E2E_BASE_URL'] ?? 'http://localhost:4200';

interface Result {
  status: number;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  body: any;
}

class Api {
  private constructor(private readonly context: APIRequestContext) {}

  static async as(email: string): Promise<Api> {
    const api = new Api(await pwRequest.newContext({ baseURL: BASE }));
    await api.ok('POST', '/api/v1/auth/login', { email, password: demoPassword() });
    return api;
  }

  private async csrf(): Promise<string> {
    await this.context.get('/api/v1/auth/csrf');
    return (await this.context.storageState()).cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? '';
  }

  async call(method: string, path: string, data?: unknown): Promise<Result> {
    const headers: Record<string, string> = method === 'GET' ? {} : { 'X-XSRF-TOKEN': await this.csrf() };
    const response = await this.context.fetch(path, { method, data, headers });
    const text = await response.text();
    return { status: response.status(), body: text ? JSON.parse(text) : null };
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  async ok(method: string, path: string, data?: unknown): Promise<any> {
    const result = await this.call(method, path, data);
    expect(result.status, `${method} ${path}: ${JSON.stringify(result.body)}`).toBeLessThan(300);
    return result.body;
  }

  version = async (id: number): Promise<number> => (await this.ok('GET', `/api/v1/requests/${id}`)).version;
  status = async (id: number): Promise<string> => (await this.ok('GET', `/api/v1/requests/${id}`)).status;
}

let sami: Api;
let omar: Api;
let ines: Api;
let mehdi: Api;
let karim: Api;

test.beforeAll(async () => {
  [sami, omar, ines, mehdi, karim] = await Promise.all(
    [USERS.requesterTn, USERS.receiverTn, USERS.buyer, USERS.purchasingManager, USERS.managerTn].map((user) => Api.as(user)),
  );
});

interface Order {
  id: number;
  poLines: { id: number; description: string }[];
}

async function confirmedOrder(prefix: string, lines: { description: string; quantity: number; lineKind: 'GOODS' | 'SERVICE' }[]): Promise<Order> {
  const options = await sami.ok('GET', '/api/v1/requests/form-options');
  const company = options.companies[0];
  const draft = await sami.ok('POST', '/api/v1/requests', {
    title: uniqueTitle(prefix),
    categoryId: options.categories[0].id,
    priority: 'NORMAL',
    purchaseType: 'LOCAL',
    currency: company.currency,
    departmentId: options.defaults?.departmentId ?? company.departments[0].id,
    requiredDate: futureDate(21),
    lines: lines.map((line) => ({ ...line, unit: 'PCE', estimatedUnitPrice: 5 })),
  });
  const id: number = draft.id;
  await sami.ok('POST', `/api/v1/requests/${id}/submit`, { version: draft.version });
  const approval = await karim.ok('GET', `/api/v1/requests/${id}/approval`);
  await karim.ok('POST', `/api/v1/approvals/tasks/${approval.myTask.id}/approve`, { version: approval.myTask.version });
  const request = await mehdi.ok('GET', `/api/v1/requests/${id}`);
  const buyers = await mehdi.ok('GET', `/api/v1/purchasing/buyers?companyId=${request.buyingCompany.id}`);
  await mehdi.ok('POST', `/api/v1/purchasing/requests/${id}/assign`, { version: request.version, buyerUserId: buyers[0].id });
  await ines.ok('POST', `/api/v1/purchasing/requests/${id}/accept`, { version: await ines.version(id) });
  if (lines.some((line) => line.lineKind === 'SERVICE')) {
    await ines.ok('POST', `/api/v1/purchasing/requests/${id}/line-classification`, { version: await ines.version(id), reclassifyAsGoods: [] });
  }
  const suppliers = await ines.ok('GET', '/api/v1/suppliers?q=DEMO-SKF');
  const rfq = await ines.ok('POST', `/api/v1/sourcing/requests/${id}/rfq`, { kind: 'QUICK', supplierIds: [(suppliers.items ?? suppliers)[0].id] });
  await ines.ok('POST', `/api/v1/sourcing/rfqs/${rfq.id}/quotes`, {
    rfqSupplierId: rfq.suppliers[0].id,
    currency: company.currency,
    freightAmount: 0,
    dutyAmount: 0,
    otherAmount: 0,
    lines: rfq.lines.map((line: { id: number; quantity: number }) => ({ rfqLineId: line.id, quantity: line.quantity, unitPrice: 5 })),
  });
  await ines.ok('POST', `/api/v1/sourcing/requests/${id}/validate-technical`, { requestVersion: await ines.version(id), comment: 'Fits' });
  await ines.ok('POST', `/api/v1/sourcing/requests/${id}/validate-commercial`, { requestVersion: await ines.version(id), comment: 'Price OK' });
  const quote = (await ines.ok('GET', `/api/v1/sourcing/requests/${id}/rfq`)).quotes[0].id;
  await ines.ok('POST', `/api/v1/ordering/requests/${id}/award`, { requestVersion: await ines.version(id), quotationId: quote, reason: 'Award' });
  const po = await ines.ok('POST', `/api/v1/ordering/requests/${id}/purchase-order`, { requestVersion: await ines.version(id) });
  const issued = await ines.ok('POST', `/api/v1/ordering/purchase-orders/${po.id}/issue`, { version: po.version });
  await ines.ok('POST', `/api/v1/ordering/purchase-orders/${po.id}/confirm`, { version: issued.version, confirmationNote: 'OK' });
  const dossier = await ines.ok('GET', `/api/v1/receipts/requests/${id}`);
  return { id, poLines: dossier.purchaseOrder.lines };
}

async function shipAndDeliver(order: Order, lines?: { poLineId: number; quantity: number }[]): Promise<void> {
  const body: Record<string, unknown> = { requestVersion: await ines.version(order.id), kind: 'LOCAL' };
  if (lines) {
    body['lines'] = lines.map((line) => ({ purchaseOrderLineId: line.poLineId, quantity: line.quantity }));
  }
  const shipment = await ines.ok('POST', `/api/v1/logistics/requests/${order.id}/shipments`, body);
  const today = new Date().toISOString().slice(0, 10);
  for (const milestone of shipment.milestones) {
    await ines.ok('PUT', `/api/v1/logistics/shipments/${shipment.id}/milestones/${milestone.id}`, { actualDate: today });
  }
}

const shipmentLineOf = async (order: Order, index: number): Promise<number> =>
  (await ines.ok('GET', `/api/v1/receipts/requests/${order.id}`)).purchaseOrder.lines[index].shipmentLines[0].id;

function receiptBody(version: number, poLineId: number, quantity: number): unknown {
  return {
    requestVersion: version,
    lines: [{ purchaseOrderLineId: poLineId, quantityReceived: quantity, quantityAccepted: quantity, quantityRejected: 0, quantityDamaged: 0, condition: 'OK' }],
  };
}

test('a partial receipt through the browser: Accepted follows Received, a bad manual value is explained, receipts add up', async ({ browser }) => {
  test.setTimeout(180_000);
  const order = await confirmedOrder('Partial browser', [{ description: 'Seal', quantity: 3, lineKind: 'GOODS' }]);
  await shipAndDeliver(order);
  const shipmentLine = await shipmentLineOf(order, 0);

  const page = await newUserPage(browser);
  await signIn(page, USERS.receiverTn, 'en', `/requests/${order.id}`);
  const panel = page.locator('app-receipt-actions');
  const record = panel.getByRole('button', { name: t('en', 'receipt.actions.record') });
  const received = page.locator(`#rcpt-received-${shipmentLine}`);
  const accepted = page.locator(`#rcpt-accepted-${shipmentLine}`);
  await expect(received).toHaveValue('3', { timeout: 20_000 });

  await received.fill('1');
  await expect(accepted).toHaveValue('1');
  await record.click();
  await expect(page.getByText(t('en', 'receipt.actions.recorded')).first()).toBeVisible({ timeout: 20_000 });
  await expect(panel.getByText('1 / 3')).toBeVisible({ timeout: 15_000 });
  await expect(page.locator(`#rcpt-error-${shipmentLine}`)).toHaveCount(0);

  await accepted.fill('2');
  await received.fill('1');
  await expect(page.locator(`#rcpt-error-${shipmentLine}`)).toHaveText(t('en', 'receipt.actions.acceptedExceedsReceived'));
  await expect(record).toBeDisabled();
  await accepted.fill('1');
  await expect(record).toBeEnabled();
  await record.click();
  await expect(panel.getByText('2 / 3')).toBeVisible({ timeout: 15_000 });

  await expect(received).toHaveValue('1');
  await record.click();
  await expect(received).toHaveCount(0, { timeout: 20_000 });
  await expect(page.locator('#receipt-title')).toBeFocused();
  expect(await sami.status(order.id)).toBe('PARTIALLY_RECEIVED');
  const dossier = await ines.ok('GET', `/api/v1/receipts/requests/${order.id}`);
  expect(dossier.receipts).toHaveLength(3);
  expect(Number(dossier.purchaseOrder.lines[0].received)).toBe(3);
});

test('an unshipped line on a partly shipped order is not offered and is refused by the API', async ({ browser }) => {
  test.setTimeout(180_000);
  const order = await confirmedOrder('Unshipped', [
    { description: 'Shipped part', quantity: 2, lineKind: 'GOODS' },
    { description: 'Unshipped part', quantity: 2, lineKind: 'GOODS' },
  ]);
  const [shipped, unshipped] = order.poLines;
  await shipAndDeliver(order, [{ poLineId: shipped.id, quantity: 2 }]);

  const page = await newUserPage(browser);
  await signIn(page, USERS.receiverTn, 'en', `/requests/${order.id}`);
  const panel = page.locator('app-receipt-actions');
  await expect(panel.getByRole('button', { name: t('en', 'receipt.actions.record') })).toBeVisible({ timeout: 20_000 });
  await expect(panel.locator('legend')).toHaveCount(1);
  await expect(panel.locator('legend')).toContainText('Shipped part');

  const refused = await omar.call('POST', `/api/v1/receipts/requests/${order.id}/goods-receipts`, receiptBody(await omar.version(order.id), unshipped.id, 1));
  expect(refused.status).toBe(409);
  expect(refused.body.code).toBe('line-not-shipped');
});

test('approvers see service lines, and purchasing can send a misclassified line through receipt as goods', async ({ browser }) => {
  test.setTimeout(180_000);
  const options = await sami.ok('GET', '/api/v1/requests/form-options');
  const company = options.companies[0];
  const title = uniqueTitle('Declared service');
  const draft = await sami.ok('POST', '/api/v1/requests', {
    title,
    categoryId: options.categories[0].id,
    priority: 'NORMAL',
    purchaseType: 'LOCAL',
    currency: company.currency,
    departmentId: options.defaults?.departmentId ?? company.departments[0].id,
    requiredDate: futureDate(21),
    lines: [{ description: 'Physical bearings', quantity: 2, lineKind: 'SERVICE', unit: 'PCE', estimatedUnitPrice: 5 }],
  });
  const id: number = draft.id;
  await sami.ok('POST', `/api/v1/requests/${id}/submit`, { version: draft.version });

  const approver = await newUserPage(browser);
  await signIn(approver, USERS.managerTn, 'en', '/approvals');
  // The queue shows the longest-waiting requests first, so find this one.
  await approver.locator('input[type="search"]').first().fill(title);
  await expect(approver.locator('.card', { hasText: title })).toHaveCount(1, { timeout: 20_000 });
  // Beside the queue on a wide screen the flag is in the decision pane (the row's details are folded away there).
  await expect(approver.locator('.service-flag:visible')).toHaveText(t('en', 'approvals.inbox.serviceLines', { count: 1 }), { timeout: 20_000 });
  await approver.goto(`/requests/${id}`);
  await expect(approver.locator('.service-flag')).toHaveText(t('en', 'approvals.inbox.serviceLines', { count: 1 }), { timeout: 20_000 });
  const approval = await karim.ok('GET', `/api/v1/requests/${id}/approval`);
  await karim.ok('POST', `/api/v1/approvals/tasks/${approval.myTask.id}/approve`, { version: approval.myTask.version });

  const request = await mehdi.ok('GET', `/api/v1/requests/${id}`);
  const buyers = await mehdi.ok('GET', `/api/v1/purchasing/buyers?companyId=${request.buyingCompany.id}`);
  await mehdi.ok('POST', `/api/v1/purchasing/requests/${id}/assign`, { version: request.version, buyerUserId: buyers[0].id });
  await ines.ok('POST', `/api/v1/purchasing/requests/${id}/accept`, { version: await ines.version(id) });

  const buyer = await newUserPage(browser);
  await signIn(buyer, USERS.buyer, 'en', `/requests/${id}`);
  const panel = buyer.locator('app-line-classification');
  const lineId = request.lines[0].id;
  await panel.locator(`#classify-goods-${lineId}`).check();
  await panel.getByRole('button', { name: t('en', 'purchasing.classification.confirm') }).click();
  await expect(buyer.getByText(t('en', 'purchasing.classification.done')).first()).toBeVisible({ timeout: 15_000 });
  await expect(panel.locator('section')).toHaveCount(0, { timeout: 15_000 });
  expect((await ines.ok('GET', `/api/v1/requests/${id}`)).lines[0].lineKind).toBe('GOODS');
});

test('goods cannot be accepted as a service through the API', async () => {
  test.setTimeout(180_000);
  const order = await confirmedOrder('Goods as service', [{ description: 'Goods', quantity: 1, lineKind: 'GOODS' }]);
  const refused = await sami.call('POST', `/api/v1/receipts/requests/${order.id}/service-acceptance`, { requestVersion: await sami.version(order.id), comment: 'Self accept' });
  expect(refused.status).toBe(409);
  expect(refused.body.code).toBe('no-service-lines');
  expect(await sami.status(order.id)).toBe('ORDERED');
});

test('a service order is accepted in the browser, and Close explains what is missing until it can succeed', async ({ browser }) => {
  test.setTimeout(180_000);
  const order = await confirmedOrder('Service', [{ description: 'Pump overhaul', quantity: 1, lineKind: 'SERVICE' }]);

  const page = await newUserPage(browser);
  await signIn(page, USERS.requesterTn, 'en', `/requests/${order.id}`);
  const panel = page.locator('app-receipt-actions');
  await panel.locator('#service-comment').fill('Work completed');
  await panel.getByRole('button', { name: t('en', 'receipt.actions.serviceAccept') }).click();
  await expect(page.getByText(t('en', 'receipt.actions.serviceAcceptedToast')).first()).toBeVisible({ timeout: 15_000 });
  await expect.poll(() => sami.status(order.id)).toBe('ACCEPTED');

  await ines.ok('POST', `/api/v1/receipts/requests/${order.id}/invoice`, {
    requestVersion: await ines.version(order.id), invoiceNumber: uniqueTitle('INV'), amount: 5, currency: 'TND', matchAttested: false,
  });
  await page.reload();
  const close = panel.getByRole('button', { name: t('en', 'receipt.actions.close') });
  await expect(close).toBeDisabled({ timeout: 20_000 });
  await expect(panel.locator('#closure-why')).toContainText(t('en', 'receipt.closureMissingItem.match-attested'));

  // F-5: the buyer who recorded the invoice does not attest it; the purchasing manager does.
  await mehdi.ok('POST', `/api/v1/receipts/requests/${order.id}/invoice/match-attested`, { requestVersion: await mehdi.version(order.id) });
  await page.reload();
  await expect(close).toBeEnabled({ timeout: 20_000 });
  await close.click();
  await expect(page.getByText(t('en', 'receipt.actions.closedToast')).first()).toBeVisible({ timeout: 15_000 });
  expect(await sami.status(order.id)).toBe('CLOSED');
});
