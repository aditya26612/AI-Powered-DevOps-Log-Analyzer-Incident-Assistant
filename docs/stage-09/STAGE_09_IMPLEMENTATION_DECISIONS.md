# Stage 09 implementation decisions

The existing dashboard retrieves one page of full log DTOs and cannot produce organization-wide metrics. The existing incident, diagnosis and delivery queries already enforce membership and expose authoritative state. Preserve these contracts and add bounded read-only summary and log-list projections in the Log Layer.

The overview groups persisted logs by stored event occurrence time. Incidents use last occurrence and application scope includes actual membership. Diagnosis and delivery breakdowns describe current states of jobs created in the selected period. Each timestamp basis is visible. Historical Kafka acknowledgements, wall-clock latency, host utilization and Slack read receipts are unavailable.

Reuse the PostgreSQL tenant scope and React layout. Use visibility-aware polling with request cancellation and scope-keyed views. Use native accessible tables and CSS charts. Start with existing indexes and keep V9 immutable. Measure on the protected fixture before considering another migration. The notification history remains separate from diagnosis progress and preserves the duplicate warning on authorized retries.

The React app previously placed log listing and a partial-page summary on one route. The new Overview and Log Explorer give investigation a separate entry point while retaining manual ingestion and the existing analyzer. A read-only PostgreSQL query is sufficient for the current local volume. A materialized analytics table would duplicate tenant data and add migration and freshness responsibilities. Push updates would require a separate authorization and reconnect strategy. The current bounded polling model uses the existing HTTP and session behavior.

The first reused native Kafka fixture hit a Windows file lock during old-segment expiration. Stage 09 used a separately named disposable broker directory. Earlier Kafka records and offsets were retained.
