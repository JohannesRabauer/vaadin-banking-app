CREATE SEQUENCE transaction_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE transaction (
    id                BIGINT          NOT NULL DEFAULT nextval('transaction_seq') PRIMARY KEY,
    account_id        BIGINT          NOT NULL REFERENCES account(id),
    target_account_id BIGINT                   REFERENCES account(id),
    type              VARCHAR(20)     NOT NULL,
    amount            NUMERIC(19, 4)  NOT NULL,
    description       VARCHAR(500),
    created_at        TIMESTAMP       NOT NULL
);

CREATE INDEX idx_transaction_account_id ON transaction(account_id);
CREATE INDEX idx_transaction_target_account_id ON transaction(target_account_id);
