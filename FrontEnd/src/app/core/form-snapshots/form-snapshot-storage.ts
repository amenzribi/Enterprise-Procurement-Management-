import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';

const PREFIX = 'epi.formSnapshot.';
/** Snapshots older than this are discarded (S1CR-06): the longest a session can last. */
export const SNAPSHOT_TTL_MS = 8 * 60 * 60 * 1000;

export interface FormSnapshot<T> {
  /** Server version the user was editing, or null for a form that was never saved. */
  version: number | null;
  savedAt: string;
  value: T;
}

/**
 * Keeps unsaved form content in `sessionStorage` (this tab only, per user) so a session expiry or an accidental
 * reload does not lose work (sprint-1 Q6, S1DR-11). Storage may be unavailable; the form then simply is not kept.
 */
@Injectable({ providedIn: 'root' })
export class FormSnapshotStorage {
  private readonly document = inject(DOCUMENT);

  save<T>(userId: number, scope: string, snapshot: FormSnapshot<T>): void {
    try {
      this.storage()?.setItem(this.key(userId, scope), JSON.stringify(snapshot));
    } catch {
      // Quota exceeded or storage blocked: the snapshot is best effort.
    }
  }

  load<T>(userId: number, scope: string): FormSnapshot<T> | null {
    try {
      const raw = this.storage()?.getItem(this.key(userId, scope));
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw) as FormSnapshot<T>;
      if (!parsed || typeof parsed !== 'object' || !('value' in parsed)) {
        return null;
      }
      if (this.isExpired(parsed)) {
        this.clear(userId, scope);
        return null;
      }
      return parsed;
    } catch {
      return null;
    }
  }

  clear(userId: number, scope: string): void {
    try {
      this.storage()?.removeItem(this.key(userId, scope));
    } catch {
      // Nothing to clear.
    }
  }

  /** Removes every snapshot of a user, e.g. on explicit sign-out on a shared computer. */
  clearUser(userId: number): void {
    this.removeWhere((key) => key.startsWith(`${PREFIX}${userId}.`));
  }

  /**
   * At sign-in: removes snapshots left by other users of this tab and expired ones, so business data of a previous
   * user does not linger in storage (S1CR-06).
   */
  prepareFor(userId: number): void {
    this.removeWhere((key, storage) => {
      if (!key.startsWith(PREFIX)) {
        return false;
      }
      if (!key.startsWith(`${PREFIX}${userId}.`)) {
        return true;
      }
      try {
        return this.isExpired(JSON.parse(storage.getItem(key) ?? 'null') as FormSnapshot<unknown>);
      } catch {
        return true;
      }
    });
  }

  private isExpired(snapshot: FormSnapshot<unknown> | null): boolean {
    const savedAt = snapshot ? Date.parse(snapshot.savedAt) : NaN;
    return !Number.isFinite(savedAt) || Date.now() - savedAt > SNAPSHOT_TTL_MS;
  }

  private removeWhere(predicate: (key: string, storage: Storage) => boolean): void {
    try {
      const storage = this.storage();
      if (!storage) {
        return;
      }
      const keys: string[] = [];
      for (let index = 0; index < storage.length; index++) {
        const key = storage.key(index);
        if (key && predicate(key, storage)) {
          keys.push(key);
        }
      }
      keys.forEach((key) => storage.removeItem(key));
    } catch {
      // Nothing to clear.
    }
  }

  private key(userId: number, scope: string): string {
    return `${PREFIX}${userId}.${scope}`;
  }

  private storage(): Storage | null {
    return this.document.defaultView?.sessionStorage ?? null;
  }
}
