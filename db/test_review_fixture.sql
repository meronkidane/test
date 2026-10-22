-- Run after review_fixture.sql against a disposable database.
BEGIN;
INSERT INTO review_orders (customer_id, amount_cents, status)
SELECT n, 100, 'pending' FROM generate_series(1, 60) AS n;
DO $$
DECLARE page_count INTEGER;
BEGIN
    SELECT count(*) INTO page_count FROM (
        SELECT id FROM review_orders ORDER BY created_at DESC, id DESC LIMIT 50
    ) AS page;
    IF page_count <> 50 THEN RAISE EXCEPTION 'Page must contain 50 rows'; END IF;
    BEGIN
        INSERT INTO review_orders (customer_id, amount_cents, status) VALUES (1, -1, 'paid');
        RAISE EXCEPTION 'Negative amount accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO review_orders (customer_id, amount_cents, status) VALUES (NULL, 1, 'paid');
        RAISE EXCEPTION 'Missing customer accepted';
    EXCEPTION WHEN not_null_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO review_orders (customer_id, amount_cents, status) VALUES (1, 1, 'invalid');
        RAISE EXCEPTION 'Invalid status accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
END $$;
ROLLBACK;
