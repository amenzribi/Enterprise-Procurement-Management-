import { Dialog } from '@angular/cdk/dialog';
import { Location } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Observable, debounceTime, distinctUntilChanged, forkJoin, map, of, startWith, switchMap } from 'rxjs';
import { ApiProblem, toApiProblem } from '../../core/api/api-problem';
import { AuthService } from '../../core/auth/auth.service';
import { FormSnapshot, FormSnapshotStorage } from '../../core/form-snapshots/form-snapshot-storage';
import { LanguageService } from '../../core/i18n/language.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { openConfirm } from '../../shared/ui/confirm-dialog';
import { Attachments } from '../documents/attachments';
import { openSubmitPreview } from '../approvals/submit-preview-dialog';
import { openSubmitSuccess } from './submit-success-dialog';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { Icon } from '../../shared/ui/icon';
import { PageHeader } from '../../shared/ui/page-header';
import { Skeleton } from '../../shared/ui/skeleton';
import { formatMoney, lineTotalMinor, minorToDecimalString, normaliseDecimal } from './money';
import { CompanyOption, FormOptions, LineKind, PRIORITIES, PURCHASE_TYPES, PurchaseRequest, RequestPayload, optionLabel } from './request.models';
import {
  DEFAULT_MAX_LINES,
  ErrorInfo,
  controlPath,
  decimalValidator,
  errorInfo,
  notPastDate,
  requiredValue,
} from './request-form.validation';
import { RequestsApi } from './requests.api';
import { HasUnsavedChanges } from './unsaved-changes.guard';

type LineForm = FormGroup<{
  /** Server id of an existing line; null for a line added in this form. */
  id: FormControl<number | null>;
  description: FormControl<string>;
  itemReference: FormControl<string>;
  quantity: FormControl<string>;
  unit: FormControl<string>;
  estimatedUnitPrice: FormControl<string>;
  requiredDate: FormControl<string>;
  deliveryLocation: FormControl<string>;
  lineKind: FormControl<LineKind>;
}>;

export type LineValue = LineForm['value'];

export type RequestFormValue = ReturnType<RequestFormPage['form']['getRawValue']>;

type PageState = 'loading' | 'ready' | 'error' | 'not-found' | 'not-editable' | 'cannot-create';
type Busy = 'idle' | 'saving' | 'submitting' | 'discarding';

interface SummaryError extends ErrorInfo {
  id: string;
  labelKey: string;
  labelParams?: Record<string, unknown>;
}

const HEADER_LABELS: Record<string, string> = {
  title: 'requests.fields.title',
  categoryId: 'requests.fields.category',
  priority: 'requests.fields.priority',
  purchaseType: 'requests.fields.purchaseType',
  currency: 'requests.fields.currency',
  requestingCompanyId: 'requests.fields.requestingCompany',
  requesterId: 'requests.fields.requester',
  departmentId: 'requests.fields.department',
  siteId: 'requests.fields.site',
  costCenterId: 'requests.fields.costCenter',
  project: 'requests.fields.project',
  justification: 'requests.fields.justification',
  urgencyJustification: 'requests.fields.urgencyJustification',
  budgetLineRef: 'requests.fields.budgetLineRef',
  equipmentRef: 'requests.fields.equipmentRef',
  maintenanceOrderRef: 'requests.fields.maintenanceOrderRef',
  requiredDate: 'requests.fields.requiredDate',
  deliveryLocation: 'requests.fields.deliveryLocation',
  beneficiary: 'requests.fields.beneficiary',
  beneficiarySiteId: 'requests.fields.beneficiarySite',
  lines: 'requests.fields.lines',
};

/** Labels used in the error summary, which name the line ("Line 2, quantity"). */
const LINE_LABELS: Record<string, string> = {
  description: 'requests.fields.lineSummary.description',
  itemReference: 'requests.fields.lineSummary.itemReference',
  quantity: 'requests.fields.lineSummary.quantity',
  unit: 'requests.fields.lineSummary.unit',
  estimatedUnitPrice: 'requests.fields.lineSummary.unitPrice',
  requiredDate: 'requests.fields.lineSummary.requiredDate',
  deliveryLocation: 'requests.fields.lineSummary.deliveryLocation',
};

