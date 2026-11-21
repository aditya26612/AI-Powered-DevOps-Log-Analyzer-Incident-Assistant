"use strict";

const http = require("node:http");
const https = require("node:https");
const { randomUUID } = require("node:crypto");

const SYNC = "/api/v1/logs/batch";
const ASYNC = "/api/v2/logs/batch";
const LEVELS = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"];
const ENVIRONMENTS = ["DEVELOPMENT", "TESTING", "QA", "STAGING", "PRODUCTION"];
const RETRYABLE = new Set([408, 429, 500, 502, 503, 504]);
const TRACE_PARENT = /^00-([a-f0-9]{32})-([a-f0-9]{16})-[a-f0-9]{2}$/;
const TRACE_ORIGINS = new Set(["INBOUND_REPORTED", "LOCAL_ROOT_REPORTED", "EXPLICIT"]);

function setting(name, fallback, minimum, maximum, override) {
  const value = Number(override ?? process.env["DEVINSIGHT_" + name] ?? fallback);
  if (!Number.isInteger(value) || value < minimum || value > maximum) {
    throw new Error("Invalid DevInsight " + name);
  }
  return value;
}

function retryAfter(value) {
  if (typeof value !== "string") return 0;
  const millis = /^\d{1,12}$/.test(value) ? Number(value) * 1000 : Date.parse(value) - Date.now();
  return Number.isFinite(millis) ? Math.max(0, Math.min(60000, millis)) : 0;
}

