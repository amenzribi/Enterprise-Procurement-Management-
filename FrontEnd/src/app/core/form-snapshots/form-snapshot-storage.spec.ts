import { TestBed } from '@angular/core/testing';
import { FormSnapshotStorage, SNAPSHOT_TTL_MS } from './form-snapshot-storage';

describe('FormSnapshotStorage', () => {
  let storage: FormSnapshotStorage;
  const snapshot = (savedAt: Date) => ({ version: null, savedAt: savedAt.toISOString(), value: { title: 'Pump seals' } });

  beforeEach(() => {
    sessionStorage.clear();
    storage = TestBed.inject(FormSnapshotStorage);
  });

  it('keeps a recent snapshot per user and scope', () => {
    storage.save(1, 'request.new', snapshot(new Date()));
    expect(storage.load(1, 'request.new')?.value).toEqual({ title: 'Pump seals' });
    expect(storage.load(2, 'request.new')).toBeNull();
  });

  it('discards snapshots older than the session lifetime (S1CR-06)', () => {
    storage.save(1, 'request.new', snapshot(new Date(Date.now() - SNAPSHOT_TTL_MS - 1000)));
    expect(storage.load(1, 'request.new')).toBeNull();
    expect(sessionStorage.length).toBe(0);
  });

  it('removes other users\' and expired snapshots when a user signs in, keeping that user\'s recent ones', () => {
    storage.save(1, 'request.new', snapshot(new Date()));
    storage.save(1, 'request.7', snapshot(new Date(Date.now() - SNAPSHOT_TTL_MS - 1000)));
    storage.save(2, 'request.new', snapshot(new Date()));
    sessionStorage.setItem('epi.sidebar.collapsed', 'true');

    storage.prepareFor(1);

    expect(sessionStorage.getItem('epi.formSnapshot.1.request.new')).not.toBeNull();
    expect(sessionStorage.getItem('epi.formSnapshot.1.request.7')).toBeNull();
    expect(sessionStorage.getItem('epi.formSnapshot.2.request.new')).toBeNull();
    expect(sessionStorage.getItem('epi.sidebar.collapsed')).toBe('true');
  });

  it('clears every snapshot of a user on explicit sign-out', () => {
    storage.save(1, 'request.new', snapshot(new Date()));
    storage.save(1, 'request.7', snapshot(new Date()));
    storage.clearUser(1);
    expect(sessionStorage.length).toBe(0);
  });
});
