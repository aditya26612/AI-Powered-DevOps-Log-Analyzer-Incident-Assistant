# Stage 09 performance results

Windows local fixture with Java 21, PostgreSQL 16, authenticated Kafka and Ollama. Workload included 42 persisted source logs in the selected tenant and period. The table reports the median of five warm requests after one uncounted request per route through the Gateway.

| Action | Median |
|---|---:|
| Overview summary with log-volume buckets | 9.15 ms |
| Log list, first 25-row page | 30.31 ms |
| Incident list | 27.64 ms |
| Incident detail | 14.83 ms |

The scoped PostgreSQL rollup ran in **0.21 ms** in one `EXPLAIN ANALYZE` sample under the restricted role. Its plan used an index scan. The tablet run observed three initial Gateway requests for organization, application, and summary data. The earlier Chrome receipt counted only the numeric loopback host even though the browser used localhost. The request counter now accepts both hosts. The tablet receipt provides the verified initial count.

The production bundle produced 123.07 kB gzip for the main JavaScript, 3.84 kB for the lazy analyzer, and 9.28 kB for the main CSS. Chrome verified that Overview and log tables render at the reviewed sizes. No memory, saturation, percentile or production capacity result was collected.
