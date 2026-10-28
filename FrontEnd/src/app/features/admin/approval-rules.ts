import { Dialog } from '@angular/cdk/dialog';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { LanguageService } from '../../core/i18n/language.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { openConfirm } from '../../shared/ui/confirm-dialog';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { Icon } from '../../shared/ui/icon';
import { PageHeader } from '../../shared/ui/page-header';
import { Skeleton } from '../../shared/ui/skeleton';
import { StatusBadge } from '../../shared/ui/status-badge';
import { Reference } from '../../core/auth/auth.models';
import { AdminCompany, AdminUser, ApprovalRule, ApprovalRuleInput, ApproverType, StagePolicy } from '../approvals/approval.models';
import { ApprovalsApi } from '../approvals/approvals.api';
import { normaliseDecimal } from '../requests/money';
import { NamedOption, PURCHASE_TYPES, optionLabel } from '../requests/request.models';
import { RequestsApi } from '../requests/requests.api';

type PageState = 'loading' | 'ready' | 'error';

const APPROVER_TYPES: ApproverType[] = ['DEPARTMENT_MANAGER', 'BUDGET_APPROVER', 'MANAGEMENT_APPROVER', 'NAMED_USER'];
const POLICIES: StagePolicy[] = ['ANY', 'ALL'];
const RULE_ERRORS = new Set(['ambiguous-approval-rule', 'band', 'invalid-value', 'required', 'not-in-company', 'save-failed']);

type StageForm = FormGroup<{
  name: FormControl<string>;
  approverType: FormControl<ApproverType>;
  approverUserId: FormControl<number | null>;
  minAmount: FormControl<string>;
  policy: FormControl<StagePolicy>;
  slaHours: FormControl<number>;
  escalationRole: FormControl<string | null>;
}>;

function stageForm(stage?: Partial<{ name: string | null; approverType: ApproverType; approverUserId: number | null; minAmount: string | number | null; policy: StagePolicy; slaHours: number; escalationRole: string | null }>): StageForm {
  return new FormGroup({
    name: new FormControl(stage?.name ?? '', { nonNullable: true }),
    approverType: new FormControl<ApproverType>(stage?.approverType ?? 'DEPARTMENT_MANAGER', { nonNullable: true }),
    approverUserId: new FormControl<number | null>(stage?.approverUserId ?? null),
    minAmount: new FormControl(stage?.minAmount !== null && stage?.minAmount !== undefined ? String(stage.minAmount) : '', { nonNullable: true }),
    policy: new FormControl<StagePolicy>(stage?.policy ?? 'ANY', { nonNullable: true }),
    slaHours: new FormControl(stage?.slaHours ?? 16, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(720)] }),
    escalationRole: new FormControl<string | null>(stage?.escalationRole ?? null),
  });
}

/**
 * Approval rules per company (S2-AC-02, UC-28). Rules are versioned: saving creates a new draft version and the
 * active version stays in force until the new one is activated; overlapping rules are refused at activation
 * (S2DR-05). Amount bands are in company currency, lower bound included and upper bound excluded.
 */
@Component({
  selector: 'app-approval-rules',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, DatePipe, EmptyState, ErrorState, Icon, PageHeader, ReactiveFormsModule, Skeleton, StatusBadge, TranslatePipe],
  templateUrl: './approval-rules.html',
  styleUrl: './admin.scss',
})
export class ApprovalRulesPage {
  private readonly api = inject(ApprovalsApi);
  private readonly requestsApi = inject(RequestsApi);
  private readonly dialog = inject(Dialog);
  private readonly notifications = inject(NotificationService);
  protected readonly language = inject(LanguageService);

  protected readonly approverTypes = APPROVER_TYPES;
  protected readonly policies = POLICIES;
  protected readonly purchaseTypes = PURCHASE_TYPES;

