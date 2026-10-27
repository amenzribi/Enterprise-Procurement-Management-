/** Roles known to the frontend (sprint-1.md). Unknown roles from the server are shown by their code. */
export const ROLES = [
  'REQUESTER',
  'ENTER_ON_BEHALF',
  'DEPARTMENT_MANAGER',
  'BUYER',
  'PURCHASING_MANAGER',
  'GROUP_MANAGEMENT',
  'ADMIN',
] as const;
export type Role = (typeof ROLES)[number];

/** Permissions from `GET /me`. The UI shows or hides features by permission, never by role name. */
export const PERMISSIONS = [
  'REQUEST_CREATE',
  'REQUEST_ENTER_ON_BEHALF',
  'REQUEST_VIEW_DEPARTMENT',
  'REQUEST_VIEW_GROUP',
  'SYSTEM_ADMIN',
] as const;
export type Permission = (typeof PERMISSIONS)[number];

export interface Reference {
  id: number;
  code?: string;
  name: string;
}

/** `GET /api/v1/me`. */
export interface Me {
  id: number;
  email: string;
  displayName: string;
  company: Reference;
  department?: Reference | null;
  roles: string[];
  permissions?: string[];
  preferredLanguage?: string | null;
  /** When the server session expires if idle from now: min(last access + idle timeout, sign-in + absolute timeout). */
  sessionExpiresAt?: string | null;
  idleTimeoutSeconds?: number | null;
  absoluteTimeoutSeconds?: number | null;
  /** True for demonstration accounts (available only with the demo profile). */
  demo?: boolean;
}

export function hasPermission(me: Me | null, permission: Permission): boolean {
  return !!me?.permissions?.includes(permission);
}
