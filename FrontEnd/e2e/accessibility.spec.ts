import AxeBuilder from '@axe-core/playwright';
import { Page, expect, test } from '@playwright/test';
import { Lang, USERS, fillHeader, fillLine, signIn, t, uniqueTitle } from './support';

/** S1-AC-10: no axe violations (WCAG 2.2 A/AA rules) on sign-in, list, form and detail, in EN and FR, at 1280 and 360 px. */
const WIDTHS = [
  { name: 'desktop', width: 1280, height: 900 },
  { name: 'phone', width: 360, height: 780 },
];

async function expectNoViolations(page: Page, label: string): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .analyze();
  const summary = results.violations.map((violation) => `${violation.id}: ${violation.nodes.map((node) => node.target.join(' ')).join(', ')}`);
  expect(summary, label).toEqual([]);
}

async function noHorizontalScroll(page: Page, label: string): Promise<void> {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow, `${label}: horizontal scroll`).toBeLessThanOrEqual(0);
}

for (const lang of ['en', 'fr'] as Lang[]) {
  for (const size of WIDTHS) {
    test(`accessibility ${lang.toUpperCase()} ${size.name}`, async ({ page }) => {
      await page.setViewportSize({ width: size.width, height: size.height });

      await page.goto('/sign-in');
      await page.locator(`app-language-select button[lang="${lang}"]`).click();
      await expectNoViolations(page, 'sign-in');
      await noHorizontalScroll(page, 'sign-in');

      await signIn(page, USERS.requesterTn, lang);
      await expect(page.getByRole('heading', { level: 1, name: t(lang, 'requests.list.title') })).toBeVisible();
      await expectNoViolations(page, 'list');
      await noHorizontalScroll(page, 'list');
      // Row hover changes the background behind links: check that state too (QA1-D-01).
      const firstRow = page.locator('tbody tr, .card').first();
      if (await firstRow.count()) {
        await firstRow.hover();
        await expectNoViolations(page, 'list with hovered row');
      }

      await page.goto('/requests/new');
      await expect(page.locator('#request-title')).toBeVisible();
      await expectNoViolations(page, 'form');
      await noHorizontalScroll(page, 'form');

      // Show validation errors too: they must be announced and linked to their fields.
      await page.getByRole('button', { name: t(lang, 'requests.form.submit') }).click();
      await expect(page.locator('#form-error-summary')).toBeFocused();
      await expectNoViolations(page, 'form with errors');

      await fillHeader(page, uniqueTitle('Axe check'));
      await fillLine(page, 0, { description: 'Gloves', quantity: '4', unit: 'PCE', price: '3' });
      await page.getByRole('button', { name: t(lang, 'requests.form.saveDraft') }).click();
      await expect(page).toHaveURL(/\/requests\/\d+\/edit$/);
      const id = /\/requests\/(\d+)\/edit/.exec(page.url())![1];

      await page.goto(`/requests/${id}`);
      await expect(page.locator('.dossier')).toBeVisible();
      await expectNoViolations(page, 'detail');
      await noHorizontalScroll(page, 'detail');
    });
  }
}
