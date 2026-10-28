import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { ApprovalActions } from './approval-actions';
import { ApprovalAction, ApprovalView } from './approval.models';

function view(actions: ApprovalAction[], extra: Partial<ApprovalView> = {}): ApprovalView {
  return { requestId: 7, rounds: [], actions, myTask: actions.length ? { id: 11, version: 2, stageNo: 1 } : null, ...extra };
}

describe('ApprovalActions', () => {
  let fixture: ComponentFixture<ApprovalActions>;
  let http: HttpTestingController;
  const element = () => fixture.nativeElement as HTMLElement;
  const buttons = () => Array.from(element().querySelectorAll<HTMLButtonElement>('.choices button')).map((b) => b.textContent!.trim());
  const choose = async (action: ApprovalAction) => {
    Array.from(element().querySelectorAll<HTMLButtonElement>('.choices button'))
      .find((b) => b.textContent!.includes(`approvals.actions.${action}`))!
      .click();
    await fixture.whenStable();
  };
  const submit = async () => {
    element().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  };
  const type = async (text: string) => {
    const area = element().querySelector<HTMLTextAreaElement>('textarea')!;
    area.value = text;
    area.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  };

  async function render(approval: ApprovalView) {
    TestBed.configureTestingModule({
      imports: [ApprovalActions],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService({ fallbackLang: 'en' })],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ApprovalActions);
    fixture.componentRef.setInput('requestId', 7);
    fixture.componentRef.setInput('approval', approval);
    await fixture.whenStable();
  }

  afterEach(() => http.verify());

  it('offers exactly the actions the server allows, approve first', async () => {
    await render(view(['REJECT', 'APPROVE', 'QUESTION']));
    expect(buttons()).toEqual(['approvals.actions.APPROVE', 'approvals.actions.QUESTION', 'approvals.actions.REJECT']);
  });

  it('offers no answer form without the ANSWER action from the server', async () => {
    await render({
      requestId: 7,
      rounds: [],
      actions: [],
      openQuestions: [{ taskId: 11, question: 'Which pump?', askedAt: '2026-11-02T09:00:00Z' }],
    });
    expect(element().querySelector('.answer')).toBeNull();
  });

  it('shows nothing to a user without a task', async () => {
    await render({ requestId: 7, rounds: [], actions: [], myTask: null });
    expect(element().querySelector('.decide')).toBeNull();
  });

  it('requires a reason to reject and sends nothing without one', async () => {
    await render(view(['APPROVE', 'REJECT']));
    await choose('REJECT');
    await submit();

    http.expectNone(() => true);
    expect(element().querySelector('#decide-error')?.textContent).toContain('approvals.decide.reasonRequired');
  });

  it('approves with the task version and one idempotency key, kept for a retry of the same action', async () => {
    await render(view(['APPROVE']));
    const changed = vi.fn();
    fixture.componentInstance.changed.subscribe(changed);
    await choose('APPROVE');
    await type('OK for P-204');

    await submit();
    const first = http.expectOne('/api/v1/approvals/tasks/11/approve');
    expect(first.request.body).toEqual({ version: 2, comment: 'OK for P-204', reason: null, question: null });
    const key = first.request.headers.get('Idempotency-Key');
    expect(key).toBeTruthy();
    first.error(new ProgressEvent('error'), { status: 0 });
    await fixture.whenStable();

    await submit();
    const retry = http.expectOne('/api/v1/approvals/tasks/11/approve');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush(view([]));
    await fixture.whenStable();
    expect(changed).toHaveBeenCalled();
  });

  it('sends a return with its reason in the reason field', async () => {
    await render(view(['RETURN']));
    await choose('RETURN');
    await type('Add the datasheet');
    await submit();
    const request = http.expectOne('/api/v1/approvals/tasks/11/return');
    expect(request.request.body).toEqual({ version: 2, comment: null, reason: 'Add the datasheet', question: null });
    request.flush(view([]));
  });

  it('refreshes from the server when the task was decided elsewhere meanwhile', async () => {
    await render(view(['APPROVE']));
    const changed = vi.fn();
    fixture.componentInstance.changed.subscribe(changed);
    await choose('APPROVE');
    await submit();
    http.expectOne('/api/v1/approvals/tasks/11/approve').flush({ code: 'task-not-pending' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    expect(changed).toHaveBeenCalledWith(null);
    expect(element().querySelector('.choices')).not.toBeNull();
  });

  it('lists eligible delegates and requires one before delegating', async () => {
    await render(view(['DELEGATE']));
    await choose('DELEGATE');
    http.expectOne('/api/v1/approvals/tasks/11/delegates').flush([{ id: 4, displayName: 'Leila' }]);
    await fixture.whenStable();
    await submit();
    http.expectNone('/api/v1/approvals/tasks/11/delegate');
    expect(element().querySelector('#decide-error')?.textContent).toContain('approvals.decide.delegateRequired');

    const select = element().querySelector<HTMLSelectElement>('#delegate-to')!;
    select.selectedIndex = 1;
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    await submit();
    const request = http.expectOne('/api/v1/approvals/tasks/11/delegate');
    expect(request.request.body).toEqual({ version: 2, reason: null, toUserId: 4 });
    request.flush(view([]));
  });

  it('lets the requester answer an open question', async () => {
    await render({
      requestId: 7,
      rounds: [],
      actions: ['ANSWER'],
      openQuestions: [{ taskId: 11, question: 'Which pump?', askedAt: '2026-11-02T09:00:00Z', askedBy: { id: 3, displayName: 'Karim' } }],
    });
    expect(element().textContent).toContain('Which pump?');
    const area = element().querySelector<HTMLTextAreaElement>('#answer-text')!;
    area.value = 'P-204 on line 2';
    area.dispatchEvent(new Event('input'));
    element().querySelector<HTMLFormElement>('.answer form')!.dispatchEvent(new Event('submit'));
    const request = http.expectOne('/api/v1/requests/7/answers');
    expect(request.request.body).toEqual({ taskId: 11, answer: 'P-204 on line 2' });
    expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
    request.flush({ requestId: 7, rounds: [] });
  });
});
