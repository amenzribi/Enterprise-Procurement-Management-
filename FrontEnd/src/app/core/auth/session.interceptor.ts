import {
  HttpContextToken,
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpRequest,
  HttpResponse,
  HttpXsrfTokenExtractor,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, filter, switchMap, take, tap, throwError } from 'rxjs';
import { SKIP_SESSION_HANDLING } from './auth.service';
import { SessionTimerService } from './session-timer.service';

/** Marks the single retry after a rejected CSRF token, so a second rejection is not retried again. */
const CSRF_RETRIED = new HttpContextToken<boolean>(() => false);

/**
 * Every successful API response extends the client-side session timer (the server extends its session on each
 * request). A 401 on any other request means the server session ended: the user is sent to sign-in. A 403
 * `csrf-invalid` (token rotated or missing) is retried once with a freshly issued token.
 */
export const sessionInterceptor: HttpInterceptorFn = (request, next) => {
  const isApi = request.url.startsWith('/api/');
  // Resolved here: inject() is only valid synchronously inside the interceptor, not in the RxJS callbacks.
  // Only services without an HttpClient dependency: injecting AuthService here created a circular dependency with
  // the translation loader (QA1-D-03). The session timer reports the lost session; AuthService reacts to it.
  const timer = inject(SessionTimerService);
  const tokens = inject(HttpXsrfTokenExtractor);

  return next(request).pipe(
    tap((event) => {
      if (isApi && event instanceof HttpResponse) {
        timer.touch();
      }
    }),
    catchError((error: unknown) => {
      if (!isApi || !(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      if (error.status === 403 && error.error?.code === 'csrf-invalid' && !request.context.get(CSRF_RETRIED)) {
        return next(new HttpRequest('GET', '/api/v1/auth/csrf')).pipe(
          filter((event) => event instanceof HttpResponse),
          take(1),
          switchMap(() => {
            const token = tokens.getToken();
            const retry = request.clone({
              context: request.context.set(CSRF_RETRIED, true),
              setHeaders: token ? { 'X-XSRF-TOKEN': token } : {},
            });
            return next(retry);
          }),
        );
      }
      if (error.status === 401 && !request.context.get(SKIP_SESSION_HANDLING)) {
        timer.serverRejected();
      }
      return throwError(() => error);
    }),
  );
};
