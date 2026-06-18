CREATE TABLE productlines (
    productLine VARCHAR(50) PRIMARY KEY,
    textDescription TEXT
);

CREATE TABLE products (
    productCode VARCHAR(15) PRIMARY KEY,
    productName VARCHAR(70) NOT NULL,
    productLine VARCHAR(50) REFERENCES productlines(productLine),
    buyPrice NUMERIC(10, 2),
    quantityInStock INTEGER DEFAULT 0
);
