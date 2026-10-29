import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { Icon, IconName } from '../../shared/ui/icon';
import { StatusBadge, StatusTone } from '../../shared/ui/status-badge';
import { elapsedText, untilText } from '../../shared/relative-time';
import { formatMoney, minorUnitsOf } from '../requests/money';
import { TRACKER_STEPS, UserStatus, trackerIndex } from '../requests/request-status';
import { ApprovalEvent, ApprovalRound, ApprovalStage, ApprovalTask, ApprovalView, StageStatus, TaskStatus } from './approval.models';

type StepState = 'done' | 'current' | 'upcoming' | 'returned' | 'rejected';

export interface TrackerStep {
  status: UserStatus;
  state: StepState;
  /** The status named on the step: the request's own status on a Returned / Rejected / Cancelled branch. */
  label: UserStatus;
}

/** The parcel-style step line for a user-facing status (S2-AC-11). Exported for tests. */
export function trackerSteps(status: UserStatus | null): TrackerStep[] {
  const current = trackerIndex(status);
  return TRACKER_STEPS.map((step, index) => {
    let state: StepState = index < current ? 'done' : index === current ? 'current' : 'upcoming';
    let label: UserStatus = step;
    if (index === current && status === 'RETURNED') {
      state = 'returned';
      label = status;
    } else if (index === current && (status === 'REJECTED' || status === 'CANCELLED')) {
      state = 'rejected';
      label = status;
    } else if (index === current && status === 'CLOSED') {
      state = 'done';
    }
    return { status: step, state, label };
  });
}

const STAGE_TONES: Record<StageStatus, StatusTone> = {
  WAITING: 'neutral',
  IN_PROGRESS: 'info',
  APPROVED: 'success',
  REJECTED: 'danger',
  RETURNED: 'warning',
  SKIPPED: 'neutral',
  CANCELLED: 'neutral',
};

/** Routing notes the server sets on tasks; each has a translated explanation. */
const ROUTING_NOTES = ['ABSENCE_DELEGATE', 'ABSENCE_UNRESOLVED', 'DELEGATED'] as const;

/**
 * Translation key for a task's routing note, or null for none. Unknown codes get a generic explanation rather than
 * the raw code (QA2-D-10). Exported for tests.
 */
export function routingNoteKey(note: string | null | undefined): string | null {
  if (!note) {
    return null;
  }
  return (ROUTING_NOTES as readonly string[]).includes(note) ? `tracker.routingNotes.${note}` : 'tracker.routingNotes.OTHER';
}

/** A question asked on a task and its answer, rebuilt from the decision history. */
export interface TaskQuestion {
  question: string;
  askedAt: string;
  answer: string | null;
  answeredBy: string | null;
  superseded: boolean;
}

/** Questions and answers of each task, from the QUESTION / ANSWER / QUESTION_SUPERSEDED events. Exported for tests. */
export function questionsByTask(events: ApprovalEvent[] | undefined): Map<number, TaskQuestion[]> {
  const result = new Map<number, TaskQuestion[]>();
  const sorted = [...(events ?? [])].sort((a, b) => a.occurredAt.localeCompare(b.occurredAt));
  for (const event of sorted) {
    if (event.taskId === null || event.taskId === undefined) {
      continue;
    }
    const list = result.get(event.taskId) ?? [];
    const open = [...list].reverse().find((item) => item.answer === null && !item.superseded);
    if (event.kind === 'QUESTION') {
      list.push({ question: event.text ?? '', askedAt: event.occurredAt, answer: null, answeredBy: null, superseded: false });
    } else if (event.kind === 'ANSWER' && open) {
      open.answer = event.text ?? '';
      open.answeredBy = event.actor?.displayName ?? null;
    } else if (event.kind === 'QUESTION_SUPERSEDED' && open) {
      open.superseded = true;
    }
    result.set(event.taskId, list);
  }
  return result;
}

const TASK_ICONS: Record<TaskStatus, IconName> = {
  PENDING: 'hourglass',
  APPROVED: 'circle-check',
  REJECTED: 'circle-x',
  RETURNED: 'undo',
  DELEGATED: 'forward',
  CANCELLED: 'x',
  SUPERSEDED: 'x',
};

