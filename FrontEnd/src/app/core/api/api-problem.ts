import { HttpErrorResponse } from '@angular/common/http';

/** Field error inside a `validation-failed` problem, e.g. `{ field: 'lines[2].quantity', code: 'quantity-positive' }`. */
export interface FieldError {
  field: string;
  code: string;
  /** Values for the message, e.g. `{ max: 200 }` for `too-long`. */
  params?: Record<string, unknown>;
}

/** RFC 9457 problem detail as returned by the API. `code` is a stable, translatable identifier. */
export interface ApiProblem {
  status: number;
  code: string;
  errors: FieldError[];
}

/**
 * Reads the API's problem detail from an HTTP error. Network failures (status 0) and non-JSON bodies become
 * `network-error` / `unexpected-error` so callers always get a translatable code.
 */
export function toApiProblem(error: unknown): ApiProblem {
  if (!(error instanceof HttpErrorResponse)) {
    return { status: 0, code: 'unexpected-error', errors: [] };
  }
  if (error.status === 0) {
    return { status: 0, code: 'network-error', errors: [] };
  }
  const body = typeof error.error === 'object' && error.error !== null ? (error.error as Record<string, unknown>) : {};
  const code = typeof body['code'] === 'string' ? body['code'] : fallbackCode(error.status);
  const errors = Array.isArray(body['errors'])
    ? (body['errors'] as unknown[]).filter(isFieldError)
    : [];
  return { status: error.status, code, errors };
}

function isFieldError(value: unknown): value is FieldError {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as FieldError).field === 'string' &&
    typeof (value as FieldError).code === 'string'
  );
}

function fallbackCode(status: number): string {
  switch (status) {
    case 401:
      return 'unauthenticated';
    case 403:
      return 'forbidden';
    case 404:
      return 'not-found';
    case 409:
      return 'conflict';
    case 422:
      return 'validation-failed';
    default:
      return status >= 500 ? 'internal-error' : 'unexpected-error';
  }
}
