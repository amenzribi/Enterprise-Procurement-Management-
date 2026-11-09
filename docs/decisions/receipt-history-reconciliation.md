# Reconciling receipts recorded before the line rules (QA-001, QA-003)

Applies to data created before migration V20. **The migration and the application do not change historical quantities or receipts.** Run these read-only queries, review each result with the purchasing owner, and record any correction as a new, audited business action.

## What changed

- Every request line is now `GOODS` or `SERVICE`. Existing lines became `GOODS`.
- A goods receipt line is tied to a delivered shipment line. Older receipt lines may have no shipment line. They still count against what remains, using the oldest delivered shipment lines first.
- Goods need a confirmed receipt before closure. A service acceptance no longer covers goods lines.

## Find the records

**Service acceptance on an order with goods lines, not yet closed.** These cannot close until the goods are received or the lines are correctly classified.

```sql
SELECT pr.id, pr.number, pr.status, sa.accepted_at
FROM service_acceptance sa
JOIN purchase_request pr ON pr.id = sa.request_id
WHERE pr.status NOT IN ('CLOSED', 'CANCELLED')
  AND EXISTS (SELECT 1 FROM purchase_request_line prl WHERE prl.request_id = pr.id AND prl.line_kind = 'GOODS');
```

**Already closed with goods lines but no confirmed receipt.** The QA-001 path may have been used. Review for audit.

```sql
SELECT pr.id, pr.number
FROM purchase_request pr
WHERE pr.status = 'CLOSED'
  AND EXISTS (SELECT 1 FROM purchase_request_line prl WHERE prl.request_id = pr.id AND prl.line_kind = 'GOODS')
  AND NOT EXISTS (SELECT 1 FROM goods_receipt gr WHERE gr.request_id = pr.id AND gr.status = 'CONFIRMED');
```

**Received more than was delivered on a purchase-order line.** This includes units received before any shipment existed (QA-003).

```sql
SELECT pol.id AS purchase_order_line_id, po.request_id, pol.description, r.received, COALESCE(d.delivered, 0) AS delivered
FROM purchase_order_line pol
JOIN purchase_order po ON po.id = pol.purchase_order_id
CROSS APPLY (SELECT SUM(grl.quantity_received) AS received
             FROM goods_receipt_line grl JOIN goods_receipt gr ON gr.id = grl.goods_receipt_id
             WHERE grl.purchase_order_line_id = pol.id AND gr.status <> 'CANCELLED') r
OUTER APPLY (SELECT SUM(sl.quantity) AS delivered
             FROM shipment_line sl JOIN shipment s ON s.id = sl.shipment_id
             WHERE sl.purchase_order_line_id = pol.id AND s.status <> 'CANCELLED'
               AND EXISTS (SELECT 1 FROM shipment_milestone m
                           WHERE m.shipment_id = s.id AND m.code = 'DELIVERED' AND m.actual_date IS NOT NULL)) d
WHERE r.received > COALESCE(d.delivered, 0);
```

**Receipt lines with no shipment line**, for information. These need no action unless they also appear above.

```sql
SELECT gr.request_id, gr.number, grl.id, grl.purchase_order_line_id, grl.quantity_received
FROM goods_receipt_line grl JOIN goods_receipt gr ON gr.id = grl.goods_receipt_id
WHERE grl.shipment_line_id IS NULL AND gr.status <> 'CANCELLED';
```

## Reconcile

| Finding | Action |
|---|---|
| Open request whose lines are really services | Correct the classification through an audited correction, then record service acceptance. Reclassifying is not available in the application today, so this needs a decision. |
| Open request whose goods did arrive | Record the missing shipment and delivery, then the receipt, with a second person confirming. |
| Open request whose goods did not arrive | Keep it open, or cancel it through the normal process. |
| Receipt above the delivered quantity | Record the missing shipment if the goods arrived. If the receipt is wrong, the purchasing owner agrees a correcting action. Do not edit the receipt row. |
| Closed request with no confirmed receipt | Refer to internal control. Do not reopen or alter it silently. |
