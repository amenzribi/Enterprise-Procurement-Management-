import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { AuditCenterPage } from './audit-center';
import { dayToInstant, toParams } from './audit-center.api';

describe('Audit Center filters', () => {
  it('turns calendar days into UTC instants, the end day included', () => {
    expect(dayToInstant('2026-11-18')).toBe('2026-11-18T00:00:00.000Z');
    expect(dayToInstant('2026-11-18', true)).toBe('2026-11-19T00:00:00.000Z');
    expect(dayToInstant('')).toBeUndefined();
    expect(dayToInstant('18/11/2026')).toBeUndefined();
  });

  it('sends only the filters that are set', () => {
    const params = toParams({ action: 'USER_ACTIVATED', companyId: null, entityId: '  ', actorUserId: 7 });
    expect(params.toString()).toBe('action=USER_ACTIVATED&actorUserId=7');
  });
});

describe('AuditCenterPage', () => {
  let fixture: ComponentFixture<AuditCenterPage>;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [AuditCenterPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService({ fallbackLang: 'en' })],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AuditCenterPage);
    await fixture.whenStable();
  });

  function settleStartupCalls(): void {
    http.match('/api/v1/admin/companies').forEach((r) => r.flush([]));
    http.match('/api/v1/admin/users').forEach((r) => r.flush([]));
    http.match('/api/v1/admin/audit/verifications/current').forEach((r) =>
      r.flush({ outcome: 'NONE', startedBy: null, startedAt: null, finishedAt: null, chainsTotal: 0, chainsChecked: 0, eventsChecked: 0, invalidChain: null, firstInvalidEventId: null }),
    );
  }

  it('loads the newest events at start, then the next page with the cursor', async () => {
    settleStartupCalls();
    const first = http.expectOne((r) => r.url === '/api/v1/admin/audit/events' && !r.params.has('cursor'));
    expect(first.request.params.get('size')).toBe('50');
    first.flush({
      items: [{ id: 9, chainKey: 'global', chainSeq: 3, occurredAt: '2026-11-18T10:00:00Z', actorUserId: 1, actorLabel: 'admin', companyId: null, companyName: null, entityType: 'AUDIT_TRAIL', entityId: 'audit', action: 'AUDIT_SEARCHED', fromStatus: null, toStatus: null, comment: null, payloadJson: '{}' }],
      nextCursor: '1763460000000.9',
    });
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('tbody tr').length).toBe(1);

    const more = Array.from(element.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.includes('auditCenter.more'))!;
    more.click();
    const next = http.expectOne((r) => r.url === '/api/v1/admin/audit/events' && r.params.get('cursor') === '1763460000000.9');
    next.flush({ items: [], nextCursor: null });
    await fixture.whenStable();
    http.verify();
  });

  it('refuses a malformed action code without calling the server', async () => {
    settleStartupCalls();
    http.expectOne((r) => r.url === '/api/v1/admin/audit/events').flush({ items: [], nextCursor: null });
    await fixture.whenStable();
    const page = fixture.componentInstance;
    page['form'].controls.action.setValue('drop table');
    expect(page.filterFromForm()).toBeNull();
    page['search']();
    await fixture.whenStable();
    http.expectNone('/api/v1/admin/audit/events');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('auditCenter.errors.code');
  });
});
