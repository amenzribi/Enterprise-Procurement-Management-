import { JOURNEY_STAGES, NAVIGATION, findNavItem } from './navigation';

describe('navigation roadmap', () => {
  const sprintOf = (key: string) => findNavItem(key)?.plannedSprint;

  it('opens purchase requests as an available module from Sprint 1', () => {
    expect(findNavItem('requests')?.route).toBe('/requests');
    expect(sprintOf('requests')).toBeUndefined();
  });

  it('opens approvals from Sprint 2, and the approval administration for administrators only', () => {
    expect(findNavItem('approvals')?.route).toBe('/approvals');
    expect(findNavItem('approvals')?.badge).toBe('approvals');
    expect(findNavItem('approvalRules')?.permission).toBe('SYSTEM_ADMIN');
    expect(findNavItem('exchangeRates')?.permission).toBe('SYSTEM_ADMIN');
  });

  it('matches the roadmap realigned to the Solution Guide v3.0 (docs/product/roadmap.md)', () => {
    expect(findNavItem('purchasingInbox')?.route).toBe('/purchasing');
    expect(findNavItem('organization')?.route).toBe('/admin/organization');
    expect(findNavItem('suppliers')?.route).toBe('/suppliers');
    expect(findNavItem('import')?.route).toBe('/admin/import');
    expect(findNavItem('dashboards')?.route).toBe('/dashboards');
    expect(findNavItem('dashboards')?.permission).toBe('DASHBOARD_VIEW');
    // S4–S7 dossier actions are delivered; nav opens the request list (work continues on the detail).
    expect(sprintOf('sourcing')).toBeUndefined();
    expect(findNavItem('sourcing')?.route).toBe('/requests');
    expect(sprintOf('purchaseOrders')).toBeUndefined();
    expect(findNavItem('purchaseOrders')?.route).toBe('/requests');
    expect(sprintOf('logistics')).toBeUndefined();
    expect(findNavItem('logistics')?.route).toBe('/logistics');
    expect(sprintOf('receipts')).toBeUndefined();
    expect(findNavItem('receipts')?.route).toBe('/requests');
  });

  it('marks every Core journey stage as available (no planned-sprint placeholders left)', () => {
    for (const stage of JOURNEY_STAGES) {
      const item = findNavItem(stage.navItem);
      expect(item?.route, stage.key).toBeTruthy();
      expect(item?.plannedSprint, stage.key).toBeUndefined();
    }
  });

  it('uses unique item keys', () => {
    const keys = NAVIGATION.flatMap((group) => group.items.map((item) => item.key));
    expect(new Set(keys).size).toBe(keys.length);
  });
});
