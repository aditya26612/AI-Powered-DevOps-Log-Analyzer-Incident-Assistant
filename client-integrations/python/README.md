# DevInsight Python Integration SDK

Drop-in telemetry handler for Python applications (FastAPI, Django, Flask, Celery, standard library `logging`).

## Quick Start (30 Seconds)

### 1. Copy `devinsight_logger.py` into your project

### 2. Attach the `DevInsightHandler` to your root or application logger:

```python
import logging
from devinsight_logger import DevInsightHandler

logger = logging.getLogger("my-python-service")
logger.setLevel(logging.INFO)

handler = DevInsightHandler(
    gateway_url="http://localhost:8080",
    api_key="your_service_api_key_here",
    application_name="my-python-service",
    environment="PRODUCTION"
)
logger.addHandler(handler)

# Any log or exception will now stream in real time to DevInsight:
try:
    1 / 0
except Exception:
    logger.exception("Critical unhandled exception!")
```

## How It Works
- **Non-blocking**: Logs are placed into an in-memory `queue.Queue` and sent in a background daemon thread.
- **Fail-safe**: If the DevInsight gateway is offline or network fails, logs are silently dropped or retried without interrupting application execution.

