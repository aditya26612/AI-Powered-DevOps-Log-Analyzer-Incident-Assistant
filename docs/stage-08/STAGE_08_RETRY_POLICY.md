# Slack retry policy

Canonical HTTP 200 with text/plain ok marks DELIVERED. HTTP 429 and explicitly retryable 503 or connection setup failures use exponential backoff with up to two seconds of jitter. A 429 delay is persisted on the delivery, the application and the shared destination gate, including when that job exhausts its budget. Another registration with the same webhook waits without starting an attempt. Retry-After beyond the lifetime terminates the job.

The transport accepts whole-second Retry-After values and RFC 1123 dates, rounding fractional time remaining upward. Malformed values use the normal retry schedule.

Most provider errors, including redirects and rejected 4xx requests, terminate. Timeout, interrupted response, unexpected success and ambiguous 5xx outcomes become DELIVERY_UNKNOWN. A network intermediary can make even an explicit 503 ambiguous in practice.

Defaults are five automatic attempts in 24 hours and at most two manager retry rounds. Current destination revision and eligibility are revalidated. Unknown requires explicit possible-duplicate confirmation. Delivered rows cannot be retried.
