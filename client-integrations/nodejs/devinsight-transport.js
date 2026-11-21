"use strict";

const Transport = require("winston-transport");
const { DevInsightCollector } = require("./devinsight-collector");
let tracing;
try {
  tracing = require("@opentelemetry/api").trace;
} catch (error) {
  if (error.code !== "MODULE_NOT_FOUND") throw error;
}

class DevInsightTransport extends Transport {
  constructor(options = {}) {
    super(options);
    this.collector = new DevInsightCollector(options);
  }

  log(info, callback) {
    const level = info[Symbol.for("level")] || info.level;
    const active = !info.traceparent && tracing?.getActiveSpan()?.spanContext();
    const traceparent = info.traceparent || (active
      ? `00-${active.traceId}-${active.spanId}-${(active.traceFlags & 255).toString(16).padStart(2, "0")}` : undefined);
    this.collector.capture(level, info.message, {
      stack: info.stack, loggerName: info.label, traceparent, traceOrigin: info.trace_origin,
    });
    setImmediate(() => this.emit("logged", info));
    if (callback) callback();
  }

  stats() {
    return this.collector.stats();
  }

  close() {
    return this.collector.close();
  }
}

module.exports = DevInsightTransport;