class DevInsightCollector {
  #key;
  #url;
  #prefix;
  #options;
  #asynchronous;
  #success;
  #closing;
  #closePromise;
  #timer;
  #work;
  #pending = [];
  #interrupt;
  #finishDelay;
  #flight = 0;
  #counts = {
    captured: 0, enqueued: 0, requests: 0, attemptedEvents: 0, acknowledged: 0,
    duplicates: 0, retries: 0, failedBatches: 0, rejected: 0, dropped: 0, peakDepth: 0,
    lastSuccess: null, lastFailure: null,
  };

  constructor(options = {}) {
    this.#key = options.apiKey || process.env.DEVINSIGHT_API_KEY;
    const name = options.applicationName || process.env.DEVINSIGHT_APPLICATION_NAME;
    const environment = options.environment || process.env.DEVINSIGHT_ENVIRONMENT;
    if (!/^[\x21-\x7e]{16,160}$/.test(this.#key || "")) throw new Error("A valid DEVINSIGHT_API_KEY is required");
    if (!name || name.length > 100 || !ENVIRONMENTS.includes(environment)) {
      throw new Error("DevInsight application name and environment are required");
    }
    const mode = options.deliveryMode ?? process.env.DEVINSIGHT_DELIVERY_MODE ?? "SYNC_PERSISTED";
    if (!["SYNC_PERSISTED", "ASYNC_KAFKA"].includes(mode)) throw new Error("Invalid DEVINSIGHT_DELIVERY_MODE");
    this.#asynchronous = mode === "ASYNC_KAFKA";
    this.#success = this.#asynchronous ? 202 : 200;
    const path = this.#asynchronous ? ASYNC : SYNC;
    const gateway = (options.gatewayUrl || "http://127.0.0.1:8080").replace(/\/$/, "");
    try {
      this.#url = new URL(options.endpoint || process.env.DEVINSIGHT_ENDPOINT || gateway + path);
      const local = ["localhost", "127.0.0.1", "[::1]"].includes(this.#url.hostname);
      if (this.#url.username || this.#url.password || this.#url.search || this.#url.hash
          || this.#url.pathname !== path
          || !(this.#url.protocol === "https:" || this.#url.protocol === "http:" && local)) {
        throw new Error();
      }
    } catch {
      throw new Error("Use an HTTPS DevInsight batch URL or a loopback HTTP URL");
    }
    this.#prefix = Buffer.from(JSON.stringify({ applicationName: name, environment }).slice(0, -1) + ',"events":[');
    const level = String(options.minLevel ?? process.env.DEVINSIGHT_MIN_LEVEL ?? "INFO").toUpperCase();
    if (!LEVELS.includes(level)) throw new Error("Invalid DEVINSIGHT_MIN_LEVEL");
    this.#options = {
      level: LEVELS.indexOf(level),
      count: setting("BATCH_COUNT", 10, 1, 10, options.batchCount),
      bytes: setting("BATCH_BYTES", 65536, 1024, 65536, options.batchBytes),
      capacity: setting("QUEUE_CAPACITY", 256, 1, 1024, options.queueCapacity),
      interval: setting("FLUSH_MS", 250, 10, 5000, options.flushMs),
      connect: setting("CONNECT_TIMEOUT_MS", 1500, 50, 10000, options.connectTimeoutMs),
      timeout: setting("REQUEST_TIMEOUT_MS", 15000, 50, 60000, options.requestTimeoutMs),
      attempts: setting("MAX_ATTEMPTS", 4, 1, 8, options.maxAttempts),
      shutdown: setting("SHUTDOWN_MS", 5000, 50, 30000, options.shutdownMs),
      redact: String(options.redact ?? process.env.DEVINSIGHT_REDACT ?? true).toLowerCase() !== "false",
    };
  }

  capture(level, message, { stack, loggerName, traceparent, traceOrigin } = {}) {
    level = String(level).toUpperCase();
    if (level === "WARNING") level = "WARN";
    if (level === "CRITICAL") level = "FATAL";
    const severity = LEVELS.indexOf(level);
    if (severity < this.#options.level || String(loggerName || "").startsWith("devinsight.collector")) return;
    this.#counts.captured++;
    if (this.#closing !== undefined || this.#pending.length >= this.#options.capacity) {
      this.#counts.dropped++;
      return;
    }
    const eventId = randomUUID();
    const event = {
      eventId, timestamp: new Date().toISOString(), level, source: "NODEJS",
      message: this.#clean(message ?? "", 8192).trim() || "(empty log message)",
    };
    if (stack) event.exception = this.#clean(stack, 4096);
    if (loggerName) event.loggerName = this.#clean(loggerName, 255);
    const trace = typeof traceparent === "string" && traceparent.length === 55 && TRACE_PARENT.exec(traceparent);
    if (trace && /[1-9a-f]/.test(trace[1]) && /[1-9a-f]/.test(trace[2])) {
      event.traceparent = traceparent;
      event.traceOrigin = TRACE_ORIGINS.has(traceOrigin) ? traceOrigin : "COLLECTOR_REPORTED";
    }
    const data = Buffer.from(JSON.stringify(event));
    if (this.#prefix.length + data.length + 2 > this.#options.bytes) {
      this.#counts.rejected++;
      return;
    }
    this.#pending.push({ eventId, data, queuedAt: performance.now() });
    this.#counts.enqueued++;
    this.#counts.peakDepth = Math.max(this.#pending.length, this.#counts.peakDepth);
    this.#schedule();
  }

  #clean(value, limit) {
    let text = String(value).replaceAll(this.#key, "[REDACTED]");
    if (this.#options.redact) {
      text = text
        .replace(/\bBearer\s+[A-Za-z0-9._~+/=-]+/gi, "Bearer [REDACTED]")
        .replace(/dvi_[A-Za-z0-9_-]{16,}/g, "[REDACTED]")
        .replace(/\b(password|passwd|secret|token|authorization|api[-_ ]?key|cookie)(["']?\s*[:=]\s*)(?:"[^"\r\n]*"|'[^'\r\n]*'|[^\s,;]+)/gi, "$1$2[REDACTED]")
        .replace(/(\b[a-z][a-z0-9+.-]*:\/\/)[^/\s@]+:[^/\s@]+@/gi, "$1[REDACTED]@");
    }
    return Buffer.from(text.replaceAll("\0", "\uFFFD")).subarray(0, limit).toString("utf8").replace(/\uFFFD$/u, "");
  }

  stats() {
    const acknowledged = this.#counts.acknowledged;
    return { ...this.#counts, queueDepth: this.#pending.length, inFlight: this.#flight,
      brokerAccepted: this.#asynchronous ? acknowledged : 0, persisted: this.#asynchronous ? 0 : acknowledged };
  }

  #schedule() {
    clearTimeout(this.#timer);
    if (this.#work || !this.#pending.length || this.#closing !== undefined) return;
    const wait = this.#pending.length >= this.#options.count ? 0
      : Math.max(0, this.#pending[0].queuedAt + this.#options.interval - performance.now());
    this.#timer = setTimeout(() => this.#start(), wait);
    this.#timer.unref();
  }

  #start() {
    if (this.#work || !this.#pending.length) return;
    if (this.#closing !== undefined && performance.now() >= this.#closing) {
      this.#counts.dropped += this.#pending.length;
      this.#pending = [];
      return;
    }
    const frames = [];
    let size = this.#prefix.length + 2;
    while (this.#pending.length && frames.length < this.#options.count) {
      const next = this.#pending[0];
      const newSize = size + next.data.length + Number(frames.length !== 0);
      if (newSize > this.#options.bytes) break;
      frames.push(this.#pending.shift());
      size = newSize;
    }
    this.#flight = frames.length;
    this.#work = this.#deliver(frames).catch(() => {
      this.#fail(frames, "sender");
    }).finally(() => {
      this.#work = null;
      this.#flight = 0;
      if (this.#closing === undefined) this.#schedule();
    });
  }

  async #deliver(batch) {
    const parts = [this.#prefix];
    batch.forEach((frame, i) => {
      if (i) parts.push(Buffer.from(","));
      parts.push(frame.data);
    });
    parts.push(Buffer.from("]}"));
    const body = Buffer.concat(parts);
    let failure = "retry_exhausted";
    for (let i = 0; i < this.#options.attempts; i++) {
      if (this.#remaining() <= 0) {
        this.#counts.dropped += batch.length;
        return;
      }
      this.#counts.requests++;
      this.#counts.attemptedEvents += batch.length;
      const reply = await this.#send(body);
      if (reply.status === this.#success) {
        const result = acknowledge(reply.data, batch, this.#asynchronous);
        if (result !== null) {
          this.#counts.acknowledged += batch.length;
          this.#counts.duplicates += result;
          this.#counts.lastSuccess = new Date().toISOString();
          return;
        }
        failure = "invalid_ack";
      } else if (reply.status === 413 && batch.length > 1) {
        const middle = Math.floor(batch.length / 2);
        await this.#deliver(batch.slice(0, middle));
        await this.#deliver(batch.slice(middle));
        return;
      } else {
        failure = reply.status === null ? "network" : "http_" + reply.status;
        if (reply.status !== null && !RETRYABLE.has(reply.status)) break;
      }
      if (i + 1 >= this.#options.attempts) break;
      const wait = Math.max(retryAfter(reply.retryAfter), 100 + Math.random() * (Math.min(4000, 250 * (2 ** i)) - 100));
      if (!await this.#sleep(wait)) {
        this.#counts.dropped += batch.length;
        return;
      }
      this.#counts.retries++;
    }
    this.#fail(batch, failure);
  }

  #fail(batch, category) {
    this.#counts.failedBatches++;
    this.#counts.rejected += batch.length;
    this.#counts.lastFailure = category;
  }

  #remaining() {
    return this.#closing === undefined ? Infinity : Math.max(0, this.#closing - performance.now());
  }

  #sleep(delay) {
    if (delay >= this.#remaining()) return Promise.resolve(false);
    return new Promise(resolve => {
      const finish = value => {
        clearTimeout(timer);
        this.#finishDelay = undefined;
        resolve(value);
      };
      const timer = setTimeout(() => finish(true), delay);
      this.#finishDelay = finish;
    });
  }

  #send(body) {
    return new Promise(resolve => {
      const client = this.#url.protocol === "https:" ? https : http;
      const request = client.request(this.#url, {
        method: "POST",
        headers: { "Content-Type": "application/json", "Content-Length": body.length, "X-API-Key": this.#key },
      });
      let done = false;
      const finish = value => {
        if (done) return;
        done = true;
        clearTimeout(total);
        clearTimeout(connect);
        this.#interrupt = undefined;
        resolve(value);
      };
      const cancel = () => {
        request.destroy();
        finish({ status: null });
      };
      const total = setTimeout(cancel, Math.min(this.#options.timeout, this.#remaining()));
      const connect = setTimeout(cancel, this.#options.connect);
      this.#interrupt = cancel;
      request.on("socket", socket => {
        if (socket.connecting) {
          socket.once(this.#url.protocol === "https:" ? "secureConnect" : "connect", () => clearTimeout(connect));
        } else {
          clearTimeout(connect);
        }
      });
      request.on("error", () => finish({ status: null }));
      request.on("response", response => {
        if (response.statusCode !== this.#success) {
          finish({ status: response.statusCode, retryAfter: response.headers["retry-after"] });
          response.destroy();
          return;
        }
        const parts = [];
        let size = 0;
        response.on("data", part => {
          size += part.length;
          if (size > 8192) {
            finish({ status: this.#success, data: Buffer.alloc(0) });
            response.destroy();
          } else {
            parts.push(part);
          }
        });
        response.on("error", () => finish({ status: null }));
        response.on("aborted", () => finish({ status: null }));
        response.on("end", () => finish({ status: this.#success, data: Buffer.concat(parts) }));
      });
      request.end(body);
    });
  }

  close() {
    this.#closePromise ??= this.#drain();
    return this.#closePromise;
  }

  async #drain() {
    this.#closing = performance.now() + this.#options.shutdown;
    clearTimeout(this.#timer);
    const deadline = setTimeout(() => {
      this.#interrupt?.();
      this.#finishDelay?.(false);
    }, this.#options.shutdown);
    try {
      while (this.#work || this.#pending.length) {
        if (!this.#work) this.#start();
        await this.#work;
      }
    } finally {
      clearTimeout(deadline);
    }
  }
}

function acknowledge(response, batch, asynchronous) {
  try {
    const { events, receiptId, acknowledgement, processingState } = JSON.parse(response);
    if (asynchronous && (acknowledgement !== "BROKER_ACCEPTED" || processingState !== "ASYNCHRONOUS"
        || typeof receiptId !== "string" || !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(receiptId))) return null;
    if (!Array.isArray(events) || events.length !== batch.length) return null;
    let duplicates = 0;
    for (const [i, item] of events.entries()) {
      if (item.eventId !== batch[i].eventId) return null;
      if (asynchronous) {
        if (item.status !== "BROKER_ACCEPTED" || Object.hasOwn(item, "logId")) return null;
      } else {
        if (typeof item.logId !== "string" || !/^[1-9]\d{0,18}$/.test(item.logId)
            || !["ACCEPTED", "DUPLICATE"].includes(item.status)) return null;
        if (item.status === "DUPLICATE") duplicates++;
      }
    }
    return duplicates;
  } catch {
    return null;
  }
}

module.exports = { DevInsightCollector };
