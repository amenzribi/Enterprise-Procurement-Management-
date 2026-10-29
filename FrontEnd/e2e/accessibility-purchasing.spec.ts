import AxeBuilder from '@axe-core/playwright';
import { Page, expect, test } from '@playwright/test';
import { Lang, USERS, newUserPage, signIn, t } from './support';

/** S3-AC-10: no axe violations, LTR and RTL, on the queue, supplier list, org admin and import, at 1280 and 360 px. */
const WIDTHS = [
  { name: 'desktop', width: 1280, height: 900 },
  { name: 'phone', width: 360, height: 780 },
];

async function expectNoViolations(page: Page, label: string): Promise<void> {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze();
  const summary = results.violations.map((violation) => `${violation.id}: ${violation.nodes.map((node) => node.target.join(' ')).join(', ')}`);
  expect(summary, label).toEqual([]);
}

async function noHorizontalScroll(page: Page, label: string): Promise<void> {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow, `${label}: horizontal scroll`).toBeLessThanOrEqual(0);
}

for (const lang of ['en', 'ar'] as Lang[]) {
  for (const size of WIDTHS) {
    test(`purchasing screens accessibility ${lang.toUpperCase()} ${size.name}`, async ({ page, browser }) => {
      await page.setViewportSize({ width: size.width, height: size.height });
      await signIn(page, USERS.buyer, lang, '/purchasing');
      await expect(page.locator('html')).toHaveAttribute('dir', lang === 'ar' ? 'rtl' : 'ltr');
      await expect(page.getByRole('heading', { level: 1, name: t(lang, 'purchasing.inbox.title') })).toBeVisible();
      await expectNoViolations(page, 'purchasing inbox');
      await noHorizontalScroll(page, 'purchasing inbox');

      await page.goto('/suppliers');
      await expect(page.getByRole('heading', { level: 1, name: t(lang, 'suppliers.title') })).toBeVisible();
      await expectNoViolations(page, 'suppliers');
      await noHorizontalScroll(page, 'suppliers');

      const mehdi = await newUserPage(browser);
      await mehdi.setViewportSize({ width: size.width, height: size.height });
      await signIn(mehdi, USERS.purchasingManager, lang, '/admin/import');
      await expect(mehdi.getByRole('heading', { level: 1, name: t(lang, 'imports.title') })).toBeVisible();
      await expectNoViolations(mehdi, 'import');
      await noHorizontalScroll(mehdi, 'import');
      await mehdi.context().close();

      if (size.name === 'desktop') {
        const admin = await newUserPage(browser);
        await signIn(admin, USERS.admin, lang, '/admin/organization');
        await expect(admin.getByRole('heading', { level: 1, name: t(lang, 'org.title') })).toBeVisible();
        await expectNoViolations(admin, 'organisation');
        await admin.context().close();
      }
    });
  }
}
