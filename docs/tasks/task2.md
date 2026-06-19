Migrate PathHandler to Kotlin and harden path parsing.

Goal:
Replace the loose Java PathHandler with a Kotlin implementation that validates legacy operation paths.

Expected legacy query path format:
- /api/op/{operationId}.{format}

Examples:
- /api/op/all-products.json
- /api/op/get-product.json
- /api/op/reports/sales-by-month.json

Rules:
- path must not be null or blank
- path must start with /api/op/
- path must contain an operation id
- path must contain a format extension
- format must not be blank
- operation id must not be blank
- use the last dot to split operation id and format
- preserve operation id with leading slash if the existing OperationMappingHelper expects ids like /all-products
- reject paths with malformed format
- throw a custom exception instead of returning partial/null results

Create custom exception:
- InvalidOperationPathException

Suggested package:
- helianthus.core.exception.InvalidOperationPathException
  or
- helianthus.core.web.InvalidOperationPathException

Implementation notes:
- Convert PathHandler.java to PathHandler.kt.
- Keep public method name parsePath for now.
- Return PathMappingResultBean for compatibility.
- Do not redesign PathMappingResultBean yet.
- Add unit tests for valid and invalid paths.
- Update HelianthusController to return 400 Bad Request for InvalidOperationPathException.
- Run cd server && mvn clean test.

Test cases:
Valid:
- /api/op/all-products.json -> operationId=/all-products, format=json
- /api/op/get-product.json -> operationId=/get-product, format=json
- /api/op/reports/sales-by-month.html -> operationId=/reports/sales-by-month, format=html

Invalid:
- null
- ""
- "/"
- "/api/op/"
- "/api/op/all-products"
- "/api/op/.json"
- "/api/op/all-products."
- "/products.json"