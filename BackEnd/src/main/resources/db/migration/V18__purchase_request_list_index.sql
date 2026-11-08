-- Default "newest first" request list and its archived/removed filter. Measured on 50,000 synthetic requests: without
-- this index the list is a clustered scan plus a sort of every row (docs/reviews/stabilization-review.md, PERF-01).
CREATE INDEX ix_purchase_request_active_created ON purchase_request (created_at DESC, id DESC)
    INCLUDE (requester_user_id, entered_by_user_id, status)
    WHERE archived_at IS NULL AND removed_at IS NULL;
