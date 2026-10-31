/** Roles known to the frontend (sprint-1.md). Unknown roles from the server are shown by their code. */
export const ROLES = [
  'REQUESTER',
  'ENTER_ON_BEHALF',
  'DEPARTMENT_MANAGER',
  'BUDGET_APPROVER',
  'MANAGEMENT_APPROVER',
  'BUYER',
  'PURCHASING_MANAGER',
  'RECEIVER',
  'GROUP_MANAGEMENT',
  'ADMIN',
  'GLOBAL_ADMIN',
  'SUPPLIER_CONTACT',
] as const;
export type Role = (typeof ROLES)[number];

/** Permissions from `GET /me`. The UI shows or hides features by permission, never by role name. */
export const PERMISSIONS = [
  'REQUEST_CREATE',
  'REQUEST_ENTER_ON_BEHALF',
  'REQUEST_VIEW_DEPARTMENT',
  'REQUEST_VIEW_GROUP',
  'APPROVAL_ACT',
  'APPROVAL_ADMIN',
  'PURCHASING_ACT',
  'PURCHASING_MANAGE',
  'RECEIPT_ACT',
  'DASHBOARD_VIEW',
  'ERP_EXPORT',
  'SUPPLIER_READ',
  'SUPPLIER_MANAGE',
  'ORG_ADMIN',
  'IMPORT_RUN',
  'SYSTEM_ADMIN',
  'USER_ADMIN',
  'USER_ADMIN_GLOBAL',
  'MESSAGING_BUYER',
  'MESSAGING_SUPPLIER',
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
  /** Sprint 10: account must change password before other APIs. */
  mustChangePassword?: boolean;
}

export function hasPermission(me: Me | null, permission: Permission): boolean {
  return !!me?.permissions?.includes(permission);
}
