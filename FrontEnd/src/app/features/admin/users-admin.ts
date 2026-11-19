import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { hasPermission } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { PageHeader } from '../../shared/ui/page-header';
import { Skeleton } from '../../shared/ui/skeleton';
import { StatusBadge } from '../../shared/ui/status-badge';

interface UserListItem {
  id: number;
  email: string;
  displayName: string;
  companyId: number;
  companyCode: string;
  active: boolean;
}

interface GrantView {
  id: number;
  role: string;
  companyId?: number | null;
  departmentId?: number | null;
}

interface UserDetail extends UserListItem {
  siteId?: number | null;
  departmentId?: number | null;
  preferredLanguage: string;
  demo: boolean;
  version: number;
  roles: GrantView[];
  pendingApprovals: number;
  assignedBuying: number;
}

export interface EmailChangeView {
  id: number;
  status: 'PENDING_APPROVAL' | 'PENDING_VERIFICATION';
  oldEmail: string;
  newEmail: string;
  reason: string;
  requestedBy: number;
  requestedAt: string;
  approvedBy?: number | null;
  tokenExpiresAt?: string | null;
}

interface CompanyRow {
  id: number;
  code: string;
  legalName: string;
}

const API = '/api/v1/admin/accounts';
const ASSIGNABLE = [
  'REQUESTER',
  'ENTER_ON_BEHALF',
  'DEPARTMENT_MANAGER',
  'BUDGET_APPROVER',
  'MANAGEMENT_APPROVER',
  'BUYER',
  'PURCHASING_MANAGER',
  'RECEIVER',
  'GROUP_MANAGEMENT',
  'ADMIN',
  'GLOBAL_ADMIN',
] as const;

