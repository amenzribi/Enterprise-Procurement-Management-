import { Message } from './internal-messaging.models';

/** Upper bound of the composer, the same as the server's (InternalMessagingService.MAX_BODY). */
export const MAX_BODY = 4000;

/**
 * Merges messages into a list: one entry per id, ordered by id (the server's order: stored time, then a unique
 * sequence). Live events, reconnect resyncs and "load older" can all deliver the same message; it appears once.
 */
export function mergeMessages(existing: readonly Message[], incoming: readonly Message[]): Message[] {
  if (incoming.length === 0) {
    return existing as Message[];
  }
  const byId = new Map<number, Message>();
  for (const m of existing) {
    byId.set(m.id, m);
  }
  for (const m of incoming) {
    byId.set(m.id, m);
  }
  return [...byId.values()].sort((a, b) => a.id - b.id);
}

/** The highest id in the list, or null. Used as the resync cursor and as the read marker. */
export function newestId(messages: readonly Message[]): number | null {
  return messages.length === 0 ? null : messages[messages.length - 1].id;
}

/**
 * The read marker to send for what is on screen: the newest displayed message written by someone else, but only if
 * it is newer than the marker the server already has. Null means "nothing to send".
 */
export function readMarkerFor(displayed: readonly Message[], current: number | null): number | null {
  for (let i = displayed.length - 1; i >= 0; i--) {
    const m = displayed[i];
    if (!m.mine) {
      return current === null || m.id > current ? m.id : null;
    }
  }
  return null;
}

/** A client message id: random, URL-safe, within the server's 8 to 64 characters. */
export function newClientMessageId(): string {
  const cryptoApi = globalThis.crypto;
  if (cryptoApi?.randomUUID) {
    return 'c-' + cryptoApi.randomUUID();
  }
  return 'c-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 12);
}
