ALTER TABLE borrowing_details
    ADD COLUMN quantity INT UNSIGNED NOT NULL DEFAULT 1 AFTER book_id,
    ADD COLUMN returned_quantity INT UNSIGNED NOT NULL DEFAULT 0 AFTER quantity,
    ADD COLUMN last_returned_date DATETIME(6) NULL AFTER returned_quantity,
    CHANGE COLUMN overdue_days late_days INT UNSIGNED NOT NULL DEFAULT 0;

UPDATE borrowing_details
SET returned_quantity = 1,
    last_returned_date = returned_at
WHERE returned_at IS NOT NULL;

ALTER TABLE borrowings
    ADD COLUMN returned_date DATETIME(6) NULL AFTER due_date,
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'BORROWING' AFTER notes,
    ADD COLUMN fine_status VARCHAR(16) NOT NULL DEFAULT 'FINE_PAID' AFTER status,
    ADD COLUMN total_fine_amount DECIMAL(12, 0) UNSIGNED NOT NULL DEFAULT 0 AFTER fine_status,
    ADD COLUMN paid_fine_amount DECIMAL(12, 0) UNSIGNED NOT NULL DEFAULT 0 AFTER total_fine_amount,
    ADD COLUMN waived_fine_amount DECIMAL(12, 0) UNSIGNED NOT NULL DEFAULT 0 AFTER paid_fine_amount,
    ADD COLUMN unpaid_fine_amount DECIMAL(12, 0) UNSIGNED NOT NULL DEFAULT 0 AFTER waived_fine_amount;

UPDATE borrowings borrowing
SET total_fine_amount = (
        SELECT COALESCE(SUM(detail.fine_amount), 0)
        FROM borrowing_details detail
        WHERE detail.borrowing_id = borrowing.id
    ),
    unpaid_fine_amount = (
        SELECT COALESCE(SUM(detail.fine_amount), 0)
        FROM borrowing_details detail
        WHERE detail.borrowing_id = borrowing.id
    ),
    fine_status = CASE WHEN unpaid_fine_amount > 0 THEN 'FINE_PENDING' ELSE 'FINE_PAID' END,
    status = CASE
        WHEN NOT EXISTS (
            SELECT 1 FROM borrowing_details detail
            WHERE detail.borrowing_id = borrowing.id AND detail.returned_quantity < detail.quantity
        ) THEN 'RETURNED'
        WHEN borrowing.due_date < CURRENT_DATE() THEN 'OVERDUE'
        WHEN EXISTS (
            SELECT 1 FROM borrowing_details detail
            WHERE detail.borrowing_id = borrowing.id AND detail.returned_quantity > 0
        ) THEN 'PARTIALLY_RETURNED'
        ELSE 'BORROWING'
    END,
    returned_date = CASE
        WHEN NOT EXISTS (
            SELECT 1 FROM borrowing_details detail
            WHERE detail.borrowing_id = borrowing.id AND detail.returned_quantity < detail.quantity
        ) THEN (
            SELECT MAX(detail.last_returned_date)
            FROM borrowing_details detail
            WHERE detail.borrowing_id = borrowing.id
        )
        ELSE NULL
    END;

CREATE TABLE fine_policies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    daily_fine_amount DECIMAL(12, 0) UNSIGNED NOT NULL,
    max_fine_amount DECIMAL(12, 0) UNSIGNED NULL,
    grace_days INT UNSIGNED NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    active_guard TINYINT GENERATED ALWAYS AS (IF(active, 1, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fine_policies_one_active (active_guard),
    CONSTRAINT chk_fine_policy_daily_positive CHECK (daily_fine_amount >= 0),
    CONSTRAINT chk_fine_policy_grace_nonnegative CHECK (grace_days >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO fine_policies (daily_fine_amount, max_fine_amount, grace_days, active)
VALUES (5000, NULL, 0, TRUE);

CREATE TABLE fine_payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    borrowing_id BIGINT NOT NULL,
    amount DECIMAL(12, 0) UNSIGNED NOT NULL,
    payment_date DATETIME(6) NOT NULL,
    method VARCHAR(32) NOT NULL,
    note VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_fine_payments_borrowing FOREIGN KEY (borrowing_id)
        REFERENCES borrowings (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_fine_payments_amount_positive CHECK (amount > 0),
    INDEX idx_fine_payments_borrowing_date (borrowing_id, payment_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fine_waivers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    borrowing_id BIGINT NOT NULL,
    amount DECIMAL(12, 0) UNSIGNED NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    approved_by VARCHAR(200) NOT NULL,
    approved_date DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_fine_waivers_borrowing FOREIGN KEY (borrowing_id)
        REFERENCES borrowings (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_fine_waivers_amount_positive CHECK (amount > 0),
    INDEX idx_fine_waivers_borrowing_date (borrowing_id, approved_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;