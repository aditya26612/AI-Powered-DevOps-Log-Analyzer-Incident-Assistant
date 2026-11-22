import { after, before, test } from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { createServer } from "vite";
import { boundedPage, label, rangeKey, signalTotals } from "../src/observability.js";

let server;
let controls;
let endpoints;
let api;
let resources;

before(async () => {
  server = await createServer({ server: { middlewareMode: true, ws: false }, appType: "custom" });
  controls = await server.ssrLoadModule("/src/components/WorkspaceControls.jsx");
  endpoints = await server.ssrLoadModule("/src/api/observabilityApi.js");
  api = (await server.ssrLoadModule("/src/api/axios.js")).default;
  resources = await server.ssrLoadModule("/src/hooks/useResource.js");
});

after(async () => { await server?.close(); });

test("filter range and page cannot exceed the supported bounds", () => {
  assert.equal(rangeKey("MONTH"), "MONTH");
  assert.equal(rangeKey("YEAR"), "DAY");
  assert.equal(boundedPage("200"), 200);
  assert.equal(boundedPage("201"), 0);
  assert.equal(boundedPage("-5"), 0);
  assert.equal(boundedPage("1.5"), 0);
});

test("log totals keep operational errors, anomalies and failed scoring independent", () => {
  assert.deepEqual(signalTotals([
    { logs: 3, anomalies: 0, errors: 2, scoringFailures: 1 },
    { logs: 5, anomalies: 2, errors: 0, scoringFailures: 1 },
  ]), { logs: 8, anomalies: 2, errors: 2, failures: 2 });
  assert.deepEqual(signalTotals([]), { logs: 0, anomalies: 0, errors: 0, failures: 0 });
});

test("unknown notification is rendered as a labelled warning", () => {
  const html = renderToStaticMarkup(React.createElement(controls.Status, { value: "DELIVERY_UNKNOWN" }));
  assert.match(html, /class="work-status warning"/);
  assert.match(html, />Delivery unknown</);
  assert.equal(label("V2_NORMAL"), "V2 normal");
});

test("application filters use scoped IDs and React escapes the application name", () => {
  const html = renderToStaticMarkup(React.createElement(controls.ApplicationSelect, {
    value: "9007199254740993",
    applications: [{ id: "9007199254740993", applicationName: "<script>", environment: "PRODUCTION" }],
    onChange() {},
  }));
  assert.match(html, /value="9007199254740993" selected=""/);
  assert.match(html, /&lt;script&gt;/);
  assert.doesNotMatch(html, /<script>/);
});

test("error display identifies both the failure and remaining data", () => {
  const html = renderToStaticMarkup(React.createElement(controls.ResourceError, {
    resource: { error: "The resource changed.", data: { content: [] } },
  }));
  assert.match(html, /role="alert"/);
  assert.match(html, /Previously loaded data is shown/);
});

test("scoped log queries keep search text in a body and send cancellation and authorization", async (t) => {
  const previousAdapter = api.defaults.adapter;
  const headers = new Map([["token", "synthetic-session"], ["devinsight.activeOrganization", "stale-tenant"]]);
  const previous = globalThis.localStorage;
  globalThis.localStorage = { getItem: (key) => headers.get(key) };
  t.after(() => {
    api.defaults.adapter = previousAdapter;
    globalThis.localStorage = previous;
  });
  const controller = new AbortController();
  api.defaults.adapter = async (config) => {
    assert.equal(config.url, "/api/v1/organizations/selected-tenant/observability/logs");
    assert.equal(config.method, "post");
    assert.equal(config.headers.get("Authorization"), "Bearer synthetic-session");
    assert.equal(config.headers.has("X-DevInsight-Organization"), false);
    assert.deepEqual(config.params, { range: "WEEK", page: 2, size: 25 });
    assert.deepEqual(JSON.parse(config.data), { text: "distinct message" });
    assert.equal(config.signal, controller.signal);
    return { data: { content: [] }, status: 200, config };
  };
  assert.deepEqual(await endpoints.getLogPage("selected-tenant",
    { range: "WEEK", page: 2, size: 25, search: "distinct message" }, controller.signal), { content: [] });
  controller.abort();
  await assert.rejects(endpoints.getLogPage("selected-tenant", {}, controller.signal), { code: "ERR_CANCELED" });
});

test("status-specific read failures cannot display attacker-provided error payloads", () => {
  for (const [status, expected] of [[401, /expired/], [403, /permission/], [404, /unavailable/], [409, /changed/]]) {
    const error = resources.readError({ response: { status, data: { message: "private server content" } } });
    assert.match(error, expected);
    assert.doesNotMatch(error, /private server content/);
  }
});
