"use strict";

const assert = require("node:assert/strict");
const http = require("node:http");
const { once } = require("node:events");
const { test } = require("node:test");
const { DevInsightCollector } = require("../devinsight-collector");

const key = "synthetic-key-for-local-test";

async function fixture(t, respond, options = {}) {
  const seen = [];
  const listener = http.createServer(async (req, res) => {
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    const bytes = Buffer.concat(chunks);
    const data = JSON.parse(bytes);
    seen.push({ data, bytes: bytes.length });
    respond(res, data, seen.length);
  });
  listener.listen(0, "127.0.0.1");
  await once(listener, "listening");
  const version = options.deliveryMode === "ASYNC_KAFKA" ? "v2" : "v1";
  const collector = new DevInsightCollector({
    endpoint: "http://127.0.0.1:" + listener.address().port + "/api/" + version + "/logs/batch",
    apiKey: key, applicationName: "fixture", environment: "TESTING",
    flushMs: 20, shutdownMs: 3000, ...options,
  });
  t.after(async () => {
    await collector.close();
    listener.closeAllConnections();
    await new Promise(resolve => listener.close(resolve));
  });
  return { seen, collector };
}

function ack(res, batch, status = "ACCEPTED") {
  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ events: batch.events.map((event, i) => ({
    eventId: event.eventId, logId: String(i + 1), status,
  })) }));
}

test("trace fields preserve valid context and omit malformed input", async t => {
  const { collector, seen } = await fixture(t, (res, batch) => ack(res, batch));
  const parent = "00-268ced385f22156b8c8233406b31c3da-38f598163b30a21c-01";
  collector.capture("ERROR", "valid", { traceparent: parent, traceOrigin: "INBOUND_REPORTED" });
  collector.capture("ERROR", "invalid", { traceparent: "fake", traceOrigin: "EXPLICIT" });
  await collector.close();
  const events = seen.flatMap(item => item.data.events);
  assert.equal(events.length, 2);
  assert.equal(events[0].traceparent, parent);
  assert.equal(events[0].traceOrigin, "INBOUND_REPORTED");
  assert.equal(events[1].traceparent, undefined);
});

test("bounds count, bytes, queue and redacts before dispatch", async t => {
  const { collector, seen } = await fixture(t, ackSafe, { batchCount: 2, batchBytes: 1024, queueCapacity: 4 });
  function ackSafe(res, batch) { ack(res, batch); }
  collector.capture("INFO", "excluded", { loggerName: "devinsight.collector.internal" });
  for (let i = 0; i < 7; i++) collector.capture("WARN", "password=fixture-secret " + "λ".repeat(280));
  await collector.close();
  const stats = collector.stats();
  assert.equal(stats.captured, 7);
  assert.equal(stats.dropped, 3);
  assert.equal(stats.acknowledged, 4);
  assert.equal(stats.peakDepth, 4);
  assert.ok(seen.every(row => row.bytes <= 1024 && row.data.events.length <= 2));
  assert.ok(seen.every(row => !JSON.stringify(row).includes("fixture-secret")));
});

test("stable event ID when acknowledgement is lost and the receiver recovers", async t => {
  const { collector, seen } = await fixture(t, (res, data, attempt) => {
    if (attempt === 1) res.socket.destroy();
    else ack(res, data, "DUPLICATE");
  });
  collector.capture("ERROR", "application error", { stack: "Error: fixture\n at service" });
  await collector.close();
  assert.equal(seen.length, 2);
  assert.deepEqual(seen[0], seen[1]);
  assert.equal(collector.stats().duplicates, 1);
  assert.equal(collector.stats().retries, 1);
});

test("caps requests, validates acknowledgements and respects Retry-After", async t => {
  const sent = [];
  const { collector } = await fixture(t, (res, data, attempt) => {
    sent.push(performance.now());
    if (attempt === 1) {
      res.writeHead(429, { "Retry-After": "1" });
      res.end();
    } else if (attempt === 2) {
      res.writeHead(200);
      res.end("{}");
    } else ack(res, data);
  });
  collector.capture("INFO", "delivery fixture");
  await collector.close();
  assert.ok(sent[1] - sent[0] > 950);
  assert.equal(collector.stats().requests, 3);
  assert.equal(collector.stats().acknowledged, 1);
});

test("splits 413 and treats authorization as permanent", async t => {
  const { collector, seen } = await fixture(t, (res, data) => {
    if (data.events.length > 1) {
      res.writeHead(413);
      res.end();
    } else if (data.events[0].message === "denied") {
      res.writeHead(403);
      res.end();
    } else ack(res, data);
  });
  collector.capture("INFO", "accepted");
  collector.capture("INFO", "denied");
  await collector.close();
  assert.equal(seen.length, 3);
  assert.equal(collector.stats().rejected, 1);
  assert.equal(collector.stats().acknowledged, 1);
  assert.equal(collector.stats().retries, 0);
});

test("valid broker acknowledgement counts independently of persisted events", async t => {
  const { collector, seen } = await fixture(t, (res, data, attempt) => {
    res.writeHead(202, { "Content-Type": "application/json" });
    res.end(JSON.stringify(attempt === 1 ? {} : {
      receiptId: "12345678-1234-1234-1234-123456789abc",
      acknowledgement: "BROKER_ACCEPTED",
      processingState: "ASYNCHRONOUS",
      events: data.events.map(event => ({ eventId: event.eventId, status: "BROKER_ACCEPTED" })),
    }));
  }, { deliveryMode: "ASYNC_KAFKA" });
  collector.capture("INFO", "asynchronous capture");
  await collector.close();
  assert.deepEqual(seen[0], seen[1]);
  assert.equal(collector.stats().brokerAccepted, 1);
  assert.equal(collector.stats().persisted, 0);
  assert.equal(collector.stats().retries, 1);
});

test("request timeout and total shutdown deadline bound a silent receiver", async t => {
  const { collector } = await fixture(t, () => {}, { requestTimeoutMs: 70, maxAttempts: 2, shutdownMs: 900 });
  collector.capture("INFO", "receiver is silent");
  const start = performance.now();
  await collector.close();
  assert.ok(performance.now() - start < 900);
  assert.equal(collector.stats().requests, 2);
  assert.equal(collector.stats().rejected, 1);
});
