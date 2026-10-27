import { Browser, Page, expect } from '@playwright/test';
import en from '../public/i18n/en.json';
import fr from '../public/i18n/fr.json';

export type Lang = 'en' | 'fr';

interface Tree {
  [key: string]: string | Tree;
}

const DICTIONARIES: Record<Lang, Tree> = { en: en as Tree, fr: fr as Tree };

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
  requesterFr: 'requester.fr@demo.local',
  buyer: 'buyer@demo.local',
  ceo: 'ceo@demo.local',
  admin: 'admin@demo.local',
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
  if (lang === 'fr') {
    await page.locator('app-language-select button[lang="fr"]').click();
  } else {
    await page.locator('app-language-select button[lang="en"]').click();
  }
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
