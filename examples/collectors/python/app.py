"""Emit a finite synthetic workload through ordinary Python logging."""
import json
import logging
import os
import time

from devinsight_logger import DevInsightHandler


def main():
    count = int(os.getenv("DEMO_EVENTS", "6"))
    delay = float(os.getenv("DEMO_DELAY_MS", "0")) / 1000
    failures = os.getenv("DEMO_ERRORS", "true").lower() != "false"
    if not 1 <= count <= 5000 or not 0 <= delay <= 1:
        raise ValueError("Choose 1 to 5000 events and a delay between 0 and 1000 ms")
    logger = logging.getLogger("devinsight.example.python")
    logger.setLevel(logging.INFO)
    console = logging.StreamHandler()
    collector = DevInsightHandler()
    logger.addHandler(console)
    logger.addHandler(collector)
    try:
        for index in range(count):
            if failures and index == count - 1:
                try:
                    raise RuntimeError("Synthetic database connection timed out")
                except RuntimeError:
                    logger.exception("Database connection timed out during checkout")
            elif failures and index == count - 2:
                logger.warning("Synthetic dependency slowdown")
            else:
                logger.info("Payment accepted in independent Python example")
            if delay:
                time.sleep(delay)
    finally:
        collector.close()
        logger.removeHandler(collector)
    print("DEVINSIGHT_STATS " + json.dumps(collector.stats(), separators=(",", ":")))


if __name__ == "__main__":
    main()
