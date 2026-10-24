CREATE TABLE customers (
    id UUID PRIMARY KEY,
    auth_subject VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL
);
CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    balance NUMERIC(19,4) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);
CREATE INDEX accounts_customer_cursor_idx ON accounts(customer_id, id);
