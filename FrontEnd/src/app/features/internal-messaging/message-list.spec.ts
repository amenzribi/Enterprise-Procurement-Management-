import { describe, expect, it } from 'vitest';
import { Message } from './internal-messaging.models';
import { mergeMessages, newClientMessageId, newestId, readMarkerFor } from './message-list';

function message(id: number, mine = false): Message {
  return {
    id,
    conversationId: 1,
    kind: 'TEXT',
    sender: { id: mine ? 1 : 2, displayName: mine ? 'Me' : 'Them', department: null, roles: [] },
    subject: null,
    mine,
    bodyText: 'm' + id,
    request: null,
    clientMessageId: 'c-' + id,
    createdAt: '2026-11-13T10:00:00Z',
  };
}

describe('mergeMessages', () => {
  it('keeps one entry per id when a live event, a resync and a page deliver the same message', () => {
    const merged = mergeMessages([message(1), message(3)], [message(3), message(2), message(3)]);
    expect(merged.map((m) => m.id)).toEqual([1, 2, 3]);
  });

  it('orders older history before newer messages', () => {
    expect(mergeMessages([message(10), message(11)], [message(4), message(5)]).map((m) => m.id)).toEqual([4, 5, 10, 11]);
  });

  it('returns the same list when nothing arrives', () => {
    const list = [message(1)];
    expect(mergeMessages(list, [])).toBe(list);
  });
});

describe('readMarkerFor', () => {
  it('marks the newest displayed message written by someone else', () => {
    expect(readMarkerFor([message(1), message(2), message(3, true)], null)).toBe(2);
  });

  it('never moves the marker back or repeats it', () => {
    expect(readMarkerFor([message(1), message(2)], 2)).toBeNull();
    expect(readMarkerFor([message(1), message(2)], 5)).toBeNull();
  });

  it('sends nothing when only the user wrote', () => {
    expect(readMarkerFor([message(1, true)], null)).toBeNull();
  });
});

describe('helpers', () => {
  it('reports the newest id', () => {
    expect(newestId([])).toBeNull();
    expect(newestId([message(1), message(7)])).toBe(7);
  });

  it('creates client ids the server accepts', () => {
    const id = newClientMessageId();
    expect(id).toMatch(/^[A-Za-z0-9_-]{8,64}$/);
    expect(newClientMessageId()).not.toBe(id);
  });
});
