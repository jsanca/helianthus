# Task1 - Entities UI

Create a dedicated UI for `Entities` separate from `Operations`.

## Objective

Separate two distinct concepts in the Admin UI:

- `Operations`: executable actions/queries.

- `Entities`: navigable/editable CRUD resources.

## Expected Changes

Sidebar:

```text
Catalog
Operations
Products
Product Lines
Inventory Report
High-Value Customers

Entities
Product
Product Line
Customer
Order

Settings
````

##New Screen

Create an `Entity Browser` with:

* Record listing
* Basic search/filters
* Detailed view
* Actions: create/edit/delete if available
* Initial support for schemas/fields from backend metadata

## Design Note

Do not reuse the `Operation Runner` as the main entity screen.

Entities should feel like manageable resources, not operations.