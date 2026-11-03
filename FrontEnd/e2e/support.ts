import { Browser, Page, expect } from '@playwright/test';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import ar from '../public/i18n/ar.json';
import en from '../public/i18n/en.json';
import fr from '../public/i18n/fr.json';

export type Lang = 'en' | 'fr' | 'ar';

interface Tree {
  [key: string]: string | Tree;
}

const DICTIONARIES: Record<Lang, Tree> = { en: en as Tree, fr: fr as Tree, ar: ar as Tree };

/** The exact interface text for a key, read from the real translation files (no duplicated strings in tests). */
export function t(lang: Lang, key: string, params: Record<string, string | number> = {}): string {
  const value = key.split('.').reduce<string | Tree | undefined>((node, part) => (typeof node === 'object' ? node[part] : undefined), DICTIONARIES[lang]);
  if (typeof value !== 'string') {
    throw new Error(`Missing translation ${lang}:${key}`);
  }
  return value.replace(/\{\{\s*(\w+)\s*\}\}/g, (_, name: string) => String(params[name] ?? ''));
}

/** Demo accounts (sprint-1.md). The password is never stored in the repository. */
export const USERS = {
  requesterTn: 'requester.tn@demo.local',
  assistantTn: 'assistant.tn@demo.local',
  managerTn: 'manager.tn@demo.local',
  manager2Tn: 'manager2.tn@demo.local',
  budgetTn: 'budget.tn@demo.local',
  requesterFr: 'requester.fr@demo.local',
  managerFr: 'manager.fr@demo.local',
  buyer: 'buyer@demo.local',
  purchasingManager: 'purchasing.manager@demo.local',
  receiverTn: 'receiver.tn@demo.local',
  ceo: 'ceo@demo.local',
  admin: 'admin@demo.local',
  globalAdmin: 'global.admin@demo.local',
} as const;

export function demoPassword(): string {
  const password = process.env['DEMO_USER_PASSWORD'];
  if (!password) {
    throw new Error('Set DEMO_USER_PASSWORD to the password the backend uses for demo users (see README).');
  }
  return password;
}

/** Signs in through the real sign-in page, optionally choosing the language first (which is saved to the account). */
export async function signIn(page: Page, email: string, lang: Lang = 'en', startAt = '/requests'): Promise<void> {
  await page.goto(startAt);
  await expect(page).toHaveURL(/\/sign-in/);
  await page.locator(`app-language-select button[lang="${lang}"]`).click();
  await page.locator('#sign-in-email').fill(email);
  await page.locator('#sign-in-password').fill(demoPassword());
  await page.getByRole('button', { name: t(lang, 'auth.signIn.submit') }).click();
  await expect(page).not.toHaveURL(/\/sign-in/);
  // The account's saved language applies at sign-in unless the user changed it on the sign-in page (S1DR-21);
  // switch explicitly when another test left a different preference on this account.
  if ((await page.locator('html').getAttribute('lang')) !== lang) {
    await page.locator(`app-language-select button[lang="${lang}"]`).click();
  }
  await expect(page.locator('html')).toHaveAttribute('lang', lang);
}

/** A fresh browser context (separate cookies and storage), i.e. another person on another computer. */
export async function newUserPage(browser: Browser): Promise<Page> {
  const context = await browser.newContext();
  return context.newPage();
}

export function futureDate(days: number): string {
  return new Date(Date.now() + days * 86_400_000).toISOString().slice(0, 10);
}

export function uniqueTitle(prefix: string): string {
  return `${prefix} ${new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14)}`;
}

/** Fills the mandatory header fields of the request form. */
export async function fillHeader(page: Page, title: string): Promise<void> {
  await page.locator('#request-title').fill(title);
  await page.locator('#request-categoryId').selectOption({ index: 1 });
  const department = page.locator('#request-departmentId');
  if ((await department.inputValue()) === '' || (await department.locator('option:checked').innerText()).includes('…')) {
    await department.selectOption({ index: 1 });
  }
  await page.locator('#request-requiredDate').fill(futureDate(21));
}

export async function fillLine(page: Page, index: number, line: { description: string; quantity: string; unit: string; price: string }): Promise<void> {
  await page.locator(`#line-${index}-description`).fill(line.description);
  await page.locator(`#line-${index}-quantity`).fill(line.quantity);
  const unit = page.locator(`#line-${index}-unit`);
  if ((await unit.evaluate((element) => element.tagName)) === 'SELECT') {
    await unit.selectOption({ index: 1 });
  } else {
    await unit.fill(line.unit);
  }
  await page.locator(`#line-${index}-estimatedUnitPrice`).fill(line.price);
}

/** Folder where the backend writes e-mails as .eml files in development (S2-AC-10). */
const MAIL_DIR = process.env['MAIL_DIR'] ?? resolve(__dirname, '../../BackEnd/storage/mail');

/** Reads an .eml file and undoes quoted-printable encoding so links can be found in it. */
function readMail(path: string): string {
  return readFileSync(path, 'utf8')
    .replace(/=\r?\n/g, '')
    .replace(/=([0-9A-F]{2})/g, (_: string, hex: string) => String.fromCharCode(parseInt(hex, 16)));
}

