# Phase 4.5 — Operation Runner UX + Output Formats

## Goal

Turn the current YAML operation API into a usable starter/demo product before moving into declarative CRUD.

Focus:

```text
catalog metadata
typed parameters
result viewers
multiple output formats
starter docker
client/backend contract
````

## 4.5.1 Catalog Metadata for UI

Extend `operations.yml` with optional UI metadata.

Example:

```yaml
operations:
  products:
    label: Products
    description: Product catalog operations
    queryRef: products.base
    security:
      roles: [GUEST, ADMIN]

    parameters:
      productLine:
        type: string
        required: false
        label: Product line
        description: Filter products by product line
        placeholder: Classic Cars
        input:
          kind: select
          options: [Classic Cars, Motorcycles, Planes]

      minPrice:
        type: decimal
        required: false
        label: Minimum price
        input:
          kind: number
          min: 0

    configurations:
      default:
        label: Default
        description: Standard product list
        pipeline:
          - limit: 100
```

Those are the types proposed
string   → input text, default
number   → input numeric; internally decimal
select   → dropdown
boolean  → checkbox/switch
date     → date picker

## 4.5.2 Catalog Endpoint Contract

Create/refine:

```http
GET /api/admin/catalog
```

It should return a UI-safe DTO, not raw internal pipeline classes.

Include:

```text
app name
formats
operations
configurations
parameters
security visibility
datasource name
descriptions/labels
```

Do not expose raw SQL.

## 4.5.3 Dynamic Parameter Form

React should generate parameter inputs from catalog metadata.

Initial supported input kinds:

```text
text
number
select
boolean
date
```

Fallback:

```text
manual key/value rows
```

## 4.5.4 Result Viewers

Frontend result panel supports:

```text
Table
JSON
Raw
HTML preview
CSV preview/download
XML preview
```

For JSON `ResultFrame`, table rendering uses:

```text
schema.columns + rows
```

Metadata badges:

```text
status
duration
rowCount
contentType
operation/configuration
```

## 4.5.5 Backend Output Formats

Implement `ResultFrame` message converters/renderers:

```text
json — already working
csv
xml
html
```

Suggested media types:

```text
application/json
text/csv
application/xml
text/html
```

HTML can start as a basic table renderer.

CSV should use schema columns as headers.

XML can be simple ResultFrame XML first.


## Done Criteria

* Starter stack runs with one command.
* UI loads catalog from backend.
* UI generates parameter form from catalog metadata.
* User can execute operation/configuration from the tree.
* JSON result can display as table and raw JSON.
* CSV/XML/HTML formats work end-to-end at least basically.
* Result metadata is visible.
* Backend does not expose raw SQL through catalog endpoint.
* Security filtering works for guest/admin.
