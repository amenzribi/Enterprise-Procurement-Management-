import { APIRequestContext, Page, expect, request as pwRequest, test } from '@playwright/test';
import { execSync } from 'node:child_process';
import { appendFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { USERS, demoPassword, futureDate, newUserPage, signIn, t } from './support';

/** Independent QA scenarios. Each check is recorded (expected/actual) rather than stopping at the first mismatch. */
const OUT = process.env['QA_EVIDENCE'] as string;
const BASE = process.env['E2E_BASE_URL'] ?? 'http://localhost:4200';
const DB = 'procurement_qa_36c69d8';
const stamp = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14);
mkdirSync(OUT, { recursive: true });

function sql(q: string): string {
  return execSync(`sqlcmd -S localhost -E -C -d ${DB} -h -1 -W -Q "SET NOCOUNT ON; ${q}"`).toString().trim();
}
function check(id: string, what: string, expected: unknown, actual: unknown, pass?: boolean): void {
  const e = JSON.stringify(expected);
  const a = JSON.stringify(actual);
  appendFileSync(join(OUT, 'checks.jsonl'), JSON.stringify({ id, what, expected: e, actual: a, pass: pass ?? e === a }) + '\n');
}

interface Res { status: number; body: any }
class Api {
  private constructor(private ctx: APIRequestContext) {}
  static async anon(): Promise<Api> {
    return new Api(await pwRequest.newContext({ baseURL: BASE }));
  }
  static async as(email: string): Promise<Api> {
    const api = await Api.anon();
    const r = await api.call('POST', '/api/v1/auth/login', { email, password: demoPassword() });
    if (r.status >= 300) throw new Error(`login ${email}: ${r.status}`);
    return api;
  }
  private async token(): Promise<string> {
    await this.ctx.get('/api/v1/auth/csrf');
    return (await this.ctx.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? '';
  }
  async call(method: string, path: string, data?: unknown): Promise<Res> {
    const r = await this.ctx.fetch(path, { method, data, headers: method === 'GET' ? {} : { 'X-XSRF-TOKEN': await this.token() } });
    const text = await r.text();
    let body: any = text;
    try { body = JSON.parse(text); } catch { /* not JSON */ }
    return { status: r.status(), body };
  }
  async ok(method: string, path: string, data?: unknown): Promise<any> {
    const r = await this.call(method, path, data);
    if (r.status >= 300) throw new Error(`${method} ${path}: ${r.status} ${JSON.stringify(r.body)}`);
    return r.body;
  }
  version = async (id: number): Promise<number> => (await this.ok('GET', `/api/v1/requests/${id}`)).version;
  status = async (id: number): Promise<string> => (await this.ok('GET', `/api/v1/requests/${id}`)).status;
}

let sami: Api, omar: Api, ines: Api, mehdi: Api;
let ids: { category: number; dept: number; skf: number; buyer: number };

test.beforeAll(async () => {
  [sami, omar, ines, mehdi] = await Promise.all([USERS.requesterTn, USERS.receiverTn, USERS.buyer, USERS.purchasingManager].map((u) => Api.as(u)));
  ids = {
    category: Number(sql(`SELECT c.id FROM request_category c WHERE c.code='DEMO-SPARE'`)),
    dept: Number(sql(`SELECT d.id FROM department d JOIN company c ON c.id=d.company_id WHERE c.code='DEMO-TN-PLT' AND d.code='DEMO-MAINT'`)),
    skf: Number(sql(`SELECT id FROM supplier WHERE code='DEMO-SKF'`)),
    buyer: Number(sql(`SELECT id FROM user_account WHERE email='buyer@demo.local'`)),
  };
});

async function draftBody(title: string, lines: { d: string; q: number }[], dept = ids.dept) {
  return {
    title, categoryId: ids.category, priority: 'NORMAL', purchaseType: 'LOCAL', currency: 'TND', departmentId: dept, requiredDate: futureDate(21),
    lines: lines.map((l) => ({ description: l.d, quantity: l.q, unit: 'PCE', estimatedUnitPrice: 10 })),
  };
}

async function approveAll(id: number): Promise<void> {
  for (let round = 0; round < 6 && (await sami.status(id)) === 'IN_APPROVAL'; round++) {
    let acted = false;
    for (const email of [USERS.managerTn, USERS.manager2Tn, USERS.budgetTn, USERS.ceo]) {
      const a = await Api.as(email);
      const r = await a.call('GET', `/api/v1/requests/${id}/approval`);
      if (r.status === 200 && r.body.myTask) {
        await a.ok('POST', `/api/v1/approvals/tasks/${r.body.myTask.id}/approve`, { version: r.body.myTask.version });
        acted = true;
        break;
      }
    }
    if (!acted) throw new Error(`No approver found for ${id}`);
  }
}

interface Po { id: number; poId: number; lines: { id: number; quantity: number; description: string }[] }

async function confirmedPo(title: string, lines: { d: string; q: number }[]): Promise<Po> {
  const draft = await sami.ok('POST', '/api/v1/requests', await draftBody(`${title} ${stamp}`, lines));
  const id: number = draft.id;
  await sami.ok('POST', `/api/v1/requests/${id}/submit`, { version: draft.version });
  await approveAll(id);
  await mehdi.ok('POST', `/api/v1/purchasing/requests/${id}/assign`, { version: await mehdi.version(id), buyerUserId: ids.buyer });
  await ines.ok('POST', `/api/v1/purchasing/requests/${id}/accept`, { version: await ines.version(id) });
  const rfq = await ines.ok('POST', `/api/v1/sourcing/requests/${id}/rfq`, { kind: 'QUICK', supplierIds: [ids.skf] });
  await ines.ok('POST', `/api/v1/sourcing/rfqs/${rfq.id}/quotes`, {
    rfqSupplierId: rfq.suppliers[0].id, currency: 'TND', freightAmount: 0, dutyAmount: 0, otherAmount: 0,
    lines: rfq.lines.map((l: any) => ({ rfqLineId: l.id, quantity: l.quantity, unitPrice: 10 })),
  });
  await ines.ok('POST', `/api/v1/sourcing/requests/${id}/validate-technical`, { requestVersion: await ines.version(id), comment: 'QA fits' });
  await ines.ok('POST', `/api/v1/sourcing/requests/${id}/validate-commercial`, { requestVersion: await ines.version(id), comment: 'QA price' });
  const quote = (await ines.ok('GET', `/api/v1/sourcing/requests/${id}/rfq`)).quotes[0].id;
  await ines.ok('POST', `/api/v1/ordering/requests/${id}/award`, { requestVersion: await ines.version(id), quotationId: quote, reason: 'QA award' });
  const po = await ines.ok('POST', `/api/v1/ordering/requests/${id}/purchase-order`, { requestVersion: await ines.version(id) });
  const issued = await ines.ok('POST', `/api/v1/ordering/purchase-orders/${po.id}/issue`, { version: po.version });
  await ines.ok('POST', `/api/v1/ordering/purchase-orders/${po.id}/confirm`, { version: issued.version, confirmationNote: 'QA confirm' });
  const dossier = await ines.ok('GET', `/api/v1/receipts/requests/${id}`);
  return { id, poId: po.id, lines: dossier.purchaseOrder.lines.map((l: any) => ({ id: l.id, quantity: Number(l.quantity), description: l.description })) };
}

async function ship(po: Po, lines?: { lineId: number; q: number }[]): Promise<any> {
  const body: any = { requestVersion: await ines.version(po.id), kind: 'LOCAL' };
  if (lines) body.lines = lines.map((l) => ({ purchaseOrderLineId: l.lineId, quantity: l.q }));
  return ines.ok('POST', `/api/v1/logistics/requests/${po.id}/shipments`, body);
}
async function deliver(shipment: any): Promise<void> {
  const today = new Date().toISOString().slice(0, 10);
  for (const m of shipment.milestones) {
    await ines.ok('PUT', `/api/v1/logistics/shipments/${shipment.id}/milestones/${m.id}`, { actualDate: today });
  }
}
function line(id: number, received: number, accepted = received, rejected = 0, condition = 'OK') {
  return { purchaseOrderLineId: id, quantityReceived: received, quantityAccepted: accepted, quantityRejected: rejected, quantityDamaged: 0, condition };
}
async function receive(actor: Api, id: number, lines: unknown[]): Promise<Res> {
  return actor.call('POST', `/api/v1/receipts/requests/${id}/goods-receipts`, { requestVersion: await sami.version(id), lines });
}
const dossierOf = (id: number) => ines.ok('GET', `/api/v1/receipts/requests/${id}`);
const receivable = async (id: number) => (await dossierOf(id)).purchaseOrder.lines.map((l: any) => Number(l.receivable));
const confirm = (a: Api, r: any) => a.call('POST', `/api/v1/receipts/goods-receipts/${r.id}/confirm`, { version: r.version });
const shot = (p: Page, name: string) => p.screenshot({ path: join(OUT, `${name}.png`), fullPage: true });

test('R1 split shipments and cumulative quantities (API)', async () => {
  test.setTimeout(180_000);
  const po = await confirmedPo('QA-R1 split', [{ d: 'R1 line A', q: 10 }, { d: 'R1 line B', q: 4 }]);
  const [a, b] = po.lines;
  const s1 = await ship(po, [{ lineId: a.id, q: 6 }]);
  await deliver(s1);
  check('R1.1', 'First split shipment delivered sets DELIVERED', 'DELIVERED', await sami.status(po.id));
  check('R1.2', 'Receivable after shipment of A=6 only (B not shipped)', [6, 0], await receivable(po.id));
  const blocked = await receive(omar, po.id, [line(b.id, 1)]);
  check('R1.3', 'Receiving unshipped line B while another shipment exists is refused', 409, blocked.status);
  if (blocked.status === 200) await confirm(sami, blocked.body);
  const r1 = await receive(omar, po.id, [line(a.id, 6)]);
  check('R1.4', 'Receipt 1 A=6', 200, r1.status);
  check('R1.5', 'Status after partial receipt', 'PARTIALLY_RECEIVED', await sami.status(po.id));
  const over = await receive(omar, po.id, [line(a.id, 1)]);
  check('R1.6', 'Over-receipt beyond shipped quantity', 409, over.status);
  const s2 = await ship(po, [{ lineId: a.id, q: 4 }, ...(blocked.status === 200 ? [{ lineId: b.id, q: 3 }] : [{ lineId: b.id, q: 4 }])]);
  await deliver(s2);
  check('R1.7', 'Status unchanged by second delivered shipment', 'PARTIALLY_RECEIVED', await sami.status(po.id));
  const rec = await receivable(po.id);
  check('R1.8', 'Cumulative receivable after second shipment', [4, 4], blocked.status === 200 ? [rec[0], rec[1] + 1] : rec);
  const r2 = await receive(omar, po.id, [line(a.id, 4), line(b.id, blocked.status === 200 ? 3 : 4)]);
  check('R1.9', 'Receipt 2 completes both lines', 200, r2.status);
  check('R1.10', 'Nothing left to receive', [0, 0], await receivable(po.id));
  check('R1.11', 'Recorder cannot confirm own receipt', 403, (await confirm(omar, r1.body)).status);
  check('R1.12', 'Requester confirms receipt 1', 200, (await confirm(sami, r1.body)).status);
  check('R1.13', 'After 1 of 2 confirmations', 'PARTIALLY_RECEIVED', await sami.status(po.id));
  check('R1.14', 'Requester confirms receipt 2', 200, (await confirm(sami, r2.body)).status);
  check('R1.15', 'Fully accepted after all confirmed', 'ACCEPTED', await sami.status(po.id));
});

test('R2 API zero line, status order, service acceptance bypass', async () => {
  test.setTimeout(240_000);
  const po = await confirmedPo('QA-R2 zero', [{ d: 'R2 line A', q: 5 }, { d: 'R2 line B', q: 3 }]);
  await deliver(await ship(po));
  const [a, b] = po.lines;
  const zero = await receive(omar, po.id, [line(a.id, 5), line(b.id, 0)]);
  check('R2.1', 'API: explicit zero line in a receipt (doc: UI omits it, API rejects)', 422, zero.status);
  check('R2.2', 'Rejected receipt left nothing behind', 0, (await dossierOf(po.id)).receipts.length);
  check('R2.3', 'Problem detail, no stack trace', false, JSON.stringify(zero.body).includes('Exception') || JSON.stringify(zero.body).includes('.java'));

  // Out-of-order confirmation: a rejected unit first, then a clean receipt.
  const bad = await receive(omar, po.id, [line(a.id, 2, 1, 1, 'NON_CONFORMING')]);
  const good = await receive(omar, po.id, [line(a.id, 3), line(b.id, 3)]);
  await confirm(sami, bad.body);
  check('R2.4', 'Confirmed receipt with rejected unit', 'NON_CONFORMING', await sami.status(po.id));
  await confirm(sami, good.body);
  check('R2.5', 'A later clean confirmation must not hide the earlier rejected unit (spec S7-AC-04)', 'NON_CONFORMING', await sami.status(po.id));

  const svcPo = await confirmedPo('QA-R2 service', [{ d: 'R2 svc', q: 2 }]);
  const svc = await sami.call('POST', `/api/v1/receipts/requests/${svcPo.id}/service-acceptance`, { requestVersion: await sami.version(svcPo.id), comment: 'QA self-accept', performanceOk: true });
  check('R2.6', 'One person can accept undelivered goods via service acceptance, skipping D-03 second person (should be refused)', 'refused (4xx)', svc.status === 200 ? `accepted 200, status ${await sami.status(svcPo.id)}` : `refused (${svc.status})`, svc.status >= 400 && svc.status < 500);
});

test('R3 authorization matrix (API)', async () => {
  test.setTimeout(240_000);
  const po = await confirmedPo('QA-R3 authz', [{ d: 'R3 line', q: 2 }]);
  const anon = await Api.anon();
  const [clare, julien, budget, ceo, admin, global, karim, amel] = await Promise.all(
    [USERS.requesterFr, USERS.managerFr, USERS.budgetTn, USERS.ceo, USERS.admin, USERS.globalAdmin, USERS.managerTn, USERS.assistantTn].map((u) => Api.as(u)));
  const shipUrl = `/api/v1/logistics/requests/${po.id}/shipments`;
  const v = await ines.version(po.id);
  check('Z.1', 'Anonymous reading a record', 401, (await anon.call('GET', `/api/v1/requests/${po.id}`)).status);
  check('Z.2', 'Other-company requester reads record', 404, (await clare.call('GET', `/api/v1/requests/${po.id}`)).status);
  check('Z.3', 'Other-company manager reads receipts', 404, (await julien.call('GET', `/api/v1/receipts/requests/${po.id}`)).status);
  check('Z.4', 'Other-company user creates shipment', 404, (await clare.call('POST', shipUrl, { requestVersion: v, kind: 'LOCAL' })).status);
  check('Z.5', 'Requester creates shipment', 403, (await sami.call('POST', shipUrl, { requestVersion: v, kind: 'LOCAL' })).status);
  const z6 = (await omar.call('POST', shipUrl, { requestVersion: v, kind: 'LOCAL' })).status;
  check('Z.6', 'Receiver refused shipment creation (403 or 404)', 'refused', z6, z6 === 403 || z6 === 404);
  await deliver(await ship(po));
  const [l] = po.lines;
  check('Z.7', 'Buyer cannot record receipt', 403, (await receive(ines, po.id, [line(l.id, 2)])).status);
  check('Z.8', 'Other-company user cannot record receipt', 404, (await clare.call('POST', `/api/v1/receipts/requests/${po.id}/goods-receipts`, { requestVersion: v, lines: [line(l.id, 2)] })).status);
  check('Z.9', 'Admin (company-scoped) cannot record receipt', 403, (await receive(admin, po.id, [line(l.id, 2)])).status);
  check('Z.10', 'CEO cannot record receipt', 403, (await receive(ceo, po.id, [line(l.id, 2)])).status);
  check('Z.11', 'Same-company manager cannot record receipt', 403, (await receive(karim, po.id, [line(l.id, 2)])).status);
  const r = await receive(omar, po.id, [line(l.id, 2)]);
  check('Z.12', 'Buyer cannot confirm receipt', 403, (await confirm(ines, r.body)).status);
  check('Z.13', 'Other-company user cannot confirm receipt', 404, (await confirm(clare, r.body)).status);
  check('Z.14', 'Global admin cannot confirm receipt', 403, (await confirm(global, r.body)).status);
  await confirm(sami, r.body);
  const iv = { requestVersion: await ines.version(po.id), invoiceNumber: `QA-${stamp}`, amount: 20, currency: 'TND', matchAttested: false };
  check('Z.15', 'Requester cannot record invoice', 403, (await sami.call('POST', `/api/v1/receipts/requests/${po.id}/invoice`, iv)).status);
  check('Z.16', 'Receiver cannot record invoice', 403, (await omar.call('POST', `/api/v1/receipts/requests/${po.id}/invoice`, iv)).status);
  check('Z.17', 'Budget approver cannot record invoice', 403, (await budget.call('POST', `/api/v1/receipts/requests/${po.id}/invoice`, iv)).status);
  check('Z.18', 'Buyer records invoice', 200, (await ines.call('POST', `/api/v1/receipts/requests/${po.id}/invoice`, iv)).status);
  const body = async () => ({ requestVersion: await ines.version(po.id) });
  check('Z.19', 'Requester cannot attest', 403, (await sami.call('POST', `/api/v1/receipts/requests/${po.id}/invoice/match-attested`, await body())).status);
  check('Z.20', 'Requester cannot mark paid', 403, (await sami.call('POST', `/api/v1/receipts/requests/${po.id}/invoice/paid`, await body())).status);
  check('Z.21', 'Other-company user cannot mark paid', 404, (await clare.call('POST', `/api/v1/receipts/requests/${po.id}/invoice/paid`, await body())).status);
  check('Z.22', 'Close refused while unattested', 409, (await sami.call('POST', `/api/v1/receipts/requests/${po.id}/close`, await body())).status);
  check('Z.23', 'Receiver cannot close', 403, (await omar.call('POST', `/api/v1/receipts/requests/${po.id}/close`, await body())).status);
  check('Z.24', 'Buyer (not manager) cannot close', 403, (await ines.call('POST', `/api/v1/receipts/requests/${po.id}/close`, await body())).status);
  check('Z.25', 'Buyer attests', 200, (await ines.call('POST', `/api/v1/receipts/requests/${po.id}/invoice/match-attested`, await body())).status);
  check('Z.26', 'Second attestation is a conflict', 409, (await ines.call('POST', `/api/v1/receipts/requests/${po.id}/invoice/match-attested`, await body())).status);
  check('Z.27', 'Purchasing manager closes', 200, (await mehdi.call('POST', `/api/v1/receipts/requests/${po.id}/close`, { ...(await body()), rating: 4 })).status);
  check('Z.28', 'Payment recorded after closure (D-02) keeps CLOSED', [200, 'CLOSED'], [(await ines.call('POST', `/api/v1/receipts/requests/${po.id}/invoice/paid`, await body())).status, await sami.status(po.id)]);

  // Approval and purchasing, with a same-company peer and a department boundary.
  const d = await sami.ok('POST', '/api/v1/requests', await draftBody(`QA-R3 approval ${stamp}`, [{ d: 'x', q: 1 }]));
  await sami.ok('POST', `/api/v1/requests/${d.id}/submit`, { version: d.version });
  const task = await karim.ok('GET', `/api/v1/requests/${d.id}/approval`);
  const taskUrl = `/api/v1/approvals/tasks/${task.myTask.id}/approve`;
  check('Z.29', 'Requester cannot approve own request', 403, (await sami.call('POST', taskUrl, { version: task.myTask.version })).status);
  check('Z.30', 'Other-company manager cannot approve (not found)', 404, (await julien.call('POST', taskUrl, { version: task.myTask.version })).status);
  check('Z.31', 'Same-company peer requester cannot read a colleague\'s request unless configured', 404, (await amel.call('GET', `/api/v1/requests/${d.id}`)).status);
  const other = sql(`SELECT TOP 1 d.id FROM department d JOIN company c ON c.id=d.company_id WHERE c.code='DEMO-TN-PLT' AND d.code<>'DEMO-MAINT'`);
  check('Z.32a', 'A second department exists in demo TN company', true, other !== '', other !== '');
  if (other) {
    const od = await sami.call('POST', '/api/v1/requests', await draftBody(`QA-R3 dept ${stamp}`, [{ d: 'y', q: 1 }], Number(other)));
    if (od.status < 300) {
      await sami.ok('POST', `/api/v1/requests/${od.body.id}/submit`, { version: od.body.version }).catch(() => undefined);
      check('Z.32', 'Department-scoped manager cannot see request of another department', 404, (await karim.call('GET', `/api/v1/requests/${od.body.id}`)).status);
    } else {
      check('Z.32', 'Could not create request in other department', 'created', od.status, false);
    }
  }
  const mehdiQueue = await julien.call('GET', '/api/v1/purchasing/queue');
  check('Z.33', 'Non-purchasing user refused purchasing queue', 403, mehdiQueue.status);
  check('Z.34', 'Requester refused admin users API', 403, (await sami.call('GET', '/api/v1/admin/users')).status);
});

test('R1c clean split delivery and cumulative confirmation (API)', async () => {
  test.setTimeout(180_000);
  const po = await confirmedPo('QA-R1c split', [{ d: 'R1c line A', q: 10 }, { d: 'R1c line B', q: 4 }]);
  const [a, b] = po.lines;
  await deliver(await ship(po, [{ lineId: a.id, q: 6 }]));
  const r1 = await receive(omar, po.id, [line(a.id, 6)]);
  check('R1c.1', 'Receipt 1 for first split shipment', 200, r1.status);
  check('R1c.2', 'Over-receipt beyond shipped quantity refused', 409, (await receive(omar, po.id, [line(a.id, 1)])).status);
  await deliver(await ship(po, [{ lineId: a.id, q: 4 }, { lineId: b.id, q: 4 }]));
  check('R1c.3', 'Status stays partial after second shipment delivered', 'PARTIALLY_RECEIVED', await sami.status(po.id));
  check('R1c.4', 'Receivable is cumulative across shipments', [4, 4], await receivable(po.id));
  const r2 = await receive(omar, po.id, [line(a.id, 2)]);
  const r3 = await receive(omar, po.id, [line(a.id, 2), line(b.id, 4)]);
  check('R1c.5', 'Receipts 2 and 3 recorded', [200, 200], [r2.status, r3.status]);
  check('R1c.6', 'Nothing left to receive', [0, 0], await receivable(po.id));
  check('R1c.7', 'Over-receipt after completion refused', 409, (await receive(omar, po.id, [line(b.id, 1)])).status);
  check('R1c.8', 'Recorder cannot confirm', 403, (await confirm(omar, r1.body)).status);
  await confirm(sami, r1.body);
  await confirm(sami, r3.body);
  check('R1c.9', 'Not accepted while a receipt is unconfirmed', 'PARTIALLY_RECEIVED', await sami.status(po.id));
  await confirm(sami, r2.body);
  check('R1c.10', 'Accepted when all receipts confirmed, in any order', 'ACCEPTED', await sami.status(po.id));
  const d = await dossierOf(po.id);
  check('R1c.11', 'Cumulative received per line', [10, 4], d.purchaseOrder.lines.map((l: any) => Number(l.received)));
});

test('R1b receipt on an unshipped line then shipment of the remainder (API)', async () => {
  test.setTimeout(180_000);
  const po = await confirmedPo('QA-R1b unshipped', [{ d: 'R1b line A', q: 2 }, { d: 'R1b line B', q: 4 }]);
  const [a, b] = po.lines;
  await deliver(await ship(po, [{ lineId: a.id, q: 2 }]));
  const early = await receive(omar, po.id, [line(b.id, 1)]);
  check('R1b.1', 'Observation: unshipped line can be received (S7-AC-02 PO-quantity fallback)', 200, early.status);
  await deliver(await ship(po, [{ lineId: b.id, q: 3 }]));
  check('R1b.2', 'Remaining 3 units of B stay receivable after they are shipped', 3, (await receivable(po.id))[1]);
});

test('R2b service acceptance reaches closure with no shipment or receipt (API)', async () => {
  test.setTimeout(180_000);
  const po = await confirmedPo('QA-R2b bypass', [{ d: 'R2b goods', q: 2 }]);
  const svc = await sami.call('POST', `/api/v1/receipts/requests/${po.id}/service-acceptance`, { requestVersion: await sami.version(po.id), comment: 'QA self-accept', performanceOk: true });
  check('R2b.1', 'Requester alone accepts goods never shipped', 'refused', svc.status, svc.status >= 400);
  const invoice = await ines.call('POST', `/api/v1/receipts/requests/${po.id}/invoice`, { requestVersion: await ines.version(po.id), invoiceNumber: `QA-B-${stamp}`, amount: 20, currency: 'TND', matchAttested: true });
  const closed = await sami.call('POST', `/api/v1/receipts/requests/${po.id}/close`, { requestVersion: await sami.version(po.id) });
  const d = await dossierOf(po.id);
  check('R2b.2', 'Without shipment or receipt the request must not reach closure', 'not CLOSED', { invoice: invoice.status, close: closed.status, status: await sami.status(po.id), receipts: d.receipts.length }, (await sami.status(po.id)) !== 'CLOSED');
});

test('U1 receipt form: zero line, partial edit, cumulative (UI, EN)', async ({ browser }) => {
  test.setTimeout(240_000);
  const po = await confirmedPo('QA-U1 receipt', [{ d: 'U1 line A', q: 5 }, { d: 'U1 line B', q: 3 }]);
  await deliver(await ship(po));
  const [a, b] = po.lines;
  const page = await newUserPage(browser);
  await signIn(page, USERS.receiverTn, 'en', `/requests/${po.id}`);
  const panel = page.locator('app-receipt-actions');
  const record = panel.getByRole('button', { name: t('en', 'receipt.actions.record') });
  await expect(record).toBeVisible({ timeout: 20_000 });
  await shot(page, 'U1-01-form-prefilled');
  await page.locator(`#rcpt-received-${b.id}`).fill('0');
  await record.click();
  await expect(page.getByText(t('en', 'receipt.actions.recorded')).first()).toBeVisible({ timeout: 20_000 });
  await expect(page.locator(`#rcpt-received-${a.id}`)).toHaveCount(0, { timeout: 15_000 });
  await shot(page, 'U1-02-after-zero-line');
  let d = await dossierOf(po.id);
  check('U1.1', 'Zero-line receipt recorded only line A', [1, 5], [d.receipts[0].lines.length, Number(d.receipts[0].lines[0].quantityReceived)]);
  check('U1.2', 'Line B stays open in full', [0, 3], d.purchaseOrder.lines.map((l: any) => Number(l.receivable)));

  // Partial entry the way a receiver does it: change only the received count.
  const received = page.locator(`#rcpt-received-${b.id}`);
  await expect(received).toHaveValue('3');
  await received.fill('1');
  await record.click();
  await page.waitForTimeout(1500);
  const errorText = await panel.locator('.error').allInnerTexts();
  await shot(page, 'U1-03-partial-only-received-edited');
  d = await dossierOf(po.id);
  check('U1.3', 'Editing only "received" to 1 of 3 records a partial receipt (or the form keeps accepted in step)', 2, d.receipts.length);
  check('U1.4', 'Message shown after that attempt', 'none', errorText.length ? errorText : 'none');

  if (d.receipts.length === 1) {
    await page.locator(`#rcpt-accepted-${b.id}`).fill('1');
    await record.click();
    await expect(page.getByText(t('en', 'receipt.actions.recorded')).first()).toBeVisible({ timeout: 20_000 });
    await page.waitForTimeout(1500);
    const stale = await panel.locator('.error').allInnerTexts();
    check('U1.5', 'Earlier error cleared after a successful retry', 'none', stale.length ? stale : 'none');
  }
  await expect(page.locator(`#rcpt-received-${b.id}`)).toHaveValue('2', { timeout: 15_000 });
  await expect(panel.getByText('1 / 3')).toBeVisible();
  await shot(page, 'U1-04-cumulative-1-of-3');
  await record.click();
  await expect(page.locator(`#rcpt-received-${b.id}`)).toHaveCount(0, { timeout: 20_000 });
  d = await dossierOf(po.id);
  check('U1.6', 'Three receipts, nothing left', [3, [0, 0]], [d.receipts.length, d.purchaseOrder.lines.map((l: any) => Number(l.receivable))]);
  check('U1.7', 'Recorder gets no confirm button', 0, await panel.getByRole('button', { name: t('en', 'receipt.actions.confirm') }).count());

  const req = await newUserPage(browser);
  await signIn(req, USERS.requesterTn, 'en', `/requests/${po.id}`);
  const samiPanel = req.locator('app-receipt-actions');
  for (let i = 0; i < 3; i++) {
    const btn = samiPanel.getByRole('button', { name: t('en', 'receipt.actions.confirm') }).first();
    await expect(btn).toBeEnabled({ timeout: 15_000 });
    const before = await samiPanel.getByRole('button', { name: t('en', 'receipt.actions.confirm') }).count();
    await btn.click();
    await expect(samiPanel.getByRole('button', { name: t('en', 'receipt.actions.confirm') })).toHaveCount(before - 1, { timeout: 15_000 });
  }
  check('U1.8', 'All confirmed via UI', 'ACCEPTED', await sami.status(po.id));
  await shot(req, 'U1-05-accepted');
});

test('U2 invoice attestation recovery (UI, EN)', async ({ browser }) => {
  test.setTimeout(240_000);
  const po = await confirmedPo('QA-U2 invoice', [{ d: 'U2 line', q: 2 }]);
  await deliver(await ship(po));
  const r = await receive(omar, po.id, [line(po.lines[0].id, 2)]);
  await confirm(sami, r.body);
  const page = await newUserPage(browser);
  await signIn(page, USERS.buyer, 'en', `/requests/${po.id}`);
  const panel = page.locator('app-receipt-actions');
  await expect(panel.locator('#inv-no')).toBeVisible({ timeout: 20_000 });
  await panel.locator('#inv-no').fill(`INV-U2-${stamp}`);
  await panel.locator('#inv-amt').fill('');
  const record = panel.getByRole('button', { name: t('en', 'receipt.actions.recordInvoice') });
  await record.click();
  await page.waitForTimeout(1500);
  const firstErr = await panel.locator('.error').allInnerTexts();
  check('U2.1', 'Blank amount shows a validation message and records nothing', true, firstErr.length > 0, firstErr.length > 0);
  await panel.locator('#inv-amt').fill('20');
  check('U2.2', 'Attestation unchecked by default', false, await panel.locator('input[type="checkbox"]').isChecked());
  await record.click();
  await expect(page.getByText(t('en', 'receipt.actions.invoiceSaved')).first()).toBeVisible({ timeout: 15_000 });
  await page.waitForTimeout(1500);
  const staleErr = await panel.locator('.error').allInnerTexts();
  check('U2.3', 'Error is cleared after successful save', 'none', staleErr.length ? staleErr : 'none');
  await shot(page, 'U2-01-saved-unattested');

  const mgr = await newUserPage(browser);
  await signIn(mgr, USERS.purchasingManager, 'en', `/requests/${po.id}`);
  const mPanel = mgr.locator('app-receipt-actions');
  const close = mPanel.getByRole('button', { name: t('en', 'receipt.actions.close') });
  await expect(close).toBeVisible({ timeout: 20_000 });
  const missing = t('en', 'receipt.closureMissingItem.match-attested');
  check('U2.4', 'Closure names the missing attestation', true, await mPanel.getByText(missing).count() > 0, await mPanel.getByText(missing).count() > 0);
  check('U2.5', 'Close is not offered while closure is not ready (DESIGN: never imply a path that does not work)', false, await close.isEnabled());
  await shot(mgr, 'U2-02-close-before-attest');
  if (await close.isEnabled()) {
    await close.click();
    await mgr.waitForTimeout(1500);
    check('U2.6', 'Result of pressing Close too early is a translated message', true, (await mPanel.locator('.error').allInnerTexts()), (await mPanel.locator('.error').count()) > 0 && !(await mPanel.locator('.error').innerText()).includes('errors.'));
    await shot(mgr, 'U2-03-close-too-early');
  }

  const attest = panel.getByRole('button', { name: t('en', 'receipt.actions.attestNow') });
  await expect(attest).toBeEnabled({ timeout: 15_000 });
  await attest.click();
  await expect(page.getByText(t('en', 'receipt.actions.attestedToast')).first()).toBeVisible({ timeout: 15_000 });
  await expect(attest).toHaveCount(0, { timeout: 15_000 });
  const paid = panel.getByRole('button', { name: t('en', 'receipt.actions.markPaid') });
  await expect(paid).toBeEnabled({ timeout: 15_000 });
  await paid.click();
  await expect(page.getByText(t('en', 'receipt.actions.paidToast')).first()).toBeVisible({ timeout: 15_000 });
  check('U2.7', 'Attested and paid', 'PAID', await sami.status(po.id));
  const dossier = await dossierOf(po.id);
  check('U2.8', 'Invoice flags persisted', [true, 'PAID'], [dossier.invoice.matchAttested, dossier.invoice.paymentStatus]);

  await mgr.reload();
  await expect(close).toBeEnabled({ timeout: 20_000 });
  await mPanel.locator('.error').count();
  await close.click();
  await expect(mgr.getByText(t('en', 'receipt.actions.closedToast')).first()).toBeVisible({ timeout: 15_000 });
  check('U2.9', 'Closes after recovery', 'CLOSED', await sami.status(po.id));
  await shot(mgr, 'U2-04-closed');
});

test('U3 receipt form keyboard, mobile, Arabic RTL and French', async ({ browser }) => {
  test.setTimeout(240_000);
  const po = await confirmedPo('QA-U3 rtl', [{ d: 'U3 line', q: 2 }]);
  await deliver(await ship(po));
  const ctx = await browser.newContext({ viewport: { width: 375, height: 800 }, hasTouch: true });
  const page = await ctx.newPage();
  await signIn(page, USERS.receiverTn, 'ar', `/requests/${po.id}`);
  const panel = page.locator('app-receipt-actions');
  const lineId = po.lines[0].id;
  const received = page.locator(`#rcpt-received-${lineId}`);
  await expect(received).toBeVisible({ timeout: 20_000 });
  check('U3.1', 'Document is right to left', 'rtl', await page.locator('html').getAttribute('dir'));
  check('U3.2', 'No sideways scroll at 375 px', true, await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1));
  const untranslated = await panel.evaluate((el) => /\b(receipt|errors)\.[a-z]/i.test((el as HTMLElement).innerText));
  check('U3.3', 'No raw translation keys in Arabic panel', false, untranslated);
  await shot(page, 'U3-01-ar-375-form');

  await received.focus();
  const order: string[] = [];
  for (let i = 0; i < 6; i++) {
    await page.keyboard.press('Tab');
    order.push(await page.evaluate(() => { const e = document.activeElement as HTMLElement; return e.id || e.tagName + ':' + (e.innerText || '').slice(0, 20); }));
  }
  const expected = [`rcpt-accepted-${lineId}`, `rcpt-rejected-${lineId}`, `rcpt-condition-${lineId}`, 'rcpt-note', 'rcpt-photo'];
  check('U3.4', 'Tab order through receipt form', expected, order.slice(0, 5));
  const focusVisible = await page.evaluate(() => getComputedStyle(document.activeElement as Element).outlineStyle !== 'none');
  check('U3.5', 'Focused record button shows a focus ring', true, focusVisible, focusVisible);
  await page.keyboard.press('Enter');
  await expect(received).toHaveCount(0, { timeout: 20_000 });
  check('U3.6', 'Enter on focused record button submits the receipt', 'PARTIALLY_RECEIVED', await sami.status(po.id));
  const activeAfter = await page.evaluate(() => document.activeElement?.tagName);
  check('U3.7', 'Focus is not lost to <body> after the form disappears', true, activeAfter, activeAfter !== 'BODY');
  await shot(page, 'U3-02-ar-375-recorded');
  await ctx.close();

  const fr = await newUserPage(browser);
  await signIn(fr, USERS.requesterTn, 'fr', `/requests/${po.id}`);
  const frPanel = fr.locator('app-receipt-actions');
  await expect(frPanel.getByRole('button', { name: t('fr', 'receipt.actions.confirm') })).toBeVisible({ timeout: 20_000 });
  check('U3.8', 'French panel has no raw keys', false, await frPanel.evaluate((el) => /\b(receipt|errors)\.[a-z]/i.test((el as HTMLElement).innerText)));
  await shot(fr, 'U3-03-fr-1280-confirm');
});
