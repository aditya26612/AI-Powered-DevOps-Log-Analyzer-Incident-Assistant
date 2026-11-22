# Webhook security

The existing AES-256-GCM format binds ciphertext to the tenant and app. Plaintext is resolved in memory at attempt time. Outbox and attempt metadata exclude URLs, ciphertext, and response bodies.

The separate destination gate stores a domain-separated HMAC of the canonical path. It contains no tenant, application, plaintext URL, or payload. The restricted runtime can invoke only the lease routines. A bounded lease token fences renewal and release. Rotating the encryption key requires a coordinated re-encryption and planned delivery pause so replicas keep the same key during rollout.

Ordinary delivery needs a canonical HTTPS Slack incoming webhook and public resolved addresses. The channel resolver verifies the address used to connect. Proxy use, redirects, and transparent transport retry are disabled. HTTP response size and deadlines are bounded. The fixture overwrites the resolved destination with an operator-provided literal loopback URL only after a profile and database-port check.

Deployments should independently restrict egress to approved Slack destinations. This validation does not establish network policy for a production cluster.
