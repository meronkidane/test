-- Standalone PostgreSQL example; customer IDs refer to an external customer service.
CREATE TABLE review_orders (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL CHECK (customer_id > 0),
    amount_cents BIGINT NOT NULL CHECK (amount_cents >= 0),
    status TEXT NOT NULL CHECK (status IN ('pending', 'paid', 'cancelled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX review_orders_created_id_idx ON review_orders (created_at DESC, id DESC);

-- Bounded, stable first page. Subsequent pages use a (created_at, id) cursor.
SELECT id, customer_id, amount_cents, status, created_at
FROM review_orders
ORDER BY created_at DESC, id DESC
LIMIT 50;
