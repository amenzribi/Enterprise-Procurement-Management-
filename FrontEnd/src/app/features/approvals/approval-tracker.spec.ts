import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { ApprovalTracker, questionsByTask, trackerSteps } from './approval-tracker';
import { ApprovalView } from './approval.models';

const NOW = Date.parse('2026-11-02T10:00:00Z');

function view(overrides: Partial<ApprovalView> = {}): ApprovalView {
  return {
    requestId: 7,
    userStatus: 'SUBMITTED',
    nextActors: [{ id: 3, displayName: 'Karim' }],
    waitingSince: '2026-11-02T08:00:00Z',
    rounds: [
      {
        id: 20,
        roundNo: 2,
        status: 'ACTIVE',
        startedAt: '2026-11-02T08:00:00Z',
        amounts: { requestAmount: '2500', requestCurrency: 'EUR', companyAmount: '8400.000', companyCurrency: 'TND', rate: '3.36', rateDate: '2026-11-01' },
        stages: [
          {
            stageNo: 1,
            approverType: 'DEPARTMENT_MANAGER',
            policy: 'ANY',
            status: 'IN_PROGRESS',
            tasks: [
              {
                id: 11,
                version: 1,
                assignee: { id: 3, displayName: 'Karim' },
                onBehalfOf: { id: 9, displayName: 'Mounir' },
                status: 'PENDING',
                createdAt: '2026-11-02T08:00:00Z',
                dueAt: '2026-11-01T08:00:00Z',
              },
            ],
          },
          { stageNo: 2, approverType: 'BUDGET_APPROVER', policy: 'ALL', status: 'WAITING', tasks: [] },
        ],
      },
      {
        id: 19,
        roundNo: 1,
        status: 'RETURNED',
        startedAt: '2026-10-30T08:00:00Z',
        stages: [
          {
            stageNo: 1,
            approverType: 'DEPARTMENT_MANAGER',
            policy: 'ANY',
            status: 'RETURNED',
            tasks: [
              { id: 5, version: 2, assignee: { id: 3, displayName: 'Karim' }, status: 'RETURNED', createdAt: '2026-10-30T08:00:00Z', decidedAt: '2026-10-31T08:00:00Z', comment: 'Add the datasheet' },
            ],
          },
        ],
      },
    ],
    events: [
      { id: 1, roundNo: 2, taskId: 11, kind: 'QUESTION', actor: { id: 3, displayName: 'Karim' }, text: 'Which pump?', occurredAt: '2026-11-02T09:00:00Z' },
      { id: 2, roundNo: 2, taskId: 11, kind: 'ANSWER', actor: { id: 1, displayName: 'Sami' }, text: 'P-204', occurredAt: '2026-11-02T09:30:00Z' },
      { id: 3, roundNo: 1, taskId: 5, kind: 'RETURNED', actor: { id: 3, displayName: 'Karim' }, text: 'Add the datasheet', channel: 'EMAIL', occurredAt: '2026-10-31T08:00:00Z' },
    ],
    ...overrides,
  };
}

describe('questionsByTask', () => {
  it('pairs each question with its answer and marks superseded ones', () => {
    const events = [
      { id: 1, roundNo: 1, taskId: 4, kind: 'QUESTION', text: 'Q1', occurredAt: '2026-11-02T09:00:00Z' },
      { id: 2, roundNo: 1, taskId: 4, kind: 'ANSWER', text: 'A1', actor: { id: 1, displayName: 'Sami' }, occurredAt: '2026-11-02T10:00:00Z' },
      { id: 3, roundNo: 1, taskId: 4, kind: 'QUESTION', text: 'Q2', occurredAt: '2026-11-02T11:00:00Z' },
      { id: 4, roundNo: 1, taskId: 4, kind: 'QUESTION_SUPERSEDED', occurredAt: '2026-11-02T12:00:00Z' },
    ];
    expect(questionsByTask(events).get(4)).toEqual([
      { question: 'Q1', askedAt: '2026-11-02T09:00:00Z', answer: 'A1', answeredBy: 'Sami', superseded: false },
      { question: 'Q2', askedAt: '2026-11-02T11:00:00Z', answer: null, answeredBy: null, superseded: true },
    ]);
  });
});

describe('trackerSteps', () => {
  it('marks steps before the current one as done and the current one as current', () => {
    const steps = trackerSteps('APPROVED');
    expect(steps.slice(0, 4).map((step) => step.state)).toEqual(['done', 'done', 'current', 'upcoming']);
  });

  it('shows a return or a rejection as a branch on the "Submitted" step', () => {
    expect(trackerSteps('RETURNED')[1]).toEqual({ status: 'SUBMITTED', state: 'returned', label: 'RETURNED' });
    expect(trackerSteps('REJECTED')[1]).toEqual({ status: 'SUBMITTED', state: 'rejected', label: 'REJECTED' });
    expect(trackerSteps('CANCELLED')[1]).toEqual({ status: 'SUBMITTED', state: 'rejected', label: 'CANCELLED' });
    expect(trackerSteps('REJECTED')[2].state).toBe('upcoming');
  });

  it('marks every step done once the request is closed', () => {
    expect(trackerSteps('CLOSED').every((step) => step.state === 'done')).toBe(true);
  });
});

describe('ApprovalTracker', () => {
  let fixture: ComponentFixture<ApprovalTracker>;
  const element = () => fixture.nativeElement as HTMLElement;

  async function render(approval: ApprovalView | null, status: 'SUBMITTED' | 'APPROVED' = 'SUBMITTED') {
    TestBed.configureTestingModule({ imports: [ApprovalTracker], providers: [provideTranslateService({ fallbackLang: 'en' })] });
    fixture = TestBed.createComponent(ApprovalTracker);
    fixture.componentRef.setInput('userStatus', status);
    fixture.componentRef.setInput('approval', approval);
    fixture.componentRef.setInput('now', NOW);
    await fixture.whenStable();
  }

  it('says who acts next, the current step for assistive technology, and the overdue task', async () => {
    await render(view());
    expect(element().querySelector('.next')?.textContent).toContain('tracker.waitingFor');
    expect(element().querySelector('[aria-current="step"]')?.textContent).toContain('requests.userStatus.SUBMITTED');
    expect(element().querySelector('.task .is-overdue')).not.toBeNull();
  });

  it('shows the latest round first, with approvers, delegation, questions and answers', async () => {
    await render(view());
    const current = element().querySelector('.round')!;
    expect(current.textContent).toContain('Karim');
    expect(current.textContent).toContain('tracker.onBehalfOf');
    expect(current.textContent).toContain('Which pump?');
    expect(current.textContent).toContain('P-204');
    expect(current.querySelectorAll('.stage').length).toBe(2);
  });

  it('keeps earlier rounds readable in a collapsed section', async () => {
    await render(view());
    const earlier = element().querySelector('details.earlier')!;
    expect(earlier.hasAttribute('open')).toBe(false);
    expect(earlier.textContent).toContain('Add the datasheet');
    // The decision was taken from the e-mail link (event channel EMAIL).
    expect(earlier.textContent).toContain('tracker.viaLink');
  });

  it('shows only the step line when no approval data is available', async () => {
    await render(null, 'APPROVED');
    expect(element().querySelectorAll('.step').length).toBe(10);
    expect(element().querySelector('.round')).toBeNull();
    expect(element().querySelector('.next')).toBeNull();
  });
});