/**
 * Where the request stands and who acts next: the step line (Draft → … → Closed), then every approval stage with its
 * approvers, decisions, comments and questions. Earlier approval rounds (before a return for revision) stay visible
 * in a collapsed section so the full history is readable (S2-AC-05, S2-AC-11).
 */
@Component({
  selector: 'app-approval-tracker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, Icon, NgTemplateOutlet, StatusBadge, TranslatePipe],
  template: `
    <section class="panel tracker" aria-labelledby="tracker-title">
      <h2 class="panel-title" id="tracker-title">{{ 'tracker.title' | translate }}</h2>

      <ol class="steps">
        @for (step of steps(); track step.status) {
          <li class="step" [class]="'is-' + step.state" [attr.aria-current]="isCurrent(step) ? 'step' : null">
            <span class="step-dot" aria-hidden="true">
              @switch (step.state) {
                @case ('done') { <app-icon name="check" [size]="12" [strokeWidth]="3" /> }
                @case ('returned') { <app-icon name="undo" [size]="12" [strokeWidth]="2.5" /> }
                @case ('rejected') { <app-icon name="x" [size]="12" [strokeWidth]="3" /> }
              }
            </span>
            <span class="step-label">{{ 'requests.userStatus.' + step.label | translate }}</span>
            <span class="visually-hidden">{{ 'tracker.stepState.' + step.state | translate }}</span>
          </li>
        }
      </ol>
      <!-- Medium widths show the dots only; this caption names the current step (aria-hidden: the list already says it). -->
      <p class="steps-caption" aria-hidden="true">
        {{ 'tracker.stepOf' | translate: { step: currentStepNumber(), total: steps().length, label: ('requests.userStatus.' + currentStepLabel() | translate) } }}
      </p>

      @if (nextActorNames(); as names) {
        <div class="next" role="status">
          <app-icon name="hourglass" [size]="18" />
          <div>
            <p class="next-title">
              {{ 'tracker.waitingFor' | translate: { names: names } }}
              @if (approval()?.waitingSince; as since) {
                <span class="next-since">{{ 'tracker.since' | translate: { elapsed: elapsed(since) } }}</span>
              }
            </p>
            @if (nextDue(); as due) {
              <p class="next-due" [class.is-overdue]="isPast(due)">
                {{ (isPast(due) ? 'tracker.overdue' : 'tracker.due') | translate: { when: until(due) } }}
                <time [attr.datetime]="due">({{ due | date: 'medium' : undefined : locale() }})</time>
              </p>
            }
          </div>
        </div>
      }

      @if (currentRound(); as round) {
        <div class="round">
          <h3 class="round-title">
            {{ 'tracker.approvalCircuit' | translate }}
            @if (rounds().length > 1) {
              <span class="round-no">{{ 'tracker.round' | translate: { round: round.roundNo } }}</span>
            }
          </h3>
          @if (round.amounts?.companyAmount !== null && round.amounts?.companyAmount !== undefined && round.amounts?.companyCurrency) {
            <p class="round-meta">
              {{ 'tracker.amountForApproval' | translate }}
              <bdi class="tabular strong">{{ money(round.amounts!.companyAmount!, round.amounts!.companyCurrency!) }}</bdi>
              @if (round.amounts!.rate && round.amounts!.rateDate && round.amounts!.requestCurrency !== round.amounts!.companyCurrency) {
                <span class="muted">· {{ 'tracker.rate' | translate: { rate: round.amounts!.rate, date: (round.amounts!.rateDate | date: 'mediumDate' : undefined : locale()) } }}</span>
              }
            </p>
          }
          <ol class="stages">
            @for (stage of round.stages; track stage.stageNo) {
              <ng-container [ngTemplateOutlet]="stageTpl" [ngTemplateOutletContext]="{ $implicit: stage }" />
            }
          </ol>
        </div>
      }

      @if (earlierRounds().length > 0) {
        <details class="earlier">
          <summary>{{ 'tracker.earlierRounds' | translate: { count: earlierRounds().length } }}</summary>
          @for (round of earlierRounds(); track round.roundNo) {
            <div class="round is-earlier">
              <h3 class="round-title">
                {{ 'tracker.round' | translate: { round: round.roundNo } }}
                @if (round.status !== 'ACTIVE') {
                  <app-status-badge [tone]="outcomeTone(round.status)">{{ 'tracker.outcome.' + round.status | translate }}</app-status-badge>
                }
              </h3>
              <ol class="stages">
                @for (stage of round.stages; track stage.stageNo) {
                  <ng-container [ngTemplateOutlet]="stageTpl" [ngTemplateOutletContext]="{ $implicit: stage }" />
                }
              </ol>
            </div>
          }
        </details>
      }

      <ng-template #stageTpl let-stage>
        <li class="stage" [class]="'is-' + stage.status.toLowerCase()">
          <div class="stage-head">
            <span class="stage-order tabular" aria-hidden="true">{{ stage.stageNo }}</span>
            <div class="stage-name">
              <!-- The approver type is translated; the stage name is free text from the rule, shown as a detail. -->
              <p class="strong">{{ 'tracker.approverType.' + stage.approverType | translate }}</p>
              <p class="muted small">
                {{ 'tracker.policy.' + stage.policy | translate }}
                @if (stage.name) {
                  · <bdi>{{ stage.name }}</bdi>
                }
              </p>
            </div>
            <app-status-badge [tone]="stageTone(stage)">{{ 'tracker.stageStatus.' + stage.status | translate }}</app-status-badge>
          </div>
          @if (stage.tasks.length > 0) {
            <ul class="tasks">
              @for (task of stage.tasks; track task.id) {
                <li class="task" [class]="'is-' + task.status.toLowerCase()">
                  <app-icon [name]="taskIcon(task)" [size]="16" class="task-icon" />
                  <div class="task-main">
                    <p>
                      <span class="strong">{{ task.assignee.displayName }}</span>
                      @if (task.onBehalfOf) {
                        <span class="muted"> · {{ 'tracker.onBehalfOf' | translate: { name: task.onBehalfOf.displayName } }}</span>
                      }
                      — {{ 'tracker.taskStatus.' + task.status | translate }}
                      @if (delegatedTo(task); as name) {
                        {{ 'tracker.delegatedTo' | translate: { name } }}
                      }
                    </p>
                    @if (routingNoteKey(task.routingNote); as noteKey) {
                      <p class="muted small routing-note" [class.is-overdue]="task.routingNote === 'ABSENCE_UNRESOLVED'">
                        {{ noteKey | translate }}
                      </p>
                    }
                    <p class="muted small">
                      @if (task.decidedAt) {
                        <time [attr.datetime]="task.decidedAt">{{ task.decidedAt | date: 'medium' : undefined : locale() }}</time>
                        @if (task.decidedBy && task.decidedBy.id !== task.assignee.id) {
                          · {{ 'tracker.by' | translate: { name: task.decidedBy.displayName } }}
                        }
                        @if (viaLink(task)) {
                          · {{ 'tracker.viaLink' | translate }}
                        }
                      } @else if (task.status === 'PENDING') {
                        {{ 'tracker.since' | translate: { elapsed: elapsed(task.createdAt) } }}
                        @if (task.dueAt) {
                          · <span [class.is-overdue]="task.overdue || isPast(task.dueAt)">
                            {{ (task.overdue || isPast(task.dueAt) ? 'tracker.overdue' : 'tracker.due') | translate: { when: until(task.dueAt) } }}
                          </span>
                        }
                      }
                    </p>
                    @if (task.comment) {
                      <blockquote class="comment">{{ task.comment }}</blockquote>
                    }
                    @for (question of questions().get(task.id) ?? []; track $index) {
                      <div class="question">
                        <p class="small">
                          <app-icon name="question" [size]="14" />
                          <span class="strong">{{ 'tracker.question' | translate }}</span>
                          <span class="muted"> · <time [attr.datetime]="question.askedAt">{{ question.askedAt | date: 'short' : undefined : locale() }}</time></span>
                        </p>
                        <p class="prose">{{ question.question }}</p>
                        @if (question.answer !== null) {
                          <p class="small">
                            <app-icon name="reply" [size]="14" />
                            <span class="strong">{{ 'tracker.answer' | translate }}</span>
                            @if (question.answeredBy) {
                              <span class="muted"> · {{ question.answeredBy }}</span>
                            }
                          </p>
                          <p class="prose">{{ question.answer }}</p>
                        } @else if (question.superseded) {
                          <p class="muted small">{{ 'tracker.questionSuperseded' | translate }}</p>
                        } @else {
                          <p class="muted small">{{ 'tracker.awaitingAnswer' | translate }}</p>
                        }
                      </div>
                    }
                  </div>
                </li>
              }
            </ul>
          }
        </li>
      </ng-template>
    </section>
  `,
  styleUrl: './approval-tracker.scss',
})
export class ApprovalTracker {
  readonly userStatus = input<UserStatus | null>(null);
  readonly approval = input<ApprovalView | null>(null);
  readonly locale = input('en-GB');
  /** Current time, ticking in the parent so relative times stay fresh. */
  readonly now = input(Date.now());

