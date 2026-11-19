import { APIRequestContext, BrowserContext, Page, expect, test } from '@playwright/test';
import { USERS, fillHeader, fillLine, newUserPage, signIn, t, uniqueTitle } from './support';

/**
 * Sprint 1 hardening, seen from the browser (audit S-1, S-2, S-4, S-5, S-7): the screens whose behaviour changed.
 * API calls made here use the signed-in browser session, like the application does.
 */

async function csrf(context: BrowserContext): Promise<string> {
  return (await context.cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? '';
}

async function post(page: Page, url: string, data?: unknown) {
  const api: APIRequestContext = page.request;
  return api.post(url, { data, headers: { 'X-XSRF-TOKEN': await csrf(page.context()) } });
}

async function openUser(page: Page, displayName: string): Promise<void> {
  await page.goto('/admin/users');
  await expect(page.getByRole('heading', { level: 1, name: t('en', 'usersAdmin.title') })).toBeVisible();
  await page.getByRole('button', { name: displayName, exact: true }).first().click();
  await expect(page.getByRole('button', { name: t('en', 'usersAdmin.resetPassword') })).toBeVisible();
}

test('nobody chooses another person\'s password: administrators send the owner a reset link (ADR-016)', async ({ page, browser }) => {
  test.setTimeout(180_000);
  const users = [USERS.admin, USERS.globalAdmin];
  let samiId = 0;
  for (const [index, who] of users.entries()) {
    const admin = index === 0 ? page : await newUserPage(browser);
    await signIn(admin, who, 'en');
    await openUser(admin, 'Sami Ben Ali (Demo)');
    // No password form for anyone; the reset link is the only action on the owner's password.
    await expect(admin.locator('input[type="password"]')).toHaveCount(0);
    await expect(admin.getByRole('button', { name: t('en', 'usersAdmin.resetPassword') })).toBeVisible();
    if (!samiId) {
      const list = await (await admin.request.get('/api/v1/admin/accounts?size=100')).json();
      samiId = list.find((user: { email: string }) => user.email === USERS.requesterTn).id;
    }
    // The endpoint that let an administrator choose a password no longer exists.
    const refused = await post(admin, `/api/v1/admin/accounts/${samiId}/temporary-password`, { temporaryPassword: 'Chosen-By-Admin-2026' });
    expect(refused.status(), who).toBe(404);
    if (index > 0) {
      await admin.context().close();
    }
  }
  // The account still opens with its own password.
  const samiPage = await newUserPage(browser);
  await signIn(samiPage, USERS.requesterTn, 'en');
  await samiPage.context().close();
});

test('an attachment must match its type, is shown as not virus-checked, and stays invisible to another company', async ({ page, browser }) => {
  const title = uniqueTitle('S1 attachment');
  await signIn(page, USERS.requesterTn, 'en');
  await page.getByRole('link', { name: t('en', 'requests.list.new') }).first().click();
  await fillHeader(page, title);
  await fillLine(page, 0, { description: 'Bearing', quantity: '1', unit: 'PCE', price: '10' });
  await page.getByRole('button', { name: t('en', 'requests.form.saveDraft') }).click();
  await expect(page).toHaveURL(/\/requests\/\d+\/edit$/);
  const id = Number(/\/requests\/(\d+)\/edit/.exec(page.url())![1]);

  const pdf = Buffer.from('%PDF-1.7\n1 0 obj\n<<>>\nendobj\n', 'latin1');
  const panel = page.locator('app-attachments');
  await panel.locator('#attachment-file').setInputFiles({ name: 'datasheet.pdf', mimeType: 'application/pdf', buffer: pdf });
  const row = panel.locator('li', { hasText: 'datasheet.pdf' });
  await expect(row).toBeVisible();
  // No antivirus on this instance: the file is labelled as such, never as checked.
  await expect(row).toContainText(t('en', 'attachments.notScanned'));
  const link = row.locator('a.file-name');
  await expect(link).toBeVisible();
  const list = await (await page.request.get(`/api/v1/requests/${id}/attachments`)).json();
  expect(list).toHaveLength(1);
  expect(list[0].scanStatus).toBe('PENDING');
  expect(list[0].downloadable).toBe(true);
  expect((await page.request.get(list[0].downloadUrl)).status()).toBe(200);

  // A script behind a PDF header, under a name Windows would run.
  await panel.locator('#attachment-file').setInputFiles({
    name: 'invoice.bat',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.7\r\n@echo off\r\ncalc.exe\r\n', 'latin1'),
  });
  await expect(page.getByText(t('en', 'errors.file-type-not-allowed')).last()).toBeVisible();
  await expect(panel.locator('li', { hasText: 'invoice.bat' })).toHaveCount(0);
  expect(await (await page.request.get(`/api/v1/requests/${id}/attachments`)).json()).toHaveLength(1);

  const other = await newUserPage(browser);
  await signIn(other, USERS.requesterFr, 'en');
  expect((await other.request.get(`/api/v1/requests/${id}/attachments`)).status()).toBe(404);
  expect((await other.request.get(list[0].downloadUrl)).status()).toBe(404);
  await other.context().close();
});

test('a company administrator sees only their own companies and cannot become an approver\'s delegate', async ({ page, browser }) => {
  test.setTimeout(180_000);
  // Give the French plant its own administrator for the duration of the test.
  const global = await newUserPage(browser);
  await signIn(global, USERS.globalAdmin, 'en');
  const companies: { id: number; code: string }[] = await (await global.request.get('/api/v1/admin/companies')).json();
  const fr = companies.find((company) => company.code === 'DEMO-FR-PLT')!;
  const tn = companies.find((company) => company.code === 'DEMO-TN-PLT')!;
  expect(companies.length).toBeGreaterThanOrEqual(3);
  const everyone: { id: number; email: string }[] = await (await global.request.get('/api/v1/admin/users')).json();
  const julien = everyone.find((user) => user.email === USERS.managerFr)!;
  const leila = everyone.find((user) => user.email === USERS.budgetTn)!;
  const granted = await post(global, `/api/v1/admin/accounts/${julien.id}/roles`, { role: 'ADMIN', companyId: fr.id, departmentId: null });
  expect(granted.status(), await granted.text()).toBe(200);
  const grant = (await granted.json()).roles.find((role: { role: string; companyId: number }) => role.role === 'ADMIN' && role.companyId === fr.id);

  try {
    await signIn(page, USERS.managerFr, 'en');
    await page.goto('/admin/approval-rules');
    await page.getByRole('button', { name: t('en', 'rules.new') }).click();
    await expect(page.locator('#rule-company')).toBeVisible();
    // The placeholder and the one company this administrator administers (companies are listed by name).
    // The list is filled when the companies arrive: wait for it before reading it.
    await expect(page.locator('#rule-company option')).toHaveCount(2);
    const options = await page.locator('#rule-company option').allInnerTexts();
    expect(options.join(' | ')).toContain('Lyon');
    expect(options.join(' | ')).not.toMatch(/Sfax|Sourcing|Holding/);

    const visible: { email: string }[] = await (await page.request.get('/api/v1/admin/users')).json();
    const emails = visible.map((user) => user.email);
    expect(emails).toContain(USERS.requesterFr);
    expect(emails).not.toContain(USERS.requesterTn);
    expect(emails).not.toContain(USERS.buyer);
    expect((await page.request.get(`/api/v1/admin/users?companyId=${tn.id}`)).status()).toBe(404);
    expect((await page.request.get(`/api/v1/admin/approval-rules?companyId=${tn.id}`)).status()).toBe(404);
  } finally {
    const removed = await post(global, `/api/v1/admin/accounts/${julien.id}/roles/${grant.id}/remove`);
    expect(removed.status()).toBe(200);
  }

  // The demo administrator administers the Tunisian plant, and still cannot route its budget approvals to themselves.
  const admin = await newUserPage(browser);
  await signIn(admin, USERS.admin, 'en');
  const me = await (await admin.request.get('/api/v1/me')).json();
  const starts = new Date(Date.now() + 500 * 86_400_000).toISOString();
  const ends = new Date(Date.now() + 501 * 86_400_000).toISOString();
  const self = await post(admin, '/api/v1/admin/absences', { userId: leila.id, delegateUserId: me.id, adminOverride: true, startsAt: starts, endsAt: ends });
  expect(self.status()).toBe(422);
  expect(await self.text()).toContain('delegate-is-administrator');

  // Review R-1 to R-3: nor through a supplier contact, an account they just created, or a role given to themselves.
  const contact = everyone.find((user) => user.email === 'contact.skf@demo.local');
  if (contact) {
    const viaContact = await post(admin, '/api/v1/admin/absences', { userId: leila.id, delegateUserId: contact.id, adminOverride: true, startsAt: starts, endsAt: ends });
    expect(viaContact.status()).toBe(422);
  }
  const puppet = await post(admin, '/api/v1/admin/accounts', {
    email: `s1.e2e.puppet.${Date.now()}@demo.local`,
    displayName: 'S1 E2E Puppet',
    companyId: tn.id,
    preferredLanguage: 'en',
    active: true,
    roles: [],
  });
  expect(puppet.status(), await puppet.text()).toBe(201);
  const puppetId = (await puppet.json()).id;
  // F-1: the new account is inactive until another administrator activates it; the creator cannot.
  expect((await post(admin, `/api/v1/admin/accounts/${puppetId}/active`, { active: true })).status()).toBe(403);
  expect((await post(global, `/api/v1/admin/accounts/${puppetId}/active`, { active: true })).status()).toBe(200);
  const viaPuppet = await post(admin, '/api/v1/admin/absences', { userId: leila.id, delegateUserId: puppetId, adminOverride: true, startsAt: starts, endsAt: ends });
  expect(viaPuppet.status()).toBe(422);
  expect(await viaPuppet.text()).toContain('delegate-created-by-administrator');
  const promote = await post(admin, `/api/v1/admin/accounts/${puppetId}/roles`, { role: 'BUDGET_APPROVER', companyId: tn.id, departmentId: null });
  expect(promote.status()).toBe(403);
  const selfGrant = await post(admin, `/api/v1/admin/accounts/${me.id}/roles`, { role: 'BUDGET_APPROVER', companyId: tn.id, departmentId: null });
  expect(selfGrant.status()).toBe(403);
  expect((await (await admin.request.get('/api/v1/me')).json()).roles).toEqual(['ADMIN']);
  expect((await post(global, `/api/v1/admin/accounts/${puppetId}/active`, { active: false })).status()).toBe(200);
  await admin.context().close();
  await global.context().close();
});
