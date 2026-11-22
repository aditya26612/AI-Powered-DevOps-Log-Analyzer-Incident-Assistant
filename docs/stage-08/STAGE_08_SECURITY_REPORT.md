# Notification security

The runtime discovers organization IDs through existing Log Layer behavior and uses transaction-local tenant scope for polling and reads. App keys cannot use the incident notification API. Delivery IDs and route data are constrained by organization. PostgreSQL checks foreign-key identity and forced RLS.

The shared destination gate has no direct runtime table grants. The application startup guard verifies the required access boundary and the lease functions. The tests registered the same synthetic webhook in two organizations and observed shared throttling without disclosing either organization's IDs, payloads or destination data through the API.

Plaintext may exist only during a real authorized attempt. Sanitized categories and HTTP codes are exposed to users. Safe requests never carry internal API credentials to the provider. The fixture receiver is an operator-only exception for local verification.
