# Notification implementation decisions

- The Log Layer owns application configuration and the only connected automatic sender. The User Service supplies numeric user identity. No distributed transaction is needed.
- Diagnosis result, diagnosis attempt, source projection, and outbox intent use one transaction. Network availability is irrelevant to that transaction. Enqueue failure rolls back the accepted result.
- Route by the diagnosis owner's verified application ID. One app is one logical destination. A multi-app incident sends only a generic notice through the owner and keeps shared diagnosis detail inside DevInsight.
- PostgreSQL workers reuse scoped organization discovery and run separately from Ollama workers. Cloud queues and separate services add operational dependencies without improving the result/outbox boundary.
- Once the DB dispatch marker commits, a lost outcome is treated as uncertain. Manual retry requires confirmation for this state. A stable delivery reference helps people recognize duplicates.
- Operator-configured receiver mode is restricted to a loopback fixture and verified database port. Ordinary delivery requires an explicit transport and external enable switch. The local managed launcher forces OFF.

[Slack incoming webhook contract](https://docs.slack.dev/messaging/sending-messages-using-incoming-webhooks/) · [Slack rate limits](https://docs.slack.dev/apis/web-api/rate-limits/) · [PostgreSQL row locking](https://www.postgresql.org/docs/16/sql-select.html)
