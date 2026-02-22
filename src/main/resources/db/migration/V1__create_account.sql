CREATE SEQUENCE account_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE account (
    id             BIGINT       NOT NULL DEFAULT nextval('account_seq') PRIMARY KEY,
    owner_name     VARCHAR(255) NOT NULL,
    account_number VARCHAR(20)  NOT NULL UNIQUE,
    created_at     TIMESTAMP    NOT NULL
);
