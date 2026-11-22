# Slack delivery worker

Separate scheduled slots discover trusted organizations and claim scoped rows with SKIP LOCKED. Defaults are two workers, 500 ms polling, 30-second leases, five-second heartbeats and eight-second HTTP deadlines. One active claim per registered app is serialized by transaction lock and state. A separate gate protects identical webhook URLs across registered apps. Outbound I/O happens after the dispatch-marker transaction commits.

An expired claim with no dispatch marker can be recovered within budget. An expired claim after the marker becomes DELIVERY_UNKNOWN. Results and heartbeats require tenant, worker, lease token, attempt and an unexpired lease. Graceful shutdown gives in-flight work four seconds before interrupting the slots.
