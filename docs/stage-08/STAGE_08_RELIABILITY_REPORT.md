# Notification reliability

The outbox is atomic with the accepted diagnosis result. A claim closed before the dispatch marker can be recovered. Once the dispatch marker commits, expiry is treated as an uncertain provider outcome even if no response was received.

The live fixture killed a worker before dispatch and observed one receipt after recovery. Killing a worker during HTTP, terminating it after the receiver returned success while the final transaction was blocked, and stopping PostgreSQL after the receiver accepted a request all preserved `DELIVERY_UNKNOWN`. Two simultaneous retry requests produced one HTTP 202 and one 409 after explicit confirmation. The controlled receiver observed two requests for the same ID after that retry.

Deduplication constrains logical jobs in PostgreSQL. Incoming webhooks provide no shared transaction or read-receipt guarantee. A provider or intermediary can lose a response after accepting the message. A 503 is treated as retryable and retains an ambiguity risk if an intermediary replaces an acknowledgement.
