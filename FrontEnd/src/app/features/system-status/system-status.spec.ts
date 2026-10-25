import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideMissingTranslationHandler, provideTranslateService } from '@ngx-translate/core';
import { FallbackMissingTranslationHandler } from '../../core/i18n/fallback-missing-translation-handler';
import { HealthReport, SystemStatusResponse } from '../../core/system/system-status.service';
import { SystemStatusPage } from './system-status';

describe('SystemStatusPage', () => {
  let fixture: ComponentFixture<SystemStatusPage>;
  let http: HttpTestingController;

  const status = (database: Partial<NonNullable<SystemStatusResponse['database']>> = {}): SystemStatusResponse => ({
    application: 'procurement',
    version: '0.1.0',
    serverTime: '2026-10-24T09:30:00Z',
    database: { status: 'UP', productVersion: '17.0.1000.7', schemaVersion: '3', ...database },
  });
  const health: HealthReport = { status: 'UP', components: { db: { status: 'UP' }, ping: { status: 'UP' } } };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [SystemStatusPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // No loader: every key is "missing", so texts render as keys and fallbacks are visible.
        provideTranslateService({
          missingTranslationHandler: provideMissingTranslationHandler(FallbackMissingTranslationHandler),
        }),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SystemStatusPage);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const query = (selector: string) => (fixture.nativeElement as HTMLElement).querySelector(selector);
  const queryAll = (selector: string) => (fixture.nativeElement as HTMLElement).querySelectorAll(selector);

  async function respond(statusBody: SystemStatusResponse | null, healthBody: HealthReport | null = health) {
    const statusRequest = http.expectOne('/api/v1/system/status');
    const healthRequest = http.expectOne('/actuator/health');
    if (statusBody) {
      statusRequest.flush(statusBody);
    } else {
      statusRequest.flush('', { status: 504, statusText: 'Gateway Timeout' });
    }
    if (healthRequest.cancelled) {
      // A failed status call cancels the parallel health call.
    } else if (healthBody) {
      healthRequest.flush(healthBody);
    } else {
      healthRequest.flush('', { status: 504, statusText: 'Gateway Timeout' });
    }
    await fixture.whenStable();
  }

  it('shows a loading state while the check runs', () => {
    expect(query('.route-panel')?.getAttribute('aria-busy')).toBe('true');
    expect(text()).toContain('systemStatus.summary.checking');
    expect(query('app-skeleton')).not.toBeNull();

    http.expectOne('/api/v1/system/status');
    http.expectOne('/actuator/health');
  });

  it('keeps skeletons and notes out of description lists', async () => {
    expect(query('dl app-skeleton')).toBeNull();
    await respond(null, null);
    expect(query('dl p')).toBeNull();
  });

  it('shows every hop connected with the reported details', async () => {
    await respond(status());

    expect(text()).toContain('systemStatus.summary.allConnected');
    expect(text()).toContain('17.0.1000.7');
    expect(query('.route-panel')?.getAttribute('aria-busy')).toBe('false');
    expect(queryAll('.health-table tbody tr').length).toBe(2);
    expect(query('app-error-state')).toBeNull();
  });

  it('reports a database outage without treating it as a server error', async () => {
    await respond(status({ status: 'DOWN', productVersion: null, schemaVersion: null }), {
      status: 'DOWN',
      components: { db: { status: 'DOWN' } },
    });

    expect(text()).toContain('systemStatus.summary.databaseDown');
    expect(query('.hop.is-down')).not.toBeNull();
    expect(query('app-error-state')).toBeNull();
    expect(query('app-empty-state')).toBeNull();
  });

  it('shows an empty state when the server reports that no schema is applied', async () => {
    await respond(status({ schemaVersion: null }));

    expect(query('app-empty-state')?.textContent).toContain('systemStatus.empty.title');
    expect(text()).toContain('systemStatus.facts.none');
  });

  it('shows "not available" for missing optional fields instead of blanks or a wrong diagnosis', async () => {
    await respond({ application: 'procurement', database: { status: 'UP' } });

    const values = Array.from(queryAll('dd')).map((dd) => dd.textContent?.trim());
    expect(values.filter((value) => !value)).toEqual([]);
    expect(values.filter((value) => value === 'systemStatus.facts.notAvailable').length).toBe(3);
    expect(query('app-empty-state')).toBeNull();
  });

  it('treats a missing database status as not reported', async () => {
    await respond({ version: '0.1.0' });

    expect(text()).toContain('systemStatus.summary.databaseNotReported');
    expect(query('.hop.is-unknown')).not.toBeNull();
  });

  it('labels unknown health checks with their technical name', async () => {
    await respond(status(), { status: 'UP', components: { db: { status: 'UP' }, mailServer: { status: 'UP' } } });

    const labels = Array.from(queryAll('.health-table tbody td:first-child')).map((td) => td.textContent?.trim());
    expect(labels).toContain('mailServer');
  });

  it('shows an error state with a working retry when the server is unreachable, announced once', async () => {
    await respond(null, null);

    expect(text()).toContain('systemStatus.summary.serverUnreachable');
    const errorState = query('app-error-state');
    expect(errorState?.getAttribute('role')).toBeNull();
    expect(queryAll('[aria-live], [role="alert"]').length).toBe(1);

    (errorState?.querySelector('button') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(text()).toContain('systemStatus.summary.checking');

    await respond(status());
    expect(text()).toContain('systemStatus.summary.allConnected');
  });

  it('still succeeds when only the health endpoint fails', async () => {
    await respond(status(), null);

    expect(text()).toContain('systemStatus.summary.allConnected');
    expect(text()).toContain('systemStatus.health.unavailable');
  });
});
