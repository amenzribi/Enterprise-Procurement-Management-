-- Indexes on foreign-key columns that the application filters by (audit DB-2, Sprint 1).
--
-- The audit found 117 foreign keys without a supporting index and asked to index only those backed by query
-- evidence. Each index below names the statement that needs it. The other columns of the audit's list
-- (approval_task_event.task_id and .round_id, award.rfq_id and .quotation_id, the *_line.request_line_id columns,
-- supplier_invoice.purchase_order_id, ...) are not used as a predicate by any statement today: those tables are
-- read by request_id or by their parent key, which are indexed. They are left alone until the load test
-- (docs/audit/04) shows a need, because every index slows writes.
--
-- Additive only: no column, constraint or data changes. Rollback: DROP INDEX for the four names below.

-- approval/ApprovalStore: tokens of a request's tasks are revoked on every decision
--   UPDATE approval_link_token ... WHERE used_at IS NULL AND task_id IN (SELECT id FROM approval_task WHERE request_id = ?)
CREATE INDEX ix_approval_link_token_task ON approval_link_token (task_id) INCLUDE (used_at);

-- receipt/ReceiptStore: quantity already received against a shipment line, on every receipt
--   ... FROM goods_receipt_line grl JOIN goods_receipt gr ... WHERE grl.shipment_line_id = sl.id
CREATE INDEX ix_goods_receipt_line_shipment_line ON goods_receipt_line (shipment_line_id)
    INCLUDE (goods_receipt_id, quantity_received);

-- receipt/ReceiptStore: quantity received against an order line (two statements), on every receipt
--   ... WHERE grl.purchase_order_line_id = ? ...
CREATE INDEX ix_goods_receipt_line_po_line ON goods_receipt_line (purchase_order_line_id)
    INCLUDE (goods_receipt_id, shipment_line_id, quantity_received);

-- logistics/LogisticsStore and receipt/ReceiptStore: quantity shipped against an order line
--   ... FROM shipment_line sl JOIN shipment s ... WHERE sl.purchase_order_line_id = ?
CREATE INDEX ix_shipment_line_po_line ON shipment_line (purchase_order_line_id) INCLUDE (shipment_id, quantity);
