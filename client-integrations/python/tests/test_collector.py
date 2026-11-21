import json
import logging
import os
from pathlib import Path
import sys
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from devinsight_logger import DevInsightHandler, _trace_fields


class CollectorTest(unittest.TestCase):
    def test_trace_metadata_is_optional_and_origin_is_reported(self):
        record = logging.LogRecord("example", logging.INFO, "", 0, "log", (), None)
        record.traceparent = "00-268ced385f22156b8c8233406b31c3da-38f598163b30a21c-01"
        record.trace_origin = "LOCAL_ROOT_REPORTED"
        self.assertEqual(_trace_fields(record)["traceOrigin"], "LOCAL_ROOT_REPORTED")
        record.trace_origin = "VERIFIED"
        self.assertEqual(_trace_fields(record)["traceOrigin"], "COLLECTOR_REPORTED")
        record.traceparent = "00-" + "0" * 32 + "-38f598163b30a21c-01"
        self.assertEqual(_trace_fields(record), {})

    def fixture(self, respond, **settings):
        seen = []

        class Receiver(BaseHTTPRequestHandler):
            def do_POST(self):
                raw = self.rfile.read(int(self.headers["Content-Length"]))
                seen.append((len(raw), json.loads(raw)))
                respond(self, seen[-1][1], len(seen))

            def log_message(self, *_):
                pass

        server = ThreadingHTTPServer(("127.0.0.1", 0), Receiver)
        server.daemon_threads = True
        threading.Thread(target=server.serve_forever, daemon=True).start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        config = patch.dict(os.environ, {
            "DEVINSIGHT_SHUTDOWN_MS": "3000", "DEVINSIGHT_FLUSH_MS": "20",
            "DEVINSIGHT_MIN_LEVEL": "INFO", "DEVINSIGHT_DELIVERY_MODE": "SYNC_PERSISTED", **settings,
        })
        config.start()
        self.addCleanup(config.stop)
        version = "v2" if settings.get("DEVINSIGHT_DELIVERY_MODE") == "ASYNC_KAFKA" else "v1"
        handler = DevInsightHandler(
            endpoint=f"http://127.0.0.1:{server.server_port}/api/{version}/logs/batch",
            api_key="synthetic-key-for-local-test",
            application_name="fixture", environment="TESTING",
        )
        logger = logging.Logger("sample", logging.DEBUG)
        logger.addHandler(handler)
        self.addCleanup(handler.close)
        return logger, handler, seen

    @staticmethod
    def reply(receiver, data, _=None, status="ACCEPTED"):
        encoded = json.dumps({"events": [
            {"eventId": event["eventId"], "logId": str(i + 1), "status": status}
            for i, event in enumerate(data["events"])
        ]}).encode()
        receiver.send_response(200)
        receiver.send_header("Content-Length", str(len(encoded)))
        receiver.end_headers()
        receiver.wfile.write(encoded)

    def test_byte_count_queue_limit_and_redaction(self):
        request_started = threading.Event()
        allow_delivery = threading.Event()

        def respond(receiver, data, attempt):
            request_started.set()
            if allow_delivery.wait(5):
                self.reply(receiver, data, attempt)

        logger, handler, seen = self.fixture(respond, DEVINSIGHT_BATCH_COUNT="2",
                                             DEVINSIGHT_BATCH_BYTES="1024", DEVINSIGHT_QUEUE_CAPACITY="4",
                                             DEVINSIGHT_FLUSH_MS="500")
        logger.debug("excluded by minimum severity")
        message = "secret=fixture-secret " + "λ" * 280
        logger.warning(message)
        try:
            self.assertTrue(request_started.wait(3))
            threads = [threading.Thread(target=logger.warning, args=(message,)) for _ in range(6)]
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join()
        finally:
            allow_delivery.set()
        handler.close()
        stats = handler.stats()
        self.assertEqual(stats["captured"], 7)
        self.assertGreaterEqual(stats["dropped"], 1)
        self.assertEqual(stats["acknowledged"] + stats["dropped"], 7)
        self.assertTrue(all(size <= 1024 and len(row["events"]) <= 2 for size, row in seen))
        self.assertTrue(all("fixture-secret" not in str(row) for _, row in seen))

    def test_response_loss_and_duplicate(self):
        def respond(receiver, data, attempt):
            if attempt == 1:
                receiver.close_connection = True
            else:
                self.reply(receiver, data, status="DUPLICATE")
        logger, handler, seen = self.fixture(respond)
        try:
            raise RuntimeError("fixture exception")
        except RuntimeError:
            logger.exception("exception event")
        handler.close()
        self.assertEqual(seen[0], seen[1])
        self.assertIn("RuntimeError", seen[0][1]["events"][0]["exception"])
        self.assertEqual(handler.stats()["retries"], 1)
        self.assertEqual(handler.stats()["duplicates"], 1)

    def test_malformed_ack_retry_after_and_permanent_credentials(self):
        attempts = []
        def respond(receiver, data, attempt):
            attempts.append(time.monotonic())
            code = [429, 200, 401][attempt - 1]
            receiver.send_response(code)
            if code == 429:
                receiver.send_header("Retry-After", "1")
            receiver.send_header("Content-Length", "0")
            receiver.end_headers()
        logger, handler, seen = self.fixture(respond)
        logger.info("delivery")
        handler.close()
        self.assertEqual(len(seen), 3)
        self.assertGreaterEqual(attempts[1] - attempts[0], 0.95)
        self.assertEqual(handler.stats()["lastFailure"], "http_401")
        self.assertEqual(handler.stats()["rejected"], 1)

    def test_flush_interval_and_recursion(self):
        logger, handler, seen = self.fixture(self.reply)
        internal = logging.Logger("devinsight.collector.internal", logging.INFO)
        internal.addHandler(handler)
        internal.warning("dispatcher failure")
        logger.info("timed flush")
        limit = time.monotonic() + 2
        while handler.stats()["acknowledged"] == 0 and time.monotonic() < limit:
            time.sleep(0.02)
        handler.close()
        self.assertEqual(handler.stats()["acknowledged"], 1)
        self.assertEqual(len(seen), 1)

    def test_broker_acknowledgement_validated_before_counting(self):
        def respond(receiver, data, attempt):
            body = {} if attempt == 1 else {
                "receiptId": "12345678-1234-1234-1234-123456789abc",
                "acknowledgement": "BROKER_ACCEPTED", "processingState": "ASYNCHRONOUS",
                "events": [{"eventId": event["eventId"], "status": "BROKER_ACCEPTED"} for event in data["events"]],
            }
            encoded = json.dumps(body).encode()
            receiver.send_response(202)
            receiver.send_header("Content-Length", str(len(encoded)))
            receiver.end_headers()
            receiver.wfile.write(encoded)
        logger, handler, seen = self.fixture(respond, DEVINSIGHT_DELIVERY_MODE="ASYNC_KAFKA")
        logger.info("asynchronous fixture")
        handler.close()
        self.assertEqual(seen[0], seen[1])
        self.assertEqual(handler.stats()["brokerAccepted"], 1)
        self.assertEqual(handler.stats()["persisted"], 0)
        self.assertEqual(handler.stats()["retries"], 1)

    def test_timeout_is_bounded(self):
        def silent(receiver, data, attempt):
            time.sleep(0.5)
        logger, handler, seen = self.fixture(silent, DEVINSIGHT_REQUEST_TIMEOUT_MS="65",
                                             DEVINSIGHT_MAX_ATTEMPTS="2", DEVINSIGHT_SHUTDOWN_MS="1000")
        logger.info("silent receiver")
        start = time.monotonic()
        handler.close()
        self.assertLess(time.monotonic() - start, 1.0)
        self.assertEqual(len(seen), 2)
        self.assertEqual(handler.stats()["rejected"], 1)