/**
 * Runs the backend's background jobs now (dev test hook, administrators only): sends the e-mails waiting in the outbox
 * and the due reminders, so tests do not wait for the scheduler.
 */
export async function runBackgroundJobs(browser: Browser): Promise<void> {
  const context = await browser.newContext();
  const api = context.request;
  const csrf = async () => {
    await api.get('/api/v1/auth/csrf');
    return (await context.cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? '';
  };
  const login = await api.post('/api/v1/auth/login', {
    data: { email: USERS.admin, password: demoPassword() },
    headers: { 'X-XSRF-TOKEN': await csrf() },
  });
  expect(login.ok(), `admin sign-in for the test hook: ${login.status()}`).toBe(true);
  const run = await api.post('/api/v1/testing/jobs/run', { headers: { 'X-XSRF-TOKEN': await csrf() } });
  expect(run.ok(), `POST /testing/jobs/run: ${run.status()} ${await run.text()}`).toBe(true);
  await context.close();
}

/**
 * Waits for the one-tap approval e-mail sent to `recipient` about `requestNumber` after `since`, and returns the
 * path-and-fragment of its Approve link (`/a#<token>`), ready for `page.goto` on the test's base URL. The outbox is
 * flushed through the test hook first.
 */
export async function approvalLink(
  browser: Browser,
  recipient: string,
  requestNumber: string,
  since: number,
  timeoutMs = 30_000,
): Promise<string> {
  await runBackgroundJobs(browser);
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    let files: string[] = [];
    try {
      files = readdirSync(MAIL_DIR).filter((name) => name.endsWith('.eml'));
    } catch {
      // The folder appears with the first e-mail.
    }
    const candidates = files
      .map((name) => join(MAIL_DIR, name))
      .filter((path) => statSync(path).mtimeMs >= since - 1000)
      .sort((a, b) => statSync(b).mtimeMs - statSync(a).mtimeMs);
    for (const path of candidates) {
      const mail = readMail(path);
      if (mail.toLowerCase().includes(recipient.toLowerCase()) && mail.includes(requestNumber)) {
        const match = /\/a#([A-Za-z0-9_-]{20,512})/.exec(mail);
        if (match) {
          return `/a#${match[1]}`;
        }
      }
    }
    await new Promise((done) => setTimeout(done, 1000));
  }
  throw new Error(`No approval e-mail for ${recipient} about ${requestNumber} in ${MAIL_DIR}`);
}

/**
 * Creates a request with one line (quantity × unit price, in the company currency) and submits it through the
 * approval preview. Returns its id and number.
 */
export async function createAndSubmit(
  page: Page,
  lang: Lang,
  title: string,
  line: { quantity: string; price: string } = { quantity: '2', price: '150' },
): Promise<{ id: number; number: string }> {
  await page.goto('/requests/new');
  await expect(page.locator('#request-title')).toBeVisible();
  await fillHeader(page, title);
  await fillLine(page, 0, { description: 'Mechanical seal P-204', quantity: line.quantity, unit: 'PCE', price: line.price });
  await page.getByRole('button', { name: t(lang, 'requests.form.submit') }).click();
  const preview = page.getByRole('alertdialog');
  await expect(preview).toContainText(t(lang, 'approvals.preview.title'));
  await preview.getByRole('button', { name: t(lang, 'requests.submit.confirm') }).click();
  // Success dialog replaces the preview; wait for its title (submit can be slow under parallel IT load).
  const successTitle = page.getByRole('heading', { name: t(lang, 'requests.submit.successTitle') });
  await expect(successTitle).toBeVisible({ timeout: 30_000 });
  const success = page.getByRole('alertdialog').filter({ has: successTitle });
  await expect(success.locator('.number')).not.toBeEmpty();
  const number = (await success.locator('.number').innerText()).trim();
  await success.getByRole('button', { name: t(lang, 'requests.submit.openRequest') }).click();
  await expect(page).toHaveURL(/\/requests\/\d+$/);
  const id = Number(/\/requests\/(\d+)$/.exec(page.url())![1]);
  await expect(page.locator('.dossier-number')).toContainText(number);
  return { id, number };
}

/** Opens a request as the signed-in approver and takes a decision from the decision panel. */
export async function decide(
  page: Page,
  lang: Lang,
  requestId: number,
  action: 'APPROVE' | 'QUESTION' | 'RETURN' | 'REJECT',
  text = '',
): Promise<void> {
  await page.goto(`/requests/${requestId}`);
  const panel = page.locator('app-approval-actions .decide');
  await expect(panel).toBeVisible();
  await panel.getByRole('button', { name: t(lang, `approvals.actions.${action}`) }).click();
  if (text) {
    await panel.locator('#decide-text').fill(text);
  }
  await panel.getByRole('button', { name: t(lang, `approvals.confirm.${action}`) }).click();
  await expect(page.getByText(t(lang, `approvals.done.${action}`)).first()).toBeVisible();
}
