import { expect, test } from '@playwright/test';
import { USERS, createAndSubmit, newUserPage, signIn, uniqueTitle } from './support';

/**
 * Wide-screen inbox: the queue sits beside the decision. Covers what the record-page journeys do not: choosing a row
 * with the keyboard, deciding in the pane, and the next item taking its place.
 */
test.describe('approval inbox split pane', () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test('a row is chosen with the keyboard and a decision advances the queue', async ({ page, browser }) => {
    test.setTimeout(120_000);
    const sami = await newUserPage(browser);
    await signIn(sami, USERS.requesterTn, 'en');
    await createAndSubmit(sami, 'en', uniqueTitle('Split one'));
    await createAndSubmit(sami, 'en', uniqueTitle('Split two'));
    await sami.context().close();
    await signIn(page, USERS.managerTn, 'en', '/approvals');
    const rows = page.locator('.card');
    await expect(rows.first()).toBeVisible({ timeout: 20_000 });
    await expect(rows.nth(1)).toBeVisible();

    const second = rows.nth(1);
    const secondTitle = (await second.locator('.card-title').innerText()).trim();
    await second.locator('.row-select').focus();
    await page.keyboard.press('Enter');
    await expect(page.locator('.pane-title')).toHaveText(secondTitle);
    await expect(second.locator('.row-select')).toHaveAttribute('aria-pressed', 'true');

    const pane = page.locator('.pane');
    await pane.getByRole('button', { name: 'Approve', exact: true }).click();
    await pane.getByRole('button', { name: 'Confirm approval' }).click();
    await expect(page.locator('.pane-title')).not.toHaveText(secondTitle, { timeout: 15_000 });
    await expect(page.locator('.card-title', { hasText: new RegExp(`^\\s*${secondTitle}\\s*$`) })).toHaveCount(0);
  });

  test('reduced motion removes the row transition', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await signIn(page, USERS.managerTn, 'en', '/approvals');
    const row = page.locator('.card').first();
    await expect(row).toBeVisible({ timeout: 20_000 });
    const duration = await row.evaluate((el) => parseFloat(getComputedStyle(el).transitionDuration));
    expect(duration).toBeLessThan(0.001);
  });
});
