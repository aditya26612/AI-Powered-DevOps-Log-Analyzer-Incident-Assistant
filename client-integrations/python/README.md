# DevInsight Python logging handler

Put this directory on PYTHONPATH or add devinsight_logger.py to your application. The handler uses the Python standard library. Provide DEVINSIGHT_API_KEY, DEVINSIGHT_APPLICATION_NAME and DEVINSIGHT_ENVIRONMENT through protected process configuration. Set DEVINSIGHT_ENDPOINT to the full Gateway batch URL when using a different local address.

```python
import logging
from devinsight_logger import DevInsightHandler

logger = logging.getLogger("checkout")
collector = DevInsightHandler()
logger.addHandler(collector)
logger.setLevel(logging.INFO)
try:
    logger.info("Payment accepted")
finally:
    collector.close()
```

Keep your existing console and file handlers. The FastAPI example attaches and closes its collector with the application lifespan. Use collector.stats() for local counters.

See the [Python collector example](../../examples/collectors/python).
