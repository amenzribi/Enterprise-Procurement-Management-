import { expect, test } from '@playwright/test';
import { Lang, USERS, approvalLink, createAndSubmit, decide, futureDate, newUserPage, signIn, t, uniqueTitle } from './support';

/**
 * S2-AC-14: the approval journeys of Solution Guide v3.0 Step 2 with the demo rules of sprint-2.md
 * (DEMO-TN-PLT: department manager; from 5 000 TND also the budget approver).
 *
 * Karim (manager.tn) approves from the e-mail link, without being signed in; Leila (budget.tn) approves from her
 * inbox; Sami (requester.tn) follows the request in the tracker. Run in English, French and Arabic.
 */
for (const lang of ['en', 'fr', 'ar'] as Lang[]) {
  test(`Sami → Karim (e-mail link) → Leila → approved, in ${lang.toUpperCase()}`, async ({ page, browser }) => {
    // 10 × 600 TND = 6 000 TND: department manager, then budget approver.
    await signIn(page, USERS.requesterTn, lang);
    if (lang === 'ar') {
      await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
    }
    const since = Date.now();
    const request = await createAndSubmit(page, lang, uniqueTitle('P-204 seals'), { quantity: '10', price: '600' });
    await expect(page.locator('.dossier-status')).toContainText(t(lang, 'requests.userStatus.SUBMITTED'));
    await expect(page.locator('app-approval-tracker .stage')).toHaveCount(2);

    // Karim opens the Approve link from his e-mail on his phone: nothing happens until he confirms.
    const link = await approvalLink(browser, USERS.managerTn, request.number, since);
    const phone = await newUserPage(browser);
    await phone.setViewportSize({ width: 360, height: 780 });
    await phone.goto(link);
    await expect(phone.locator('.number')).toContainText(request.number);
    expect(phone.url()).not.toContain('#');
    await phone.getByRole('button', { name: t('en', 'oneTap.approve') }).click();
    await expect(phone.getByRole('heading', { name: t('en', 'oneTap.approved.title') })).toBeVisible();
    // The same link cannot be used twice (leave the page first: a fragment-only change does not reload it).
    await phone.goto('about:blank');
    await phone.goto(link);
    await expect(phone.getByRole('heading', { name: t('en', 'oneTap.invalid.title') })).toBeVisible();
    await phone.context().close();

    // Leila decides from her inbox.
    const leila = await newUserPage(browser);
    await signIn(leila, USERS.budgetTn, lang, '/approvals');
    await expect(leila.locator('.card', { hasText: request.number })).toBeVisible();
    await decide(leila, lang, request.id, 'APPROVE', 'Budget line OK');
    await leila.context().close();

    // Sami sees the request approved, with both decisions in the tracker.
    await page.reload();
    await expect(page.locator('.dossier-status')).toContainText(t(lang, 'requests.userStatus.APPROVED'));
    await expect(page.locator('app-approval-tracker')).toContainText(t(lang, 'tracker.viaLink'));
    await expect(page.locator('app-approval-tracker')).toContainText('Budget line OK');
  });
}

test('return for revision, correction and resubmission start a new round (EN)', async ({ page, browser }) => {
  await signIn(page, USERS.requesterTn, 'en');
  const request = await createAndSubmit(page, 'en', uniqueTitle('Return check'));

  const karim = await newUserPage(browser);
  await signIn(karim, USERS.managerTn, 'en');
  // A return needs a reason.
  await karim.goto(`/requests/${request.id}`);
  const panel = karim.locator('app-approval-actions .decide');
  await panel.getByRole('button', { name: t('en', 'approvals.actions.RETURN') }).click();
  await panel.getByRole('button', { name: t('en', 'approvals.confirm.RETURN') }).click();
  await expect(panel.locator('#decide-error')).toContainText(t('en', 'approvals.decide.reasonRequired'));
  await decide(karim, 'en', request.id, 'RETURN', 'Attach the pump datasheet');

  await page.reload();
  await expect(page.locator('.dossier-status')).toContainText(t('en', 'requests.userStatus.RETURNED'));
  await expect(page.locator('app-approval-tracker')).toContainText('Attach the pump datasheet');
  await page.getByRole('link', { name: t('en', 'requests.detail.editReturned') }).click();
  await page.locator('#line-0-quantity').fill('3');
  await page.getByRole('button', { name: t('en', 'requests.form.submit') }).click();
  await page.getByRole('alertdialog').getByRole('button', { name: t('en', 'requests.submit.confirm') }).click();
  const successTitle = page.getByRole('heading', { name: t('en', 'requests.submit.successTitle') });
  await expect(successTitle).toBeVisible({ timeout: 30_000 });
  await page
    .getByRole('alertdialog')
    .filter({ has: successTitle })
    .getByRole('button', { name: t('en', 'requests.submit.openRequest') })
    .click();
  await expect(page).toHaveURL(new RegExp(`/requests/${request.id}$`));
  // Same number, new round; the earlier round stays readable.
  await expect(page.locator('.dossier-number')).toContainText(request.number);
  await expect(page.locator('app-approval-tracker details.earlier')).toBeVisible();

  await decide(karim, 'en', request.id, 'APPROVE');
  await karim.context().close();
  await page.reload();
  await expect(page.locator('.dossier-status')).toContainText(t('en', 'requests.userStatus.APPROVED'));
});