/**
 * Create and edit a draft purchase request (S1-AC-03..05, S1-AC-15). Saving a draft only checks formats; required
 * fields are enforced when submitting. The server re-validates everything and derives the charged and buying
 * companies (S1DR-07); its field errors are shown on the matching controls.
 */
@Component({
  selector: 'app-request-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Attachments, Button, EmptyState, ErrorState, Icon, PageHeader, ReactiveFormsModule, RouterLink, Skeleton, TranslatePipe],
  templateUrl: './request-form.html',
  styleUrl: './request-form.scss',
  host: { '(window:beforeunload)': 'onBeforeUnload($event)' },
})
export class RequestFormPage implements HasUnsavedChanges {
  private readonly api = inject(RequestsApi);
  protected readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly dialog = inject(Dialog);
  private readonly notifications = inject(NotificationService);
  private readonly snapshots = inject(FormSnapshotStorage);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly language = inject(LanguageService);

  protected readonly priorities = PRIORITIES;
  protected readonly purchaseTypes = PURCHASE_TYPES;
  protected readonly optionLabel = optionLabel;

  // Declared before the form: its validators read them while the form is being built.
  protected readonly options = signal<FormOptions | null>(null);
  protected readonly maxLines = computed(() => this.options()?.maxLines ?? DEFAULT_MAX_LINES);

  /** "draft": format checks only; "submit": required fields too. Validators read it when they run. */
  private mode: 'draft' | 'submit' = 'draft';
  private readonly onSubmit =
    (validator: ValidatorFn): ValidatorFn =>
    (control) =>
      this.mode === 'submit' ? validator(control) : null;

  readonly form = this.fb.group({
    title: ['', [Validators.maxLength(200), this.onSubmit(requiredValue)]],
    categoryId: this.fb.control<number | null>(null, this.onSubmit(requiredValue)),
    priority: ['NORMAL', this.onSubmit(requiredValue)],
    purchaseType: ['LOCAL', this.onSubmit(requiredValue)],
    currency: ['', this.onSubmit(requiredValue)],
    requestingCompanyId: this.fb.control<number | null>(null, this.onSubmit(requiredValue)),
    requesterId: this.fb.control<number | null>(null),
    departmentId: this.fb.control<number | null>(null, this.onSubmit(requiredValue)),
    siteId: this.fb.control<number | null>(null),
    costCenterId: this.fb.control<number | null>(null),
    project: ['', Validators.maxLength(100)],
    justification: ['', Validators.maxLength(2000)],
    urgencyJustification: ['', [Validators.maxLength(2000), this.onSubmit((c) => this.urgencyRequired(c))]],
    budgetLineRef: ['', Validators.maxLength(100)],
    equipmentRef: ['', Validators.maxLength(100)],
    maintenanceOrderRef: ['', Validators.maxLength(100)],
    requiredDate: ['', [this.onSubmit(requiredValue), this.onSubmit(notPastDate)]],
    deliveryLocation: ['', Validators.maxLength(200)],
    beneficiary: ['', Validators.maxLength(200)],
    beneficiarySiteId: this.fb.control<number | null>(null),
    lines: this.fb.array<LineForm>(
      [],
      [
        this.onSubmit((c) => ((c as FormArray).length === 0 ? { required: true } : null)),
        (c) => ((c as FormArray).length > this.maxLines() ? { 'too-many-lines': { max: this.maxLines() } } : null),
      ],
    ),
  });

  protected readonly pageState = signal<PageState>('loading');
  protected readonly loadError = signal<string | null>(null);
  protected readonly request = signal<PurchaseRequest | null>(null);
  protected readonly busy = signal<Busy>('idle');
  protected readonly conflict = signal<'stale-version' | 'invalid-state' | null>(null);
  protected readonly summaryErrors = signal<SummaryError[]>([]);
  protected readonly restoreOffer = signal<FormSnapshot<unknown> | null>(null);

  protected readonly requestId = signal<number | null>(null);
  protected readonly isNew = computed(() => this.requestId() === null);
  protected readonly canDiscard = computed(() => !!this.request()?.actions?.includes('DISCARD'));

