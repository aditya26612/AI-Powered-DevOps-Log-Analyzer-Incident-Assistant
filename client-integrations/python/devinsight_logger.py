"""Bounded DevInsight delivery for Python's standard logging framework."""
from __future__ import annotations

from collections import deque
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
import http.client
import json
import logging
import os
import random
import re
import socket
import threading
import time
import traceback
from urllib.parse import urlsplit
from uuid import uuid4

try:
    from opentelemetry.trace import get_current_span
except ImportError:
    get_current_span = None

_SYNC = "/api/v1/logs/batch"
_ASYNC = "/api/v2/logs/batch"
_RECEIPT = re.compile(r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
_LEVELS = ("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL")
_ENVIRONMENTS = {"DEVELOPMENT", "TESTING", "QA", "STAGING", "PRODUCTION"}
_KEY_VALUE = re.compile(r"""(?i)\b(password|passwd|secret|token|authorization|api[-_ ]?key|cookie)(["']?\s*[:=]\s*)(?:"[^"\r\n]*"|'[^'\r\n]*'|[^\s,;]+)""")
_BEARER = re.compile(r"(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+")
_API_KEY = re.compile(r"dvi_[A-Za-z0-9_-]{16,}")
_USERINFO = re.compile(r"(?i)(\b[a-z][a-z0-9+.-]*://)[^/\s@]+:[^/\s@]+@")
_RETRYABLE = {408, 429, 500, 502, 503, 504}
_LOG_ID = re.compile(r"[1-9][0-9]{0,18}")
_TRACE_PARENT = re.compile(r"00-([0-9a-f]{32})-([0-9a-f]{16})-[0-9a-f]{2}")


def _trace_fields(record):
    parent = getattr(record, "traceparent", None)
    if parent is None and get_current_span is not None:
        context = get_current_span().get_span_context()
        if context.is_valid:
            parent = f"00-{context.trace_id:032x}-{context.span_id:016x}-{int(context.trace_flags) & 255:02x}"
    match = _TRACE_PARENT.fullmatch(parent) if isinstance(parent, str) and len(parent) == 55 else None
    if not match or int(match[1], 16) == 0 or int(match[2], 16) == 0:
        return {}
    origin = getattr(record, "trace_origin", None)
    if origin not in ("INBOUND_REPORTED", "LOCAL_ROOT_REPORTED", "EXPLICIT"):
        origin = "COLLECTOR_REPORTED"
    return {"traceparent": parent, "traceOrigin": origin}


def _setting(name, default, lo, hi, override=None):
    try:
        value = int(os.environ.get("DEVINSIGHT_" + name, default) if override is None else override)
    except (ValueError, TypeError):
        raise ValueError("Invalid DevInsight " + name) from None
    if not lo <= value <= hi:
        raise ValueError("Invalid DevInsight " + name)
    return value


def _truncate(value, limit):
    return value.replace("\x00", "\ufffd").encode("utf-8", "replace")[:limit].decode("utf-8", "ignore")


def _retry_after(value):
    if not value:
        return 0
    try:
        if value.isascii() and value.isdecimal() and len(value) <= 12:
            return min(60, int(value))
        return max(0, min(60, parsedate_to_datetime(value).timestamp() - time.time()))
    except (ValueError, TypeError, OverflowError):
        return 0


class DevInsightHandler(logging.Handler):
    """Capture on the caller thread and deliver on one daemon worker."""

    def __init__(self, gateway_url=None, api_key=None, application_name=None, environment=None,
                 batch_size=None, flush_interval_sec=None, max_queue_size=None, *, endpoint=None):
        super().__init__()
        self._key = api_key or os.getenv("DEVINSIGHT_API_KEY")
        name = application_name or os.getenv("DEVINSIGHT_APPLICATION_NAME")
        environment = environment or os.getenv("DEVINSIGHT_ENVIRONMENT")
        if not self._key or not re.fullmatch(r"[\x21-\x7e]{16,160}", self._key):
            raise ValueError("A valid DEVINSIGHT_API_KEY is required")
        if not name or not 1 <= len(name.encode("utf-16-le")) // 2 <= 100 or environment not in _ENVIRONMENTS:
            raise ValueError("DevInsight application name and environment are required")
        mode = os.getenv("DEVINSIGHT_DELIVERY_MODE", "SYNC_PERSISTED")
        if mode not in {"SYNC_PERSISTED", "ASYNC_KAFKA"}:
            raise ValueError("Invalid DEVINSIGHT_DELIVERY_MODE")
        self._asynchronous = mode == "ASYNC_KAFKA"
        self._success = 202 if self._asynchronous else 200
        self._path = _ASYNC if self._asynchronous else _SYNC
        try:
            self._url = urlsplit(endpoint or os.getenv("DEVINSIGHT_ENDPOINT") or
                                 ((gateway_url or "http://127.0.0.1:8080").rstrip("/") + self._path))
            local = self._url.hostname in {"localhost", "127.0.0.1", "::1"}
            if (not self._url.hostname or self._url.username is not None or self._url.password is not None
                    or self._url.query or self._url.fragment or self._url.path != self._path
                    or not (self._url.scheme == "https" or self._url.scheme == "http" and local)):
                raise ValueError()
            self._url.port
        except ValueError:
            raise ValueError("Use an HTTPS DevInsight batch URL or a loopback HTTP URL") from None

        self._prefix = _encode({"applicationName": name, "environment": environment})[:-1] + b',"events":['
        self._count = _setting("BATCH_COUNT", 10, 1, 10, batch_size)
        self._bytes = _setting("BATCH_BYTES", 65536, 1024, 65536)
        self._capacity = _setting("QUEUE_CAPACITY", 256, 1, 1024, max_queue_size)
        flush_ms = None if flush_interval_sec is None else round(flush_interval_sec * 1000)
        self._interval = _setting("FLUSH_MS", 250, 10, 5000, flush_ms) / 1000
        self._connect = _setting("CONNECT_TIMEOUT_MS", 1500, 50, 10000) / 1000
        self._timeout = _setting("REQUEST_TIMEOUT_MS", 15000, 50, 60000) / 1000
        self._attempts = _setting("MAX_ATTEMPTS", 4, 1, 8)
        self._shutdown = _setting("SHUTDOWN_MS", 5000, 50, 30000) / 1000
        minimum = os.getenv("DEVINSIGHT_MIN_LEVEL", "INFO").upper()
        if minimum not in _LEVELS:
            raise ValueError("Invalid DEVINSIGHT_MIN_LEVEL")
        self._minimum = _LEVELS.index(minimum)
        self._redact = os.getenv("DEVINSIGHT_REDACT", "true").lower() != "false"
        self._condition = threading.Condition()
        self._queue = deque()
        self._closing = None
        self._in_flight = 0
        self._socket = None
        self._flush_requested = False
        self._counts = dict.fromkeys(("captured", "enqueued", "requests", "attemptedEvents", "acknowledged",
                                     "duplicates", "retries", "failedBatches", "rejected", "dropped", "peakDepth"), 0)
        self._last_delivery = None
        self._last_failure = None
        self._worker = threading.Thread(target=self._run, name="devinsight-python-sender", daemon=True)
        self._worker.start()

    def emit(self, record):
        if record.name.startswith("devinsight.collector"):
            return
        if record.levelno >= logging.CRITICAL:
            level = "FATAL"
        elif record.levelno >= logging.ERROR:
            level = "ERROR"
        elif record.levelno >= logging.WARNING:
            level = "WARN"
        elif record.levelno >= logging.INFO:
            level = "INFO"
        elif record.levelno >= logging.DEBUG:
            level = "DEBUG"
        else:
            level = "TRACE"
        if _LEVELS.index(level) < self._minimum:
            return
        self._count_items(captured=1)
        with self._condition:
            if self._closing is not None or len(self._queue) >= self._capacity:
                self._counts["dropped"] += 1
                return
        try:
            event_id = str(uuid4())
            event = {
                "eventId": event_id,
                "timestamp": datetime.fromtimestamp(record.created, timezone.utc).isoformat(timespec="milliseconds"),
                "level": level, "source": "PYTHON",
                "message": self._clean(record.getMessage(), 8192).strip() or "(empty log message)",
                "loggerName": self._clean(record.name, 255),
                "threadName": self._clean(record.threadName or "", 100),
            }
            if record.exc_info and record.exc_info[0]:
                frames = traceback.TracebackException(*record.exc_info, limit=40, capture_locals=False)
                event["exception"] = self._clean("".join(frames.format()), 4096)
            event.update(_trace_fields(record))
            data = _encode(event)
        except Exception:
            self._count_items(rejected=1)
            return
        with self._condition:
            if len(self._prefix) + len(data) + 2 > self._bytes:
                self._counts["rejected"] += 1
            elif self._closing is not None or len(self._queue) >= self._capacity:
                self._counts["dropped"] += 1
            else:
                self._queue.append((time.monotonic(), event_id, data))
                self._counts["enqueued"] += 1
                self._counts["peakDepth"] = max(self._counts["peakDepth"], len(self._queue))
                self._condition.notify()

    def _clean(self, text, size):
        text = text.replace(self._key, "[REDACTED]")
        if self._redact:
            text = _BEARER.sub("Bearer [REDACTED]", text)
            text = _API_KEY.sub("[REDACTED]", text)
            text = _KEY_VALUE.sub(r"\1\2[REDACTED]", text)
            text = _USERINFO.sub(r"\1[REDACTED]@", text)
        return _truncate(text, size)

    def _count_items(self, **values):
        with self._condition:
            for name, amount in values.items():
                self._counts[name] += amount

    def stats(self):
        with self._condition:
            acknowledged = self._counts["acknowledged"]
            return {**self._counts, "queueDepth": len(self._queue), "inFlight": self._in_flight,
                    "brokerAccepted": acknowledged if self._asynchronous else 0,
                    "persisted": 0 if self._asynchronous else acknowledged,
                    "lastSuccess": self._last_delivery, "lastFailure": self._last_failure}

    def _take(self):
        with self._condition:
            while True:
                if self._closing is not None and (not self._queue or time.monotonic() >= self._closing):
                    self._counts["dropped"] += len(self._queue)
                    self._queue.clear()
                    return None
                if not self._queue:
                    self._condition.wait()
                    continue
                remaining = self._queue[0][0] + self._interval - time.monotonic()
                if self._closing is None and not self._flush_requested and len(self._queue) < self._count and remaining > 0:
                    self._condition.wait(remaining)
                    continue
                batch, size = [], len(self._prefix) + 2
                while self._queue and len(batch) < self._count:
                    frame = self._queue[0]
                    next_size = size + len(frame[2]) + bool(batch)
                    if next_size > self._bytes:
                        break
                    batch.append(self._queue.popleft())
                    size = next_size
                self._flush_requested = bool(self._queue) and self._flush_requested
                self._in_flight = len(batch)
                return batch

    def _run(self):
        while (batch := self._take()) is not None:
            try:
                self._deliver(batch)
            finally:
                with self._condition:
                    self._in_flight = 0
                    self._condition.notify_all()

    def _deliver(self, batch):
        body = self._prefix + b",".join(event[2] for event in batch) + b"]}"
        failure = "retry_exhausted"
        for attempt in range(self._attempts):
            if self._closing is not None and time.monotonic() >= self._closing:
                self._count_items(dropped=len(batch))
                return
            self._count_items(requests=1, attemptedEvents=len(batch))
            status, response, retry_after = self._send(body)
            if status == self._success:
                duplicates = _acknowledge(response, batch, self._asynchronous)
                if duplicates is not None:
                    with self._condition:
                        self._counts["acknowledged"] += len(batch)
                        self._counts["duplicates"] += duplicates
                        self._last_delivery = datetime.now(timezone.utc).isoformat()
                    return
                failure = "invalid_ack"
            elif status == 413 and len(batch) > 1:
                midpoint = len(batch) // 2
                self._deliver(batch[:midpoint])
                self._deliver(batch[midpoint:])
                return
            elif status is not None and status not in _RETRYABLE:
                failure = "http_" + str(status)
                break
            else:
                failure = "network" if status is None else "http_" + str(status)
            if attempt + 1 == self._attempts:
                break
            wait = max(_retry_after(retry_after), random.uniform(0.1, min(4, 0.25 * (2 ** attempt))))
            if not self._sleep(wait):
                self._count_items(dropped=len(batch))
                return
            self._count_items(retries=1)

        with self._condition:
            self._counts["failedBatches"] += 1
            self._counts["rejected"] += len(batch)
            self._last_failure = failure

    def _sleep(self, wait):
        until = time.monotonic() + wait
        with self._condition:
            while True:
                now = time.monotonic()
                if self._closing is not None and until >= self._closing:
                    return False
                if now >= until:
                    return True
                self._condition.wait(until - now)

    def _send(self, body):
        timeout = self._timeout
        if self._closing is not None:
            timeout = min(timeout, self._closing - time.monotonic())
        if timeout <= 0:
            return None, b"", None
        deadline = time.monotonic() + timeout
        connector = http.client.HTTPSConnection if self._url.scheme == "https" else http.client.HTTPConnection
        connection = connector(self._url.hostname, self._url.port, timeout=min(timeout, self._connect))
        timer = None
        try:
            connection.connect()
            sock = connection.sock
            with self._condition:
                self._socket = sock
                if self._closing is not None:
                    deadline = min(deadline, self._closing)
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                return None, b"", None
            sock.settimeout(remaining)
            timer = threading.Timer(remaining, _interrupt, args=(sock,))
            timer.daemon = True
            timer.start()
            connection.request("POST", self._url.path, body=body,
                               headers={"Content-Type": "application/json", "X-API-Key": self._key})
            response = connection.getresponse()
            data = response.read(8193) if response.status == self._success else b""
            if len(data) > 8192:
                return self._success, b"", None
            return response.status, data, response.getheader("Retry-After")
        except (OSError, ValueError, http.client.HTTPException):
            return None, b"", None
        finally:
            if timer is not None:
                timer.cancel()
            with self._condition:
                self._socket = None
            connection.close()

    def flush(self):
        if hasattr(self, "_condition"):
            with self._condition:
                self._flush_requested = bool(self._queue)
                self._condition.notify_all()

    def close(self):
        if hasattr(self, "_condition"):
            with self._condition:
                if self._closing is None:
                    self._closing = time.monotonic() + self._shutdown
                    self._condition.notify_all()
                remaining = max(0, self._closing - time.monotonic())
                timer = threading.Timer(remaining, self._cancel_active)
                timer.daemon = True
                timer.start()
            if self._worker is not threading.current_thread():
                self._worker.join(remaining)
            if not self._worker.is_alive():
                timer.cancel()
        super().close()


    def _cancel_active(self):
        with self._condition:
            _interrupt(self._socket)


def _interrupt(sock):
    if sock is not None:
        try:
            sock.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass


def _encode(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def _acknowledge(body, batch, asynchronous):
    try:
        response = json.loads(body)
        if asynchronous and (response.get("acknowledgement") != "BROKER_ACCEPTED"
                             or response.get("processingState") != "ASYNCHRONOUS"
                             or not _RECEIPT.fullmatch(response.get("receiptId", ""))):
            return None
        events = response["events"]
        if not isinstance(events, list) or len(events) != len(batch):
            return None
        duplicates = 0
        for ack, (_, event_id, _) in zip(events, batch):
            if ack["eventId"] != event_id:
                return None
            if asynchronous:
                if ack["status"] != "BROKER_ACCEPTED" or "logId" in ack:
                    return None
            else:
                if not _LOG_ID.fullmatch(ack["logId"]) or ack["status"] not in {"ACCEPTED", "DUPLICATE"}:
                    return None
                duplicates += ack["status"] == "DUPLICATE"
        return duplicates
    except (ValueError, KeyError, TypeError, UnicodeError, AttributeError):
        return None
