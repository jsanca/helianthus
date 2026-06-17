Start Kotlin migration with simple bean classes only.

Tasks:
- Add Kotlin Maven support to the relevant module.
- Convert only these classes from Java to Kotlin data classes:
    - PathMappingResultBean
    - QueryConfigBean
    - QueryParameterBean
- Preserve package names.
- Preserve JavaBean compatibility: use var properties with default null values.
- Preserve Serializable.
- Do not migrate service/config/workflow/data-access classes yet.
- Do not change behavior.
- Run cd server && mvn clean test.

Acceptance:
- Java code can still call getName(), setName(), getOperationId(), etc.
- Digester/queryConfig parsing still compiles.
- mvn clean test succeeds.