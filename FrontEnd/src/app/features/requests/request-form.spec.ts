import { Dialog } from '@angular/cdk/dialog';
import { SubmitPreviewDialog } from '../approvals/submit-preview-dialog';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { Me } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { FormOptions, PurchaseRequest } from './request.models';
import { RequestFormPage } from './request-form';

@Component({ template: '' })
class BlankPage {}

const ME: Me = {
  id: 11,
  email: 'assistant.tn@demo.local',
  displayName: 'Ines Trabelsi',
  company: { id: 3, code: 'DEMO-TN-PLT', name: 'Atlas Mechanical Sfax (Demo)' },
  department: { id: 31, name: 'Maintenance (Demo)' },
  roles: ['REQUESTER', 'ENTER_ON_BEHALF'],
  permissions: ['REQUEST_CREATE', 'REQUEST_ENTER_ON_BEHALF'],
};

const COMPANY = {
  id: 3,
  code: 'DEMO-TN-PLT',
  name: 'Atlas Mechanical Sfax (Demo)',
  currency: 'TND',
  canEnterOnBehalf: true,
  departments: [{ id: 31, code: 'DEMO-MAINT', name: 'Maintenance (Demo)', siteId: 41 }],
  sites: [{ id: 41, code: 'DEMO-SFAX', name: 'Sfax plant (Demo)' }],
  costCenters: [{ id: 51, code: 'DEMO-CC', name: 'Sfax plant maintenance (Demo)' }],
  requesters: [
    { id: 11, displayName: 'Ines Trabelsi', departmentId: 31 },
    { id: 12, displayName: 'Salma Ben Ali', departmentId: 31 },
  ],
  chargedCompany: { id: 3, code: 'DEMO-TN-PLT', name: 'Atlas Mechanical Sfax (Demo)' },
  buyingCompany: { id: 3, code: 'DEMO-TN-PLT', name: 'Atlas Mechanical Sfax (Demo)' },
  buyingCompanyProposed: true,
};

const OPTIONS: FormOptions = {
  categories: [{ id: 5, code: 'SPARE', nameEn: 'Spare parts', nameFr: 'Pièces de rechange' }],
  currencies: [
    { code: 'TND', minorUnits: 3, nameEn: 'Tunisian dinar' },
    { code: 'EUR', minorUnits: 2, nameEn: 'Euro' },
  ],
  units: [
    { code: 'PCE', nameEn: 'Piece', nameFr: 'Pièce' },
    { code: 'KG', nameEn: 'Kilogram', nameFr: 'Kilogramme' },
  ],
  priorities: ['LOW', 'NORMAL', 'HIGH', 'URGENT'],
  purchaseTypes: ['LOCAL', 'INTERNATIONAL'],
  maxLines: 100,
  defaults: { requestingCompanyId: 3, departmentId: 31, siteId: 41, currency: 'TND', requesterId: 11 },
  companies: [COMPANY],
};

function future(days: number): string {
  const date = new Date(Date.now() + days * 86_400_000);
  return date.toISOString().slice(0, 10);
}

function saved(overrides: Partial<PurchaseRequest> = {}): PurchaseRequest {
  return {
    id: 101,
    number: null,
    status: 'DRAFT',
    title: 'Bearings for line 2',
    priority: 'NORMAL',
    purchaseType: 'LOCAL',
    currency: 'TND',
    requestingCompany: { id: 3, name: 'Atlas Mechanical Sfax (Demo)' },
    requester: { id: 11, displayName: 'Ines Trabelsi' },
    enteredBy: { id: 11, displayName: 'Ines Trabelsi' },
    lines: [{ id: 501, lineNo: 1, description: 'SKF 6205', quantity: 10, unit: 'PCE', estimatedUnitPrice: 12.5 }],
    version: 0,
    actions: ['EDIT', 'SUBMIT', 'DISCARD'],
    ...overrides,
  };
}

