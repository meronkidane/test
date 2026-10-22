-- Deliberately incomplete database design for review only. Do not execute or merge.
CREATE TABLE review_orders (
    id BIGINT,
    customer_id BIGINT,
    amount_cents INTEGER,
    status TEXT,
    created_at TIMESTAMPTZ
);

-- Example hot-path query over a potentially large table.
SELECT * FROM review_orders ORDER BY created_at DESC;
