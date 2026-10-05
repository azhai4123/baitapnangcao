CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE books (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    isbn VARCHAR(20) NULL,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL,
    publisher VARCHAR(255) NULL,
    publication_year SMALLINT UNSIGNED NULL,
    total_quantity INT UNSIGNED NOT NULL,
    available_quantity INT UNSIGNED NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_books_isbn UNIQUE (isbn),
    CONSTRAINT fk_books_category
        FOREIGN KEY (category_id) REFERENCES categories (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_books_available_not_above_total
        CHECK (available_quantity <= total_quantity),
    CONSTRAINT chk_books_version_nonnegative CHECK (version >= 0),
    INDEX idx_books_title (title),
    INDEX idx_books_author (author),
    INDEX idx_books_category (category_id),
    INDEX idx_books_available_quantity (available_quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(200) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone VARCHAR(30) NULL,
    address VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_members_email UNIQUE (email),
    CONSTRAINT chk_members_status CHECK (status IN ('ACTIVE', 'LOCKED')),
    CONSTRAINT chk_members_version_nonnegative CHECK (version >= 0),
    INDEX idx_members_full_name (full_name),
    INDEX idx_members_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE borrowings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    borrowed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    due_date DATE NOT NULL,
    notes VARCHAR(1000) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_borrowings_member
        FOREIGN KEY (member_id) REFERENCES members (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_borrowings_version_nonnegative CHECK (version >= 0),
    INDEX idx_borrowings_member_borrowed_at (member_id, borrowed_at),
    INDEX idx_borrowings_due_date (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE borrowing_details (
    id BIGINT NOT NULL AUTO_INCREMENT,
    borrowing_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    returned_at DATETIME(6) NULL,
    overdue_days INT UNSIGNED NOT NULL DEFAULT 0,
    fine_amount DECIMAL(12, 0) UNSIGNED NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_borrowing_details_borrowing
        FOREIGN KEY (borrowing_id) REFERENCES borrowings (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_borrowing_details_book
        FOREIGN KEY (book_id) REFERENCES books (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_borrowing_details_version_nonnegative CHECK (version >= 0),
    INDEX idx_borrowing_details_borrowing_returned (borrowing_id, returned_at),
    INDEX idx_borrowing_details_book (book_id),
    INDEX idx_borrowing_details_returned_at (returned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
