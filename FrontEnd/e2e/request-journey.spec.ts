import { expect, test } from '@playwright/test';
import { Lang, USERS, fillHeader, fillLine, newUserPage, signIn, t, uniqueTitle } from './support';

/**
 * S1-AC-16: sign in -> create a draft with 2 lines -> save -> edit -> submit -> find it in "My requests" -> the detail
 * shows number, status and owner; a user of another company cannot open it.
 */
const journeys: { lang: Lang; user: string; onBehalf: boolean }[] = [
  { lang: 'en', user: USERS.requesterTn, onBehalf: false },
  // In French, the assistant enters the request on behalf of a colleague (S1-AC-04).
  { lang: 'fr', user: USERS.assistantTn, onBehalf: true },
];

for (const { lang, user, onBehalf } of journeys) {
  test(`request journey in ${lang.toUpperCase()}${onBehalf ? ' (entered on behalf)' : ''}`, async ({ page, browser }) => {
    const title = uniqueTitle(lang === 'fr' ? 'Roulements ligne 2' : 'Bearings line 2');

    await signIn(page, user, lang);
    await expect(page.locator('html')).toHaveAttribute('lang', lang);
    await expect(page.getByRole('heading', { level: 1, name: t(lang, 'requests.list.title') })).toBeVisible();

    // Create a draft with two lines and save it.
    await page.getByRole('link', { name: t(lang, 'requests.list.new') }).first().click();
    await expect(page).toHaveURL(/\/requests\/new$/);
    await fillHeader(page, title);
    if (onBehalf) {
      await page.locator('#request-requesterId').selectOption({ index: 1 });
    }
    await fillLine(page, 0, { description: 'SKF 6205 bearing', quantity: '10', unit: 'PCE', price: lang === 'fr' ? '12,5' : '12.5' });
    await page.getByRole('button', { name: t(lang, 'requests.form.addLine') }).click();
    await fillLine(page, 1, { description: 'Lithium grease', quantity: '2.5', unit: 'KG', price: '8' });

    await page.getByRole('button', { name: t(lang, 'requests.form.saveDraft') }).click();
    await expect(page.getByText(t(lang, 'requests.form.saved'))).toBeVisible();
    await expect(page).toHaveURL(/\/requests\/\d+\/edit$/);
    const id = Number(/\/requests\/(\d+)\/edit/.exec(page.url())![1]);

    // Edit: change the title and a quantity, save again.
    await page.locator('#request-title').fill(`${title} *`);
    await page.locator('#line-1-quantity').fill('3');
    await page.getByRole('button', { name: t(lang, 'requests.form.saveDraft') }).click();
    await expect(page.getByText(t(lang, 'requests.form.saved')).last()).toBeVisible();

    // Submit after confirmation.
    await page.getByRole('button', { name: t(lang, 'requests.form.submit') }).click();
    const dialog = page.getByRole('alertdialog');
    await expect(dialog).toContainText(t(lang, 'requests.submit.title'));
    await dialog.getByRole('button', { name: t(lang, 'requests.submit.confirm') }).click();

    await expect(page).toHaveURL(new RegExp(`/requests/${id}$`));
    const number = page.locator('.dossier-number');
    await expect(number).toHaveText(/^\s*\S+-PR-\d{4}-\d{6}\s*$/);
    const requestNumber = (await number.innerText()).trim();
    await expect(page.locator('.dossier-status')).toContainText(t(lang, 'requests.status.CREATED'));
    await expect(page.locator('.dossier-facts')).toContainText(t(lang, 'requests.stages.REQUEST'));
    // The edit and submit actions are gone once submitted.
    await expect(page.getByRole('link', { name: t(lang, 'requests.detail.edit') })).toHaveCount(0);

    // Find it in "My requests" (the assistant sees requests they entered).
    await page.goto('/requests');
    await page.getByRole('searchbox').fill(requestNumber);
    await expect(page.getByRole('link', { name: requestNumber })).toBeVisible();

    // Another company's requester gets "not found", in the UI and from the API (S1-AC-07).
    const other = await newUserPage(browser);
    await signIn(other, USERS.requesterFr, lang);
    await other.goto(`/requests/${id}`);
    await expect(other.getByRole('heading', { level: 1, name: t(lang, 'requests.notFound.title') })).toBeVisible();
    const api = await other.request.get(`/api/v1/requests/${id}`);
    expect(api.status()).toBe(404);
    await other.context().close();
  });
}
