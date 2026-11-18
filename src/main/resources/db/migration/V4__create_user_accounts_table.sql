
CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,

    customer_id UUID UNIQUE,

    email VARCHAR(255) NOT NULL UNIQUE,

    password_hash VARCHAR(255) NOT NULL,

    role VARCHAR(20) NOT NULL DEFAULT 'BORROWER',

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP WITH TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_accounts_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id),

    CONSTRAINT chk_user_accounts_role
        CHECK (role IN ('BORROWER', 'OPERATIONS', 'ADMIN')),

    CONSTRAINT chk_user_accounts_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);
