import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { idempotencyHeaders } from '../../core/api/idempotency';
import { SKIP_SESSION_HANDLING } from '../../core/auth/auth.service';
import { Page, PersonOption } from '../requests/request.models';
import {
  Absence,
  AbsenceInput,
  ApprovalAction,
  ApprovalPreview,
  ApprovalRule,
  ApprovalRuleInput,
  AdminCompany,
  AdminUser,
  ApprovalView,
  ExchangeRate,
  ExchangeRateInput,
  InboxItem,
  InboxQuery,
  LinkResult,
  LinkSummary,
} from './approval.models';

const API = '/api/v1';

/** Path segment of each approver action (`POST /approvals/tasks/{id}/<segment>`). */
const ACTION_PATHS: Record<Exclude<ApprovalAction, 'DELEGATE'>, string> = {
  APPROVE: 'approve',
  REJECT: 'reject',
  RETURN: 'return',
  QUESTION: 'question',
};

/** Body of every task action: `{version, comment?, reason?, question?, toUserId?}`. */
export interface DecisionInput {
  version: number;
  comment?: string | null;
  reason?: string | null;
  question?: string | null;
  toUserId?: number | null;
}

@Injectable({ providedIn: 'root' })
export class ApprovalsApi {
  private readonly http = inject(HttpClient);

  inbox(query: InboxQuery): Observable<Page<InboxItem>> {
    const params = new HttpParams()
      .set('status', query.view === 'pending' ? 'PENDING' : 'DECIDED')
      .set('sort', `${query.sort},${query.direction}`)
      .set('page', query.page)
      .set('size', query.size);
    return this.http.get<Page<InboxItem>>(`${API}/approvals/inbox`, {
      params: query.q ? params.set('q', query.q) : params,
    });
  }

  inboxCount(): Observable<{ pending: number; overdue: number }> {
    return this.http
      .get<{ pending?: number; overdue?: number }>(`${API}/approvals/inbox/count`)
      .pipe(map((body) => ({ pending: body.pending ?? 0, overdue: body.overdue ?? 0 })));
  }

  approval(requestId: number): Observable<ApprovalView> {
    return this.http.get<ApprovalView>(`${API}/requests/${requestId}/approval`);
  }

  preview(requestId: number): Observable<ApprovalPreview> {
    return this.http.get<ApprovalPreview>(`${API}/requests/${requestId}/approval-preview`);
  }

  /** Approve, reject, return or ask a question. The same idempotency key must be reused for a retry. */
  decide(taskId: number, action: Exclude<ApprovalAction, 'DELEGATE'>, input: DecisionInput, key: string): Observable<ApprovalView> {
    return this.http.post<ApprovalView>(`${API}/approvals/tasks/${taskId}/${ACTION_PATHS[action]}`, input, idempotencyHeaders(key));
  }

  delegate(taskId: number, toUserId: number, reason: string | null, version: number, key: string): Observable<ApprovalView> {
    return this.http.post<ApprovalView>(
      `${API}/approvals/tasks/${taskId}/delegate`,
      { version, reason, toUserId },
      idempotencyHeaders(key),
    );
  }

  delegates(taskId: number): Observable<PersonOption[]> {
    return this.http.get<PersonOption[]>(`${API}/approvals/tasks/${taskId}/delegates`);
  }

  answer(requestId: number, taskId: number, answer: string, key: string): Observable<ApprovalView> {
    return this.http.post<ApprovalView>(`${API}/requests/${requestId}/answers`, { taskId, answer }, idempotencyHeaders(key));
  }

  // One-tap e-mail approval (S2-AC-07, S2DR-07). The token travels in the body, never in a URL, so it cannot leak
  // through server logs or the Referer header. A 401 here must not send the visitor to sign-in.
  linkSummary(token: string): Observable<LinkSummary> {
    return this.http.post<LinkSummary>(`${API}/approval-links/summary`, { token }, { context: skipSession() });
  }

  linkApprove(token: string, key: string): Observable<LinkResult> {
    return this.http.post<LinkResult>(
      `${API}/approval-links/approve`,
      { token },
      { ...idempotencyHeaders(key), context: skipSession() },
    );
  }

  myAbsences(): Observable<Absence[]> {
    return this.http.get<Absence[]>(`${API}/me/absences`);
  }

  absenceDelegates(): Observable<PersonOption[]> {
    return this.http.get<PersonOption[]>(`${API}/me/absences/delegates`);
  }

  createAbsence(input: AbsenceInput, key: string): Observable<Absence> {
    return this.http.post<Absence>(`${API}/me/absences`, input, idempotencyHeaders(key));
  }

  deleteAbsence(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/me/absences/${id}`);
  }

  rules(companyId?: number): Observable<ApprovalRule[]> {
    const params = companyId ? new HttpParams().set('companyId', companyId) : undefined;
    return this.http.get<ApprovalRule[]>(`${API}/admin/approval-rules`, { params });
  }

  ruleVersions(id: number): Observable<ApprovalRule[]> {
    return this.http.get<ApprovalRule[]>(`${API}/admin/approval-rules/${id}/versions`);
  }

  createRule(input: ApprovalRuleInput): Observable<ApprovalRule> {
    return this.http.post<ApprovalRule>(`${API}/admin/approval-rules`, input);
  }

  /** Saves a new version of a rule; the active version stays in force until the new one is activated. */
  updateRule(id: number, input: ApprovalRuleInput): Observable<ApprovalRule> {
    return this.http.put<ApprovalRule>(`${API}/admin/approval-rules/${id}`, input);
  }

  activateRule(id: number): Observable<ApprovalRule> {
    return this.http.post<ApprovalRule>(`${API}/admin/approval-rules/${id}/activate`, {});
  }

  retireRule(id: number): Observable<ApprovalRule> {
    return this.http.post<ApprovalRule>(`${API}/admin/approval-rules/${id}/retire`, {});
  }

  /** Companies an administrator manages, for the rule editor. */
  adminCompanies(): Observable<AdminCompany[]> {
    return this.http.get<AdminCompany[]>(`${API}/admin/companies`);
  }

  /** Users of a company, optionally filtered by role and text, for the named-approver picker. */
  adminUsers(companyId: number, q?: string, role?: string): Observable<AdminUser[]> {
    let params = new HttpParams().set('companyId', companyId);
    if (q) {
      params = params.set('q', q);
    }
    if (role) {
      params = params.set('role', role);
    }
    return this.http.get<AdminUser[]>(`${API}/admin/users`, { params });
  }

  exchangeRates(): Observable<ExchangeRate[]> {
    return this.http
      .get<ExchangeRate[] | Page<ExchangeRate>>(`${API}/admin/exchange-rates`)
      .pipe(map((body) => (Array.isArray(body) ? body : body.items)));
  }

  addExchangeRate(input: ExchangeRateInput): Observable<ExchangeRate> {
    return this.http.post<ExchangeRate>(`${API}/admin/exchange-rates`, input);
  }
}

function skipSession(): HttpContext {
  return new HttpContext().set(SKIP_SESSION_HANDLING, true);
}