  protected readonly steps = computed(() => trackerSteps(this.userStatus()));

  protected readonly currentStepNumber = computed(() => {
    const index = this.steps().findIndex((step) => step.state !== 'done' && step.state !== 'upcoming');
    return (index === -1 ? this.steps().length - 1 : index) + 1;
  });
  protected readonly currentStepLabel = computed(() => this.steps()[this.currentStepNumber() - 1]?.label ?? 'DRAFT');

  protected readonly rounds = computed<ApprovalRound[]>(() =>
    [...(this.approval()?.rounds ?? [])].sort((a, b) => b.roundNo - a.roundNo),
  );
  protected readonly currentRound = computed(() => this.rounds()[0] ?? null);
  protected readonly earlierRounds = computed(() => this.rounds().slice(1));

  protected readonly questions = computed(() => questionsByTask(this.approval()?.events));

  /** The earliest deadline of the pending tasks of the current round. */
  protected readonly nextDue = computed(() => {
    const dues = (this.currentRound()?.stages ?? [])
      .flatMap((stage) => stage.tasks)
      .filter((task) => task.status === 'PENDING' && task.dueAt)
      .map((task) => task.dueAt!)
      .sort();
    return dues[0] ?? null;
  });

  protected readonly nextActorNames = computed(() => {
    const actors = this.approval()?.nextActors ?? [];
    const status = this.userStatus();
    if (actors.length === 0 || (status !== 'SUBMITTED' && status !== 'RETURNED')) {
      return null;
    }
    return actors.map((actor) => actor.displayName).join(', ');
  });

