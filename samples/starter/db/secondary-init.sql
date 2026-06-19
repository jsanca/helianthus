-- Sample data for secondary database (customers and orders)
INSERT INTO customers (customerNumber, customerName, contactLastName, contactFirstName, phone, city, country, creditLimit) VALUES
    (103, 'Atelier graphique', 'Schmitt', 'Carine', '40.32.2555', 'Nantes', 'France', 21000.00),
    (112, 'Signal Gift Stores', 'King', 'Jean', '7025551234', 'Las Vegas', 'USA', 71800.00),
    (114, 'Australian Collectors, Co.', 'Ferguson', 'Peter', '03 9520 4555', 'Melbourne', 'Australia', 117300.00),
    (119, 'La Rochelle Gifts', 'Labrune', 'Janine', '40.67.8555', 'Nantes', 'France', 118200.00),
    (121, 'Baane Mini Imports', 'Bergulfsen', 'Jonas', '07-98 9555', 'Stavern', 'Norway', 81700.00);

INSERT INTO orders (orderNumber, orderDate, requiredDate, shippedDate, status, customerNumber) VALUES
    (10100, '2003-01-06', '2003-01-13', '2003-01-10', 'Shipped', 103),
    (10101, '2003-01-09', '2003-01-18', '2003-01-11', 'Shipped', 112),
    (10102, '2003-01-10', '2003-01-18', '2003-01-14', 'Shipped', 114),
    (10103, '2003-01-29', '2003-02-07', '2003-02-02', 'Shipped', 119),
    (10104, '2003-01-31', '2003-02-09', '2003-02-01', 'Shipped', 121);
