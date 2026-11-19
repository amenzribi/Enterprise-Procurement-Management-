import { Permission } from '../core/auth/auth.models';
import { IconName } from '../shared/ui/icon';

/**
 * Navigation grouped by the procurement journey. Planned-sprint markers remain only for modules not yet built;
 * keep them aligned with docs/product/roadmap.md. Delivered Core stages must use a real route (never a stale
 * planned page). Sourcing / PO / logistics / receipt work continues on the request dossier.
 */
export interface NavItem {
  /** Translation key suffix under `nav.items` and route parameter for planned modules. */
  key: string;
  icon: IconName;
  /** Route of an available module. */
  route?: string;
  /** Target sprint of a module that is not available yet. */
  plannedSprint?: number;
  /** Shown only to users holding this permission (the server enforces access regardless). */
  permission?: Permission;
  /** Hidden from users holding this permission (internal pages are not shown to supplier contacts). */
  hiddenFor?: Permission;
  /** Shows the number of decisions waiting for the user (S2-AC-08), or of unread messages. */
  badge?: 'approvals' | 'purchasing' | 'messages';
}

export interface NavGroup {
  key: string;
  items: NavItem[];
}

export const NAVIGATION: NavGroup[] = [
  {
    key: 'workspace',
    items: [
      { key: 'overview', icon: 'house', route: '/overview' },
      { key: 'conversations', icon: 'messages', route: '/conversations', badge: 'messages', hiddenFor: 'MESSAGING_SUPPLIER' },
      { key: 'systemStatus', icon: 'activity', route: '/system-status' },
    ],
  },
  {
    key: 'requestToOrder',
    items: [
      { key: 'requests', icon: 'file-plus', route: '/requests' },
      { key: 'approvals', icon: 'circle-check', route: '/approvals', badge: 'approvals' },
      { key: 'purchasingInbox', icon: 'inbox', route: '/purchasing', permission: 'PURCHASING_ACT', badge: 'purchasing' },
      { key: 'messages', icon: 'inbox', route: '/messages', permission: 'MESSAGING_BUYER' },
      { key: 'assistant', icon: 'activity', route: '/assistant', permission: 'ASSISTANT_USE' },
      { key: 'supplierMessages', icon: 'inbox', route: '/supplier/messages', permission: 'MESSAGING_SUPPLIER' },
      // Sourcing / PO / logistics / receipt actions live on the request dossier (S4–S7).
      { key: 'sourcing', icon: 'scale', route: '/requests', permission: 'PURCHASING_ACT' },
    ],
  },
  {
    key: 'orderToReceipt',
    items: [
      { key: 'purchaseOrders', icon: 'file-text', route: '/requests', permission: 'PURCHASING_ACT' },
      { key: 'logistics', icon: 'truck', route: '/logistics', permission: 'PURCHASING_ACT' },
      { key: 'receipts', icon: 'package-check', route: '/requests', permission: 'RECEIPT_ACT' },
    ],
  },
  {
    key: 'management',
    items: [{ key: 'dashboards', icon: 'dashboard', route: '/dashboards', permission: 'DASHBOARD_VIEW' }],
  },
  {
    key: 'administration',
    items: [
      {
        key: 'requestAdmin',
        icon: 'file-text',
        route: '/admin/requests',
        permission: 'REQUEST_DRAFT_ADMIN',
      },
      { key: 'approvalRules', icon: 'settings', route: '/admin/approval-rules', permission: 'SYSTEM_ADMIN' },
      { key: 'exchangeRates', icon: 'coins', route: '/admin/exchange-rates', permission: 'SYSTEM_ADMIN' },
      { key: 'organization', icon: 'building', route: '/admin/organization', permission: 'ORG_ADMIN' },
      { key: 'users', icon: 'users', route: '/admin/users', permission: 'USER_ADMIN' },
      { key: 'auditCenter', icon: 'history', route: '/admin/audit', permission: 'AUDIT_VIEW' },
      { key: 'suppliers', icon: 'users', route: '/suppliers', permission: 'SUPPLIER_READ' },
      { key: 'import', icon: 'upload', route: '/admin/import', permission: 'IMPORT_RUN' },
      { key: 'changePassword', icon: 'settings', route: '/account/password' },
    ],
  },
];

/**
 * The purchasing journey in order, each stage pointing at the navigation item that delivers it, so stage sprints
 * are always read from {@link NAVIGATION}. A stage whose item has a route is available now.
 */
export const JOURNEY_STAGES = [
  { key: 'request', navItem: 'requests' },
  { key: 'approval', navItem: 'approvals' },
  { key: 'purchasing', navItem: 'purchasingInbox' },
  { key: 'sourcing', navItem: 'sourcing' },
  { key: 'order', navItem: 'purchaseOrders' },
  { key: 'logistics', navItem: 'logistics' },
  { key: 'receipt', navItem: 'receipts' },
] as const;

const ALL_ITEMS = NAVIGATION.flatMap((group) => group.items);

export function findNavItem(key: string | null): NavItem | undefined {
  return ALL_ITEMS.find((item) => item.key === key);
}

export function findPlannedItem(key: string | null): NavItem | undefined {
  const item = findNavItem(key);
  return item?.plannedSprint ? item : undefined;
}
