# Engineering Log

This file is the compact, current index of material engineering work. Detailed task, report, review, and checkpoint records live under `agents/`; this index links their relationship rather than repeating their evidence.

| Task | Description | Status | Depends On | Task File | Report | Review | Fix / Checkpoint | Knowledge |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TASK-ID | concise outcome | Complete | — | [task](agents/tasks/TASK-ID.md) | [report](agents/reports/TASK-ID.md) | — | — | [current concept](../knowledge/area/concept.md) |
| HEL-KOTLIN-001 | Kotlin-native architecture & Ktor migration assessment (Spring coupling map, dependency analysis, Ktor capability mapping, migration surface) | Complete — recommend INVESTIGATE | — | — | [report](agents/reports/HEL-KOTLIN-001.md) | — | — | — |
| HEL-KOTLIN-002 | Runtime migration prep: framework-neutral Principal, HTTP characterization tests, ResultFrame JSON contract, permission-evaluator tests, dead-code removal | Complete | HEL-KOTLIN-001 | — | [report](agents/reports/HEL-KOTLIN-002.md) | — | — | — |
| HEL-KOTLIN-003 | Core / runtime / web boundary assessment: three-layer architecture in two modules; recommend INTRODUCE_RUNTIME | Complete | HEL-KOTLIN-002 | — | [report](agents/reports/HEL-KOTLIN-003.md) | — | — | — |
| HEL-KOTLIN-004 | Runtime module extraction: new helianthus-runtime (catalog/pipeline/security/util), EntityService, CatalogLoader, HelianthusRuntime facade, NamedParameterSql re-parent | Complete | HEL-KOTLIN-003 | — | [report](agents/reports/HEL-KOTLIN-004.md) | — | — | — |
| HEL-KOTLIN-005 | Module public API & encapsulation assessment: runtime/core API classification, JPMS/qualified-export feasibility; recommend API_TIGHTEN_FIRST | Complete | HEL-KOTLIN-004 | — | [report](agents/reports/HEL-KOTLIN-005.md) | — | — | — |
| HEL-KOTLIN-006 | Public API & internal boundary enforcement: Kotlin `internal` for pipeline/catalog/impl.db, HelianthusRuntimeBuilder + DataAccessFactory + catalogSummary facade | Complete | HEL-KOTLIN-005 | — | [report](agents/reports/HEL-KOTLIN-006.md) | — | — | — |

Use `—` where a relationship does not exist. Keep cells brief; the linked durable record carries evidence, limitations, unresolved issues, and validation. Add a knowledge link when work establishes or changes reusable current understanding.
