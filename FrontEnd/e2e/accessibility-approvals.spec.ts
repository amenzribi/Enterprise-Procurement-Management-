import AxeBuilder from '@axe-core/playwright';
import { Page, expect, test } from '@playwright/test';
import { Lang, USERS, createAndSubmit, newUserPage, signIn, t, uniqueTitle } from './support';

/**
 * S2-AC-14: no axe violations, left to right and right to left, on the inbox, the approval page with the tracker,
 * the one-tap page, the absences page and the administration pages, at 1280 and 360 px.
 */
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
    test(`approval screens accessibility ${lang.toUpperCase()} ${size.name}`, async ({ page, browser }) => {
      test.setTimeout(180_000);
      // Clear leftover absences so Karim (not a delegate) receives the new task for inbox content.
      await page.setViewportSize({ width: size.width, height: size.height });
      await signIn(page, USERS.managerTn, lang, '/absences');
      await expect(page.locator('#absence-delegate')).toBeVisible();
      while ((await page.getByRole('button', { name: t(lang, 'absences.remove') }).count()) > 0) {
        await page.getByRole('button', { name: t(lang, 'absences.remove') }).first().click();
        await page.getByRole('alertdialog').getByRole('button', { name: t(lang, 'absences.remove') }).click();
        await expect(page.getByText(t(lang, 'absences.removed')).last()).toBeVisible();
      }

      const sami = await newUserPage(browser);
      await signIn(sami, USERS.requesterTn, lang);
      const request = await createAndSubmit(sami, lang, uniqueTitle('Axe approvals'));
      await sami.setViewportSize({ width: size.width, height: size.height });
      await expectNoViolations(sami, 'tracker (requester)');
      await noHorizontalScroll(sami, 'tracker (requester)');
      await sami.context().close();

      // Search by number: Karim may have hundreds of pending items sorted by waiting-longest.
      await page.goto(`/approvals?q=${encodeURIComponent(request.number)}`);
      await expect(page.locator('html')).toHaveAttribute('dir', lang === 'ar' ? 'rtl' : 'ltr');
      await expect(page.getByRole('heading', { level: 1, name: t(lang, 'approvals.inbox.title') })).toBeVisible();
      await expect(page.locator('.card').getByText(request.number)).toBeVisible({ timeout: 20_000 });
      await expectNoViolations(page, 'inbox');
      await noHorizontalScroll(page, 'inbox');

      await page.goto(`/requests/${request.id}`);
      const panel = page.locator('app-approval-actions .decide');
      await expect(panel).toBeVisible();
      await expectNoViolations(page, 'approval page');
      await noHorizontalScroll(page, 'approval page');
      await panel.getByRole('button', { name: t(lang, 'approvals.actions.REJECT') }).click();
      await panel.getByRole('button', { name: t(lang, 'approvals.confirm.REJECT') }).click();
      await expect(panel.locator('#decide-error')).not.toBeEmpty();
      await expectNoViolations(page, 'approval page with an error');

      await page.goto('/absences');
      await expect(page.locator('#absence-delegate')).toBeVisible();
      await expectNoViolations(page, 'absences');
      await noHorizontalScroll(page, 'absences');

      // The one-tap page without a valid token (neutral state), outside the workspace.
      await page.goto('/a#invalid');
      await expect(page.getByRole('heading', { name: t(lang, 'oneTap.invalid.title') })).toBeVisible();
      await expectNoViolations(page, 'one-tap (invalid link)');
      await noHorizontalScroll(page, 'one-tap (invalid link)');

      if (size.name === 'desktop') {
        const admin = await newUserPage(browser);
        await signIn(admin, USERS.admin, lang, '/admin/approval-rules');
        await expect(admin.getByRole('heading', { level: 1, name: t(lang, 'rules.title') })).toBeVisible();
        await expectNoViolations(admin, 'approval rules');
        await admin.goto('/admin/exchange-rates');
        await expect(admin.getByRole('heading', { level: 1, name: t(lang, 'rates.title') })).toBeVisible();
        await expectNoViolations(admin, 'exchange rates');
        await admin.context().close();
      }
    });
  }
}