  private readonly formValue = toSignal(this.form.valueChanges.pipe(startWith(null), map(() => this.form.getRawValue())), {
    initialValue: this.form.getRawValue(),
  });

  protected readonly companies = computed(() => this.options()?.companies ?? []);
  /** The requesting company drives the department, site, cost-centre and requester choices. */
  protected readonly company = computed<CompanyOption | null>(
    () => this.companies().find((company) => company.id === this.formValue().requestingCompanyId) ?? null,
  );
  protected readonly canEnterOnBehalf = computed(() => !!this.company()?.canEnterOnBehalf);
  protected readonly isUrgent = computed(() => this.formValue().priority === 'URGENT');

  protected readonly minorUnits = computed(() => {
    const code = this.formValue().currency;
    return this.options()?.currencies.find((currency) => currency.code === code)?.minorUnits ?? 2;
  });

  protected readonly lineTotals = computed(() =>
    this.formValue().lines.map((line) => lineTotalMinor(line.quantity, line.estimatedUnitPrice, this.minorUnits())),
  );

  protected readonly total = computed(() => this.lineTotals().reduce<bigint>((sum, value) => sum + (value ?? 0n), 0n));

  constructor() {
    const destroyRef = inject(DestroyRef);
    const idParam = this.route.snapshot.paramMap.get('id');
    const id = idParam === null ? null : Number(idParam);
    this.requestId.set(id !== null && Number.isInteger(id) ? id : null);
    if (idParam !== null && this.requestId() === null) {
      this.pageState.set('not-found');
      return;
    }
    this.load();

    // Choosing another requesting company resets the choices that belong to the previous one.
    this.form.controls.requestingCompanyId.valueChanges
      .pipe(distinctUntilChanged(), takeUntilDestroyed(destroyRef))
      .subscribe(() => {
        if (this.pageState() !== 'ready') {
          return;
        }
        const company = this.company();
        this.form.patchValue({
          departmentId: null,
          siteId: null,
          costCenterId: null,
          beneficiarySiteId: null,
          requesterId: this.auth.me()?.id ?? null,
          currency: company?.currency ?? this.form.controls.currency.value,
        });
      });

    // Keep unsaved content in session storage (Q6): restored after a session expiry or reload.
    this.form.valueChanges.pipe(debounceTime(400), takeUntilDestroyed(destroyRef)).subscribe(() => {
      const userId = this.auth.me()?.id;
      if (userId !== undefined && this.form.dirty && this.pageState() === 'ready') {
        this.snapshots.save(userId, this.snapshotScope(), {
          version: this.request()?.version ?? null,
          savedAt: new Date().toISOString(),
          value: this.form.getRawValue(),
        });
      }
    });
  }

  get lines(): FormArray<LineForm> {
    return this.form.controls.lines;
  }

  hasUnsavedChanges(): boolean {
    return this.form.dirty && this.busy() === 'idle';
  }

