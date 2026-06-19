-- Secondary database schema for customer data
CREATE TABLE customers (
    customerNumber INTEGER PRIMARY KEY,
    customerName VARCHAR(100) NOT NULL,
    contactLastName VARCHAR(50),
    contactFirstName VARCHAR(50),
    phone VARCHAR(50),
    addressLine1 VARCHAR(100),
    city VARCHAR(50),
    state VARCHAR(50),
    postalCode VARCHAR(15),
    country VARCHAR(50),
    creditLimit DECIMAL(10, 2)
);

CREATE TABLE orders (
    orderNumber INTEGER PRIMARY KEY,
    orderDate DATE NOT NULL,
    requiredDate DATE,
    shippedDate DATE,
    status VARCHAR(15) NOT NULL,
    comments TEXT,
    customerNumber INTEGER REFERENCES customers(customerNumber)
);