  protected isCurrent(step: TrackerStep): boolean {
    return step.state === 'current' || step.state === 'returned' || step.state === 'rejected';
  }

  protected stageTone(stage: ApprovalStage): StatusTone {
    return STAGE_TONES[stage.status] ?? 'neutral';
  }

  protected outcomeTone(outcome: string): StatusTone {
    return outcome === 'APPROVED' ? 'success' : outcome === 'REJECTED' ? 'danger' : outcome === 'RETURNED' ? 'warning' : 'neutral';
  }

  protected routingNoteKey(note: string | null | undefined): string | null {
    return routingNoteKey(note);
  }

  /** Who a delegated task went to, from the DELEGATED event of that task. */
  protected delegatedTo(task: ApprovalTask): string | null {
    if (task.status !== 'DELEGATED') {
      return null;
    }
    const event = (this.approval()?.events ?? []).find((item) => item.taskId === task.id && item.kind === 'DELEGATED');
    return event?.target?.displayName ?? null;
  }

  /** True when the decision on this task was taken through the one-tap e-mail link. */
  protected viaLink(task: ApprovalTask): boolean {
    return (this.approval()?.events ?? []).some((event) => event.taskId === task.id && event.channel === 'EMAIL');
  }

  protected taskIcon(task: ApprovalTask): IconName {
    return TASK_ICONS[task.status] ?? 'clock';
  }

  protected elapsed(iso: string): string {
    return elapsedText(iso, this.now(), this.locale());
  }

  protected until(iso: string): string {
    return untilText(iso, this.now(), this.locale());
  }

  protected isPast(iso: string): boolean {
    return Date.parse(iso) < this.now();
  }

  protected money(amount: string | number, currency: string): string {
    return formatMoney(amount, currency, minorUnitsOf(currency), this.locale());
  }
}
