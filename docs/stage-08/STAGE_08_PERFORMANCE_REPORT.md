# Notification performance

The V9 run used Windows, Java 21, PostgreSQL 16.14, Kafka KRaft, and local Ollama on an Intel Core i5-12450H with 12 logical CPUs and about 16 GB RAM. The service stack was running before the collector submitted its workload. The intentionally restarted workers used cold JVMs.

| Measurement | Result |
|---|---:|
| Related errors / V2 predictions | 12 / 12 |
| Incidents / logical diagnoses / logical delivery records | 1 / 1 / 1 |
| Accepted diagnosis results / receiver-confirmed sends | 1 / 1 |
| Enqueue to first claim, with sender startup | 13.400 s |
| Claim through recovered delivery, with lease expiry and restart | 20.232 s |
| Successful loopback HTTP request, measured in the worker | 0.518097 s |
| Broker acceptance and log persistence while another request was held open | 0.142 s |
| Disposable PostgreSQL interruption to persisted unknown state | 19.417 s |
| Persisted retry delays in three recorded 429 attempts with a three-second provider minimum | 4.000 s each |
| Physical requests for a separate operator-confirmed uncertain retry | 2 for the same delivery ID |

The first five timing measurements each represent a single observed scenario. The recovery timings include deliberate fault injection and startup. The worker exposes low-cardinality transport outcomes and timing through `devinsight.slack.http` and stale-fence rejection through `devinsight.slack.fenced`. Queue state and attempt timing are available through scoped SQL and the notification API. This small fixture provides no estimate of production latency, tail behavior or channel throughput.