describe('RequestFormPage', () => {
  let fixture: ComponentFixture<RequestFormPage>;
  let page: RequestFormPage;
  let http: HttpTestingController;
  const confirm = vi.fn(() => ({ closed: of(true) }));

  async function create(id: string | null = null): Promise<void> {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [RequestFormPage],
      providers: [
        provideRouter([{ path: '**', component: BlankPage }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService({ fallbackLang: 'en' }),
        { provide: AuthService, useValue: { me: signal(ME), isLeavingForSignIn: false } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } } },
        { provide: Dialog, useValue: { open: confirm } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RequestFormPage);
    page = fixture.componentInstance;
    http.expectOne('/api/v1/requests/form-options').flush(OPTIONS);
    if (id) {
      http.expectOne(`/api/v1/requests/${id}`).flush(saved({ id: Number(id), version: 3 }));
    }
    await fixture.whenStable();
  }

  const element = () => fixture.nativeElement as HTMLElement;

  function fillValidRequest(): void {
    page.form.patchValue({ title: 'Bearings for line 2', categoryId: 5, departmentId: 31, requiredDate: future(10) });
    page.lines.at(0).patchValue({ description: 'SKF 6205 bearing', quantity: '10', unit: 'PCE', estimatedUnitPrice: '12,5' });
    page.form.markAsDirty();
  }

  afterEach(() => {
    // The attachments panel of a saved request loads its list on its own (S2-AC-12).
    http.match((request) => request.url.endsWith('/attachments')).forEach((request) => request.flush([]));
    http.verify();
  });

  it('starts a new request with the user, their department and the company currency', async () => {
    await create();

    const value = page.form.getRawValue();
    expect(value.requestingCompanyId).toBe(3);
    expect(value.requesterId).toBe(11);
    expect(value.departmentId).toBe(31);
    expect(value.siteId).toBe(41);
    expect(value.currency).toBe('TND');
    expect(page.lines.length).toBe(1);
    expect(page.hasUnsavedChanges()).toBe(false);
  });

  it('saves an incomplete draft: required fields are only enforced at submission', async () => {
    await create();
    page.form.patchValue({ title: 'Only a title' });
    page.form.markAsDirty();

    page['saveDraft']();
    const post = http.expectOne('/api/v1/requests');
    expect(post.request.method).toBe('POST');
    expect(post.request.body.title).toBe('Only a title');
    post.flush(saved({ title: 'Only a title' }));

    expect(page.hasUnsavedChanges()).toBe(false);
  });

  it('never sends server-derived fields (companies, number, status, entered-by)', async () => {
    await create();
    fillValidRequest();

    page['saveDraft']();
    const post = http.expectOne('/api/v1/requests');
    const body = post.request.body as Record<string, unknown>;

    for (const forbidden of ['chargedCompanyId', 'buyingCompanyId', 'number', 'status', 'enteredById', 'origin', 'totalAmount']) {
      expect(body).not.toHaveProperty(forbidden);
    }
    expect(body['requestingCompanyId']).toBe(3);
    // Requesting for oneself: requesterId is omitted.
    expect(body).not.toHaveProperty('requesterId');
    expect(body['lines']).toEqual([
      expect.objectContaining({ description: 'SKF 6205 bearing', quantity: 10, unit: 'PCE', estimatedUnitPrice: 12.5 }),
    ]);
    // A new request has only new lines: no line ids are sent.
    expect((body['lines'] as Record<string, unknown>[])[0]).not.toHaveProperty('id');
    post.flush(saved());
  });

  it('blocks submission and lists every missing required field', async () => {
    await create();

    page['submit']();
    fixture.detectChanges();
    await fixture.whenStable();

    http.expectNone('/api/v1/requests');
    const summary = element().querySelector('#form-error-summary');
    expect(summary).not.toBeNull();
    const codes = page['summaryErrors']().map((error: { id: string; code: string }) => `${error.id}:${error.code}`);
    expect(codes).toEqual(
      expect.arrayContaining([
        'request-title:required',
        'request-categoryId:required',
        'request-requiredDate:required',
        'line-0-description:required',
        'line-0-quantity:required',
      ]),
    );
  });

  it('requires a reason when the priority is urgent', async () => {
    await create();
    fillValidRequest();
    page.form.patchValue({ priority: 'URGENT' });

    page['submit']();

    expect(page['summaryErrors']().map((e: { id: string }) => e.id)).toContain('request-urgencyJustification');
  });

  it('saves then submits a complete draft and keeps the version for optimistic locking', async () => {
    await create('101');
    fillValidRequest();

    page['submit']();
    const put = http.expectOne('/api/v1/requests/101');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body.version).toBe(3);
    put.flush(saved({ version: 4 }));

    // The approval preview is shown for the saved request before submitting (S2DR-17).
    expect(confirm).toHaveBeenCalledWith(SubmitPreviewDialog, expect.objectContaining({ data: { requestId: 101 } }));
    const submit = http.expectOne('/api/v1/requests/101/submit');
    expect(submit.request.body).toEqual({ version: 4 });
    submit.flush(saved({ status: 'CREATED', number: 'DEMO-TN-PLT-PR-2026-000001', version: 5, actions: [] }));

    expect(page.hasUnsavedChanges()).toBe(false);
  });

  it('sends the ids of existing lines and none for added lines (S1CR-07)', async () => {
    await create('101');
    fillValidRequest();
    page.lines.push(page['createLine']({ description: 'Grease', quantity: '3', unit: 'KG', estimatedUnitPrice: '8' }));

    page['saveDraft']();
    const put = http.expectOne('/api/v1/requests/101');
    const lines = put.request.body.lines as Record<string, unknown>[];
    expect(lines[0]['id']).toBe(501);
    expect(lines[1]).not.toHaveProperty('id');
    put.flush(
      saved({
        version: 4,
        lines: [
          { id: 501, lineNo: 1, description: 'SKF 6205 bearing', quantity: 10, unit: 'PCE', estimatedUnitPrice: 12.5 },
          { id: 502, lineNo: 2, description: 'Grease', quantity: 3, unit: 'KG', estimatedUnitPrice: 8 },
        ],
      }),
    );

    // The next save updates the added line instead of adding it again.
    expect(page.lines.at(1).controls.id.value).toBe(502);
  });

  it('shows server field errors on the matching line field', async () => {
    await create('101');
    fillValidRequest();

    page['saveDraft']();
    http.expectOne('/api/v1/requests/101').flush(
      {
        code: 'validation-failed',
        errors: [
          { field: 'lines[0].quantity', code: 'max-decimals', params: { max: 4 } },
          { field: 'departmentId', code: 'not-in-company' },
        ],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    expect(page.lines.at(0).controls.quantity.errors?.['server']).toEqual({ code: 'max-decimals', params: { max: 4 } });
    expect(page.form.controls.departmentId.errors?.['server']).toEqual({ code: 'not-in-company', params: undefined });
    expect(page['summaryErrors']().map((e: { id: string }) => e.id)).toEqual(['line-0-quantity', 'request-departmentId']);
  });

  it('reports a concurrent change instead of overwriting it', async () => {
    await create('101');
    fillValidRequest();

    page['saveDraft']();
    http.expectOne('/api/v1/requests/101').flush({ code: 'stale-version' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(page['conflict']()).toBe('stale-version');
    expect(element().querySelector('#form-conflict')).not.toBeNull();
    // The user's edits stay in the form.
    expect(page.form.getRawValue().title).toBe('Bearings for line 2');
  });

  it('computes line and request totals with the currency minor units', async () => {
    await create();
    fillValidRequest();
    page.lines.push(page['createLine']({ description: 'Grease', quantity: '3', unit: 'KG', estimatedUnitPrice: '0.0005' }));
    fixture.detectChanges();

    // 10 × 12.5 = 125.000 TND; 3 × 0.0005 = 0.0015 -> 0.002 TND; total 125.002 TND
    expect(page['lineTotals']()).toEqual([125000n, 2n]);
    expect(page['total']()).toBe(125002n);
  });

  it('keeps unsaved content for this tab and offers to restore it on return', async () => {
    await create();
    fillValidRequest();
    // The copy is written 400 ms after the last change.
    await new Promise((resolve) => setTimeout(resolve, 450));
    const stored = JSON.parse(sessionStorage.getItem('epi.formSnapshot.11.request.new') ?? 'null');
    expect(stored?.value.title).toBe('Bearings for line 2');

    // A new visit to the form (e.g. after signing in again) offers the stored content.
    TestBed.resetTestingModule();
    const keep = sessionStorage.getItem('epi.formSnapshot.11.request.new');
    expect(keep).not.toBeNull();
    await create();
    sessionStorage.setItem('epi.formSnapshot.11.request.new', keep!);
    page['offerRestore']();

    expect(page['restoreOffer']()).not.toBeNull();
    page['restore']();
    expect(page.form.getRawValue().title).toBe('Bearings for line 2');
    expect(page.lines.at(0).getRawValue().estimatedUnitPrice).toBe('12,5');
  });

  it('only offers "enter on behalf" when the company allows it for this user', async () => {
    await create();
    fixture.detectChanges();
    expect(element().querySelector('#request-requesterId')).not.toBeNull();
  });

  it('sends the chosen colleague as requester when entering on behalf', async () => {
    await create();
    fillValidRequest();
    page.form.patchValue({ requesterId: 12 });

    page['saveDraft']();
    const post = http.expectOne('/api/v1/requests');
    expect(post.request.body.requesterId).toBe(12);
    post.flush(saved());
  });

  it('explains when the user has no company to create requests for', async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [RequestFormPage],
      providers: [
        provideRouter([{ path: '**', component: BlankPage }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService({ fallbackLang: 'en' }),
        { provide: AuthService, useValue: { me: signal(ME), isLeavingForSignIn: false } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({}) } } },
        { provide: Dialog, useValue: { open: confirm } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RequestFormPage);
    http.expectOne('/api/v1/requests/form-options').flush({ ...OPTIONS, companies: [] });
    await fixture.whenStable();

    expect(fixture.componentInstance['pageState']()).toBe('cannot-create');
    expect(element().textContent).toContain('requests.form.cannotCreate.title');
  });
});
