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
    expect({
      sourcing: sprintOf('sourcing'),
      purchaseOrders: sprintOf('purchaseOrders'),
      logistics: sprintOf('logistics'),
      receipts: sprintOf('receipts'),
      dashboards: sprintOf('dashboards'),
    }).toEqual({
      sourcing: 4,
      purchaseOrders: 5,
      logistics: 6,
      receipts: 7,
      dashboards: 8,
    });
  });

  it('points every journey stage at an available or planned navigation item, in sprint order', () => {
    // Available modules count as sprint 0 so they come first.
    const sprints = JOURNEY_STAGES.map((stage) => {
      const item = findNavItem(stage.navItem);
      return item?.route ? 0 : item?.plannedSprint;
    });

    expect(sprints.every((sprint) => typeof sprint === 'number')).toBe(true);
    expect([...sprints].sort((a, b) => a! - b!)).toEqual(sprints);
  });

  it('uses unique item keys', () => {
    const keys = NAVIGATION.flatMap((group) => group.items.map((item) => item.key));
    expect(new Set(keys).size).toBe(keys.length);
  });
});
