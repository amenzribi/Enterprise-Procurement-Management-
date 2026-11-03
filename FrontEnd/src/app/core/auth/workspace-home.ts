import { Me, hasPermission } from './auth.models';

/** Post-sign-in / shell home for the current principal (supplier portal vs internal workspace). */
export function workspaceHome(me: Me | null | undefined): string {
  return hasPermission(me ?? null, 'MESSAGING_SUPPLIER') ? '/supplier/messages' : '/overview';
}
