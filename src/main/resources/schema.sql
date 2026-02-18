-- =============================================
-- DDL для всех JPA-сущностей приложения
-- Таблицы создаются только если не существуют
-- =============================================

-- 1. Клиенты
CREATE TABLE IF NOT EXISTS customers
(
    id              BIGSERIAL    PRIMARY KEY,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    phone           VARCHAR(30),
    date_of_birth   DATE,
    registered_at   TIMESTAMP    NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    loyalty_points  INTEGER      NOT NULL,
    country         VARCHAR(60)
);

CREATE INDEX IF NOT EXISTS idx_customer_email  ON customers (email);
CREATE INDEX IF NOT EXISTS idx_customer_status ON customers (status);

-- 2. Профили клиентов (OneToOne → customers)
CREATE TABLE IF NOT EXISTS customer_profiles
(
    id                     BIGSERIAL    PRIMARY KEY,
    customer_id            BIGINT       NOT NULL UNIQUE,
    avatar_url             VARCHAR(500),
    bio                    TEXT,
    preferred_language     VARCHAR(10),
    notifications_enabled  BOOLEAN      NOT NULL,
    address                VARCHAR(255),
    city                   VARCHAR(100),
    zip_code               VARCHAR(20),
    CONSTRAINT fk_profile_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);

-- 3. Товары
CREATE TABLE IF NOT EXISTS products
(
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    sku         VARCHAR(50)  NOT NULL UNIQUE,
    description TEXT,
    price       NUMERIC(10, 2) NOT NULL,
    category    VARCHAR(50)  NOT NULL,
    weight      DOUBLE PRECISION NOT NULL,
    in_stock    BOOLEAN      NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_product_sku      ON products (sku);
CREATE INDEX IF NOT EXISTS idx_product_category ON products (category);

-- 4. Заказы (ManyToOne → customers)
CREATE TABLE IF NOT EXISTS orders
(
    id               BIGSERIAL      PRIMARY KEY,
    customer_id      BIGINT         NOT NULL,
    order_number     VARCHAR(40)    NOT NULL UNIQUE,
    order_date       TIMESTAMP      NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    total_amount     NUMERIC(12, 2) NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    shipping_address VARCHAR(500),
    notes            TEXT,
    expected_delivery DATE,
    CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);

CREATE INDEX IF NOT EXISTS idx_order_customer ON orders (customer_id);
CREATE INDEX IF NOT EXISTS idx_order_status   ON orders (status);
CREATE INDEX IF NOT EXISTS idx_order_date     ON orders (order_date);

-- 5. Позиции заказа (ManyToOne → orders, ManyToOne → products)
CREATE TABLE IF NOT EXISTS order_items
(
    id          BIGSERIAL      PRIMARY KEY,
    order_id    BIGINT         NOT NULL,
    product_id  BIGINT         NOT NULL,
    quantity    INTEGER        NOT NULL,
    unit_price  NUMERIC(12, 2) NOT NULL,
    total_price NUMERIC(12, 2) NOT NULL,
    discount    NUMERIC(12, 2),
    created_at  TIMESTAMP      NOT NULL,
    CONSTRAINT fk_item_order   FOREIGN KEY (order_id)   REFERENCES orders (id),
    CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX IF NOT EXISTS idx_item_order   ON order_items (order_id);
CREATE INDEX IF NOT EXISTS idx_item_product ON order_items (product_id);
