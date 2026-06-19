# Cache Design Study

## Overview

This document explores where caching could benefit Helianthus and defines safe cache candidates for future implementation.

## Potential Cache Locations

### 1. Catalog Cache

**What:** Cache the parsed `OperationCatalog` in memory after initial load.

**Current State:** The catalog is loaded once at startup and stored as a Spring bean. No caching needed — it's already a singleton.

**Verdict:** Already optimal. No action needed.

---

### 2. Operation Result Cache

**What:** Cache the results of operation executions to avoid repeated database queries.

**Cache Key Components:**
- `operationId` — which operation
- `configurationId` — which configuration
- `format` — output format (json, csv, xml, html)
- `params` — sorted parameter key-value pairs
- `user/roles` — if security-sensitive (optional)

**Example Cache Key:**
```
op:products|cfg:default|fmt:json|params:productLine=Classic Cars|roles:GUEST
```

**Considerations:**
- **Pros:** Reduces database load for repeated queries
- **Cons:** 
  - Stale data if underlying data changes
  - Memory usage grows with unique parameter combinations
  - Security implications if caching user-specific results
  - Cache invalidation complexity

**Safe First Candidate:**
- Cache only operations with no parameters (or fixed parameters)
- Use short TTL (e.g., 60 seconds)
- Exclude operations that modify data (not applicable currently since all are read-only)
- Do not include user/roles in cache key initially (all users see same result)

**Implementation Approach:**
```kotlin
@Service
class OperationResultCache(
    private val cacheManager: CacheManager
) {
    fun getOrExecute(
        operationId: String,
        configurationId: String,
        format: String,
        params: Map<String, String>,
        executor: () -> ResultFrame
    ): ResultFrame {
        val cacheKey = buildCacheKey(operationId, configurationId, format, params)
        val cache = cacheManager.getCache("operationResults")
        
        return cache?.get(cacheKey, ResultFrame::class.java) ?: run {
            val result = executor()
            cache?.put(cacheKey, result)
            result
        }
    }
    
    private fun buildCacheKey(
        operationId: String,
        configurationId: String,
        format: String,
        params: Map<String, String>
    ): String {
        val sortedParams = params.toSortedMap()
            .entries.joinToString(",") { "${it.key}=${it.value}" }
        return "$operationId|$configurationId|$format|$sortedParams"
    }
}
```

**Verdict:** Defer implementation. Current workload doesn't justify the complexity. Revisit when performance issues arise.

---

### 3. HTTP Cache / Varnish

**What:** Use HTTP cache headers or a reverse proxy (Varnish, Nginx) to cache responses at the HTTP level.

**Cache Headers:**
- `Cache-Control: max-age=60` — cache for 60 seconds
- `ETag` — content-based cache validation
- `Vary: Authorization` — different cache per user

**Considerations:**
- **Pros:** Offloads work from application server
- **Cons:** 
  - Requires infrastructure changes (Varnish/Nginx)
  - Complex with authenticated requests
  - Not suitable for dynamic/real-time data

**Verdict:** Not recommended for now. The application serves authenticated, dynamic data. HTTP caching adds complexity without clear benefit.

---

### 4. Datasource Metadata Cache

**What:** Cache database metadata (table schemas, column types) to avoid repeated JDBC metadata queries.

**Current State:** Helianthus doesn't query database metadata at runtime. The schema is defined in `ResultSchema` from query results.

**Verdict:** Not applicable. No metadata queries to cache.

---

## Recommended First Cache Candidate

**Operation Result Cache** with restrictions:

1. **Scope:** Only cache operations with no parameters or fixed parameters
2. **TTL:** 60 seconds (configurable)
3. **Key:** `operationId + configurationId + format + sortedParams`
4. **Exclusions:** 
   - Operations with user-specific logic
   - Operations marked with `cacheable: false` in catalog
5. **Invalidation:** Time-based only (no manual invalidation)

**When to Implement:**
- When database load becomes a bottleneck
- When the same operation is executed repeatedly with the same parameters
- After monitoring shows cache hit rate would be high

**When NOT to Implement:**
- If data freshness is critical
- If memory is constrained
- If operations are highly parameterized (low cache hit rate)

---

## Monitoring Cache Effectiveness

If caching is implemented, track:
- Cache hit rate
- Cache miss rate
- Average cache size
- Memory usage
- Stale data incidents

Use Spring Boot Actuator metrics:
```
/cache/operationResults/hits
/cache/operationResults/misses
/cache/operationResults/size
```

---

## Conclusion

No caching is implemented in Phase 4.6. The current architecture is simple and correct. Caching should be added only when:
1. Performance monitoring shows a clear bottleneck
2. Cache hit rate is predicted to be high
3. Data freshness requirements allow it

The operation result cache is the safest first candidate, but should be deferred until needed.