  protected readonly state = signal<PageState>('loading');
  protected readonly rules = signal<ApprovalRule[]>([]);
  /** Categories come from the request form options; there is no separate admin options endpoint. */
  protected readonly categories = signal<NamedOption[]>([]);
  /** Companies from `GET /admin/companies`. */
  protected readonly adminCompanies = signal<AdminCompany[]>([]);
  /** Users of the company being edited, for the named-approver picker (loaded when the company changes). */
  protected readonly companyUsers = signal<AdminUser[]>([]);
  /** Text typed in each stage's approver search box, by stage index. */
  protected readonly userFilters = signal<Record<number, string>>({});
  protected readonly versions = signal<Record<number, ApprovalRule[]>>({});
  /** The rule being edited (null = new rule); undefined = editor closed. */
  protected readonly editing = signal<ApprovalRule | null | undefined>(undefined);
  protected readonly saving = signal(false);
  protected readonly submitted = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Rules grouped by company, newest version first within each rule. */
  protected readonly companies = computed(() => {
    const names = new Map<number, string>();
    for (const rule of this.rules()) {
      names.set(rule.company.id, rule.company.name);
    }
    return [...names.entries()].map(([id, name]) => ({
      id,
      name,
      rules: this.rules()
        .filter((rule) => rule.company.id === id)
        .sort((a, b) => a.ruleKey - b.ruleKey || b.versionNo - a.versionNo),
    }));
  });

