import { IconName } from '../shared/ui/icon';

/**
 * Navigation grouped by the procurement journey. This is the single source of the planned-sprint numbers shown
 * anywhere in the interface (sidebar, overview, planned pages); keep it aligned with docs/product/roadmap.md.
 * Modules that are not built yet open an honest "planned" page - never a mock screen.
 */
export interface NavItem {
  /** Translation key suffix under `nav.items` and route parameter for planned modules. */
  key: string;
  icon: IconName;
  /** Route of an available module. */
  route?: string;
  /** Target sprint of a module that is not available yet. */
  plannedSprint?: number;
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
      { key: 'systemStatus', icon: 'activity', route: '/system-status' },
    ],
  },
  {
    key: 'requestToOrder',
    items: [
      { key: 'requests', icon: 'file-plus', plannedSprint: 1 },
      { key: 'approvals', icon: 'circle-check', plannedSprint: 2 },
      { key: 'purchasingInbox', icon: 'inbox', plannedSprint: 3 },
      { key: 'sourcing', icon: 'scale', plannedSprint: 4 },
    ],
  },
  {
    key: 'orderToReceipt',
    items: [
      { key: 'purchaseOrders', icon: 'file-text', plannedSprint: 5 },
      { key: 'logistics', icon: 'truck', plannedSprint: 5 },
      { key: 'receipts', icon: 'package-check', plannedSprint: 6 },
    ],
  },
  {
    key: 'management',
    items: [{ key: 'dashboards', icon: 'dashboard', plannedSprint: 7 }],
  },
  {
    key: 'administration',
    items: [
      { key: 'organization', icon: 'building', plannedSprint: 1 },
      { key: 'suppliers', icon: 'users', plannedSprint: 4 },
    ],
  },
];

/**
 * The purchasing journey in order, each stage pointing at the navigation item that delivers it, so stage sprints
 * are always read from {@link NAVIGATION}.
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
