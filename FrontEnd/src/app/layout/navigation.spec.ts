import { JOURNEY_STAGES, NAVIGATION, findNavItem } from './navigation';

describe('navigation roadmap', () => {
  const sprintOf = (key: string) => findNavItem(key)?.plannedSprint;

  it('matches the roadmap (docs/product/roadmap.md)', () => {
    expect({
      requests: sprintOf('requests'),
      organization: sprintOf('organization'),
      approvals: sprintOf('approvals'),
      purchasingInbox: sprintOf('purchasingInbox'),
      sourcing: sprintOf('sourcing'),
      suppliers: sprintOf('suppliers'),
      purchaseOrders: sprintOf('purchaseOrders'),
      logistics: sprintOf('logistics'),
      receipts: sprintOf('receipts'),
      dashboards: sprintOf('dashboards'),
    }).toEqual({
      requests: 1,
      organization: 1,
      approvals: 2,
      purchasingInbox: 3,
      sourcing: 4,
      suppliers: 4,
      purchaseOrders: 5,
      logistics: 5,
      receipts: 6,
      dashboards: 7,
    });
  });

  it('points every journey stage at a planned navigation item, in sprint order', () => {
    const sprints = JOURNEY_STAGES.map((stage) => sprintOf(stage.navItem));

    expect(sprints.every((sprint) => typeof sprint === 'number')).toBe(true);
    expect([...sprints].sort((a, b) => a! - b!)).toEqual(sprints);
  });

  it('uses unique item keys', () => {
    const keys = NAVIGATION.flatMap((group) => group.items.map((item) => item.key));
    expect(new Set(keys).size).toBe(keys.length);
  });
});
