"""
DevInsight Python Telemetry Client & Drop-in Logging Handler
============================================================
Provides an asynchronous, non-blocking HTTP logging handler for Python applications
(FastAPI, Django, Flask, Celery, standard logging) to stream real-time logs and
unhandled tracebacks directly into DevInsight.
"""

import atexit
import json
import logging
import queue
import threading
import time
import urllib.request
import urllib.error
from typing import Optional


class DevInsightHandler(logging.Handler):
    """
    Drop-in asynchronous logging handler that queues log records and streams them
    to the DevInsight API Gateway via HTTP POST without stalling application worker threads.
    """

    def __init__(
        self,
        gateway_url: str = "http://localhost:8080",
        api_key: str = "devinsight_internal_ml_key_secret_12345",
        application_name: str = "python-service",
        environment: str = "PRODUCTION",
        batch_size: int = 20,
        flush_interval_sec: float = 1.0,
        max_queue_size: int = 1000,
    ):
        super().__init__()
        self.gateway_url = gateway_url.rstrip("/") + "/api/v1/logs"
        self.api_key = api_key
        self.application_name = application_name
        self.environment = environment
        self.batch_size = batch_size
        self.flush_interval_sec = flush_interval_sec

        self.log_queue: queue.Queue = queue.Queue(maxsize=max_queue_size)
        self.running = True

        self.worker_thread = threading.Thread(
            target=self._worker_loop, name="DevInsight-Worker", daemon=True
        )
        self.worker_thread.start()
        atexit.register(self.close)

    def emit(self, record: logging.LogRecord) -> None:
        try:
            formatted_msg = self.format(record)
            if record.exc_info and not record.exc_text:
                formatted_msg += "\n" + self.formatter.formatException(record.exc_info)

            payload = {
                "rawLog": formatted_msg,
                "source": "PYTHON",
                "environment": self.environment,
                "applicationName": self.application_name,
            }

            self.log_queue.put_nowait(payload)
        except queue.Full:
            # Drop record when buffer is saturated to safeguard application throughput
            pass
        except Exception:
            self.handleError(record)

    def _worker_loop(self) -> None:
        while self.running:
            try:
                item = self.log_queue.get(timeout=self.flush_interval_sec)
                self._send_payload(item)
                self.log_queue.task_done()
            except queue.Empty:
                continue
            except Exception:
                pass

    def _send_payload(self, payload: dict) -> None:
        try:
            data = json.dumps(payload).encode("utf-8")
            req = urllib.request.Request(
                self.gateway_url,
                data=data,
                headers={
                    "Content-Type": "application/json",
                    "X-API-Key": self.api_key,
                },
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=3.0) as resp:
                pass
        except Exception:
            pass

    def close(self) -> None:
        self.running = False
        super().close()