test('rejection is final and needs a reason (FR)', async ({ page, browser }) => {
  await signIn(page, USERS.requesterTn, 'fr');
  const request = await createAndSubmit(page, 'fr', uniqueTitle('Rejet'));

  const karim = await newUserPage(browser);
  await signIn(karim, USERS.managerTn, 'fr');
  await decide(karim, 'fr', request.id, 'REJECT', 'Pièce déjà en stock à Radès');
  await karim.context().close();

  await page.reload();
  await expect(page.locator('.dossier-status')).toContainText(t('fr', 'requests.userStatus.REJECTED'));
  await expect(page.getByRole('button', { name: t('fr', 'requests.form.submit') })).toHaveCount(0);
  await expect(page.locator('app-approval-tracker')).toContainText('Pièce déjà en stock à Radès');
});

test('question and answer keep the request with the approver (AR)', async ({ page, browser }) => {
  await signIn(page, USERS.requesterTn, 'ar');
  const request = await createAndSubmit(page, 'ar', uniqueTitle('Question'));

  const karim = await newUserPage(browser);
  await signIn(karim, USERS.managerTn, 'ar');
  await decide(karim, 'ar', request.id, 'QUESTION', 'أي مضخة بالضبط؟');

  await page.reload();
  await expect(page.locator('.dossier-status')).toContainText(t('ar', 'requests.userStatus.SUBMITTED'));
  const answer = page.locator('app-approval-actions .answer');
  await expect(answer).toContainText('أي مضخة بالضبط؟');
  await answer.locator('#answer-text').fill('المضخة P-204 في خط الإنتاج 2');
  await answer.getByRole('button', { name: t('ar', 'approvals.answer.send') }).click();
  await expect(page.getByText(t('ar', 'approvals.answer.sent'))).toBeVisible();

  await karim.goto(`/requests/${request.id}`);
  await expect(karim.locator('app-approval-tracker')).toContainText('P-204');
  await decide(karim, 'ar', request.id, 'APPROVE');
  await karim.context().close();
});

test('absence delegation sends new approvals to the delegate on behalf of the absent approver (EN)', async ({ page, browser }) => {
  // Hichem (manager2.tn) is the second department manager at Sfax: eligible to approve in Karim's place.
  const karim = await newUserPage(browser);
  await signIn(karim, USERS.managerTn, 'en', '/absences');
  const delegate = karim.locator('#absence-delegate');
  const hichem = delegate.locator('option', { hasText: 'Hichem' });
  await expect(hichem).toHaveCount(1);
  await delegate.selectOption({ label: (await hichem.innerText()).trim() });
  await karim.locator('#absence-end').fill(futureDate(2));
  await karim.getByRole('button', { name: t('en', 'absences.save') }).click();
  await expect(karim.getByText(t('en', 'absences.saved'))).toBeVisible();

  try {
    await signIn(page, USERS.requesterTn, 'en');
    const request = await createAndSubmit(page, 'en', uniqueTitle('Delegation'));
    // While Karim is away his approval goes to Hichem, marked as on his behalf (S2-AC-06).
    const task = page.locator('app-approval-tracker .task');
    await expect(task).toHaveCount(1);
    await expect(task).toContainText('Hichem');
    await expect(task).toContainText(t('en', 'tracker.onBehalfOf', { name: 'Karim Haddad (Demo)' }));
    await expect(task).toContainText(t('en', 'tracker.routingNotes.ABSENCE_DELEGATE'));

    // Hichem finds it in his inbox and approves it.
    const hichemPage = await newUserPage(browser);
    await signIn(hichemPage, USERS.manager2Tn, 'en', '/approvals');
    // The demo inbox holds many requests: search for this one.
    await hichemPage.locator('#inbox-search').fill(request.number);
    await expect(hichemPage.locator('.card', { hasText: request.number })).toBeVisible();
    await decide(hichemPage, 'en', request.id, 'APPROVE');
    await hichemPage.context().close();
    await page.reload();
    await expect(page.locator('.dossier-status')).toContainText(t('en', 'requests.userStatus.APPROVED'));
  } finally {
    // Leave no absence behind: other tests expect Karim to receive his approvals. Wait for the list to load first,
    // otherwise no "Remove" button exists yet and the absence would survive.
    await karim.goto('/absences');
    await expect(karim.locator('#absence-list ~ .list, #absence-list ~ app-empty-state').first()).toBeVisible();
    while ((await karim.getByRole('button', { name: t('en', 'absences.remove') }).count()) > 0) {
      await karim.getByRole('button', { name: t('en', 'absences.remove') }).first().click();
      await karim.getByRole('alertdialog').getByRole('button', { name: t('en', 'absences.remove') }).click();
      await expect(karim.getByText(t('en', 'absences.removed')).last()).toBeVisible();
    }
    await karim.context().close();
  }
});