  /** Companies an administrator can write rules for (`GET /admin/companies`), plus any company already in a rule. */
  protected readonly companyOptions = computed(() => {
    const all = new Map<number, Reference>();
    for (const company of this.adminCompanies()) {
      all.set(company.id, company);
    }
    for (const rule of this.rules()) {
      all.set(rule.company.id, rule.company);
    }
    return [...all.values()].sort((a, b) => a.name.localeCompare(b.name));
  });

  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(200)] }),
    companyId: new FormControl<number | null>(null, Validators.required),
    categoryId: new FormControl<number | null>(null),
    purchaseType: new FormControl<string | null>(null),
    minAmount: new FormControl('0', { nonNullable: true, validators: [Validators.required] }),
    maxAmount: new FormControl('', { nonNullable: true }),
    stages: new FormArray<StageForm>([stageForm()]),
  });

  constructor() {
    this.load();
    this.requestsApi.formOptions().subscribe({ next: (options) => this.categories.set(options.categories), error: () => undefined });
    this.api.adminCompanies().subscribe({ next: (companies) => this.adminCompanies.set(companies), error: () => undefined });
    this.form.controls.companyId.valueChanges.subscribe((companyId) => this.loadUsers(companyId));
  }

  load(): void {
    this.state.set('loading');
    this.api.rules().subscribe({
      next: (rules) => {
        this.rules.set(rules);
        this.state.set('ready');
      },
      error: () => this.state.set('error'),
    });
  }

  protected label(option: Parameters<typeof optionLabel>[0]): string {
    return optionLabel(option, this.language.current());
  }

  /** Users of the company matching the stage's search text (name or e-mail); the chosen user always stays listed. */
  protected usersFor(index: number): AdminUser[] {
    const text = (this.userFilters()[index] ?? '').trim().toLowerCase();
    const chosen = this.form.controls.stages.at(index)?.controls.approverUserId.value;
    return this.companyUsers().filter(
      (user) =>
        user.id === chosen || !text || user.displayName.toLowerCase().includes(text) || (user.email ?? '').toLowerCase().includes(text),
    );
  }

  protected filterUsers(index: number, text: string): void {
    this.userFilters.update((filters) => ({ ...filters, [index]: text }));
  }

  private loadUsers(companyId: number | null): void {
    this.companyUsers.set([]);
    if (companyId) {
      this.api.adminUsers(companyId).subscribe({ next: (users) => this.companyUsers.set(users), error: () => undefined });
    }
  }

  protected categoryName(id: number | null | undefined): string {
    const category = this.categories().find((item) => item.id === id);
    return category ? this.label(category) : '';
  }

  protected toggleVersions(rule: ApprovalRule): void {
    if (this.versions()[rule.id]) {
      this.versions.update((all) => {
        const next = { ...all };
        delete next[rule.id];
        return next;
      });
      return;
    }
    this.api.ruleVersions(rule.id).subscribe({
      next: (versions) => this.versions.update((all) => ({ ...all, [rule.id]: versions })),
      error: () => this.notifications.error('errors.unexpected-error'),
    });
  }

  protected edit(rule: ApprovalRule | null): void {
    this.editing.set(rule);
    this.userFilters.set({});
    this.submitted.set(false);
    this.error.set(null);
    this.form.reset({
      name: rule?.name ?? '',
      companyId: rule?.company.id ?? null,
      categoryId: rule?.categoryId ?? null,
      purchaseType: rule?.purchaseType ?? null,
      minAmount: rule ? String(rule.minAmount) : '0',
      maxAmount: rule?.maxAmount !== null && rule?.maxAmount !== undefined ? String(rule.maxAmount) : '',
    });
    this.form.controls.stages.clear();
    const stages = rule?.stages.length ? rule.stages : [undefined];
    for (const stage of stages) {
      this.form.controls.stages.push(
        stageForm(stage && { ...stage, approverUserId: stage.approverUserId ?? null }),
      );
    }
  }

  protected closeEditor(): void {
    this.editing.set(undefined);
  }

  protected addStage(): void {
    this.form.controls.stages.push(stageForm());
  }

  protected removeStage(index: number): void {
    if (this.form.controls.stages.length > 1) {
      this.form.controls.stages.removeAt(index);
    }
  }

  protected amountsInvalid(): boolean {
    const min = normaliseDecimal(this.form.controls.minAmount.value);
    const maxText = this.form.controls.maxAmount.value.trim();
    const max = maxText ? normaliseDecimal(maxText) : null;
    if (min === null || Number(min) < 0 || (maxText && max === null)) {
      return true;
    }
    return max !== null && Number(max) <= Number(min);
  }

  protected save(): void {
    this.submitted.set(true);
    this.error.set(null);
    const namedUserMissing = this.form.controls.stages.controls.some(
      (stage) => stage.controls.approverType.value === 'NAMED_USER' && !stage.controls.approverUserId.value,
    );
    if (this.form.invalid || this.amountsInvalid() || namedUserMissing || this.saving()) {
      return;
    }
    const value = this.form.getRawValue();
    const input: ApprovalRuleInput = {
      name: value.name.trim(),
      companyId: value.companyId!,
      categoryId: value.categoryId,
      purchaseType: value.purchaseType,
      minAmount: normaliseDecimal(value.minAmount)!,
      maxAmount: value.maxAmount.trim() ? normaliseDecimal(value.maxAmount) : null,
      // Stages are numbered by their order in the list.
      stages: value.stages.map((stage) => ({
        name: stage.name.trim() || null,
        approverType: stage.approverType,
        approverUserId: stage.approverType === 'NAMED_USER' ? stage.approverUserId : null,
        policy: stage.policy,
        slaHours: stage.slaHours,
        escalationRole: stage.escalationRole || null,
        minAmount: stage.minAmount.trim() ? normaliseDecimal(stage.minAmount) : null,
      })),
    };
    const editing = this.editing();
    this.saving.set(true);
    (editing ? this.api.updateRule(editing.id, input) : this.api.createRule(input)).subscribe({
      next: () => {
        this.saving.set(false);
        this.editing.set(undefined);
        this.notifications.success('rules.saved');
        this.versions.set({});
        this.load();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        const problem = toApiProblem(error);
        if (problem.status !== 401) {
          const code = problem.errors[0]?.code ?? problem.code;
          this.error.set(RULE_ERRORS.has(code) ? code : 'save-failed');
        }
      },
    });
  }

  protected activate(rule: ApprovalRule): void {
    openConfirm(this.dialog, {
      titleKey: 'rules.activateTitle',
      messageKey: 'rules.activateMessage',
      confirmKey: 'rules.activate',
      params: { name: rule.name, version: rule.versionNo },
    }).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.api.activateRule(rule.id).subscribe({
        next: () => {
          this.notifications.success('rules.activated', { name: rule.name });
          this.versions.set({});
          this.load();
        },
        error: (error: unknown) => {
          const problem = toApiProblem(error);
          if (problem.status !== 401) {
            this.notifications.error(problem.code === 'ambiguous-approval-rule' ? 'rules.errors.ambiguous-approval-rule' : `errors.${problem.code}`);
          }
        },
      });
    });
  }

  protected retire(rule: ApprovalRule): void {
    openConfirm(this.dialog, {
      titleKey: 'rules.retireTitle',
      messageKey: 'rules.retireMessage',
      confirmKey: 'rules.retire',
      params: { name: rule.name, version: rule.versionNo },
      tone: 'danger',
    }).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.api.retireRule(rule.id).subscribe({
        next: () => {
          this.notifications.success('rules.retired', { name: rule.name });
          this.versions.set({});
          this.load();
        },
        error: (error: unknown) => {
          const problem = toApiProblem(error);
          if (problem.status !== 401) {
            this.notifications.error(`errors.${problem.code}`);
          }
        },
      });
    });
  }

  protected tone(rule: ApprovalRule) {
    return rule.status === 'ACTIVE' ? 'success' : rule.status === 'DRAFT' ? 'info' : 'neutral';
  }

  protected band(rule: ApprovalRule): Record<string, unknown> {
    return { min: rule.minAmount, max: rule.maxAmount ?? '', currency: '' };
  }
}