  protected onBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.hasUnsavedChanges()) {
      event.preventDefault();
      // Legacy browsers need returnValue set to show the confirmation.
      event.returnValue = '';
    }
  }

  protected load(): void {
    this.pageState.set('loading');
    const id = this.requestId();
    forkJoin([this.api.formOptions(), id === null ? of(null) : this.api.get(id)]).subscribe({
      next: ([options, request]) => {
        this.options.set(options);
        this.request.set(request);
        if (request && !request.actions?.includes('EDIT')) {
          this.pageState.set('not-editable');
          return;
        }
        if (!request && options.companies.length === 0) {
          this.pageState.set('cannot-create');
          return;
        }
        this.resetForm(request);
        this.pageState.set('ready');
        this.offerRestore();
      },
      error: (error: unknown) => {
        const problem = toApiProblem(error);
        if (problem.status === 404) {
          this.pageState.set('not-found');
        } else {
          this.loadError.set(problem.code);
          this.pageState.set('error');
        }
      },
    });
  }

  protected addLine(): void {
    if (this.lines.length >= this.maxLines()) {
      return;
    }
    this.lines.push(this.createLine());
    this.form.markAsDirty();
    const index = this.lines.length - 1;
    afterNextRender(() => this.focusById(`line-${index}-description`), { injector: this.injector });
  }

  protected removeLine(index: number): void {
    if (this.lines.length <= 1) {
      return;
    }
    this.lines.removeAt(index);
    this.form.markAsDirty();
    const next = Math.min(index, this.lines.length - 1);
    afterNextRender(() => this.focusById(`line-${next}-description`), { injector: this.injector });
  }

  protected saveDraft(): void {
    this.mode = 'draft';
    if (!this.validate()) {
      return;
    }
    this.busy.set('saving');
    this.persist().subscribe({
      next: () => {
        this.busy.set('idle');
        this.notifications.success('requests.form.saved');
      },
      error: (error: unknown) => this.handleFailure(error),
    });
  }

  /**
   * Saves the draft, shows who will approve it (S2DR-17), then submits on confirmation. Saving first is required:
   * the approval preview is computed by the server from the saved request.
   */
  protected submit(): void {
    this.mode = 'submit';
    if (!this.validate()) {
      return;
    }
    this.busy.set('submitting');
    this.persist()
      .pipe(
        switchMap((saved) => {
          this.busy.set('idle');
          return openSubmitPreview(this.dialog, saved.id).pipe(map((confirmed) => ({ confirmed, saved })));
        }),
      )
      .subscribe({
        next: ({ confirmed, saved }) => {
          if (!confirmed) {
            // The draft is saved; the user stays on the form.
            this.notifications.info('requests.form.saved');
            return;
          }
          this.busy.set('submitting');
          this.api.submit(saved.id, saved.version).subscribe({
            next: (submitted) => {
              this.clearSnapshot();
              this.form.markAsPristine();
              this.busy.set('idle');
              const number = submitted.number ?? '';
              this.notifications.success('requests.submit.done', { number });
              openSubmitSuccess(this.dialog, {
                requestId: submitted.id,
                number,
                title: submitted.title ?? '',
              }).subscribe((action) => {
                if (action === 'list') {
                  void this.router.navigate(['/requests']);
                } else if (action !== 'open') {
                  void this.router.navigate(['/requests', submitted.id]);
                }
              });
            },
            error: (error: unknown) => this.handleFailure(error),
          });
        },
        error: (error: unknown) => this.handleFailure(error),
      });
  }

  protected discard(): void {
    const request = this.request();
    if (!request) {
      return;
    }
    openConfirm(this.dialog, {
      titleKey: 'requests.discard.title',
      messageKey: 'requests.discard.message',
      confirmKey: 'requests.discard.confirm',
      tone: 'danger',
    }).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.busy.set('discarding');
      this.api.discard(request.id, request.version).subscribe({
        next: () => {
          this.clearSnapshot();
          this.form.markAsPristine();
          this.busy.set('idle');
          this.notifications.success('requests.discard.done');
          void this.router.navigate(['/requests']);
        },
        error: (error: unknown) => this.handleFailure(error),
      });
    });
  }

  protected reloadLatest(): void {
    openConfirm(this.dialog, {
      titleKey: 'requests.conflict.reloadTitle',
      messageKey: 'requests.conflict.reloadMessage',
      confirmKey: 'requests.conflict.reload',
      tone: 'danger',
    }).subscribe((confirmed) => {
      if (confirmed) {
        this.clearSnapshot();
        this.conflict.set(null);
        this.load();
      }
    });
  }

  protected restore(): void {
    const snapshot = this.restoreOffer();
    if (!snapshot) {
      return;
    }
    this.patchForm(snapshot.value as RequestFormValue);
    this.form.markAsDirty();
    this.restoreOffer.set(null);
  }

  protected dismissRestore(): void {
    this.clearSnapshot();
    this.restoreOffer.set(null);
  }

  protected focusById(id: string): void {
    this.host.nativeElement.querySelector<HTMLElement>(`#${CSS.escape(id)}`)?.focus();
  }

  /** The error to show under a control, once the user touched it or tried to save. */
  protected errorFor(control: AbstractControl): ErrorInfo | null {
    return control.invalid && (control.touched || control.dirty) ? errorInfo(control.errors) : null;
  }

  protected lineTotalText(index: number): string {
    const value = this.lineTotals()[index];
    return value === null || value === undefined ? '—' : this.money(value);
  }

  protected totalText(): string {
    return this.money(this.total());
  }

  protected locale(): string {
    return this.language.locale();
  }

  private money(minor: bigint): string {
    const currency = this.formValue().currency;
    const decimal = minorToDecimalString(minor, this.minorUnits());
    return currency ? formatMoney(decimal, currency, this.minorUnits(), this.locale()) : decimal;
  }

  private urgencyRequired(control: AbstractControl): Record<string, boolean> | null {
    return this.form?.controls.priority.value === 'URGENT' && requiredValue(control) ? { required: true } : null;
  }

  private createLine(value?: Partial<LineValue>): LineForm {
    return this.fb.group({
      id: this.fb.control<number | null>(value?.id ?? null),
      description: [value?.description ?? '', [Validators.maxLength(500), this.onSubmit(requiredValue)]],
      itemReference: [value?.itemReference ?? '', Validators.maxLength(100)],
      quantity: [value?.quantity ?? '', [decimalValidator('quantity'), this.onSubmit(requiredValue)]],
      unit: [value?.unit ?? '', this.onSubmit(requiredValue)],
      estimatedUnitPrice: [value?.estimatedUnitPrice ?? '', [decimalValidator('price'), this.onSubmit(requiredValue)]],
      requiredDate: [value?.requiredDate ?? '', this.onSubmit(notPastDate)],
      deliveryLocation: [value?.deliveryLocation ?? '', Validators.maxLength(200)],
      lineKind: this.fb.control<LineKind>(value?.lineKind ?? 'GOODS'),
    });
  }

  private resetForm(request: PurchaseRequest | null): void {
    const options = this.options();
    const me = this.auth.me();
    const text = (value: string | null | undefined) => value ?? '';
    if (request) {
      this.patchForm({
        title: text(request.title),
        categoryId: request.category?.id ?? null,
        priority: request.priority ?? 'NORMAL',
        purchaseType: request.purchaseType ?? 'LOCAL',
        currency: text(request.currency),
        requestingCompanyId: request.requestingCompany?.id ?? null,
        requesterId: request.requester?.id ?? null,
        departmentId: request.department?.id ?? null,
        siteId: request.site?.id ?? null,
        costCenterId: request.costCenter?.id ?? null,
        project: text(request.project),
        justification: text(request.justification),
        urgencyJustification: text(request.urgencyJustification),
        budgetLineRef: text(request.budgetLineRef),
        equipmentRef: text(request.equipmentRef),
        maintenanceOrderRef: text(request.maintenanceOrderRef),
        requiredDate: text(request.requiredDate),
        deliveryLocation: text(request.deliveryLocation),
        beneficiary: text(request.beneficiary),
        beneficiarySiteId: request.beneficiarySite?.id ?? null,
        lines: request.lines.map((line) => ({
          id: line.id ?? null,
          description: text(line.description),
          itemReference: text(line.itemReference),
          quantity: line.quantity === null || line.quantity === undefined ? '' : String(line.quantity),
          unit: text(line.unit),
          estimatedUnitPrice:
            line.estimatedUnitPrice === null || line.estimatedUnitPrice === undefined ? '' : String(line.estimatedUnitPrice),
          requiredDate: text(line.requiredDate),
          deliveryLocation: text(line.deliveryLocation),
          lineKind: line.lineKind ?? 'GOODS',
        })) as Required<LineValue>[],
      });
    } else {
      const defaults = options?.defaults ?? {};
      const companyId = defaults.requestingCompanyId ?? options?.companies[0]?.id ?? null;
      const company = options?.companies.find((c) => c.id === companyId);
      this.patchForm({
        ...this.form.getRawValue(),
        requestingCompanyId: companyId,
        currency: defaults.currency ?? company?.currency ?? options?.currencies[0]?.code ?? '',
        requesterId: defaults.requesterId ?? me?.id ?? null,
        departmentId: defaults.departmentId ?? null,
        siteId: defaults.siteId ?? null,
        lines: [],
      });
    }
    if (this.lines.length === 0) {
      this.lines.push(this.createLine());
    }
    this.form.markAsPristine();
    this.form.markAsUntouched();
  }

  private patchForm(value: RequestFormValue): void {
    this.lines.clear({ emitEvent: false });
    (value.lines ?? []).forEach((line) => this.lines.push(this.createLine(line), { emitEvent: false }));
    // Lines were rebuilt above; patch the header without triggering the company-change reset.
    this.form.patchValue({ ...value, lines: undefined }, { emitEvent: false });
    this.form.updateValueAndValidity();
  }

  private offerRestore(): void {
    const userId = this.auth.me()?.id;
    if (userId === undefined) {
      return;
    }
    const snapshot = this.snapshots.load<unknown>(userId, this.snapshotScope());
    if (!snapshot) {
      return;
    }
    const sameVersion = snapshot.version === (this.request()?.version ?? null);
    const differs = JSON.stringify(snapshot.value) !== JSON.stringify(this.form.getRawValue());
    if (sameVersion && differs) {
      this.restoreOffer.set(snapshot);
    } else {
      this.clearSnapshot();
    }
  }

  private snapshotScope(): string {
    return `request.${this.requestId() ?? 'new'}`;
  }

  private clearSnapshot(): void {
    const userId = this.auth.me()?.id;
    if (userId !== undefined) {
      this.snapshots.clear(userId, this.snapshotScope());
    }
  }

  /** Runs validators for the current mode; on failure shows the error summary and focuses it. */
  private validate(): boolean {
    this.conflict.set(null);
    this.updateValidity(this.form);
    this.form.markAllAsTouched();
    if (this.form.valid) {
      this.summaryErrors.set([]);
      return true;
    }
    this.showSummary(this.collectErrors());
    return false;
  }

  private updateValidity(control: AbstractControl): void {
    if (control instanceof FormGroup || control instanceof FormArray) {
      Object.values(control.controls).forEach((child) => this.updateValidity(child));
    }
    control.updateValueAndValidity({ onlySelf: true, emitEvent: false });
  }

  private collectErrors(): SummaryError[] {
    const errors: SummaryError[] = [];
    for (const [name, control] of Object.entries(this.form.controls)) {
      if (name === 'lines') {
        const info = errorInfo(this.lines.errors);
        if (info) {
          errors.push({ id: 'add-line', labelKey: HEADER_LABELS['lines'], ...info });
        }
        this.lines.controls.forEach((line, index) => {
          for (const [field, lineControl] of Object.entries(line.controls)) {
            if (field === 'id') {
              continue;
            }
            const lineInfo = errorInfo(lineControl.errors);
            if (lineInfo) {
              errors.push({ id: `line-${index}-${field}`, labelKey: LINE_LABELS[field], labelParams: { line: index + 1 }, ...lineInfo });
            }
          }
        });
        continue;
      }
      const info = errorInfo(control.errors);
      if (info) {
        errors.push({ id: `request-${name}`, labelKey: HEADER_LABELS[name] ?? 'requests.fields.general', ...info });
      }
    }
    return errors;
  }

  private showSummary(errors: SummaryError[]): void {
    this.summaryErrors.set(errors);
    afterNextRender(() => this.focusById('form-error-summary'), { injector: this.injector });
  }

  private persist(): Observable<PurchaseRequest> {
    const id = this.requestId();
    const payload = this.toPayload();
    const call = id === null ? this.api.create(payload) : this.api.update(id, { ...payload, version: this.request()?.version });
    return call.pipe(
      map((saved) => {
        const wasNew = id === null;
        this.request.set(saved);
        this.requestId.set(saved.id);
        if (wasNew) {
          // The snapshot scope becomes the request id; drop the "new" copy and keep the URL bookmarkable.
          const userId = this.auth.me()?.id;
          if (userId !== undefined) {
            this.snapshots.clear(userId, 'request.new');
          }
          this.location.replaceState(`/requests/${saved.id}/edit`);
        }
        this.adoptLineIds(saved);
        this.clearSnapshot();
        this.form.markAsPristine();
        this.summaryErrors.set([]);
        return saved;
      }),
    );
  }

  /** Lines are saved in form order, so the saved request's lines map one to one onto the form lines. */
  private adoptLineIds(saved: PurchaseRequest): void {
    const savedLines = [...saved.lines].sort((a, b) => (a.lineNo ?? 0) - (b.lineNo ?? 0));
    if (savedLines.length !== this.lines.length) {
      return;
    }
    savedLines.forEach((line, index) => this.lines.at(index).controls.id.setValue(line.id ?? null, { emitEvent: false }));
  }

  private toPayload(): RequestPayload {
    const value = this.form.getRawValue();
    const text = (input: string) => (input.trim() === '' ? null : input.trim());
    const decimal = (input: string) => {
      const normalised = normaliseDecimal(input);
      return normalised === null ? null : Number(normalised);
    };
    const meId = this.auth.me()?.id;
    return {
      title: value.title.trim(),
      categoryId: value.categoryId,
      priority: value.priority || null,
      purchaseType: value.purchaseType || null,
      currency: value.currency || null,
      requestingCompanyId: value.requestingCompanyId,
      // Omitted when requesting for oneself; the server records the session user as requester.
      ...(value.requesterId !== null && value.requesterId !== meId ? { requesterId: value.requesterId } : {}),
      departmentId: value.departmentId,
      siteId: value.siteId,
      costCenterId: value.costCenterId,
      project: text(value.project),
      justification: text(value.justification),
      urgencyJustification: value.priority === 'URGENT' ? text(value.urgencyJustification) : null,
      budgetLineRef: text(value.budgetLineRef),
      equipmentRef: text(value.equipmentRef),
      maintenanceOrderRef: text(value.maintenanceOrderRef),
      requiredDate: value.requiredDate || null,
      deliveryLocation: text(value.deliveryLocation),
      beneficiary: text(value.beneficiary),
      beneficiarySiteId: value.beneficiarySiteId,
      lines: value.lines.map((line) => ({
        ...(line.id !== null ? { id: line.id } : {}),
        description: line.description.trim(),
        itemReference: text(line.itemReference),
        quantity: decimal(line.quantity),
        unit: line.unit,
        estimatedUnitPrice: decimal(line.estimatedUnitPrice),
        requiredDate: line.requiredDate || null,
        deliveryLocation: text(line.deliveryLocation),
        lineKind: line.lineKind,
      })),
    };
  }

  private handleFailure(error: unknown): void {
    this.busy.set('idle');
    const problem = toApiProblem(error);
    if (problem.status === 401) {
      // The interceptor already returns the user to sign-in; the content is kept for restore.
      return;
    }
    if (problem.status === 409) {
      this.conflict.set(problem.code === 'invalid-state' ? 'invalid-state' : 'stale-version');
      afterNextRender(() => this.focusById('form-conflict'), { injector: this.injector });
      return;
    }
    if (problem.status === 422 && problem.errors.length > 0) {
      this.applyServerErrors(problem);
      return;
    }
    if (problem.status === 404) {
      this.pageState.set('not-found');
      return;
    }
    // Includes the approval configuration problems at submission (missing-exchange-rate, no-approval-rule, ...).
    this.notifications.error(`errors.${problem.code}`);
  }

  private applyServerErrors(problem: ApiProblem): void {
    const summary: SummaryError[] = [];
    for (const fieldError of problem.errors) {
      const info: ErrorInfo = { code: fieldError.code, params: fieldError.params };
      const path = controlPath(fieldError.field);
      const control = this.form.get(path);
      const lineMatch = /^lines\.(\d+)\.(\w+)$/.exec(path);
      if (control) {
        control.setErrors({ ...(control.errors ?? {}), server: info });
        control.markAsTouched();
      }
      summary.push({
        id: lineMatch ? `line-${lineMatch[1]}-${lineMatch[2]}` : control ? (path === 'lines' ? 'add-line' : `request-${path}`) : 'form-error-summary',
        labelKey: lineMatch
          ? (LINE_LABELS[lineMatch[2]] ?? 'requests.fields.lineSummary.general')
          : (HEADER_LABELS[path] ?? 'requests.fields.general'),
        labelParams: lineMatch ? { line: Number(lineMatch[1]) + 1 } : undefined,
        ...info,
      });
    }
    this.showSummary(summary);
  }
}
