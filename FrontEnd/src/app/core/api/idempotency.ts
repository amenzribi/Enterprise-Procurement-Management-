import { HttpHeaders } from '@angular/common/http';

/**
 * A fresh `Idempotency-Key` for one user action (S2-AC-13). The key is created when the user confirms the action and
 * reused for retries of that same action, so a double tap or a network retry is answered with the first result
 * instead of acting twice.
 */
export function newIdempotencyKey(): string {
  const crypto = globalThis.crypto;
  if (crypto?.randomUUID) {
    return crypto.randomUUID();
  }
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
}

export function idempotencyHeaders(key: string): { headers: HttpHeaders } {
  return { headers: new HttpHeaders({ 'Idempotency-Key': key }) };
}
