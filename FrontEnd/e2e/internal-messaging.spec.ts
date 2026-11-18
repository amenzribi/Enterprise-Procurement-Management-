import { expect, test } from '@playwright/test';
import { USERS, createAndSubmit, newUserPage, signIn, t, uniqueTitle } from './support';

/**
 * Internal employee messaging through the browser (docs/architecture/internal-messaging.md): "Message approver",
 * live delivery and the unread badge, opening the request from the card, the request discussion, and refusals.
 */

test('a requester messages the current approver, who opens the request from the message', async ({ page, browser }) => {
  test.setTimeout(150_000);
  await signIn(page, USERS.requesterTn, 'en', '/requests');
  const request = await createAndSubmit(page, 'en', uniqueTitle('Messaging pumps'));

  // The approver is already in the application: the badge must move without a reload.
  const karim = await newUserPage(browser);
  await signIn(karim, USERS.managerTn, 'en', '/overview');
  const messagesLink = karim.locator('.sidebar .nav-link', { hasText: t('en', 'nav.items.conversations') });
  await expect(messagesLink).toBeVisible();
  const badge = messagesLink.locator('.nav-badge');
  // The baseline comes from the server: the badge may not have loaded yet, and earlier runs leave unread messages.
  const unreadNow = async () =>
    ((await (await karim.request.get('/api/v1/internal-messaging/unread')).json()) as { total: number }).total;
  const before = await unreadNow();
  const shown = async () => ((await badge.count()) ? Number((await badge.innerText()).trim().split(/\s/)[0]) : 0);

  await page.getByRole('button', { name: t('en', 'internalMessaging.approver.action') }).click();
  const dialog = page.getByRole('dialog', { name: t('en', 'internalMessaging.approver.title') });
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText(request.number);
  // Two department managers can decide: the sender chooses.
  await dialog.getByRole('radio', { name: /Karim Haddad/ }).check();
  const text = dialog.locator('textarea');
  await expect(text).toHaveValue(t('en', 'internalMessaging.approver.suggestion'));
  await text.fill('Could you please review this request when you have a moment? It blocks line 2.');
  await dialog.getByRole('button', { name: t('en', 'internalMessaging.composer.send') }).click();
  // A loaded workstation can take more than the default 10 s for this round trip; the button stays busy meanwhile.
  await expect(dialog.getByRole('status')).toContainText('Karim Haddad', { timeout: 30_000 });
  await dialog.getByRole('button', { name: t('en', 'internalMessaging.approver.openConversation') }).click();
  await expect(page).toHaveURL(/\/conversations\/\d+$/);
  await expect(page.locator('app-message-thread')).toContainText('It blocks line 2.', { timeout: 30_000 });

  // Live: the badge moves without a reload.
  await expect.poll(shown, { timeout: 15_000 }).toBe(before + 1);
  await messagesLink.click();
  await karim.locator('.conversation', { hasText: 'Sami Ben Ali' }).click();
  const thread = karim.locator('app-message-thread');
  await expect(thread).toContainText('It blocks line 2.', { timeout: 30_000 });
  // Read once displayed: nothing in this conversation stays unread (an earlier aborted run may have left older
  // unread messages in it, which are read too), and the badge follows the server.
  await expect(karim).toHaveURL(/\/conversations\/\d+$/);
  const openId = Number(/\/conversations\/(\d+)$/.exec(karim.url())![1]);
  const unreadHere = async () => {
    const list = (await (await karim.request.get('/api/v1/internal-messaging/conversations')).json()) as { id: number; unread: number }[];
    return list.find((c) => c.id === openId)?.unread ?? -1;
  };
  await expect.poll(unreadHere, { timeout: 15_000 }).toBe(0);
  const after = await unreadNow();
  expect(after).toBeLessThanOrEqual(before);
  await expect.poll(shown, { timeout: 15_000 }).toBe(after);

  // The reply reaches the requester's open conversation live.
  await thread.locator('textarea').fill('Looking at it now.');
  await thread.getByRole('button', { name: t('en', 'internalMessaging.composer.send') }).click();
  await expect(page.locator('app-message-thread')).toContainText('Looking at it now.', { timeout: 15_000 });

  // The approver opens the request from the card and finds the decision there.
  await thread.locator('app-request-card a', { hasText: request.number }).last().click();
  await expect(karim).toHaveURL(new RegExp(`/requests/${request.id}$`));
  await expect(karim.locator('app-approval-actions .decide')).toBeVisible();
  await karim.context().close();
});

test('a request discussion includes the approvers and is refused to people outside it', async ({ page, browser }) => {
  test.setTimeout(150_000);
  await signIn(page, USERS.requesterTn, 'en', '/requests');
  const request = await createAndSubmit(page, 'en', uniqueTitle('Discussion seals'));

  const panel = page.locator('app-request-discussion');
  await panel.getByRole('button', { name: t('en', 'internalMessaging.discussion.start') }).click();
  await expect(panel.locator('.chips')).toContainText('Karim Haddad', { timeout: 30_000 });
  await expect(panel.locator('.chips')).toContainText('Hichem Gharbi');
  await panel.locator('textarea').fill('Supplier asked for the drawing reference.');
  await panel.getByRole('button', { name: t('en', 'internalMessaging.composer.send') }).click();
  await expect(panel.locator('app-message-thread')).toContainText('Supplier asked for the drawing reference.', { timeout: 30_000 });
  const href = await panel.getByRole('link', { name: t('en', 'internalMessaging.discussion.openFull') }).getAttribute('href');
  const conversation = Number(/\/conversations\/(\d+)/.exec(href ?? '')![1]);

  const hichem = await newUserPage(browser);
  await signIn(hichem, USERS.manager2Tn, 'en', `/requests/${request.id}`);
  await expect(hichem.locator('app-request-discussion app-message-thread')).toContainText('Supplier asked for the drawing reference.');
  await hichem.context().close();

  // Another company: the conversation does not exist for them.
  const claire = await newUserPage(browser);
  await signIn(claire, USERS.requesterFr, 'en', '/requests');
  await claire.goto(`/conversations/${conversation}`);
  await expect(claire.locator('app-message-thread')).toContainText(t('en', 'internalMessaging.thread.notFound'));
  await claire.context().close();
});

test('supplier contacts have no internal messaging', async ({ browser }) => {
  const contact = await newUserPage(browser);
  await contact.goto('/sign-in');
  await contact.locator('#sign-in-email').fill('contact.skf@demo.local');
  await contact.locator('#sign-in-password').fill(process.env['DEMO_USER_PASSWORD']!);
  await contact.locator('form button[type="submit"]').click();
  await expect(contact).not.toHaveURL(/\/sign-in/);
  await expect(contact.locator('.sidebar .nav-link', { hasText: t('en', 'nav.items.conversations') })).toHaveCount(0);
  await contact.goto('/conversations');
  await expect(contact).not.toHaveURL(/\/conversations$/);
  await contact.context().close();
});