@Component({
  selector: 'app-users-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Button,
    EmptyState,
    ErrorState,
    PageHeader,
    ReactiveFormsModule,
    Skeleton,
    StatusBadge,
    TranslatePipe,
  ],
  template: `
    <app-page-header [heading]="'usersAdmin.title' | translate" [description]="'usersAdmin.description' | translate" />

    @if (state() === 'loading') {
      <app-skeleton height="8rem" />
    } @else if (state() === 'error') {
      <app-error-state
        [heading]="'usersAdmin.loadError' | translate"
        [retryLabel]="'common.retry' | translate"
        (retry)="load()"
      />
    } @else {
      <section class="panel" aria-labelledby="users-list-heading">
        <div class="toolbar">
          <h2 class="panel-title" id="users-list-heading">{{ 'usersAdmin.list' | translate }}</h2>
          <form class="search" [formGroup]="search" (ngSubmit)="load()">
            <label class="sr-only" for="user-q">{{ 'usersAdmin.search' | translate }}</label>
            <input id="user-q" type="search" formControlName="q" [attr.placeholder]="'usersAdmin.search' | translate" />
            <button appButton type="submit" variant="secondary">{{ 'common.search' | translate }}</button>
          </form>
        </div>

        <section class="role-help" aria-labelledby="role-help-heading">
          <h3 id="role-help-heading">{{ 'usersAdmin.roleHelpTitle' | translate }}</h3>
          <ul>
            @for (role of assignable; track role) {
              <li>
                <strong>{{ role }}</strong> — {{ 'usersAdmin.roleHelp.' + role | translate }}
              </li>
            }
          </ul>
          <p class="meta">{{ 'usersAdmin.roleCombinations' | translate }}</p>
        </section>

        <form class="create" [formGroup]="create" (ngSubmit)="createUser()">
          <h3>{{ 'usersAdmin.create' | translate }}</h3>
          <div class="grid">
            <label
              >{{ 'usersAdmin.email' | translate }}
              <input type="email" formControlName="email" autocomplete="off" />
            </label>
            <label
              >{{ 'usersAdmin.displayName' | translate }}
              <input type="text" formControlName="displayName" />
            </label>
            <label
              >{{ 'usersAdmin.company' | translate }}
              <select formControlName="companyId">
                @for (c of companies(); track c.id) {
                  <option [value]="c.id">{{ c.code }} — {{ c.legalName }}</option>
                }
              </select>
            </label>
            <label
              >{{ 'usersAdmin.language' | translate }}
              <select formControlName="preferredLanguage">
                <option value="en">EN</option>
                <option value="fr">FR</option>
                <option value="ar">AR</option>
              </select>
            </label>
          </div>
          <button appButton type="submit" [disabled]="create.invalid || busy()">
            {{ 'usersAdmin.createSubmit' | translate }}
          </button>
        </form>

        @if (users().length === 0) {
          <app-empty-state
            icon="users"
            [heading]="'usersAdmin.empty.title' | translate"
            [message]="'usersAdmin.empty.message' | translate"
          />
        } @else {
          <div class="table-wrap" tabindex="0" role="region" [attr.aria-label]="'usersAdmin.list' | translate">
            <table>
              <thead>
                <tr>
                  <th scope="col">{{ 'usersAdmin.displayName' | translate }}</th>
                  <th scope="col">{{ 'usersAdmin.email' | translate }}</th>
                  <th scope="col">{{ 'usersAdmin.company' | translate }}</th>
                  <th scope="col">{{ 'usersAdmin.status' | translate }}</th>
                </tr>
              </thead>
              <tbody>
                @for (user of users(); track user.id) {
                  <tr>
                    <td>
                      <button type="button" class="linkish" (click)="select(user.id)">{{ user.displayName }}</button>
                    </td>
                    <td>{{ user.email }}</td>
                    <td>{{ user.companyCode }}</td>
                    <td>
                      <app-status-badge [tone]="user.active ? 'success' : 'neutral'">
                        {{ (user.active ? 'usersAdmin.active' : 'usersAdmin.inactive') | translate }}
                      </app-status-badge>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>

      @if (selected(); as detail) {
        <section class="panel detail" aria-labelledby="user-detail-heading">
          <h2 class="panel-title" id="user-detail-heading">{{ detail.displayName }}</h2>
          <p class="meta">{{ detail.email }} · {{ detail.companyCode }}</p>
          @if (detail.pendingApprovals || detail.assignedBuying) {
            <p class="warn" role="status">
              {{
                'usersAdmin.openWork'
                  | translate
                    : { approvals: detail.pendingApprovals, buying: detail.assignedBuying }
              }}
            </p>
          }
          <div class="actions">
            <button appButton type="button" variant="secondary" [disabled]="busy()" (click)="toggleActive(detail)">
              {{ (detail.active ? 'usersAdmin.deactivate' : 'usersAdmin.activate') | translate }}
            </button>
            <button appButton type="button" variant="secondary" [disabled]="busy()" (click)="resetPassword(detail.id)">
              {{ 'usersAdmin.resetPassword' | translate }}
            </button>
            <button appButton type="button" variant="secondary" [disabled]="busy()" (click)="revokeSessions(detail.id)">
              {{ 'usersAdmin.revokeSessions' | translate }}
            </button>
          </div>

          @if (canCorrectEmail()) {
            <section class="email-change" aria-labelledby="email-change-heading">
              <h3 id="email-change-heading">{{ 'usersAdmin.emailChange.title' | translate }}</h3>
              @if (emailChange(); as change) {
                <p role="status">
                  {{ 'usersAdmin.emailChange.status.' + change.status | translate: { newEmail: change.newEmail } }}
                </p>
                <p class="meta">{{ 'usersAdmin.emailChange.reasonShown' | translate: { reason: change.reason } }}</p>
                <div class="actions">
                  @if (change.status === 'PENDING_APPROVAL') {
                    <button appButton type="button" [disabled]="busy()" (click)="approveEmailChange(detail.id)">
                      {{ 'usersAdmin.emailChange.approve' | translate }}
                    </button>
                  }
                  <button appButton type="button" variant="secondary" [disabled]="busy()" (click)="cancelEmailChange(detail.id)">
                    {{ 'usersAdmin.emailChange.cancel' | translate }}
                  </button>
                </div>
              } @else {
                <p class="meta">{{ 'usersAdmin.emailChange.help' | translate }}</p>
                <form class="grant" [formGroup]="emailForm" (ngSubmit)="requestEmailChange(detail.id)">
                  <label
                    >{{ 'usersAdmin.emailChange.newEmail' | translate }}
                    <input type="email" formControlName="newEmail" autocomplete="off" />
                  </label>
                  <label
                    >{{ 'usersAdmin.emailChange.reason' | translate }}
                    <input type="text" formControlName="reason" maxlength="500" />
                  </label>
                  <button appButton type="submit" [disabled]="emailForm.invalid || busy()">
                    {{ 'usersAdmin.emailChange.submit' | translate }}
                  </button>
                </form>
              }
            </section>
          }

          <h3>{{ 'usersAdmin.roles' | translate }}</h3>
          <ul class="roles">
            @for (role of detail.roles; track role.id) {
              <li>
                <span>{{ role.role }}@if (role.companyId) { ({{ role.companyId }}) }</span>
                <button type="button" class="linkish" [disabled]="busy()" (click)="removeRole(detail.id, role.id)">
                  {{ 'usersAdmin.removeRole' | translate }}
                </button>
              </li>
            }
          </ul>
          <form class="grant" [formGroup]="grant" (ngSubmit)="addRole(detail.id)">
            <label
              >{{ 'usersAdmin.role' | translate }}
              <select formControlName="role">
                @for (role of assignable; track role) {
                  <option [value]="role">{{ role }}</option>
                }
              </select>
            </label>
            <label
              >{{ 'usersAdmin.grantCompany' | translate }}
              <select formControlName="companyId">
                <option value="">—</option>
                @for (c of companies(); track c.id) {
                  <option [value]="c.id">{{ c.code }}</option>
                }
              </select>
            </label>
            <button appButton type="submit" [disabled]="grant.invalid || busy()">
              {{ 'usersAdmin.addRole' | translate }}
            </button>
          </form>
        </section>
      }
    }
  `,
  styles: `
    .panel {
      margin-block: 1.25rem;
      display: grid;
      gap: 1rem;
    }
    .toolbar {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      justify-content: space-between;
      align-items: end;
    }
    .panel-title {
      margin: 0;
      font-size: 1.1rem;
    }
    .search,
    .create,
    .grant,
    .temp,
    .grid {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      align-items: end;
    }
    .role-help {
      padding: 0.75rem;
      border: 1px solid var(--color-border, #d7dde5);
      border-radius: 0.5rem;
      font-size: 0.9rem;
    }
    .email-change {
      display: grid;
      gap: 0.5rem;
      padding: 0.75rem;
      border: 1px solid var(--color-border, #d7dde5);
      border-radius: 0.5rem;
    }
    .email-change h3 {
      margin: 0;
    }
    .role-help ul {
      margin: 0.5rem 0;
      padding-inline-start: 1.1rem;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
      width: 100%;
    }
    label {
      display: grid;
      gap: 0.25rem;
      font-size: 0.9rem;
    }
    label {
      min-inline-size: 0;
      max-inline-size: 100%;
    }

    select {
      inline-size: 100%;
    }

    input,
    select {
      min-block-size: 2.4rem;
      padding-inline: 0.6rem;
    }
    .table-wrap {
      overflow: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    th,
    td {
      text-align: start;
      padding: 0.55rem 0.4rem;
      border-block-end: 1px solid var(--color-border, #d7dde5);
    }
    .linkish {
      background: none;
      border: 0;
      color: var(--color-accent, #0b5fff);
      cursor: pointer;
      text-decoration: underline;
      padding: 0;
      font: inherit;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    .roles {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: 0.4rem;
    }
    .roles li {
      display: flex;
      justify-content: space-between;
      gap: 0.75rem;
    }
    .warn {
      color: var(--color-warning-fg, #8a5a00);
    }
    .meta {
      margin: 0;
      color: var(--color-muted, #52606d);
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
    }
  `,
})
export class UsersAdminPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly notifications = inject(NotificationService);
  private readonly auth = inject(AuthService);

  /**
   * The server reserves administrator-chosen passwords for global administrators (audit S-4); company
   * administrators send a reset link. No permission carries this distinction, hence the role.
   */

  readonly assignable = ASSIGNABLE;
  readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  readonly busy = signal(false);
  readonly users = signal<UserListItem[]>([]);
  readonly companies = signal<CompanyRow[]>([]);
  readonly selected = signal<UserDetail | null>(null);

  readonly search = new FormGroup({
    q: new FormControl('', { nonNullable: true }),
  });

  readonly create = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    displayName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    companyId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    preferredLanguage: new FormControl('en', { nonNullable: true }),
  });

  readonly grant = new FormGroup({
    role: new FormControl('REQUESTER', { nonNullable: true, validators: [Validators.required] }),
    companyId: new FormControl<number | null>(null),
  });

  /** E-mail correction (Sprint 2, ADR-016 step 5): global administrators only; the server decides every rule. */
  readonly canCorrectEmail = computed(() => hasPermission(this.auth.me(), 'USER_ADMIN_GLOBAL'));
  readonly emailChange = signal<EmailChangeView | null>(null);
  readonly emailForm = new FormGroup({
    newEmail: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    reason: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(5), Validators.maxLength(500)],
    }),
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.state.set('loading');
    this.http.get<CompanyRow[]>('/api/v1/admin/organization/companies').subscribe({
      next: (companies) => {
        this.companies.set(companies);
        if (companies.length && this.create.controls.companyId.value == null) {
          this.create.controls.companyId.setValue(companies[0].id);
        }
      },
      error: () => undefined,
    });
    const q = this.search.controls.q.value.trim();
    this.http
      .get<UserListItem[]>(API, { params: q ? { q } : {} })
      .subscribe({
        next: (rows) => {
          this.users.set(rows);
          this.state.set('ready');
        },
        error: (err) => {
          this.state.set('error');
          this.notifications.error(`errors.${toApiProblem(err).code}`);
        },
      });
  }

  select(id: number): void {
    this.http.get<UserDetail>(`${API}/${id}`).subscribe({
      next: (detail) => {
        this.selected.set(detail);
        this.loadEmailChange(id);
      },
      error: (err) => this.notifications.error(`errors.${toApiProblem(err).code}`),
    });
  }

  loadEmailChange(id: number): void {
    this.emailChange.set(null);
    if (!this.canCorrectEmail()) {
      return;
    }
    // 204 (no open change) arrives as a null body.
    this.http.get<EmailChangeView | null>(`${API}/${id}/email-change`).subscribe({
      next: (change) => this.emailChange.set(change ?? null),
      error: () => this.emailChange.set(null),
    });
  }

  requestEmailChange(id: number): void {
    if (this.emailForm.invalid) {
      return;
    }
    const value = this.emailForm.getRawValue();
    this.busy.set(true);
    this.http
      .post<EmailChangeView>(`${API}/${id}/email-change`, { newEmail: value.newEmail.trim(), reason: value.reason.trim() })
      .subscribe({
        next: (change) => {
          this.busy.set(false);
          this.emailChange.set(change);
          this.emailForm.reset();
          this.notifications.success(
            change.status === 'PENDING_APPROVAL' ? 'usersAdmin.emailChange.requestedNeedsApproval' : 'usersAdmin.emailChange.requested',
          );
        },
        error: (err) => {
          this.busy.set(false);
          this.notifications.error(`errors.${toApiProblem(err).code}`);
        },
      });
  }

  approveEmailChange(id: number): void {
    this.busy.set(true);
    this.http.post<EmailChangeView>(`${API}/${id}/email-change/approve`, {}).subscribe({
      next: (change) => {
        this.busy.set(false);
        this.emailChange.set(change);
        this.notifications.success('usersAdmin.emailChange.approved');
      },
      error: (err) => {
        this.busy.set(false);
        this.notifications.error(`errors.${toApiProblem(err).code}`);
      },
    });
  }

  cancelEmailChange(id: number): void {
    this.busy.set(true);
    this.http.post(`${API}/${id}/email-change/cancel`, {}).subscribe({
      next: () => {
        this.busy.set(false);
        this.emailChange.set(null);
        this.notifications.success('usersAdmin.emailChange.cancelled');
      },
      error: (err) => {
        this.busy.set(false);
        this.notifications.error(`errors.${toApiProblem(err).code}`);
      },
    });
  }

  createUser(): void {
    if (this.create.invalid) {
      return;
    }
    const value = this.create.getRawValue();
    this.busy.set(true);
    this.http
      .post<UserDetail>(API, {
        email: value.email,
        displayName: value.displayName,
        companyId: Number(value.companyId),
        preferredLanguage: value.preferredLanguage,
        active: true,
        roles: [],
      })
      .subscribe({
        next: (detail) => {
          this.busy.set(false);
          this.notifications.success('usersAdmin.createdPendingActivation');
          this.create.patchValue({ email: '', displayName: '' });
          this.load();
          this.selected.set(detail);
        },
        error: (err) => {
          this.busy.set(false);
          this.notifications.error(`errors.${toApiProblem(err).code}`);
        },
      });
  }

  toggleActive(detail: UserDetail): void {
    this.busy.set(true);
    this.http.post<{ userId: number; active: boolean; pendingApprovals: number; assignedBuying: number }>(
      `${API}/${detail.id}/active`,
      { active: !detail.active },
    ).subscribe({
      next: (result) => {
        this.busy.set(false);
        if (!result.active && (result.pendingApprovals || result.assignedBuying)) {
          this.notifications.info('usersAdmin.deactivatedWithOpenWork');
        } else {
          this.notifications.success(result.active ? 'usersAdmin.activated' : 'usersAdmin.deactivated');
        }
        this.load();
        this.select(detail.id);
      },
      error: (err) => {
        this.busy.set(false);
        this.notifications.error(`errors.${toApiProblem(err).code}`);
      },
    });
  }

  resetPassword(id: number): void {
    this.busy.set(true);
    this.http.post(`${API}/${id}/password-reset`, {}).subscribe({
      next: () => {
        this.busy.set(false);
        this.notifications.success('usersAdmin.resetIssued');
      },
      error: (err) => {
        this.busy.set(false);
        this.notifications.error(`errors.${toApiProblem(err).code}`);
      },
    });
  }


  revokeSessions(id: number): void {
    this.busy.set(true);
    this.http.post(`${API}/${id}/revoke-sessions`, {}).subscribe({
      next: () => {
        this.busy.set(false);
        this.notifications.success('usersAdmin.sessionsRevoked');
      },
      error: (err) => {
        this.busy.set(false);
        this.notifications.error(`errors.${toApiProblem(err).code}`);
      },
    });
  }

  addRole(userId: number): void {
    const value = this.grant.getRawValue();
    this.busy.set(true);
    this.http
      .post<UserDetail>(`${API}/${userId}/roles`, {
        role: value.role,
        companyId:
          value.companyId === null || value.companyId === undefined || (value.companyId as unknown) === ''
            ? null
            : Number(value.companyId),
        departmentId: null,
      })
      .subscribe({
        next: (detail) => {
          this.busy.set(false);
          this.selected.set(detail);
          this.notifications.success('usersAdmin.roleAdded');
        },
        error: (err) => {
          this.busy.set(false);
          this.notifications.error(`errors.${toApiProblem(err).code}`);
        },
      });
  }

  removeRole(userId: number, grantId: number): void {
    this.busy.set(true);
    this.http.post<UserDetail>(`${API}/${userId}/roles/${grantId}/remove`, {}).subscribe({
      next: (detail) => {
        this.busy.set(false);
        this.selected.set(detail);
        this.notifications.success('usersAdmin.roleRemoved');
      },
      error: (err) => {
        this.busy.set(false);
        this.notifications.error(`errors.${toApiProblem(err).code}`);
      },
    });
  }
}
